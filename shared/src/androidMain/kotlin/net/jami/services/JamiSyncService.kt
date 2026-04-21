/*
 *  Copyright (C) 2004-2025 Savoir-faire Linux Inc.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package net.jami.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import net.jami.utils.Log

/**
 * Android foreground service for background synchronization.
 *
 * Keeps the daemon alive when the app is backgrounded, enabling DHT sync to continue.
 * Uses a foreground service with DATA_SYNC type notification (Android Q+).
 *
 * Reference: jami-android-client SyncService.kt
 */
class JamiSyncService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var serviceUsers = 0
    private var notification: Notification? = null
    private var timeoutRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}")

        when (intent?.action) {
            ACTION_START -> {
                startSync(intent)
            }
            ACTION_STOP -> {
                stopSync()
            }
        }

        return START_NOT_STICKY
    }

    private fun startSync(intent: Intent) {
        if (notification == null) {
            notification = createNotification()
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification!!,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            Log.d(TAG, "Foreground service started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service", e)
        }

        serviceUsers++
        Log.d(TAG, "Service users: $serviceUsers")

        // Handle timeout
        val timeout = intent.getLongExtra(EXTRA_TIMEOUT, -1)
        if (timeout > 0) {
            timeoutRunnable?.let { handler.removeCallbacks(it) }
            timeoutRunnable = Runnable { stopSync() }
            handler.postDelayed(timeoutRunnable!!, timeout)
            Log.d(TAG, "Sync timeout scheduled: ${timeout}ms")
        }
    }

    private fun stopSync() {
        serviceUsers--
        Log.d(TAG, "stopSync called, remaining users: $serviceUsers")

        if (serviceUsers <= 0) {
            serviceUsers = 0
            timeoutRunnable?.let { handler.removeCallbacks(it) }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
                Log.d(TAG, "Service stopped")
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Service already stopped", e)
            }

            notification = null
        }
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w(TAG, "onTimeout: startId=$startId, fgsType=$fgsType")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            stopSelf()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Error in onTimeout", e)
        }
        notification = null
        serviceUsers = 0
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        timeoutRunnable?.let { handler.removeCallbacks(it) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background synchronization"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        // Create intent to stop the service when notification is dismissed
        val deleteIntent = Intent(ACTION_STOP)
            .setClass(applicationContext, JamiSyncService::class.java)

        val deletePendingIntent = PendingIntent.getService(
            applicationContext,
            0,
            deleteIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Create intent to open app when notification is tapped
        val contentIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val contentPendingIntent = contentIntent?.let {
            PendingIntent.getActivity(
                applicationContext,
                0,
                it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Jami Sync")
            .setContentText("Syncing conversations in background")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(false)
            .setOngoing(true)
            .setVibrate(null)
            .setSound(null)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOnlyAlertOnce(true)
            .setDeleteIntent(deletePendingIntent)
            .setContentIntent(contentPendingIntent)
            .build()
    }

    companion object {
        private const val TAG = "JamiSyncService"
        private const val NOTIFICATION_ID = 1004
        private const val CHANNEL_ID = "jami_sync_channel"
        private const val CHANNEL_NAME = "Jami Sync"

        const val ACTION_START = "net.jami.SYNC_START"
        const val ACTION_STOP = "net.jami.SYNC_STOP"
        const val EXTRA_TIMEOUT = "timeout"
    }
}
