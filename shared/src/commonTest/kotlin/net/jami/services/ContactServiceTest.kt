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

import net.jami.model.Contact
import net.jami.model.ContactViewModel
import net.jami.model.Conversation
import net.jami.model.Profile
import net.jami.model.Uri
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ContactServiceTest {

    // ==================== ContactEvent Tests ====================

    @Test
    fun testContactEventContactsLoaded() {
        val contacts = listOf(
            Contact(Uri.fromId("abc123")),
            Contact(Uri.fromId("def456"))
        )
        val event = ContactEvent.ContactsLoaded("acc123", contacts)
        assertEquals("acc123", event.accountId)
        assertEquals(2, event.contacts.size)
        assertEquals("abc123", event.contacts[0].uri.rawRingId)
    }

    @Test
    fun testContactEventContactAdded() {
        val contact = Contact(Uri.fromId("abc123"))
        val event = ContactEvent.ContactAdded("acc123", contact)
        assertEquals("acc123", event.accountId)
        assertEquals(contact, event.contact)
    }

    @Test
    fun testContactEventContactRemoved() {
        val contact = Contact(Uri.fromId("abc123"))
        val eventNotBanned = ContactEvent.ContactRemoved("acc123", contact, banned = false)
        assertEquals("acc123", eventNotBanned.accountId)
        assertEquals(contact, eventNotBanned.contact)
        assertFalse(eventNotBanned.banned)

        val eventBanned = ContactEvent.ContactRemoved("acc123", contact, banned = true)
        assertTrue(eventBanned.banned)
    }

    @Test
    fun testContactEventPresenceUpdated() {
        val contact = Contact(Uri.fromId("abc123"))
        val event = ContactEvent.PresenceUpdated("acc123", contact)
        assertEquals("acc123", event.accountId)
        assertEquals(contact, event.contact)
    }

    @Test
    fun testContactEventProfileUpdated() {
        val uri = Uri.fromId("abc123")
        val profile = Profile("Test User", null)
        val event = ContactEvent.ProfileUpdated("acc123", uri, profile)
        assertEquals("acc123", event.accountId)
        assertEquals(uri, event.uri)
        assertEquals("Test User", event.profile.displayName)
    }

    // ==================== ConversationItemViewModel Tests ====================

    @Test
    fun testConversationItemViewModelProperties() {
        val contact = Contact(Uri.fromId("abc123"))
        val conversation = Conversation("acc123", contact)
        val profile = Profile("Group Chat", null)
        val contactVm = ContactViewModel(contact, Profile("Test User", null))

        val viewModel = ConversationItemViewModel(
            conversation = conversation,
            profile = profile,
            contacts = listOf(contactVm),
            hasPresence = true
        )

        assertEquals("acc123", viewModel.accountId)
        assertEquals(conversation.uri, viewModel.uri)
        assertEquals(profile, viewModel.profile)
        assertEquals(1, viewModel.contacts.size)
        assertTrue(viewModel.hasPresence)
    }

    @Test
    fun testConversationItemViewModelIsSwarm() {
        val contact = Contact(Uri.fromId("abc123"))
        val legacyConversation = Conversation("acc123", contact)
        val legacyVm = ConversationItemViewModel(
            conversation = legacyConversation,
            profile = Profile.EMPTY_PROFILE,
            contacts = emptyList(),
            hasPresence = false
        )
        assertFalse(legacyVm.isSwarm)

        val swarmConversation = Conversation("acc123", Uri.fromString("swarm:conv123"), Conversation.Mode.OneToOne)
        val swarmVm = ConversationItemViewModel(
            conversation = swarmConversation,
            profile = Profile.EMPTY_PROFILE,
            contacts = emptyList(),
            hasPresence = false
        )
        assertTrue(swarmVm.isSwarm)
    }

    @Test
    fun testConversationItemViewModelMatchesProfileName() {
        val contact = Contact(Uri.fromId("abc123"))
        val conversation = Conversation("acc123", contact)
        val profile = Profile("Alice Smith", null)
        val contactVm = ContactViewModel(contact, Profile.EMPTY_PROFILE)

        val viewModel = ConversationItemViewModel(
            conversation = conversation,
            profile = profile,
            contacts = listOf(contactVm),
            hasPresence = false
        )

        assertTrue(viewModel.matches("alice"))
        assertTrue(viewModel.matches("Alice"))
        assertTrue(viewModel.matches("SMITH"))
        assertFalse(viewModel.matches("bob"))
    }

    @Test
    fun testConversationItemViewModelMatchesContactName() {
        val contact = Contact(Uri.fromId("abc123"))
        val conversation = Conversation("acc123", contact)
        val profile = Profile.EMPTY_PROFILE
        val contactVm = ContactViewModel(
            contact = contact,
            profile = Profile("Bob Jones", null)
        )

        val viewModel = ConversationItemViewModel(
            conversation = conversation,
            profile = profile,
            contacts = listOf(contactVm),
            hasPresence = false
        )

        assertTrue(viewModel.matches("bob"))
        assertTrue(viewModel.matches("Jones"))
    }

    @Test
    fun testConversationItemViewModelMatchesContactNumber() {
        val contact = Contact(Uri.fromId("abc123def456"))
        val conversation = Conversation("acc123", contact)
        val contactVm = ContactViewModel(contact, Profile.EMPTY_PROFILE)

        val viewModel = ConversationItemViewModel(
            conversation = conversation,
            profile = Profile.EMPTY_PROFILE,
            contacts = listOf(contactVm),
            hasPresence = false
        )

        assertTrue(viewModel.matches("abc123"))
        assertTrue(viewModel.matches("def456"))
    }

    @Test
    fun testConversationItemViewModelTitleEnum() {
        assertEquals(ConversationItemViewModel.Title.None, ConversationItemViewModel.Title.None)
        assertEquals(ConversationItemViewModel.Title.Conversations, ConversationItemViewModel.Title.Conversations)
        assertEquals(ConversationItemViewModel.Title.PublicDirectory, ConversationItemViewModel.Title.PublicDirectory)
    }

    // ==================== Contact Status Tests ====================

    @Test
    fun testContactStatusValues() {
        assertEquals(Contact.Status.BLOCKED, Contact.Status.BLOCKED)
        assertEquals(Contact.Status.REQUEST_SENT, Contact.Status.REQUEST_SENT)
        assertEquals(Contact.Status.CONFIRMED, Contact.Status.CONFIRMED)
        assertEquals(Contact.Status.NO_REQUEST, Contact.Status.NO_REQUEST)
    }

    @Test
    fun testContactStatusAssignment() {
        val contact = Contact(Uri.fromId("abc123"))
        assertEquals(Contact.Status.NO_REQUEST, contact.status)

        contact.status = Contact.Status.CONFIRMED
        assertEquals(Contact.Status.CONFIRMED, contact.status)

        contact.status = Contact.Status.BLOCKED
        assertEquals(Contact.Status.BLOCKED, contact.status)
        assertTrue(contact.isBlocked)
    }

    // ==================== Contact Presence Tests ====================

    @Test
    fun testContactPresenceStatusValues() {
        assertEquals(Contact.PresenceStatus.OFFLINE, Contact.PresenceStatus.OFFLINE)
        assertEquals(Contact.PresenceStatus.AVAILABLE, Contact.PresenceStatus.AVAILABLE)
        assertEquals(Contact.PresenceStatus.CONNECTED, Contact.PresenceStatus.CONNECTED)
    }

    @Test
    fun testContactSetPresence() {
        val contact = Contact(Uri.fromId("abc123"))
        assertEquals(Contact.PresenceStatus.OFFLINE, contact.presenceStatus.value)
        assertFalse(contact.isOnline)

        contact.setPresence(Contact.PresenceStatus.AVAILABLE)
        assertEquals(Contact.PresenceStatus.AVAILABLE, contact.presenceStatus.value)
        assertTrue(contact.isOnline)

        contact.setPresence(Contact.PresenceStatus.CONNECTED)
        assertEquals(Contact.PresenceStatus.CONNECTED, contact.presenceStatus.value)
        assertTrue(contact.isOnline)

        contact.setPresence(Contact.PresenceStatus.OFFLINE)
        assertEquals(Contact.PresenceStatus.OFFLINE, contact.presenceStatus.value)
        assertFalse(contact.isOnline)
    }

    // ==================== ContactViewModel Tests ====================

    @Test
    fun testContactViewModelDisplayUri() {
        val contact = Contact(Uri.fromId("abc123"))
        val profile = Profile.EMPTY_PROFILE

        // Without registered name, uses contact URI
        val vmNoName = ContactViewModel(contact, profile)
        assertEquals("abc123", vmNoName.displayUri)

        // With registered name, uses registered name
        val vmWithName = ContactViewModel(contact, profile, registeredName = "testuser")
        assertEquals("testuser", vmWithName.displayUri)
    }

    @Test
    fun testContactViewModelDisplayName() {
        val contact = Contact(Uri.fromId("abc123"))

        // Profile display name takes precedence
        val profile = Profile("Alice", null)
        val vm = ContactViewModel(contact, profile, registeredName = "alice_user")
        assertEquals("Alice", vm.displayName)

        // Falls back to display URI if no profile name
        val vmNoProfile = ContactViewModel(contact, Profile.EMPTY_PROFILE, registeredName = "bob_user")
        assertEquals("bob_user", vmNoProfile.displayName)

        // Falls back to contact URI if nothing else
        val vmNothing = ContactViewModel(contact, Profile.EMPTY_PROFILE)
        assertEquals("abc123", vmNothing.displayName)
    }

    @Test
    fun testContactViewModelMatches() {
        val contact = Contact(Uri.fromId("abc123"))
        val profile = Profile("Test User", null)
        val vm = ContactViewModel(contact, profile, registeredName = "testuser")

        assertTrue(vm.matches("test"))
        assertTrue(vm.matches("User"))
        assertTrue(vm.matches("testuser"))
        assertTrue(vm.matches("abc123"))
        assertFalse(vm.matches("unknown"))
    }

    @Test
    fun testContactViewModelPresence() {
        val contact = Contact(Uri.fromId("abc123"))
        val profile = Profile.EMPTY_PROFILE

        val vmOffline = ContactViewModel(contact, profile, presence = Contact.PresenceStatus.OFFLINE)
        assertEquals(Contact.PresenceStatus.OFFLINE, vmOffline.presence)

        val vmOnline = ContactViewModel(contact, profile, presence = Contact.PresenceStatus.AVAILABLE)
        assertEquals(Contact.PresenceStatus.AVAILABLE, vmOnline.presence)
    }

    @Test
    fun testContactViewModelEmpty() {
        val empty = ContactViewModel.EMPTY
        assertNotNull(empty)
        assertEquals(Profile.EMPTY_PROFILE, empty.profile)
        assertEquals(Contact.PresenceStatus.OFFLINE, empty.presence)
    }

    // ==================== Profile Integration Tests ====================

    @Test
    fun testProfileInContactViewModel() {
        val contact = Contact(Uri.fromId("abc123"))
        val avatar = byteArrayOf(1, 2, 3, 4)
        val profile = Profile("Display Name", avatar, "A description")

        val vm = ContactViewModel(contact, profile)
        assertEquals("Display Name", vm.profile.displayName)
        assertEquals(avatar, vm.profile.avatar)
        assertEquals("A description", vm.profile.description)
    }

    @Test
    fun testEmptyProfileInContactViewModel() {
        val contact = Contact(Uri.fromId("abc123"))
        val vm = ContactViewModel(contact, Profile.EMPTY_PROFILE)

        assertEquals(null, vm.profile.displayName)
        assertEquals(null, vm.profile.avatar)
    }
}
