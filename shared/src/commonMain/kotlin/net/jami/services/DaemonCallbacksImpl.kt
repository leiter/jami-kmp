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
package net.jami.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import net.jami.model.SwarmMessage
import net.jami.utils.Log
import kotlin.concurrent.Volatile

/**
 * Implementation of DaemonCallbacks that routes daemon events to the appropriate services.
 *
 * This is the orchestration layer that connects the native daemon to Kotlin services.
 * Each callback method dispatches work to the appropriate service's internal handler
 * within a coroutine scope.
 *
 * ## Usage
 *
 * ```kotlin
 * val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
 * val daemonBridge = DaemonBridge()
 *
 * val accountService = AccountService(daemonBridge, scope)
 * val callService = CallService(daemonBridge, accountService, scope)
 * val contactService = ContactService(daemonBridge, scope)
 * val conversationFacade = ConversationFacade(callService, accountService, ...)
 *
 * val callbacks = DaemonCallbacksImpl(
 *     accountService = accountService,
 *     callService = callService,
 *     contactService = contactService,
 *     conversationFacade = conversationFacade,
 *     scope = scope
 * )
 *
 * daemonBridge.init(callbacks)
 * ```
 */
class DaemonCallbacksImpl(
    private val accountService: AccountService,
    private val callService: CallService,
    private val contactService: ContactService,
    private val conversationFacade: ConversationFacade,
    private val scope: CoroutineScope
) : DaemonCallbacks {

    companion object {
        private const val TAG = "DaemonCallbacks"
        private const val CALLBACK_BUFFER_SIZE = 512
    }

    // Buffered flows for high-volume callbacks to prevent event loss during rapid sync
    private sealed class CallbackEvent {
        data class MessageReceived(
            val accountId: String,
            val conversationId: String,
            val message: SwarmMessage
        ) : CallbackEvent()

        data class SwarmLoaded(
            val id: Long,
            val accountId: String,
            val conversationId: String,
            val messages: List<SwarmMessage>
        ) : CallbackEvent()

        data class DataTransfer(
            val accountId: String,
            val conversationId: String,
            val interactionId: String,
            val fileId: String,
            val eventCode: Int
        ) : CallbackEvent()
    }

    private val messageReceivedFlow = MutableSharedFlow<CallbackEvent.MessageReceived>(
        extraBufferCapacity = CALLBACK_BUFFER_SIZE,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val swarmLoadedFlow = MutableSharedFlow<CallbackEvent.SwarmLoaded>(
        extraBufferCapacity = CALLBACK_BUFFER_SIZE,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val dataTransferFlow = MutableSharedFlow<CallbackEvent.DataTransfer>(
        extraBufferCapacity = CALLBACK_BUFFER_SIZE,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    // Counters for dropped events (for monitoring/diagnostics)
    @Volatile private var droppedMessageReceivedCount = 0L
    @Volatile private var droppedSwarmLoadedCount = 0L
    @Volatile private var droppedDataTransferCount = 0L

    init {
        // Process buffered message received events
        scope.launch {
            messageReceivedFlow.collect { event ->
                conversationFacade.onMessageReceived(
                    event.accountId,
                    event.conversationId,
                    event.message
                )
            }
        }

        // Process buffered swarm loaded events
        scope.launch {
            swarmLoadedFlow.collect { event ->
                conversationFacade.onSwarmLoaded(
                    event.id,
                    event.accountId,
                    event.conversationId,
                    event.messages
                )
            }
        }

        // Process buffered data transfer events
        scope.launch {
            dataTransferFlow.collect { event ->
                conversationFacade.onDataTransferEvent(
                    event.accountId,
                    event.conversationId,
                    event.interactionId,
                    event.fileId,
                    event.eventCode
                )
            }
        }
    }

    /**
     * Get diagnostic information about dropped events.
     * Useful for monitoring buffer overflow during heavy sync.
     */
    fun getDroppedEventCounts(): Map<String, Long> = mapOf(
        "messageReceived" to droppedMessageReceivedCount,
        "swarmLoaded" to droppedSwarmLoadedCount,
        "dataTransfer" to droppedDataTransferCount
    )

    // ==================== Account Callbacks ====================

    override fun onAccountsChanged() {
        Log.d(TAG, "onAccountsChanged")
        scope.launch {
            accountService.onAccountsChanged()
        }
    }

    override fun onAccountDetailsChanged(accountId: String, details: Map<String, String>) {
        Log.d(TAG, "onAccountDetailsChanged: $accountId")
        scope.launch {
            accountService.onAccountDetailsChanged(accountId, details)
        }
    }

    override fun onRegistrationStateChanged(accountId: String, state: String, code: Int, detail: String) {
        Log.d(TAG, "onRegistrationStateChanged: $accountId state=$state code=$code")
        scope.launch {
            accountService.onRegistrationStateChanged(accountId, state, code, detail)
        }
    }

    override fun onVolatileAccountDetailsChanged(accountId: String, details: Map<String, String>) {
        Log.d(TAG, "onVolatileAccountDetailsChanged: $accountId")
        scope.launch {
            accountService.onVolatileAccountDetailsChanged(accountId, details)
        }
    }

    override fun onKnownDevicesChanged(accountId: String, devices: Map<String, String>) {
        Log.d(TAG, "onKnownDevicesChanged: $accountId devices=${devices.size}")
        scope.launch {
            accountService.onKnownDevicesChanged(accountId, devices)
        }
    }

    override fun onDeviceRevocationEnded(accountId: String, deviceId: String, state: Int) {
        Log.d(TAG, "onDeviceRevocationEnded: $accountId device=$deviceId state=$state")
        scope.launch {
            accountService.onDeviceRevocationEnded(accountId, deviceId, state)
        }
    }

    override fun onAddDeviceStateChanged(accountId: String, opId: Long, state: Int, details: Map<String, String>) {
        Log.d(TAG, "onAddDeviceStateChanged: $accountId opId=$opId state=$state")
        scope.launch {
            accountService.onAddDeviceStateChanged(accountId, opId, state, details)
        }
    }

    override fun onMigrationEnded(accountId: String, state: String) {
        Log.d(TAG, "onMigrationEnded: $accountId state=$state")
        scope.launch {
            accountService.onMigrationEnded(accountId, state)
        }
    }

    override fun onAccountProfileReceived(accountId: String, name: String, photo: String) {
        Log.d(TAG, "onAccountProfileReceived: $accountId name=$name photoLen=${photo.length}")
        scope.launch {
            accountService.onAccountProfileReceived(accountId, name, photo)
        }
    }

    // ==================== Call Callbacks ====================

    override fun onCallStateChanged(accountId: String, callId: String, state: String, code: Int) {
        Log.d(TAG, "onCallStateChanged: $callId state=$state code=$code")
        scope.launch {
            callService.onCallStateChanged(accountId, callId, state, code)
        }
    }

    override fun onIncomingCall(accountId: String, callId: String, from: String) {
        Log.d(TAG, "onIncomingCall: $callId from=$from")
        scope.launch {
            callService.onIncomingCall(accountId, callId, from, emptyList())
        }
    }

    override fun onIncomingCallWithMedia(accountId: String, callId: String, from: String, mediaList: List<Map<String, String>>) {
        Log.d(TAG, "onIncomingCallWithMedia: $callId from=$from media=${mediaList.size}")
        scope.launch {
            callService.onIncomingCall(accountId, callId, from, mediaList)
        }
    }

    override fun onMediaChangeRequested(accountId: String, callId: String, mediaList: List<Map<String, String>>) {
        Log.d(TAG, "onMediaChangeRequested: $callId media=${mediaList.size}")
        scope.launch {
            callService.onMediaNegotiationStatus(callId, "media_change_requested", mediaList)
        }
    }

    override fun onAudioMuted(callId: String, muted: Boolean) {
        Log.d(TAG, "onAudioMuted: $callId muted=$muted")
        scope.launch {
            callService.onAudioMuted(callId, muted)
        }
    }

    override fun onVideoMuted(callId: String, muted: Boolean) {
        Log.d(TAG, "onVideoMuted: $callId muted=$muted")
        scope.launch {
            callService.onVideoMuted(callId, muted)
        }
    }

    override fun onMediaNegotiationStatus(callId: String, event: String, mediaList: List<Map<String, String>>) {
        Log.d(TAG, "onMediaNegotiationStatus: $callId event=$event")
        scope.launch {
            callService.onMediaNegotiationStatus(callId, event, mediaList)
        }
    }

    override fun onConferenceCreated(accountId: String, conversationId: String, confId: String) {
        Log.d(TAG, "onConferenceCreated: $confId conversationId=$conversationId")
        scope.launch {
            callService.onConferenceCreated(accountId, conversationId, confId)
        }
    }

    override fun onConferenceChanged(accountId: String, confId: String, state: String) {
        Log.d(TAG, "onConferenceChanged: $confId state=$state")
        scope.launch {
            callService.onConferenceChanged(accountId, confId, state)
        }
    }

    override fun onConferenceRemoved(accountId: String, confId: String) {
        Log.d(TAG, "onConferenceRemoved: $confId")
        scope.launch {
            callService.onConferenceRemoved(accountId, confId)
        }
    }

    // ==================== Conversation Callbacks ====================

    override fun onConversationReady(accountId: String, conversationId: String) {
        Log.d(TAG, "onConversationReady: $conversationId")
        scope.launch {
            conversationFacade.onConversationReady(accountId, conversationId)
        }
    }

    override fun onConversationRemoved(accountId: String, conversationId: String) {
        Log.d(TAG, "onConversationRemoved: $conversationId")
        scope.launch {
            conversationFacade.onConversationRemoved(accountId, conversationId)
        }
    }

    override fun onConversationRequestReceived(accountId: String, conversationId: String, metadata: Map<String, String>) {
        Log.d(TAG, "onConversationRequestReceived: $conversationId")
        scope.launch {
            conversationFacade.onConversationRequestReceived(accountId, conversationId, metadata)
        }
    }

    override fun onConversationRequestDeclined(accountId: String, conversationId: String) {
        Log.d(TAG, "onConversationRequestDeclined: $conversationId")
        scope.launch {
            conversationFacade.onConversationRequestDeclined(accountId, conversationId)
        }
    }

    override fun onConversationMemberEvent(accountId: String, conversationId: String, memberId: String, event: Int) {
        Log.d(TAG, "onConversationMemberEvent: $conversationId member=$memberId event=$event")
        scope.launch {
            conversationFacade.onConversationMemberEvent(accountId, conversationId, memberId, event)
        }
    }

    override fun onMessageReceived(accountId: String, conversationId: String, message: SwarmMessage) {
        Log.d(TAG, "onMessageReceived: $conversationId msgId=${message.id}")
        val emitted = messageReceivedFlow.tryEmit(
            CallbackEvent.MessageReceived(accountId, conversationId, message)
        )
        if (!emitted) {
            droppedMessageReceivedCount++
            Log.w(TAG, "Dropped onMessageReceived event (buffer full), total dropped: $droppedMessageReceivedCount")
        }
    }

    override fun onMessageUpdated(accountId: String, conversationId: String, message: SwarmMessage) {
        Log.d(TAG, "onMessageUpdated: $conversationId msgId=${message.id}")
        scope.launch {
            conversationFacade.onMessageUpdated(accountId, conversationId, message)
        }
    }

    override fun onMessagesFound(messageId: Int, accountId: String, conversationId: String, messages: List<Map<String, String>>) {
        Log.d(TAG, "onMessagesFound: $conversationId found=${messages.size}")
        scope.launch {
            conversationFacade.onMessagesFound(messageId, accountId, conversationId, messages)
        }
    }

    override fun onSwarmLoaded(id: Long, accountId: String, conversationId: String, messages: List<SwarmMessage>) {
        Log.d(TAG, "onSwarmLoaded: $conversationId messages=${messages.size}")
        val emitted = swarmLoadedFlow.tryEmit(
            CallbackEvent.SwarmLoaded(id, accountId, conversationId, messages)
        )
        if (!emitted) {
            droppedSwarmLoadedCount++
            Log.w(TAG, "Dropped onSwarmLoaded event (buffer full), total dropped: $droppedSwarmLoadedCount")
        }
    }

    override fun onConversationProfileUpdated(accountId: String, conversationId: String, profile: Map<String, String>) {
        Log.d(TAG, "onConversationProfileUpdated: $conversationId")
        scope.launch {
            conversationFacade.onConversationProfileUpdated(accountId, conversationId, profile)
        }
    }

    override fun onConversationPreferencesUpdated(accountId: String, conversationId: String, preferences: Map<String, String>) {
        Log.d(TAG, "onConversationPreferencesUpdated: $conversationId")
        scope.launch {
            conversationFacade.onConversationPreferencesUpdated(accountId, conversationId, preferences)
        }
    }

    override fun onReactionAdded(accountId: String, conversationId: String, messageId: String, reaction: Map<String, String>) {
        Log.d(TAG, "onReactionAdded: $conversationId msgId=$messageId")
        scope.launch {
            conversationFacade.onReactionAdded(accountId, conversationId, messageId, reaction)
        }
    }

    override fun onReactionRemoved(accountId: String, conversationId: String, messageId: String, reactionId: String) {
        Log.d(TAG, "onReactionRemoved: $conversationId msgId=$messageId")
        scope.launch {
            conversationFacade.onReactionRemoved(accountId, conversationId, messageId, reactionId)
        }
    }

    override fun onActiveCallsChanged(accountId: String, conversationId: String, activeCalls: List<Map<String, String>>) {
        Log.d(TAG, "onActiveCallsChanged: $conversationId calls=${activeCalls.size}")
        scope.launch {
            conversationFacade.onActiveCallsChanged(accountId, conversationId, activeCalls)
        }
    }

    // ==================== Presence Callbacks ====================

    override fun onNewBuddyNotification(accountId: String, buddyUri: String, status: Int, lineStatus: String) {
        Log.d(TAG, "onNewBuddyNotification: $buddyUri status=$status")
        scope.launch {
            contactService.onPresenceUpdate(accountId, buddyUri, status)
        }
    }

    // ==================== Contact Callbacks ====================

    override fun onContactAdded(accountId: String, uri: String, confirmed: Boolean) {
        Log.d(TAG, "onContactAdded: $uri confirmed=$confirmed")
        scope.launch {
            accountService.onContactAdded(accountId, uri, confirmed)
        }
    }

    override fun onContactRemoved(accountId: String, uri: String, banned: Boolean) {
        Log.d(TAG, "onContactRemoved: $uri banned=$banned")
        scope.launch {
            accountService.onContactRemoved(accountId, uri, banned)
        }
    }

    override fun onIncomingTrustRequest(accountId: String, conversationId: String, from: String, payload: ByteArray, receiveTime: Long) {
        Log.d(TAG, "onIncomingTrustRequest: from=$from")
        scope.launch {
            accountService.onIncomingTrustRequest(accountId, conversationId, from, payload, receiveTime)
        }
    }

    override fun onProfileReceived(accountId: String, peerId: String, vcardPath: String) {
        Log.d(TAG, "onProfileReceived: $peerId")
        scope.launch {
            contactService.onProfileReceived(accountId, peerId, vcardPath)
        }
    }

    // ==================== Message Callbacks ====================

    override fun onIncomingAccountMessage(accountId: String, messageId: String?, callId: String?, from: String, messages: Map<String, String>) {
        Log.d(TAG, "onIncomingAccountMessage: from=$from")
        scope.launch {
            accountService.onIncomingAccountMessage(accountId, messageId, callId, from, messages)
        }
    }

    override fun onAccountMessageStatusChanged(accountId: String, conversationId: String, messageId: String, contactId: String, status: Int) {
        Log.d(TAG, "onAccountMessageStatusChanged: $messageId status=$status")
        scope.launch {
            conversationFacade.onAccountMessageStatusChanged(accountId, conversationId, messageId, contactId, status)
        }
    }

    override fun onComposingStatusChanged(accountId: String, conversationId: String, contactUri: String, status: Int) {
        Log.d(TAG, "onComposingStatusChanged: $contactUri status=$status")
        scope.launch {
            conversationFacade.onComposingStatusChanged(accountId, conversationId, contactUri, status)
        }
    }

    // ==================== Name Service Callbacks ====================

    override fun onNameRegistrationEnded(accountId: String, state: Int, name: String) {
        Log.d(TAG, "onNameRegistrationEnded: $name state=$state")
        scope.launch {
            accountService.onNameRegistrationEnded(accountId, state, name)
        }
    }

    override fun onRegisteredNameFound(accountId: String, state: Int, address: String, name: String, query: String) {
        Log.d(TAG, "onRegisteredNameFound: query=$query name=$name address=$address state=$state accountId=$accountId")
        scope.launch {
            accountService.onRegisteredNameFound(accountId, state, address, name, query)
        }
    }

    override fun onUserSearchEnded(accountId: String, state: Int, query: String, results: List<Map<String, String>>) {
        Log.d(TAG, "onUserSearchEnded: $query results=${results.size}")
        scope.launch {
            accountService.onUserSearchEnded(accountId, state, query, results)
        }
    }

    // ==================== Data Transfer Callbacks ====================

    override fun onDataTransferEvent(accountId: String, conversationId: String, interactionId: String, fileId: String, eventCode: Int) {
        Log.d(TAG, "onDataTransferEvent: $fileId event=$eventCode")
        val emitted = dataTransferFlow.tryEmit(
            CallbackEvent.DataTransfer(accountId, conversationId, interactionId, fileId, eventCode)
        )
        if (!emitted) {
            droppedDataTransferCount++
            Log.w(TAG, "Dropped onDataTransferEvent (buffer full), total dropped: $droppedDataTransferCount")
        }
    }
}
