package net.jami.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CallTest {

    @Test
    fun testCallStatusFromString() {
        assertEquals(Call.CallStatus.RINGING, Call.CallStatus.fromString("RINGING"))
        assertEquals(Call.CallStatus.RINGING, Call.CallStatus.fromString("INCOMING"))
        assertEquals(Call.CallStatus.CURRENT, Call.CallStatus.fromString("CURRENT"))
        assertEquals(Call.CallStatus.HUNGUP, Call.CallStatus.fromString("HUNGUP"))
        assertEquals(Call.CallStatus.NONE, Call.CallStatus.fromString("UNKNOWN"))
    }

    @Test
    fun testCallStatusProperties() {
        assertTrue(Call.CallStatus.RINGING.isRinging)
        assertTrue(Call.CallStatus.CONNECTING.isRinging)
        assertFalse(Call.CallStatus.CURRENT.isRinging)

        assertTrue(Call.CallStatus.CURRENT.isOnGoing)
        assertTrue(Call.CallStatus.HOLD.isOnGoing)
        assertFalse(Call.CallStatus.RINGING.isOnGoing)

        assertTrue(Call.CallStatus.HUNGUP.isOver)
        assertTrue(Call.CallStatus.FAILURE.isOver)
        assertFalse(Call.CallStatus.CURRENT.isOver)
    }

    @Test
    fun testCallDirection() {
        assertEquals(Call.Direction.INCOMING, Call.Direction.fromInt(0))
        assertEquals(Call.Direction.OUTGOING, Call.Direction.fromInt(1))
        assertEquals(Call.Direction.OUTGOING, Call.Direction.fromInt(99))
    }

    @Test
    fun testCallCreation() {
        val uri = Uri.fromString("jami:abc123")
        val call = Call(
            account = "account1",
            daemonId = "call123",
            peerUri = uri,
            isIncoming = true
        )

        assertEquals("account1", call.account)
        assertEquals("call123", call.daemonId)
        assertEquals(uri, call.peerUri)
        assertTrue(call.isIncoming)
        assertEquals(Call.CallStatus.NONE, call.callStatus)
        assertTrue(call.isMissed)
    }

    @Test
    fun testSetCallState() {
        val call = Call(
            account = "account1",
            daemonId = "call123",
            peerUri = Uri.fromString("jami:abc123"),
            isIncoming = true
        )

        assertTrue(call.isMissed)
        call.setCallState(Call.CallStatus.CURRENT)
        assertFalse(call.isMissed)
        assertEquals(Call.CallStatus.CURRENT, call.callStatus)
    }

    @Test
    fun testCallFromDetails() {
        val details = mapOf(
            Call.KEY_ACCOUNT_ID to "account1",
            Call.KEY_PEER_NUMBER to "jami:def456",
            Call.KEY_CALL_TYPE to "0",
            Call.KEY_CALL_STATE to "RINGING",
            Call.KEY_AUDIO_MUTED to "false",
            Call.KEY_VIDEO_MUTED to "true"
        )

        val call = Call("call456", details)

        assertEquals("account1", call.account)
        assertEquals("call456", call.daemonId)
        assertEquals(Call.CallStatus.RINGING, call.callStatus)
        assertTrue(call.isIncoming)
        assertFalse(call.isAudioMuted)
        assertTrue(call.isVideoMuted)
    }

    @Test
    fun testMediaList() {
        val call = Call(
            account = "account1",
            daemonId = "call123",
            peerUri = Uri.fromString("jami:abc123"),
            isIncoming = false
        )

        assertTrue(call.mediaList.isEmpty())
        assertFalse(call.hasVideo())
        assertFalse(call.hasAudio())

        call.setMediaList(listOf(Media.DEFAULT_AUDIO, Media.DEFAULT_VIDEO))

        assertEquals(2, call.mediaList.size)
        assertTrue(call.hasAudio())
        assertTrue(call.hasVideo())
        assertTrue(call.hasActiveAudio())
        assertTrue(call.hasActiveVideo())
    }

    @Test
    fun testConferenceParticipant() {
        val call = Call(
            account = "account1",
            daemonId = "call123",
            peerUri = Uri.fromString("jami:abc123"),
            isIncoming = false
        )

        assertFalse(call.isConferenceParticipant)

        call.setDetails(mapOf(Call.KEY_CONF_ID to "conf123"))
        assertTrue(call.isConferenceParticipant)
        assertEquals("conf123", call.confId)
    }
}
