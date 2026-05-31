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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.jami.services.AccountService
import net.jami.utils.Log

sealed class AppState {
    data object Loading : AppState()
    data object NoAccounts : AppState()
    data object Onboarding : AppState()
    data class HasAccounts(val needsMigration: Boolean = false) : AppState()
}

class AppViewModel(
    private val accountService: AccountService,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val scope = scope
    private val _appState = MutableStateFlow<AppState>(AppState.Loading)
    val appState: StateFlow<AppState> = _appState.asStateFlow()

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

    fun onCleared() {
        Log.d(TAG, "onCleared")
        scope.cancel()
    }

    companion object {
        private const val TAG = "AppViewModel"
    }
}
