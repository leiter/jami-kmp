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
import net.jami.model.Contact
import net.jami.services.AccountService
import net.jami.services.ContactEvent
import net.jami.services.ContactService
import net.jami.ui.contracts.BlockedContactsContract
import net.jami.ui.contracts.ContactItem

/**
 * ViewModel for the contacts list and blocked contacts screens.
 *
 * Loads contacts from the daemon, supports search filtering, and
 * observes contact events (added, removed, presence changes) for
 * real-time updates.
 */
class ContactsViewModel(
    private val contactService: ContactService,
    private val accountService: AccountService
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _blockedContactsState = MutableStateFlow(BlockedContactsContract.State())
    val blockedContactsState: StateFlow<BlockedContactsContract.State> = _blockedContactsState.asStateFlow()

    init {
        scope.launch {
            accountService.currentAccount.filterNotNull().collect {
                loadContacts()
            }
        }

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

    fun onAction(action: BlockedContactsContract.Action) {
        when (action) {
            is BlockedContactsContract.Action.UnblockContact -> {
                scope.launch {
                    val account = accountService.currentAccount.value ?: return@launch
                    val uri = net.jami.model.Uri.fromString(action.uri)
                    contactService.addContact(account.accountId, uri)
                    refreshContactList()
                }
            }
        }
    }

    fun loadContacts() {
        scope.launch {
            _blockedContactsState.value = _blockedContactsState.value.copy(isLoading = true)
            try {
                val account = accountService.currentAccount.value ?: return@launch
                contactService.loadContacts(account.accountId)
                refreshContactList()
            } catch (e: Exception) {
                _blockedContactsState.value = _blockedContactsState.value.copy(isLoading = false)
            }
        }
    }

    private fun refreshContactList() {
        val account = accountService.currentAccount.value ?: return
        val cachedContacts = contactService.getCachedContacts(account.accountId)

        val blockedItems = cachedContacts
            .filter { contact ->
                contact.status == Contact.Status.BLOCKED
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

        _blockedContactsState.value = _blockedContactsState.value.copy(
            contacts = blockedItems,
            isLoading = false
        )
    }

    fun onCleared() {
        scope.cancel()
    }
}
