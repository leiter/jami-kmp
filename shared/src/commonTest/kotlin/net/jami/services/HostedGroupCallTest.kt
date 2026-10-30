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

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.jami.model.Call
import net.jami.model.Conference
import net.jami.model.Uri
import net.jami.viewmodel.makeAccountService
import net.jami.viewmodel.makeCallService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Placing a group call hosts a conference on the conversation and returns no call id, so the
 * host has nothing to track by id. Found on a device 2026-10-30: the caller could start a group
 * call and others could join, but the caller could not hang up — with no call id the call screen
 * never subscribed to anything, so `currentConference` stayed null and the end button did
 * nothing. [CallService.hostedConfUpdates] is what the host tracks instead.
 */
class HostedGroupCallTest {

    private companion object {
        const val ACC = "acc1"
        const val CONV = "a5bdf465fcaca4da1c86613d35a356ad24cd21b7"
        const val CONF = "1571946432184773"
        const val PEER = "66d57f55227639b58fdcc6d74ec090446ffa9bc3"
    }

    private fun env(scope: kotlinx.coroutines.CoroutineScope): Pair<StubDaemonBridge, CallService> {
        val stub = StubDaemonBridge()
        return stub to makeCallService(stub, makeAccountService(stub, scope), scope = scope)
    }

    /** What placeCall hands back for a swarm target: a call with no daemon id. */
    private fun hostCall() = Call(
        ACC,
        null,
        Uri(Uri.SWARM_SCHEME, CONV),
        false,
        Uri(Uri.SWARM_SCHEME, CONV)
    )

    @Test
    fun theConferenceOnTheConversationReachesTheHost() = runTest {
        val (_, calls) = env(this)
        val host = hostCall()
        val seen = mutableListOf<Conference>()
        val job = launch { calls.hostedConfUpdates(host).collect { seen.add(it) } }
        advanceUntilIdle()

        calls.onConferenceCreated(ACC, CONV, CONF)
        advanceUntilIdle()
        job.cancel()

        val conf = assertNotNull(seen.firstOrNull(), "the host never saw its own conference")
        assertEquals(CONF, conf.id)
    }

    /** Without the link there is no conference id to hang up with, and no state to show. */
    @Test
    fun theHostCallIsLinkedToTheConference() = runTest {
        val (_, calls) = env(this)
        val host = hostCall()
        val job = launch { calls.hostedConfUpdates(host).collect { } }
        advanceUntilIdle()

        calls.onConferenceCreated(ACC, CONV, CONF)
        advanceUntilIdle()
        job.cancel()

        assertEquals(CONF, host.confId)
        assertSame(host, assertNotNull(calls.getConference(CONF)).hostCall)
    }

    /**
     * The conference can already exist when the call is placed — joining a group call that is
     * running, which is how the device reproduced it. Resolving only on later updates would miss
     * it and leave the host untracked.
     */
    @Test
    fun aConferenceThatAlreadyExistsIsPickedUp() = runTest {
        val (_, calls) = env(this)
        calls.onConferenceCreated(ACC, CONV, CONF)
        advanceUntilIdle()

        val host = hostCall()
        val seen = mutableListOf<Conference>()
        val job = launch { calls.hostedConfUpdates(host).collect { seen.add(it) } }
        advanceUntilIdle()
        job.cancel()

        assertEquals(CONF, assertNotNull(seen.firstOrNull()).id)
        assertEquals(CONF, host.confId)
    }

    // ==================== Ending a hosted group call ====================

    /**
     * The regression: the notification's end action hung the call id it carried up directly, so
     * for a group call the daemon dropped one participant and the conference — and the call
     * screen — carried on.
     */
    @Test
    fun endingAParticipantOfAConferenceEndsTheConference() = runTest {
        val (stub, calls) = env(this)
        stub.callDetails["call-a"] = mapOf(Call.KEY_PEER_NUMBER to PEER)
        stub.participantLists[CONF] = listOf("call-a")
        calls.onCallStateChanged(ACC, "call-a", "CURRENT", 0)
        advanceUntilIdle()
        calls.onConferenceCreated(ACC, CONV, CONF)
        advanceUntilIdle()

        calls.hangUpAny(ACC, "call-a")
        advanceUntilIdle()

        assertEquals(listOf(ACC to CONF), stub.hungUpConferences)
        assertTrue(stub.hungUpCalls.isEmpty(), "hung up the participant instead of the conference")
    }

    /** The host's own notification carries the conference id rather than a call id. */
    @Test
    fun endingByConferenceIdEndsTheConference() = runTest {
        val (stub, calls) = env(this)
        calls.onConferenceCreated(ACC, CONV, CONF)
        advanceUntilIdle()

        calls.hangUpAny(ACC, CONF)
        advanceUntilIdle()

        assertEquals(listOf(ACC to CONF), stub.hungUpConferences)
    }

    /** A plain 1:1 call is still hung up as a call. */
    @Test
    fun endingAPlainCallHangsUpThatCall() = runTest {
        val (stub, calls) = env(this)
        stub.callDetails["call-a"] = mapOf(Call.KEY_PEER_NUMBER to PEER)
        calls.onCallStateChanged(ACC, "call-a", "CURRENT", 0)
        advanceUntilIdle()

        calls.hangUpAny(ACC, "call-a")
        advanceUntilIdle()

        assertEquals(listOf(ACC to "call-a"), stub.hungUpCalls)
        assertTrue(stub.hungUpConferences.isEmpty())
    }

    /**
     * Once the conference is gone its participants are gone with it, so its own state is all the
     * call screen has left to judge it by. Without this it still read as CURRENT and the screen
     * stayed up after the call had ended.
     */
    @Test
    fun aRemovedConferenceReadsAsOver() = runTest {
        val (_, calls) = env(this)
        val host = hostCall()
        val seen = mutableListOf<Conference>()
        val job = launch { calls.hostedConfUpdates(host).collect { seen.add(it) } }
        advanceUntilIdle()
        calls.onConferenceCreated(ACC, CONV, CONF)
        advanceUntilIdle()

        calls.onConferenceRemoved(ACC, CONF)
        advanceUntilIdle()
        job.cancel()

        val last = assertNotNull(seen.lastOrNull(), "the host never saw the conference end")
        assertEquals(Call.CallStatus.OVER, last.state)
        assertTrue(assertNotNull(last.state).isOver)
    }

    /** A conference belonging to some other conversation is not this host's call. */
    @Test
    fun anUnrelatedConferenceIsIgnored() = runTest {
        val (_, calls) = env(this)
        val host = hostCall()
        val seen = mutableListOf<Conference>()
        val job = launch { calls.hostedConfUpdates(host).collect { seen.add(it) } }
        advanceUntilIdle()

        calls.onConferenceCreated(ACC, "ffff0000ffff0000ffff0000ffff0000ffff0000", "other-conf")
        advanceUntilIdle()
        job.cancel()

        assertTrue(seen.isEmpty(), "tracked a conference from another conversation")
        assertEquals(null, host.confId)
    }
}
