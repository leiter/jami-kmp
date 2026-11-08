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

import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.jami.viewmodel.TEST_ACCOUNT_ID
import net.jami.viewmodel.groupConversation
import net.jami.viewmodel.makeTestServiceStack
import net.jami.viewmodel.oneToOneConversation
import net.jami.viewmodel.prepareAccountInService
import net.jami.viewmodel.viewModelScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Records what a launcher would have been told. */
private class RecordingShortcuts : ConversationShortcutService {
    val published = mutableListOf<List<ConversationShortcut>>()
    var cleared = 0
    override fun publish(shortcuts: List<ConversationShortcut>) { published.add(shortcuts) }
    override fun clear() { cleared++ }
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ConversationShortcutPublisherTest {

    @Test
    fun theSmartlistBecomesShortcutsInOrder() = runTest {
        val scope = viewModelScope()
        val stack = makeTestServiceStack(scope = scope)
        val account = prepareAccountInService(stack.stub, stack.accountService)
        val service = RecordingShortcuts()
        ConversationShortcutPublisher(stack.conversationFacade, service, scope).start()
        advanceUntilIdle()

        // The smartlist prunes conversations the daemon does not list.
        stack.stub.conversations[account.accountId] = listOf("conv-a", "conv-b")
        account.conversationStarted(oneToOneConversation(conversationId = "conv-a"))
        account.conversationStarted(groupConversation(conversationId = "conv-b"))
        stack.conversationFacade.refreshConversations(account.accountId)
        advanceUntilIdle()

        val last = service.published.lastOrNull().orEmpty()
        assertTrue(last.isNotEmpty(), "nothing was published")
        assertTrue(last.all { it.accountId == TEST_ACCOUNT_ID })
    }

    /** Four: what a long-press menu shows before the launcher starts dropping them. */
    @Test
    fun atMostFourAreOffered() = runTest {
        val scope = viewModelScope()
        val stack = makeTestServiceStack(scope = scope)
        val account = prepareAccountInService(stack.stub, stack.accountService)
        val service = RecordingShortcuts()
        ConversationShortcutPublisher(stack.conversationFacade, service, scope).start()
        advanceUntilIdle()

        stack.stub.conversations[account.accountId] = (0 until 6).map { "conv$it" }
        repeat(6) { i -> account.conversationStarted(oneToOneConversation(conversationId = "conv$i")) }
        stack.conversationFacade.refreshConversations(account.accountId)
        advanceUntilIdle()

        assertTrue(service.published.all { it.size <= 4 }, "published ${service.published.map { it.size }}")
    }

    /**
     * The smartlist re-emits on every message. A launcher asked to rewrite four unchanged
     * shortcuts for each one of them is a rate limit waiting to be hit.
     */
    @Test
    fun anUnchangedListIsNotRepublished() = runTest {
        val scope = viewModelScope()
        val stack = makeTestServiceStack(scope = scope)
        val account = prepareAccountInService(stack.stub, stack.accountService)
        val service = RecordingShortcuts()
        ConversationShortcutPublisher(stack.conversationFacade, service, scope).start()
        advanceUntilIdle()

        stack.stub.conversations[account.accountId] = listOf("conv-a")
        account.conversationStarted(oneToOneConversation(conversationId = "conv-a"))
        stack.conversationFacade.refreshConversations(account.accountId)
        advanceUntilIdle()
        val afterFirst = service.published.size

        stack.conversationFacade.refreshConversations(account.accountId)
        advanceUntilIdle()

        assertEquals(afterFirst, service.published.size)
    }
}
