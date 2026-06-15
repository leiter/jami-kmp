package net.jami.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
                        if (conversationId != null && text != null) {
                            Log.d(TAG, "Reply to $conversationId: $text")
                            // Push reply not yet integrated — push notifications are a known gap
                            center.removeDeliveredNotificationsWithIdentifiers(listOf(request.identifier))
                        }
                    }
                    ACTION_MARK_READ -> {
                        if (conversationId != null) {
                            Log.d(TAG, "Mark read: $conversationId")
                            center.removeDeliveredNotificationsWithIdentifiers(listOf(request.identifier))
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
const val KEY_ACCOUNT_ID = "accountId"
const val KEY_CONVERSATION_ID = "conversationId"
const val KEY_CALL_ID = "callId"
