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
import net.jami.ui.contracts.AccountSettingsContract
import net.jami.ui.contracts.DeviceItem

/**
 * ViewModel for the account settings screen.
 *
 * Exposes split state flows (Tier 2): ProfileState and DevicesState.
 */
class AccountSettingsViewModel(
    private val accountService: AccountService,
    private val settingsRepository: SettingsRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _profileState = MutableStateFlow(AccountSettingsContract.ProfileState())
    val profileState: StateFlow<AccountSettingsContract.ProfileState> = _profileState.asStateFlow()

    private val _devicesState = MutableStateFlow(AccountSettingsContract.DevicesState())
    val devicesState: StateFlow<AccountSettingsContract.DevicesState> = _devicesState.asStateFlow()

    init {
        scope.launch {
            accountService.accountEvents.collect { event ->
                when (event) {
                    is AccountEvent.KnownDevicesChanged -> {
                        updateDevices(event.accountId, event.devices)
                    }
                    is AccountEvent.DeviceRevocationEnded -> {
                        if (event.state == 0) loadAccount()
                    }
                    is AccountEvent.ProfileReceived -> loadAccount()
                    is AccountEvent.DetailsChanged -> loadAccount()
                    else -> { /* Other events */ }
                }
            }
        }
    }

    fun onAction(action: AccountSettingsContract.Action) {
        when (action) {
            is AccountSettingsContract.Action.UpdateDisplayName -> {
                scope.launch {
                    val account = accountService.currentAccount.value ?: return@launch
                    accountService.updateProfile(account.accountId, action.name)
                    _profileState.value = _profileState.value.copy(displayName = action.name)
                }
            }
            is AccountSettingsContract.Action.RevokeDevice -> {
                scope.launch {
                    val account = accountService.currentAccount.value ?: return@launch
                    accountService.revokeDevice(
                        accountId = account.accountId,
                        deviceId = action.deviceId,
                        scheme = AccountService.ACCOUNT_SCHEME_PASSWORD,
                        password = action.password
                    )
                }
            }
            is AccountSettingsContract.Action.ExportAccount -> {
                val account = accountService.currentAccount.value ?: return
                accountService.exportToFile(
                    accountId = account.accountId,
                    path = action.path,
                    scheme = AccountService.ACCOUNT_SCHEME_PASSWORD,
                    password = action.password
                )
            }
        }
    }

    fun loadAccount() {
        scope.launch {
            _devicesState.value = _devicesState.value.copy(isLoading = true)
            try {
                val account = accountService.currentAccount.value ?: return@launch
                val accountId = account.accountId

                val displayName = account.details[ConfigKey.ACCOUNT_DISPLAYNAME.key] ?: ""
                val username = account.volatileDetails[ConfigKey.ACCOUNT_REGISTERED_NAME.key] ?: ""
                val identityHash = account.details[ConfigKey.ACCOUNT_USERNAME.key] ?: ""

                _profileState.value = AccountSettingsContract.ProfileState(
                    displayName = displayName,
                    username = username,
                    identityHash = identityHash,
                    avatarUri = null,
                )

                val knownDevices = accountService.getKnownRingDevices(accountId)
                val currentDeviceId = account.details[ConfigKey.ACCOUNT_DEVICE_ID.key] ?: ""
                val deviceItems = knownDevices.map { (id, name) ->
                    DeviceItem(
                        deviceId = id,
                        deviceName = name.ifEmpty { id },
                        isCurrent = id == currentDeviceId
                    )
                }

                _devicesState.value = AccountSettingsContract.DevicesState(
                    devices = deviceItems,
                    isLoading = false,
                )
            } catch (e: Exception) {
                _devicesState.value = _devicesState.value.copy(isLoading = false)
            }
        }
    }

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
        _devicesState.value = _devicesState.value.copy(devices = deviceItems)
    }

    fun onCleared() {
        scope.cancel()
    }
}
