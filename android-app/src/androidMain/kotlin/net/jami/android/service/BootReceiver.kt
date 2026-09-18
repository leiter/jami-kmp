package net.jami.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import net.jami.ui.platform.LocalPrefs
import net.jami.ui.platform.LocalPrefKeys
import net.jami.utils.Log

/**
 * Starts [JamiDaemonService] after device boot so the Jami daemon is alive
 * and ready to receive calls without the user having to open the app first.
 *
 * Note: Android prevents this receiver from firing after a user force-stop.
 * The user must open the app at least once after a force-stop for auto-start
 * to resume on subsequent reboots.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_REBOOT &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != "android.intent.action.QUICKBOOT_POWERON") return

        if (!LocalPrefs.getBoolean(LocalPrefKeys.START_ON_BOOT, true)) {
            Log.d(TAG, "Start on boot disabled — skipping")
            return
        }

        // Without "Run in the background" the service is not a foreground service, and a plain
        // service cannot be started from a boot broadcast (and would be stopped within a minute).
        if (!JamiDaemonService.isRunInBackgroundEnabled(context)) {
            Log.d(TAG, "Run in background disabled — not starting the daemon service on boot")
            return
        }

        Log.d(TAG, "Boot completed — starting daemon service")
        JamiDaemonService.start(context)
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
