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

import androidx.lifecycle.ViewModel

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
import kotlinx.coroutines.launch
import net.jami.services.AccountEvent
import net.jami.services.AccountService
import net.jami.services.LookupState
import net.jami.utils.Log

enum class UsernameCheckError { INVALID, NETWORK_ERROR, LOOKUP_FAILED }
enum class AccountCreationError { USERNAME_REQUIRED, USERNAME_TAKEN, PASSWORDS_DONT_MATCH, CREATION_FAILED }

/**
 * State for the account creation screen.
 */
data class AccountCreationState(
    val username: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val error: AccountCreationError? = null,
    val errorDetail: String? = null,
    val isCreated: Boolean = false,
    val usernameAvailable: Boolean? = null,
    val usernameCheckInProgress: Boolean = false,
    val isRegistering: Boolean = false,
    val usernameCheckError: UsernameCheckError? = null,
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
) : ViewModel() {
    private val scope = scope

    private val _state = MutableStateFlow(AccountCreationState())
    val state: StateFlow<AccountCreationState> = _state.asStateFlow()

    private var lookupJob: Job? = null
    private var createdAccountId: String? = null

    companion object {
        private const val TAG = "AccountCreationViewModel"
    }

    init {
        // Observe account events for creation result and post-creation name registration
        scope.launch {
            accountService.accountEvents.collect { event ->
                when (event) {
                    is AccountEvent.RegistrationStateChanged -> {
                        handleRegistrationState(event)
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
     * Update the username field and trigger an availability check.
     *
     * Debounces 500ms, then calls [AccountService.findRegistrationByName] which
     * suspends until the daemon responds — fully self-contained, no ambient event matching.
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
        if (trimmed.isEmpty()) return
        lookupJob = scope.launch {
            _state.value = _state.value.copy(usernameCheckInProgress = true)
            delay(500)
            if (trimmed != _state.value.username) return@launch  // stale after debounce
            val accountId = accountService.currentAccount.value?.accountId ?: ""
            Log.d(TAG, "setUsername debounce fired: looking up '$trimmed' accountId='$accountId'")
            try {
                val result = accountService.findRegistrationByName(accountId, "", trimmed)
                if (trimmed != _state.value.username) return@launch  // stale after await
                Log.d(TAG, "setUsername result: state=${result.state} name=${result.name}")
                _state.value = when (result.state) {
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
                        usernameCheckError = UsernameCheckError.INVALID,
                    )
                    LookupState.NetworkError -> _state.value.copy(
                        usernameAvailable = null,
                        usernameCheckInProgress = false,
                        usernameCheckError = UsernameCheckError.NETWORK_ERROR,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "setUsername lookup failed: ${e.message}")
                if (trimmed == _state.value.username) {
                    _state.value = _state.value.copy(
                        usernameCheckInProgress = false,
                        usernameCheckError = UsernameCheckError.LOOKUP_FAILED,
                    )
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
            _state.value = current.copy(error = AccountCreationError.USERNAME_REQUIRED)
            return
        }

        // Username must be available (or at least not known-taken)
        if (current.usernameAvailable == false) {
            _state.value = current.copy(error = AccountCreationError.USERNAME_TAKEN)
            return
        }

        // Password validation: optional, but if set must be >= 6 chars
        if (current.password.isNotEmpty() && current.password.length < 6) {
            _state.value = current.copy(error = AccountCreationError.PASSWORDS_DONT_MATCH)
            return
        }

        if (current.password.isNotEmpty() && current.password != current.confirmPassword) {
            _state.value = current.copy(error = AccountCreationError.PASSWORDS_DONT_MATCH)
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
                    error = AccountCreationError.CREATION_FAILED,
                    errorDetail = e.message,
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
                    error = AccountCreationError.CREATION_FAILED,
                    errorDetail = event.state,
                )
            }
            else -> {}
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
                error = AccountCreationError.CREATION_FAILED,
            )
        }
    }

    fun createSipAccount(
        hostname: String,
        username: String,
        password: String,
        port: String,
        displayName: String,
    ) {
        scope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val details = mutableMapOf(
                    net.jami.model.ConfigKey.ACCOUNT_TYPE.key to net.jami.model.AccountConfig.ACCOUNT_TYPE_SIP,
                    net.jami.model.ConfigKey.ACCOUNT_ALIAS.key to displayName.ifEmpty { username },
                    net.jami.model.ConfigKey.ACCOUNT_HOSTNAME.key to hostname,
                    net.jami.model.ConfigKey.ACCOUNT_USERNAME.key to username,
                    net.jami.model.ConfigKey.ACCOUNT_PASSWORD.key to password,
                )
                if (port.isNotBlank()) details["Account.localPort"] = port
                accountService.addAccount(details)
                _state.value = _state.value.copy(isLoading = false, isCreated = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = AccountCreationError.CREATION_FAILED,
                    errorDetail = e.message,
                )
            }
        }
    }

    /**
     * Cancel the coroutine scope when this ViewModel is no longer needed.
     */
    public override fun onCleared() {
        scope.cancel()
    }
}
