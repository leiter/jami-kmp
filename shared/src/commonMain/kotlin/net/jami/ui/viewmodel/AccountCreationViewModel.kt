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
import net.jami.services.AccountEvent
import net.jami.services.AccountService
import net.jami.services.DaemonBridge
import net.jami.services.LookupState
import net.jami.ui.contracts.CreateAccountContract

/**
 * ViewModel for the account creation wizard.
 */
class AccountCreationViewModel(
    private val accountService: AccountService,
    private val daemonBridge: DaemonBridge
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(CreateAccountContract.State())
    val state: StateFlow<CreateAccountContract.State> = _state.asStateFlow()

    init {
        scope.launch {
            accountService.accountEvents.collect { event ->
                when (event) {
                    is AccountEvent.RegistrationStateChanged -> handleRegistrationState(event)
                    is AccountEvent.RegisteredNameFound -> handleNameLookupResult(event)
                    is AccountEvent.NameRegistrationEnded -> handleNameRegistrationResult(event)
                    else -> { /* Other events not relevant here */ }
                }
            }
        }
    }

    fun onAction(action: CreateAccountContract.Action) {
        when (action) {
            is CreateAccountContract.Action.SetUsername -> {
                _state.value = _state.value.copy(
                    username = action.username,
                    usernameAvailable = null,
                    error = null
                )
            }
            is CreateAccountContract.Action.SetPassword -> {
                _state.value = _state.value.copy(password = action.password, error = null)
            }
            is CreateAccountContract.Action.SetConfirmPassword -> {
                _state.value = _state.value.copy(confirmPassword = action.confirmPassword, error = null)
            }
            CreateAccountContract.Action.CheckUsername -> checkUsernameAvailability()
            CreateAccountContract.Action.CreateAccount -> createAccount()
        }
    }

    private fun createAccount() {
        val current = _state.value

        if (current.password.isNotEmpty() && current.password != current.confirmPassword) {
            _state.value = current.copy(error = "Passwords do not match")
            return
        }

        scope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val password = current.password.ifEmpty { null }
                val accountId = accountService.createJamiAccount(
                    displayName = current.username,
                    password = password
                )

                if (current.username.isNotEmpty()) {
                    accountService.registerName(
                        accountId = accountId,
                        name = current.username,
                        password = current.password
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Account creation failed"
                )
            }
        }
    }

    private fun checkUsernameAvailability() {
        val username = _state.value.username
        if (username.isEmpty()) {
            _state.value = _state.value.copy(usernameAvailable = null)
            return
        }

        scope.launch {
            val currentAccountId = accountService.currentAccount.value?.accountId ?: ""
            accountService.lookupName(currentAccountId, username)
        }
    }

    private fun handleRegistrationState(event: AccountEvent.RegistrationStateChanged) {
        when (event.state) {
            "REGISTERED" -> {
                _state.value = _state.value.copy(isLoading = false, isCreated = true)
            }
            "ERROR_GENERIC", "ERROR_AUTH", "ERROR_NETWORK",
            "ERROR_HOST", "ERROR_SERVICE_UNAVAILABLE",
            "ERROR_NEED_MIGRATION" -> {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Registration failed: ${event.state}"
                )
            }
            "TRYING" -> { /* Still in progress */ }
            else -> { /* Other states */ }
        }
    }

    private fun handleNameLookupResult(event: AccountEvent.RegisteredNameFound) {
        val currentUsername = _state.value.username
        if (event.name != currentUsername) return

        val lookupState = LookupState.fromInt(event.state)
        val isAvailable = lookupState == LookupState.NotFound
        _state.value = _state.value.copy(usernameAvailable = isAvailable)
    }

    private fun handleNameRegistrationResult(event: AccountEvent.NameRegistrationEnded) {
        if (event.state != 0) {
            _state.value = _state.value.copy(error = "Username registration failed")
        }
    }

    fun onCleared() {
        scope.cancel()
    }
}
