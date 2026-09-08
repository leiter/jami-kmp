package net.jami.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.jami.model.Uri
import net.jami.utils.Log
import org.koin.mp.KoinPlatformTools
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationDefaultActionIdentifier
import platform.UserNotifications.UNNotificationDismissActionIdentifier
import platform.UserNotifications.UNNotificationPresentationOptionAlert
import platform.UserNotifications.UNNotificationPresentationOptionBadge
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.UserNotifications.UNNotificationResponse
import platform.UserNotifications.UNTextInputNotificationResponse
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject

class IOSNotificationDelegate : NSObject(), UNUserNotificationCenterDelegateProtocol {

    private val callService: CallService by lazy {
        KoinPlatformTools.defaultContext().get().get()
    }
    private val conversationFacade: ConversationFacade by lazy {
        KoinPlatformTools.defaultContext().get().get()
    }
    private val accountService: AccountService by lazy {
        KoinPlatformTools.defaultContext().get().get()
    }
    private val scope = CoroutineScope(Dispatchers.Default)

    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        willPresentNotification: UNNotification,
        withCompletionHandler: (ULong) -> Unit
    ) {
        withCompletionHandler(
            UNNotificationPresentationOptionAlert or
                UNNotificationPresentationOptionSound or
                UNNotificationPresentationOptionBadge
        )
    }

    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        didReceiveNotificationResponse: UNNotificationResponse,
        withCompletionHandler: () -> Unit
    ) {
        val request = didReceiveNotificationResponse.notification.request
        val userInfo = request.content.userInfo
        val accountId = userInfo[KEY_ACCOUNT_ID] as? String
        val callId = userInfo[KEY_CALL_ID] as? String
        val conversationId = userInfo[KEY_CONVERSATION_ID] as? String

        if (accountId != null) {
            scope.launch {
                when (didReceiveNotificationResponse.actionIdentifier) {
                    ACTION_ANSWER_CALL -> {
                        if (callId != null) {
                            Log.d(TAG, "Answering call: $callId")
                            callService.accept(accountId, callId, hasVideo = false)
                        }
                    }
                    ACTION_DECLINE_CALL -> {
                        if (callId != null) {
                            Log.d(TAG, "Declining call: $callId")
                            callService.refuse(accountId, callId)
                            center.removeDeliveredNotificationsWithIdentifiers(listOf(request.identifier))
                        }
                    }
                    ACTION_REPLY_MESSAGE -> {
                        val text = (didReceiveNotificationResponse as? UNTextInputNotificationResponse)
                            ?.userText?.takeIf { it.isNotBlank() }
                        val uri = conversationId?.let { Uri.fromString(it) }
                        val conversation = uri?.let { accountService.getAccount(accountId)?.getByUri(it) }
                        if (conversation != null && uri != null && text != null) {
                            Log.d(TAG, "Reply to $conversationId")
                            conversationFacade.sendTextMessage(conversation, uri, text)
                            center.removeDeliveredNotificationsWithIdentifiers(listOf(request.identifier))
                        } else {
                            Log.w(TAG, "Reply action could not resolve conversation $conversationId")
                        }
                    }
                    ACTION_MARK_READ -> {
                        if (conversationId != null) {
                            Log.d(TAG, "Mark read: $conversationId")
                            conversationFacade.readMessages(accountId, Uri.fromString(conversationId))
                            center.removeDeliveredNotificationsWithIdentifiers(listOf(request.identifier))
                        }
                    }
                    ACTION_ACCEPT -> {
                        // The trust-request notification carries a conversation URI only when the
                        // account has exactly one pending request; otherwise there is nothing
                        // unambiguous to act on and the tap should just open the app.
                        val conversation = conversationId
                            ?.let { accountService.getAccount(accountId)?.getByUri(Uri.fromString(it)) }
                        if (conversation != null) {
                            Log.d(TAG, "Accepting trust request: $conversationId")
                            conversationFacade.acceptRequest(conversation)
                            center.removeDeliveredNotificationsWithIdentifiers(listOf(request.identifier))
                        } else {
                            Log.w(TAG, "Accept action without a resolvable conversation — opening app")
                        }
                    }
                    ACTION_REQUEST_DECLINE -> {
                        if (conversationId != null) {
                            Log.d(TAG, "Declining trust request: $conversationId")
                            conversationFacade.discardRequest(accountId, Uri.fromString(conversationId))
                            center.removeDeliveredNotificationsWithIdentifiers(listOf(request.identifier))
                        } else {
                            Log.w(TAG, "Decline action without a resolvable conversation")
                        }
                    }
                    UNNotificationDefaultActionIdentifier ->
                        Log.d(TAG, "Notification tapped — callId=$callId conversationId=$conversationId")
                    UNNotificationDismissActionIdentifier ->
                        Log.d(TAG, "Notification dismissed")
                    else ->
                        Log.w(TAG, "Unhandled action: ${didReceiveNotificationResponse.actionIdentifier}")
                }
            }
        }
        withCompletionHandler()
    }

}

private const val TAG = "IOSNotificationDelegate"
const val ACTION_ANSWER_CALL = "ANSWER_CALL"
const val ACTION_DECLINE_CALL = "DECLINE_CALL"
const val ACTION_REPLY_MESSAGE = "REPLY_MESSAGE"
const val ACTION_MARK_READ = "MARK_READ"
const val ACTION_ACCEPT = "ACCEPT_ACTION"
const val ACTION_REQUEST_DECLINE = "REQUEST_DECLINE_ACTION"
const val KEY_ACCOUNT_ID = "accountId"
const val KEY_CONVERSATION_ID = "conversationId"
const val KEY_CALL_ID = "callId"
const val KEY_CONFERENCE_ID = "conferenceId"
