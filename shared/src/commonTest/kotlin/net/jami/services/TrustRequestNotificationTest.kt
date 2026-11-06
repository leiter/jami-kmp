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
import net.jami.model.Uri
import net.jami.viewmodel.TEST_ACCOUNT_ID
import net.jami.viewmodel.makeTestServiceStack
import net.jami.viewmodel.prepareAccountInService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `NotificationService.showIncomingTrustRequestNotification` was implemented on all five
 * platforms and called from none, so an incoming contact request appeared in the app's pending
 * list and nowhere else — no notification, on any platform. `ConversationFacade` now drives it
 * from every place the account's pending set moves, as the reference client does from the
 * account's pending subject.
 */
class TrustRequestNotificationTest {

    @Test
    fun anIncomingRequestIsNotified() = runTest {
        val stack = makeTestServiceStack(scope = this)
        prepareAccountInService(stack.stub, stack.accountService)

        stack.conversationFacade.onConversationRequestReceived(
            TEST_ACCOUNT_ID, CONVERSATION_ID, mapOf("from" to PEER_ID)
        )
        advanceUntilIdle()

        assertEquals(listOf(TEST_ACCOUNT_ID), stack.notificationService.shownTrustRequests)
    }

    /**
     * The pending set is republished on every change, so the same request must not be notified
     * again — that is what the `req_` preference key is for.
     */
    @Test
    fun thesameRequestIsNotNotifiedTwice() = runTest {
        val stack = makeTestServiceStack(scope = this)
        prepareAccountInService(stack.stub, stack.accountService)

        repeat(3) {
            stack.conversationFacade.onConversationRequestReceived(
                TEST_ACCOUNT_ID, CONVERSATION_ID, mapOf("from" to PEER_ID)
            )
        }
        advanceUntilIdle()

        assertEquals(1, stack.notificationService.shownTrustRequests.size)
    }

    @Test
    fun aSecondRequestFromSomeoneElseIsNotified() = runTest {
        val stack = makeTestServiceStack(scope = this)
        prepareAccountInService(stack.stub, stack.accountService)

        stack.conversationFacade.onConversationRequestReceived(
            TEST_ACCOUNT_ID, CONVERSATION_ID, mapOf("from" to PEER_ID)
        )
        stack.conversationFacade.onConversationRequestReceived(
            TEST_ACCOUNT_ID, "conv_002", mapOf("from" to OTHER_PEER_ID)
        )
        advanceUntilIdle()

        assertEquals(2, stack.notificationService.shownTrustRequests.size)
    }

    /** Answering the last request takes the notification down. */
    @Test
    fun decliningTheLastRequestCancelsTheNotification() = runTest {
        val stack = makeTestServiceStack(scope = this)
        prepareAccountInService(stack.stub, stack.accountService)

        stack.conversationFacade.onConversationRequestReceived(
            TEST_ACCOUNT_ID, CONVERSATION_ID, mapOf("from" to PEER_ID)
        )
        advanceUntilIdle()
        assertEquals(1, stack.notificationService.shownTrustRequests.size)

        stack.conversationFacade.onConversationRequestDeclined(TEST_ACCOUNT_ID, CONVERSATION_ID)
        advanceUntilIdle()

        assertEquals(listOf(TEST_ACCOUNT_ID), stack.notificationService.cancelledTrustRequests)
    }

    /**
     * Accepting clears the request's `req_` key, so a later request from the same peer — after
     * they were removed as a contact, say — notifies again rather than being silently deduped
     * against the first one.
     */
    @Test
    fun aLaterRequestFromThesamePeerNotifiesAgain() = runTest {
        val stack = makeTestServiceStack(scope = this)
        prepareAccountInService(stack.stub, stack.accountService)

        stack.conversationFacade.onConversationRequestReceived(
            TEST_ACCOUNT_ID, CONVERSATION_ID, mapOf("from" to PEER_ID)
        )
        advanceUntilIdle()
        stack.conversationFacade.discardRequest(TEST_ACCOUNT_ID, Uri.fromId(PEER_ID))
        stack.conversationFacade.onConversationRequestDeclined(TEST_ACCOUNT_ID, CONVERSATION_ID)
        advanceUntilIdle()

        stack.conversationFacade.onConversationRequestReceived(
            TEST_ACCOUNT_ID, CONVERSATION_ID, mapOf("from" to PEER_ID)
        )
        advanceUntilIdle()

        assertEquals(2, stack.notificationService.shownTrustRequests.size)
    }

    /** A request withheld by "block unknown contacts" must not notify either. */
    @Test
    fun aWithheldRequestIsNotNotified() = runTest {
        val stack = makeTestServiceStack(scope = this)
        prepareAccountInService(stack.stub, stack.accountService)
        stack.settingsRepository.updateBlockUnknownContacts(true)

        stack.conversationFacade.onConversationRequestReceived(
            TEST_ACCOUNT_ID, CONVERSATION_ID, mapOf("from" to PEER_ID)
        )
        advanceUntilIdle()

        assertTrue(stack.notificationService.shownTrustRequests.isEmpty())
        assertTrue(stack.notificationService.cancelledTrustRequests.isEmpty())
    }

    private companion object {
        const val CONVERSATION_ID = "conv_001"
        const val PEER_ID = "1234567890abcdef1234567890abcdef12345678"
        const val OTHER_PEER_ID = "fedcba9876543210fedcba9876543210fedcba98"
    }
}
