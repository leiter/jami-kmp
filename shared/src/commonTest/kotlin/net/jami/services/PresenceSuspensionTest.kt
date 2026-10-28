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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import net.jami.model.Uri
import net.jami.testHardwareService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Presence suspend/resume on background (contacts plan F2).
 *
 * The daemon reference-counts `subscribeBuddy`, and three owners subscribe independently
 * (ConversationFacade, ConversationsViewModel, ContactService.observeContact). These cover the
 * aggregate refcount in ContactService that lets presence be dropped and restored wholesale
 * without the daemon's own count drifting.
 */
class PresenceSuspensionTest {

    private val alice = Uri.fromId("aaaa1111")
    private val bob = Uri.fromId("bbbb2222")
    private val account = "acc1"

    private fun TestScope.makeService(stub: StubDaemonBridge): ContactService {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val accountService = AccountService(stub, testHardwareService(), StubDeviceRuntimeService(), scope)
        return ContactService(scope, accountService, stub, VCardService(StubDeviceRuntimeService()))
    }

    /** Only the first subscribe and the last unsubscribe reach the daemon. */
    @Test
    fun refCountsCollapseRepeatedSubscribes() = runTest {
        val stub = StubDaemonBridge()
        val contacts = makeService(stub)

        contacts.subscribeBuddy(account, alice, true)
        contacts.subscribeBuddy(account, alice, true)
        contacts.subscribeBuddy(account, alice, true)
        assertEquals(
            listOf(Triple(account, alice.rawRingId, true)),
            stub.subscribeBuddyCalls,
            "three owners subscribing must produce one daemon subscribe"
        )

        contacts.subscribeBuddy(account, alice, false)
        contacts.subscribeBuddy(account, alice, false)
        assertEquals(1, stub.subscribeBuddyCalls.size, "unsubscribe only on the last ref")

        contacts.subscribeBuddy(account, alice, false)
        assertEquals(
            Triple(account, alice.rawRingId, false),
            stub.subscribeBuddyCalls.last()
        )
    }

    /** An unsubscribe with no matching subscribe must not drive the daemon count negative. */
    @Test
    fun unbalancedUnsubscribeIsIgnored() = runTest {
        val stub = StubDaemonBridge()
        val contacts = makeService(stub)

        contacts.subscribeBuddy(account, alice, false)
        assertTrue(stub.subscribeBuddyCalls.isEmpty())

        contacts.subscribeBuddy(account, alice, true)
        contacts.subscribeBuddy(account, alice, false)
        contacts.subscribeBuddy(account, alice, false)
        assertEquals(2, stub.subscribeBuddyCalls.size, "the extra unsubscribe must be dropped")
    }

    /** Background releases every live subscription; foreground restores exactly those. */
    @Test
    fun suspendReleasesAndResumeRestores() = runTest {
        val stub = StubDaemonBridge()
        val contacts = makeService(stub)

        contacts.subscribeBuddy(account, alice, true)
        contacts.subscribeBuddy(account, bob, true)
        stub.subscribeBuddyCalls.clear()

        contacts.suspendPresence()
        assertTrue(contacts.isPresenceSuspended)
        assertEquals(2, stub.subscribeBuddyCalls.size)
        assertTrue(stub.subscribeBuddyCalls.none { it.third }, "suspend only unsubscribes")
        assertEquals(
            setOf(alice.rawRingId, bob.rawRingId),
            stub.subscribeBuddyCalls.map { it.second }.toSet()
        )

        stub.subscribeBuddyCalls.clear()
        contacts.resumePresence()
        assertFalse(contacts.isPresenceSuspended)
        assertEquals(2, stub.subscribeBuddyCalls.size)
        assertTrue(stub.subscribeBuddyCalls.all { it.third }, "resume only subscribes")
        assertEquals(
            setOf(alice.rawRingId, bob.rawRingId),
            stub.subscribeBuddyCalls.map { it.second }.toSet()
        )
    }

    /** Both are idempotent — a double background or a resume without a suspend is a no-op. */
    @Test
    fun suspendAndResumeAreIdempotent() = runTest {
        val stub = StubDaemonBridge()
        val contacts = makeService(stub)
        contacts.subscribeBuddy(account, alice, true)
        stub.subscribeBuddyCalls.clear()

        contacts.resumePresence()
        assertTrue(stub.subscribeBuddyCalls.isEmpty(), "resume without suspend does nothing")

        contacts.suspendPresence()
        contacts.suspendPresence()
        assertEquals(1, stub.subscribeBuddyCalls.size, "second suspend must not unsubscribe twice")

        stub.subscribeBuddyCalls.clear()
        contacts.resumePresence()
        contacts.resumePresence()
        assertEquals(1, stub.subscribeBuddyCalls.size, "second resume must not subscribe twice")
    }

    /** A buddy first seen while backgrounded is subscribed once, on resume — not twice. */
    @Test
    fun buddyAddedWhileSuspendedIsSubscribedOnceOnResume() = runTest {
        val stub = StubDaemonBridge()
        val contacts = makeService(stub)

        contacts.subscribeBuddy(account, alice, true)
        contacts.suspendPresence()
        stub.subscribeBuddyCalls.clear()

        // e.g. a smartlist load driven by a push while the app is in the background.
        contacts.subscribeBuddy(account, bob, true)
        assertTrue(stub.subscribeBuddyCalls.isEmpty(), "nothing reaches the daemon while suspended")

        contacts.resumePresence()
        assertEquals(2, stub.subscribeBuddyCalls.size)
        assertEquals(
            setOf(alice.rawRingId, bob.rawRingId),
            stub.subscribeBuddyCalls.map { it.second }.toSet()
        )
    }

    /** A buddy released while backgrounded is not resurrected on resume. */
    @Test
    fun buddyReleasedWhileSuspendedIsNotRestored() = runTest {
        val stub = StubDaemonBridge()
        val contacts = makeService(stub)

        contacts.subscribeBuddy(account, alice, true)
        contacts.subscribeBuddy(account, bob, true)
        contacts.suspendPresence()
        stub.subscribeBuddyCalls.clear()

        // e.g. ConversationsViewModel.onCleared() while the app is backgrounded.
        contacts.subscribeBuddy(account, bob, false)
        assertTrue(stub.subscribeBuddyCalls.isEmpty(), "no daemon call while suspended")

        contacts.resumePresence()
        assertEquals(
            listOf(Triple(account, alice.rawRingId, true)),
            stub.subscribeBuddyCalls,
            "bob's ref was released while suspended, so only alice comes back"
        )
    }

    /** Subscriptions are per account, not per contact URI. */
    @Test
    fun refsAreScopedPerAccount() = runTest {
        val stub = StubDaemonBridge()
        val contacts = makeService(stub)

        contacts.subscribeBuddy("acc1", alice, true)
        contacts.subscribeBuddy("acc2", alice, true)
        assertEquals(2, stub.subscribeBuddyCalls.size, "same buddy on two accounts is two subscriptions")

        stub.subscribeBuddyCalls.clear()
        contacts.subscribeBuddy("acc1", alice, false)
        assertEquals(
            listOf(Triple("acc1", alice.rawRingId, false)),
            stub.subscribeBuddyCalls,
            "releasing one account must not release the other"
        )
    }
}
