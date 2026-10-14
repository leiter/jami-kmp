package net.jami.android

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.jami.android.push.PushServiceManager
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
    private val sendQueue: net.jami.services.SendQueueService by inject()
    private val daemonLifecycle: net.jami.services.DaemonLifecycleService by inject()

    override fun onCreate() {
        super.onCreate()

        // Initialize Koin
        initKoin {
            androidContext(this@JamiApplication)
        }

        // Fetch the FCM token early — it arrives asynchronously and is applied to the daemon by
        // PushServiceManager once both a token and loaded accounts are available.
        PushServiceManager.initialize(this)

        // Initialize Jami daemon
        // Note: JamiDaemonService is started from MainActivity (foreground-safe) and from
        // BootReceiver. Starting a foreground service from Application.onCreate() is
        // disallowed on Android 12+ when the app is launched in a background/restricted state.
        // The daemon start and everything that depends on it run through DaemonLifecycleService,
        // so a failed start is retried and a later recovery re-runs this same sequence instead of
        // leaving the app wired to a daemon that never came up.
        daemonLifecycle.setStartupSequence {
            Log.i(TAG, "Jami daemon started — wiring up")
            // Listen before accounts load, so messages held in the durable outbox are sent
            // once the first smartlist load finds their conversation live.
            sendQueue.start()
            // Explicitly load accounts now — accountsChanged may not fire on a fresh
            // install (no accounts) or may fire asynchronously after JamiService.init()
            // returns. This guarantees daemonAccountsReady is set so AppViewModel can
            // exit Loading state regardless.
            accountService.loadAccountsFromDaemon(isConnected = true)
            // Accounts exist now, so a push token can actually be applied to them.
            // Safe when Firebase is unconfigured — this just clears the daemon token.
            PushServiceManager.applyCurrentMode()
            // Enumerate cameras and register them (+ screen-sharing device) with the
            // daemon so video negotiation can proceed.
            hardwareService.initVideo()
        }
        // A daemon that could not start while the device was offline gets another chance as soon
        // as there is a network.
        daemonLifecycle.observeConnectivity(hardwareService.connectivityState)
        CoroutineScope(Dispatchers.Default).launch {
            Log.d(TAG, "Initializing Jami daemon...")
            daemonLifecycle.ensureRunning()
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        try {
            Log.d(TAG, "Stopping Jami daemon...")
            daemonLifecycle.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Exception during daemon shutdown", e)
        }
    }

    companion object {
        private const val TAG = "JamiApplication"
    }
}
