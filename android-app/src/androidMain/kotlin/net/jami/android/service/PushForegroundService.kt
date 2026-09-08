package net.jami.android.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import net.jami.android.R
import net.jami.utils.Log

/**
 * Short-lived foreground service started when a high-priority call push arrives while the app
 * is in the background.
 *
 * It exists purely to lift background execution and network restrictions for the few seconds
 * the daemon needs to reconnect and negotiate the incoming call; it stops itself after
 * [TIMEOUT_MS] so it never lingers as a second permanent notification alongside
 * [JamiDaemonService].
 *
 * Uses remoteMessaging on Android 14+ — this is exactly the type's intended purpose, and unlike
 * dataSync it is not on the Android 15 BOOT_COMPLETED blocklist (see
 * `doc/play-console-special-use-justification.md`).
 */
class PushForegroundService : Service() {

    private val handler = Handler(Looper.getMainLooper())

    private val stopRunnable = Runnable {
        Log.d(TAG, "Push window elapsed, stopping")
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A push can hit a cold process, so the daemon service may never have run.
        JamiDaemonService.ensureChannel(this)

        val notification = NotificationCompat.Builder(this, JamiDaemonService.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_jami_24)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notif_push_sync))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()

        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0

        ServiceCompat.startForeground(this, NOTIF_ID, notification, serviceType)

        // Restart the window on every push rather than stacking timers.
        handler.removeCallbacks(stopRunnable)
        handler.postDelayed(stopRunnable, TIMEOUT_MS)

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(stopRunnable)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PushForegroundService"
        private const val TIMEOUT_MS = 5_000L
        const val NOTIF_ID = 2001
    }
}
