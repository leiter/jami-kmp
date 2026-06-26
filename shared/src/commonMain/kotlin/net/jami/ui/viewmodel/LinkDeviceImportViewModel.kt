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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.jami.model.AccountConfig
import net.jami.model.ConfigKey
import net.jami.services.AccountEvent
import net.jami.services.AccountService
import net.jami.services.AuthError
import net.jami.services.AuthState
import net.jami.utils.Log

/**
 * UI state machine for the import-side of the new-protocol device linking flow.
 * Mirrors AddDeviceImportState in jami-client-android ImportSideViewModel.
 */
sealed class AddDeviceImportState {
    data object Init : AddDeviceImportState()
    data class TokenAvailable(val token: String) : AddDeviceImportState()
    data object Connecting : AddDeviceImportState()
    data class Authenticating(
        val peerId: String,
        val needPassword: Boolean,
        val registeredName: String? = null,
        val inputError: InputError? = null,
    ) : AddDeviceImportState()
    data object InProgress : AddDeviceImportState()
    data class Done(val error: AuthError? = null) : AddDeviceImportState()

    enum class InputError {
        BAD_PASSWORD, UNKNOWN;
        companion object {
            fun fromString(value: String) = if (value == "bad_password") BAD_PASSWORD else UNKNOWN
        }
    }
}

/**
 * ViewModel for the "Link from another device" import flow (new-device side).
 *
 * On creation it immediately:
 *   1. Fetches a Jami account template from the daemon.
 *   2. Sets Account.archiveURL = "jami-auth" to request the linking protocol.
 *   3. Calls addAccount() — the daemon creates a temp account and begins the
 *      TOKEN_AVAILABLE → CONNECTING → AUTHENTICATING → IN_PROGRESS → DONE flow.
 *
 * The flow's state is driven by [AccountEvent.AddDeviceStateChanged] events
 * filtered to the temp account. On success [onCleared] keeps the account; on
 * cancellation or error [onCleared] removes it so it doesn't pollute the list.
 */
class LinkDeviceImportViewModel(
    private val accountService: AccountService,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : ViewModel() {
    private val scope = scope

    private val _state = MutableStateFlow<AddDeviceImportState>(AddDeviceImportState.Init)
    val state: StateFlow<AddDeviceImportState> = _state.asStateFlow()

    private var tempAccountId: String? = null

    init {
        scope.launch {
            try {
                val details = accountService.getAccountTemplate(AccountConfig.ACCOUNT_TYPE_JAMI)
                    .toMutableMap()
                details[ConfigKey.ACCOUNT_ARCHIVE_URL.key] = "jami-auth"
                val accountId = accountService.addAccount(details)
                tempAccountId = accountId

                accountService.accountEvents.collect { event ->
                    if (event is AccountEvent.AddDeviceStateChanged
                        && event.accountId == accountId
                    ) {
                        handleStateChange(event.state, event.details)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create temp import account", e)
                _state.value = AddDeviceImportState.Done(AuthError.UNKNOWN)
            }
        }
    }

    private suspend fun handleStateChange(stateInt: Int, details: Map<String, String>) {
        val authState = AuthState.fromInt(stateInt)
        if (!checkNewStateValidity(authState)) {
            Log.e(TAG, "Invalid state transition: ${_state.value} → $authState")
            return
        }
        when (authState) {
            AuthState.INIT -> Unit
            AuthState.TOKEN_AVAILABLE -> {
                val token = details[KEY_TOKEN]
                if (token != null) _state.value = AddDeviceImportState.TokenAvailable(token)
            }
            AuthState.CONNECTING -> {
                _state.value = AddDeviceImportState.Connecting
            }
            AuthState.AUTHENTICATING -> {
                val peerId = details[KEY_PEER_ID] ?: return
                val needPassword = details[KEY_AUTH_SCHEME] == "password"
                val inputError = details[KEY_AUTH_ERROR]
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { AddDeviceImportState.InputError.fromString(it) }

                // Optimistically update state, then try to resolve the display name.
                _state.value = AddDeviceImportState.Authenticating(
                    peerId = peerId,
                    needPassword = needPassword,
                    inputError = inputError,
                )

                val accountId = tempAccountId ?: return
                try {
                    val registered = accountService.findRegistrationByAddress(accountId, "", peerId)
                    val displayName = registered.name.takeIf { it.isNotBlank() }
                    // Only update if still in Authenticating (user might have moved on).
                    val current = _state.value
                    if (current is AddDeviceImportState.Authenticating && current.peerId == peerId) {
                        _state.value = current.copy(registeredName = displayName, inputError = inputError)
                    }
                } catch (_: Exception) { /* name resolution is best-effort */ }
            }
            AuthState.IN_PROGRESS -> {
                _state.value = AddDeviceImportState.InProgress
            }
            AuthState.DONE -> {
                val errorStr = details[KEY_ERROR]?.takeIf { it.isNotEmpty() && it != "none" }
                val error = errorStr?.let { AuthError.fromString(it) }
                _state.value = AddDeviceImportState.Done(error)
            }
        }
    }

    /** User confirmed the peer identity and optionally provided a password. */
    fun onAuthentication(password: String = "") {
        val accountId = tempAccountId ?: return
        scope.launch { accountService.provideAccountAuthentication(accountId, password) }
    }

    /** User cancelled or navigated back before completion. Cleans up the temp account. */
    fun onCancel() {
        val accountId = tempAccountId ?: return
        scope.launch { accountService.removeAccount(accountId) }
        tempAccountId = null
    }

    public override fun onCleared() {
        if (_state.value !is AddDeviceImportState.Done) onCancel()
        scope.cancel()
    }

    private fun checkNewStateValidity(new: AuthState): Boolean = new in when (_state.value) {
        is AddDeviceImportState.Init           -> listOf(AuthState.TOKEN_AVAILABLE, AuthState.DONE)
        is AddDeviceImportState.TokenAvailable -> listOf(AuthState.TOKEN_AVAILABLE, AuthState.CONNECTING, AuthState.DONE)
        is AddDeviceImportState.Connecting     -> listOf(AuthState.AUTHENTICATING, AuthState.DONE)
        is AddDeviceImportState.Authenticating -> listOf(AuthState.AUTHENTICATING, AuthState.IN_PROGRESS, AuthState.DONE)
        is AddDeviceImportState.InProgress     -> listOf(AuthState.IN_PROGRESS, AuthState.AUTHENTICATING, AuthState.DONE)
        is AddDeviceImportState.Done           -> listOf(AuthState.DONE)
    }

    companion object {
        private val TAG = LinkDeviceImportViewModel::class.simpleName!!
        private const val KEY_TOKEN       = "token"
        private const val KEY_PEER_ID     = "peer_id"
        private const val KEY_AUTH_SCHEME = "auth_scheme"
        private const val KEY_AUTH_ERROR  = "auth_error"
        private const val KEY_ERROR       = "error"
    }
}
