package net.jami.services

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.ExperimentalForeignApi
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
import platform.Foundation.NSMutableDictionary
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Foundation.NSUserDefaults
import platform.Foundation.create
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthentication
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
 * iOS biometric authentication backed by the Keychain.
 *
 * The account password is stored as a generic-password Keychain item guarded by a
 * [SecAccessControlCreateWithFlags] policy of `biometryCurrentSet` +
 * `kSecAttrAccessibleWhenUnlockedThisDeviceOnly`. That means the item:
 * - never leaves the device and is not included in backups,
 * - is readable only after a successful biometric evaluation,
 * - is invalidated automatically by iOS when the enrolled biometric set changes.
 *
 * The already-authenticated [LAContext] is handed to the Keychain via
 * `kSecUseAuthenticationContext`, so enrolling or unlocking shows exactly one prompt
 * rather than two.
 *
 * Earlier builds stored the password as plaintext in NSUserDefaults. [migrateLegacyEntry]
 * moves any such value into the Keychain and deletes it; it needs no prompt, because
 * writing a biometry-guarded item does not require authentication, only reading does.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
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

    /**
     * Deliberately reads a plain marker rather than probing the Keychain: a biometry-guarded
     * item cannot be read without showing a prompt, and merely asking whether the feature is
     * on must never prompt the user.
     */
    actual suspend fun isEnabled(accountId: String): Boolean {
        migrateLegacyEntry(accountId)
        return defaults.boolForKey(enabledKey(accountId))
    }

    actual suspend fun enroll(
        accountId: String,
        password: String,
        promptTitle: String,
        promptDescription: String
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
        promptDescription: String
    ): BiometricResult {
        return try {
            migrateLegacyEntry(accountId)
            val context = authenticate(promptTitle, promptDescription)
                ?: return BiometricResult.Cancelled
            val password = retrieve(accountId, context)
            if (password == null) {
                // The item is gone — most often because iOS invalidated it when the
                // enrolled biometric set changed. Clear the marker so the UI offers
                // re-enrollment instead of failing on every launch.
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
        defaults.removeObjectForKey(legacyKey(accountId))
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
                localizedReason = "$title - $description"
            ) { success, _ -> cont.resume(if (success) context else null) }
        }

    // ==================== Keychain ====================

    private fun store(accountId: String, password: String, context: LAContext): Boolean = memScoped {
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
        status == noErr.toInt()
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
     * The Core Foundation string constants (`kSecClass` and friends) are bridged to
     * `NSString` keys rather than used as dictionary keys directly — putting the raw
     * pointers in an NSDictionary yields keys like `CPointer(raw=0x…)` and every query
     * silently fails with `errSecParam`.
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

    // ==================== Migration ====================

    /**
     * Moves a password written by an earlier build — plaintext, in NSUserDefaults — into the
     * Keychain, then deletes the plaintext copy.
     */
    private fun migrateLegacyEntry(accountId: String) {
        val legacy = defaults.stringForKey(legacyKey(accountId)) ?: return
        Log.w(TAG, "Migrating legacy plaintext credential to the Keychain")
        // No LAContext: writing a biometry-guarded item needs no authentication.
        val migrated = memScoped {
            val accessControl = SecAccessControlCreateWithFlags(
                allocator = null,
                protection = kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
                flags = kSecAccessControlBiometryCurrentSet,
                error = null,
            ) ?: return@memScoped false
            delete(accountId)
            val query = keychainQuery(accountId) {
                put(kSecValueData, legacy.encodeToByteArray().toNSData())
                put(kSecAttrAccessControl, CFBridgingRelease(accessControl))
            }
            query.use { SecItemAdd(it, null) } == noErr.toInt()
        }
        if (migrated) {
            defaults.setBool(true, forKey = enabledKey(accountId))
            defaults.removeObjectForKey(legacyKey(accountId))
            Log.d(TAG, "Legacy credential migrated; plaintext copy removed")
        } else {
            Log.e(TAG, "Legacy credential migration failed; leaving the old value in place")
        }
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

    /** Key used by builds that stored the password in plaintext. */
    private fun legacyKey(accountId: String) = "jami_biometric_$accountId"

    private companion object {
        const val TAG = "BiometricService"
        const val KEYCHAIN_SERVICE = "net.jami.biometric"
    }
}
