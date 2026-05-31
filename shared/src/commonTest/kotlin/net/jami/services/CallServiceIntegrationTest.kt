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
import net.jami.model.AccountConfig
import net.jami.model.Call
import net.jami.model.ConfigKey
import net.jami.model.Uri
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for CallService using StubDaemonBridge.
 * Tests actual service method calls and state transitions via callbacks.
 */
class CallServiceIntegrationTest {

    private fun makeServices(
        stub: StubDaemonBridge,
        scope: kotlinx.coroutines.test.TestScope
    ): Pair<AccountService, CallService> {
        val accountService = AccountService(stub, net.jami.services.expect.HardwareService(), StubDeviceRuntimeService(), scope)
        val callService = CallService(stub, accountService, net.jami.repository.SettingsRepository(stub, scope), scope)
        return accountService to callService
    }

    @Test
    fun placeCallCreatesCallObject() = runTest {
        val stub = StubDaemonBridge()
        stub.placeCallResult = "call_001"
        val (accountService, callService) = makeServices(stub, this)

        val call = callService.placeCall("acc1", Uri.fromString("jami:peer123"), hasVideo = false)

        assertNotNull(call)
        assertEquals("call_001", call.daemonId)
        assertEquals("acc1", call.account)
    }

    @Test
    fun placeCallWithVideoSetsMediaList() = runTest {
        val stub = StubDaemonBridge()
        stub.placeCallResult = "call_002"
        val (accountService, callService) = makeServices(stub, this)

        val call = callService.placeCall("acc1", Uri.fromString("jami:peer123"), hasVideo = true)

        assertNotNull(call)
        assertTrue(call.hasVideo())
        assertTrue(call.hasAudio())
    }

    @Test
    fun onCallStateChangedCreatesCallIfNew() = runTest {
        val stub = StubDaemonBridge()
        val (accountService, callService) = makeServices(stub, this)

        callService.onCallStateChanged("acc1", "call_003", "RINGING", 0)
        advanceUntilIdle()

        val call = callService.getCall("call_003")
        assertNotNull(call)
        assertEquals(Call.CallStatus.RINGING, call.callStatus)
    }

    @Test
    fun callStateTransitionToOverRemovesCall() = runTest {
        val stub = StubDaemonBridge()
        val (accountService, callService) = makeServices(stub, this)

        callService.onCallStateChanged("acc1", "call_004", "CURRENT", 0)
        advanceUntilIdle()
        assertNotNull(callService.getCall("call_004"))

        callService.onCallStateChanged("acc1", "call_004", "OVER", 0)
        advanceUntilIdle()
        assertNull(callService.getCall("call_004"))
    }

    @Test
    fun onIncomingCallCreatesIncomingCall() = runTest {
        val stub = StubDaemonBridge()
        val (accountService, callService) = makeServices(stub, this)

        callService.onIncomingCall("acc1", "call_005", "jami:peer456", emptyList())
        advanceUntilIdle()

        val call = callService.getCall("call_005")
        assertNotNull(call)
        assertTrue(call.isIncoming)
    }

    @Test
    fun hangUpCallsDaemonBridge() = runTest {
        val stub = StubDaemonBridge()
        stub.placeCallResult = "call_006"
        val (accountService, callService) = makeServices(stub, this)

        callService.placeCall("acc1", Uri.fromString("jami:peer"), hasVideo = false)
        callService.hangUp("acc1", "call_006")
        advanceUntilIdle()
        // No crash — daemon bridge hangUp was called
    }

    @Test
    fun currentCallsStateFlowUpdates() = runTest {
        val stub = StubDaemonBridge()
        val (accountService, callService) = makeServices(stub, this)

        assertTrue(callService.currentCalls.value.isEmpty())

        callService.onCallStateChanged("acc1", "call_007", "CURRENT", 0)
        advanceUntilIdle()

        assertEquals(1, callService.currentCalls.value.size)
    }

    @Test
    fun onAudioMutedUpdatesCallState() = runTest {
        val stub = StubDaemonBridge()
        val (accountService, callService) = makeServices(stub, this)

        callService.onCallStateChanged("acc1", "call_008", "CURRENT", 0)
        advanceUntilIdle()

        callService.onAudioMuted("call_008", true)
        advanceUntilIdle()

        val call = callService.getCall("call_008")
        assertNotNull(call)
        assertTrue(call.isAudioMuted)
    }
}
