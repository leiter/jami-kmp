package net.jami.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
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

        if (!LocalPrefs.getBoolean(LocalPrefKeys.START_ON_BOOT, false)) {
            Log.d(TAG, "Start on boot disabled — skipping")
            return
        }

        Log.d(TAG, "Boot completed — starting daemon service")
        try {
            ContextCompat.startForegroundService(context, Intent(context, JamiDaemonService::class.java))
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Error starting service on boot", e)
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
