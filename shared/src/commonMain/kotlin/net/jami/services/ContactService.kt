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
package net.jami.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import net.jami.model.Contact
import net.jami.model.ContactViewModel
import net.jami.model.Conversation
import net.jami.model.Profile
import net.jami.model.Uri
import net.jami.utils.Log

/**
 * Service for managing contacts and their profiles.
 *
 * Handles:
 * - Contact cache management per account
 * - Contact lookup by URI
 * - Profile loading (name, avatar)
 * - Presence subscription
 * - Contact events (added, removed, updated)
 *
 * Ported from: jami-client-android libjamiclient
 * Changes: RxJava → Kotlin Flow
 */
class ContactService(
    private val scope: CoroutineScope,
    private val accountService: AccountService,
    private val daemonBridge: DaemonBridge
) {
    // Contact cache: accountId -> (contactUri -> Contact)
    private val contactCache = mutableMapOf<String, MutableMap<String, Contact>>()

    // Profile cache: accountId:contactUri -> Profile
    private val profileCache = mutableMapOf<String, Profile>()

    // Events
    private val _contactEvents = MutableSharedFlow<ContactEvent>()
    val contactEvents: SharedFlow<ContactEvent> = _contactEvents.asSharedFlow()

    // ==================== Contact Cache ====================

    /**
     * Get or create a contact from the cache.
     */
    fun getContactFromCache(accountId: String, uri: Uri): Contact {
        val accountCache = contactCache.getOrPut(accountId) { mutableMapOf() }
        return accountCache.getOrPut(uri.uri) {
            Contact(uri).also {
                Log.d(TAG, "Created new contact in cache: ${uri.uri}")
            }
        }
    }

    /**
     * Get a contact from the cache if it exists.
     */
    fun findContactInCache(accountId: String, uri: Uri): Contact? {
        return contactCache[accountId]?.get(uri.uri)
    }

    /**
     * Get all cached contacts for an account.
     */
    fun getCachedContacts(accountId: String): List<Contact> {
        return contactCache[accountId]?.values?.toList() ?: emptyList()
    }

    /**
     * Clear the contact cache for an account.
     */
    fun clearCache(accountId: String) {
        contactCache.remove(accountId)
        // Remove profile cache entries for this account
        profileCache.keys.filter { it.startsWith("$accountId:") }.forEach {
            profileCache.remove(it)
        }
    }

    // ==================== Contact Loading ====================

    /**
     * Load contacts from the daemon for an account.
     */
    fun loadContacts(accountId: String) {
        val contactsList = daemonBridge.getContacts(accountId)
        val accountCache = contactCache.getOrPut(accountId) { mutableMapOf() }

        for (contactMap in contactsList) {
            val uriString = contactMap["uri"] ?: continue
            val uri = Uri.fromString(uriString)
            val contact = accountCache.getOrPut(uri.uri) { Contact(uri) }

            // Update contact properties from daemon data
            contactMap["added"]?.toLongOrNull()?.let { contact.addedDate = it }
            // Update contact status based on daemon data
            val isBanned = contactMap["banned"]?.toBoolean() ?: false
            val isConfirmed = contactMap["confirmed"]?.toBoolean() ?: false
            contact.status = when {
                isBanned -> Contact.Status.BLOCKED
                isConfirmed -> Contact.Status.CONFIRMED
                else -> Contact.Status.NO_REQUEST
            }
        }

        scope.launch {
            _contactEvents.emit(ContactEvent.ContactsLoaded(accountId, getCachedContacts(accountId)))
        }
    }

    /**
     * Get contact details from the daemon.
     */
    fun getContactDetails(accountId: String, uri: Uri): Map<String, String> {
        return daemonBridge.getContactDetails(accountId, uri.uri)
    }

    // ==================== Contact Operations ====================

    /**
     * Add a contact.
     */
    fun addContact(accountId: String, uri: Uri) {
        daemonBridge.addContact(accountId, uri.uri)
        val contact = getContactFromCache(accountId, uri)
        contact.status = Contact.Status.CONFIRMED
        scope.launch {
            _contactEvents.emit(ContactEvent.ContactAdded(accountId, contact))
        }
    }

    /**
     * Remove a contact.
     */
    fun removeContact(accountId: String, uri: Uri, ban: Boolean = false) {
        daemonBridge.removeContact(accountId, uri.uri, ban)
        val contact = findContactInCache(accountId, uri)
        contactCache[accountId]?.remove(uri.uri)
        if (contact != null) {
            scope.launch {
                _contactEvents.emit(ContactEvent.ContactRemoved(accountId, contact, ban))
            }
        }
    }

    // ==================== Presence ====================

    /**
     * Subscribe to presence updates for a contact.
     */
    fun subscribeBuddy(accountId: String, uri: Uri, subscribe: Boolean) {
        daemonBridge.subscribeBuddy(accountId, uri.uri, subscribe)
    }

    /**
     * Handle presence update from daemon callback.
     */
    internal fun onPresenceUpdate(accountId: String, uriString: String, status: Int) {
        val uri = Uri.fromString(uriString)
        val contact = findContactInCache(accountId, uri) ?: return
        val presenceStatus = when (status) {
            0 -> Contact.PresenceStatus.OFFLINE
            1 -> Contact.PresenceStatus.AVAILABLE
            else -> Contact.PresenceStatus.CONNECTED
        }
        contact.setPresence(presenceStatus)
        scope.launch {
            _contactEvents.emit(ContactEvent.PresenceUpdated(accountId, contact))
        }
    }

    // ==================== Profile ====================

    /**
     * Load contact profile (name and photo).
     */
    suspend fun loadContactData(contact: Contact, accountId: String): Profile {
        val cacheKey = "$accountId:${contact.uri.uri}"

        // Check cache first
        profileCache[cacheKey]?.let { return it }

        // TODO: Load profile from VCard file or daemon
        // For now, return a profile based on contact username
        val profile = Profile(
            displayName = contact.username ?: contact.displayName,
            avatar = null
        )

        profileCache[cacheKey] = profile
        return profile
    }

    /**
     * Set a custom profile for a contact.
     */
    fun setCustomProfile(accountId: String, uri: Uri, profile: Profile) {
        val cacheKey = "$accountId:${uri.uri}"
        profileCache[cacheKey] = profile
        scope.launch {
            _contactEvents.emit(ContactEvent.ProfileUpdated(accountId, uri, profile))
        }
    }

    // ==================== Observation ====================

    /**
     * Observe a single contact with optional presence.
     */
    fun observeContact(
        accountId: String,
        contact: Contact,
        withPresence: Boolean
    ): Flow<ContactViewModel> {
        if (withPresence) {
            subscribeBuddy(accountId, contact.uri, true)
        }

        return contactEvents
            .map { event ->
                when (event) {
                    is ContactEvent.PresenceUpdated ->
                        if (event.contact.uri == contact.uri) contact else null
                    is ContactEvent.ProfileUpdated ->
                        if (event.uri == contact.uri) contact else null
                    else -> null
                }
            }
            .map {
                val profile = profileCache["$accountId:${contact.uri.uri}"] ?: Profile.EMPTY_PROFILE
                ContactViewModel(
                    contact = contact,
                    profile = profile,
                    registeredName = contact.username,
                    presence = if (withPresence) contact.presenceStatus.value else Contact.PresenceStatus.OFFLINE
                )
            }
    }

    /**
     * Observe multiple contacts.
     */
    fun observeContacts(
        accountId: String,
        contacts: List<Contact>,
        withPresence: Boolean
    ): Flow<List<ContactViewModel>> {
        if (contacts.isEmpty()) {
            return flowOf(emptyList())
        }

        return flowOf(contacts.map { contact ->
            if (withPresence) {
                subscribeBuddy(accountId, contact.uri, true)
            }
            val profile = profileCache["$accountId:${contact.uri.uri}"] ?: Profile.EMPTY_PROFILE
            ContactViewModel(
                contact = contact,
                profile = profile,
                registeredName = contact.username,
                presence = if (withPresence) contact.presenceStatus.value else Contact.PresenceStatus.OFFLINE
            )
        })
    }

    /**
     * Get a loaded conversation view model.
     */
    suspend fun getLoadedConversation(conversation: Conversation): ConversationItemViewModel {
        val contacts = conversation.contacts
        val contactViewModels = contacts.map { contact ->
            val profile = loadContactData(contact, conversation.accountId)
            ContactViewModel(
                contact = contact,
                profile = profile,
                registeredName = contact.username,
                presence = Contact.PresenceStatus.OFFLINE
            )
        }

        return ConversationItemViewModel(
            conversation = conversation,
            profile = conversation.profileFlow.value,
            contacts = contactViewModels,
            hasPresence = false
        )
    }

    // ==================== Lookup ====================

    /**
     * Find a contact by URI, creating it if necessary.
     */
    fun findContact(accountId: String, uri: Uri): Contact {
        return getContactFromCache(accountId, uri)
    }

    /**
     * Find a contact by phone number or identifier.
     */
    fun findContactByNumber(accountId: String, number: String): Contact? {
        if (number.isEmpty()) return null
        return findContact(accountId, Uri.fromString(number))
    }

    companion object {
        private const val TAG = "ContactService"
    }
}

