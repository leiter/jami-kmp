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
import net.jami.model.Conversation
import net.jami.model.Uri
import net.jami.viewmodel.TEST_ACCOUNT_ID
import net.jami.viewmodel.makeTestServiceStack
import net.jami.viewmodel.prepareAccountInService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Leaving a conversation has to reach the other participants.
 *
 * The daemon propagates a leave by committing `{type: member, action: remove, uri: <us>}` into
 * the swarm and notifying its members — `Conversation::leave()`, reached from
 * `ConversationModule::removeConversation`. The peer turns that commit into a
 * `ContactEvent(REMOVED)` and renders "<name> left the conversation".
 *
 * A 1:1 used to take a different route: the facade removed the *contact* instead (as
 * libjamiclient does), which lands in `ConversationModule::removeContact` →
 * `removeRepository(force = true)`. That erases the git repo on the spot without ever calling
 * `leave()`, so no commit is written and the other participant is told nothing at all — their
 * conversation just stops working. Reported 2026-11-17.
 */
class LeaveConversationTest {

    @Test
    fun leavingAOneToOneCommitsTheLeaveInsteadOfRemovingTheContact() = runTest {
        val stack = makeTestServiceStack(scope = this)
        val account = prepareAccountInService(stack.stub, stack.accountService)
        val conversation = account.newSwarm(CONVERSATION_ID, Conversation.Mode.OneToOne)
        conversation.addContact(account.getContactFromCache(Uri.fromId(PEER_ID)))
        account.conversationStarted(conversation)

        stack.conversationFacade.removeConversation(
            TEST_ACCOUNT_ID, Uri(Uri.SWARM_SCHEME, CONVERSATION_ID)
        )
        advanceUntilIdle()

        assertEquals(
            listOf(TEST_ACCOUNT_ID to CONVERSATION_ID),
            stack.stub.removedConversations
        )
        // The contact stays: leaving is not blocking, and contact details has its own remove.
        assertTrue(stack.stub.removedContacts.isEmpty())
    }

    @Test
    fun leavingAGroupStillCommitsTheLeave() = runTest {
        val stack = makeTestServiceStack(scope = this)
        val account = prepareAccountInService(stack.stub, stack.accountService)
        val conversation = account.newSwarm(CONVERSATION_ID, Conversation.Mode.InvitesOnly)
        account.conversationStarted(conversation)

        stack.conversationFacade.removeConversation(
            TEST_ACCOUNT_ID, Uri(Uri.SWARM_SCHEME, CONVERSATION_ID)
        )
        advanceUntilIdle()

        assertEquals(
            listOf(TEST_ACCOUNT_ID to CONVERSATION_ID),
            stack.stub.removedConversations
        )
        assertTrue(stack.stub.removedContacts.isEmpty())
    }

    private companion object {
        const val CONVERSATION_ID = "aa11bb22cc33dd44ee55ff6677889900aabbccdd"
        const val PEER_ID = "1234567890abcdef1234567890abcdef12345678"
    }
}
