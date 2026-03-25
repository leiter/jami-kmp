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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.jami.services.AccountEvent
import net.jami.services.AccountService
import net.jami.services.LookupState

/**
 * State for the account creation screen.
 */
data class AccountCreationState(
    val username: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isCreated: Boolean = false,
    val usernameAvailable: Boolean? = null,
    val usernameCheckInProgress: Boolean = false,
    val isRegistering: Boolean = false
)

/**
 * ViewModel for the account creation wizard.
 *
 * Manages the creation of a new Jami account, including username
 * availability checking, password validation, and account registration
 * via the daemon.
 */
class AccountCreationViewModel(
    private val accountService: AccountService
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(AccountCreationState())
    val state: StateFlow<AccountCreationState> = _state.asStateFlow()

    private var lookupJob: Job? = null
    private var createdAccountId: String? = null

    init {
        // Observe account events for creation result and username lookup results
        scope.launch {
            accountService.accountEvents.collect { event ->
                when (event) {
                    is AccountEvent.RegistrationStateChanged -> {
                        handleRegistrationState(event)
                    }
                    is AccountEvent.RegisteredNameFound -> {
                        handleNameLookupResult(event)
                    }
                    is AccountEvent.NameRegistrationEnded -> {
                        handleNameRegistrationResult(event)
                    }
                    else -> { /* Other events not relevant here */ }
                }
            }
        }
    }

    /**
     * Update the username field.
     *
     * @param username New username value.
     */
    fun setUsername(username: String) {
        _state.value = _state.value.copy(
            username = username,
            usernameAvailable = null,
            usernameCheckInProgress = false,
            error = null
        )
        lookupJob?.cancel()
        if (username.isNotEmpty()) {
            lookupJob = scope.launch {
                _state.value = _state.value.copy(usernameCheckInProgress = true)
                delay(500)
                val currentAccountId = accountService.currentAccount.value?.accountId ?: ""
                accountService.lookupName(currentAccountId, username)
            }
        }
    }

    /**
     * Update the password field.
     *
     * @param password New password value.
     */
    fun setPassword(password: String) {
        _state.value = _state.value.copy(password = password, error = null)
    }

    /**
     * Update the confirm password field.
     *
     * @param confirmPassword New confirm password value.
     */
    fun setConfirmPassword(confirmPassword: String) {
        _state.value = _state.value.copy(confirmPassword = confirmPassword, error = null)
    }

    /**
     * Create a new Jami account with the current form values.
     *
     * Validates passwords match before attempting creation via the daemon.
     */
    fun createAccount() {
        val current = _state.value

        // Validate passwords
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
                createdAccountId = accountId

                // If a username was provided, register it
                if (current.username.isNotEmpty()) {
                    _state.value = _state.value.copy(isRegistering = true)
                    accountService.registerName(
                        accountId = accountId,
                        name = current.username,
                        password = current.password
                    )
                    // Timeout: if no response in 30s, show error
                    scope.launch {
                        delay(30_000)
                        if (_state.value.isRegistering) {
                            _state.value = _state.value.copy(
                                isRegistering = false,
                                error = "Username registration timed out"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Account creation failed"
                )
            }
        }
    }

    /**
     * Handle registration state changes from the daemon.
     */
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
            "TRYING" -> {
                // Still in progress, keep loading state
            }
            else -> { /* Other states */ }
        }
    }

    /**
     * Handle username lookup results from the name server.
     */
    private fun handleNameLookupResult(event: AccountEvent.RegisteredNameFound) {
        val currentUsername = _state.value.username
        if (event.name != currentUsername) return

        val lookupState = LookupState.fromInt(event.state)
        val isAvailable = lookupState == LookupState.NotFound
        _state.value = _state.value.copy(
            usernameAvailable = isAvailable,
            usernameCheckInProgress = false
        )
    }

    /**
     * Handle name registration completion.
     */
    private fun handleNameRegistrationResult(event: AccountEvent.NameRegistrationEnded) {
        _state.value = if (event.state == 0) {
            _state.value.copy(isRegistering = false)
        } else {
            _state.value.copy(
                isRegistering = false,
                error = "Username registration failed"
            )
        }
    }

    /**
     * Cancel the coroutine scope when this ViewModel is no longer needed.
     */
    fun onCleared() {
        scope.cancel()
    }
}
