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

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import net.jami.ensureComposeResourceContext
import net.jami.ensurePlatformTestKoin
import net.jami.robolectricApplication
import net.jami.services.HardwareServiceTest
import net.jami.viewmodel.AppSettingsViewModelTest
import net.jami.viewmodel.AppViewModelTest
import net.jami.viewmodel.CallViewModelTest
import net.jami.viewmodel.ContactDetailsViewModelTest
import net.jami.viewmodel.GroupMemberBanTest
import net.jami.viewmodel.ImportAccountViewModelTest
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.AudioDeviceInfoBuilder
import org.robolectric.shadows.ShadowNetworkCapabilities

/**
 * The four common test classes excluded from the Android unit-test task, re-run against the
 * *Android* actuals.
 *
 * They were excluded because the Android implementations they exercise need a real `Context`:
 * `LocalPrefs` reaches SharedPreferences through Koin, `HardwareService` wants an `AudioManager`
 * and a `ConnectivityManager`, `BiometricService` reads its own prefs, and `android.jar` stubs
 * return defaults rather than behaving. So the Android side of all of this — the half that ships
 * to a phone — was untested while the suite looked green.
 *
 * Robolectric supplies that Context. The bodies hold little but the Koin rule on purpose: JUnit
 * runs the test methods inherited from the common class, so these are the same assertions against
 * the other set of actuals, and a divergence between the two shows up here rather than on a
 * device. Three real divergences did show up, and are fixed rather than papered over:
 *
 *  - The Android `HardwareService` injected the concrete JNI `DaemonBridge` where every other
 *    service injects `DaemonBridgeApi`, and called `JamiService.setAudioPlugin` directly from
 *    `toggleSpeakerphone`. Both reached the native library; the first is now the interface and
 *    the second goes through `daemonBridge.restartAudioLayer()`, which wraps that exact pair.
 *  - `setPendingScreenShareProjection` hard-cast its `Any?` argument to `MediaProjection`, so
 *    a wrong type crashed inside the service. It is a checked cast now.
 *  - `testBiometricService()` handed the Android actual a `ContextWrapper(null)`, whose
 *    `getSharedPreferences` throws — inside `AppViewModel`'s account collector, where the
 *    exception was swallowed and the app state simply never left `Loading`.
 *
 * The base classes stay excluded from the plain task: without the Robolectric runner there is
 * still no Context, so they are only ever run through the subclasses here.
 *
 * Pinned to SDK 34 rather than the project's compileSdk: Robolectric ships a prebuilt Android
 * runtime per API level and supports only the ones it has shipped, which lags the SDK. The
 * point is to run the Android actuals at all, and they do not branch on 34 versus 36.
 */
private const val ROBOLECTRIC_SDK = 34

/**
 * Puts the Android runtime's two global prerequisites in place around the whole test rather
 * than in a setup method: the Koin graph the actuals resolve from, and the Context that Compose
 * Multiplatform's resource reader needs before any `getString` call.
 *
 * A rule, not a `@Before`: a superclass's setup runs first, and these parents reset `LocalPrefs`
 * in theirs — which on Android is the very thing that needs Koin. A rule wraps the setup methods
 * too, so it is the only place early enough.
 */
class AndroidRuntimeRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement =
        object : Statement() {
            override fun evaluate() {
                ensurePlatformTestKoin()
                ensureComposeResourceContext()
                base.evaluate()
            }
        }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK])
class RobolectricAppSettingsViewModelTest : AppSettingsViewModelTest() {
    @get:Rule
    val androidRuntime = AndroidRuntimeRule()
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK])
class RobolectricHardwareServiceTest : HardwareServiceTest() {
    @get:Rule
    val androidRuntime = AndroidRuntimeRule()

    /**
     * Gives the simulated device the two things the Android HardwareService reads out of the
     * platform before it will agree it is online, or that it can reach a speaker.
     *
     * Robolectric's default device has neither: no capabilities on the default network, and no
     * audio devices at all. The Android actual seeds `connectivityState` from a live snapshot in
     * its `init` and routes the speaker through `availableCommunicationDevices`, where the stub
     * and the desktop actual just flip a boolean — so unconfigured, the service came up claiming
     * *offline* and refused to turn the speaker on, and the shared assertions failed against a
     * phone-like expectation that was never set up. A connected phone with a built-in speaker is
     * the condition the shared test describes, so the environment is what has to say so.
     *
     * Runs before the test body, which is where `testHardwareService()` constructs the service:
     * `hasSpeakerphone()` and the connectivity snapshot are both read from there onwards.
     */
    @Before
    fun describeAPhoneLikeDevice() {
        val context = robolectricApplication() ?: return

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = ShadowNetworkCapabilities.newInstance()
        shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        shadowOf(cm).setNetworkCapabilities(cm.activeNetwork, caps)

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val speaker = AudioDeviceInfoBuilder.newBuilder()
            .setType(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
            .build()
        // Both lists: `hasSpeakerphone()` reads the output devices, `routeToSpeaker()` picks
        // from the communication devices, and the toggle needs each to find the speaker.
        shadowOf(audioManager).setOutputDevices(listOf(speaker))
        shadowOf(audioManager).setAvailableCommunicationDevices(listOf(speaker))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK])
class RobolectricAppViewModelTest : AppViewModelTest() {
    @get:Rule
    val androidRuntime = AndroidRuntimeRule()
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK])
class RobolectricCallViewModelTest : CallViewModelTest() {
    @get:Rule
    val androidRuntime = AndroidRuntimeRule()
}

/*
 * Below: the three classes that were never excluded, and so had been failing in the open on the
 * Android target — eleven tests, all green on desktop. One cause for all of them: Compose
 * Multiplatform's resource reader had no Context, so `getString` threw inside the ViewModels'
 * `launch` blocks and the state update after it never ran. See [ensureComposeResourceContext],
 * which the rule now calls. They need nothing else from the Android runtime.
 */

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK])
class RobolectricContactDetailsViewModelTest : ContactDetailsViewModelTest() {
    @get:Rule
    val androidRuntime = AndroidRuntimeRule()
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK])
class RobolectricGroupMemberBanTest : GroupMemberBanTest() {
    @get:Rule
    val androidRuntime = AndroidRuntimeRule()
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK])
class RobolectricImportAccountViewModelTest : ImportAccountViewModelTest() {
    @get:Rule
    val androidRuntime = AndroidRuntimeRule()
}
