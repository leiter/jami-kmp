package net.jami.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver.PendingResult
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
        // goAsync() keeps the process alive while our coroutine runs; mandatory for
        // async work in BroadcastReceiver (otherwise Android may kill the process on return).
        val pendingResult = goAsync()

        when (intent.action) {
            AndroidNotificationService.ACTION_REPLY_MESSAGE -> {
                val conversationId = intent.getStringExtra(AndroidNotificationService.KEY_CONVERSATION_ID) ?: run {
                    Log.w(TAG, "NotificationActionReceiver: missing conversationId in intent for reply")
                    pendingResult.finish()
                    return
                }
                handleReply(context, accountId, conversationId, intent, pendingResult)
            }

            AndroidNotificationService.ACTION_MARK_READ -> {
                val conversationId = intent.getStringExtra(AndroidNotificationService.KEY_CONVERSATION_ID) ?: run {
                    Log.w(TAG, "NotificationActionReceiver: missing conversationId in intent for mark read")
                    pendingResult.finish()
                    return
                }
                handleMarkRead(context, accountId, conversationId, intent, pendingResult)
            }

            AndroidNotificationService.ACTION_ANSWER -> {
                val callId = intent.getStringExtra(AndroidNotificationService.KEY_CALL_ID) ?: run {
                    Log.w(TAG, "NotificationActionReceiver: missing callId in intent for answer call")
                    pendingResult.finish()
                    return
                }
                handleAnswerCall(context, callId, pendingResult)
            }

            AndroidNotificationService.ACTION_DECLINE -> {
                val callId = intent.getStringExtra(AndroidNotificationService.KEY_CALL_ID) ?: run {
                    Log.w(TAG, "NotificationActionReceiver: missing callId in intent for decline call")
                    pendingResult.finish()
                    return
                }
                handleDeclineCall(context, callId, pendingResult)
            }

            else -> {
                Log.w(TAG, "Unknown action: ${intent.action}")
                pendingResult.finish()
            }
        }
    }

    private fun handleReply(
        context: Context,
        accountId: String,
        conversationId: String,
        intent: Intent,
        pendingResult: PendingResult
    ) {
        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        val replyText = remoteInput?.getCharSequence(AndroidNotificationService.KEY_REPLY_TEXT)?.toString()

        if (replyText.isNullOrBlank()) {
            Log.w(TAG, "handleReply: empty reply text")
            pendingResult.finish()
            return
        }

        val notifId = intent.getIntExtra(AndroidNotificationService.KEY_NOTIFICATION_ID, -1)
        Log.d(TAG, "Replying to conversation $conversationId")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                daemonBridge.sendMessage(accountId, conversationId, replyText, "", 0)
                if (notifId != -1) cancelNotification(context, notifId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send reply message", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleMarkRead(
        context: Context,
        accountId: String,
        conversationId: String,
        intent: Intent,
        pendingResult: PendingResult
    ) {
        val notifId = intent.getIntExtra(AndroidNotificationService.KEY_NOTIFICATION_ID, -1)
        Log.d(TAG, "Marking conversation $conversationId as read")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                daemonBridge.setConversationPreferences(
                    accountId,
                    conversationId,
                    mapOf("read" to "true")
                )
                if (notifId != -1) cancelNotification(context, notifId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to mark conversation as read", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleAnswerCall(context: Context, callId: String, pendingResult: PendingResult) {
        Log.d(TAG, "Answering call $callId")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val accountId = callService.getCall(callId)?.account ?: return@launch
                callService.accept(accountId, callId)
                cancelCallNotification(context, callId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to answer call $callId", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleDeclineCall(context: Context, callId: String, pendingResult: PendingResult) {
        Log.d(TAG, "Declining call $callId")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val accountId = callService.getCall(callId)?.account ?: return@launch
                callService.refuse(accountId, callId)
                cancelCallNotification(context, callId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decline call $callId", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun cancelNotification(context: Context, notifId: Int) {
        NotificationManagerCompat.from(context).cancel(notifId)
        Log.d(TAG, "Cancelled notification: $notifId")
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
