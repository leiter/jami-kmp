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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.jami.services.AccountEvent
import net.jami.services.AccountService
import net.jami.services.BiometricResult
import net.jami.services.BiometricService
import net.jami.utils.Log

sealed class AppState {
    data object Loading : AppState()
    data object NoAccounts : AppState()
    data object Onboarding : AppState()
    data class HasAccounts(val needsMigration: Boolean = false) : AppState()
}

class AppViewModel(
    private val accountService: AccountService,
    private val biometricService: BiometricService,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : ViewModel() {
    private val scope = scope
    private val _appState = MutableStateFlow<AppState>(AppState.Loading)
    val appState: StateFlow<AppState> = _appState.asStateFlow()

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    // When true, account creation is in progress and we stay in Onboarding
    // instead of switching to HasAccounts. Cleared when the wizard completes.
    private var onboardingInProgress = false

    init {
        Log.d(TAG, "AppViewModel created, waiting for daemon accounts ready signal")
        scope.launch {
            // Wait for the daemon to fire accountsChanged at least once before
            // evaluating state. This prevents the race where loadAccounts() is called
            // before the daemon has finished loading accounts from disk, which would
            // incorrectly flash the Welcome screen before transitioning to HasAccounts.
            accountService.daemonAccountsReady.filter { it }.first()
            Log.d(TAG, "Daemon accounts ready — starting account state collection")

            accountService.accounts.collect { accountList ->
                val current = _appState.value
                val next = when {
                    onboardingInProgress -> AppState.Onboarding
                    // Keep HasAccounts during transient daemon-driven empty states (e.g. on
                    // reconnect). Deliberate deletions are handled separately via AccountRemoved.
                    accountList.isEmpty() && current is AppState.HasAccounts -> current
                    accountList.isEmpty() -> AppState.NoAccounts
                    accountList.any { it.needsMigration } ->
                        AppState.HasAccounts(needsMigration = true)
                    else -> AppState.HasAccounts()
                }
                Log.d(TAG, "accounts=${accountList.size} onboarding=$onboardingInProgress " +
                        "$current -> $next")
                _appState.value = next
            }
        }

        // React to deliberate account removals. When the user deletes the last account we
        // must override the transient-empty guard above and navigate to onboarding.
        scope.launch {
            accountService.accountEvents.collect { event ->
                if (event is AccountEvent.AccountRemoved &&
                    accountService.accounts.value.isEmpty()) {
                    Log.d(TAG, "AccountRemoved(${event.accountId}): last account deleted, " +
                            "navigating to NoAccounts")
                    onboardingInProgress = false
                    _appState.value = AppState.NoAccounts
                }
            }
        }
    }

    /**
     * Called when the user starts account creation. Keeps the app in
     * Onboarding state even after the account is created by the daemon.
     */
    fun startOnboarding() {
        Log.d(TAG, "startOnboarding")
        onboardingInProgress = true
        _appState.value = AppState.Onboarding
    }

    /**
     * Called when the user finishes the onboarding wizard (AccountSummary).
     * Allows the reactive account flow to switch to HasAccounts.
     */
    fun finishOnboarding() {
        Log.d(TAG, "finishOnboarding")
        onboardingInProgress = false
        val accountList = accountService.accounts.value
        _appState.value = when {
            accountList.isEmpty() -> AppState.NoAccounts
            accountList.any { it.needsMigration } ->
                AppState.HasAccounts(needsMigration = true)
            else -> AppState.HasAccounts()
        }
    }

    /**
     * Lock the app if the current account has biometric authentication enabled.
     * Called when the app goes to background (ON_STOP lifecycle event).
     */
    fun lockIfNeeded() {
        scope.launch {
            val account = accountService.currentAccount.value ?: return@launch
            if (biometricService.isEnabled(account.accountId)) {
                Log.d(TAG, "lockIfNeeded: locking app")
                _isLocked.value = true
            }
        }
    }

    /**
     * Unlock the app after successful biometric authentication.
     */
    fun unlock() {
        Log.d(TAG, "unlock")
        _isLocked.value = false
    }

    /**
     * Trigger biometric authentication for the current account.
     * Returns the result to the caller; call [unlock] on success.
     */
    suspend fun authenticateBiometric(
        promptTitle: String,
        promptDescription: String,
    ): BiometricResult {
        val account = accountService.currentAccount.value
            ?: return BiometricResult.Error("No account", false)
        return biometricService.authenticate(account.accountId, promptTitle, promptDescription)
    }

    public override fun onCleared() {
        Log.d(TAG, "onCleared")
        scope.cancel()
    }

    companion object {
        private const val TAG = "AppViewModel"
    }
}
