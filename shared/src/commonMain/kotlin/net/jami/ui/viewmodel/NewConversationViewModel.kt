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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
import net.jami.model.TextMessage
import net.jami.model.Uri
import net.jami.services.AccountEvent
import net.jami.services.AccountService
import net.jami.services.ContactService
import net.jami.services.ConversationEvent
import net.jami.services.ConversationFacade
import net.jami.services.DeviceRuntimeService
import net.jami.utils.VCardUtils

/**
 * State for the new conversation creation screen.
 */
data class NewConversationState(
    val searchQuery: String = "",
    val publicDirectoryResults: List<ContactItem> = emptyList(),
    val conversationResults: List<ConversationItem> = emptyList(),
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
    private val accountService: AccountService,
    private val deviceRuntimeService: DeviceRuntimeService,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val scope = scope
    private var searchJob: Job? = null

    private val _state = MutableStateFlow(NewConversationState())
    val state: StateFlow<NewConversationState> = _state.asStateFlow()

    init {
        // Observe name lookup results for public directory search
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

    /**
     * Search for contacts, conversations, or Jami IDs with 300ms debounce.
     *
     * When query is empty all existing conversations are loaded so the screen
     * shows a useful list instead of a blank page.
     *
     * @param query Search string (username, display name, or Jami ID hash).
     */
    fun search(query: String) {
        _state.value = _state.value.copy(
            searchQuery = query,
            publicDirectoryResults = emptyList(),
            conversationResults = emptyList(),
        )
        searchJob?.cancel()
        if (query.isEmpty()) {
            searchJob = scope.launch { loadAllConversations() }
            return
        }
        searchJob = scope.launch {
            delay(300)
            performSearch(query)
        }
    }

    /**
     * Load all existing conversations for the current account (no filtering).
     * Used when the search query is empty to fill the result list.
     */
    private suspend fun loadAllConversations() {
        val account = accountService.currentAccount.value ?: return
        val accountId = account.accountId
        val filesDir = deviceRuntimeService.getDataPath()
        val convResults = account.getConversations().mapNotNull { conversation ->
            val contact = conversation.contact
            val displayName = conversation.profileFlow.value.displayName?.takeIf { it.isNotBlank() }
                ?: contact?.displayUsername
                ?: conversation.uri.rawRingId
            val lastEvent = conversation.lastEvent
            val lastMessage = if (lastEvent is TextMessage) lastEvent.body ?: "" else ""
            val timestamp = lastEvent?.timestamp ?: 0L
            val avatarBytes = contact?.let { c ->
                VCardUtils.loadPeerProfileFromDisk(filesDir, accountId, c.uri.rawRingId)
            }
            ConversationItem(
                id = conversation.uri.rawRingId,
                contactId = contact?.uri?.rawRingId,
                displayName = displayName,
                lastMessage = lastMessage,
                timestamp = timestamp,
                unreadCount = 0,
                avatarBytes = avatarBytes,
                isOnline = contact?.isOnline == true,
                isRead = lastEvent?.isRead ?: true,
            )
        }.sortedByDescending { it.timestamp }
        _state.value = _state.value.copy(conversationResults = convResults)
    }

    /**
     * Reset all search state and reload the full conversation list.
     * Called when entering SearchScreen or NewConversationScreen.
     */
    fun resetSearch() {
        searchJob?.cancel()
        _state.value = _state.value.copy(
            searchQuery = "",
            publicDirectoryResults = emptyList(),
            conversationResults = emptyList(),
            selectedContacts = emptyList(),
            isGroup = false,
            groupName = "",
            isLoading = false,
        )
        searchJob = scope.launch { loadAllConversations() }
    }

    /**
     * Toggle group conversation mode.
     */
    fun setGroupMode(enabled: Boolean) {
        _state.value = _state.value.copy(isGroup = enabled)
    }

    /**
     * Set the group conversation name.
     */
    fun setGroupName(name: String) {
        _state.value = _state.value.copy(groupName = name)
    }

    /**
     * Perform the actual search: filter conversations locally and route public directory lookup.
     */
    private suspend fun performSearch(query: String) {
        val account = accountService.currentAccount.value ?: return
        val accountId = account.accountId
        val filesDir = deviceRuntimeService.getDataPath()
        val lowerQuery = query.lowercase()

        // Filter existing conversations by query
        val convResults = account.getConversations().mapNotNull { conversation ->
            val contact = conversation.contact
            val displayName = conversation.profileFlow.value.displayName?.takeIf { it.isNotBlank() }
                ?: contact?.displayUsername
                ?: conversation.uri.rawRingId
            if (!displayName.lowercase().contains(lowerQuery)) return@mapNotNull null

            val lastEvent = conversation.lastEvent
            val lastMessage = if (lastEvent is TextMessage) lastEvent.body ?: "" else ""
            val timestamp = lastEvent?.timestamp ?: 0L
            val avatarBytes = contact?.let { c ->
                VCardUtils.loadPeerProfileFromDisk(filesDir, accountId, c.uri.rawRingId)
            }

            ConversationItem(
                id = conversation.uri.rawRingId,
                contactId = contact?.uri?.rawRingId,
                displayName = displayName,
                lastMessage = lastMessage,
                timestamp = timestamp,
                unreadCount = 0,
                avatarBytes = avatarBytes,
                isOnline = contact?.isOnline == true,
                isRead = lastEvent?.isRead ?: true,
            )
        }
        _state.value = _state.value.copy(conversationResults = convResults)

        // Route public directory lookup: hex ID / username@server / plain name
        _state.value = _state.value.copy(isLoading = true)
        when {
            isJamiId(query) -> {
                // Show immediately as a tappable result; also resolve username via nameserver
                _state.value = _state.value.copy(
                    publicDirectoryResults = listOf(
                        ContactItem(
                            uri = query,
                            displayName = query,
                            username = "",
                            presenceStatus = Contact.PresenceStatus.OFFLINE,
                            avatarUri = null
                        )
                    ),
                    isLoading = true
                )
                accountService.lookupAddress(accountId, query)
            }
            query.contains("@") -> {
                // username@nameserver — single nameserver lookup
                accountService.lookupName(accountId, query)
            }
            else -> {
                // Plain text — both nameserver lookup and JAMS directory search
                accountService.lookupName(accountId, query)
                accountService.searchUser(accountId, query)
            }
        }
    }

    /**
     * Returns true if the query looks like a 40+ char hex Jami ID.
     */
    private fun isJamiId(query: String): Boolean =
        query.length >= 40 && query.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }

    /**
     * Select a contact to add to the new conversation.
     */
    fun selectContact(contact: ContactItem) {
        val current = _state.value.selectedContacts
        if (current.none { it.uri == contact.uri }) {
            _state.value = _state.value.copy(selectedContacts = current + contact)
        }
    }

    /**
     * Remove a contact from the selection.
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
                // Start a 1:1 conversation — add contact first, then find/await conversation
                val contactUri = Uri.fromString(selected.first().uri)
                accountService.addContact(accountId, contactUri.uri)
                val conversation = try {
                    conversationFacade.startConversation(accountId, contactUri)
                } catch (_: Exception) {
                    // Conversation may not exist yet — wait for ConversationReady from daemon
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
     * Handle a name lookup result from the daemon (for "@" queries).
     */
    private fun handleNameFound(event: AccountEvent.RegisteredNameFound) {
        val currentQuery = _state.value.searchQuery
        // Match by the original query sent to daemon, or by the resolved name/address
        if (event.query != currentQuery && event.name != currentQuery && event.address != currentQuery) return

        if (event.state == 0 && event.address.isNotEmpty()) {
            val newResult = ContactItem(
                uri = event.address,
                displayName = event.name.ifEmpty { event.address },
                username = event.name,
                presenceStatus = Contact.PresenceStatus.OFFLINE,
                avatarUri = null
            )
            val existing = _state.value.publicDirectoryResults
            // Replace placeholder (same URI) or append if new
            val updated = if (existing.any { it.uri == newResult.uri }) {
                existing.map { if (it.uri == newResult.uri) newResult else it }
            } else {
                existing + newResult
            }
            _state.value = _state.value.copy(
                publicDirectoryResults = updated,
                isLoading = false
            )
        } else {
            _state.value = _state.value.copy(isLoading = false)
        }
    }

    /**
     * Handle user search results from the daemon (for plain name queries).
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

        // Merge with existing public directory results, avoiding duplicates
        val existing = _state.value.publicDirectoryResults.associateBy { it.uri }
        val merged = existing.toMutableMap()
        for (result in results) {
            if (!merged.containsKey(result.uri)) merged[result.uri] = result
        }

        _state.value = _state.value.copy(
            publicDirectoryResults = merged.values.toList(),
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
