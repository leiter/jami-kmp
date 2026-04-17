package net.jami.services

import kotlinx.cinterop.*
import platform.Foundation.*
import platform.LocalAuthentication.*
import platform.Security.*
import platform.darwin.noErr
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * iOS implementation of BiometricService using LocalAuthentication and Keychain.
 *
 * Security model:
 * - Passwords are stored in the iOS Keychain
 * - Keychain items are protected with biometric access control
 * - Access requires Face ID or Touch ID authentication
 * - Keychain items are automatically invalidated when biometrics change
 * - Data is stored with kSecAttrAccessibleWhenUnlockedThisDeviceOnly
 */
@OptIn(ExperimentalForeignApi::class)
actual class BiometricService {
    companion object {
        private const val TAG = "BiometricService"
        private const val KEYCHAIN_SERVICE = "net.jami.biometric"
    }

    actual suspend fun checkAvailability(): BiometricAvailability {
        return try {
            val context = LAContext()
            memScoped {
                val error = alloc<ObjCObjectVar<NSError?>>()
                val canEvaluate = context.canEvaluatePolicy(
                    LAPolicyDeviceOwnerAuthenticationWithBiometrics,
                    error.ptr
                )

                when {
                    canEvaluate -> BiometricAvailability.AVAILABLE
                    error.value?.code == LAErrorBiometryNotEnrolled.toLong() -> BiometricAvailability.NOT_ENROLLED
                    error.value?.code == LAErrorBiometryNotAvailable.toLong() -> BiometricAvailability.NO_HARDWARE
                    else -> BiometricAvailability.UNAVAILABLE
                }
            }
        } catch (e: Exception) {
            BiometricAvailability.UNKNOWN_ERROR
        }
    }

    actual suspend fun isEnabled(accountId: String): Boolean {
        return keychainItemExists(accountId)
    }

    actual suspend fun enroll(
        accountId: String,
        password: String,
        promptTitle: String,
        promptDescription: String
    ): Boolean {
        if (password.isEmpty()) {
            return false
        }

        return try {
            // Authenticate first
            val authenticated = authenticateUser(promptTitle, promptDescription)
            if (!authenticated) {
                return false
            }

            // Store password in Keychain with biometric protection
            storePasswordInKeychain(accountId, password)
        } catch (e: Exception) {
            NSLog("Biometric enrollment failed: ${e.message}")
            false
        }
    }

    actual suspend fun authenticate(
        accountId: String,
        promptTitle: String,
        promptDescription: String
    ): BiometricResult {
        return try {
            // Authenticate with biometrics
            val authenticated = authenticateUser(promptTitle, promptDescription)
            if (!authenticated) {
                return BiometricResult.Cancelled
            }

            // Retrieve password from Keychain
            val password = retrievePasswordFromKeychain(accountId)
                ?: return BiometricResult.Error("Failed to retrieve password from Keychain", false)

            BiometricResult.Success(password)
        } catch (e: Exception) {
            NSLog("Biometric authentication failed: ${e.message}")
            BiometricResult.Error(e.message ?: "Authentication failed", false)
        }
    }

    actual suspend fun disable(accountId: String): Boolean {
        return deletePasswordFromKeychain(accountId)
    }

    // Private helper methods

    private suspend fun authenticateUser(title: String, description: String): Boolean = suspendCoroutine { continuation ->
        val context = LAContext()

        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            val canEvaluate = context.canEvaluatePolicy(
                LAPolicyDeviceOwnerAuthenticationWithBiometrics,
                error.ptr
            )

            if (!canEvaluate) {
                NSLog("Cannot evaluate biometric policy: ${error.value?.localizedDescription}")
                continuation.resume(false)
                return@memScoped
            }

            context.evaluatePolicy(
                LAPolicyDeviceOwnerAuthenticationWithBiometrics,
                localizedReason = "$title - $description"
            ) { success, error ->
                if (success) {
                    continuation.resume(true)
                } else {
                    NSLog("Biometric authentication failed: ${error?.localizedDescription}")
                    continuation.resume(false)
                }
            }
        }
    }

    private fun storePasswordInKeychain(accountId: String, password: String): Boolean {
        memScoped {
            // Delete existing item first
            deletePasswordFromKeychain(accountId)

            // Create access control
            val error = alloc<ObjCObjectVar<CFErrorRef?>>()
            val accessControl = SecAccessControlCreateWithFlags(
                null,
                kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
                kSecAccessControlBiometryCurrentSet or kSecAccessControlOr,
                error.ptr
            )

            if (accessControl == null) {
                NSLog("Failed to create access control: ${error.value}")
                return false
            }

            // Create query dictionary
            val query = mapOf(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to KEYCHAIN_SERVICE,
                kSecAttrAccount to accountId,
                kSecValueData to password.encodeToByteArray().toNSData(),
                kSecAttrAccessControl to accessControl,
                kSecUseAuthenticationContext to LAContext() // Don't prompt again
            )

            val status = SecItemAdd(query.toNSDictionary() as CFDictionaryRef, null)

            if (status != noErr) {
                NSLog("Failed to store password in Keychain: $status")
                return false
            }

            NSLog("Password stored successfully in Keychain")
            return true
        }
    }

    private fun retrievePasswordFromKeychain(accountId: String): String? {
        memScoped {
            val query = mapOf(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to KEYCHAIN_SERVICE,
                kSecAttrAccount to accountId,
                kSecReturnData to kCFBooleanTrue,
                kSecMatchLimit to kSecMatchLimitOne
            )

            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query.toNSDictionary() as CFDictionaryRef, result.ptr)

            if (status != noErr) {
                NSLog("Failed to retrieve password from Keychain: $status")
                return null
            }

            val data = result.value as? NSData
            if (data == null) {
                NSLog("Retrieved data is null")
                return null
            }

            return data.toByteArray().decodeToString()
        }
    }

    private fun deletePasswordFromKeychain(accountId: String): Boolean {
        val query = mapOf(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to KEYCHAIN_SERVICE,
            kSecAttrAccount to accountId
        )

        val status = SecItemDelete(query.toNSDictionary() as CFDictionaryRef)

        // errSecItemNotFound means it wasn't there, which is fine
        return status == noErr || status == errSecItemNotFound
    }

    private fun keychainItemExists(accountId: String): Boolean {
        memScoped {
            val query = mapOf(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to KEYCHAIN_SERVICE,
                kSecAttrAccount to accountId,
                kSecReturnData to kCFBooleanFalse,
                kSecMatchLimit to kSecMatchLimitOne
            )

            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query.toNSDictionary() as CFDictionaryRef, result.ptr)

            return status == noErr
        }
    }

    // Extension functions for conversions

    private fun Map<Any?, Any?>.toNSDictionary(): NSDictionary {
        val dictionary = NSMutableDictionary()
        forEach { (key, value) ->
            dictionary.setValue(value, forKey = key.toString())
        }
        return dictionary
    }

    private fun ByteArray.toNSData(): NSData {
        return NSData.create(
            bytes = this.refTo(0).getPointer(MemScope()),
            length = this.size.toULong()
        )
    }

    private fun NSData.toByteArray(): ByteArray {
        val size = this.length.toInt()
        val bytes = ByteArray(size)
        if (size > 0) {
            bytes.usePinned { pinned ->
                memcpy(pinned.addressOf(0), this.bytes, size.toULong())
            }
        }
        return bytes
    }
}
