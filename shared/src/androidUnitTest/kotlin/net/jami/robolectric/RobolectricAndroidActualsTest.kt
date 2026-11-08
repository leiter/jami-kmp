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
package net.jami.robolectric

import net.jami.ensurePlatformTestKoin
import net.jami.viewmodel.AppSettingsViewModelTest
import org.junit.Rule
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `AppSettingsViewModelTest`, re-run against the *Android* actuals.
 *
 * They were excluded from the Android unit-test task because the Android implementations they
 * would exercise need a real `Context`: `LocalPrefs` reaches SharedPreferences through Koin,
 * `HardwareService` wants an `AudioManager`, and `android.jar` stubs return defaults rather
 * than behaving. So the Android side of all of this — the half that ships to a phone — was
 * untested while the suite looked green.
 *
 * Robolectric supplies that Context. The bodies hold nothing but the Koin rule on purpose:
 * JUnit runs the test methods inherited from the common class, so these are the same
 * assertions against the other set of actuals, and a divergence between the two shows up
 * here rather than on a device.
 *
 * Pinned to SDK 34 rather than the project's compileSdk: Robolectric ships a prebuilt Android
 * runtime per API level and supports only the ones it has shipped, which lags the SDK. The
 * point is to run the Android actuals at all, and they do not branch on 34 versus 36.
 */
private const val ROBOLECTRIC_SDK = 34

/**
 * Starts the Android Koin graph around the whole test rather than in a setup method.
 *
 * A superclass's setup runs first, and these parents reset `LocalPrefs` in theirs — which on
 * Android is the very thing that needs Koin. A rule wraps the setup methods too, so it is the
 * only place early enough.
 */
class AndroidKoinRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement =
        object : Statement() {
            override fun evaluate() {
                ensurePlatformTestKoin()
                base.evaluate()
            }
        }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK])
class RobolectricAppSettingsViewModelTest : AppSettingsViewModelTest() {
    @get:Rule
    val koin = AndroidKoinRule()
}

/*
 * The other three excluded classes do NOT run here yet, and are left excluded rather than
 * left failing. What stops each, observed on 2026-11-08 with Robolectric 4.14.1:
 *
 *  - HardwareServiceTest: the Android HardwareService resolves `DaemonBridge` from Koin, and
 *    that is the JNI class — binding it in a unit test would load libjami-core-jni.so, which
 *    is what the test is trying to avoid. It wants the bridge behind an interface first.
 *  - AppViewModelTest, CallViewModelTest: their state machines never leave Loading /
 *    Outgoing under Robolectric, although `advanceUntilIdle()` returns. Something in the
 *    Android path is not on the test scheduler; it is a real difference between the actuals
 *    worth chasing, and not a one-line fix.
 *
 * Both are the sort of finding this task existed to surface: until now neither could even be
 * observed, because the classes did not run on this target at all.
 */

