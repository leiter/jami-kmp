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
import net.jami.ui.viewmodel.ContactDetailsViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class ContactDetailsViewModelTest {

    @Test
    fun initialStateIsEmpty() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val contactService = makeContactService(stub, accountService, this)
        val callService = makeCallService(stub, accountService, this)
        val facade = makeConversationFacade(stub, accountService, callService, contactService, this)
        val vm = ContactDetailsViewModel(contactService, facade, viewModelScope())
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
        val callService = makeCallService(stub, accountService, this)
        val facade = makeConversationFacade(stub, accountService, callService, contactService, this)
        val vm = ContactDetailsViewModel(contactService, facade, viewModelScope())
        assertNotNull(vm.state)
        assertNotNull(vm.state.value)
    }

    @Test
    fun loadContactDoesNotCrash() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val contactService = makeContactService(stub, accountService, this)
        val callService = makeCallService(stub, accountService, this)
        val facade = makeConversationFacade(stub, accountService, callService, contactService, this)
        val vm = ContactDetailsViewModel(contactService, facade, viewModelScope())
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
        val callService = makeCallService(stub, accountService, this)
        val facade = makeConversationFacade(stub, accountService, callService, contactService, this)
        val vm = ContactDetailsViewModel(contactService, facade, viewModelScope())
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
        val callService = makeCallService(stub, accountService, this)
        val facade = makeConversationFacade(stub, accountService, callService, contactService, this)
        val vm = ContactDetailsViewModel(contactService, facade, viewModelScope())
        vm.removeContact()
        advanceUntilIdle()
        // No crash — no account set
    }

    @Test
    fun onClearedDoesNotThrow() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val contactService = makeContactService(stub, accountService, this)
        val callService = makeCallService(stub, accountService, this)
        val facade = makeConversationFacade(stub, accountService, callService, contactService, this)
        val vm = ContactDetailsViewModel(contactService, facade, disposableScope())
        vm.onCleared()
    }
}
