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
import net.jami.ui.viewmodel.ChatViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatViewModelTest {

    private fun makeVm(
        stub: StubDaemonBridge,
        scope: kotlinx.coroutines.test.TestScope
    ): ChatViewModel {
        val accountService = makeAccountService(stub, scope)
        val contactService = makeContactService(stub, accountService, scope)
        val callService = makeCallService(stub, accountService, scope)
        val facade = makeConversationFacade(stub, accountService, callService, contactService, scope)
        return ChatViewModel(facade, accountService, scope)
    }

    @Test
    fun initialStateIsEmpty() = runTest {
        val stub = StubDaemonBridge()
        val vm = makeVm(stub, this)
        assertTrue(vm.state.value.messages.isEmpty())
        assertEquals("", vm.state.value.inputText)
        assertEquals("", vm.state.value.conversationTitle)
        assertFalse(vm.state.value.isLoading)
        assertFalse(vm.state.value.isSearchActive)
    }

    @Test
    fun updateInputSetsInputText() = runTest {
        val stub = StubDaemonBridge()
        val vm = makeVm(stub, this)
        vm.updateInput("Hello World")
        assertEquals("Hello World", vm.state.value.inputText)
    }

    @Test
    fun updateInputWithEmptyStringClearsText() = runTest {
        val stub = StubDaemonBridge()
        val vm = makeVm(stub, this)
        vm.updateInput("Hello")
        vm.updateInput("")
        assertEquals("", vm.state.value.inputText)
    }

    @Test
    fun sendMessageWithEmptyInputDoesNothing() = runTest {
        val stub = StubDaemonBridge()
        val vm = makeVm(stub, this)
        vm.sendMessage()
        advanceUntilIdle()
        assertEquals("", vm.state.value.inputText)
        assertTrue(vm.state.value.messages.isEmpty())
    }

    @Test
    fun sendMessageWithTextClearsInput() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        prepareAccountInService(stub, accountService)
        val contactService = makeContactService(stub, accountService, this)
        val callService = makeCallService(stub, accountService, this)
        val facade = makeConversationFacade(stub, accountService, callService, contactService, this)
        val vm = ChatViewModel(facade, accountService, viewModelScope())
        vm.updateInput("Hello")
        // Load a conversation first so currentAccountId/conversationId are set
        vm.loadConversation("conv_001")
        advanceUntilIdle()
        vm.sendMessage()
        advanceUntilIdle()
        // Input cleared after send
        assertEquals("", vm.state.value.inputText)
    }

    @Test
    fun searchConversationWithEmptyQueryClearsResults() = runTest {
        val stub = StubDaemonBridge()
        val vm = makeVm(stub, this)
        vm.searchConversation("alice")
        vm.searchConversation("")
        advanceUntilIdle()
        assertEquals("", vm.state.value.searchQuery)
        assertTrue(vm.state.value.searchResults.isEmpty())
        assertFalse(vm.state.value.isSearchActive)
    }

    @Test
    fun searchConversationWithQuerySetsSearchActive() = runTest {
        val stub = StubDaemonBridge()
        val vm = makeVm(stub, this)
        vm.searchConversation("hello")
        assertEquals("hello", vm.state.value.searchQuery)
        assertTrue(vm.state.value.isSearchActive)
    }

    @Test
    fun closeSearchResetsSearchState() = runTest {
        val stub = StubDaemonBridge()
        val vm = makeVm(stub, this)
        vm.searchConversation("hello")
        vm.closeSearch()
        assertEquals("", vm.state.value.searchQuery)
        assertTrue(vm.state.value.searchResults.isEmpty())
        assertFalse(vm.state.value.isSearchActive)
    }

    @Test
    fun loadConversationWithNoAccountDoesNotCrash() = runTest {
        val stub = StubDaemonBridge()
        val vm = makeVm(stub, this)
        vm.loadConversation("conv_001")
        advanceUntilIdle()
        // No account → early return after setting isLoading
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun deleteMessageWithNoConversationDoesNotCrash() = runTest {
        val stub = StubDaemonBridge()
        val vm = makeVm(stub, this)
        vm.deleteMessage("msg_001")
        advanceUntilIdle()
        // No conversationId → early return, no crash
    }

    @Test
    fun editMessageWithNoConversationDoesNotCrash() = runTest {
        val stub = StubDaemonBridge()
        val vm = makeVm(stub, this)
        vm.editMessage("msg_001", "updated text")
        advanceUntilIdle()
        // No conversationId → early return, no crash
    }

    @Test
    fun loadMoreWithNoConversationDoesNotCrash() = runTest {
        val stub = StubDaemonBridge()
        val vm = makeVm(stub, this)
        vm.loadMore()
        advanceUntilIdle()
        // No conversationId → early return, no crash
    }

    @Test
    fun onClearedDoesNotThrow() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val contactService = makeContactService(stub, accountService, this)
        val callService = makeCallService(stub, accountService, this)
        val facade = makeConversationFacade(stub, accountService, callService, contactService, this)
        val vm = ChatViewModel(facade, accountService, disposableScope())
        vm.onCleared()
    }
}
