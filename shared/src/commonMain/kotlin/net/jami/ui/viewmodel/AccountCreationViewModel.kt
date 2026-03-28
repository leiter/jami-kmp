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
import net.jami.utils.Log

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
    val isRegistering: Boolean = false,
    val usernameCheckError: String? = null  // non-null when lookup failed (e.g. network error)
)

/**
 * ViewModel for the account creation wizard.
 *
 * Manages the creation of a new Jami account, including username
 * availability checking, password validation, and account registration
 * via the daemon.
 */
class AccountCreationViewModel(
    private val accountService: AccountService,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val scope = scope

    private val _state = MutableStateFlow(AccountCreationState())
    val state: StateFlow<AccountCreationState> = _state.asStateFlow()

    private var lookupJob: Job? = null
    private var createdAccountId: String? = null

    companion object {
        private const val TAG = "AccountCreationViewModel"
    }

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
        val trimmed = username.trim()
        Log.d(TAG, "setUsername: '$trimmed'")
        _state.value = _state.value.copy(
            username = trimmed,
            usernameAvailable = null,
            usernameCheckInProgress = false,
            usernameCheckError = null,
            error = null
        )
        lookupJob?.cancel()
        if (trimmed.isNotEmpty()) {
            lookupJob = scope.launch {
                _state.value = _state.value.copy(usernameCheckInProgress = true)
                delay(500)
                val currentAccountId = accountService.currentAccount.value?.accountId ?: ""
                Log.d(TAG, "setUsername debounce fired: looking up '$trimmed' with accountId='$currentAccountId'")
                val result = accountService.lookupName(currentAccountId, trimmed)
                Log.d(TAG, "setUsername lookupName returned: $result")
                if (!result) {
                    // Daemon rejected the call (not initialized or invalid); clear spinner
                    Log.w(TAG, "lookupName returned false — daemon may not be ready")
                    _state.value = _state.value.copy(usernameCheckInProgress = false)
                }
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
     * Mirrors official jami-android-client AccountWizardPresenter:
     * - username is mandatory and must be available
     * - password is optional but must be >= 6 chars if set
     * - calls addAccount immediately and navigates to profile setup
     * - username is embedded in the account details (ACCOUNT_REGISTERED_NAME)
     *   and registered by the daemon automatically after DHT connects
     */
    fun createAccount() {
        val current = _state.value

        // Username is mandatory
        if (current.username.isEmpty()) {
            _state.value = current.copy(error = "Username is required")
            return
        }

        // Username must be available (or at least not known-taken)
        if (current.usernameAvailable == false) {
            _state.value = current.copy(error = "Username is already taken")
            return
        }

        // Password validation: optional, but if set must be >= 6 chars
        if (current.password.isNotEmpty() && current.password.length < 6) {
            _state.value = current.copy(error = "Password must be at least 6 characters")
            return
        }

        if (current.password.isNotEmpty() && current.password != current.confirmPassword) {
            _state.value = current.copy(error = "Passwords do not match")
            return
        }

        scope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val accountId = accountService.createJamiAccount(
                    displayName = current.username,
                    username = current.username,
                    password = current.password
                )
                createdAccountId = accountId
                Log.d(TAG, "addAccount returned accountId='$accountId' — navigating to profile setup")
                // Navigate immediately, like the official client; account initializes in background
                _state.value = _state.value.copy(isLoading = false, isCreated = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Account creation failed"
                )
            }
        }
    }

    /**
     * Handle registration state changes — used for error feedback only.
     * Navigation no longer waits for REGISTERED state.
     */
    private fun handleRegistrationState(event: AccountEvent.RegistrationStateChanged) {
        // Only show errors if we're still on this screen (isLoading)
        if (!_state.value.isLoading) return
        when (event.state) {
            "ERROR_GENERIC", "ERROR_AUTH", "ERROR_NETWORK",
            "ERROR_HOST", "ERROR_SERVICE_UNAVAILABLE",
            "ERROR_NEED_MIGRATION" -> {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Account creation failed: ${event.state}"
                )
            }
            else -> {}
        }
    }

    /**
     * Handle username lookup results from the name server.
     */
    private fun handleNameLookupResult(event: AccountEvent.RegisteredNameFound) {
        val currentUsername = _state.value.username
        // Match by query (the term we searched) because name may be empty when not found
        val searchTerm = event.query.ifEmpty { event.name }
        Log.d(TAG, "handleNameLookupResult: query='${event.query}' name='${event.name}' state=${event.state} currentUsername='$currentUsername' searchTerm='$searchTerm'")
        if (searchTerm != currentUsername) {
            Log.d(TAG, "handleNameLookupResult: ignoring stale result (searchTerm='$searchTerm' != current='$currentUsername')")
            return
        }

        val lookupState = LookupState.fromInt(event.state)
        Log.d(TAG, "handleNameLookupResult: lookupState=$lookupState")
        _state.value = when (lookupState) {
            LookupState.NotFound -> _state.value.copy(
                usernameAvailable = true,
                usernameCheckInProgress = false,
                usernameCheckError = null
            )
            LookupState.Success -> _state.value.copy(
                usernameAvailable = false,
                usernameCheckInProgress = false,
                usernameCheckError = null
            )
            LookupState.Invalid -> _state.value.copy(
                usernameAvailable = false,
                usernameCheckInProgress = false,
                usernameCheckError = "Invalid username"
            )
            LookupState.NetworkError -> _state.value.copy(
                usernameAvailable = null,  // unknown — don't block the user
                usernameCheckInProgress = false,
                usernameCheckError = "Name server unreachable"
            )
        }
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
