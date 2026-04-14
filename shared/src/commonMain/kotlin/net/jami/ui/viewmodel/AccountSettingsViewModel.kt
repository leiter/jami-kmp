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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.jami.model.ConfigKey
import net.jami.repository.SettingsRepository
import net.jami.services.AccountEvent
import net.jami.services.AccountService
import net.jami.services.DeviceRuntimeService
import net.jami.utils.VCardUtils

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
            isLoading == other.isLoading
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
    private val deviceRuntimeService: DeviceRuntimeService? = null,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val scope = scope

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
                val currentDeviceName = account.details[ConfigKey.ACCOUNT_DEVICE_NAME.key] ?: ""

                // Load profile photo from disk
                val filesDir = deviceRuntimeService?.getDataPath() ?: ""
                val avatarBytes = if (filesDir.isNotEmpty())
                    VCardUtils.loadLocalProfileFromDisk(filesDir, accountId)
                else null

                // Load known devices
                val knownDevices = accountService.getKnownRingDevices(accountId)
                val currentDeviceId = account.details[ConfigKey.ACCOUNT_DEVICE_ID.key] ?: ""
                val deviceItems = knownDevices.map { (id, name) ->
                    DeviceItem(
                        deviceId = id,
                        deviceName = name.ifEmpty { id },
                        isCurrent = id == currentDeviceId
                    )
                }

                _state.value = AccountSettingsState(
                    displayName = displayName,
                    username = username,
                    identityHash = identityHash,
                    avatarBytes = avatarBytes,
                    isOnline = account.registrationState == net.jami.model.Account.RegistrationState.REGISTERED,
                    currentDeviceName = currentDeviceName,
                    devices = deviceItems,
                    isLoading = false
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
    fun exportAccount(password: String): Boolean {
        val account = accountService.currentAccount.value ?: return false
        val timestamp = net.jami.utils.currentTimeMillis()
        val dir = deviceRuntimeService?.getTempPath() ?: "/tmp"
        val path = "$dir/jami_export_${timestamp}.gz"
        return accountService.exportToFile(
            accountId = account.accountId,
            path = path,
            scheme = AccountService.ACCOUNT_SCHEME_PASSWORD,
            password = password
        )
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

    /**
     * Cancel the coroutine scope when this ViewModel is no longer needed.
     */
    fun onCleared() {
        scope.cancel()
    }
}
