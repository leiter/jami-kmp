package net.jami.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import net.jami.android.MainActivity
import net.jami.android.R
import net.jami.utils.Log

/**
 * Persistent foreground service that keeps the Jami daemon process alive.
 *
 * Started by [BootReceiver] after device reboot and restarted automatically
 * if the OS kills it (START_STICKY). The daemon itself is initialised by
 * [net.jami.android.JamiApplication.onCreate], which runs whenever the
 * process is created — this service's sole job is to prevent the process
 * from being reaped when the UI is not visible.
 *
 * Uses foreground service type SPECIAL_USE on Android 14+ (falling back to DATA_SYNC
 * on older releases). DATA_SYNC is disallowed from a BOOT_COMPLETED receiver on Android 15+,
 * and SPECIAL_USE is the type that honestly describes this service: keeping the P2P daemon
 * reachable when there is no push infrastructure. See
 * `doc/play-console-special-use-justification.md`.
 */
class JamiDaemonService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        startForegroundWithNotification()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundWithNotification() {
        createChannel()

        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_jami_24)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notif_background_service))
            .setContentIntent(tapIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setSilent(true)
            .build()

        // Android 15+ blocks dataSync (among others) from a BOOT_COMPLETED receiver, so the
        // pre-34 constant cannot be used on the boot path of a modern release. remoteMessaging
        // is *not* blocked and is the documented fallback if Play review rejects specialUse.
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0

        ServiceCompat.startForeground(this, NOTIF_ID, notification, serviceType)
    }

    private fun createChannel() = ensureChannel(this)

    companion object {
        private const val TAG = "JamiDaemonService"

        /**
         * Create the background-service channel if it does not exist yet.
         *
         * Shared with [PushForegroundService], which posts on the same channel and can start
         * before this service ever has — a push can arrive at a cold process.
         */
        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_channel_background_service),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notif_channel_background_service_descr)
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }

        // v2: forces channel recreation at IMPORTANCE_LOW (old channel was created at IMPORTANCE_MIN)
        const val CHANNEL_ID = "jami_daemon_service_v2"
        const val NOTIF_ID = 1
    }
}
