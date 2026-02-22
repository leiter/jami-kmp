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
package net.jami.ui.contracts

import androidx.compose.runtime.Immutable

object AccountSettingsContract {

    @Immutable
    data class ProfileState(
        val displayName: String = "",
        val username: String = "",
        val identityHash: String = "",
        val avatarUri: String? = null,
    )

    @Immutable
    data class DevicesState(
        val devices: List<DeviceItem> = emptyList(),
        val isLoading: Boolean = false,
    )

    sealed interface Action {
        data class UpdateDisplayName(val name: String) : Action
        data class RevokeDevice(val deviceId: String, val password: String) : Action
        data class ExportAccount(val path: String, val password: String) : Action
    }
}
