package net.jami

import net.jami.services.expect.SystemContactsService
import net.jami.services.BiometricService
import net.jami.services.expect.AudioRecorderService

// Platform services for common tests. Their Android actuals take a Context (only used inside their
// methods), which JVM unit tests don't have — the Android actuals pass a placeholder, as for
// testHardwareService().
expect fun testSystemContactsService(): SystemContactsService
expect fun testBiometricService(): BiometricService
expect fun testAudioRecorderService(): AudioRecorderService
