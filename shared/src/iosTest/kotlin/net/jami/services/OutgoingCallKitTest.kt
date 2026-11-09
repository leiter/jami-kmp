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

import net.jami.model.Call
import net.jami.model.Uri
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Which outgoing calls CallKit is told about. Before, none were: reportOutgoingCallStarted had no
 * callers, so an outgoing call had no system call UI and no Recents entry.
 */
class OutgoingCallKitTest {

    private fun call(id: String, incoming: Boolean, status: Call.CallStatus) =
        Call("acc1", id, Uri.fromString("jami:bbbb"), isIncoming = incoming).apply { setCallState(status) }

    @Test
    fun anOutgoingCallIsReportedFromItsFirstLiveState() {
        for (status in listOf(Call.CallStatus.SEARCHING, Call.CallStatus.CONNECTING, Call.CallStatus.RINGING, Call.CallStatus.CURRENT)) {
            assertTrue(shouldReportOutgoing(call("c1", incoming = false, status), emptySet(), pushPending = false), status.name)
        }
    }

    @Test
    fun anIncomingCallIsNotReportedAsOutgoing() {
        assertFalse(shouldReportOutgoing(call("c1", incoming = true, Call.CallStatus.RINGING), emptySet(), pushPending = false))
    }

    @Test
    fun aCallIsReportedOnce() {
        assertFalse(shouldReportOutgoing(call("c1", incoming = false, Call.CallStatus.RINGING), setOf("c1"), pushPending = false))
    }

    @Test
    fun aConferencesFurtherLegsAreNotReported() {
        assertFalse(shouldReportOutgoing(call("c2", incoming = false, Call.CallStatus.CONNECTING), setOf("c1"), pushPending = false))
    }

    @Test
    fun nothingIsReportedOverARingingPushPlaceholder() {
        assertFalse(shouldReportOutgoing(call("c1", incoming = false, Call.CallStatus.CONNECTING), emptySet(), pushPending = true))
    }

    @Test
    fun anEndedOrUnstartedCallIsNotReported() {
        for (status in listOf(Call.CallStatus.NONE, Call.CallStatus.OVER, Call.CallStatus.FAILURE)) {
            assertFalse(shouldReportOutgoing(call("c1", incoming = false, status), emptySet(), pushPending = false), status.name)
        }
    }
}
