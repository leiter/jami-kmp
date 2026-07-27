package net.jami.android.push

import android.app.ActivityManager
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import net.jami.android.service.PushForegroundService

/**
 * Receives FCM pushes and hands them to the daemon.
 *
 * A Jami push carries no content — it is a wake-up telling the daemon to reconnect to the DHT
 * and pull whatever is waiting. The work therefore happens inside libjami after
 * [PushServiceManager.onPushReceived]; this class only has to make sure the process stays alive
 * and unthrottled long enough for that to finish.
 */
class JamiFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // Deprecated, but without it some devices doze mid-negotiation and the call never
        // rings. The upstream Android client keeps it for the same reason.
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "jami:push").apply {
                setReferenceCounted(false)
                acquire(WAKE_LOCK_TIMEOUT_MS)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire wake lock", e)
        }

        // A high-priority call push has to negotiate media before the ~10s FCM budget runs out.
        // Promoting to a foreground service buys that time (and lifts background network limits)
        // while the app itself is not visible.
        val pushType = remoteMessage.data[KEY_PUSH_TYPE].orEmpty()
        val isCallPush = (pushType.contains("audioCall") || pushType.contains("videoCall")) &&
                remoteMessage.priority == RemoteMessage.PRIORITY_HIGH

        if (isCallPush && !isAppInForeground()) {
            // startForegroundService must be called from the main thread on some OEM builds.
            Handler(Looper.getMainLooper()).post {
                try {
                    startForegroundService(Intent(this, PushForegroundService::class.java))
                } catch (e: Exception) {
                    Log.e(TAG, "Could not start the push foreground service", e)
                }
            }
        }

        try {
            PushServiceManager.onPushReceived(remoteMessage.from.orEmpty(), remoteMessage.data)
        } catch (e: Exception) {
            Log.e(TAG, "Error while processing push", e)
        }
    }

    override fun onNewToken(refreshedToken: String) {
        Log.i(TAG, "FCM token refreshed")
        PushServiceManager.onTokenAvailable(refreshedToken)
    }

    private fun isAppInForeground(): Boolean {
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val procs = am.runningAppProcesses ?: return false
        val pid = Process.myPid()
        return procs.any {
            it.pid == pid && (
                it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
                    it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
                )
        }
    }

    companion object {
        private const val TAG = "JamiFcm"
        private const val KEY_PUSH_TYPE = "pt"
        private const val WAKE_LOCK_TIMEOUT_MS = 10_000L
    }
}
