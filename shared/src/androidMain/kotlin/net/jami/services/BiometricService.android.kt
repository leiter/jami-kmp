package net.jami.services

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import kotlin.coroutines.resume

/**
 * Android implementation of BiometricService using AndroidKeyStore and BiometricPrompt.
 *
 * Security model:
 * - Passwords are encrypted using AES-CBC with PKCS7 padding
 * - Keys are stored in AndroidKeyStore (hardware-backed on supported devices)
 * - Keys require biometric authentication for use
 * - Keys are automatically invalidated when device biometrics change
 * - Encrypted data is stored in app-private SharedPreferences
 */
actual class BiometricService(private val context: Context) {
    companion object {
        private const val TAG = "BiometricService"
        private const val PREFS_PREFIX = "biometric_"
        private const val KEY_NAME = "keyName"
        private const val KEY_ENCRYPTED_PASSWORD = "encryptedPassword"
        private const val KEY_IV = "iv"
        private const val KEYSTORE_NAME = "AndroidKeyStore"

        // Use BIOMETRIC_STRONG for maximum security
        private const val ALLOWED_AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_STRONG

        @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
        private const val KEY_AUTH = KeyProperties.AUTH_BIOMETRIC_STRONG
    }

    actual suspend fun checkAvailability(): BiometricAvailability {
        return try {
            val biometricManager = BiometricManager.from(context)
            when (biometricManager.canAuthenticate(ALLOWED_AUTHENTICATORS)) {
                BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.AVAILABLE
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NOT_ENROLLED
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
                BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> BiometricAvailability.NO_HARDWARE
                else -> BiometricAvailability.UNAVAILABLE
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking biometric availability", e)
            BiometricAvailability.UNKNOWN_ERROR
        }
    }

    actual suspend fun isEnabled(accountId: String): Boolean {
        val prefs = context.getSharedPreferences("$PREFS_PREFIX$accountId", Context.MODE_PRIVATE)
        val keyName = prefs.getString(KEY_NAME, null)
        val encryptedPassword = prefs.getString(KEY_ENCRYPTED_PASSWORD, null)
        val iv = prefs.getString(KEY_IV, null)

        // Check if all required data exists and the key is still in the keystore
        return keyName != null && encryptedPassword != null && iv != null && getSecretKey(keyName) != null
    }

    actual suspend fun enroll(
        accountId: String,
        password: String,
        promptTitle: String,
        promptDescription: String
    ): Boolean {
        if (password.isEmpty()) {
            Log.w(TAG, "Cannot enroll with empty password")
            return false
        }

        return try {
            // Generate or reuse key
            val keyName = generateKeyName()
            val secretKey = generateSecretKey(keyName)

            // Encrypt the password
            val cipher = getCipher()
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv

            // Show biometric prompt and encrypt
            val encryptedPassword = authenticateAndEncrypt(cipher, password.toByteArray(), promptTitle, promptDescription)
                ?: return false

            // Store encrypted data
            storeEncryptedData(accountId, keyName, encryptedPassword, iv)
            Log.d(TAG, "Biometric enrollment successful for account: $accountId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Biometric enrollment failed", e)
            false
        }
    }

    actual suspend fun authenticate(
        accountId: String,
        promptTitle: String,
        promptDescription: String
    ): BiometricResult {
        return try {
            // Load encrypted data
            val biometricData = loadEncryptedData(accountId)
                ?: return BiometricResult.Error("No biometric data found for this account", false)

            // Get the key
            val secretKey = getSecretKey(biometricData.keyName)
                ?: return BiometricResult.Error("Biometric key not found or invalidated", false)

            // Decrypt the password
            val cipher = getCipher()
            cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(biometricData.iv))

            val decryptedBytes = authenticateAndDecrypt(cipher, biometricData.encryptedPassword, promptTitle, promptDescription)
                ?: return BiometricResult.Cancelled

            val password = String(decryptedBytes)
            Log.d(TAG, "Biometric authentication successful")
            BiometricResult.Success(password)
        } catch (e: Exception) {
            Log.e(TAG, "Biometric authentication failed", e)
            BiometricResult.Error(e.message ?: "Authentication failed", false)
        }
    }

    actual suspend fun disable(accountId: String): Boolean {
        return try {
            // Delete the key from keystore
            val biometricData = loadEncryptedData(accountId)
            biometricData?.let {
                deleteSecretKey(it.keyName)
            }

            // Delete shared preferences
            context.deleteSharedPreferences("$PREFS_PREFIX$accountId")
            Log.d(TAG, "Biometric disabled for account: $accountId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to disable biometric", e)
            false
        }
    }

    // Private helper methods

    private fun generateKeyName(): String = UUID.randomUUID().toString()

    private fun generateSecretKey(keyName: String): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_NAME)

        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            keyName,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setUserAuthenticationParameters(0, KEY_AUTH)
                }
            }
            .build()

        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }

    private fun getSecretKey(keyName: String): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_NAME)
            keyStore.load(null)
            keyStore.getKey(keyName, null) as? SecretKey
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get secret key", e)
            null
        }
    }

    private fun deleteSecretKey(keyName: String) {
        try {
            val keyStore = KeyStore.getInstance(KEYSTORE_NAME)
            keyStore.load(null)
            keyStore.deleteEntry(keyName)
            Log.d(TAG, "Secret key deleted: $keyName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete secret key", e)
        }
    }

    private fun getCipher(): Cipher {
        return Cipher.getInstance(
            "${KeyProperties.KEY_ALGORITHM_AES}/" +
                    "${KeyProperties.BLOCK_MODE_CBC}/" +
                    KeyProperties.ENCRYPTION_PADDING_PKCS7
        )
    }

    private data class BiometricData(
        val keyName: String,
        val encryptedPassword: ByteArray,
        val iv: ByteArray
    )

    private fun loadEncryptedData(accountId: String): BiometricData? {
        return try {
            val prefs = context.getSharedPreferences("$PREFS_PREFIX$accountId", Context.MODE_PRIVATE)
            val keyName = prefs.getString(KEY_NAME, null) ?: return null
            val encryptedPasswordBase64 = prefs.getString(KEY_ENCRYPTED_PASSWORD, null) ?: return null
            val ivBase64 = prefs.getString(KEY_IV, null) ?: return null

            BiometricData(
                keyName = keyName,
                encryptedPassword = Base64.decode(encryptedPasswordBase64, Base64.DEFAULT),
                iv = Base64.decode(ivBase64, Base64.DEFAULT)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load encrypted data", e)
            null
        }
    }

    private fun storeEncryptedData(accountId: String, keyName: String, encryptedPassword: ByteArray, iv: ByteArray) {
        val prefs = context.getSharedPreferences("$PREFS_PREFIX$accountId", Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_NAME, keyName)
            .putString(KEY_ENCRYPTED_PASSWORD, Base64.encodeToString(encryptedPassword, Base64.DEFAULT))
            .putString(KEY_IV, Base64.encodeToString(iv, Base64.DEFAULT))
            .apply()
    }

    private suspend fun authenticateAndEncrypt(
        cipher: Cipher,
        data: ByteArray,
        promptTitle: String,
        promptDescription: String
    ): ByteArray? = suspendCancellableCoroutine { continuation ->
        val activity = context as? FragmentActivity
        if (activity == null) {
            Log.e(TAG, "Context is not a FragmentActivity")
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(promptTitle)
            .setDescription(promptDescription)
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)
            .build()

        val biometricPrompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    Log.e(TAG, "Authentication error: $errString")
                    continuation.resume(null)
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    try {
                        val encryptedData = result.cryptoObject?.cipher?.doFinal(data)
                        continuation.resume(encryptedData)
                    } catch (e: Exception) {
                        Log.e(TAG, "Encryption failed", e)
                        continuation.resume(null)
                    }
                }

                override fun onAuthenticationFailed() {
                    Log.w(TAG, "Authentication failed")
                }
            }
        )

        try {
            biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show biometric prompt", e)
            continuation.resume(null)
        }

        continuation.invokeOnCancellation {
            biometricPrompt.cancelAuthentication()
        }
    }

    private suspend fun authenticateAndDecrypt(
        cipher: Cipher,
        encryptedData: ByteArray,
        promptTitle: String,
        promptDescription: String
    ): ByteArray? = suspendCancellableCoroutine { continuation ->
        val activity = context as? FragmentActivity
        if (activity == null) {
            Log.e(TAG, "Context is not a FragmentActivity")
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(promptTitle)
            .setDescription(promptDescription)
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)
            .build()

        val biometricPrompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    Log.e(TAG, "Authentication error: $errString")
                    continuation.resume(null)
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    try {
                        val decryptedData = result.cryptoObject?.cipher?.doFinal(encryptedData)
                        continuation.resume(decryptedData)
                    } catch (e: Exception) {
                        Log.e(TAG, "Decryption failed", e)
                        continuation.resume(null)
                    }
                }

                override fun onAuthenticationFailed() {
                    Log.w(TAG, "Authentication failed")
                }
            }
        )

        try {
            biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show biometric prompt", e)
            continuation.resume(null)
        }

        continuation.invokeOnCancellation {
            biometricPrompt.cancelAuthentication()
        }
    }
}
