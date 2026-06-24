/*
 *  Copyright (C) 2004-2025 Savoir-faire Linux Inc.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package net.jami.ui.viewmodel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.jami.model.ConfigKey
import net.jami.repository.SettingsRepository
import net.jami.services.AccountEvent
import net.jami.services.AccountService
import net.jami.services.AuthError
import net.jami.services.AuthState
import net.jami.services.LookupState
import net.jami.services.BiometricAvailability
import net.jami.services.BiometricResult
import net.jami.services.BiometricService
import net.jami.services.DeviceRuntimeService
import net.jami.utils.VCardUtils
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * State machine for the export side of the add-device protocol (existing device).
 * Mirrors AddDeviceExportState in jami-client-android ExportSideViewModel.
 */
sealed class AddDeviceExportState {
    /** Sheet just opened — waiting for the user to scan/paste a jami-auth URI. */
    data class Init(val error: ExportInputError? = null) : AddDeviceExportState()
    /** URI submitted; daemon connecting to the new device. */
    object Connecting : AddDeviceExportState()
    /** New device identified; showing peer address for user to confirm. */
    data class Authenticating(val peerAddress: String?) : AddDeviceExportState()
    /** User confirmed; account sync in progress. */
    object InProgress : AddDeviceExportState()
    /** Terminal state — null error means success. */
    data class Done(val error: AuthError? = null) : AddDeviceExportState()
}

enum class ExportInputError { INVALID_INPUT }

/**
 * Item representing a linked device.
 */
data class DeviceItem(
    val deviceId: String,
    val deviceName: String,
    val isCurrent: Boolean
)

/**
 * State for the account settings screen.
 */
data class AccountSettingsState(
    val displayName: String = "",
    val username: String = "",
    val identityHash: String = "",
    val avatarBytes: ByteArray? = null,
    val isOnline: Boolean = false,
    val currentDeviceName: String = "",
    val devices: List<DeviceItem> = emptyList(),
    val isLoading: Boolean = false,
    val hasPassword: Boolean = false,
    val hasManager: Boolean = false,
    val hasBiometric: Boolean = false,
    val biometricAvailability: BiometricAvailability = BiometricAvailability.UNKNOWN_ERROR,
    /** True when the active account is a SIP account (not a Jami/DHT account). */
    val isSip: Boolean = false,
    val sipHostname: String = "",
    val sipUsername: String = "",
    // Link device state — drives the multi-step export-side sheet
    val linkDeviceState: AddDeviceExportState = AddDeviceExportState.Init(),
    // Register name dialog state
    val registerNameDialogOpen: Boolean = false,
    val registerNameInput: String = "",
    val registerNameChecking: Boolean = false,
    val registerNameAvailable: Boolean? = null,
    val registerNameError: UsernameCheckError? = null,
    val registerNameInProgress: Boolean = false,
    /** null = no result yet, true = success, false = failed */
    val registerNameResult: Boolean? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AccountSettingsState) return false
        return displayName == other.displayName &&
            username == other.username &&
            identityHash == other.identityHash &&
            (avatarBytes?.contentEquals(other.avatarBytes ?: return false) ?: (other.avatarBytes == null)) &&
            isOnline == other.isOnline &&
            currentDeviceName == other.currentDeviceName &&
            devices == other.devices &&
            isLoading == other.isLoading &&
            hasPassword == other.hasPassword &&
            hasManager == other.hasManager &&
            hasBiometric == other.hasBiometric &&
            isSip == other.isSip &&
            sipHostname == other.sipHostname &&
            sipUsername == other.sipUsername &&
            linkDeviceState == other.linkDeviceState &&
            registerNameDialogOpen == other.registerNameDialogOpen &&
            registerNameInput == other.registerNameInput &&
            registerNameChecking == other.registerNameChecking &&
            registerNameAvailable == other.registerNameAvailable &&
            registerNameError == other.registerNameError &&
            registerNameInProgress == other.registerNameInProgress &&
            registerNameResult == other.registerNameResult
    }

    override fun hashCode(): Int {
        var result = displayName.hashCode()
        result = 31 * result + username.hashCode()
        result = 31 * result + identityHash.hashCode()
        result = 31 * result + (avatarBytes?.contentHashCode() ?: 0)
        result = 31 * result + isOnline.hashCode()
        result = 31 * result + currentDeviceName.hashCode()
        result = 31 * result + devices.hashCode()
        result = 31 * result + isLoading.hashCode()
        result = 31 * result + hasPassword.hashCode()
        result = 31 * result + isSip.hashCode()
        result = 31 * result + sipHostname.hashCode()
        result = 31 * result + sipUsername.hashCode()
        result = 31 * result + hasManager.hashCode()
        result = 31 * result + hasBiometric.hashCode()
        result = 31 * result + linkDeviceState.hashCode()
        result = 31 * result + registerNameDialogOpen.hashCode()
        result = 31 * result + registerNameInput.hashCode()
        result = 31 * result + registerNameChecking.hashCode()
        result = 31 * result + registerNameAvailable.hashCode()
        result = 31 * result + registerNameError.hashCode()
        result = 31 * result + registerNameInProgress.hashCode()
        result = 31 * result + registerNameResult.hashCode()
        return result
    }
}

