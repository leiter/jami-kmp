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

/**
 * Device-local preference storage for settings that must NOT sync via daemon.
 *
 * Use this for settings that are device-specific (e.g. screenshot blocking,
 * start on boot, run in background). Settings stored here are never uploaded
 * to account details and never synced across devices.
 *
 * Platform implementations:
 * - Android: SharedPreferences via Koin-injected Settings singleton
 * - iOS/macOS: NSUserDefaults
 * - Desktop: java.util.prefs.Preferences
 * - JS: localStorage
 */
expect object LocalPrefs {
    fun getBoolean(key: String, default: Boolean): Boolean
    fun setBoolean(key: String, value: Boolean)
}

/**
 * Keys for device-local settings stored in [LocalPrefs].
 */
object LocalPrefKeys {
    const val SCREENSHOT_BLOCKING = "local_screenshot_blocking"
    const val START_ON_BOOT = "local_start_on_boot"
    const val RUN_IN_BACKGROUND = "local_run_in_background"
}
