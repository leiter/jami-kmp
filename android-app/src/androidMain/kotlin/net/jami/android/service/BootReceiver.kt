package net.jami.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
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
            action != "android.intent.action.QUICKBOOT_POWERON") return

        Log.d(TAG, "Boot completed — starting daemon service")
        val serviceIntent = Intent(context, JamiDaemonService::class.java)
        ContextCompat.startForegroundService(context, serviceIntent)
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
