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

/**
 * State for the account import screen.
 */
data class ImportAccountState(
    val archivePath: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isImported: Boolean = false
)

/**
 * ViewModel for importing an existing Jami account from a backup archive.
 *
 * Supports importing from a .gz archive file with optional password
 * decryption. Observes daemon registration events to track import progress.
 */
class ImportAccountViewModel(
    private val accountService: AccountService,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val scope = scope

    private val _state = MutableStateFlow(ImportAccountState())
    val state: StateFlow<ImportAccountState> = _state.asStateFlow()

    init {
        // Observe account events to detect successful import
        scope.launch {
            accountService.accountEvents.collect { event ->
                when (event) {
                    is AccountEvent.RegistrationStateChanged -> {
                        handleRegistrationState(event)
                    }
                    is AccountEvent.MigrationEnded -> {
                        handleMigrationResult(event)
                    }
                    else -> { /* Other events not relevant */ }
                }
            }
        }
    }

    /**
     * Set the path to the account archive file.
     *
     * @param path Absolute path to the .gz archive.
     */
    fun setArchivePath(path: String) {
        _state.value = _state.value.copy(archivePath = path, error = null)
    }

    /**
     * Set the password for archive decryption.
     *
     * @param password Archive password.
     */
    fun setPassword(password: String) {
        _state.value = _state.value.copy(password = password, error = null)
    }

    /**
     * Import the account from the specified archive.
     *
     * Creates a new Jami account using the archive file and password
     * provided. The daemon will restore the account from the backup.
     */
    fun importAccount() {
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
                // Import progress is tracked via AccountEvent callbacks
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Import failed"
                )
            }
        }
    }

    /**
     * Handle registration state changes to detect successful import.
     */
    private fun handleRegistrationState(event: AccountEvent.RegistrationStateChanged) {
        when (event.state) {
            "REGISTERED" -> {
                _state.value = _state.value.copy(
                    isLoading = false,
                    isImported = true
                )
            }
            "ERROR_GENERIC", "ERROR_AUTH", "ERROR_NETWORK",
            "ERROR_HOST", "ERROR_SERVICE_UNAVAILABLE" -> {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Import failed: ${event.state}"
                )
            }
            "ERROR_NEED_MIGRATION" -> {
                // Migration will be handled by MigrationEnded event
            }
            else -> { /* Still in progress */ }
        }
    }

    /**
     * Handle migration result after importing an older-format account.
     */
    private fun handleMigrationResult(event: AccountEvent.MigrationEnded) {
        when (event.state) {
            "SUCCESS" -> {
                _state.value = _state.value.copy(
                    isLoading = false,
                    isImported = true
                )
            }
            else -> {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Migration failed: ${event.state}"
                )
            }
        }
    }

    /**
     * Cancel the coroutine scope when this ViewModel is no longer needed.
     */
    fun onCleared() {
        scope.cancel()
    }
}
