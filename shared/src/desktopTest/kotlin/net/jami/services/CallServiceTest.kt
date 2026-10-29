package net.jami.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.jami.model.Call
import net.jami.services.expect.HardwareService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CallServiceTest {

    private fun createTestCallService(
        daemonBridge: StubDaemonBridge = StubDaemonBridge()
    ): CallService {
        val hardwareService = HardwareService()
        val deviceRuntimeService = StubDeviceRuntimeService()
        val scope = CoroutineScope(Dispatchers.Default)
        val accountService = AccountService(daemonBridge, hardwareService, deviceRuntimeService, scope)
        val settingsRepository = net.jami.repository.SettingsRepository(daemonBridge, net.jami.services.InMemoryClientSettingsStore(), scope)
        return CallService(daemonBridge, accountService, settingsRepository, scope)
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

    // ==================== In-call chat (calling plan F8) ====================

    /**
     * The in-call path must use the daemon's call-level send. CallService.sendTextMessage routes
     * to the account-level API, which addresses a contact URI, so a call ID handed to it went
     * nowhere.
     */
    @Test
    fun sendInCallMessageUsesTheCallLevelDaemonApi() = runTest {
        val daemonBridge = StubDaemonBridge()
        val service = createTestCallService(daemonBridge)

        service.onIncomingCall("account1", "call123", "jami:peer", emptyList())
        service.callUpdates.first()

        service.sendInCallMessage("account1", "call123", "me123", "on my way")
        service.inCallMessages.first { it.isNotEmpty() }

        val sent = daemonBridge.sentCallMessages.single()
        assertEquals("call123", sent.callId)
        assertEquals("me123", sent.from)
        assertEquals(mapOf("text/plain" to "on my way"), sent.messages)
        assertFalse(sent.isMixed)
    }

    /** Our own messages are not echoed back by the daemon, so they are recorded locally. */
    @Test
    fun sendInCallMessageRecordsTheOutgoingMessage() = runTest {
        val daemonBridge = StubDaemonBridge()
        val service = createTestCallService(daemonBridge)

        service.sendInCallMessage("account1", "call123", "me123", "hello")
        val messages = service.inCallMessages.first { it.isNotEmpty() }

        assertEquals(1, messages.size)
        assertTrue(messages[0].isOutgoing)
        assertEquals("hello", messages[0].body)
        assertEquals("call123", messages[0].callId)
    }

    @Test
    fun incomingInCallMessageIsRecorded() = runTest {
        val service = createTestCallService()

        service.onInCallMessage("account1", "call123", "peer123", mapOf("text/plain" to "hi there"))
        val messages = service.inCallMessages.first { it.isNotEmpty() }

        assertEquals("hi there", messages[0].body)
        assertEquals("peer123", messages[0].author)
        assertFalse(messages[0].isOutgoing)
    }

    /** A message with no usable body must not produce an empty bubble. */
    @Test
    fun incomingInCallMessageWithNoBodyIsIgnored() = runTest {
        val service = createTestCallService()

        service.onInCallMessage("account1", "call123", "peer123", emptyMap())

        assertTrue(service.inCallMessages.value.isEmpty())
    }

    /** The chat belongs to the call, so it goes away when the call does. */
    @Test
    fun inCallMessagesAreClearedWhenTheCallEnds() = runTest {
        val service = createTestCallService()

        service.onIncomingCall("account1", "call123", "jami:peer", emptyList())
        service.callUpdates.first()
        service.onInCallMessage("account1", "call123", "peer123", mapOf("text/plain" to "hi"))
        service.inCallMessages.first { it.isNotEmpty() }

        service.onCallStateChanged("account1", "call123", "OVER", 0)

        service.inCallMessages.first { it.isEmpty() }
        assertTrue(service.inCallMessages.value.isEmpty())
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
