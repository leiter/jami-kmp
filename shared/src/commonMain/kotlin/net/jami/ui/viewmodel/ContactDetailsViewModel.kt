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

/**
 * State for the contact details screen.
 */
data class ContactDetailsState(
    val displayName: String = "",
    val username: String = "",
    val identityHash: String = "",
    val avatarUri: String? = null,
    val isBlocked: Boolean = false,
    val isLoading: Boolean = false
)

/**
 * ViewModel for the contact details screen.
 *
 * Displays detailed information about a contact and provides actions
 * for blocking, removing, or starting a conversation with them.
 */
class ContactDetailsViewModel(
    private val contactService: ContactService,
    private val conversationFacade: ConversationFacade
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(ContactDetailsState())
    val state: StateFlow<ContactDetailsState> = _state.asStateFlow()

    private var currentContactUri: Uri? = null
    private var currentAccountId: String? = null

    init {
        // Observe contact updates for real-time changes
        scope.launch {
            contactService.contactEvents.collect { event ->
                when (event) {
                    is ContactEvent.PresenceUpdated -> {
                        if (event.contact.uri == currentContactUri) {
                            refreshContact()
                        }
                    }
                    is ContactEvent.ProfileUpdated -> {
                        if (event.uri == currentContactUri) {
                            refreshContact()
                        }
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

    /**
     * Load contact details by URI string.
     *
     * @param uri URI of the contact to display.
     */
    fun loadContact(uri: String) {
        scope.launch {
            _state.value = _state.value.copy(isLoading = true)
            try {
                val contactUri = Uri.fromString(uri)
                currentContactUri = contactUri

                // Determine the current account
                // In a real scenario, accountId would be passed or obtained from AccountService
                currentAccountId = null // Set from context

                val contact = contactService.findContact(currentAccountId ?: "", contactUri)
                val profile = contactService.loadContactData(contact, currentAccountId ?: "")

                _state.value = ContactDetailsState(
                    displayName = profile.displayName ?: contact.displayUsername,
                    username = contact.username ?: "",
                    identityHash = contact.primaryNumber,
                    avatarUri = null, // Avatar is ByteArray, would need encoding for display
                    isBlocked = contact.status == Contact.Status.BLOCKED,
                    isLoading = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    /**
     * Block or unblock the current contact.
     */
    fun blockContact() {
        scope.launch {
            val accountId = currentAccountId ?: return@launch
            val contactUri = currentContactUri ?: return@launch
            val isCurrentlyBlocked = _state.value.isBlocked

            if (isCurrentlyBlocked) {
                // Unblock by re-adding
                contactService.addContact(accountId, contactUri)
            } else {
                // Block (remove with ban)
                contactService.removeContact(accountId, contactUri, ban = true)
            }
            _state.value = _state.value.copy(isBlocked = !isCurrentlyBlocked)
        }
    }

    /**
     * Remove the current contact from the contact list.
     */
    fun removeContact() {
        scope.launch {
            val accountId = currentAccountId ?: return@launch
            val contactUri = currentContactUri ?: return@launch
            contactService.removeContact(accountId, contactUri, ban = false)
        }
    }

    /**
     * Start or open a conversation with the current contact.
     *
     * @return The conversation URI, or null if no contact is loaded.
     */
    suspend fun startConversation(): String? {
        val accountId = currentAccountId ?: return null
        val contactUri = currentContactUri ?: return null
        return try {
            val conversation = conversationFacade.startConversation(accountId, contactUri)
            conversation.uri.uri
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Refresh the displayed contact from the cache.
     */
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

    /**
     * Cancel the coroutine scope when this ViewModel is no longer needed.
     */
    fun onCleared() {
        scope.cancel()
    }
}
