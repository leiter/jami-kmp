package net.jami.android

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import net.jami.android.service.JamiDaemonService
import net.jami.di.initKoin
import net.jami.services.AccountService
import net.jami.services.DaemonBridge
import net.jami.services.DaemonCallbacks
import org.koin.android.ext.koin.androidContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class JamiApplication : Application(), KoinComponent {

    private val daemonBridge: DaemonBridge by inject()
    private val daemonCallbacks: DaemonCallbacks by inject()
    private val accountService: AccountService by inject()

    override fun onCreate() {
        super.onCreate()

        // Initialize Koin
        initKoin {
            androidContext(this@JamiApplication)
        }

        // Keep the daemon process alive even when the UI is closed
        ContextCompat.startForegroundService(this, Intent(this, JamiDaemonService::class.java))

        // Initialize Jami daemon
        try {
            Log.d(TAG, "Initializing Jami daemon...")
            val initResult = daemonBridge.init(daemonCallbacks)
            if (initResult) {
                Log.i(TAG, "Jami daemon initialized successfully")
                val startResult = daemonBridge.start()
                if (startResult) {
                    Log.i(TAG, "Jami daemon started successfully")
                    // Explicitly load accounts now — accountsChanged may not fire on a fresh
                    // install (no accounts) or may fire asynchronously after JamiService.init()
                    // returns. This guarantees daemonAccountsReady is set so AppViewModel can
                    // exit Loading state regardless.
                    accountService.loadAccountsFromDaemon(isConnected = true)
                } else {
                    Log.e(TAG, "Failed to start Jami daemon")
                }
            } else {
                Log.e(TAG, "Failed to initialize Jami daemon")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during daemon initialization", e)
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        try {
            Log.d(TAG, "Stopping Jami daemon...")
            daemonBridge.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Exception during daemon shutdown", e)
        }
    }

    companion object {
        private const val TAG = "JamiApplication"
    }
}
