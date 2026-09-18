package net.jami

import net.jami.services.expect.SystemContactsService
import net.jami.services.BiometricService
import net.jami.services.expect.AudioRecorderService

actual fun testSystemContactsService(): SystemContactsService = SystemContactsService()
actual fun testBiometricService(): BiometricService = BiometricService()
actual fun testAudioRecorderService(): AudioRecorderService = AudioRecorderService()
