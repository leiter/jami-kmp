package net.jami.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import net.jami.android.MainActivity
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
 * Uses foreground service type DATA_SYNC (no privileged role required).
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
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Jami")
            .setContentText("Ready to receive calls and messages")
            .setContentIntent(tapIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setSilent(true)
            .build()

        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0

        ServiceCompat.startForeground(this, NOTIF_ID, notification, serviceType)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Jami background service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps Jami ready to receive calls"
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "JamiDaemonService"
        private const val CHANNEL_ID = "jami_daemon_service"
        private const val NOTIF_ID = 1  // lowest-priority persistent slot
    }
}
