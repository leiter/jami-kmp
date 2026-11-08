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
package net.jami

import android.content.Context
import net.jami.services.DaemonBridgeApi
import net.jami.services.Settings
import net.jami.services.StubDaemonBridge
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatformTools
import org.koin.dsl.module

/**
 * Starts the smallest Koin graph the Android actuals resolve from: [Settings], which
 * `LocalPrefs` looks up to reach SharedPreferences, and a stub daemon bridge.
 *
 * Only possible under Robolectric, which is why these tests could not run on the Android
 * target at all before: without an Android runtime there is no Context to hand
 * `getSharedPreferences`, so `LocalPrefs` threw and four common test classes were excluded
 * from `testDebugUnitTest` — leaving the actuals that ship to a phone untested.
 *
 * Outside Robolectric this is a no-op, so the plain Android unit tests behave as before.
 */
actual fun ensurePlatformTestKoin() {
    val context = robolectricApplication() ?: return
    if (KoinPlatformTools.defaultContext().getOrNull() != null) return
    startKoin {
        modules(module {
            single { Settings(context, "jami_test_settings") }
            single<DaemonBridgeApi> { StubDaemonBridge() }
        })
    }
}

/**
 * The Robolectric application, or null when these tests are not running under it.
 *
 * Reflective because the call only exists at runtime under the Robolectric runner, and
 * asking for it anywhere else throws rather than returning null.
 */
internal fun robolectricApplication(): Context? = try {
    val clazz = Class.forName("org.robolectric.RuntimeEnvironment")
    clazz.getMethod("getApplication").invoke(null) as? Context
} catch (e: Throwable) {
    null
}
