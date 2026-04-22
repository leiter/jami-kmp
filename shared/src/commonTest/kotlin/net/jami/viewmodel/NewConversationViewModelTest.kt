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
        return NewConversationViewModel(contactService, facade, accountService, StubDeviceRuntimeService(), scope)
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
        val vm = NewConversationViewModel(contactService, facade, accountService, StubDeviceRuntimeService(), viewModelScope())
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
        val vm = NewConversationViewModel(contactService, facade, accountService, StubDeviceRuntimeService(), disposableScope())
        vm.onCleared()
    }
}
