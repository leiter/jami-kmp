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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

/**
 * Whether the platform's power management is allowed to stop the app in the background, and the
 * means to ask the user to stop it from doing so.
 *
 * On Android this is the battery-optimisation exemption (Doze / App Standby). Without it the
 * system freezes and eventually kills the Jami daemon process while the screen is off, which
 * silently drops incoming calls and messages — the persistent foreground service and
 * "Run in the background" alone do not prevent it on most vendor ROMs.
 *
 * Every other platform reports [isSupported] `false` and [isExempt] `true`: there is nothing of
 * this kind to grant there, so callers must not nag the user about it.
 */
@Stable
interface BatteryOptimizationState {
    /** True when the platform has a power-management exemption that the app can ask for. */
    val isSupported: Boolean

    /**
     * True when the app is currently exempt from power management — and on platforms where
     * [isSupported] is false, since nothing is going to stop the app there.
     *
     * Re-read whenever the app is resumed, so it is correct after the user returns from the
     * system dialog or changes the setting by hand.
     */
    val isExempt: Boolean

    /**
     * Asks the user for the exemption: shows the system's own dialog when there is one, and
     * otherwise opens the settings page where it can be changed. When the app is already exempt
     * this opens that settings page too, so the user can revoke it (no app can revoke its own).
     *
     * A no-op where [isSupported] is false.
     */
    fun request()
}

/**
 * Remembers the current [BatteryOptimizationState] for the calling composable.
 *
 * The returned object re-reads the exemption on every `ON_RESUME`, so a screen holding it shows
 * the right state after the user comes back from the system dialog.
 */
@Composable
expect fun rememberBatteryOptimizationState(): BatteryOptimizationState
