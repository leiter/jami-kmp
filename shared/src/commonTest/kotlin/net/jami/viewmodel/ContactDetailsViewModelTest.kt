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
package net.jami.viewmodel

import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.jami.services.StubDaemonBridge
import net.jami.services.StubDeviceRuntimeService
import net.jami.ui.viewmodel.ContactDetailsViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ContactDetailsViewModelTest {

    @Test
    fun initialStateIsEmpty() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val contactService = makeContactService(stub, accountService, this)
        val callService = makeCallService(stub, accountService, scope = this)
        val facade = makeConversationFacade(stub, accountService, callService, contactService, this)
        val vm = ContactDetailsViewModel(accountService, contactService, facade, StubDeviceRuntimeService(), viewModelScope())
        assertEquals("", vm.state.value.displayName)
        assertEquals("", vm.state.value.username)
        assertFalse(vm.state.value.isBlocked)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun stateFlowIsNotNull() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val contactService = makeContactService(stub, accountService, this)
        val callService = makeCallService(stub, accountService, scope = this)
        val facade = makeConversationFacade(stub, accountService, callService, contactService, this)
        val vm = ContactDetailsViewModel(accountService, contactService, facade, StubDeviceRuntimeService(), viewModelScope())
        assertNotNull(vm.state)
        assertNotNull(vm.state.value)
    }

    @Test
    fun loadContactDoesNotCrash() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val contactService = makeContactService(stub, accountService, this)
        val callService = makeCallService(stub, accountService, scope = this)
        val facade = makeConversationFacade(stub, accountService, callService, contactService, this)
        val vm = ContactDetailsViewModel(accountService, contactService, facade, StubDeviceRuntimeService(), viewModelScope())
        vm.loadContact("jami:abc123def456abc123def456abc123def456abc123def")
        advanceUntilIdle()
        // No crash — state may be partially populated
        assertNotNull(vm.state.value)
    }

    @Test
    fun blockContactWithNoAccountDoesNotChangeState() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val contactService = makeContactService(stub, accountService, this)
        val callService = makeCallService(stub, accountService, scope = this)
        val facade = makeConversationFacade(stub, accountService, callService, contactService, this)
        val vm = ContactDetailsViewModel(accountService, contactService, facade, StubDeviceRuntimeService(), viewModelScope())
        val before = vm.state.value.isBlocked
        vm.blockContact()
        advanceUntilIdle()
        // currentAccountId is null → early return → state unchanged
        assertEquals(before, vm.state.value.isBlocked)
    }

    @Test
    fun removeContactWithNoAccountDoesNotCrash() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val contactService = makeContactService(stub, accountService, this)
        val callService = makeCallService(stub, accountService, scope = this)
        val facade = makeConversationFacade(stub, accountService, callService, contactService, this)
        val vm = ContactDetailsViewModel(accountService, contactService, facade, StubDeviceRuntimeService(), viewModelScope())
        vm.removeContact()
        advanceUntilIdle()
        // No crash — no account set
    }

    /**
     * Opened from a conversation, this screen is handed the swarm id, not the peer. Acting on
     * that id asks the daemon to remove a contact that does not exist — it does nothing and
     * reports nothing, which is why "Delete contact" was silently dead on a device (2026-11-07).
     */
    @Test
    fun removeContactLoadedBySwarmIdActsOnThePeer() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val contactService = makeContactService(stub, accountService, this)
        val callService = makeCallService(stub, accountService, scope = this)
        val facade = makeConversationFacade(stub, accountService, callService, contactService, this)
        val account = prepareAccountInService(stub, accountService)
        val conv = account.newSwarm(SWARM_ID, net.jami.model.Conversation.Mode.OneToOne)
        conv.addContact(net.jami.model.Contact(net.jami.model.Uri.fromString(PEER_ID)))
        account.conversationStarted(conv)

        val vm = ContactDetailsViewModel(accountService, contactService, facade, StubDeviceRuntimeService(), viewModelScope())
        vm.loadContact("swarm:$SWARM_ID")
        advanceUntilIdle()
        vm.removeContact()
        advanceUntilIdle()

        val (_, uri, _) = stub.removedContacts.single()
        assertEquals(net.jami.model.Uri.fromString(PEER_ID).uri, uri)
    }

    /**
     * The screen stayed open on a conversation it had just deleted (seen on a device
     * 2026-11-07): the delete looked like it had done nothing. The reference pops back after
     * delete, block, unblock and leave alike.
     */
    @Test
    fun removingTheContactMarksTheScreenGone() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val contactService = makeContactService(stub, accountService, this)
        val callService = makeCallService(stub, accountService, scope = this)
        val facade = makeConversationFacade(stub, accountService, callService, contactService, this)
        val account = prepareAccountInService(stub, accountService)
        val conv = account.newSwarm(SWARM_ID, net.jami.model.Conversation.Mode.OneToOne)
        conv.addContact(net.jami.model.Contact(net.jami.model.Uri.fromString(PEER_ID)))
        account.conversationStarted(conv)

        val vm = ContactDetailsViewModel(accountService, contactService, facade, StubDeviceRuntimeService(), viewModelScope())
        vm.loadContact("swarm:$SWARM_ID")
        advanceUntilIdle()
        assertFalse(vm.state.value.isGone)

        vm.removeContact()
        advanceUntilIdle()

        assertTrue(vm.state.value.isGone)
    }

    @Test
    fun blockingTheContactMarksTheScreenGone() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val contactService = makeContactService(stub, accountService, this)
        val callService = makeCallService(stub, accountService, scope = this)
        val facade = makeConversationFacade(stub, accountService, callService, contactService, this)
        val account = prepareAccountInService(stub, accountService)
        val conv = account.newSwarm(SWARM_ID, net.jami.model.Conversation.Mode.OneToOne)
        conv.addContact(net.jami.model.Contact(net.jami.model.Uri.fromString(PEER_ID)))
        account.conversationStarted(conv)

        val vm = ContactDetailsViewModel(accountService, contactService, facade, StubDeviceRuntimeService(), viewModelScope())
        vm.loadContact("swarm:$SWARM_ID")
        advanceUntilIdle()

        vm.blockContact()
        advanceUntilIdle()

        assertTrue(vm.state.value.isGone)
        val (_, uri, banned) = stub.removedContacts.single()
        assertEquals(net.jami.model.Uri.fromString(PEER_ID).uri, uri)
        assertTrue(banned)
    }

    /** Removed on another device: the daemon signal reaches this screen as a ContactEvent. */
    @Test
    fun aRemoteRemovalMarksTheScreenGone() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val contactService = makeContactService(stub, accountService, this)
        val callService = makeCallService(stub, accountService, scope = this)
        val facade = makeConversationFacade(stub, accountService, callService, contactService, this)
        val account = prepareAccountInService(stub, accountService)
        val conv = account.newSwarm(SWARM_ID, net.jami.model.Conversation.Mode.OneToOne)
        conv.addContact(net.jami.model.Contact(net.jami.model.Uri.fromString(PEER_ID)))
        account.conversationStarted(conv)

        val vm = ContactDetailsViewModel(accountService, contactService, facade, StubDeviceRuntimeService(), viewModelScope())
        vm.loadContact("swarm:$SWARM_ID")
        advanceUntilIdle()
        assertFalse(vm.state.value.isGone)

        // The daemon's own signals are what drive this path; the contact has to be known to
        // the cache for ContactService to re-emit a removal at all.
        contactService.onContactAdded(account.accountId, PEER_ID, confirmed = true)
        advanceUntilIdle()
        contactService.onContactRemoved(account.accountId, PEER_ID, banned = false)
        advanceUntilIdle()

        assertTrue(vm.state.value.isGone)
    }

    /**
     * A peer whose vCard carries an empty `FN` gave the screen a non-null, blank display name,
     * which won every fallback and left the header printing "Unknown" over a "?" avatar for
     * someone the chat screen rendered with a name (seen on a device 2026-11-07). A blank name
     * is no name.
     */
    @Test
    fun aBlankProfileNameFallsBackToTheContactsOwnName() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val contactService = makeContactService(stub, accountService, this)
        val callService = makeCallService(stub, accountService, scope = this)
        val facade = makeConversationFacade(stub, accountService, callService, contactService, this)
        val account = prepareAccountInService(stub, accountService)
        val conv = account.newSwarm(SWARM_ID, net.jami.model.Conversation.Mode.OneToOne)
        conv.addContact(net.jami.model.Contact(net.jami.model.Uri.fromString(PEER_ID)))
        account.conversationStarted(conv)
        // What a nameless vCard leaves in the profile cache.
        contactService.setCustomProfile(
            account.accountId,
            net.jami.model.Uri.fromString(PEER_ID),
            net.jami.model.Profile("", null),
        )

        val vm = ContactDetailsViewModel(accountService, contactService, facade, StubDeviceRuntimeService(), viewModelScope())
        vm.loadContact("swarm:$SWARM_ID")
        advanceUntilIdle()

        assertEquals(net.jami.model.Uri.fromString(PEER_ID).rawRingId, vm.state.value.displayName)
    }

    /**
     * A presence ping carries no profile. Refreshing from the contact cache alone used to
     * overwrite the resolved name, so a named peer became its 40-hex id as soon as it came
     * online.
     */
    @Test
    fun aPresenceUpdateDoesNotDemoteTheResolvedName() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val contactService = makeContactService(stub, accountService, this)
        val callService = makeCallService(stub, accountService, scope = this)
        val facade = makeConversationFacade(stub, accountService, callService, contactService, this)
        val account = prepareAccountInService(stub, accountService)
        val conv = account.newSwarm(SWARM_ID, net.jami.model.Conversation.Mode.OneToOne)
        conv.addContact(net.jami.model.Contact(net.jami.model.Uri.fromString(PEER_ID)))
        account.conversationStarted(conv)
        contactService.onContactAdded(account.accountId, PEER_ID, confirmed = true)
        contactService.setCustomProfile(
            account.accountId,
            net.jami.model.Uri.fromString(PEER_ID),
            net.jami.model.Profile("Alice", null),
        )

        val vm = ContactDetailsViewModel(accountService, contactService, facade, StubDeviceRuntimeService(), viewModelScope())
        vm.loadContact("swarm:$SWARM_ID")
        advanceUntilIdle()
        assertEquals("Alice", vm.state.value.displayName)

        contactService.onPresenceUpdate(account.accountId, PEER_ID, status = 1)
        advanceUntilIdle()

        assertEquals("Alice", vm.state.value.displayName)
    }

    /**
     * A vCard that arrives while the screen is open must show up without reopening it. The
     * peer is not necessarily in ContactService's cache — it is filled lazily, and this screen
     * resolves its peer from the conversation's members — so the refresh must work from the
     * profile alone (found on a device 2026-11-07: the name appeared only after a reopen).
     */
    @Test
    fun aProfileArrivingWhileOpenUpdatesTheHeaderWithNoCachedContact() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val contactService = makeContactService(stub, accountService, this)
        val callService = makeCallService(stub, accountService, scope = this)
        val facade = makeConversationFacade(stub, accountService, callService, contactService, this)
        val account = prepareAccountInService(stub, accountService)
        val conv = account.newSwarm(SWARM_ID, net.jami.model.Conversation.Mode.OneToOne)
        conv.addContact(net.jami.model.Contact(net.jami.model.Uri.fromString(PEER_ID)))
        account.conversationStarted(conv)

        val vm = ContactDetailsViewModel(accountService, contactService, facade, StubDeviceRuntimeService(), viewModelScope())
        vm.loadContact("swarm:$SWARM_ID")
        advanceUntilIdle()
        // Nothing put this peer in ContactService's cache, which is exactly the device case.
        assertEquals(null, contactService.findContactInCache(account.accountId, net.jami.model.Uri.fromString(PEER_ID)))

        contactService.setCustomProfile(
            account.accountId,
            net.jami.model.Uri.fromString(PEER_ID),
            net.jami.model.Profile("Testname", null),
        )
        advanceUntilIdle()

        assertEquals("Testname", vm.state.value.displayName)
    }

    @Test
    fun onClearedDoesNotThrow() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val contactService = makeContactService(stub, accountService, this)
        val callService = makeCallService(stub, accountService, scope = this)
        val facade = makeConversationFacade(stub, accountService, callService, contactService, this)
        val vm = ContactDetailsViewModel(accountService, contactService, facade, StubDeviceRuntimeService(), disposableScope())
        vm.onCleared()
    }

    private companion object {
        const val SWARM_ID = "716e83d1500cb93a8a6b816ebc72d177f009e98e"
        const val PEER_ID = "66d57f55227639b58fdcc6d74ec090446ffa9bc3"
    }
}
