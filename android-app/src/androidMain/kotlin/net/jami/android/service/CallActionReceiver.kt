package net.jami.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.os.Build
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
    private val notificationService: NotificationService by inject()

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

        confirmActionHaptic(context, intent.action)

        when (intent.action) {
            AndroidNotificationService.ACTION_ANSWER -> {
                Log.d(TAG, "Answer call: callId=$callId accountId=$accountId")
                // Honour the offered video flag from the call's media list —
                // mirrors CallViewModel.initIncoming video detection.
                val hasVideo = callService.getCall(callId)?.mediaList?.any {
                    it.mediaType == net.jami.model.Media.MediaType.MEDIA_TYPE_VIDEO
                } ?: false
                callService.accept(accountId, callId, hasVideo = hasVideo)
                // Dismiss the incoming call notification immediately rather than waiting for
                // the CURRENT state callback. Mirrors letsJam's CallNotificationReceiver
                // pattern: cancel first, then open the call screen. The ongoing-call
                // notification is re-posted by ConversationFacade once CURRENT fires.
                notificationService.removeCallNotification()
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
                // hangUpAny, not hangUp: the notification for a group call carries a conference,
                // and hanging up one participant of it would leave the call running.
                callService.hangUpAny(accountId, callId)
            }

            else -> Log.w(TAG, "Unknown action: ${intent.action}")
        }
    }

    companion object {
        private const val TAG = "CallActionReceiver"
        const val ACTION_VIEW_CALL = "net.jami.action.VIEW_CALL"
    }

    /**
     * A short haptic when a call is answered or declined from the notification.
     *
     * The in-call screen gets this for free from Compose's LocalHapticFeedback, but the
     * notification's buttons are handled here, outside any composition — so answering from
     * the shade, which is how a call is normally answered, gave no feedback at all while
     * hanging up inside the app did.
     *
     * Uses a predefined effect rather than a raw pattern so it matches the platform's own
     * confirm/reject feel, and checks the user's haptic setting by hand, which is the one
     * thing LocalHapticFeedback was doing for us.
     */
    private fun confirmActionHaptic(context: Context, action: String?) {
        val confirm = when (action) {
            AndroidNotificationService.ACTION_ANSWER -> true
            AndroidNotificationService.ACTION_DECLINE -> false
            else -> return
        }
        try {
            val enabled = android.provider.Settings.System.getInt(
                context.contentResolver,
                android.provider.Settings.System.HAPTIC_FEEDBACK_ENABLED,
                1,
            ) != 0
            if (!enabled) return
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                    as android.os.VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            }
            if (!vibrator.hasVibrator()) return
            val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                android.os.VibrationEffect.createPredefined(
                    if (confirm) android.os.VibrationEffect.EFFECT_CLICK
                    else android.os.VibrationEffect.EFFECT_DOUBLE_CLICK
                )
            } else {
                @Suppress("DEPRECATION")
                android.os.VibrationEffect.createOneShot(if (confirm) 20L else 40L, 128)
            }
            vibrator.vibrate(effect)
            Log.d(TAG, "Notification action haptic: action=$action confirm=$confirm")
        } catch (e: Exception) {
            Log.w(TAG, "Unable to play the notification action haptic", e)
        }
    }
}
