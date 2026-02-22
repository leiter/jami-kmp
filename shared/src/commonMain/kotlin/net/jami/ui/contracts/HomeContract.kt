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

object HomeContract {

    @Immutable
    data class ConversationsState(
        val conversations: List<ConversationItem> = emptyList(),
        val isLoading: Boolean = false,
    )

    @Immutable
    data class HeaderState(
        val pendingRequests: Int = 0,
        val userDisplayName: String = "",
        val userAvatarUri: String? = null,
    )

    sealed interface Action {
        data object Refresh : Action
    }
}
