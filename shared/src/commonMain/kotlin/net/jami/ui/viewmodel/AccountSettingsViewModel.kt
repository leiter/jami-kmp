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
    val avatarUri: String? = null,
    val devices: List<DeviceItem> = emptyList(),
    val isLoading: Boolean = false
)

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
                val deviceName = account.details[ConfigKey.ACCOUNT_DEVICE_NAME.key] ?: ""

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
                    avatarUri = null,
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
     *
     * @param name New display name.
     */
    fun updateDisplayName(name: String) {
        scope.launch {
            val account = accountService.currentAccount.value ?: return@launch
            accountService.updateProfile(account.accountId, name)
            _state.value = _state.value.copy(displayName = name)
        }
    }

    /**
     * Revoke (unlink) a device from the current account.
     *
     * @param deviceId ID of the device to revoke.
     * @param password Account password required for revocation.
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
     * Export the current account to a file.
     *
     * @param path Destination file path.
     * @param password Account password for encryption.
     * @return True if export succeeded.
     */
    fun exportAccount(path: String, password: String): Boolean {
        val account = accountService.currentAccount.value ?: return false
        return accountService.exportToFile(
            accountId = account.accountId,
            path = path,
            scheme = AccountService.ACCOUNT_SCHEME_PASSWORD,
            password = password
        )
    }

    /**
     * Export the current account to a generated file path.
     *
     * @param password Account password for encryption.
     * @return True if export succeeded.
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
