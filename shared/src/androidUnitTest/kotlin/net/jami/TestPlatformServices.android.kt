package net.jami

import android.content.ContextWrapper
import net.jami.services.expect.SystemContactsService
import net.jami.services.BiometricService
import net.jami.services.expect.AudioRecorderService

/**
 * Under Robolectric these get a real Context, so the Android actuals run for real instead of
 * against `android.jar` stubs — [BiometricService.isEnabled] reaches SharedPreferences, which
 * `ContextWrapper(null)` cannot provide (it threw inside AppViewModel's account collector, and
 * the state machine simply never left Loading). Elsewhere they stay the placeholder wrapper the
 * non-Robolectric Android unit tests have always used. Same shape as [testHardwareService].
 */
actual fun testSystemContactsService(): SystemContactsService =
    SystemContactsService(robolectricApplication() ?: ContextWrapper(null))

actual fun testBiometricService(): BiometricService =
    BiometricService(robolectricApplication() ?: ContextWrapper(null))

actual fun testAudioRecorderService(): AudioRecorderService =
    AudioRecorderService(robolectricApplication() ?: ContextWrapper(null))
