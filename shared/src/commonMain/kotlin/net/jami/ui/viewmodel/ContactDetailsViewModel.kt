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
import kotlinx.coroutines.launch
import net.jami.model.Contact
import net.jami.model.Conversation
import net.jami.model.Uri
import net.jami.services.AccountService
import net.jami.services.ContactEvent
import net.jami.services.ContactService
import net.jami.services.ConversationFacade
import net.jami.services.DeviceRuntimeService
import net.jami.utils.VCardUtils

/**
 * State for the contact details screen.
 */
data class ContactDetailsState(
    val displayName: String = "",
    val username: String = "",
    val identityHash: String = "",
    val avatarBytes: ByteArray? = null,
    val isBlocked: Boolean = false,
    val isLoading: Boolean = false,
    val conversationType: String = "",
    val swarmId: String = "",
    val contactUri: String = "",
    val isSwarm: Boolean = false,
    val isAdmin: Boolean = false,
    val memberUris: List<String> = emptyList(),
)

/**
 * ViewModel for the contact details screen.
 *
 * Displays detailed information about a contact and provides actions
 * for blocking, removing, or starting a conversation with them.
 */
class ContactDetailsViewModel(
    private val accountService: AccountService,
    private val contactService: ContactService,
    private val conversationFacade: ConversationFacade,
    private val deviceRuntimeService: DeviceRuntimeService,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : ViewModel() {
    private val scope = scope

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
     * Load contact details by conversation URI/ID string.
     *
     * @param uri URI or raw ring ID of the conversation/contact to display.
     */
    fun loadContact(uri: String) {
        scope.launch {
            _state.value = _state.value.copy(isLoading = true)
            try {
                val account = accountService.currentAccount.value ?: return@launch
                val accountId = account.accountId
                currentAccountId = accountId

                val contactUri = Uri.fromString(uri)
                currentContactUri = contactUri

                // Find the conversation first (conversationId may be a swarm ID)
                val conversation = account.getConversations().firstOrNull { conv ->
                    conv.uri.rawRingId == contactUri.rawRingId ||
                        conv.contact?.uri?.rawRingId == contactUri.rawRingId
                }

                // Resolve the contact — prefer the conversation's contact for correct name lookup
                val resolvedContact = conversation?.contact
                val contact = resolvedContact
                    ?: contactService.findContact(accountId, contactUri)
                val profile = contactService.loadContactData(contact, accountId)

                // Load avatar from VCard on disk (same path as NewConversationViewModel)
                val filesDir = deviceRuntimeService.getDataPath()
                val avatarBytes = VCardUtils.loadPeerProfileFromDisk(
                    filesDir, accountId, contact.uri.rawRingId
                )

                val convType = when (conversation?.mode) {
                    Conversation.Mode.OneToOne -> "Private"
                    Conversation.Mode.AdminInvitesOnly -> "Group (admin invites)"
                    Conversation.Mode.InvitesOnly -> "Group (invite only)"
                    Conversation.Mode.Public -> "Public group"
                    Conversation.Mode.Legacy -> "Legacy"
                    else -> ""
                }
                val isSwarm = conversation?.isSwarm == true
                val swarmId = if (isSwarm) conversation!!.uri.uri else ""
                val isAdmin = conversation?.isUserGroupAdmin() == true
                val memberUris = if (isSwarm) {
                    conversation!!.roles.keys.toList()
                } else emptyList()

                _state.value = ContactDetailsState(
                    displayName = profile.displayName ?: contact.displayUsername,
                    username = contact.username ?: "",
                    identityHash = contact.primaryNumber,
                    avatarBytes = avatarBytes ?: profile.avatar,
                    isBlocked = contact.status == Contact.Status.BLOCKED,
                    isLoading = false,
                    conversationType = convType,
                    swarmId = swarmId,
                    contactUri = contact.uri.uri,
                    isSwarm = isSwarm,
                    isAdmin = isAdmin,
                    memberUris = memberUris,
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
                contactService.addContact(accountId, contactUri)
            } else {
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

    fun leaveConversation() {
        val accountId = currentAccountId ?: return
        val swarmId = _state.value.swarmId.takeIf { it.isNotEmpty() } ?: return
        scope.launch {
            conversationFacade.removeConversation(accountId, Uri(Uri.SWARM_SCHEME, swarmId))
        }
    }

    fun addMember(memberUri: String) {
        val accountId = currentAccountId ?: return
        val swarmId = _state.value.swarmId.takeIf { it.isNotEmpty() } ?: return
        conversationFacade.addConversationMember(accountId, swarmId, memberUri)
    }

    fun removeMember(memberUri: String) {
        val accountId = currentAccountId ?: return
        val swarmId = _state.value.swarmId.takeIf { it.isNotEmpty() } ?: return
        conversationFacade.removeConversationMember(accountId, swarmId, memberUri)
        _state.value = _state.value.copy(memberUris = _state.value.memberUris.filter { it != memberUri })
    }

    /**
     * Refresh the displayed contact from the cache (presence/profile changes).
     */
    private fun refreshContact() {
        val contactUri = currentContactUri ?: return
        val accountId = currentAccountId ?: return
        val contact = contactService.findContactInCache(accountId, contactUri) ?: return
        _state.value = _state.value.copy(
            displayName = contact.displayUsername,
            username = contact.username ?: "",
            isBlocked = contact.status == Contact.Status.BLOCKED,
        )
    }

    /**
     * Cancel the coroutine scope when this ViewModel is no longer needed.
     */
    public override fun onCleared() {
        scope.cancel()
    }
}
