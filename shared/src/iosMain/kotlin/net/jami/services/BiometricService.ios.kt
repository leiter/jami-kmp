package net.jami.services

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSUserDefaults
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthentication
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@OptIn(ExperimentalForeignApi::class)
actual class BiometricService {

    private val defaults = NSUserDefaults.standardUserDefaults

    actual suspend fun checkAvailability(): BiometricAvailability {
        val context = LAContext()
        return when {
            context.canEvaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, error = null) ->
                BiometricAvailability.AVAILABLE
            context.canEvaluatePolicy(LAPolicyDeviceOwnerAuthentication, error = null) ->
                BiometricAvailability.NOT_ENROLLED
            else ->
                BiometricAvailability.NO_HARDWARE
        }
    }

    actual suspend fun isEnabled(accountId: String): Boolean =
        defaults.stringForKey(prefKey(accountId)) != null

    actual suspend fun enroll(
        accountId: String,
        password: String,
        promptTitle: String,
        promptDescription: String
    ): Boolean {
        if (password.isEmpty()) return false
        return try {
            if (!authenticateUser(promptTitle, promptDescription)) false
            else {
                defaults.setObject(password, forKey = prefKey(accountId))
                true
            }
        } catch (_: Exception) { false }
    }

    actual suspend fun authenticate(
        accountId: String,
        promptTitle: String,
        promptDescription: String
    ): BiometricResult {
        return try {
            if (!authenticateUser(promptTitle, promptDescription)) return BiometricResult.Cancelled
            val password = defaults.stringForKey(prefKey(accountId))
                ?: return BiometricResult.Error("No stored credential", false)
            BiometricResult.Success(password)
        } catch (e: Exception) {
            BiometricResult.Error(e.message ?: "Authentication failed", false)
        }
    }

    actual suspend fun disable(accountId: String): Boolean {
        defaults.removeObjectForKey(prefKey(accountId))
        return true
    }

    private suspend fun authenticateUser(title: String, description: String): Boolean =
        suspendCoroutine { cont ->
            val context = LAContext()
            if (!context.canEvaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, error = null)) {
                cont.resume(false)
                return@suspendCoroutine
            }
            context.evaluatePolicy(
                LAPolicyDeviceOwnerAuthenticationWithBiometrics,
                localizedReason = "$title - $description"
            ) { success, _ -> cont.resume(success) }
        }

    private fun prefKey(accountId: String) = "jami_biometric_$accountId"
}
