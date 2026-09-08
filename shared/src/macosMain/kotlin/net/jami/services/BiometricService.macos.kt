package net.jami.services

import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import net.jami.utils.Log
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFRetain
import platform.CoreFoundation.CFStringRef
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSMutableDictionary
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Foundation.create
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAErrorBiometryNotAvailable
import platform.LocalAuthentication.LAErrorBiometryNotEnrolled
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics
import platform.Security.SecAccessControlCreateWithFlags
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecItemNotFound
import platform.Security.kSecAccessControlBiometryCurrentSet
import platform.Security.kSecAttrAccessControl
import platform.Security.kSecAttrAccessibleWhenUnlockedThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecUseAuthenticationContext
import platform.Security.kSecValueData
import platform.darwin.noErr
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * macOS biometric authentication backed by the Keychain. Mirrors the iOS implementation.
 *
 * The account password is stored as a generic-password item guarded by a
 * [SecAccessControlCreateWithFlags] policy of `biometryCurrentSet` +
 * `kSecAttrAccessibleWhenUnlockedThisDeviceOnly`, and the already-authenticated [LAContext] is
 * handed to the Keychain through `kSecUseAuthenticationContext` so Touch ID prompts once.
 *
 * Note on the previous implementation: its query builder did
 * `setValue(value, forKey = key.toString())` on the `CFStringRef` constants, producing keys like
 * `CPointer(raw=0x…)`, so every Keychain call would have failed with `errSecParam`. It also did
 * not compile — the Core Foundation types it referenced were never imported. macOS biometrics
 * have therefore never worked; there is no legacy data to migrate.
 */
@OptIn(ExperimentalForeignApi::class)
actual class BiometricService {

    private val defaults = platform.Foundation.NSUserDefaults.standardUserDefaults

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

    /**
     * Reads a plain marker rather than probing the Keychain: a biometry-guarded item cannot be
     * read without prompting, and asking whether the feature is on must never prompt.
     */
    actual suspend fun isEnabled(accountId: String): Boolean =
        defaults.boolForKey(enabledKey(accountId))

    actual suspend fun enroll(
        accountId: String,
        password: String,
        promptTitle: String,
        promptDescription: String,
    ): Boolean {
        if (password.isEmpty()) return false
        return try {
            val context = authenticate(promptTitle, promptDescription) ?: return false
            store(accountId, password, context).also { stored ->
                if (stored) defaults.setBool(true, forKey = enabledKey(accountId))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Biometric enrollment failed: ${e.message}")
            false
        }
    }

    actual suspend fun authenticate(
        accountId: String,
        promptTitle: String,
        promptDescription: String,
    ): BiometricResult {
        return try {
            val context = authenticate(promptTitle, promptDescription)
                ?: return BiometricResult.Cancelled
            val password = retrieve(accountId, context)
            if (password == null) {
                // Most often because macOS invalidated the item when the enrolled biometric
                // set changed. Clear the marker so the UI offers re-enrollment.
                defaults.setBool(false, forKey = enabledKey(accountId))
                return BiometricResult.Error("No stored credential", false)
            }
            BiometricResult.Success(password)
        } catch (e: Exception) {
            Log.e(TAG, "Biometric authentication failed: ${e.message}")
            BiometricResult.Error(e.message ?: "Authentication failed", false)
        }
    }

    actual suspend fun disable(accountId: String): Boolean {
        delete(accountId)
        defaults.removeObjectForKey(enabledKey(accountId))
        return true
    }

    // ==================== LocalAuthentication ====================

    /** Returns the authenticated context on success, or null if cancelled/unavailable. */
    private suspend fun authenticate(title: String, description: String): LAContext? =
        suspendCoroutine { cont ->
            val context = LAContext()
            if (!context.canEvaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, error = null)) {
                cont.resume(null)
                return@suspendCoroutine
            }
            context.evaluatePolicy(
                LAPolicyDeviceOwnerAuthenticationWithBiometrics,
                localizedReason = "$title - $description",
            ) { success, _ -> cont.resume(if (success) context else null) }
        }

    // ==================== Keychain ====================

    private fun store(accountId: String, password: String, context: LAContext): Boolean {
        delete(accountId)
        val accessControl = SecAccessControlCreateWithFlags(
            allocator = null,
            protection = kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
            flags = kSecAccessControlBiometryCurrentSet,
            error = null,
        ) ?: run {
            Log.e(TAG, "SecAccessControlCreateWithFlags failed")
            return false
        }
        val query = keychainQuery(accountId) {
            put(kSecValueData, password.encodeToByteArray().toNSData())
            put(kSecAttrAccessControl, CFBridgingRelease(accessControl))
            put(kSecUseAuthenticationContext, context)
        }
        val status = query.use { SecItemAdd(it, null) }
        if (status != noErr.toInt()) Log.e(TAG, "SecItemAdd failed: $status")
        return status == noErr.toInt()
    }

    private fun retrieve(accountId: String, context: LAContext): String? = memScoped {
        val query = keychainQuery(accountId) {
            put(kSecReturnData, NSNumber(bool = true))
            put(kSecMatchLimit, kSecMatchLimitOne)
            put(kSecUseAuthenticationContext, context)
        }
        val result = alloc<COpaquePointerVar>()
        val status = query.use { SecItemCopyMatching(it, result.ptr) }
        if (status != noErr.toInt()) {
            if (status != errSecItemNotFound) Log.e(TAG, "SecItemCopyMatching failed: $status")
            return null
        }
        val data = CFBridgingRelease(result.value) as? NSData ?: return null
        data.toByteArray().decodeToString()
    }

    private fun delete(accountId: String): Boolean {
        val status = keychainQuery(accountId).use { SecItemDelete(it) }
        return status == noErr.toInt() || status == errSecItemNotFound
    }

    /**
     * Builds a `CFDictionary` for the Security framework.
     *
     * The Core Foundation string constants are bridged to `NSString` keys rather than used
     * directly — putting the raw pointers into an NSDictionary yields keys like
     * `CPointer(raw=0x…)` and every query fails with `errSecParam`.
     */
    private fun keychainQuery(
        accountId: String,
        extra: NSMutableDictionary.() -> Unit = {},
    ): CFDictionaryRef {
        val dictionary = NSMutableDictionary().apply {
            put(kSecClass, CFBridgingRelease(CFRetain(kSecClassGenericPassword)))
            put(kSecAttrService, KEYCHAIN_SERVICE)
            put(kSecAttrAccount, accountId)
            extra()
        }
        return CFBridgingRetain(dictionary) as CFDictionaryRef
    }

    private fun NSMutableDictionary.put(key: CFStringRef?, value: Any?) {
        setObject(value, forKey = CFBridgingRelease(CFRetain(key)) as NSString)
    }

    /** Runs [block] with this query and always balances the `CFBridgingRetain` above. */
    private inline fun <T> CFDictionaryRef.use(block: (CFDictionaryRef) -> T): T =
        try {
            block(this)
        } finally {
            CFRelease(this)
        }

    // ==================== Helpers ====================

    private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }

    private fun NSData.toByteArray(): ByteArray {
        val size = length.toInt()
        val bytes = ByteArray(size)
        if (size > 0) bytes.usePinned { memcpy(it.addressOf(0), this.bytes, size.toULong()) }
        return bytes
    }

    private fun enabledKey(accountId: String) = "jami_biometric_enabled_$accountId"

    private companion object {
        const val TAG = "BiometricService"
        const val KEYCHAIN_SERVICE = "net.jami.biometric"
    }
}
