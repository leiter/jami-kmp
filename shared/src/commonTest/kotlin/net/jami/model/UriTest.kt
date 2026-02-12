package net.jami.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UriTest {

    @Test
    fun testFromStringSimple() {
        val uri = Uri.fromString("jami:abc123def456789012345678901234567890ab")
        assertEquals("jami:", uri.scheme)
        assertEquals("abc123def456789012345678901234567890ab", uri.host)
    }

    @Test
    fun testFromStringWithUsername() {
        val uri = Uri.fromString("sip:user@example.com")
        assertEquals("sip:", uri.scheme)
        assertEquals("user", uri.username)
        assertEquals("example.com", uri.host)
    }

    @Test
    fun testFromStringWithPort() {
        val uri = Uri.fromString("sip:user@example.com:5060")
        assertEquals("sip:", uri.scheme)
        assertEquals("user", uri.username)
        assertEquals("example.com", uri.host)
        assertEquals("5060", uri.port)
    }

    @Test
    fun testIsHexId() {
        val hexUri = Uri.fromString("1234567890abcdef1234567890abcdef12345678")
        assertTrue(hexUri.isHexId)

        val nonHexUri = Uri.fromString("user@example.com")
        assertFalse(nonHexUri.isHexId)
    }

    @Test
    fun testIsJami() {
        val jamiUri = Uri.fromString("jami:1234567890abcdef1234567890abcdef12345678")
        assertTrue(jamiUri.isJami)

        val ringUri = Uri.fromString("ring:1234567890abcdef1234567890abcdef12345678")
        assertTrue(ringUri.isJami)

        val sipUri = Uri.fromString("sip:user@example.com")
        assertFalse(sipUri.isJami)
    }

    @Test
    fun testIsSwarm() {
        val swarmUri = Uri(Uri.SWARM_SCHEME, "conversationId123")
        assertTrue(swarmUri.isSwarm)

        val jamiUri = Uri.fromString("jami:abc123")
        assertFalse(jamiUri.isSwarm)
    }

    @Test
    fun testToString() {
        val uri = Uri("sip:", "user", "example.com", "5060")
        assertEquals("sip:user@example.com:5060", uri.toString())
    }

    @Test
    fun testRawRingId() {
        val uriWithUsername = Uri("jami:", "user123", "host", null)
        assertEquals("user123", uriWithUsername.rawRingId)

        val uriWithoutUsername = Uri("jami:", null, "host456", null)
        assertEquals("host456", uriWithoutUsername.rawRingId)
    }

    @Test
    fun testIsIpAddress() {
        assertTrue(Uri.isIpAddress("192.168.1.1"))
        assertTrue(Uri.isIpAddress("10.0.0.1"))
        assertTrue(Uri.isIpAddress("255.255.255.255"))
        assertFalse(Uri.isIpAddress("example.com"))
        assertFalse(Uri.isIpAddress("abc123"))
    }

    @Test
    fun testFromId() {
        val uri = Uri.fromId("conversation123")
        assertEquals("conversation123", uri.host)
        assertEquals(null, uri.scheme)
    }

    @Test
    fun testEquality() {
        val uri1 = Uri("jami:", "user", "host", null)
        val uri2 = Uri("jami:", "user", "host", "5060")
        val uri3 = Uri("sip:", "user", "host", null)

        // Equality is based on username and host only
        assertEquals(uri1, uri2)
        assertEquals(uri1, uri3)
    }
}
