package net.jami.services

import kotlinx.cinterop.*
import platform.Foundation.*
import platform.LocalAuthentication.*
import platform.Security.*
import platform.darwin.noErr
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@OptIn(ExperimentalForeignApi::class)
actual class BiometricService {
    companion object {
        private const val KEYCHAIN_SERVICE = "net.jami.biometric"
    }

    actual suspend fun checkAvailability(): BiometricAvailability {
        return try {
            val context = LAContext()
            memScoped {
                val error = alloc<ObjCObjectVar<NSError?>>()
                val canEvaluate = context.canEvaluatePolicy(
                    LAPolicyDeviceOwnerAuthenticationWithBiometrics,
                    error.ptr,
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

    actual suspend fun isEnabled(accountId: String): Boolean = keychainItemExists(accountId)

    actual suspend fun enroll(
        accountId: String,
        password: String,
        promptTitle: String,
        promptDescription: String,
    ): Boolean {
        if (password.isEmpty()) return false
        return try {
            if (!authenticateUser(promptTitle, promptDescription)) return false
            storePasswordInKeychain(accountId, password)
        } catch (e: Exception) {
            NSLog("Biometric enrollment failed: ${e.message}")
            false
        }
    }

    actual suspend fun authenticate(
        accountId: String,
        promptTitle: String,
        promptDescription: String,
    ): BiometricResult {
        return try {
            if (!authenticateUser(promptTitle, promptDescription)) return BiometricResult.Cancelled
            val password = retrievePasswordFromKeychain(accountId)
                ?: return BiometricResult.Error("Failed to retrieve password from Keychain", false)
            BiometricResult.Success(password)
        } catch (e: Exception) {
            NSLog("Biometric authentication failed: ${e.message}")
            BiometricResult.Error(e.message ?: "Authentication failed", false)
        }
    }

    actual suspend fun disable(accountId: String): Boolean = deletePasswordFromKeychain(accountId)

    private suspend fun authenticateUser(title: String, description: String): Boolean = suspendCoroutine { continuation ->
        val context = LAContext()
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            val canEvaluate = context.canEvaluatePolicy(
                LAPolicyDeviceOwnerAuthenticationWithBiometrics,
                error.ptr,
            )
            if (!canEvaluate) {
                continuation.resume(false)
                return@memScoped
            }
            context.evaluatePolicy(
                LAPolicyDeviceOwnerAuthenticationWithBiometrics,
                localizedReason = "$title - $description",
            ) { success, _ -> continuation.resume(success) }
        }
    }

    private fun storePasswordInKeychain(accountId: String, password: String): Boolean {
        memScoped {
            deletePasswordFromKeychain(accountId)
            val error = alloc<ObjCObjectVar<CFErrorRef?>>()
            val accessControl = SecAccessControlCreateWithFlags(
                null,
                kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
                kSecAccessControlBiometryCurrentSet or kSecAccessControlOr,
                error.ptr,
            ) ?: return false
            val query = mapOf(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to KEYCHAIN_SERVICE,
                kSecAttrAccount to accountId,
                kSecValueData to password.encodeToByteArray().toNSData(),
                kSecAttrAccessControl to accessControl,
                kSecUseAuthenticationContext to LAContext(),
            )
            val status = SecItemAdd(query.toNSDictionary() as CFDictionaryRef, null)
            return status == noErr
        }
    }

    private fun retrievePasswordFromKeychain(accountId: String): String? {
        memScoped {
            val query = mapOf(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to KEYCHAIN_SERVICE,
                kSecAttrAccount to accountId,
                kSecReturnData to kCFBooleanTrue,
                kSecMatchLimit to kSecMatchLimitOne,
            )
            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query.toNSDictionary() as CFDictionaryRef, result.ptr)
            if (status != noErr) return null
            val data = result.value as? NSData ?: return null
            return data.toByteArray().decodeToString()
        }
    }

    private fun deletePasswordFromKeychain(accountId: String): Boolean {
        val query = mapOf(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to KEYCHAIN_SERVICE,
            kSecAttrAccount to accountId,
        )
        val status = SecItemDelete(query.toNSDictionary() as CFDictionaryRef)
        return status == noErr || status == errSecItemNotFound
    }

    private fun keychainItemExists(accountId: String): Boolean {
        memScoped {
            val query = mapOf(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to KEYCHAIN_SERVICE,
                kSecAttrAccount to accountId,
                kSecReturnData to kCFBooleanFalse,
                kSecMatchLimit to kSecMatchLimitOne,
            )
            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query.toNSDictionary() as CFDictionaryRef, result.ptr)
            return status == noErr
        }
    }

    private fun Map<Any?, Any?>.toNSDictionary(): NSDictionary {
        val dictionary = NSMutableDictionary()
        forEach { (key, value) -> dictionary.setValue(value, forKey = key.toString()) }
        return dictionary
    }

    private fun ByteArray.toNSData(): NSData =
        NSData.create(bytes = this.refTo(0).getPointer(MemScope()), length = this.size.toULong())

    private fun NSData.toByteArray(): ByteArray {
        val size = this.length.toInt()
        val bytes = ByteArray(size)
        if (size > 0) bytes.usePinned { pinned -> memcpy(pinned.addressOf(0), this.bytes, size.toULong()) }
        return bytes
    }
}
