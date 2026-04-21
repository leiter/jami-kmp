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

    @Test
    fun conversationsAreSortedByTimestampDescending() = runTest {
        val stub = StubDaemonBridge()
        val services = makeTestServiceStack(stub, this)
        val account = prepareAccountInService(stub, services.accountService)

        // Create conversations with different timestamps
        val conv1 = account.newSwarm("conv1", net.jami.model.Conversation.Mode.OneToOne)
        val conv2 = account.newSwarm("conv2", net.jami.model.Conversation.Mode.OneToOne)
        val conv3 = account.newSwarm("conv3", net.jami.model.Conversation.Mode.OneToOne)

        // Add text messages with different timestamps
        val msg1 = net.jami.model.TextMessage(null, account.accountId, null, conv1, "oldest")
        msg1.timestamp = 1000L
        conv1.addElement(msg1)

        val msg2 = net.jami.model.TextMessage(null, account.accountId, null, conv2, "newest")
        msg2.timestamp = 3000L
        conv2.addElement(msg2)

        val msg3 = net.jami.model.TextMessage(null, account.accountId, null, conv3, "middle")
        msg3.timestamp = 2000L
        conv3.addElement(msg3)

        account.conversationStarted(conv1)
        account.conversationStarted(conv2)
        account.conversationStarted(conv3)

        val vm = ConversationsViewModel(services.accountService, services.conversationFacade, StubDeviceRuntimeService(), viewModelScope())
        advanceUntilIdle()

        // Verify conversations are sorted by timestamp descending (most recent first)
        val conversations = vm.state.value.conversations
        assertTrue(conversations.size >= 3, "Expected at least 3 conversations")

        // Find our test conversations
        val items = conversations.filter { it.id in listOf("conv1", "conv2", "conv3") }
        assertEquals(3, items.size, "Expected 3 test conversations")

        // Verify order: conv2 (3000) > conv3 (2000) > conv1 (1000)
        assertEquals("conv2", items[0].id, "Expected conv2 (newest) first")
        assertEquals("conv3", items[1].id, "Expected conv3 (middle) second")
        assertEquals("conv1", items[2].id, "Expected conv1 (oldest) third")
    }
}
