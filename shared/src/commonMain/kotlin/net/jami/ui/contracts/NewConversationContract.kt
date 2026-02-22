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

object NewConversationContract {

    @Immutable
    data class SearchState(
        val query: String = "",
        val results: List<ContactItem> = emptyList(),
        val isLoading: Boolean = false,
    )

    @Immutable
    data class SelectionState(
        val selectedContacts: List<ContactItem> = emptyList(),
        val isGroup: Boolean = false,
        val groupName: String = "",
    )

    sealed interface Action {
        data class Search(val query: String) : Action
        data class SelectContact(val contact: ContactItem) : Action
        data class RemoveContact(val contact: ContactItem) : Action
        data class SetGroupName(val name: String) : Action
        data class SetIsGroup(val isGroup: Boolean) : Action
        data object CreateConversation : Action
    }
}
