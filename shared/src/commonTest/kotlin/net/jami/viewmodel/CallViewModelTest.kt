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
package net.jami.viewmodel

import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.jami.services.StubDaemonBridge
import net.jami.ui.viewmodel.CallViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CallViewModelTest {

    @Test
    fun initialStateIsEmpty() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val callService = makeCallService(stub, accountService, this)
        val vm = CallViewModel(callService, accountService, viewModelScope())
        assertEquals("", vm.state.value.callStatus)
        assertEquals("", vm.state.value.peerUri)
        assertFalse(vm.state.value.isAudioMuted)
        assertFalse(vm.state.value.isVideoMuted)
        assertFalse(vm.state.value.isSpeakerOn)
        assertFalse(vm.state.value.isOnHold)
        assertFalse(vm.state.value.isConference)
    }

    @Test
    fun initCallWithAccountSetsPeerUri() = runTest {
        val stub = StubDaemonBridge()
        stub.placeCallResult = "call_001"
        val accountService = makeAccountService(stub, this)
        prepareAccountInService(stub, accountService)
        val callService = makeCallService(stub, accountService, this)
        val vm = CallViewModel(callService, accountService, viewModelScope())
        vm.initCall("jami:abc123", isVideo = false)
        advanceUntilIdle()
        assertEquals("jami:abc123", vm.state.value.peerUri)
    }

    @Test
    fun initCallWithNoAccountDoesNotSetPeerUri() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val callService = makeCallService(stub, accountService, this)
        val vm = CallViewModel(callService, accountService, viewModelScope())
        vm.initCall("jami:abc123", isVideo = false)
        advanceUntilIdle()
        // No account → early return — peerUri stays empty
        assertEquals("", vm.state.value.peerUri)
    }

    @Test
    fun toggleMuteFlipsAudioMuted() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val callService = makeCallService(stub, accountService, this)
        val vm = CallViewModel(callService, accountService, viewModelScope())
        assertFalse(vm.state.value.isAudioMuted)
        vm.toggleMute()
        // No callId set → early return, state unchanged
        assertFalse(vm.state.value.isAudioMuted)
    }

    @Test
    fun toggleVideoFlipsVideoMuted() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val callService = makeCallService(stub, accountService, this)
        val vm = CallViewModel(callService, accountService, viewModelScope())
        assertFalse(vm.state.value.isVideoMuted)
        vm.toggleVideo()
        // No callId set → early return, state unchanged
        assertFalse(vm.state.value.isVideoMuted)
    }

    @Test
    fun toggleSpeakerFlipsIsSpeakerOn() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val callService = makeCallService(stub, accountService, this)
        val vm = CallViewModel(callService, accountService, viewModelScope())
        assertFalse(vm.state.value.isSpeakerOn)
        vm.toggleSpeaker()
        // toggleSpeaker has no callId guard — it always flips
        assertTrue(vm.state.value.isSpeakerOn)
        vm.toggleSpeaker()
        assertFalse(vm.state.value.isSpeakerOn)
    }

    @Test
    fun callStateChangedFromDaemonUpdatesStatus() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        prepareAccountInService(stub, accountService)
        val callService = makeCallService(stub, accountService, this)
        val vm = CallViewModel(callService, accountService, viewModelScope())
        // Simulate daemon callback: call transitioned to CURRENT
        callService.onCallStateChanged(TEST_ACCOUNT_ID, "call_001", "CURRENT", 0)
        advanceUntilIdle()
        assertEquals("CURRENT", vm.state.value.callStatus)
    }

    @Test
    fun callEndedFromDaemonUpdatesStatus() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        prepareAccountInService(stub, accountService)
        val callService = makeCallService(stub, accountService, this)
        val vm = CallViewModel(callService, accountService, viewModelScope())
        callService.onCallStateChanged(TEST_ACCOUNT_ID, "call_001", "CURRENT", 0)
        advanceUntilIdle()
        callService.onCallStateChanged(TEST_ACCOUNT_ID, "call_001", "OVER", 0)
        advanceUntilIdle()
        assertEquals("OVER", vm.state.value.callStatus)
    }

    @Test
    fun acceptCallWithNoCallIdDoesNotCrash() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val callService = makeCallService(stub, accountService, this)
        val vm = CallViewModel(callService, accountService, viewModelScope())
        vm.acceptCall()
        // No callId → early return, no crash
    }

    @Test
    fun endCallWithNoCallIdDoesNotCrash() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val callService = makeCallService(stub, accountService, this)
        val vm = CallViewModel(callService, accountService, viewModelScope())
        vm.endCall()
        // No callId → early return, no crash
    }

    @Test
    fun onClearedDoesNotThrow() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val callService = makeCallService(stub, accountService, this)
        val vm = CallViewModel(callService, accountService, disposableScope())
        vm.onCleared()
    }
}
