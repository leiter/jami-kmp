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
import net.jami.model.Account
import net.jami.model.ConfigKey
import net.jami.services.AccountEvent
import net.jami.services.AccountService
import net.jami.utils.Log
import net.jami.utils.ScratchFiles
import jami_kmp.shared.generated.resources.Res
import jami_kmp.shared.generated.resources.*
import org.jetbrains.compose.resources.getString

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
) : ViewModel() {
    private val scope = scope

    private val _state = MutableStateFlow(ImportAccountState())
    val state: StateFlow<ImportAccountState> = _state.asStateFlow()

    // Id of the account created by importAccount(), used to correlate daemon events.
    private var importedAccountId: String? = null

    // Jami accounts already on the device when the import started. Importing the archive of one
    // of them again would add a second account with the same identity (neither the daemon nor
    // the reference clients refuse it), so such an import is undone; see completeImport().
    private var accountsBeforeImport: List<Account> = emptyList()

    init {
        // Observe account events to detect successful import
        scope.launch {
            accountService.accountEvents.collect { event ->
                when (event) {
                    is AccountEvent.AccountsChanged -> {
                        handleAccountsChanged()
                    }
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
        // Picking another file replaces the previous one: drop the previous picker copy (it may be
        // an account archive with private keys). Only app cache/temp copies are ever deleted.
        val previous = _state.value.archivePath
        if (importedAccountId == null && previous.isNotEmpty() && previous != path) {
            ScratchFiles.deleteIfScratch(previous)
        }
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
            scope.launch { _state.value = current.copy(error = getString(Res.string.import_select_archive)) }
            return
        }

        scope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            accountsBeforeImport = accountService.accounts.value.filter { it.isJami && it.username.isNotEmpty() }
            try {
                val accountId = accountService.createJamiAccount(
                    displayName = "",
                    password = current.password,
                    archivePath = current.archivePath
                )
                importedAccountId = accountId.ifEmpty { null }
                // The picker's copy of the archive holds the private keys: delete it as soon as the
                // daemon has loaded it (AccountService outlives this screen).
                importedAccountId?.let { accountService.deleteImportArchiveWhenLoaded(it, current.archivePath) }
                    ?: ScratchFiles.deleteIfScratch(current.archivePath)
                // The account may already be present if the daemon added it synchronously.
                handleAccountsChanged()
                // Otherwise, completion is tracked via AccountsChanged / registration events.
            } catch (e: Exception) {
                if (importedAccountId == null) ScratchFiles.deleteIfScratch(current.archivePath)
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: getString(Res.string.import_failed)
                )
            }
        }
    }

    /**
     * Detect a successful import the moment the daemon adds the account to its list.
     *
     * This is network-independent: a freshly imported Jami account only reaches the
     * "REGISTERED" registration state once it connects to the DHT, so relying on that
     * alone leaves the user stranded on the import screen when offline (the account is
     * created but never reported as registered until an app restart re-evaluates state).
     * Account presence is the same criterion the app uses to show Home after a restart.
     */
    private suspend fun handleAccountsChanged() {
        completeImport()
    }

    /**
     * Declare the import done once the imported account is present — unless it turns out to be a
     * second copy of an account that is already on the device.
     *
     * The archive's identity is only known after the daemon has decrypted it (the account leaves
     * INITIALIZING and AccountService re-reads its details), so while other Jami accounts exist the
     * decision waits for that. With none (onboarding) there is nothing to compare and the import
     * completes as soon as the account appears, as before. A duplicate is removed again — its own
     * account directory only; the existing account is untouched — and the user is told.
     */
    private suspend fun completeImport() {
        val id = importedAccountId ?: return
        if (_state.value.isImported) return
        val account = accountService.getAccount(id) ?: return
        if (accountsBeforeImport.isNotEmpty()) {
            val identity = account.username
            if (identity.isEmpty()) {
                val status = account.volatileDetails[ConfigKey.ACCOUNT_REGISTRATION_STATUS.key]
                // Archive not decrypted yet: wait for the next event.
                if (status == null || status == "INITIALIZING") return
            } else if (accountsBeforeImport.any { it.accountId != id && it.isOwn(identity) }) {
                Log.i(TAG, "Import of an account already on this device; removing the copy $id")
                accountService.removeAccount(id)
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = getString(Res.string.import_account_already_on_device),
                )
                return
            }
        }
        _state.value = _state.value.copy(isLoading = false, isImported = true)
    }

    /**
     * Handle registration state changes to detect successful import.
     */
    private suspend fun handleRegistrationState(event: AccountEvent.RegistrationStateChanged) {
        // Only react to the account we just imported.
        if (importedAccountId != null && event.accountId != importedAccountId) return
        when (event.state) {
            "REGISTERED" -> {
                if (importedAccountId != null) {
                    completeImport()
                } else {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        isImported = true
                    )
                }
            }
            "ERROR_GENERIC", "ERROR_AUTH", "ERROR_NETWORK",
            "ERROR_HOST", "ERROR_SERVICE_UNAVAILABLE" -> {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = getString(Res.string.import_failed_reason, event.state)
                )
            }
            "ERROR_NEED_MIGRATION" -> {
                // Migration will be handled by MigrationEnded event
            }
            // Left INITIALIZING offline (UNREGISTERED / TRYING): the identity is known now.
            else -> completeImport()
        }
    }

    /**
     * Handle migration result after importing an older-format account.
     */
    private suspend fun handleMigrationResult(event: AccountEvent.MigrationEnded) {
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
                    error = getString(Res.string.migration_failed_reason, event.state)
                )
            }
        }
    }

    /**
     * Cancel the coroutine scope when this ViewModel is no longer needed.
     */
    public override fun onCleared() {
        // Left the screen without importing: the picked archive copy is no longer needed.
        // (After an import, AccountService deletes it once the daemon has loaded it.)
        if (importedAccountId == null) ScratchFiles.deleteIfScratch(_state.value.archivePath)
        scope.cancel()
    }

    private companion object {
        const val TAG = "ImportAccountViewModel"
    }
}
