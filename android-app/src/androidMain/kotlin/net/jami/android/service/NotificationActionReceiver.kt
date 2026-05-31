package net.jami.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.jami.services.AndroidNotificationService
import net.jami.services.CallService
import net.jami.services.DaemonBridge
import net.jami.utils.Log
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Receives REPLY_MESSAGE / MARK_READ / ANSWER_CALL / DECLINE_CALL broadcast intents from notifications.
 * Resolves [DaemonBridge] and [CallService] via Koin and dispatches the appropriate action.
 *
 * Mirrors the notification action handling pattern from MessageNotificationReceiver
 * in letsjam project.
 */
class NotificationActionReceiver : BroadcastReceiver(), KoinComponent {

    private val daemonBridge: DaemonBridge by inject()
    private val callService: CallService by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val accountId = intent.getStringExtra(AndroidNotificationService.KEY_ACCOUNT_ID) ?: run {
            Log.w(TAG, "NotificationActionReceiver: missing accountId in intent")
            return
        }

        when (intent.action) {
            AndroidNotificationService.ACTION_REPLY_MESSAGE -> {
                val conversationId = intent.getStringExtra(AndroidNotificationService.KEY_CONVERSATION_ID) ?: run {
                    Log.w(TAG, "NotificationActionReceiver: missing conversationId in intent for reply")
                    return
                }
                handleReply(context, accountId, conversationId, intent)
            }

            AndroidNotificationService.ACTION_MARK_READ -> {
                val conversationId = intent.getStringExtra(AndroidNotificationService.KEY_CONVERSATION_ID) ?: run {
                    Log.w(TAG, "NotificationActionReceiver: missing conversationId in intent for mark read")
                    return
                }
                handleMarkRead(context, accountId, conversationId)
            }

            AndroidNotificationService.ACTION_ANSWER -> {
                val callId = intent.getStringExtra(AndroidNotificationService.KEY_CALL_ID) ?: run {
                    Log.w(TAG, "NotificationActionReceiver: missing callId in intent for answer call")
                    return
                }
                handleAnswerCall(context, callId)
            }

            AndroidNotificationService.ACTION_DECLINE -> {
                val callId = intent.getStringExtra(AndroidNotificationService.KEY_CALL_ID) ?: run {
                    Log.w(TAG, "NotificationActionReceiver: missing callId in intent for decline call")
                    return
                }
                handleDeclineCall(context, callId)
            }

            else -> Log.w(TAG, "Unknown action: ${intent.action}")
        }
    }

    private fun handleReply(context: Context, accountId: String, conversationId: String, intent: Intent) {
        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        val replyText = remoteInput?.getCharSequence(AndroidNotificationService.KEY_REPLY_TEXT)?.toString()

        if (replyText.isNullOrBlank()) {
            Log.w(TAG, "handleReply: empty reply text")
            return
        }

        Log.d(TAG, "Replying to conversation $conversationId with message")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                daemonBridge.sendMessage(accountId, conversationId, replyText, "", 0)
                // Cancel notification after successful reply
                cancelMessageNotification(context, conversationId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send reply message", e)
            }
        }
    }

    private fun handleMarkRead(context: Context, accountId: String, conversationId: String) {
        Log.d(TAG, "Marking conversation $conversationId as read")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Mark conversation as read in daemon
                daemonBridge.setConversationPreferences(
                    accountId,
                    conversationId,
                    mapOf("read" to "true")
                )
                // Cancel notification after marking as read
                cancelMessageNotification(context, conversationId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to mark conversation as read", e)
            }
        }
    }

    private fun handleAnswerCall(context: Context, callId: String) {
        Log.d(TAG, "Answering call $callId")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val accountId = callService.getCall(callId)?.account ?: return@launch
                callService.accept(accountId, callId)
                cancelCallNotification(context, callId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to answer call $callId", e)
            }
        }
    }

    private fun handleDeclineCall(context: Context, callId: String) {
        Log.d(TAG, "Declining call $callId")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val accountId = callService.getCall(callId)?.account ?: return@launch
                callService.refuse(accountId, callId)
                cancelCallNotification(context, callId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decline call $callId", e)
            }
        }
    }

    private fun cancelMessageNotification(context: Context, conversationId: String) {
        val notificationManager = NotificationManagerCompat.from(context)
        val notifId = AndroidNotificationService.NOTIF_MESSAGE_BASE + conversationId.hashCode()
        notificationManager.cancel(notifId)
        Log.d(TAG, "Cancelled message notification: $notifId for conversation $conversationId")
    }

    private fun cancelCallNotification(context: Context, callId: String) {
        val notificationManager = NotificationManagerCompat.from(context)
        val notifId = AndroidNotificationService.NOTIF_CALL_BASE + callId.hashCode()
        notificationManager.cancel(notifId)
        Log.d(TAG, "Cancelled call notification: $notifId for call $callId")
    }

    companion object {
        private const val TAG = "NotificationActionReceiver"
    }
}
