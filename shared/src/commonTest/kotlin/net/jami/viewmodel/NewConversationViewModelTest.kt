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
import net.jami.model.Contact
import net.jami.services.StubDaemonBridge
import net.jami.services.StubDeviceRuntimeService
import net.jami.ui.viewmodel.ContactItem
import net.jami.ui.viewmodel.NewConversationViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NewConversationViewModelTest {

    private fun makeVm(
        stub: StubDaemonBridge,
        scope: kotlinx.coroutines.test.TestScope
    ): NewConversationViewModel {
        val accountService = makeAccountService(stub, scope)
        val contactService = makeContactService(stub, accountService, scope)
        val callService = makeCallService(stub, accountService, scope = scope)
        val facade = makeConversationFacade(stub, accountService, callService, contactService, scope)
        // The ViewModel's own collectors must not be children of the TestScope either, or
        // runTest waits on them and fails with UncompletedCoroutinesError after a minute.
        val vmScope = scope.viewModelScope()
        return NewConversationViewModel(contactService, facade, accountService, StubDeviceRuntimeService(), callService, vmScope)
    }

    private companion object {
        const val ACC = "acc1"
        const val ME = "me"
        const val BOB = "bbbb111122223333444455556666777788889999"
        const val CAROL = "cccc111122223333444455556666777788889999"
    }

    /**
     * An account with a 1:1 conversation with Bob and a three-member group. The group is what
     * made `loadAllConversations` throw (2026-10-29), and it is also what must not appear in the
     * picker: a group is not someone you can add to a group.
     */
    private suspend fun kotlinx.coroutines.test.TestScope.vmWithAContactAndAGroup(
        stub: StubDaemonBridge,
    ): NewConversationViewModel {
        stub.accountIds = listOf(ACC)
        stub.accountDetails[ACC] = mapOf(
            net.jami.model.ConfigKey.ACCOUNT_TYPE.key to "RING",
            net.jami.model.ConfigKey.ACCOUNT_USERNAME.key to ME,
        )
        stub.conversations[ACC] = listOf("conv-bob", "conv-group")
        stub.conversationInfo["conv-bob"] = mapOf("mode" to "0")
        stub.conversationMembers["conv-bob"] = listOf(
            mapOf("uri" to ME, "role" to "admin"),
            mapOf("uri" to BOB, "role" to "member"),
        )
        stub.conversationInfo["conv-group"] = mapOf("mode" to "1")
        stub.conversationMembers["conv-group"] = listOf(
            mapOf("uri" to ME, "role" to "admin"),
            mapOf("uri" to BOB, "role" to "member"),
            mapOf("uri" to CAROL, "role" to "member"),
        )
        val accountService = makeAccountService(stub, this)
        val contactService = makeContactService(stub, accountService, this)
        val callService = makeCallService(stub, accountService, scope = this)
        val facade = makeConversationFacade(stub, accountService, callService, contactService, this)
        accountService.loadAccounts()
        advanceUntilIdle()
        facade.onConversationReady(ACC, "conv-bob")
        facade.onConversationReady(ACC, "conv-group")
        advanceUntilIdle()
        return NewConversationViewModel(
            contactService, facade, accountService, StubDeviceRuntimeService(), callService,
            viewModelScope(),
        )
    }

    /**
     * The picker was blank until you guessed a name: the screen only ever rendered
     * publicDirectoryResults, and nothing filled a browsable list. The reference client's picker
     * has no search at all — it lists your contacts.
     */
    @Test
    fun withNothingTypedThePickerOffersThePeopleYouAlreadyTalkTo() = runTest {
        val stub = StubDaemonBridge()
        val vm = vmWithAContactAndAGroup(stub)
        vm.resetSearch()
        advanceUntilIdle()

        val known = vm.state.value.knownContacts
        assertTrue(known.any { it.uri.contains(BOB) }, "Bob is a contact you talk to: $known")
        assertFalse(known.any { it.uri.contains("conv-group") }, "a group is not someone to add")
        assertFalse(known.any { it.uri.contains(ME) }, "you are not your own contact")
    }

    /** A group conversation in the list must not take the screen down (regression, 2026-10-29). */
    @Test
    fun aGroupInTheListDoesNotBreakTheLoad() = runTest {
        val stub = StubDaemonBridge()
        val vm = vmWithAContactAndAGroup(stub)
        vm.resetSearch()
        advanceUntilIdle()

        assertTrue(vm.state.value.conversationResults.isNotEmpty())
    }

    /**
     * The bug behind "the second search erased my first pick": searching does not clear the
     * selection — it was only ever invisible, because nothing rendered it.
     */
    @Test
    fun searchingAgainKeepsWhatYouAlreadyPicked() = runTest {
        val stub = StubDaemonBridge()
        val vm = vmWithAContactAndAGroup(stub)
        vm.resetSearch()
        advanceUntilIdle()

        val bob = assertNotNull(vm.state.value.knownContacts.firstOrNull())
        vm.selectContact(bob)
        vm.search("someone else")
        advanceUntilIdle()

        assertEquals(listOf(bob), vm.state.value.selectedContacts)
    }

    /** And the chip's X has to actually remove it. */
    @Test
    fun removingAPickedContactDropsIt() = runTest {
        val stub = StubDaemonBridge()
        val vm = vmWithAContactAndAGroup(stub)
        vm.resetSearch()
        advanceUntilIdle()

        val bob = assertNotNull(vm.state.value.knownContacts.firstOrNull())
        vm.selectContact(bob)
        vm.removeContact(bob)

        assertTrue(vm.state.value.selectedContacts.isEmpty())
    }

    // ==================== Group picker (reference ContactPickerFragment) ====================

    /**
     * The reference hands whatever was picked straight to startConversation, with no 1:1
     * fallback and no title — so one picked contact still makes a swarm.
     */
    @Test
    fun pickingOnePersonStillMakesASwarm() = runTest {
        val stub = StubDaemonBridge()
        val vm = vmWithAContactAndAGroup(stub)
        vm.startGroupPicking()
        advanceUntilIdle()

        vm.selectContact(assertNotNull(vm.state.value.knownContacts.firstOrNull()))
        val id = vm.createGroup()
        advanceUntilIdle()

        assertNotNull(id, "a conversation should have been started")
        assertEquals(1, stub.addedConversationMembers.size, "the one pick was added as a member")
    }

    @Test
    fun theGroupPickerCreatesNothingWithNobodyPicked() = runTest {
        val stub = StubDaemonBridge()
        val vm = vmWithAContactAndAGroup(stub)
        vm.startGroupPicking()
        advanceUntilIdle()

        assertNull(vm.createGroup())
        assertTrue(stub.addedConversationMembers.isEmpty())
    }

    /** The picker is a sheet over the search screen and shares this ViewModel with it. */
    @Test
    fun openingThePickerDoesNotWipeTheSearchBehindIt() = runTest {
        val stub = StubDaemonBridge()
        val vm = vmWithAContactAndAGroup(stub)
        vm.search("bob")
        advanceUntilIdle()

        vm.startGroupPicking()
        advanceUntilIdle()

        assertEquals("bob", vm.state.value.searchQuery)
        assertTrue(vm.state.value.knownContacts.isNotEmpty(), "and it loaded who to pick from")
    }

    @Test
    fun initialStateIsEmpty() = runTest {
        val stub = StubDaemonBridge()
        val vm = makeVm(stub, this)
        assertEquals("", vm.state.value.searchQuery)
        assertTrue(vm.state.value.publicDirectoryResults.isEmpty())
        assertTrue(vm.state.value.selectedContacts.isEmpty())
        assertFalse(vm.state.value.isGroup)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun emptySearchClearsResults() = runTest {
        val stub = StubDaemonBridge()
        val vm = makeVm(stub, this)
        vm.search("alice")
        advanceUntilIdle()
        vm.search("")
        advanceUntilIdle()
        assertEquals("", vm.state.value.searchQuery)
        assertTrue(vm.state.value.publicDirectoryResults.isEmpty())
    }

    @Test
    fun searchUpdatesQuery() = runTest {
        val stub = StubDaemonBridge()
        val vm = makeVm(stub, this)
        vm.search("alice")
        assertEquals("alice", vm.state.value.searchQuery)
    }

    @Test
    fun selectContactAddsToSelection() = runTest {
        val stub = StubDaemonBridge()
        val vm = makeVm(stub, this)
        val contact = ContactItem(
            uri = "jami:abc123",
            displayName = "Alice",
            username = "alice",
            presenceStatus = Contact.PresenceStatus.OFFLINE,
            avatarUri = null
        )
        vm.selectContact(contact)
        assertEquals(1, vm.state.value.selectedContacts.size)
        assertEquals("jami:abc123", vm.state.value.selectedContacts.first().uri)
    }

    @Test
    fun selectSameContactTwiceOnlyAddsOnce() = runTest {
        val stub = StubDaemonBridge()
        val vm = makeVm(stub, this)
        val contact = ContactItem(
            uri = "jami:abc123",
            displayName = "Alice",
            username = "alice",
            presenceStatus = Contact.PresenceStatus.OFFLINE,
            avatarUri = null
        )
        vm.selectContact(contact)
        vm.selectContact(contact)
        assertEquals(1, vm.state.value.selectedContacts.size)
    }

    @Test
    fun removeContactRemovesFromSelection() = runTest {
        val stub = StubDaemonBridge()
        val vm = makeVm(stub, this)
        val contact = ContactItem(
            uri = "jami:abc123",
            displayName = "Alice",
            username = "alice",
            presenceStatus = Contact.PresenceStatus.OFFLINE,
            avatarUri = null
        )
        vm.selectContact(contact)
        assertEquals(1, vm.state.value.selectedContacts.size)
        vm.removeContact(contact)
        assertTrue(vm.state.value.selectedContacts.isEmpty())
    }

    @Test
    fun createConversationWithNoSelectionReturnsNull() = runTest {
        val stub = StubDaemonBridge()
        val vm = makeVm(stub, this)
        val result = vm.createConversation()
        assertNull(result)
    }

    @Test
    fun createConversationWithNoAccountReturnsNull() = runTest {
        val stub = StubDaemonBridge()
        val vm = makeVm(stub, this)
        val contact = ContactItem(
            uri = "jami:abc123",
            displayName = "Alice",
            username = "alice",
            presenceStatus = Contact.PresenceStatus.OFFLINE,
            avatarUri = null
        )
        vm.selectContact(contact)
        // No account loaded → returns null
        val result = vm.createConversation()
        assertNull(result)
    }

    @Test
    fun nameFoundEventAddsSearchResult() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val contactService = makeContactService(stub, accountService, this)
        val callService = makeCallService(stub, accountService, scope = this)
        val facade = makeConversationFacade(stub, accountService, callService, contactService, this)
        val vm = NewConversationViewModel(contactService, facade, accountService, StubDeviceRuntimeService(), callService, viewModelScope())
        vm.search("alice")
        // Simulate daemon returning a name lookup result
        accountService.onRegisteredNameFound(TEST_ACCOUNT_ID, 0, "abc123def", "alice")
        advanceUntilIdle()
        // Result should be added to search results
        assertTrue(vm.state.value.publicDirectoryResults.any { it.username == "alice" })
    }

    @Test
    fun onClearedDoesNotThrow() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val contactService = makeContactService(stub, accountService, this)
        val callService = makeCallService(stub, accountService, scope = this)
        val facade = makeConversationFacade(stub, accountService, callService, contactService, this)
        val vm = NewConversationViewModel(contactService, facade, accountService, StubDeviceRuntimeService(), callService, disposableScope())
        vm.onCleared()
    }
}
