package net.jami.android

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import net.jami.android.service.CallActionReceiver
import net.jami.services.AccountService
import net.jami.services.CallService
import net.jami.services.NotificationService
import net.jami.services.SyncManager
import net.jami.ui.JamiApp
import net.jami.utils.Log
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val syncManager: SyncManager by inject()
    private val accountService: AccountService by inject()
    private val callService: CallService by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JamiApp()
        }
        handleCallIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleCallIntent(intent)
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

    /**
     * Routes incoming call intents to [CallService] and enables lock-screen display.
     * Mirrors CallActivity.handleNewIntent() from jami-android-client.
     *
     * Currently: accepts or views the call via CallService directly.
     * The navigation graph picks up the active call via CallService.currentCalls.
     */
    private fun handleCallIntent(intent: Intent?) {
        val callId = intent?.getStringExtra(NotificationService.KEY_CALL_ID) ?: return

        when (intent.action) {
            CallActionReceiver.ACTION_VIEW_CALL -> {
                Log.d(TAG, "VIEW_CALL: callId=$callId")
                enableLockScreenDisplay()
            }

            ACTION_ACCEPT_CALL -> {
                Log.d(TAG, "ACCEPT_CALL: callId=$callId")
                enableLockScreenDisplay()
                val call = callService.getCall(callId) ?: return
                callService.accept(call.account, callId, hasVideo = false)
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

    companion object {
        private const val TAG = "MainActivity"
        const val ACTION_ACCEPT_CALL = "net.jami.action.ACCEPT_CALL"
    }
}
