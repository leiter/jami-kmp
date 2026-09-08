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
        val actionIdentifier = didReceiveNotificationResponse.actionIdentifier
        val replyText = (didReceiveNotificationResponse as? UNTextInputNotificationResponse)?.userText

        // Only the extraction from Objective-C happens here; the action mapping lives in
        // dispatchNotificationAction so it can be tested without a UNNotificationResponse.
        scope.launch {
            val dismiss = dispatchNotificationAction(
                actionIdentifier = actionIdentifier,
                accountId = userInfo[KEY_ACCOUNT_ID] as? String,
                callId = userInfo[KEY_CALL_ID] as? String,
                conversationId = userInfo[KEY_CONVERSATION_ID] as? String,
                replyText = replyText,
                actions = serviceActions,
            )
            if (dismiss) {
                center.removeDeliveredNotificationsWithIdentifiers(listOf(request.identifier))
            }
        }
        withCompletionHandler()
    }

    /** Binds the dispatch to the real services. */
    private val serviceActions = object : NotificationActions {
        override suspend fun answerCall(accountId: String, callId: String) {
            Log.d(TAG, "Answering call: $callId")
            callService.accept(accountId, callId, hasVideo = false)
        }

        override suspend fun declineCall(accountId: String, callId: String) {
            Log.d(TAG, "Declining call: $callId")
            callService.refuse(accountId, callId)
        }

        override suspend fun reply(accountId: String, conversationId: String, text: String) {
            val uri = Uri.fromString(conversationId)
            val conversation = accountService.getAccount(accountId)?.getByUri(uri)
            if (conversation == null) {
                Log.w(TAG, "Reply could not resolve conversation $conversationId")
                return
            }
            Log.d(TAG, "Reply to $conversationId")
            conversationFacade.sendTextMessage(conversation, uri, text)
        }

        override suspend fun markRead(accountId: String, conversationId: String) {
            Log.d(TAG, "Mark read: $conversationId")
            conversationFacade.readMessages(accountId, Uri.fromString(conversationId))
        }

        override suspend fun acceptRequest(accountId: String, conversationId: String) {
            val conversation = accountService.getAccount(accountId)
                ?.getByUri(Uri.fromString(conversationId))
            if (conversation == null) {
                Log.w(TAG, "Accept could not resolve conversation $conversationId")
                return
            }
            Log.d(TAG, "Accepting trust request: $conversationId")
            conversationFacade.acceptRequest(conversation)
        }

        override suspend fun declineRequest(accountId: String, conversationId: String) {
            Log.d(TAG, "Declining trust request: $conversationId")
            conversationFacade.discardRequest(accountId, Uri.fromString(conversationId))
        }
    }

}

/**
 * The side effects a notification action can perform.
 *
 * Extracted so [dispatchNotificationAction] can be exercised without constructing a
 * UNNotificationResponse, which has no public initialiser and cannot be built in a test.
 * The action/key mapping is the part worth testing — a mismatch between the keys written
 * into a notification's userInfo and the keys read back here silently disabled every
 * action button once before.
 */
internal interface NotificationActions {
    suspend fun answerCall(accountId: String, callId: String)
    suspend fun declineCall(accountId: String, callId: String)
    suspend fun reply(accountId: String, conversationId: String, text: String)
    suspend fun markRead(accountId: String, conversationId: String)
    suspend fun acceptRequest(accountId: String, conversationId: String)
    suspend fun declineRequest(accountId: String, conversationId: String)
}

/**
 * Maps a notification action to its effect.
 *
 * @return true when the notification should be dismissed afterwards.
 */
internal suspend fun dispatchNotificationAction(
    actionIdentifier: String,
    accountId: String?,
    callId: String?,
    conversationId: String?,
    replyText: String?,
    actions: NotificationActions,
): Boolean {
    // Every action is account-scoped; without an accountId there is nothing to act on.
    if (accountId == null) {
        Log.w(TAG, "Notification action $actionIdentifier without an accountId — ignoring")
        return false
    }
    return when (actionIdentifier) {
        ACTION_ANSWER_CALL -> {
            if (callId == null) return false
            actions.answerCall(accountId, callId)
            false // the call UI takes over; leave the notification to the system
        }
        ACTION_DECLINE_CALL -> {
            if (callId == null) return false
            actions.declineCall(accountId, callId)
            true
        }
        ACTION_REPLY_MESSAGE -> {
            val text = replyText?.takeIf { it.isNotBlank() } ?: return false
            if (conversationId == null) return false
            actions.reply(accountId, conversationId, text)
            true
        }
        ACTION_MARK_READ -> {
            if (conversationId == null) return false
            actions.markRead(accountId, conversationId)
            true
        }
        ACTION_ACCEPT -> {
            if (conversationId == null) {
                // The trust-request notification only carries a conversation when the account
                // has exactly one pending request; otherwise the tap should just open the app.
                Log.w(TAG, "Accept without a resolvable conversation — opening app")
                return false
            }
            actions.acceptRequest(accountId, conversationId)
            true
        }
        ACTION_REQUEST_DECLINE -> {
            if (conversationId == null) return false
            actions.declineRequest(accountId, conversationId)
            true
        }
        UNNotificationDefaultActionIdentifier -> false
        UNNotificationDismissActionIdentifier -> false
        else -> {
            Log.w(TAG, "Unhandled action: $actionIdentifier")
            false
        }
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
