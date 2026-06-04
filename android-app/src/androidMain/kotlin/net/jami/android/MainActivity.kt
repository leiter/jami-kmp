package net.jami.android

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import net.jami.android.service.CallActionReceiver
import net.jami.services.AccountService
import net.jami.services.AndroidPictureInPictureManager
import net.jami.services.CallService
import net.jami.services.NotificationService
import net.jami.services.SyncManager
import net.jami.services.expect.HardwareService
import net.jami.ui.JamiApp
import net.jami.utils.Log
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val syncManager: SyncManager by inject()
    private val accountService: AccountService by inject()
    private val callService: CallService by inject()
    private val hardwareService: HardwareService by inject()
    private val pipManager: AndroidPictureInPictureManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pipManager.attachActivity(this)
        setContent {
            JamiApp()
        }
        handleCallIntent(intent)
        lifecycleScope.launch {
            hardwareService.screenShareRequest.collect {
                requestScreenSharePermission()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleCallIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        pipManager.detachActivity()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Enter PiP mode when user navigates away during an active call
        pipManager.enterPipMode()
    }

    override fun onPictureInPictureModeChanged(isInPipMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPipMode)
        pipManager.onPipModeChanged(isInPipMode)
    }

    override fun onStop() {
        super.onStop()
        val currentAccount = accountService.currentAccount.value
        if (currentAccount != null) {
            Log.d(TAG, "App backgrounded, starting background sync for account: ${currentAccount.accountId}")
            syncManager.startBackgroundSyncWithTimeout(timeoutMs = 2 * 60 * 60 * 1000L)
        }
    }

    override fun onStart() {
        super.onStart()
        if (syncManager.isBackgroundSyncActive) {
            Log.d(TAG, "App foregrounded, stopping background sync")
            syncManager.stopBackgroundSync()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQUEST_SCREEN_SHARE -> {
                if (resultCode == RESULT_OK && data != null) {
                    val mediaProjection = (getSystemService(MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager)
                        ?.getMediaProjection(resultCode, data)
                    if (mediaProjection != null) {
                        hardwareService.setPendingScreenShareProjection(mediaProjection)
                        Log.d(TAG, "Screen share permission granted, projection stored")
                    }
                } else {
                    Log.w(TAG, "Screen share permission denied")
                }
            }
        }
    }

    /**
     * Routes incoming call intents to [CallService] and enables lock-screen display.
     * Mirrors CallActivity.handleNewIntent() from jami-android-client.
     *
     * Currently: accepts or views the call via CallService directly.
     * The navigation graph picks up the active call via CallService.currentCalls.
     */
    private fun handleCallIntent(intent: Intent?) {
        val callId = intent?.getStringExtra(NotificationService.KEY_CALL_ID) ?: return
        enableLockScreenDisplay()

        val accountId = intent.getStringExtra(NotificationService.KEY_ACCOUNT_ID)

        when (intent.action) {
            CallActionReceiver.ACTION_VIEW_CALL -> {
                Log.d(TAG, "VIEW_CALL: callId=$callId")
                callService.setPendingCallNavId(callId)
            }

            ACTION_ACCEPT_CALL -> {
                Log.d(TAG, "ACCEPT_CALL: callId=$callId")
                val resolvedAccount = accountId
                    ?: callService.getCall(callId)?.account
                    ?: return
                callService.accept(resolvedAccount, callId, hasVideo = false)
                callService.setPendingCallNavId(callId)
            }
        }
    }

    private fun enableLockScreenDisplay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun requestScreenSharePermission() {
        val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        if (mediaProjectionManager != null) {
            startActivityForResult(
                mediaProjectionManager.createScreenCaptureIntent(),
                REQUEST_SCREEN_SHARE
            )
            Log.d(TAG, "Screen share permission request started")
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        const val ACTION_ACCEPT_CALL = "net.jami.action.ACCEPT_CALL"
        const val REQUEST_SCREEN_SHARE = 1001
    }
}
