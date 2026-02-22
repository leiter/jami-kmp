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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.jami.model.Contact
import net.jami.model.Uri
import net.jami.services.AccountEvent
import net.jami.services.AccountService
import net.jami.services.ContactService
import net.jami.services.ConversationFacade
import net.jami.ui.contracts.ContactItem
import net.jami.ui.contracts.NewConversationContract

/**
 * ViewModel for creating a new conversation.
 *
 * Exposes split state flows (Tier 2): SearchState and SelectionState.
 * Emits a one-shot conversationCreated event via SharedFlow.
 */
class NewConversationViewModel(
    private val contactService: ContactService,
    private val conversationFacade: ConversationFacade,
    private val accountService: AccountService
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _searchState = MutableStateFlow(NewConversationContract.SearchState())
    val searchState: StateFlow<NewConversationContract.SearchState> = _searchState.asStateFlow()

    private val _selectionState = MutableStateFlow(NewConversationContract.SelectionState())
    val selectionState: StateFlow<NewConversationContract.SelectionState> = _selectionState.asStateFlow()

    private val _conversationCreated = MutableSharedFlow<String>()
    val conversationCreated: SharedFlow<String> = _conversationCreated.asSharedFlow()

    init {
        scope.launch {
            accountService.accountEvents.collect { event ->
                when (event) {
                    is AccountEvent.RegisteredNameFound -> handleNameFound(event)
                    is AccountEvent.UserSearchEnded -> handleSearchResults(event)
                    else -> { /* Other events */ }
                }
            }
        }
    }

    fun onAction(action: NewConversationContract.Action) {
        when (action) {
            is NewConversationContract.Action.Search -> search(action.query)
            is NewConversationContract.Action.SelectContact -> selectContact(action.contact)
            is NewConversationContract.Action.RemoveContact -> removeContact(action.contact)
            is NewConversationContract.Action.SetGroupName -> {
                _selectionState.value = _selectionState.value.copy(groupName = action.name)
            }
            is NewConversationContract.Action.SetIsGroup -> {
                _selectionState.value = _selectionState.value.copy(isGroup = action.isGroup)
            }
            NewConversationContract.Action.CreateConversation -> {
                scope.launch { createConversation() }
            }
        }
    }

    private fun search(query: String) {
        _searchState.value = _searchState.value.copy(query = query)

        if (query.isEmpty()) {
            _searchState.value = _searchState.value.copy(results = emptyList())
            return
        }

        scope.launch {
            _searchState.value = _searchState.value.copy(isLoading = true)
            val account = accountService.currentAccount.value ?: return@launch

            val cachedContacts = contactService.getCachedContacts(account.accountId)
            val lowerQuery = query.lowercase()
            val localResults = cachedContacts
                .filter { contact ->
                    contact.displayUsername.lowercase().contains(lowerQuery) ||
                        contact.uri.uri.lowercase().contains(lowerQuery)
                }
                .map { contact ->
                    ContactItem(
                        uri = contact.uri.uri,
                        displayName = contact.displayUsername,
                        username = contact.username ?: "",
                        presenceStatus = contact.presenceStatus.value,
                        avatarUri = null
                    )
                }

            _searchState.value = _searchState.value.copy(results = localResults)

            accountService.searchUser(account.accountId, query)
        }
    }

    private fun selectContact(contact: ContactItem) {
        val current = _selectionState.value.selectedContacts
        if (current.none { it.uri == contact.uri }) {
            _selectionState.value = _selectionState.value.copy(
                selectedContacts = current + contact
            )
        }
    }

    private fun removeContact(contact: ContactItem) {
        val current = _selectionState.value.selectedContacts
        _selectionState.value = _selectionState.value.copy(
            selectedContacts = current.filter { it.uri != contact.uri }
        )
    }

    private suspend fun createConversation() {
        val selected = _selectionState.value.selectedContacts
        if (selected.isEmpty()) return

        val account = accountService.currentAccount.value ?: return
        val accountId = account.accountId

        try {
            _searchState.value = _searchState.value.copy(isLoading = true)

            val conversationId = if (selected.size == 1 && !_selectionState.value.isGroup) {
                val contactUri = Uri.fromString(selected.first().uri)
                val conversation = conversationFacade.startConversation(accountId, contactUri)
                conversation.uri.uri
            } else {
                val memberUris = selected.map { it.uri }
                val convId = accountService.startConversation(accountId, memberUris)

                val groupName = _selectionState.value.groupName
                if (groupName.isNotEmpty()) {
                    accountService.updateConversationInfo(
                        accountId, convId, mapOf("title" to groupName)
                    )
                }
                convId
            }

            _searchState.value = _searchState.value.copy(isLoading = false)
            _conversationCreated.emit(conversationId)
        } catch (e: Exception) {
            _searchState.value = _searchState.value.copy(isLoading = false)
        }
    }

    private fun handleNameFound(event: AccountEvent.RegisteredNameFound) {
        val currentQuery = _searchState.value.query
        if (event.name != currentQuery && event.address != currentQuery) return

        if (event.state == 0 && event.address.isNotEmpty()) {
            val newResult = ContactItem(
                uri = event.address,
                displayName = event.name.ifEmpty { event.address },
                username = event.name,
                presenceStatus = Contact.PresenceStatus.OFFLINE,
                avatarUri = null
            )

            val existing = _searchState.value.results
            if (existing.none { it.uri == newResult.uri }) {
                _searchState.value = _searchState.value.copy(
                    results = existing + newResult,
                    isLoading = false
                )
            }
        } else {
            _searchState.value = _searchState.value.copy(isLoading = false)
        }
    }

    private fun handleSearchResults(event: AccountEvent.UserSearchEnded) {
        if (event.query != _searchState.value.query) return

        val results = event.results.mapNotNull { result ->
            val address = result["id"] ?: return@mapNotNull null
            val username = result["username"] ?: ""
            val firstName = result["firstName"] ?: ""
            val lastName = result["lastName"] ?: ""
            val displayName = when {
                firstName.isNotEmpty() || lastName.isNotEmpty() -> "$firstName $lastName".trim()
                username.isNotEmpty() -> username
                else -> address
            }

            ContactItem(
                uri = address,
                displayName = displayName,
                username = username,
                presenceStatus = Contact.PresenceStatus.OFFLINE,
                avatarUri = null
            )
        }

        val existing = _searchState.value.results.associateBy { it.uri }
        val merged = existing.toMutableMap()
        for (result in results) {
            merged.putIfAbsent(result.uri, result)
        }

        _searchState.value = _searchState.value.copy(
            results = merged.values.toList(),
            isLoading = false
        )
    }

    fun onCleared() {
        scope.cancel()
    }
}
