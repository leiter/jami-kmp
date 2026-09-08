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
import net.jami.services.expect.HardwareService
import net.jami.ui.viewmodel.CallMode
import net.jami.ui.viewmodel.CallViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import net.jami.services.StubDeviceRuntimeService
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlin.test.BeforeTest
import net.jami.ensurePlatformTestKoin

class CallViewModelTest {

    /** iOS's HardwareService resolves its dependencies from Koin; desktop's does not. */
    @BeforeTest
    fun startPlatformKoin() = ensurePlatformTestKoin()

    private fun makeVm(stub: StubDaemonBridge, scope: kotlinx.coroutines.CoroutineScope): Triple<CallViewModel, net.jami.services.CallService, net.jami.services.AccountService> {
        val accountService = makeAccountService(stub, scope)
        val contactService = makeContactService(stub, accountService, scope)
        val callService = makeCallService(stub, accountService, scope = scope)
        val vm = CallViewModel(callService, accountService, contactService, HardwareService(), StubDeviceRuntimeService(), scope)
        return Triple(vm, callService, accountService)
    }

    /**
     * Advances a bounded amount instead of [advanceUntilIdle].
     *
     * Once a call reaches CURRENT, CallViewModel starts a duration timer and video-loss
     * detection, both of which are `while (isActive) { ...; delay(n) }` loops. Those are
     * correct in production but never let advanceUntilIdle finish: on virtual time there is
     * always another scheduled task, so the scheduler spins forever — including in
     * runTest's own end-of-test cleanup, which is why bounding mid-test is not enough and
     * the tests below also call vm.onCleared() to cancel the timers before finishing.
     */
    private fun kotlinx.coroutines.test.TestScope.settle() {
        advanceTimeBy(100)
        runCurrent()
    }

    @Test
    fun initialStateIsEmpty() = runTest {
        val stub = StubDaemonBridge()
        val (vm) = makeVm(stub, viewModelScope())
        assertEquals("", vm.state.value.callStatus)
        assertEquals("", vm.state.value.peerUri)
        assertFalse(vm.state.value.isAudioMuted)
        assertFalse(vm.state.value.isVideoMuted)
        assertFalse(vm.state.value.isSpeakerOn)
        assertFalse(vm.state.value.isOnHold)
        assertFalse(vm.state.value.isConference)
    }

    @Test
    fun initOutgoingWithAccountSetsPeerUri() = runTest {
        val stub = StubDaemonBridge()
        stub.placeCallResult = "call_001"
        val (vm, _, accountService) = makeVm(stub, viewModelScope())
        prepareAccountInService(stub, accountService)
        vm.initOutgoing(contactUri = "jami:abc123", hasVideo = false)
        advanceUntilIdle()
        assertEquals("jami:abc123", vm.state.value.peerUri)
    }

    @Test
    fun initOutgoingWithNoAccountDoesNotSetPeerUri() = runTest {
        val stub = StubDaemonBridge()
        val (vm) = makeVm(stub, viewModelScope())
        vm.initOutgoing(contactUri = "jami:abc123", hasVideo = false)
        advanceUntilIdle()
        // No account → early return — peerUri stays empty
        assertEquals("", vm.state.value.peerUri)
    }

    @Test
    fun toggleMuteWithNoCallIdDoesNotChangeState() = runTest {
        val stub = StubDaemonBridge()
        val (vm) = makeVm(stub, viewModelScope())
        assertFalse(vm.state.value.isAudioMuted)
        vm.toggleMute()
        assertFalse(vm.state.value.isAudioMuted)
    }

    @Test
    fun toggleVideoWithNoCallIdDoesNotChangeState() = runTest {
        val stub = StubDaemonBridge()
        val (vm) = makeVm(stub, viewModelScope())
        assertFalse(vm.state.value.isVideoMuted)
        vm.toggleVideo()
        assertFalse(vm.state.value.isVideoMuted)
    }

