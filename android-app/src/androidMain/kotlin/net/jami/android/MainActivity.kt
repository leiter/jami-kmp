package net.jami.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import net.jami.services.AccountService
import net.jami.services.SyncManager
import net.jami.ui.JamiApp
import net.jami.utils.Log
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val syncManager: SyncManager by inject()
    private val accountService: AccountService by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JamiApp()
        }
    }

    override fun onStop() {
        super.onStop()

        // Start background sync if an account is active
        val currentAccount = accountService.currentAccount.value
        if (currentAccount != null) {
            Log.d(TAG, "App backgrounded, starting background sync for account: ${currentAccount.accountId}")
            // Use 2-hour timeout to balance sync reliability with battery life
            syncManager.startBackgroundSyncWithTimeout(
                timeoutMs = 2 * 60 * 60 * 1000L // 2 hours
            )
        } else {
            Log.d(TAG, "App backgrounded, but no active account - skipping background sync")
        }
    }

    override fun onStart() {
        super.onStart()

        // Stop background sync when app comes to foreground
        // Daemon is already alive in the app process
        if (syncManager.isBackgroundSyncActive) {
            Log.d(TAG, "App foregrounded, stopping background sync")
            syncManager.stopBackgroundSync()
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
