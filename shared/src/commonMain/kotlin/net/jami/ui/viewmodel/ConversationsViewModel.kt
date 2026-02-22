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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import net.jami.services.AccountService
import net.jami.services.ConversationFacade
import net.jami.services.ConversationEvent
import net.jami.ui.contracts.ConversationItem
import net.jami.ui.contracts.HomeContract
import net.jami.ui.contracts.SearchContract

/**
 * ViewModel for the conversations list (HomeScreen) and search screen.
 *
 * Exposes split state flows for HomeScreen (Tier 1) and a single
 * state flow for SearchScreen (Tier 3).
 */
class ConversationsViewModel(
    private val accountService: AccountService,
    private val conversationFacade: ConversationFacade
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Home screen split state (Tier 1)
    private val _conversationsState = MutableStateFlow(HomeContract.ConversationsState())
    val conversationsState: StateFlow<HomeContract.ConversationsState> = _conversationsState.asStateFlow()

    private val _headerState = MutableStateFlow(HomeContract.HeaderState())
    val headerState: StateFlow<HomeContract.HeaderState> = _headerState.asStateFlow()

    // Search screen state (Tier 3)
    private val _searchState = MutableStateFlow(SearchContract.State())
    val searchState: StateFlow<SearchContract.State> = _searchState.asStateFlow()

    init {
        scope.launch {
            accountService.currentAccount.filterNotNull().collect { account ->
                loadConversations()
            }
        }

        scope.launch {
            conversationFacade.conversationEvents.collect { event ->
                when (event) {
                    is ConversationEvent.MessageReceived,
                    is ConversationEvent.MessageUpdated,
                    is ConversationEvent.MessageStatusChanged -> {
                        loadConversations()
                    }
                    else -> { /* Other events don't require list refresh */ }
                }
            }
        }
    }

    fun onHomeAction(action: HomeContract.Action) {
        when (action) {
            HomeContract.Action.Refresh -> loadConversations()
        }
    }

    fun onSearchAction(action: SearchContract.Action) {
        when (action) {
            is SearchContract.Action.Search -> search(action.query)
        }
    }

    private fun loadConversations() {
        scope.launch {
            _conversationsState.value = _conversationsState.value.copy(isLoading = true)
            try {
                val account = accountService.currentAccount.value ?: return@launch
                val accountId = account.accountId

                val requests = accountService.getConversationRequests(accountId)
                val pendingCount = requests.size

                val conversations = buildConversationItems(accountId, "")

                _conversationsState.value = _conversationsState.value.copy(
                    conversations = conversations,
                    isLoading = false
                )
                _headerState.value = _headerState.value.copy(
                    pendingRequests = pendingCount
                )
            } catch (e: Exception) {
                _conversationsState.value = _conversationsState.value.copy(isLoading = false)
            }
        }
    }

    private fun search(query: String) {
        _searchState.value = _searchState.value.copy(searchQuery = query)
        scope.launch {
            _searchState.value = _searchState.value.copy(isLoading = true)
            try {
                val account = accountService.currentAccount.value ?: return@launch
                val conversations = buildConversationItems(account.accountId, query.lowercase())
                _searchState.value = _searchState.value.copy(
                    conversations = conversations,
                    isLoading = false
                )
            } catch (e: Exception) {
                _searchState.value = _searchState.value.copy(isLoading = false)
            }
        }
    }

    private fun buildConversationItems(accountId: String, query: String): List<ConversationItem> {
        return emptyList()
    }

    fun onCleared() {
        scope.cancel()
    }
}
