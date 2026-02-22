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
import net.jami.ui.contracts.ImportAccountContract

/**
 * ViewModel for importing an existing Jami account from a backup archive.
 */
class ImportAccountViewModel(
    private val accountService: AccountService
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(ImportAccountContract.State())
    val state: StateFlow<ImportAccountContract.State> = _state.asStateFlow()

    init {
        scope.launch {
            accountService.accountEvents.collect { event ->
                when (event) {
                    is AccountEvent.RegistrationStateChanged -> handleRegistrationState(event)
                    is AccountEvent.MigrationEnded -> handleMigrationResult(event)
                    else -> { /* Other events not relevant */ }
                }
            }
        }
    }

    fun onAction(action: ImportAccountContract.Action) {
        when (action) {
            is ImportAccountContract.Action.SetArchivePath -> {
                _state.value = _state.value.copy(archivePath = action.path, error = null)
            }
            is ImportAccountContract.Action.SetPassword -> {
                _state.value = _state.value.copy(password = action.password, error = null)
            }
            ImportAccountContract.Action.Import -> importAccount()
        }
    }

    private fun importAccount() {
        val current = _state.value

        if (current.archivePath.isEmpty()) {
            _state.value = current.copy(error = "Please select an archive file")
            return
        }

        scope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                accountService.createJamiAccount(
                    displayName = "",
                    password = current.password.ifEmpty { null },
                    archivePath = current.archivePath
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Import failed"
                )
            }
        }
    }

    private fun handleRegistrationState(event: AccountEvent.RegistrationStateChanged) {
        when (event.state) {
            "REGISTERED" -> {
                _state.value = _state.value.copy(isLoading = false, isImported = true)
            }
            "ERROR_GENERIC", "ERROR_AUTH", "ERROR_NETWORK",
            "ERROR_HOST", "ERROR_SERVICE_UNAVAILABLE" -> {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Import failed: ${event.state}"
                )
            }
            "ERROR_NEED_MIGRATION" -> { /* Migration handled by MigrationEnded */ }
            else -> { /* Still in progress */ }
        }
    }

    private fun handleMigrationResult(event: AccountEvent.MigrationEnded) {
        when (event.state) {
            "SUCCESS" -> {
                _state.value = _state.value.copy(isLoading = false, isImported = true)
            }
            else -> {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Migration failed: ${event.state}"
                )
            }
        }
    }

    fun onCleared() {
        scope.cancel()
    }
}
