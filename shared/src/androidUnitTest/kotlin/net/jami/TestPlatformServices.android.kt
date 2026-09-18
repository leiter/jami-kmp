package net.jami

import android.content.ContextWrapper
import net.jami.services.expect.SystemContactsService
import net.jami.services.BiometricService
import net.jami.services.expect.AudioRecorderService

actual fun testSystemContactsService(): SystemContactsService = SystemContactsService(ContextWrapper(null))
actual fun testBiometricService(): BiometricService = BiometricService(ContextWrapper(null))
actual fun testAudioRecorderService(): AudioRecorderService = AudioRecorderService(ContextWrapper(null))
