package net.jami.services

/**
 * Result of a biometric authentication attempt.
 */
sealed class BiometricResult {
    /**
     * Authentication succeeded and password was decrypted.
     */
    data class Success(val decryptedPassword: String) : BiometricResult()

    /**
     * Authentication failed with an error.
     * @param message Error message to display to user
     * @param canRetry Whether the user can attempt authentication again
     */
    data class Error(val message: String, val canRetry: Boolean) : BiometricResult()

    /**
     * User cancelled the authentication prompt.
     */
    data object Cancelled : BiometricResult()
}

/**
 * Availability status of biometric authentication on the device.
 */
enum class BiometricAvailability {
    /** Biometric authentication is available and can be used */
    AVAILABLE,

    /** Hardware is available but no biometrics are enrolled */
    NOT_ENROLLED,

    /** Device does not have biometric hardware */
    NO_HARDWARE,

    /** Biometric authentication is unavailable for other reasons */
    UNAVAILABLE,

    /** Unknown error occurred while checking availability */
    UNKNOWN_ERROR
}

/**
 * Platform-specific biometric authentication service.
 *
 * Provides secure biometric authentication using platform-specific secure storage:
 * - Android: AndroidKeyStore + BiometricPrompt
 * - iOS: Keychain + LocalAuthentication
 *
 * The service encrypts account passwords and requires biometric authentication
 * to decrypt them. Keys are automatically invalidated when device biometrics change.
 */
expect class BiometricService {
    /**
     * Check if biometric authentication is available on this device.
     *
     * @return BiometricAvailability status
     */
    suspend fun checkAvailability(): BiometricAvailability

    /**
     * Check if biometric authentication is currently enabled for an account.
     *
     * @param accountId The account ID to check
     * @return true if biometric is enabled for this account
     */
    suspend fun isEnabled(accountId: String): Boolean

    /**
     * Enroll biometric authentication for an account.
     * Encrypts the password using platform-specific secure storage.
     *
     * This will:
     * 1. Show a biometric prompt to authenticate
     * 2. Generate a secure key in platform-specific storage
     * 3. Encrypt the password with the key
     * 4. Store the encrypted password
     *
     * @param accountId The account ID
     * @param password The account password to encrypt
     * @param promptTitle Title for the biometric prompt
     * @param promptDescription Description for the biometric prompt
     * @return true if enrollment succeeded, false otherwise
     */
    suspend fun enroll(
        accountId: String,
        password: String,
        promptTitle: String,
        promptDescription: String
    ): Boolean

    /**
     * Authenticate using biometrics and decrypt the account password.
     *
     * This will:
     * 1. Show a biometric prompt to authenticate
     * 2. Retrieve and decrypt the stored password using the authenticated key
     * 3. Return the decrypted password
     *
     * @param accountId The account ID
     * @param promptTitle Title for the biometric prompt
     * @param promptDescription Description for the biometric prompt
     * @return BiometricResult with decrypted password or error
     */
    suspend fun authenticate(
        accountId: String,
        promptTitle: String,
        promptDescription: String
    ): BiometricResult

    /**
     * Disable biometric authentication for an account.
     * Deletes the encrypted password and associated cryptographic keys.
     *
     * @param accountId The account ID
     * @return true if disable succeeded, false otherwise
     */
    suspend fun disable(accountId: String): Boolean
}