/**
 * Events emitted by ContactService.
 */
sealed class ContactEvent {
    data class ContactsLoaded(val accountId: String, val contacts: List<Contact>) : ContactEvent()
    data class ContactAdded(val accountId: String, val contact: Contact) : ContactEvent()
    data class ContactRemoved(val accountId: String, val contact: Contact, val banned: Boolean) : ContactEvent()
    data class PresenceUpdated(val accountId: String, val contact: Contact) : ContactEvent()
    data class ProfileUpdated(val accountId: String, val uri: Uri, val profile: Profile) : ContactEvent()
}

/**
 * View model for a conversation in a list.
 */
data class ConversationItemViewModel(
    val conversation: Conversation,
    val profile: Profile,
    val contacts: List<ContactViewModel>,
    val hasPresence: Boolean
) {
    val accountId: String get() = conversation.accountId
    val uri: Uri get() = conversation.uri
    val isSwarm: Boolean get() = conversation.isSwarm

    fun matches(query: String): Boolean {
        val lowerQuery = query.lowercase()
        if (profile.displayName?.lowercase()?.contains(lowerQuery) == true) return true
        for (contactVm in contacts) {
            if (contactVm.displayName.lowercase().contains(lowerQuery)) return true
            if (contactVm.contact.primaryNumber.lowercase().contains(lowerQuery)) return true
        }
        return false
    }

    enum class Title {
        None,
        Conversations,
        PublicDirectory
    }
}
