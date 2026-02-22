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
import kotlinx.coroutines.launch
import net.jami.model.Contact
import net.jami.model.Uri
import net.jami.services.ContactEvent
import net.jami.services.ContactService
import net.jami.services.ConversationFacade
import net.jami.ui.contracts.ConversationDetailsContract

/**
 * ViewModel for the contact/conversation details screen.
 */
class ContactDetailsViewModel(
    private val contactService: ContactService,
    private val conversationFacade: ConversationFacade
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(ConversationDetailsContract.State())
    val state: StateFlow<ConversationDetailsContract.State> = _state.asStateFlow()

    private var currentContactUri: Uri? = null
    private var currentAccountId: String? = null

    init {
        scope.launch {
            contactService.contactEvents.collect { event ->
                when (event) {
                    is ContactEvent.PresenceUpdated -> {
                        if (event.contact.uri == currentContactUri) refreshContact()
                    }
                    is ContactEvent.ProfileUpdated -> {
                        if (event.uri == currentContactUri) refreshContact()
                    }
                    is ContactEvent.ContactRemoved -> {
                        if (event.contact.uri == currentContactUri) {
                            // Contact was removed, UI should navigate back
                        }
                    }
                    else -> { /* Other events */ }
                }
            }
        }
    }

    fun onAction(action: ConversationDetailsContract.Action) {
        when (action) {
            ConversationDetailsContract.Action.BlockContact -> blockContact()
            ConversationDetailsContract.Action.RemoveContact -> removeContact()
        }
    }

    fun loadContact(uri: String) {
        scope.launch {
            _state.value = _state.value.copy(isLoading = true)
            try {
                val contactUri = Uri.fromString(uri)
                currentContactUri = contactUri
                currentAccountId = null

                val contact = contactService.findContact(currentAccountId ?: "", contactUri)
                val profile = contactService.loadContactData(contact, currentAccountId ?: "")

                _state.value = ConversationDetailsContract.State(
                    displayName = profile.displayName ?: contact.displayUsername,
                    username = contact.username ?: "",
                    identityHash = contact.primaryNumber,
                    avatarUri = null,
                    isBlocked = contact.status == Contact.Status.BLOCKED,
                    isLoading = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    private fun blockContact() {
        scope.launch {
            val accountId = currentAccountId ?: return@launch
            val contactUri = currentContactUri ?: return@launch
            val isCurrentlyBlocked = _state.value.isBlocked

            if (isCurrentlyBlocked) {
                contactService.addContact(accountId, contactUri)
            } else {
                contactService.removeContact(accountId, contactUri, ban = true)
            }
            _state.value = _state.value.copy(isBlocked = !isCurrentlyBlocked)
        }
    }

    private fun removeContact() {
        scope.launch {
            val accountId = currentAccountId ?: return@launch
            val contactUri = currentContactUri ?: return@launch
            contactService.removeContact(accountId, contactUri, ban = false)
        }
    }

    private fun refreshContact() {
        val contactUri = currentContactUri ?: return
        val accountId = currentAccountId ?: return
        val contact = contactService.findContactInCache(accountId, contactUri) ?: return

        _state.value = _state.value.copy(
            displayName = contact.displayUsername,
            username = contact.username ?: "",
            isBlocked = contact.status == Contact.Status.BLOCKED
        )
    }

    fun onCleared() {
        scope.cancel()
    }
}
