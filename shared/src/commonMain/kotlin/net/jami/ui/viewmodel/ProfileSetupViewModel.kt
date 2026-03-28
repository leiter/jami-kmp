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
import net.jami.utils.Log

data class ProfileSetupState(
    val displayName: String = "",
    val avatarPath: String? = null,
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null,
)

class ProfileSetupViewModel(
    private val accountService: AccountService,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val scope = scope

    private val _state = MutableStateFlow(ProfileSetupState())
    val state: StateFlow<ProfileSetupState> = _state.asStateFlow()

    companion object {
        private const val TAG = "ProfileSetupViewModel"
    }

    fun setDisplayName(name: String) {
        _state.value = _state.value.copy(displayName = name, error = null)
    }

    fun setAvatarPath(path: String?) {
        _state.value = _state.value.copy(avatarPath = path)
    }

    fun saveProfile() {
        val current = _state.value
        val account = accountService.currentAccount.value ?: run {
            _state.value = current.copy(isSaved = true)
            return
        }
        val accountId = account.accountId

        scope.launch {
            _state.value = current.copy(isLoading = true, error = null)
            try {
                if (current.displayName.isNotEmpty() || current.avatarPath != null) {
                    accountService.updateProfile(
                        accountId = accountId,
                        displayName = current.displayName,
                        avatar = current.avatarPath ?: "",
                        fileType = if (current.avatarPath != null) "image/png" else "",
                        flag = 0
                    )
                }
                _state.value = _state.value.copy(isLoading = false, isSaved = true)
            } catch (e: Exception) {
                Log.w(TAG, "saveProfile failed: ${e.message}")
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to save profile"
                )
            }
        }
    }

    fun onCleared() {
        scope.cancel()
    }
}
