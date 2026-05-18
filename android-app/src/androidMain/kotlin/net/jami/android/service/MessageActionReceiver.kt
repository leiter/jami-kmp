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
import net.jami.services.DaemonBridge
import net.jami.utils.Log
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Receives REPLY_MESSAGE / MARK_READ broadcast intents from message notifications.
 * Resolves [DaemonBridge] via Koin and dispatches the appropriate action.
 *
 * Mirrors the notification action handling pattern from MessageNotificationReceiver
 * in letsjam project.
 */
class MessageActionReceiver : BroadcastReceiver(), KoinComponent {

    private val daemonBridge: DaemonBridge by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val accountId = intent.getStringExtra(AndroidNotificationService.KEY_ACCOUNT_ID) ?: run {
            Log.w(TAG, "MessageActionReceiver: missing accountId in intent")
            return
        }
        val conversationId = intent.getStringExtra(AndroidNotificationService.KEY_CONVERSATION_ID) ?: run {
            Log.w(TAG, "MessageActionReceiver: missing conversationId in intent")
            return
        }

        when (intent.action) {
            AndroidNotificationService.ACTION_REPLY_MESSAGE -> {
                handleReply(context, accountId, conversationId, intent)
            }

            AndroidNotificationService.ACTION_MARK_READ -> {
                handleMarkRead(context, accountId, conversationId)
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
                daemonBridge.sendMessage(accountId, conversationId, replyText)
                // Cancel notification after successful reply
                cancelNotification(context, conversationId)
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
                cancelNotification(context, conversationId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to mark conversation as read", e)
            }
        }
    }

    private fun cancelNotification(context: Context, conversationId: String) {
        val notificationManager = NotificationManagerCompat.from(context)
        val conversationKey = conversationId
        val notifId = AndroidNotificationService.NOTIF_MESSAGE_BASE + conversationKey.hashCode()
        notificationManager.cancel(notifId)
        Log.d(TAG, "Cancelled notification: $notifId")
    }

    companion object {
        private const val TAG = "MessageActionReceiver"
    }
}
