package net.jami.services

/**
 * Web/JS implementation of BiometricService.
 * Biometric authentication is not available on web platforms.
 * Note: Web Authentication API (WebAuthn) could be considered in the future,
 * but it has different security properties and use cases.
 */
actual class BiometricService {
    actual suspend fun checkAvailability(): BiometricAvailability {
        return BiometricAvailability.NO_HARDWARE
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
        return BiometricResult.Error("Biometric authentication not available on web", false)
    }

    actual suspend fun disable(accountId: String): Boolean {
        return false
    }
}
