package net.jami.android

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.jami.android.service.CallActionReceiver
import net.jami.android.service.JamiDaemonService
import net.jami.services.AccountService
import net.jami.services.AndroidPictureInPictureManager
import net.jami.services.CallService
import net.jami.services.NotificationService
import net.jami.services.SyncManager
import net.jami.services.expect.HardwareService
import net.jami.ui.JamiApp
import net.jami.ui.navigation.ShareState
import net.jami.utils.Log
import org.koin.android.ext.android.inject
import java.io.File

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
        ContextCompat.startForegroundService(this, Intent(this, JamiDaemonService::class.java))
        setContent {
            JamiApp()
        }
        handleCallIntent(intent)
        handleShareIntent(intent)
        lifecycleScope.launch {
            hardwareService.screenShareRequest.collect {
                requestScreenSharePermission()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleCallIntent(intent)
        handleShareIntent(intent)
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
                val call = callService.getCall(callId)
                val resolvedAccount = accountId ?: call?.account ?: return
                // Honour the offered video flag from the call's media list, matching the
                // in-app IncomingCallScreen behaviour (CallViewModel.initIncoming).
                val hasVideo = call?.mediaList?.any {
                    it.mediaType == net.jami.model.Media.MediaType.MEDIA_TYPE_VIDEO
                } ?: false
                callService.accept(resolvedAccount, callId, hasVideo = hasVideo)
                callService.setPendingCallNavId(callId)
            }
        }
    }

    /**
     * Handles ACTION_SEND / ACTION_SEND_MULTIPLE from the Android share sheet.
     * Copies content URIs to cache so the daemon can read them by file path,
     * stores the payload in [ShareState], then signals navigation to SharePickerScreen.
     */
    private fun handleShareIntent(intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return

        val mimeType = intent.type ?: "*/*"
        ShareState.mimeType = mimeType

        when {
            action == Intent.ACTION_SEND && mimeType == "text/plain" -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
                ShareState.pendingText = text
            }
            action == Intent.ACTION_SEND -> {
                val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                val path = uri?.let { copyUriToCache(it, mimeType) } ?: return
                ShareState.pendingFilePaths = listOf(path)
            }
            action == Intent.ACTION_SEND_MULTIPLE -> {
                val uris: ArrayList<Uri>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                }
                val paths = uris?.mapNotNull { copyUriToCache(it, mimeType) } ?: return
                if (paths.isEmpty()) return
                ShareState.pendingFilePaths = paths
            }
            else -> return
        }

        ShareState.signalSharePicker()
    }

    private fun copyUriToCache(uri: Uri, mimeType: String): String? {
        return try {
            val ext = mimeType.substringAfter("/")
                .takeIf { it.isNotEmpty() && it != "*" && !it.contains("/") }
                ?.let { ".$it" } ?: ""
            val cacheFile = File(cacheDir, "share_${System.currentTimeMillis()}$ext")
            contentResolver.openInputStream(uri)?.use { input ->
                cacheFile.outputStream().use { output -> input.copyTo(output) }
            }
            cacheFile.absolutePath
        } catch (e: Exception) {
            Log.w(TAG, "Failed to copy share URI to cache: $e")
            null
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
