package net.jami.services

/**
 * macOS implementation of BiometricService.
 * Biometric authentication is not currently implemented for macOS.
 * TODO: Could be implemented using LocalAuthentication framework similar to iOS.
 */
actual class BiometricService {
    actual suspend fun checkAvailability(): BiometricAvailability {
        return BiometricAvailability.UNAVAILABLE
    }

    actual suspend fun isEnabled(accountId: String): Boolean {
        return false
    }

    actual suspend fun enroll(
        accountId: String,
        password: String,
        promptTitle: String,
        promptDescription: String
    ): Boolean {
        return false
    }

    actual suspend fun authenticate(
        accountId: String,
        promptTitle: String,
        promptDescription: String
    ): BiometricResult {
        return BiometricResult.Error("Biometric authentication not implemented for macOS", false)
    }

    actual suspend fun disable(accountId: String): Boolean {
        return false
    }
}
