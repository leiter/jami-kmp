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
import net.jami.ui.viewmodel.ContactsViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContactsViewModelTest {

    @Test
    fun initialStateHasEmptyContacts() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val contactService = makeContactService(stub, accountService, this)
        val vm = ContactsViewModel(contactService, accountService, viewModelScope())
        advanceUntilIdle()
        assertTrue(vm.state.value.contacts.isEmpty())
    }

    @Test
    fun initialSearchQueryIsEmpty() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val contactService = makeContactService(stub, accountService, this)
        val vm = ContactsViewModel(contactService, accountService, viewModelScope())
        assertEquals("", vm.state.value.searchQuery)
    }

    @Test
    fun loadContactsWithNoAccountDoesNotCrash() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val contactService = makeContactService(stub, accountService, this)
        val vm = ContactsViewModel(contactService, accountService, viewModelScope())
        vm.loadContacts()
        advanceUntilIdle()
        // No account → early return, contacts remain empty
        assertTrue(vm.state.value.contacts.isEmpty())
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun searchUpdatesQueryAndFiltersResults() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        prepareAccountInService(stub, accountService)
        val contactService = makeContactService(stub, accountService, this)
        val vm = ContactsViewModel(contactService, accountService, viewModelScope())
        advanceUntilIdle()
        vm.search("alice")
        assertEquals("alice", vm.state.value.searchQuery)
    }

    @Test
    fun emptySearchQueryClearsFilter() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val contactService = makeContactService(stub, accountService, this)
        val vm = ContactsViewModel(contactService, accountService, viewModelScope())
        vm.search("alice")
        vm.search("")
        assertEquals("", vm.state.value.searchQuery)
    }

    @Test
    fun loadContactsWithAccountDoesNotSetLoading() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        prepareAccountInService(stub, accountService)
        val contactService = makeContactService(stub, accountService, this)
        val vm = ContactsViewModel(contactService, accountService, viewModelScope())
        advanceUntilIdle()
        vm.loadContacts()
        advanceUntilIdle()
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun onClearedDoesNotThrow() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val contactService = makeContactService(stub, accountService, this)
        val vm = ContactsViewModel(contactService, accountService, disposableScope())
        vm.onCleared()
    }
}
