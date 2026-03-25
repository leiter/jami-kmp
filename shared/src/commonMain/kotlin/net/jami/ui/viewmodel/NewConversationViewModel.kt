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
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import net.jami.model.Contact
import net.jami.model.Uri
import net.jami.services.AccountEvent
import net.jami.services.AccountService
import net.jami.services.ContactService
import net.jami.services.ConversationEvent
import net.jami.services.ConversationFacade

/**
 * State for the new conversation creation screen.
 */
data class NewConversationState(
    val searchQuery: String = "",
    val searchResults: List<ContactItem> = emptyList(),
    val selectedContacts: List<ContactItem> = emptyList(),
    val isGroup: Boolean = false,
    val groupName: String = "",
    val isLoading: Boolean = false
)

/**
 * ViewModel for creating a new conversation.
 *
 * Supports searching for contacts by name or Jami ID, selecting
 * one or more contacts, and creating either a 1:1 or group conversation
 * via the daemon.
 */
class NewConversationViewModel(
    private val contactService: ContactService,
    private val conversationFacade: ConversationFacade,
    private val accountService: AccountService
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(NewConversationState())
    val state: StateFlow<NewConversationState> = _state.asStateFlow()

    init {
        // Observe name lookup results for search
        scope.launch {
            accountService.accountEvents.collect { event ->
                when (event) {
                    is AccountEvent.RegisteredNameFound -> {
                        handleNameFound(event)
                    }
                    is AccountEvent.UserSearchEnded -> {
                        handleSearchResults(event)
                    }
                    else -> { /* Other events */ }
                }
            }
        }
    }

    /**
     * Search for contacts or Jami IDs.
     *
     * @param query Search string (username, display name, or Jami ID hash).
     */
    fun search(query: String) {
        _state.value = _state.value.copy(searchQuery = query)

        if (query.isEmpty()) {
            _state.value = _state.value.copy(searchResults = emptyList())
            return
        }

        scope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val account = accountService.currentAccount.value ?: return@launch

            // Search locally in cached contacts
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

            _state.value = _state.value.copy(searchResults = localResults)

            // Also search the name server for remote results
            accountService.searchUser(account.accountId, query)
        }
    }

    /**
     * Select a contact to add to the new conversation.
     *
     * @param contact The contact item to select.
     */
    fun selectContact(contact: ContactItem) {
        val current = _state.value.selectedContacts
        if (current.none { it.uri == contact.uri }) {
            _state.value = _state.value.copy(
                selectedContacts = current + contact
            )
        }
    }

    /**
     * Remove a contact from the selection.
     *
     * @param contact The contact item to deselect.
     */
    fun removeContact(contact: ContactItem) {
        val current = _state.value.selectedContacts
        _state.value = _state.value.copy(
            selectedContacts = current.filter { it.uri != contact.uri }
        )
    }

    /**
     * Create the conversation with the selected contacts.
     *
     * For a single contact, starts a 1:1 conversation.
     * For multiple contacts (when isGroup is true), creates a group conversation.
     *
     * @return The conversation URI if created successfully, null otherwise.
     */
    suspend fun createConversation(): String? {
        val selected = _state.value.selectedContacts
        if (selected.isEmpty()) return null

        val account = accountService.currentAccount.value ?: return null
        val accountId = account.accountId

        return try {
            _state.value = _state.value.copy(isLoading = true)

            if (selected.size == 1 && !_state.value.isGroup) {
                // Start a 1:1 conversation - add contact first, then find/await conversation
                val contactUri = Uri.fromString(selected.first().uri)
                accountService.addContact(accountId, contactUri.uri)
                val conversation = try {
                    conversationFacade.startConversation(accountId, contactUri)
                } catch (_: Exception) {
                    // Conversation may not exist yet - wait for ConversationReady from daemon
                    conversationFacade.conversationEvents
                        .filterIsInstance<ConversationEvent.ConversationReady>()
                        .filter { it.accountId == accountId }
                        .map { conversationFacade.getConversation(accountId, contactUri) }
                        .filterNotNull()
                        .first()
                }
                _state.value = _state.value.copy(isLoading = false)
                conversation.uri.uri
            } else {
                // Create a group conversation
                val memberUris = selected.map { it.uri }
                val conversationId = accountService.startConversation(accountId, memberUris)

                // Set group name if provided
                val groupName = _state.value.groupName
                if (groupName.isNotEmpty()) {
                    accountService.updateConversationInfo(
                        accountId,
                        conversationId,
                        mapOf("title" to groupName)
                    )
                }

                _state.value = _state.value.copy(isLoading = false)
                conversationId
            }
        } catch (e: Exception) {
            _state.value = _state.value.copy(isLoading = false)
            null
        }
    }

    /**
     * Handle a name lookup result from the daemon.
     */
    private fun handleNameFound(event: AccountEvent.RegisteredNameFound) {
        val currentQuery = _state.value.searchQuery
        if (event.name != currentQuery && event.address != currentQuery) return

        if (event.state == 0 && event.address.isNotEmpty()) {
            val newResult = ContactItem(
                uri = event.address,
                displayName = event.name.ifEmpty { event.address },
                username = event.name,
                presenceStatus = Contact.PresenceStatus.OFFLINE,
                avatarUri = null
            )

            val existing = _state.value.searchResults
            if (existing.none { it.uri == newResult.uri }) {
                _state.value = _state.value.copy(
                    searchResults = existing + newResult,
                    isLoading = false
                )
            }
        } else {
            _state.value = _state.value.copy(isLoading = false)
        }
    }

    /**
     * Handle user search results from the daemon.
     */
    private fun handleSearchResults(event: AccountEvent.UserSearchEnded) {
        if (event.query != _state.value.searchQuery) return

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

        // Merge with existing local results, avoiding duplicates
        val existing = _state.value.searchResults.associateBy { it.uri }
        val merged = existing.toMutableMap()
        for (result in results) {
            merged.putIfAbsent(result.uri, result)
        }

        _state.value = _state.value.copy(
            searchResults = merged.values.toList(),
            isLoading = false
        )
    }

    /**
     * Cancel the coroutine scope when this ViewModel is no longer needed.
     */
    fun onCleared() {
        scope.cancel()
    }
}
