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
import net.jami.model.Call
import net.jami.model.Uri
import net.jami.viewmodel.makeAccountService
import net.jami.viewmodel.makeCallService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Creating a conference straight from a list of people (mini-plan calling F10).
 *
 * The daemon's createConfFromParticipantList returns no call ids, so everything here is about
 * recognising the calls it places on our behalf when they come back as plain callStateChanged.
 */
class InstantConferenceTest {

    private companion object {
        const val ACC = "acc1"
        const val ALICE = "aaaa111122223333444455556666777788889999"
        const val BOB = "bbbb111122223333444455556666777788889999"
    }

    private fun env(scope: kotlinx.coroutines.CoroutineScope): Pair<StubDaemonBridge, CallService> {
        val stub = StubDaemonBridge()
        return stub to makeCallService(stub, makeAccountService(stub, scope), scope = scope)
    }

    @Test
    fun participantsAreSentAsUriCommaAccount() = runTest {
        val (stub, calls) = env(this)

        assertTrue(calls.createConferenceWith(ACC, listOf(Uri.fromString(ALICE), Uri.fromString(BOB))))

        val (accountId, participants) = assertNotNull(stub.conferencesCreatedFromList.lastOrNull())
        assertEquals(ACC, accountId)
        assertEquals(listOf("$ALICE,$ACC", "$BOB,$ACC"), participants)
    }

    /** The daemon logs an error and does nothing below two, so the request is not worth sending. */
    @Test
    fun fewerThanTwoParticipantsIsNotSent() = runTest {
        val (stub, calls) = env(this)

        assertFalse(calls.createConferenceWith(ACC, listOf(Uri.fromString(ALICE))))
        assertFalse(calls.createConferenceWith(ACC, emptyList()))

        assertTrue(stub.conferencesCreatedFromList.isEmpty())
    }

    /**
     * The regression this guards: a RINGING call the client did not place is normally incoming,
     * and these are the one exception — they are ours, dialled by the daemon on our behalf.
     */
    @Test
    fun ourOwnConferenceDialsAreNotShownAsIncoming() = runTest {
        val (stub, calls) = env(this)
        stub.callDetails["call-a"] = mapOf(Call.KEY_PEER_NUMBER to ALICE)
        calls.createConferenceWith(ACC, listOf(Uri.fromString(ALICE), Uri.fromString(BOB)))

        calls.onCallStateChanged(ACC, "call-a", "RINGING", 0)
        advanceUntilIdle()

        val call = assertNotNull(calls.currentCalls.value.firstOrNull { it.daemonId == "call-a" })
        assertFalse(call.isIncoming, "we placed this call ourselves")
    }

    /** A genuine incoming call during the same window is still incoming. */
    @Test
    fun anUnrelatedRingingCallIsStillIncoming() = runTest {
        val (stub, calls) = env(this)
        stub.callDetails["call-x"] = mapOf(Call.KEY_PEER_NUMBER to "cccc1111222233334444555566667777888899")
        calls.createConferenceWith(ACC, listOf(Uri.fromString(ALICE), Uri.fromString(BOB)))

        calls.onCallStateChanged(ACC, "call-x", "RINGING", 0)
        advanceUntilIdle()

        val call = assertNotNull(calls.currentCalls.value.firstOrNull { it.daemonId == "call-x" })
        assertTrue(call.isIncoming)
    }

    /** Each participant is dialled once, so a later call to the same peer is not ours any more. */
    @Test
    fun eachDialIsClaimedOnlyOnce() = runTest {
        val (stub, calls) = env(this)
        stub.callDetails["call-a"] = mapOf(Call.KEY_PEER_NUMBER to ALICE)
        stub.callDetails["call-a2"] = mapOf(Call.KEY_PEER_NUMBER to ALICE)
        calls.createConferenceWith(ACC, listOf(Uri.fromString(ALICE), Uri.fromString(BOB)))

        calls.onCallStateChanged(ACC, "call-a", "RINGING", 0)
        advanceUntilIdle()
        calls.onCallStateChanged(ACC, "call-a2", "RINGING", 0)
        advanceUntilIdle()

        assertTrue(assertNotNull(calls.currentCalls.value.firstOrNull { it.daemonId == "call-a2" }).isIncoming)
    }

    /** The screen has to open on something, and the conference does not exist yet. */
    @Test
    fun theFirstOfOurCallsOpensTheCallScreen() = runTest {
        val (stub, calls) = env(this)
        stub.callDetails["call-a"] = mapOf(Call.KEY_PEER_NUMBER to ALICE)
        stub.callDetails["call-b"] = mapOf(Call.KEY_PEER_NUMBER to BOB)
        calls.createConferenceWith(ACC, listOf(Uri.fromString(ALICE), Uri.fromString(BOB)))

        calls.onCallStateChanged(ACC, "call-a", "RINGING", 0)
        advanceUntilIdle()
        assertEquals("call-a", calls.pendingCallNavId.value)

        calls.consumePendingCallNavId()
        calls.onCallStateChanged(ACC, "call-b", "RINGING", 0)
        advanceUntilIdle()
        assertEquals(null, calls.pendingCallNavId.value, "the second call must not steal the screen")
    }
}
