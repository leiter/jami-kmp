/*
 *  Copyright (C) 2004-2025 Savoir-faire Linux Inc.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package net.jami.ui.platform

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import net.jami.utils.Log
import net.jami.utils.PendingActivityResultTracker

private const val TAG = "BatteryOptimization"

/**
 * Reads the exemption straight from [PowerManager]; there is no broadcast for it changing, so
 * every caller polls on resume.
 */
internal fun Context.isIgnoringBatteryOptimizations(): Boolean {
    val pm = ContextCompat.getSystemService(this, PowerManager::class.java) ?: return false
    return try {
        pm.isIgnoringBatteryOptimizations(packageName)
    } catch (e: Exception) {
        // Some vendor ROMs throw out of this; treat an unreadable state as "not exempt" rather
        // than claiming an exemption the daemon does not have.
        Log.w(TAG, "Unable to read battery-optimisation state", e)
        false
    }
}

@Composable
actual fun rememberBatteryOptimizationState(): BatteryOptimizationState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var exempt by remember { mutableStateOf(context.isIgnoringBatteryOptimizations()) }

    // The user grants (or refuses) the exemption in a system Activity, and can also change it by
    // hand in Settings while Jami is in the background — so re-read it on every resume rather
    // than trusting the Activity result, which is RESULT_CANCELED even on a grant.
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                exempt = context.isIgnoringBatteryOptimizations()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        // Clear first, unconditionally — see PendingActivityResultTracker's kdoc. The result code
        // carries no information here; ON_RESUME above re-reads the real state.
        PendingActivityResultTracker.end()
        exempt = context.isIgnoringBatteryOptimizations()
    }

    return remember(context, launcher) {
        object : BatteryOptimizationState {
            override val isSupported = true
            override val isExempt get() = exempt

            @SuppressLint("BatteryLife")
            override fun request() {
                // Already exempt: no app may revoke its own exemption, so send the user to the
                // list where they can. Not exempt: ask directly — ACTION_REQUEST_… is a one-tap
                // system dialog, where the settings page is several screens deep behind an
                // "All apps" filter most users never find.
                val direct = !exempt
                val intent = if (direct) {
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:${context.packageName}"),
                    )
                } else {
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                }
                // Marked *before* launch so JamiNavigation's biometric-lock swap can't dispose
                // this composable — and this launcher with it — before the result arrives.
                PendingActivityResultTracker.begin()
                try {
                    launcher.launch(intent)
                } catch (e: ActivityNotFoundException) {
                    PendingActivityResultTracker.end()
                    // Stripped-down and vendor ROMs do not always ship the request dialog. Fall
                    // back to the settings list, and if that is missing too, to the app's own
                    // details page, which always exists.
                    val fallback = if (direct) {
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    } else {
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${context.packageName}"),
                        )
                    }
                    PendingActivityResultTracker.begin()
                    try {
                        launcher.launch(fallback)
                    } catch (e2: ActivityNotFoundException) {
                        PendingActivityResultTracker.end()
                        Log.w(TAG, "No battery-optimisation settings screen on this device", e2)
                    }
                }
            }
        }
    }
}
