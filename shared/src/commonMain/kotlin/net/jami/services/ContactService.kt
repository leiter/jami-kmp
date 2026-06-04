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
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import net.jami.model.Contact
import net.jami.model.ContactViewModel
import net.jami.model.Conversation
import net.jami.model.Profile
import net.jami.model.Uri
import net.jami.ui.utils.scaleImageBytes
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
    private val daemonBridge: DaemonBridgeApi,
    private val vCardService: VCardService
) {
    // Contact cache: accountId -> (contactUri -> Contact)
    private val contactCache = mutableMapOf<String, MutableMap<String, Contact>>()

    // Profile cache: accountId:contactUri -> Profile
    private val profileCache = mutableMapOf<String, Profile>()

    // Active presence subscriptions: accountId:contactUri -> refCount
    private val activeSubscriptions = mutableMapOf<String, Int>()

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
        daemonBridge.subscribeBuddy(accountId, uri.rawRingId, subscribe)
    }

    /**
     * Handle presence update from daemon callback.
     */
    internal fun onPresenceUpdate(accountId: String, uriString: String, status: Int) {
        Log.d(TAG, "onPresenceUpdate: uriString=$uriString status=$status accountId=$accountId")
        val uri = Uri.fromString(uriString)
        Log.d(TAG, "onPresenceUpdate: parsed uri.uri=${uri.uri} rawRingId=${uri.rawRingId}")
        // Use Account's contact cache — the same objects held by Conversation.contact —
        // so that Contact.isOnline reflects immediately in the conversation list.
        val account = accountService.getAccount(accountId)
        if (account == null) {
            Log.w(TAG, "onPresenceUpdate: account not found for $accountId")
            return
        }
        val contact = account.getContactFromCache(uri)
        val presenceStatus = when (status) {
            0 -> Contact.PresenceStatus.OFFLINE
            1 -> Contact.PresenceStatus.AVAILABLE
            else -> Contact.PresenceStatus.CONNECTED
        }
        Log.d(TAG, "onPresenceUpdate: setting presence $presenceStatus for contact ${contact.uri.rawRingId}, isOnline will be ${presenceStatus != Contact.PresenceStatus.OFFLINE}")
        contact.setPresence(presenceStatus)
        scope.launch {
            _contactEvents.emit(ContactEvent.PresenceUpdated(accountId, contact))
            Log.d(TAG, "onPresenceUpdate: emitted PresenceUpdated event for ${contact.uri.rawRingId}")
        }
    }

    /**
     * Handle profile received from daemon callback.
     */
    internal fun onProfileReceived(accountId: String, peerId: String, vcardPath: String) {
        scope.launch {
            val uri = Uri.fromString(peerId)
            val vcardContent = net.jami.utils.FileUtils.readText(vcardPath)
            if (vcardContent != null) {
                val vcard = net.jami.utils.VCardUtils.parseVCard(vcardContent)
                if (vcard != null && !vcard.isEmpty) {
                    // Scale the photo to ≤512 px before storing in the in-session profile cache
                    // (same as VCardService disk cache, keeps memory footprint small).
                    val scaledPhoto = vcard.photo?.let { scaleImageBytes(it, 512) }
                    val profile = Profile(
                        displayName = vcard.formattedName,
                        avatar = scaledPhoto
                    )
                    val cacheKey = "$accountId:${uri.uri}"
                    profileCache[cacheKey] = profile
                    val contact = findContactInCache(accountId, uri)
                    if (contact != null) {
                        contact.loadedProfile = profile
                    }
                    // Invalidate the VCardService disk + memory cache so the next
                    // buildConversationItems() call picks up the new vcf from disk.
                    vCardService.invalidatePeer(accountId, uri.rawRingId)
                    _contactEvents.emit(ContactEvent.ProfileUpdated(accountId, uri, profile))
                }
            }
            _contactEvents.emit(ContactEvent.ProfileReceived(accountId, uri, vcardPath))
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

        // Return a profile based on contact display name or username
        val profile = Profile(
            displayName = contact.displayName?.ifEmpty { contact.username } ?: contact.username,
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
     *
     * When [withPresence] is true, subscribes to buddy presence on flow start
     * and unsubscribes when the flow completes. Uses ref-counting so multiple
     * observers share the same subscription.
     */
    fun observeContact(
        accountId: String,
        contact: Contact,
        withPresence: Boolean
    ): Flow<ContactViewModel> {
        val subscriptionKey = "$accountId:${contact.uri.uri}"

        return contact.presenceStatus
            .combine(contactEvents) { presence, event ->
                // Re-emit on presence change or relevant contact event
                val profile = profileCache[subscriptionKey] ?: Profile.EMPTY_PROFILE
                ContactViewModel(
                    contact = contact,
                    profile = profile,
                    registeredName = contact.username,
                    presence = if (withPresence) presence else Contact.PresenceStatus.OFFLINE
                )
            }
            .onStart {
                if (withPresence) {
                    incrementSubscription(accountId, contact.uri, subscriptionKey)
                }
            }
            .onCompletion {
                if (withPresence) {
                    decrementSubscription(accountId, contact.uri, subscriptionKey)
                }
            }
    }

    private fun incrementSubscription(accountId: String, uri: Uri, key: String) {
        synchronized(activeSubscriptions) {
            val count = activeSubscriptions[key] ?: 0
            if (count == 0) {
                subscribeBuddy(accountId, uri, true)
                Log.d(TAG, "Subscribed to presence: $key")
            }
            activeSubscriptions[key] = count + 1
        }
    }

    private fun decrementSubscription(accountId: String, uri: Uri, key: String) {
        synchronized(activeSubscriptions) {
            val count = activeSubscriptions[key] ?: return
            val newCount = count - 1
            if (newCount <= 0) {
                activeSubscriptions.remove(key)
                subscribeBuddy(accountId, uri, false)
                Log.d(TAG, "Unsubscribed from presence: $key")
            } else {
                activeSubscriptions[key] = newCount
            }
        }
    }

    /**
     * Observe multiple contacts with presence updates.
     *
     * Combines presence flows from all contacts and emits updated list
     * whenever any contact's presence changes.
     */
    fun observeContacts(
        accountId: String,
        contacts: List<Contact>,
        withPresence: Boolean
    ): Flow<List<ContactViewModel>> {
        if (contacts.isEmpty()) {
            return flowOf(emptyList())
        }

        // Create individual flows for each contact
        val contactFlows = contacts.map { contact ->
            observeContact(accountId, contact, withPresence)
        }

        // Combine all flows - emits whenever any contact updates
        return combine(contactFlows) { viewModels ->
            viewModels.toList()
        }
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
    data class ProfileReceived(val accountId: String, val uri: Uri, val vcardPath: String) : ContactEvent()
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
