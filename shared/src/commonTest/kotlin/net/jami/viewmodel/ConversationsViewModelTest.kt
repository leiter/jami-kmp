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
import net.jami.ui.viewmodel.ConversationsViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConversationsViewModelTest {

    @Test
    fun initialStateHasEmptyConversations() = runTest {
        val stub = StubDaemonBridge()
        val services = makeTestServiceStack(stub, this)
        val vm = ConversationsViewModel(services.accountService, services.conversationFacade, StubDeviceRuntimeService(), viewModelScope())
        advanceUntilIdle()
        assertTrue(vm.state.value.conversations.isEmpty())
    }

    @Test
    fun initialSearchQueryIsEmpty() = runTest {
        val stub = StubDaemonBridge()
        val services = makeTestServiceStack(stub, this)
        val vm = ConversationsViewModel(services.accountService, services.conversationFacade, StubDeviceRuntimeService(), viewModelScope())
        assertEquals("", vm.state.value.searchQuery)
    }

    @Test
    fun loadConversationsWithNoAccountReturnsEmpty() = runTest {
        val stub = StubDaemonBridge()
        val services = makeTestServiceStack(stub, this)
        val vm = ConversationsViewModel(services.accountService, services.conversationFacade, StubDeviceRuntimeService(), viewModelScope())
        vm.loadConversations()
        advanceUntilIdle()
        assertTrue(vm.state.value.conversations.isEmpty())
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun searchUpdatesQuery() = runTest {
        val stub = StubDaemonBridge()
        val services = makeTestServiceStack(stub, this)
        val vm = ConversationsViewModel(services.accountService, services.conversationFacade, StubDeviceRuntimeService(), viewModelScope())
        vm.search("alice")
        assertEquals("alice", vm.state.value.searchQuery)
    }

    @Test
    fun searchWithAccountDoesNotCrash() = runTest {
        val stub = StubDaemonBridge()
        val services = makeTestServiceStack(stub, this)
        prepareAccountInService(stub, services.accountService)
        val vm = ConversationsViewModel(services.accountService, services.conversationFacade, StubDeviceRuntimeService(), viewModelScope())
        advanceUntilIdle()
        vm.search("alice")
        advanceUntilIdle()
        assertEquals("alice", vm.state.value.searchQuery)
    }

    @Test
    fun refreshDoesNotCrash() = runTest {
        val stub = StubDaemonBridge()
        val services = makeTestServiceStack(stub, this)
        val vm = ConversationsViewModel(services.accountService, services.conversationFacade, StubDeviceRuntimeService(), viewModelScope())
        vm.refresh()
        advanceUntilIdle()
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun removeConversationWithNoAccountDoesNotCrash() = runTest {
        val stub = StubDaemonBridge()
        val services = makeTestServiceStack(stub, this)
        val vm = ConversationsViewModel(services.accountService, services.conversationFacade, StubDeviceRuntimeService(), viewModelScope())
        vm.removeConversation("conv123")
        advanceUntilIdle()
        // No crash
    }

    @Test
    fun loadConversationsWithAccountSucceeds() = runTest {
        val stub = StubDaemonBridge()
        val services = makeTestServiceStack(stub, this)
        prepareAccountInService(stub, services.accountService)
        val vm = ConversationsViewModel(services.accountService, services.conversationFacade, StubDeviceRuntimeService(), viewModelScope())
        advanceUntilIdle()
        vm.loadConversations()
        advanceUntilIdle()
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun onClearedDoesNotThrow() = runTest {
        val stub = StubDaemonBridge()
        val services = makeTestServiceStack(stub, this)
        val vm = ConversationsViewModel(services.accountService, services.conversationFacade, StubDeviceRuntimeService(), disposableScope())
        vm.onCleared()
    }
}
