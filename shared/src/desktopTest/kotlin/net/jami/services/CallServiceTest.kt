package net.jami.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.jami.model.Call
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CallServiceTest {

    private fun createTestCallService(): CallService {
        val daemonBridge = DaemonBridge()
        val accountService = AccountService(daemonBridge, CoroutineScope(Dispatchers.Default))
        val scope = CoroutineScope(Dispatchers.Default)
        return CallService(daemonBridge, accountService, scope)
    }

    @Test
    fun testInitialState() {
        val service = createTestCallService()
        assertTrue(service.currentCalls.value.isEmpty())
        assertTrue(service.currentConferences.value.isEmpty())
    }

    @Test
    fun testGetCallReturnsNullForUnknownId() {
        val service = createTestCallService()
        val call = service.getCall("unknown")
        assertEquals(null, call)
    }

    @Test
    fun testGetConferenceReturnsNullForUnknownId() {
        val service = createTestCallService()
        val conf = service.getConference("unknown")
        assertEquals(null, conf)
    }

    @Test
    fun testOnIncomingCallCreatesCall() = runTest {
        val service = createTestCallService()

        // Simulate incoming call
        service.onIncomingCall(
            accountId = "account1",
            callId = "call123",
            from = "jami:peer123",
            mediaList = listOf(
                mapOf(
                    "MEDIA_TYPE" to "MEDIA_TYPE_AUDIO",
                    "LABEL" to "audio_0",
                    "ENABLED" to "true",
                    "MUTED" to "false"
                )
            )
        )

        // Wait for the call to be processed
        val call = service.callUpdates.first()
        assertNotNull(call)
        assertEquals("call123", call.daemonId)
        assertEquals("account1", call.account)
        assertTrue(call.hasAudio())
    }

    @Test
    fun testOnCallStateChanged() = runTest {
        val service = createTestCallService()

        // First, create a call via incoming
        service.onIncomingCall(
            accountId = "account1",
            callId = "call123",
            from = "jami:peer123",
            mediaList = emptyList()
        )

        // Wait for creation
        service.callUpdates.first()

        // Now change state to CURRENT
        service.onCallStateChanged(
            accountId = "account1",
            callId = "call123",
            stateStr = "CURRENT",
            detailCode = 0
        )

        // Wait for update and verify
        val call = service.callUpdates.first { it.callStatus == Call.CallStatus.CURRENT }
        assertEquals(Call.CallStatus.CURRENT, call.callStatus)
    }

    @Test
    fun testOnConferenceCreated() = runTest {
        val service = createTestCallService()

        service.onConferenceCreated(
            accountId = "account1",
            conversationId = "conv123",
            confId = "conf123"
        )

        val conf = service.conferenceUpdates.first()
        assertNotNull(conf)
        assertEquals("conf123", conf.id)
        assertEquals("account1", conf.accountId)
        assertEquals("conv123", conf.conversationId)
    }

    @Test
    fun testGetCallsForAccount() = runTest {
        val service = createTestCallService()

        // Create calls one at a time and wait for each
        service.onIncomingCall("account1", "call1", "jami:peer1", emptyList())
        service.callUpdates.first { it.daemonId == "call1" }

        service.onIncomingCall("account2", "call2", "jami:peer2", emptyList())
        service.callUpdates.first { it.daemonId == "call2" }

        service.onIncomingCall("account1", "call3", "jami:peer3", emptyList())
        service.callUpdates.first { it.daemonId == "call3" }

        val account1Calls = service.getCallsForAccount("account1")
        assertEquals(2, account1Calls.size)

        val account2Calls = service.getCallsForAccount("account2")
        assertEquals(1, account2Calls.size)
    }

    @Test
    fun testOnAudioMuted() = runTest {
        val service = createTestCallService()

        // Create a call first
        service.onIncomingCall("account1", "call123", "jami:peer", emptyList())
        service.callUpdates.first()

        // Set to CURRENT state so mute updates are emitted
        service.onCallStateChanged("account1", "call123", "CURRENT", 0)
        service.callUpdates.first { it.callStatus == Call.CallStatus.CURRENT }

        // Mute audio and wait for the update to be emitted
        service.onAudioMuted("call123", true)

        // Wait for the mute update to be emitted
        val updatedCall = service.callUpdates.first { it.daemonId == "call123" && it.isAudioMuted }
        assertTrue(updatedCall.isAudioMuted)
    }

    @Test
    fun testOnVideoMuted() = runTest {
        val service = createTestCallService()

        // Create a call first
        service.onIncomingCall("account1", "call123", "jami:peer", emptyList())
        service.callUpdates.first()

        // Set to CURRENT state
        service.onCallStateChanged("account1", "call123", "CURRENT", 0)
        service.callUpdates.first { it.callStatus == Call.CallStatus.CURRENT }

        // Mute video and wait for the update to be emitted
        service.onVideoMuted("call123", true)

        // Wait for the mute update to be emitted
        val updatedCall = service.callUpdates.first { it.daemonId == "call123" && it.isVideoMuted }
        assertTrue(updatedCall.isVideoMuted)
    }

    @Test
    fun testOnCallEnded() = runTest {
        val service = createTestCallService()

        // Create a call
        service.onIncomingCall("account1", "call123", "jami:peer", emptyList())
        service.callUpdates.first()

        assertEquals(1, service.currentCalls.value.size)

        // End the call
        service.onCallStateChanged("account1", "call123", "OVER", 0)
        service.callUpdates.first { it.callStatus == Call.CallStatus.OVER }

        // Call should be removed from current calls
        assertTrue(service.currentCalls.value.isEmpty())
    }
}
