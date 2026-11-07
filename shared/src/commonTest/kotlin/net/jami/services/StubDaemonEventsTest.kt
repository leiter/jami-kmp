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

import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.jami.model.Account
import net.jami.viewmodel.TEST_ACCOUNT_ID
import net.jami.viewmodel.connectDaemonCallbacks
import net.jami.viewmodel.makeTestServiceStack
import net.jami.viewmodel.prepareAccountInService
import net.jami.viewmodel.viewModelScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * `StubDaemonBridge.init()` used to discard the callbacks it was handed, so every daemon event
 * — registration, incoming calls, swarm messages, the whole `DaemonCallbacksImpl` routing
 * layer — could only be produced by a real daemon with a real peer, and was therefore only
 * ever exercised by hand on two phones.
 *
 * These tests do not test the stub. They test that the *production* callback path runs under
 * `runTest`, which is what retaining the callbacks bought, and they are the pattern for the
 * callback coverage that can now be written.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class StubDaemonEventsTest {

    @Test
    fun aRegistrationStateEventReachesTheAccountModel() = runTest {
        val scope = viewModelScope()
        val stack = makeTestServiceStack(scope = scope)
        val account = prepareAccountInService(stack.stub, stack.accountService)
        connectDaemonCallbacks(stack, scope)
        advanceUntilIdle()

        stack.stub.fireDaemonEvent {
            onRegistrationStateChanged(TEST_ACCOUNT_ID, "REGISTERED", 0, "")
        }
        advanceUntilIdle()

        assertEquals(Account.RegistrationState.REGISTERED, account.registrationState)
    }

    /**
     * The account callbacks are queued on a channel and drained by one consumer coroutine, so
     * this also pins the ordering: the second event must not overtake the first.
     */
    @Test
    fun queuedAccountEventsArriveInOrder() = runTest {
        val scope = viewModelScope()
        val stack = makeTestServiceStack(scope = scope)
        val account = prepareAccountInService(stack.stub, stack.accountService)
        connectDaemonCallbacks(stack, scope)
        advanceUntilIdle()

        stack.stub.fireDaemonEvent {
            onRegistrationStateChanged(TEST_ACCOUNT_ID, "REGISTERED", 0, "")
            onRegistrationStateChanged(TEST_ACCOUNT_ID, "UNREGISTERED", 0, "")
        }
        advanceUntilIdle()

        assertEquals(Account.RegistrationState.UNREGISTERED, account.registrationState)
    }

    /**
     * A conversation callback, routed through the per-conversation channels rather than the
     * account one — a different path through `DaemonCallbacksImpl`, and the one that carries
     * swarm traffic.
     */
    @Test
    fun aConversationReadyEventReachesTheFacade() = runTest {
        val scope = viewModelScope()
        val stack = makeTestServiceStack(scope = scope)
        prepareAccountInService(stack.stub, stack.accountService)
        connectDaemonCallbacks(stack, scope)
        advanceUntilIdle()

        val seen = mutableListOf<ConversationEvent.ConversationReady>()
        val collector = scope.launch {
            stack.conversationFacade.conversationEvents
                .filterIsInstance<ConversationEvent.ConversationReady>()
                .collect { seen.add(it) }
        }
        advanceUntilIdle()

        stack.stub.fireDaemonEvent { onConversationReady(TEST_ACCOUNT_ID, CONVERSATION_ID) }
        advanceUntilIdle()

        assertTrue(
            seen.any { it.accountId == TEST_ACCOUNT_ID && it.conversationId == CONVERSATION_ID },
            "the daemon's ConversationReady never reached the facade: $seen"
        )
        collector.cancel()
    }

    /** A late subscriber still hears it, which is the backfill fixed in `f487cfa`. */
    @Test
    fun aLateSubscriberStillHearsAFiredConversationReady() = runTest {
        val scope = viewModelScope()
        val stack = makeTestServiceStack(scope = scope)
        prepareAccountInService(stack.stub, stack.accountService)
        connectDaemonCallbacks(stack, scope)
        advanceUntilIdle()

        stack.stub.fireDaemonEvent { onConversationReady(TEST_ACCOUNT_ID, CONVERSATION_ID) }
        advanceUntilIdle()

        val event = stack.conversationFacade.conversationEvents
            .filterIsInstance<ConversationEvent.ConversationReady>()
            .first()

        assertEquals(CONVERSATION_ID, event.conversationId)
    }

    /** Firing into a stub nobody initialised is a test bug, and says so. */
    @Test
    fun firingBeforeInitFailsLoudly() {
        val stub = StubDaemonBridge()
        assertFailsWith<IllegalStateException> {
            stub.fireDaemonEvent { onAccountsChanged() }
        }
    }

    private companion object {
        const val CONVERSATION_ID = "conv-daemon-event"
    }
}
