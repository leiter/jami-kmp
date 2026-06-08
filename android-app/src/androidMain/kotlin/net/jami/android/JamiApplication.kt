package net.jami.android

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.jami.di.initKoin
import net.jami.services.AccountService
import net.jami.services.DaemonBridge
import net.jami.services.DaemonCallbacks
import net.jami.services.expect.HardwareService
import org.koin.android.ext.koin.androidContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class JamiApplication : Application(), KoinComponent {

    private val daemonBridge: DaemonBridge by inject()
    private val daemonCallbacks: DaemonCallbacks by inject()
    private val accountService: AccountService by inject()
    private val hardwareService: HardwareService by inject()

    override fun onCreate() {
        super.onCreate()

        // Initialize Koin
        initKoin {
            androidContext(this@JamiApplication)
        }

        // Initialize Jami daemon
        // Note: JamiDaemonService is started from MainActivity (foreground-safe) and from
        // BootReceiver. Starting a foreground service from Application.onCreate() is
        // disallowed on Android 12+ when the app is launched in a background/restricted state.
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
                    // Enumerate cameras and register them (+ screen-sharing device) with the
                    // daemon so video negotiation can proceed.
                    CoroutineScope(Dispatchers.Default).launch {
                        hardwareService.initVideo()
                    }
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
