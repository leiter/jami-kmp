package net.jami.android

import android.app.Application
import android.system.Os
import net.jami.di.initKoin
import net.jami.services.DaemonBridge
import net.jami.services.DaemonCallbacksImpl
import net.jami.utils.Log
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import java.io.File

class JamiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        setupCaCertificate()
        initKoin {
            androidContext(this@JamiApplication)
        }

        // Initialize and start the daemon
        val koin = GlobalContext.get()
        val daemonBridge: DaemonBridge = koin.get()
        val callbacks: DaemonCallbacksImpl = koin.get()

        if (daemonBridge.init(callbacks)) {
            daemonBridge.start()
            Log.i(TAG, "Jami daemon initialized and started")
        } else {
            Log.e(TAG, "Failed to initialize Jami daemon")
        }
    }

    private fun setupCaCertificate() {
        try {
            val dest = File(filesDir, "cacert.pem")
            if (!dest.exists()) {
                assets.open("cacert.pem").use { input ->
                    dest.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            Os.setenv("CA_ROOT_FILE", dest.absolutePath, true)
            Log.i(TAG, "CA_ROOT_FILE set to ${dest.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup CA certificate: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "JamiApplication"
    }
}
