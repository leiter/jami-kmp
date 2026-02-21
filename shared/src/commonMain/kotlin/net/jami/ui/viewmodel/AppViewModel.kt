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
import net.jami.services.AccountService

sealed class AppState {
    data object Loading : AppState()
    data object NoAccounts : AppState()
    data class HasAccounts(val needsMigration: Boolean = false) : AppState()
}

class AppViewModel(private val accountService: AccountService) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _appState = MutableStateFlow<AppState>(AppState.Loading)
    val appState: StateFlow<AppState> = _appState.asStateFlow()

    init {
        scope.launch {
            accountService.loadAccounts()
            accountService.accounts.collect { accountList ->
                _appState.value = when {
                    accountList.isEmpty() -> AppState.NoAccounts
                    accountList.any { it.needsMigration } ->
                        AppState.HasAccounts(needsMigration = true)
                    else -> AppState.HasAccounts()
                }
            }
        }
    }

    fun onCleared() {
        scope.cancel()
    }
}
