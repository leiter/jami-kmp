package net.jami.android.service

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.jami.services.AndroidNotificationService
import net.jami.services.CallService
import net.jami.services.NotificationService
import net.jami.utils.Log
import org.koin.android.ext.android.inject

/**
 * Foreground service that keeps an active call alive when the app is backgrounded.
 * Declares foreground service types phoneCall + microphone, matching what the Android
 * framework requires since API 29+ for call audio to continue in background.
 *
 * Mirrors cx.ring.service.CallNotificationService from jami-android-client.
 */
class CallNotificationService : Service() {

    private val callService: CallService by inject()
    private val notificationService: NotificationService by inject()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notifId = AndroidNotificationService.NOTIF_CALL_BASE

        val notification = notificationService.showCallNotification(notifId)
            ?: run {
                Log.w(TAG, "No call notification available — stopping service")
                stopSelf()
                return START_NOT_STICKY
            }

        val serviceType = computeServiceType()
        ServiceCompat.startForeground(this, notifId, notification as android.app.Notification, serviceType)

        // Stop when all calls end
        scope.launch {
            callService.currentCalls.collect { calls ->
                if (calls.isEmpty()) {
                    Log.d(TAG, "No active calls — stopping foreground service")
                    ServiceCompat.stopForeground(this@CallNotificationService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun computeServiceType(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0

        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (hasPermission(Manifest.permission.FOREGROUND_SERVICE_MICROPHONE) &&
                hasPermission(Manifest.permission.RECORD_AUDIO)) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
        } else {
            if (hasPermission(Manifest.permission.RECORD_AUDIO)) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
        }

        return type
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "CallNotificationService"
    }
}
