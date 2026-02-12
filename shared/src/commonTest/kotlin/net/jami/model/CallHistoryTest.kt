package net.jami.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CallHistoryTest {

    @Test
    fun testCallHistoryFromCall() {
        val call = Call(
            account = "account1",
            daemonId = "call123",
            peerUri = Uri.fromString("jami:peer123"),
            isIncoming = true
        )
        call.contact = Contact(Uri.fromString("jami:peer123"))

        val history = CallHistory(call)

        assertEquals("account1", history.account)
        assertEquals("call123", history.daemonIdString)
        assertTrue(history.isIncoming)
        assertEquals(Interaction.InteractionType.CALL, history.type)
        assertTrue(history.isMissed) // No duration set
    }

    @Test
    fun testCallHistoryFromInteraction() {
        val interaction = Interaction("account1").apply {
            id = 42
            author = "author1"
            timestamp = 1000L
            status = Interaction.InteractionStatus.SUCCESS
            daemonId = 999L
            extraFlag = """{"duration":5000}"""
        }

        val history = CallHistory(interaction)

        assertEquals(42, history.id)
        assertEquals("author1", history.author)
        assertEquals(1000L, history.timestamp)
        assertEquals(Interaction.InteractionType.CALL, history.type)
        assertTrue(history.isIncoming) // author != null means incoming
        assertEquals(5000L, history.duration)
        assertFalse(history.isMissed) // Has duration
    }

    @Test
    fun testCallHistoryWithDirection() {
        val historyIncoming = CallHistory(
            daemonId = "123",
            account = "account1",
            contactNumber = "jami:peer",
            direction = Call.Direction.INCOMING,
            timestamp = 1000L
        )

        assertTrue(historyIncoming.isIncoming)
        assertEquals("jami:peer", historyIncoming.author)

        val historyOutgoing = CallHistory(
            daemonId = "456",
            account = "account1",
            contactNumber = "jami:peer",
            direction = Call.Direction.OUTGOING,
            timestamp = 2000L
        )

        assertFalse(historyOutgoing.isIncoming)
        assertNull(historyOutgoing.author)
    }

    @Test
    fun testDuration() {
        val history = CallHistory(
            daemonId = "123",
            account = "account1",
            contactNumber = "jami:peer",
            direction = Call.Direction.INCOMING,
            timestamp = 1000L
        )

        assertTrue(history.isMissed)
        assertEquals(0L, history.duration)

        history.duration = 5000L
        assertFalse(history.isMissed)
        assertEquals(5000L, history.duration)
    }

    @Test
    fun testDurationString() {
        val history = CallHistory(
            daemonId = "123",
            account = "account1",
            contactNumber = "jami:peer",
            direction = Call.Direction.INCOMING,
            timestamp = 1000L
        )

        // Less than a minute
        history.duration = 45000L // 45 seconds
        assertEquals("45 secs", history.durationString)

        // Minutes
        history.duration = 125000L // 2 min 5 sec
        assertEquals("02 mins 05 secs", history.durationString)

        // Hours
        history.duration = 3725000L // 1 hour 2 min 5 sec
        assertEquals("1 h 02 mins 05 secs", history.durationString)
    }

    @Test
    fun testIsConferenceParticipant() {
        val history = CallHistory(
            daemonId = "123",
            account = "account1",
            contactNumber = "jami:peer",
            direction = Call.Direction.INCOMING,
            timestamp = 1000L
        )

        assertFalse(history.isConferenceParticipant)

        history.confId = "conf456"
        assertTrue(history.isConferenceParticipant)
    }

    @Test
    fun testIsGroupCall() {
        val history = CallHistory(
            daemonId = "123",
            account = "account1",
            contactNumber = "jami:peer",
            direction = Call.Direction.INCOMING,
            timestamp = 1000L
        )

        assertFalse(history.isGroupCall)

        history.confId = "conf456"
        assertTrue(history.isGroupCall) // Conference with no duration

        history.duration = 1000L
        assertFalse(history.isGroupCall) // Has duration
    }

    @Test
    fun testSetEnded() {
        val startCall = CallHistory(
            daemonId = "123",
            account = "account1",
            contactNumber = "jami:peer",
            direction = Call.Direction.INCOMING,
            timestamp = 1000L
        )

        val endCall = CallHistory(
            daemonId = "123",
            account = "account1",
            contactNumber = "jami:peer",
            direction = Call.Direction.INCOMING,
            timestamp = 2000L
        )
        endCall.duration = 60000L

        assertEquals(0L, startCall.duration)
        startCall.setEnded(endCall)
        assertEquals(60000L, startCall.duration)
    }
}
