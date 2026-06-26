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

import androidx.lifecycle.ViewModel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import net.jami.model.Contact
import net.jami.services.AccountService
import net.jami.services.ContactEvent
import net.jami.services.ContactService

/**
 * Item representing a contact in the contacts list.
 */
data class ContactItem(
    val uri: String,
    val displayName: String,
    val username: String,
    val presenceStatus: Contact.PresenceStatus,
    val avatarUri: String?
)

/**
 * State for the contacts list screen.
 */
data class ContactsState(
    val contacts: List<ContactItem> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false
)

/**
 * ViewModel for the contacts list screen.
 *
 * Loads contacts from the daemon, supports search filtering, and
 * observes contact events (added, removed, presence changes) for
 * real-time updates.
 */
class ContactsViewModel(
    private val contactService: ContactService,
    private val accountService: AccountService,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : ViewModel() {
    private val scope = scope

    private val _state = MutableStateFlow(ContactsState())
    val state: StateFlow<ContactsState> = _state.asStateFlow()

    private val _blockedContacts = MutableStateFlow<List<ContactItem>>(emptyList())
    val blockedContacts: StateFlow<List<ContactItem>> = _blockedContacts.asStateFlow()

    init {
        // Reload contacts when the active account changes
        scope.launch {
            accountService.currentAccount.filterNotNull().collect {
                loadContacts()
            }
        }

        // Observe contact events for real-time updates
        scope.launch {
            contactService.contactEvents.collect { event ->
                when (event) {
                    is ContactEvent.ContactsLoaded,
                    is ContactEvent.ContactAdded,
                    is ContactEvent.ContactRemoved,
                    is ContactEvent.PresenceUpdated -> {
                        refreshContactList()
                    }
                    else -> { /* Profile events handled separately */ }
                }
            }
        }
    }

    /**
     * Load contacts from the daemon for the current account.
     */
    fun loadContacts() {
        scope.launch {
            _state.value = _state.value.copy(isLoading = true)
            try {
                val account = accountService.currentAccount.value ?: return@launch
                contactService.loadContacts(account.accountId)
                refreshContactList()
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    /**
     * Filter the contacts list by a search query.
     *
     * @param query Search string to filter contacts by name or URI.
     */
    fun search(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
        refreshContactList()
    }

    /**
     * Refresh the contact list from the cache, applying the current search filter.
     */
    private fun refreshContactList() {
        val account = accountService.currentAccount.value ?: return
        val cachedContacts = contactService.getCachedContacts(account.accountId)
        val query = _state.value.searchQuery.lowercase()

        val items = cachedContacts
            .filter { contact ->
                contact.status != Contact.Status.BLOCKED
            }
            .filter { contact ->
                if (query.isEmpty()) true
                else {
                    val name = contact.displayUsername.lowercase()
                    val uri = contact.uri.uri.lowercase()
                    name.contains(query) || uri.contains(query)
                }
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
            .sortedBy { it.displayName.lowercase() }

        _state.value = _state.value.copy(
            contacts = items,
            isLoading = false
        )

        val blocked = cachedContacts
            .filter { contact -> contact.status == Contact.Status.BLOCKED }
            .map { contact ->
                ContactItem(
                    uri = contact.uri.uri,
                    displayName = contact.displayUsername,
                    username = contact.username ?: "",
                    presenceStatus = contact.presenceStatus.value,
                    avatarUri = null,
                )
            }
            .sortedBy { it.displayName.lowercase() }
        _blockedContacts.value = blocked
    }

    fun unblockContact(uri: String) {
        scope.launch {
            val accountId = accountService.currentAccount.value?.accountId ?: return@launch
            contactService.addContact(accountId, net.jami.model.Uri.fromString(uri))
        }
    }

    /**
     * Cancel the coroutine scope when this ViewModel is no longer needed.
     */
    public override fun onCleared() {
        scope.cancel()
    }
}
