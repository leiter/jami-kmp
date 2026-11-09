/*
 *  Copyright (C) 2004-2025 Savoir-faire Linux Inc.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package net.jami.services

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import net.jami.model.Contact
import net.jami.model.Conversation
import net.jami.model.Uri
import net.jami.utils.Log
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.create
import platform.Intents.INCallAudioRouteUnknown
import platform.Intents.INCallCapabilityAudioCall
import platform.Intents.INCallCapabilityVideoCall
import platform.Intents.INCallDestinationTypeNormal
import platform.Intents.INImage
import platform.Intents.INIntent
import platform.Intents.INInteraction
import platform.Intents.INInteractionDirectionIncoming
import platform.Intents.INOutgoingMessageTypeOutgoingMessageText
import platform.Intents.INPerson
import platform.Intents.INPersonHandle
import platform.Intents.INPersonHandleTypeUnknown
import platform.Intents.INSendMessageIntent
import platform.Intents.INSpeakableString
import platform.Intents.INStartCallIntent
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationContent

/**
 * iOS Communication Notifications: a message or missed call shown with the sender's name and
 * photo, the iOS counterpart of Android's `Person` on its notifications (e5af1df item 4).
 *
 * The notification content is rebuilt from an Intents object — [INSendMessageIntent] for a
 * message, [INStartCallIntent] for a missed call — whose [INPerson] carries the avatar. The
 * interaction is donated first, which is what makes iOS draw the sender's photo in place of
 * the app icon, and as a side effect lets the share sheet suggest the conversation.
 *
 * Needs the `com.apple.developer.usernotifications.communication` entitlement
 * (`ios-app/iosApp/iosApp.entitlements`) and `INSendMessageIntent` / `INStartCallIntent` in
 * `NSUserActivityTypes` (`Info.plist`). Without them `contentByUpdatingWithProvider` fails, and
 * every function here returns the plain content it was given — a notification without a photo,
 * never a missing notification.
 *
 * Callers must not use this while "show names in notifications" is off: the point of the
 * setting is that the notification does not say who it is from, and a donation would also put
 * the name into Siri and share-sheet suggestions.
 */
internal class CommunicationNotifications(
    private val vCardService: VCardService,
) {

    /**
     * [content] as a message from whoever sent [conversation]'s last message, or [content]
     * unchanged when iOS refuses the update.
     */
    fun forMessage(
        content: UNMutableNotificationContent,
        conversation: Conversation,
        body: String,
    ): UNNotificationContent {
        val senderContact = messageSender(conversation)
        val senderUri = senderContact?.uri ?: return content
        val sender = person(conversation.accountId, senderContact, senderUri)

        val groupName = if (conversation.isGroup) conversationTitle(conversation) else null
        // A group needs its other members as recipients for iOS to draw it as a group.
        val recipients = if (conversation.isGroup) {
            conversation.contacts.filter { !it.isUser && it.uri != senderUri }
                .map { person(conversation.accountId, it, it.uri) }
        } else {
            null
        }

        val intent = INSendMessageIntent(
            recipients = recipients,
            outgoingMessageType = INOutgoingMessageTypeOutgoingMessageText,
            content = body,
            speakableGroupName = groupName?.let { INSpeakableString(spokenPhrase = it) },
            conversationIdentifier = conversation.uri.uri,
            serviceName = null,
            sender = sender,
            attachments = null,
        )
        if (groupName != null) {
            conversation.profileFlow.value.avatar?.takeIf { it.isNotEmpty() }?.let { avatar ->
                intent.setImage(INImage.imageWithImageData(avatar.toNSData()), forParameterNamed = "speakableGroupName")
            }
        }
        return update(content, intent)
    }

    /** [content] as a missed call from [contact], or unchanged when iOS refuses the update. */
    fun forMissedCall(
        content: UNMutableNotificationContent,
        accountId: String,
        contact: Contact?,
        peerUri: Uri,
        callerName: String,
        hadVideo: Boolean,
    ): UNNotificationContent {
        val intent = INStartCallIntent(
            callRecordFilter = null,
            callRecordToCallBack = null,
            audioRoute = INCallAudioRouteUnknown,
            destinationType = INCallDestinationTypeNormal,
            contacts = listOf(person(accountId, contact, peerUri, callerName)),
            callCapability = if (hadVideo) INCallCapabilityVideoCall else INCallCapabilityAudioCall,
        )
        return update(content, intent)
    }

    private fun update(content: UNMutableNotificationContent, intent: INIntent): UNNotificationContent {
        INInteraction(intent = intent, response = null).apply {
            direction = INInteractionDirectionIncoming
            donateInteractionWithCompletion { error ->
                if (error != null) Log.w(TAG, "Intent donation failed: ${error.localizedDescription}")
            }
        }
        return memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            val updated = content.contentByUpdatingWithProvider(intent as platform.UserNotifications.UNNotificationContentProvidingProtocol, error.ptr)
            if (updated == null) {
                // Typically the entitlement is missing from the signed app: say so once per
                // notification, and fall back to the plain content.
                Log.w(TAG, "Communication notification refused: ${error.value?.localizedDescription}")
            }
            updated ?: content
        }
    }

    private fun person(accountId: String, contact: Contact?, uri: Uri, knownName: String? = null): INPerson {
        val name = knownName?.takeIf { it.isNotBlank() }
            ?: contact?.displayName?.takeIf { it.isNotBlank() }
            ?: contact?.username?.takeIf { it.isNotBlank() }
            ?: uri.rawRingId
        // In-memory profile first; it only carries a picture if a vCard arrived this session,
        // so fall back to the peer's vcf on disk, as Android does (VCardService caches it).
        val avatar = contact?.let { it.customProfile.avatar ?: it.loadedProfile.avatar }
            ?: runCatching { vCardService.loadPeerAvatar(accountId, uri.rawRingId) }.getOrNull()
        return INPerson(
            personHandle = INPersonHandle(value = uri.rawRingId, type = INPersonHandleTypeUnknown),
            nameComponents = null,
            displayName = name,
            image = avatar?.takeIf { it.isNotEmpty() }?.let { INImage.imageWithImageData(it.toNSData()) },
            contactIdentifier = null,
            customIdentifier = uri.uri,
        )
    }

    private companion object {
        const val TAG = "CommunicationNotifications"
    }
}

/**
 * Who sent [conversation]'s last message: the interaction's own contact, else the member
 * matching its author, else — in a 1:1 — the peer. Never `Conversation.contact`, which throws
 * for a group by design.
 */
internal fun messageSender(conversation: Conversation): Contact? {
    val last = conversation.lastEventFlow.value
    last?.contact?.let { return it }
    last?.author?.takeIf { it.isNotBlank() }?.let { author ->
        conversation.contacts.firstOrNull { it.uri.rawRingId == Uri.fromString(author).rawRingId }
            ?.let { return it }
    }
    return conversation.contactOrNull
}

/**
 * The title a conversation's notification carries. Safe for groups: `Conversation.contact`
 * throws for a swarm of more than two, which used to make every group message notification
 * throw on iOS.
 */
internal fun conversationTitle(conversation: Conversation): String =
    conversation.profileFlow.value.displayName?.takeIf { it.isNotBlank() }
        ?: conversation.contactOrNull?.let { it.displayName?.takeIf(String::isNotBlank) ?: it.username?.takeIf(String::isNotBlank) }
        ?: conversation.uri.uri

private fun ByteArray.toNSData(): NSData =
    if (isEmpty()) NSData() // addressOf(0) throws on an empty array
    else usePinned { pinned -> NSData.create(bytes = pinned.addressOf(0), length = size.toULong()) }
