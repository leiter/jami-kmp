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
import net.jami.model.Conversation
import net.jami.services.AccountService
import net.jami.services.ConversationFacade
import net.jami.services.ConversationEvent
import net.jami.services.DaemonBridge
import net.jami.ui.contracts.ConversationItem
import net.jami.ui.contracts.HomeContract
import net.jami.ui.contracts.SearchContract
import net.jami.utils.Log

/**
 * ViewModel for the conversations list (HomeScreen) and search screen.
 *
 * Exposes split state flows for HomeScreen (Tier 1) and a single
 * state flow for SearchScreen (Tier 3).
 */
class ConversationsViewModel(
    private val accountService: AccountService,
    private val conversationFacade: ConversationFacade,
    private val daemonBridge: DaemonBridge
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
        // Observe current account and its conversations via Account StateFlow
        scope.launch {
            accountService.currentAccount.filterNotNull().collect { account ->
                // Collect conversationsSubject from Account model
                scope.launch {
                    account.conversationsSubject.collect { conversations ->
                        updateConversationsState(conversations)
                    }
                }
                // Also update header
                updateHeaderState(account)
            }
        }

        // Refresh on conversation events
        scope.launch {
            conversationFacade.conversationEvents.collect { event ->
                when (event) {
                    is ConversationEvent.ConversationReady,
                    is ConversationEvent.ConversationRemoved,
                    is ConversationEvent.MessageReceived,
                    is ConversationEvent.MessageUpdated,
                    is ConversationEvent.MessageStatusChanged -> {
                        // Re-read from Account model
                        val account = accountService.currentAccount.value ?: return@collect
                        updateConversationsState(account.conversationsSubject.value)
                        updateHeaderState(account)
                    }
                    else -> { /* Other events don't require list refresh */ }
                }
            }
        }
    }

    fun onHomeAction(action: HomeContract.Action) {
        when (action) {
            HomeContract.Action.Refresh -> {
                val account = accountService.currentAccount.value ?: return
                account.conversationChanged()
            }
        }
    }

    fun onSearchAction(action: SearchContract.Action) {
        when (action) {
            is SearchContract.Action.Search -> search(action.query)
        }
    }

    private fun updateConversationsState(conversations: List<Conversation>) {
        val items = conversations.mapNotNull { conversation ->
            try {
                conversationToItem(conversation)
            } catch (e: Exception) {
                Log.w(TAG, "Error mapping conversation: ${e.message}")
                null
            }
        }
        _conversationsState.value = _conversationsState.value.copy(
            conversations = items,
            isLoading = false
        )
    }

    private fun updateHeaderState(account: net.jami.model.Account) {
        val pendingCount = account.pending.size
        val displayName = account.details["Account.displayName"]
            ?: account.volatileDetails["Account.registeredName"]
            ?: ""
        _headerState.value = _headerState.value.copy(
            pendingRequests = pendingCount,
            userDisplayName = displayName
        )
    }

    private fun conversationToItem(conversation: Conversation): ConversationItem {
        // Build display name from contacts or profile
        val profile = conversation.profileFlow.value
        val title = profile.displayName?.takeIf { it.isNotEmpty() }
            ?: conversation.contact?.displayUsername
            ?: conversation.uri.rawRingId.take(8) + "..."

        val lastEvent = conversation.lastEvent
        val lastMessage = when {
            lastEvent is net.jami.model.TextMessage -> lastEvent.body ?: ""
            lastEvent != null -> ""
            else -> ""
        }

        return ConversationItem(
            id = conversation.uri.rawRingId,
            displayName = title,
            lastMessage = lastMessage,
            timestamp = lastEvent?.timestamp ?: 0L,
            unreadCount = 0,
            avatarUri = null,
            isOnline = conversation.contact?.isOnline ?: false
        )
    }

    private fun search(query: String) {
        _searchState.value = _searchState.value.copy(searchQuery = query)
        scope.launch {
            _searchState.value = _searchState.value.copy(isLoading = true)
            try {
                val account = accountService.currentAccount.value ?: return@launch
                val lq = query.lowercase()
                val conversations = account.conversations.values
                    .filter { conv ->
                        if (lq.isEmpty()) return@filter true
                        val profile = conv.profileFlow.value
                        val name = profile.displayName
                            ?: conv.contact?.displayUsername
                            ?: conv.uri.uri
                        name.lowercase().contains(lq)
                    }
                    .mapNotNull { conversationToItem(it) }

                _searchState.value = _searchState.value.copy(
                    conversations = conversations,
                    isLoading = false
                )
            } catch (e: Exception) {
                _searchState.value = _searchState.value.copy(isLoading = false)
            }
        }
    }

    companion object {
        private const val TAG = "ConversationsVM"
    }

    fun onCleared() {
        scope.cancel()
    }
}