/**
 * ViewModel for the account settings screen.
 *
 * Displays and allows editing of account profile information, linked
 * devices management, and account export. Observes daemon events for
 * device changes and profile updates.
 */
class AccountSettingsViewModel(
    private val accountService: AccountService,
    private val settingsRepository: SettingsRepository,
    private val biometricService: BiometricService? = null,
    private val deviceRuntimeService: DeviceRuntimeService? = null,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val scope = scope
    private var lookupJob: Job? = null
    /** Operation ID of the in-flight add-device export, or null if none. */
    private var _linkOperationId: Long? = null

    private val _state = MutableStateFlow(AccountSettingsState())
    val state: StateFlow<AccountSettingsState> = _state.asStateFlow()

    init {
        // Observe device and profile changes
        scope.launch {
            accountService.accountEvents.collect { event ->
                when (event) {
                    is AccountEvent.KnownDevicesChanged -> {
                        updateDevices(event.accountId, event.devices)
                    }
                    is AccountEvent.DeviceRevocationEnded -> {
                        if (event.state == 0) {
                            loadAccount()
                        }
                    }
                    is AccountEvent.ProfileReceived -> {
                        loadAccount()
                    }
                    is AccountEvent.DetailsChanged -> {
                        loadAccount()
                    }
                    is AccountEvent.RegistrationStateChanged -> {
                        val account = accountService.currentAccount.value ?: return@collect
                        if (account.accountId == event.accountId) {
                            _state.value = _state.value.copy(
                                isOnline = account.registrationState == net.jami.model.Account.RegistrationState.REGISTERED
                            )
                        }
                    }
                    is AccountEvent.AddDeviceStateChanged -> {
                        val account = accountService.currentAccount.value ?: return@collect
                        if (event.accountId != account.accountId) return@collect
                        val opId = _linkOperationId ?: return@collect
                        if (event.opId != opId) return@collect
                        val newState = when (AuthState.fromInt(event.state)) {
                            AuthState.CONNECTING ->
                                AddDeviceExportState.Connecting
                            AuthState.AUTHENTICATING ->
                                AddDeviceExportState.Authenticating(event.details["peer_address"])
                            AuthState.IN_PROGRESS ->
                                AddDeviceExportState.InProgress
                            AuthState.DONE -> {
                                _linkOperationId = null
                                val errStr = event.details["error"]
                                val error = if (errStr.isNullOrEmpty() || errStr == "none") null
                                            else AuthError.fromString(errStr)
                                AddDeviceExportState.Done(error)
                            }
                            else -> return@collect
                        }
                        _state.update { it.copy(linkDeviceState = newState) }
                    }
                    is AccountEvent.NameRegistrationEnded -> {
                        val account = accountService.currentAccount.value ?: return@collect
                        if (event.accountId == account.accountId) {
                            val success = event.state == 0
                            _state.update { it.copy(
                                registerNameInProgress = false,
                                registerNameResult = success,
                                registerNameDialogOpen = false,
                                // Refresh username from account if registration succeeded
                            )}
                            if (success) loadAccount()
                        }
                    }
                    else -> { /* Other events */ }
                }
            }
        }
    }

    /**
     * Load the current account details and populate the state.
     */
    fun loadAccount() {
        scope.launch {
            _state.value = _state.value.copy(isLoading = true)
            try {
                val account = accountService.currentAccount.value ?: return@launch
                val accountId = account.accountId

                val displayName = account.details[ConfigKey.ACCOUNT_DISPLAYNAME.key] ?: ""
                val username = account.volatileDetails[ConfigKey.ACCOUNT_REGISTERED_NAME.key] ?: ""
                val identityHash = account.details[ConfigKey.ACCOUNT_USERNAME.key] ?: ""
                var currentDeviceName = account.details[ConfigKey.ACCOUNT_DEVICE_NAME.key] ?: ""
                // Daemon defaults to the system hostname ("localhost" on Android).
                // Auto-correct it to the actual device model on first load.
                if (currentDeviceName.isBlank() || currentDeviceName.equals("localhost", ignoreCase = true)) {
                    val localName = deviceRuntimeService?.getLocalDeviceName() ?: ""
                    if (localName.isNotBlank()) {
                        accountService.renameDevice(accountId, localName)
                        currentDeviceName = localName
                    }
                }

                // Load profile photo from disk
                val filesDir = deviceRuntimeService?.getDataPath() ?: ""
                val avatarBytes = if (filesDir.isNotEmpty())
                    VCardUtils.loadLocalProfileFromDisk(filesDir, accountId)
                else null

                // Prefer account.devices (kept in sync by onKnownDevicesChanged) over the
                // JNI call which may return empty before the daemon has synced from DHT.
                // Fall back to the JNI call only when the model cache is empty, and
                // if that also returns nothing keep whatever the state already has so a
                // correct list from a prior KnownDevicesChanged event is not overwritten.
                val currentDeviceId = account.details[ConfigKey.ACCOUNT_DEVICE_ID.key] ?: ""
                val knownDevices = account.devices.ifEmpty {
                    accountService.getKnownRingDevices(accountId)
                }
                val deviceItems = if (knownDevices.isNotEmpty()) {
                    knownDevices.map { (id, name) ->
                        DeviceItem(
                            deviceId = id,
                            deviceName = name.ifEmpty { id },
                            isCurrent = id == currentDeviceId
                        )
                    }
                } else {
                    _state.value.devices
                }

                val hasPassword = account.details[ConfigKey.ACCOUNT_ARCHIVE_HAS_PASSWORD.key]?.toBoolean() ?: false
                val hasManager = account.details[ConfigKey.ACCOUNT_MANAGER_URI.key]?.isNotEmpty() ?: false

                // Load biometric state
                val biometricAvailability = biometricService?.checkAvailability()
                    ?: BiometricAvailability.NO_HARDWARE
                val hasBiometric = if (biometricAvailability == BiometricAvailability.AVAILABLE ||
                    biometricAvailability == BiometricAvailability.NOT_ENROLLED)
                    biometricService?.isEnabled(accountId) ?: false
                else false

                _state.value = AccountSettingsState(
                    displayName = displayName,
                    username = username,
                    identityHash = identityHash,
                    avatarBytes = avatarBytes,
                    isOnline = account.registrationState == net.jami.model.Account.RegistrationState.REGISTERED,
                    currentDeviceName = currentDeviceName,
                    devices = deviceItems,
                    isLoading = false,
                    hasPassword = hasPassword,
                    hasManager = hasManager,
                    hasBiometric = hasBiometric,
                    biometricAvailability = biometricAvailability,
                    isSip = account.isSip,
                    sipHostname = account.host,
                    sipUsername = account.username,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    /**
     * Update the display name for the current account.
     */
    fun updateDisplayName(name: String) {
        scope.launch {
            val account = accountService.currentAccount.value ?: return@launch
            accountService.updateProfile(account.accountId, name)
            _state.value = _state.value.copy(displayName = name)
        }
    }

    /**
     * Toggle the account online/offline.
     */
    fun setOnline(enabled: Boolean) {
        val account = accountService.currentAccount.value ?: return
        accountService.setAccountEnabled(account.accountId, enabled)
        _state.value = _state.value.copy(isOnline = enabled)
    }

    /**
     * Revoke (unlink) a device from the current account.
     */
    fun revokeDevice(deviceId: String, password: String) {
        scope.launch {
            val account = accountService.currentAccount.value ?: return@launch
            accountService.revokeDevice(
                accountId = account.accountId,
                deviceId = deviceId,
                scheme = AccountService.ACCOUNT_SCHEME_PASSWORD,
                password = password
            )
        }
    }

    /**
     * Export the current account to a generated file path.
     */
    /**
     * Export the account archive to a temporary path.
     *
     * @return The file path of the exported archive on success, or null if the export failed.
     *         The caller should present the file to the user (e.g. via a share sheet on iOS or
     *         a file-sharing intent on Android) so it is not lost in the sandbox temp dir.
     */
    fun exportAccount(password: String): String? {
        val account = accountService.currentAccount.value ?: return null
        val timestamp = net.jami.utils.currentTimeMillis()
        val dir = deviceRuntimeService?.getTempPath() ?: "/tmp"
        val path = "$dir/jami_export_${timestamp}.gz"
        val success = accountService.exportToFile(
            accountId = account.accountId,
            path = path,
            scheme = AccountService.ACCOUNT_SCHEME_PASSWORD,
            password = password
        )
        return if (success) path else null
    }

    /**
     * Change (or set/clear) the account archive password.
     *
     * @param oldPassword Current password, or empty string if the account has no password.
     * @param newPassword New password, or empty string to remove the password.
     * @return true if the password was changed successfully.
     */
    fun changePassword(oldPassword: String, newPassword: String): Boolean {
        val account = accountService.currentAccount.value ?: return false
        val success = accountService.changeAccountPassword(account.accountId, oldPassword, newPassword)
        if (success) {
            _state.update { it.copy(hasPassword = newPassword.isNotEmpty()) }
        }
        return success
    }

    /**
     * Enable biometric authentication for the current account.
     * Shows enrollment dialog, confirms password, then enables biometric.
     *
     * @param password The account password to encrypt
     * @return Result indicating success or failure with error message
     */
    suspend fun enableBiometric(password: String): Result<Unit> {
        val account = accountService.currentAccount.value ?: return Result.failure(Exception("No account"))

        // Check device capability first
        return when (biometricService?.checkAvailability()) {
            BiometricAvailability.AVAILABLE -> {
                val success = biometricService?.enroll(
                    accountId = account.accountId,
                    password = password,
                    promptTitle = "Enable biometric authentication",
                    promptDescription = "Authenticate to secure your account password"
                ) ?: false
                if (success) {
                    _state.value = _state.value.copy(hasBiometric = true)
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Enrollment failed"))
                }
            }
            BiometricAvailability.NOT_ENROLLED -> {
                Result.failure(Exception("No biometrics enrolled on device"))
            }
            BiometricAvailability.NO_HARDWARE -> {
                Result.failure(Exception("Biometric hardware not available"))
            }
            else -> {
                Result.failure(Exception("Biometric unavailable"))
            }
        }
    }

    /**
     * Disable biometric authentication for the current account.
     *
     * @return Result indicating success or failure
     */
    suspend fun disableBiometric(): Result<Unit> {
        val account = accountService.currentAccount.value ?: return Result.failure(Exception("No account"))

        val success = biometricService?.disable(account.accountId) ?: false
        if (success) {
            _state.value = _state.value.copy(hasBiometric = false)
            return Result.success(Unit)
        }
        return Result.failure(Exception("Failed to disable biometric"))
    }

    /**
     * Authenticate using biometric and return decrypted password.
     * Used for account export, password change, etc.
     *
     * @param title Title for the biometric prompt
     * @param description Description for the biometric prompt
     * @return BiometricResult with decrypted password or error
     */
    suspend fun authenticateWithBiometric(
        title: String,
        description: String
    ): BiometricResult {
        val account = accountService.currentAccount.value ?: return BiometricResult.Error("No account", false)
        return biometricService?.authenticate(account.accountId, title, description)
            ?: BiometricResult.Error("Biometric not available", false)
    }

    /**
     * Update the avatar photo for the current account.
     * @param bytes Raw image bytes (JPEG/PNG), or null to clear the avatar.
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun updateAvatar(bytes: ByteArray?) {
        scope.launch {
            val account = accountService.currentAccount.value ?: return@launch
            val base64 = bytes?.let { Base64.encode(it) } ?: ""
            val flag = if (bytes != null) 1 else 2  // 1 = base64 data, 2 = clear
            accountService.updateProfile(account.accountId, _state.value.displayName, base64, "image/jpeg", flag)
            _state.update { it.copy(avatarBytes = bytes) }
        }
    }

    /**
     * Rename the current device.
     */
    fun renameCurrentDevice(name: String) {
        scope.launch {
            val account = accountService.currentAccount.value ?: return@launch
            accountService.renameDevice(account.accountId, name)
            _state.update { it.copy(currentDeviceName = name) }
        }
    }

    /**
     * Initiate linking a new device using its jami-auth:// URI (scanned or pasted by user).
     * Progress arrives as [AccountEvent.AddDeviceStateChanged] and drives [AddDeviceExportState].
     */
    fun startLinkDevice(uri: String) {
        // Validate format — jami-auth:// URIs are either 59 or 83 chars
        if (uri.isEmpty() || !uri.startsWith("jami-auth://") ||
            (uri.length != 59 && uri.length != 83)) {
            _state.update { it.copy(linkDeviceState = AddDeviceExportState.Init(ExportInputError.INVALID_INPUT)) }
            return
        }
        scope.launch {
            val account = accountService.currentAccount.value ?: return@launch
            val opId = accountService.addDevice(account.accountId, uri)
            if (opId < 0) {
                _state.update { it.copy(linkDeviceState = AddDeviceExportState.Init(ExportInputError.INVALID_INPUT)) }
                return@launch
            }
            _linkOperationId = opId
            // State will advance through the state machine via AddDeviceStateChanged callbacks
        }
    }

    /**
     * Confirm the peer identity during step 2 of the export-side handshake.
     * Call when the user taps "Yes, this is the right device".
     */
    fun onIdentityConfirmation() {
        scope.launch {
            val account = accountService.currentAccount.value ?: return@launch
            val opId = _linkOperationId ?: return@launch
            accountService.confirmAddDevice(account.accountId, opId)
        }
    }

    /**
     * Cancel the in-progress link-device operation and reset the sheet to its initial state.
     */
    fun cancelLinkDevice() {
        scope.launch {
            val account = accountService.currentAccount.value ?: return@launch
            val opId = _linkOperationId
            if (opId != null) {
                accountService.cancelAddDevice(account.accountId, opId)
                _linkOperationId = null
            }
            _state.update { it.copy(linkDeviceState = AddDeviceExportState.Init()) }
        }
    }

    /**
     * Update the device list from a daemon event.
     */
    private fun updateDevices(accountId: String, devices: Map<String, String>) {
        val account = accountService.currentAccount.value ?: return
        if (account.accountId != accountId) return

        val currentDeviceId = account.details[ConfigKey.ACCOUNT_DEVICE_ID.key] ?: ""
        val deviceItems = devices.map { (id, name) ->
            DeviceItem(
                deviceId = id,
                deviceName = name.ifEmpty { id },
                isCurrent = id == currentDeviceId
            )
        }
        _state.value = _state.value.copy(devices = deviceItems)
    }

    // ── Register Name ─────────────────────────────────────────────────────────

    fun openRegisterNameDialog() {
        _state.update { it.copy(
            registerNameDialogOpen = true,
            registerNameInput = "",
            registerNameChecking = false,
            registerNameAvailable = null,
            registerNameError = null,
            registerNameResult = null,
        )}
    }

    fun dismissRegisterNameDialog() {
        lookupJob?.cancel()
        _state.update { it.copy(
            registerNameDialogOpen = false,
            registerNameInput = "",
            registerNameChecking = false,
            registerNameAvailable = null,
            registerNameError = null,
        )}
    }

    fun setRegisterNameInput(name: String) {
        val trimmed = name.trim()
        _state.update { it.copy(
            registerNameInput = trimmed,
            registerNameAvailable = null,
            registerNameChecking = false,
            registerNameError = null,
        )}
        lookupJob?.cancel()
        if (trimmed.isEmpty()) return
        lookupJob = scope.launch {
            _state.update { it.copy(registerNameChecking = true) }
            delay(500)
            if (trimmed != _state.value.registerNameInput) return@launch
            val accountId = accountService.currentAccount.value?.accountId ?: ""
            try {
                val result = accountService.findRegistrationByName(accountId, "", trimmed)
                if (trimmed != _state.value.registerNameInput) return@launch
                _state.update { s -> s.copy(
                    registerNameChecking = false,
                    registerNameAvailable = when (result.state) {
                        LookupState.NotFound -> true
                        else -> false
                    },
                    registerNameError = when (result.state) {
                        LookupState.Invalid -> UsernameCheckError.INVALID
                        LookupState.NetworkError -> UsernameCheckError.NETWORK_ERROR
                        else -> null
                    },
                )}
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (trimmed == _state.value.registerNameInput) {
                    _state.update { it.copy(registerNameChecking = false, registerNameError = UsernameCheckError.NETWORK_ERROR) }
                }
            }
        }
    }

    fun confirmRegisterName(password: String = "") {
        val name = _state.value.registerNameInput.trim()
        if (name.isEmpty() || _state.value.registerNameAvailable != true) return
        val account = accountService.currentAccount.value ?: return
        _state.update { it.copy(registerNameInProgress = true) }
        val scheme = if (password.isNotEmpty()) AccountService.ACCOUNT_SCHEME_PASSWORD else ""
        accountService.registerName(account.accountId, name, scheme, password)
    }

    fun clearRegisterNameResult() {
        _state.update { it.copy(registerNameResult = null) }
    }

    /**
     * Remove (delete) the current account from this device.
     */
    fun removeAccount() {
        val account = accountService.currentAccount.value ?: return
        accountService.removeAccount(account.accountId)
    }

    /**
     * Cancel the coroutine scope when this ViewModel is no longer needed.
     */
    fun onCleared() {
        scope.cancel()
    }
}
