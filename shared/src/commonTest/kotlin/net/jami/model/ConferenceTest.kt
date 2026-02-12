package net.jami.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConferenceTest {

    @Test
    fun testConferenceCreation() {
        val conf = Conference("account1", "conf123")
        assertEquals("account1", conf.accountId)
        assertEquals("conf123", conf.id)
        assertTrue(conf.participants.isEmpty())
    }

    @Test
    fun testConferenceFromCall() {
        val call = Call(
            account = "account1",
            daemonId = "call123",
            peerUri = Uri.fromString("jami:abc123"),
            isIncoming = false
        )
        val conf = Conference(call)

        assertEquals("account1", conf.accountId)
        assertEquals("call123", conf.id)
        assertEquals(1, conf.participants.size)
        assertEquals(call, conf.firstCall)
    }

    @Test
    fun testAddRemoveParticipants() {
        val conf = Conference("account1", "conf123")
        val call1 = Call("account1", "call1", Uri.fromString("jami:peer1"), false)
        val call2 = Call("account1", "call2", Uri.fromString("jami:peer2"), false)

        conf.addParticipant(call1)
        assertEquals(1, conf.participants.size)

        conf.addParticipant(call2)
        assertEquals(2, conf.participants.size)

        assertTrue(conf.removeParticipant(call1))
        assertEquals(1, conf.participants.size)

        conf.removeParticipants()
        assertTrue(conf.participants.isEmpty())
    }

    @Test
    fun testIsSimpleCall() {
        val call = Call("account1", "call123", Uri.fromString("jami:peer"), false)
        val conf = Conference(call)

        assertTrue(conf.isSimpleCall)
        assertFalse(conf.isConference)
    }

    @Test
    fun testIsConference() {
        val conf = Conference("account1", "conf123")
        val call1 = Call("account1", "call1", Uri.fromString("jami:peer1"), false)
        val call2 = Call("account1", "call2", Uri.fromString("jami:peer2"), false)

        conf.addParticipant(call1)
        conf.addParticipant(call2)

        assertTrue(conf.isConference)
        assertFalse(conf.isSimpleCall)
    }

    @Test
    fun testSetState() {
        val conf = Conference("account1", "conf123")

        conf.setState("ACTIVE_ATTACHED")
        assertEquals(Call.CallStatus.CURRENT, conf.state)

        conf.setState("HOLD")
        assertEquals(Call.CallStatus.HOLD, conf.state)
    }

    @Test
    fun testContains() {
        val conf = Conference("account1", "conf123")
        val call = Call("account1", "call456", Uri.fromString("jami:peer"), false)

        assertFalse(conf.contains("call456"))

        conf.addParticipant(call)
        assertTrue(conf.contains("call456"))
    }

    @Test
    fun testFindCallByContact() {
        val uri = Uri.fromString("jami:specificpeer")
        val call = Call("account1", "call123", uri, false)
        val conf = Conference(call)

        assertEquals(call, conf.findCallByContact(uri))
        assertEquals(null, conf.findCallByContact(Uri.fromString("jami:otherpeer")))
    }
}