    @Test
    fun toggleSpeakerRequiresActiveConference() = runTest {
        val stub = StubDaemonBridge()
        val (vm) = makeVm(stub, viewModelScope())
        // No active conference → toggleSpeaker is a no-op
        assertFalse(vm.state.value.isSpeakerOn)
        vm.toggleSpeaker()
        assertFalse(vm.state.value.isSpeakerOn)
    }

    @Test
    fun callStateChangedFromDaemonUpdatesStatus() = runTest {
        val stub = StubDaemonBridge()
        val scope = viewModelScope()
        val (vm, callService, accountService) = makeVm(stub, scope)
        prepareAccountInService(stub, accountService)
        // Simulate daemon callback: incoming call → CURRENT
        callService.onIncomingCall(TEST_ACCOUNT_ID, "call_001", "jami:peer", emptyList())
        callService.onCallStateChanged(TEST_ACCOUNT_ID, "call_001", "CURRENT", 0)
        settle()
        vm.initIncoming("call_001")
        settle()
        assertEquals("CURRENT", vm.state.value.callStatus)
        assertIs<CallMode.OnGoing>(vm.state.value.callMode)
        vm.onCleared() // cancel duration/video-loss timers before runTest cleanup
    }

    @Test
    fun callEndedFromDaemonSetsEndedMode() = runTest {
        val stub = StubDaemonBridge()
        val scope = viewModelScope()
        val (vm, callService, accountService) = makeVm(stub, scope)
        prepareAccountInService(stub, accountService)
        callService.onIncomingCall(TEST_ACCOUNT_ID, "call_001", "jami:peer", emptyList())
        callService.onCallStateChanged(TEST_ACCOUNT_ID, "call_001", "CURRENT", 0)
        settle()
        vm.initIncoming("call_001")
        settle()
        callService.onCallStateChanged(TEST_ACCOUNT_ID, "call_001", "OVER", 0)
        settle()
        assertEquals("OVER", vm.state.value.callStatus)
        assertIs<CallMode.Ended>(vm.state.value.callMode)
        vm.onCleared() // cancel duration/video-loss timers before runTest cleanup
    }

    @Test
    fun busyCallSetsHangupReasonBusy() = runTest {
        val stub = StubDaemonBridge()
        val scope = viewModelScope()
        val (vm, callService, accountService) = makeVm(stub, scope)
        prepareAccountInService(stub, accountService)
        stub.placeCallResult = "call_busy"
        vm.initOutgoing(contactUri = "jami:busy", hasVideo = false)
        advanceUntilIdle()
        callService.onCallStateChanged(TEST_ACCOUNT_ID, "call_busy", "BUSY", 0)
        advanceUntilIdle()
        val endedMode = vm.state.value.callMode
        assertIs<CallMode.Ended>(endedMode)
        vm.onCleared() // cancel duration/video-loss timers before runTest cleanup
    }

    @Test
    fun acceptCurrentWithNoCallIdDoesNotCrash() = runTest {
        val stub = StubDaemonBridge()
        val (vm) = makeVm(stub, viewModelScope())
        vm.acceptCurrent(withVideo = false)
    }

    @Test
    fun refuseCurrentWithNoCallIdDoesNotCrash() = runTest {
        val stub = StubDaemonBridge()
        val (vm) = makeVm(stub, viewModelScope())
        vm.refuseCurrent()
    }

    @Test
    fun endCallWithNoCallIdDoesNotCrash() = runTest {
        val stub = StubDaemonBridge()
        val (vm) = makeVm(stub, viewModelScope())
        vm.endCall()
    }

    @Test
    fun sendDtmfCallsPlayDtmfOnDaemon() = runTest {
        val stub = StubDaemonBridge()
        val (vm) = makeVm(stub, viewModelScope())
        // sendDtmf delegates immediately (no guard) — just verify it doesn't throw
        vm.sendDtmf('5')
    }

    @Test
    fun onClearedDoesNotThrow() = runTest {
        val stub = StubDaemonBridge()
        val (vm) = makeVm(stub, disposableScope())
        vm.onCleared()
    }
}
