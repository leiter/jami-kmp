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
import net.jami.model.MemberRole
import net.jami.model.Profile
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
    /** Per-member role (admin/member/invited/…), keyed by member URI. Swarm groups only. */
    val memberRoles: Map<String, MemberRole> = emptyMap(),
    /** Group title, editable by admins. Empty for 1:1 conversations. */
    val groupTitle: String = "",
    /** Group avatar, editable by admins. Falls back to [avatarBytes] when unset. */
    val groupAvatarBytes: ByteArray? = null,
    /**
     * The contact or conversation this screen was opened on no longer exists — it was deleted,
     * blocked, left, or removed from another device. The host navigates back when this turns
     * true; it is a state flag rather than an event so that acting on it twice is harmless.
     */
    val isGone: Boolean = false,
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

    /**
     * The profile [loadContact] resolved, kept so that a later refresh cannot undo it. A
     * presence ping carries no profile, and the contact cache does not always hold one
     * (`setCustomProfile` writes only the service's profile cache), so refreshing from the
     * contact alone used to demote a named peer to its 40-hex id.
     */
    private var currentProfile: Profile? = null

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
                        // Hand the profile over: it is the newest name and avatar there is, and
                        // the contact cache does not always carry it (setCustomProfile writes
                        // only the service's profile cache).
                        if (event.uri == currentContactUri) {
                            currentProfile = event.profile
                            refreshContact()
                        }
                    }
                    is ContactEvent.ContactRemoved -> {
                        // Also covers a removal made on another device: the daemon's
                        // contactRemoved signal reaches ContactService, which re-emits it here.
                        if (event.contact.uri == currentContactUri) markGone()
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
                        conv.contactOrNull?.uri?.rawRingId == contactUri.rawRingId
                }

                // Resolve the contact — prefer the conversation's contact for correct name lookup.
                // contactOrNull, not contact: the match above also accepts a conversation by its
                // own raw id, which can be a group, and Conversation.contact throws for one.
                val resolvedContact = conversation?.contactOrNull
                val contact = resolvedContact
                    ?: contactService.findContact(accountId, contactUri)
                // [uri] is whatever the caller had — opened from a conversation it is the swarm
                // id, not the peer. Every action below (block, remove, call, start conversation)
                // addresses the *contact*, and handing the daemon a conversation id asks it to
                // act on a contact that does not exist: it does nothing and says nothing, so
                // "Delete contact" and "Block contact" were silently dead whenever this screen
                // was reached from a conversation. Keep the resolved peer from here on.
                currentContactUri = contact.uri
                val profile = contactService.loadContactData(contact, accountId)
                currentProfile = profile

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
                val memberRoles = if (isSwarm) conversation!!.roles.toMap() else emptyMap()
                val groupProfile = if (isSwarm) conversation!!.profileFlow.value else Profile.EMPTY_PROFILE

                _state.value = ContactDetailsState(
                    displayName = profile.displayName?.takeIf { it.isNotBlank() }
                        ?: contact.displayUsername,
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
                    memberRoles = memberRoles,
                    groupTitle = groupProfile.displayName ?: "",
                    groupAvatarBytes = groupProfile.avatar,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false)
            } finally {
                // Cleared here rather than on each path: the `?: return@launch` above exits
                // the coroutine without unwinding to the catch block, which left isLoading
                // stuck true whenever there was no current account — a spinner that never
                // stopped. Covers the success path too.
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
            // The reference finishes this screen after every one of these actions (block,
            // unblock, delete, leave): what it showed is no longer what the contact is.
            markGone()
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
            markGone()
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
            markGone()
        }
    }

    fun addMember(memberUri: String) {
        val accountId = currentAccountId ?: return
        val swarmId = _state.value.swarmId.takeIf { it.isNotEmpty() } ?: return
        conversationFacade.addConversationMember(accountId, swarmId, memberUri)
    }

    /**
     * Block a member: in a swarm this *is* `removeConversationMember`. The daemon votes to kick
     * and, for an admin, resolves the vote as a ban and disconnects the peer — there is no
     * separate ban call and no ban flag.
     *
     * The row stays in the list, marked blocked, because that is the only place [unbanMember]
     * can be reached from; dropping it here would make the ban irreversible from the UI.
     */
    fun removeMember(memberUri: String) {
        val accountId = currentAccountId ?: return
        val swarmId = _state.value.swarmId.takeIf { it.isNotEmpty() } ?: return
        conversationFacade.removeConversationMember(accountId, swarmId, memberUri)
        setMemberRole(memberUri, MemberRole.BLOCKED)
    }

    /**
     * Unblock a member. Also not its own daemon call: `addConversationMember` on a banned member
     * is routed to the unban vote (`Conversation::addMember` → `voteUnban`), which only an admin
     * can resolve — and these actions are only offered to admins.
     */
    fun unbanMember(memberUri: String) {
        val accountId = currentAccountId ?: return
        val swarmId = _state.value.swarmId.takeIf { it.isNotEmpty() } ?: return
        conversationFacade.addConversationMember(accountId, swarmId, memberUri)
        setMemberRole(memberUri, MemberRole.MEMBER)
    }

    /**
     * Show the new role straight away. The daemon confirms it with a member event, but that
     * arrives after the commit is announced, which is too slow to feel like a response.
     */
    private fun setMemberRole(memberUri: String, role: MemberRole) {
        _state.value = _state.value.copy(
            memberRoles = _state.value.memberRoles + (memberUri to role),
            memberUris = if (memberUri in _state.value.memberUris) {
                _state.value.memberUris
            } else {
                _state.value.memberUris + memberUri
            },
        )
    }

    /**
     * Rename a swarm group (admin only — the daemon rejects this call otherwise).
     */
    fun updateGroupTitle(title: String) {
        val accountId = currentAccountId ?: return
        val swarmId = _state.value.swarmId.takeIf { it.isNotEmpty() } ?: return
        conversationFacade.updateConversationTitle(accountId, swarmId, title)
        _state.value = _state.value.copy(groupTitle = title)
    }

    /**
     * Update a swarm group's avatar (admin only — the daemon rejects this call otherwise).
     */
    fun updateGroupAvatar(avatarBytes: ByteArray?) {
        val accountId = currentAccountId ?: return
        val swarmId = _state.value.swarmId.takeIf { it.isNotEmpty() } ?: return
        conversationFacade.updateConversationAvatar(accountId, swarmId, avatarBytes)
        _state.value = _state.value.copy(groupAvatarBytes = avatarBytes)
    }

    /**
     * Mark the subject of this screen as gone, so the host navigates back.
     *
     * Without this the screen stayed open on a conversation that had just been deleted — the
     * delete appeared to do nothing, and every action on it afterwards addressed a contact the
     * daemon no longer had.
     */
    private fun markGone() {
        _state.value = _state.value.copy(isGone = true)
    }

    /**
     * Refresh the displayed contact from the cache (presence/profile changes).
     *
     * Names come from [currentProfile] first; the avatar is only ever replaced by a newer one,
     * never cleared — [loadContact] reads it from the vCard on disk, which no refresh re-reads.
     *
     * The contact may be missing from [ContactService]'s cache: that cache is filled lazily and
     * is keyed by the full URI string, while the peer this screen was opened on comes from the
     * conversation's own member list. Bailing out when it is absent is what kept a vCard that
     * arrived while the screen was open from ever being shown — the name only appeared after
     * closing and reopening the screen (seen on a device 2026-11-07). The profile alone is
     * enough to update the header, so each field now falls back to what is already displayed.
     */
    private fun refreshContact() {
        val contactUri = currentContactUri ?: return
        val accountId = currentAccountId ?: return
        val contact = contactService.findContactInCache(accountId, contactUri)
        val current = _state.value
        _state.value = current.copy(
            displayName = currentProfile?.displayName?.takeIf { it.isNotBlank() }
                ?: contact?.displayUsername ?: current.displayName,
            username = contact?.username ?: current.username,
            avatarBytes = currentProfile?.avatar ?: current.avatarBytes,
            isBlocked = contact?.let { it.status == Contact.Status.BLOCKED } ?: current.isBlocked,
        )
    }

    /**
     * Cancel the coroutine scope when this ViewModel is no longer needed.
     */
    public override fun onCleared() {
        scope.cancel()
    }
}
