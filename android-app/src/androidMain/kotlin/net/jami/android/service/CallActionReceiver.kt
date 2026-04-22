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
        val call = callService.getCall(callId) ?: run {
            Log.w(TAG, "CallActionReceiver: call not found for id=$callId")
            return
        }
        val accountId = call.account

        when (intent.action) {
            AndroidNotificationService.ACTION_ANSWER -> {
                Log.d(TAG, "Answer call: $callId")
                callService.accept(accountId, callId, hasVideo = false)
                // Bring the app to the foreground so the CallScreen is visible
                val viewIntent = Intent(context, Class.forName("net.jami.android.MainActivity")).apply {
                    action = ACTION_VIEW_CALL
                    putExtra(NotificationService.KEY_CALL_ID, callId)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                context.startActivity(viewIntent)
            }

            AndroidNotificationService.ACTION_DECLINE -> {
                Log.d(TAG, "Decline call: $callId")
                callService.refuse(accountId, callId)
            }

            AndroidNotificationService.ACTION_HANGUP -> {
                Log.d(TAG, "Hangup call: $callId")
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
