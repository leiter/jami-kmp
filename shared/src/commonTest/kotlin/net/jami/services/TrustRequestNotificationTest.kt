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

    /**
     * A notification button pressed after iOS relaunched a killed app in the background: the
     * account has not loaded the request, so it is accepted by id. It must still reach the
     * daemon, as the bare id, and clear the "already notified" flag so a later request from
     * the same peer notifies again.
     */
    @Test
    fun aRequestTheAccountHasNotLoadedIsAcceptedById() = runTest {
        val stack = makeTestServiceStack(scope = this)
        prepareAccountInService(stack.stub, stack.accountService)
        stack.preferencesService.setRequestNotified(TEST_ACCOUNT_ID, CONVERSATION_ID)

        stack.conversationFacade.acceptRequest(TEST_ACCOUNT_ID, Uri.fromString("swarm:$CONVERSATION_ID"))
        advanceUntilIdle()

        assertEquals(listOf(TEST_ACCOUNT_ID to CONVERSATION_ID), stack.stub.acceptedConversationRequests)
        assertEquals(false, stack.preferencesService.hasRequestBeenNotified(TEST_ACCOUNT_ID, CONVERSATION_ID))
    }

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

    /**
     * The device case of 2026-11-17: a `req_` flag written days earlier, whose request had
     * left the pending set without ever being accepted or discarded through the app (the
     * contact was removed instead), silenced a genuine new invitation from that same peer —
     * the Pixel's daemon held the request while the app stayed silent. Every refresh now
     * prunes flags against the pending set, so the stale one cannot outlive its request.
     */
    @Test
    fun aStaleNotifiedFlagDoesNotSilenceANewRequest() = runTest {
        val stack = makeTestServiceStack(scope = this)
        prepareAccountInService(stack.stub, stack.accountService)
        // Written by an earlier request that vanished by a route other than accept/discard.
        stack.preferencesService.setRequestNotified(TEST_ACCOUNT_ID, PEER_ID)

        stack.conversationFacade.onConversationRequestReceived(
            TEST_ACCOUNT_ID, CONVERSATION_ID, mapOf("from" to PEER_ID)
        )
        advanceUntilIdle()

        assertEquals(listOf(TEST_ACCOUNT_ID), stack.notificationService.shownTrustRequests)
    }

    /** Pruning is per account: another account's flags are left alone. */
    @Test
    fun pruningLeavesOtherAccountsFlagsAlone() = runTest {
        val stack = makeTestServiceStack(scope = this)
        prepareAccountInService(stack.stub, stack.accountService)
        stack.preferencesService.setRequestNotified("other_account", PEER_ID)

        stack.conversationFacade.onConversationRequestReceived(
            TEST_ACCOUNT_ID, CONVERSATION_ID, mapOf("from" to PEER_ID)
        )
        advanceUntilIdle()

        assertTrue(stack.preferencesService.hasRequestBeenNotified("other_account", PEER_ID))
    }

    /**
     * A request that landed while the app was not running: `onConversationRequestReceived`
     * never fires for it, so before the smartlist load merged the daemon's own request list it
     * notified nobody — the Pixel 7a sat on B's invitation of 2026-11-17 13:40 in silence.
     */
    @Test
    fun aRequestHeldByTheDaemonAtStartupIsNotified() = runTest {
        val stack = makeTestServiceStack(scope = this)
        prepareAccountInService(stack.stub, stack.accountService)
        stack.stub.conversationRequests.add(
            mapOf("id" to CONVERSATION_ID, "from" to PEER_ID)
        )

        stack.conversationFacade.refreshConversations(TEST_ACCOUNT_ID)
        advanceUntilIdle()

        assertEquals(listOf(TEST_ACCOUNT_ID), stack.notificationService.shownTrustRequests)
        assertEquals(
            listOf(CONVERSATION_ID),
            stack.accountService.getAccount(TEST_ACCOUNT_ID)!!.getPending().map { it.uri.rawRingId }
        )
    }

    /** …and it is notified once, not again on every later reload. */
    @Test
    fun aDaemonHeldRequestIsNotifiedOnceAcrossReloads() = runTest {
        val stack = makeTestServiceStack(scope = this)
        prepareAccountInService(stack.stub, stack.accountService)
        stack.stub.conversationRequests.add(
            mapOf("id" to CONVERSATION_ID, "from" to PEER_ID)
        )

        repeat(3) {
            stack.conversationFacade.refreshConversations(TEST_ACCOUNT_ID)
            advanceUntilIdle()
        }

        assertEquals(1, stack.notificationService.shownTrustRequests.size)
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
