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

import android.app.Activity
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import jami_kmp.shared.generated.resources.Res
import jami_kmp.shared.generated.resources.notif_screen_sharing
import kotlinx.coroutines.runBlocking
import net.jami.services.expect.HardwareService
import net.jami.utils.Log
import org.jetbrains.compose.resources.getString
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Foreground service that holds the `mediaProjection` type for the duration of a screen share.
 *
 * **Why it exists.** From API 34 the platform refuses to redeem a screen-capture consent token
 * unless a foreground service of type `FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION` is *already*
 * running: `MediaProjectionManager.getMediaProjection()` throws
 * `SecurityException: Media projections require a foreground service of type
 * ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION`. `targetSdk` is 36, so screen sharing
 * could not work on any recent Android before this service existed — the call sat directly in
 * `MainActivity.onActivityResult`.
 *
 * **Why the projection is redeemed here rather than in the activity.** `startForegroundService()`
 * is asynchronous: the activity cannot know when `startForeground()` has actually run, so
 * redeeming the token on the activity side is a race it loses on a slow device. Passing the
 * consent result into the service and redeeming it *after* `startForeground()` removes the race
 * entirely — the ordering is a straight line through one `onStartCommand`.
 *
 * **Why `mediaProjection` is not simply added to `CallNotificationService`.** That service starts
 * for every call, long before any capture session exists, and the framework requires the runtime
 * type to be backed by a real projection — see the comment on its manifest entry.
 *
 * The service stops itself when the projection stops, which covers both ends: the user revoking
 * the share from the system UI, and `CameraService.closeCamera()` stopping it when the call
 * switches back to a camera. [UNCONSUMED_TIMEOUT_MS] covers the third case, where consent is
 * granted but `startCapture` never picks the projection up (no preview surface, or the call ended
 * while the picker was open) — without it the notification would outlive the share.
 */
class ScreenShareService : Service(), KoinComponent {

    private val hardwareService: HardwareService by inject()

    private val handler = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var unconsumedCheck: Runnable? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Log.d(TAG, "onStartCommand: ACTION_STOP")
            stopProjectionAndSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        @Suppress("DEPRECATION")
        val resultData: Intent? = intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        if (resultCode != Activity.RESULT_OK || resultData == null) {
            Log.w(TAG, "onStartCommand: no consent result to redeem — stopping")
            stopSelf()
            return START_NOT_STICKY
        }

        // Order matters: foreground first, token second. Reversing these is the bug this
        // service exists to fix.
        if (!enterForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }

        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        val newProjection = try {
            manager?.getMediaProjection(resultCode, resultData)
        } catch (e: Exception) {
            // SecurityException if the foreground state is somehow still not accepted, and
            // IllegalStateException if the token was already redeemed once.
            Log.e(TAG, "getMediaProjection failed", e)
            null
        }
        if (newProjection == null) {
            Log.w(TAG, "onStartCommand: projection could not be obtained — stopping")
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }

        projection = newProjection
        // Registered before the projection is handed over: on API 34+ a callback must exist
        // before createVirtualDisplay(), and CameraService registers its own in addition.
        newProjection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Log.d(TAG, "MediaProjection.onStop — screen share over, stopping service")
                projection = null
                handler.post { stopProjectionAndSelf() }
            }
        }, handler)

        hardwareService.setPendingScreenShareProjection(newProjection)
        Log.d(TAG, "screen share projection ready and handed to HardwareService")

        unconsumedCheck?.let { handler.removeCallbacks(it) }
        unconsumedCheck = Runnable {
            if (hardwareService.isScreenShareProjectionPending()) {
                Log.w(TAG, "projection never consumed within ${UNCONSUMED_TIMEOUT_MS}ms — stopping")
                hardwareService.setPendingScreenShareProjection(null)
                stopProjectionAndSelf()
            }
        }.also { handler.postDelayed(it, UNCONSUMED_TIMEOUT_MS) }

        return START_NOT_STICKY
    }

    private fun enterForeground(): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                createNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
        Log.d(TAG, "foreground service started with mediaProjection type")
        true
    } catch (e: Exception) {
        Log.e(TAG, "could not start the mediaProjection foreground service", e)
        false
    }

    private fun stopProjectionAndSelf() {
        unconsumedCheck?.let { handler.removeCallbacks(it) }
        unconsumedCheck = null
        projection?.let {
            projection = null
            try {
                it.stop()
            } catch (e: Exception) {
                Log.w(TAG, "projection.stop() failed", e)
            }
        }
        stopForegroundCompat()
        stopSelf()
    }

    private fun stopForegroundCompat() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (e: IllegalStateException) {
            Log.w(TAG, "service already out of foreground", e)
        }
    }

    private fun createNotification(): Notification =
        NotificationCompat.Builder(this, AndroidNotificationService.CHANNEL_SERVICE)
            .setSmallIcon(
                resources.getIdentifier("ic_jami_24", "drawable", packageName)
                    .takeIf { it != 0 } ?: android.R.drawable.ic_dialog_info
            )
            .setContentText(runBlocking { getString(Res.string.notif_screen_sharing) })
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        unconsumedCheck?.let { handler.removeCallbacks(it) }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ScreenShareService"

        /** Distinct from NOTIF_SERVICE (1), NOTIF_CALL_BASE (1000) and JamiSyncService (1004). */
        private const val NOTIFICATION_ID = 1005

        /**
         * How long a granted projection may sit unused before it is given up. Longer than
         * `CallViewModel.SCREEN_SHARE_PERMISSION_TIMEOUT_MS`, so the view model's own give-up
         * path runs first in the ordinary denial case.
         */
        private const val UNCONSUMED_TIMEOUT_MS = 60_000L

        private const val ACTION_STOP = "net.jami.SCREEN_SHARE_STOP"
        private const val EXTRA_RESULT_CODE = "net.jami.extra.RESULT_CODE"
        private const val EXTRA_RESULT_DATA = "net.jami.extra.RESULT_DATA"

        /**
         * Hands the screen-capture consent result to the service, which redeems it once it is
         * in the foreground. Call this instead of `getMediaProjection()` — see the class comment.
         */
        fun start(context: Context, resultCode: Int, resultData: Intent) {
            val intent = Intent(context, ScreenShareService::class.java)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, resultData)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** Stops the share and the service. Safe to call when neither is running. */
        fun stop(context: Context) {
            val intent = Intent(context, ScreenShareService::class.java).setAction(ACTION_STOP)
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "could not deliver ACTION_STOP", e)
            }
        }
    }
}
