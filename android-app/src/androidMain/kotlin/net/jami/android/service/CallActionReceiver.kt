package net.jami.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import net.jami.services.AndroidNotificationService
import net.jami.services.CallService
import net.jami.services.NotificationService
import net.jami.utils.Log
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Receives ANSWER / DECLINE / HANGUP broadcast intents from call notifications.
 * Resolves [CallService] via Koin and dispatches the appropriate action.
 *
 * Mirrors the notification action handling in cx.ring.service.NotificationServiceImpl
 * from jami-android-client.
 */
class CallActionReceiver : BroadcastReceiver(), KoinComponent {

    private val callService: CallService by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val callId = intent.getStringExtra(NotificationService.KEY_CALL_ID) ?: run {
            Log.w(TAG, "CallActionReceiver: missing callId in intent")
            return
        }
        // accountId is now embedded directly in the intent so we can act even if the
        // in-memory call map no longer holds the call (e.g. ended between notification
        // display and button press, or confId/daemonId mismatch).
        val accountId = intent.getStringExtra(NotificationService.KEY_ACCOUNT_ID)
            ?: callService.getCall(callId)?.account
            ?: run {
                Log.w(TAG, "CallActionReceiver: no accountId for callId=$callId")
                return
            }

        when (intent.action) {
            AndroidNotificationService.ACTION_ANSWER -> {
                Log.d(TAG, "Answer call: callId=$callId accountId=$accountId")
                // Honour the offered video flag from the call's media list —
                // mirrors CallViewModel.initIncoming video detection.
                val hasVideo = callService.getCall(callId)?.mediaList?.any {
                    it.mediaType == net.jami.model.Media.MediaType.MEDIA_TYPE_VIDEO
                } ?: false
                callService.accept(accountId, callId, hasVideo = hasVideo)
                val viewIntent = Intent(context, Class.forName("net.jami.android.MainActivity")).apply {
                    action = ACTION_VIEW_CALL
                    putExtra(NotificationService.KEY_CALL_ID, callId)
                    putExtra(NotificationService.KEY_ACCOUNT_ID, accountId)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                context.startActivity(viewIntent)
            }

            AndroidNotificationService.ACTION_DECLINE -> {
                Log.d(TAG, "Decline call: callId=$callId accountId=$accountId")
                callService.refuse(accountId, callId)
            }

            AndroidNotificationService.ACTION_HANGUP -> {
                Log.d(TAG, "Hangup call: callId=$callId accountId=$accountId")
                callService.hangUp(accountId, callId)
            }

            else -> Log.w(TAG, "Unknown action: ${intent.action}")
        }
    }

    companion object {
        private const val TAG = "CallActionReceiver"
        const val ACTION_VIEW_CALL = "net.jami.action.VIEW_CALL"
    }
}
