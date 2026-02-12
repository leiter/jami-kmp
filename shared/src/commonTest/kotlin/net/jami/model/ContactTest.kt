package net.jami.model

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContactTest {

    @Test
    fun testContactCreation() {
        val uri = Uri.fromString("jami:abc123def456789012345678901234567890ab")
        val contact = Contact(uri)

        assertEquals(uri, contact.uri)
        assertFalse(contact.isUser)
        assertEquals(Contact.Status.NO_REQUEST, contact.status)
        assertEquals(Contact.PresenceStatus.OFFLINE, contact.presenceStatus.value)
        assertNull(contact.username)
        assertTrue(contact.phones.isEmpty())
    }

    @Test
    fun testContactAsUser() {
        val uri = Uri.fromString("jami:myownid12345678901234567890123456")
        val contact = Contact(uri, isUser = true)

        assertTrue(contact.isUser)
    }

    @Test
    fun testPresenceStatus() {
        val contact = Contact(Uri.fromString("jami:peer123"))

        assertFalse(contact.isOnline)
        assertEquals(Contact.PresenceStatus.OFFLINE, contact.presenceStatus.value)

        contact.setPresence(Contact.PresenceStatus.AVAILABLE)
        assertTrue(contact.isOnline)
        assertEquals(Contact.PresenceStatus.AVAILABLE, contact.presenceStatus.value)

        contact.setPresence(Contact.PresenceStatus.CONNECTED)
        assertTrue(contact.isOnline)
        assertEquals(Contact.PresenceStatus.CONNECTED, contact.presenceStatus.value)

        contact.setPresence(Contact.PresenceStatus.OFFLINE)
        assertFalse(contact.isOnline)
    }

    @Test
    fun testUsername() {
        val contact = Contact(Uri.fromString("jami:peer123"))

        assertNull(contact.username)

        contact.username = "alice"
        assertEquals("alice", contact.username)
        assertEquals("alice", contact.usernameFlow.value)
    }

    @Test
    fun testStatus() {
        val contact = Contact(Uri.fromString("jami:peer123"))

        assertFalse(contact.isBlocked)

        contact.status = Contact.Status.BLOCKED
        assertTrue(contact.isBlocked)

        contact.status = Contact.Status.CONFIRMED
        assertFalse(contact.isBlocked)
    }

    @Test
    fun testPhoneNumbers() {
        val contact = Contact(Uri.fromString("jami:peer123"))

        assertTrue(contact.phones.isEmpty())

        val phoneUri = Uri.fromString("sip:user@example.com")
        contact.addPhoneNumber(phoneUri, 1, "Work")
        assertEquals(1, contact.phones.size)
        assertEquals(phoneUri, contact.phones[0].number)
        assertEquals("Work", contact.phones[0].label)

        // Adding same number should not duplicate
        contact.addPhoneNumber(phoneUri, 2, "Home")
        assertEquals(1, contact.phones.size)

        // Adding different number should work
        contact.addNumber("sip:other@example.com", 1, "Mobile", Phone.NumberType.SIP)
        assertEquals(2, contact.phones.size)
    }

    @Test
    fun testAddNumberWithType() {
        val contact = Contact(Uri.fromString("jami:peer123"))

        contact.addNumber("sip:user@example.com", 1, "Work", Phone.NumberType.SIP)
        assertEquals(1, contact.phones.size)
        assertEquals(Phone.NumberType.SIP, contact.phones[0].type)

        contact.addNumber(Uri.fromString("jami:abc123"), 2, null, Phone.NumberType.JAMI)
        assertEquals(2, contact.phones.size)
        assertEquals(Phone.NumberType.JAMI, contact.phones[1].type)
    }

    @Test
    fun testProfile() = runTest {
        val contact = Contact(Uri.fromString("jami:peer123"))

        // Initial state
        assertEquals(Profile.EMPTY_PROFILE, contact.loadedProfile)

        // Set loaded profile
        val loadedProfile = Profile("Alice", null)
        contact.loadedProfile = loadedProfile
        assertEquals("Alice", contact.loadedProfile.displayName)

        // Set custom profile (should override)
        val customProfile = Profile("Alice Smith", byteArrayOf(1, 2, 3))
        contact.customProfile = customProfile

        // Get merged profile
        val mergedProfile = contact.profileFlow.first()
        assertEquals("Alice Smith", mergedProfile.displayName)
        assertTrue(mergedProfile.avatar?.contentEquals(byteArrayOf(1, 2, 3)) == true)
    }

    @Test
    fun testProfileMerging() = runTest {
        val contact = Contact(Uri.fromString("jami:peer123"))

        // Set loaded profile with displayName
        contact.loadedProfile = Profile("Bob", null)

        // Set custom profile with avatar only
        contact.customProfile = Profile(null, byteArrayOf(1, 2, 3))

        // Merged should have displayName from loaded and avatar from custom
        val mergedProfile = contact.profileFlow.first()
        assertEquals("Bob", mergedProfile.displayName)
        assertTrue(mergedProfile.avatar?.contentEquals(byteArrayOf(1, 2, 3)) == true)
    }

    @Test
    fun testConversationUri() = runTest {
        val originalUri = Uri.fromString("jami:peer123")
        val contact = Contact(originalUri)

        assertEquals(originalUri, contact.conversationUri.value)

        val swarmUri = Uri(Uri.SWARM_SCHEME, "conversation123")
        contact.setConversationUri(swarmUri)

        assertEquals(swarmUri, contact.conversationUri.value)
    }

    @Test
    fun testSystemContactInfo() {
        val contact = Contact(Uri.fromString("jami:peer123"))

        contact.setSystemContactInfo(
            id = 42L,
            key = "lookup_key_123",
            displayName = "System User",
            photoId = 100L
        )

        assertEquals(42L, contact.id)
        assertEquals(100L, contact.photoId)
        assertEquals("System User", contact.loadedProfile.displayName)
    }

    @Test
    fun testSystemContactInfoWithJamiUri() {
        val contact = Contact(Uri.fromString("jami:peer123"))

        // When display name starts with jami: or ring:, username should be set
        contact.setSystemContactInfo(
            id = 42L,
            key = null,
            displayName = "jami:abc123",
            photoId = 0L
        )

        assertEquals("jami:abc123", contact.username)
    }

    @Test
    fun testStared() {
        val contact = Contact(Uri.fromString("jami:peer123"))

        assertFalse(contact.isStared)

        contact.setStared()
        assertTrue(contact.isStared)
    }

    @Test
    fun testDisplayUsername() {
        val contact = Contact(Uri.fromString("jami:abc123def456789012345678901234567890ab"))

        // Without displayName or username, should return rawRingId
        assertEquals("abc123def456789012345678901234567890ab", contact.displayUsername)

        // With username
        contact.username = "alice"
        assertEquals("alice", contact.displayUsername)

        // With displayName (should take precedence)
        contact.loadedProfile = Profile("Alice Smith", null)
        assertEquals("Alice Smith", contact.displayUsername)
    }

    @Test
    fun testPrimaryNumber() {
        val contact = Contact(Uri.fromString("jami:abc123def456789012345678901234567890ab"))
        assertEquals("abc123def456789012345678901234567890ab", contact.primaryNumber)

        val sipContact = Contact(Uri.fromString("sip:user@example.com"))
        assertEquals("user", sipContact.primaryNumber)
    }

    @Test
    fun testIsJami() {
        val jamiContact = Contact(Uri.fromString("jami:abc123def456789012345678901234567890ab"))
        assertTrue(jamiContact.isJami)

        val sipContact = Contact(Uri.fromString("sip:user@example.com"))
        assertFalse(sipContact.isJami)
    }

    @Test
    fun testEquality() {
        val uri = Uri.fromString("jami:abc123")
        val contact1 = Contact(uri)
        val contact2 = Contact(uri)
        val contact3 = Contact(Uri.fromString("jami:def456"))

        assertEquals(contact1, contact2)
        assertNotEquals(contact1, contact3)
        assertEquals(contact1.hashCode(), contact2.hashCode())
    }

    @Test
    fun testCompanionFactoryMethods() {
        val contact1 = Contact.build("jami:abc123")
        assertEquals("jami:abc123", contact1.uri.uri)
        assertFalse(contact1.isUser)

        val contact2 = Contact.build("jami:def456", isUser = true)
        assertTrue(contact2.isUser)

        val contact3 = Contact.fromString("sip:user@example.com")
        assertEquals("sip:", contact3.uri.scheme)

        val uri = Uri.fromString("jami:ghi789")
        val contact4 = Contact.fromUri(uri)
        assertEquals(uri, contact4.uri)

        val sipContact = Contact.buildSIP(Uri.fromString("sip:test@sip.example.com"))
        assertEquals("", sipContact.username)
    }

    @Test
    fun testToString() {
        val contact = Contact(Uri.fromString("jami:abc123def456789012345678901234567890ab"))
        assertEquals("jami:abc123def456789012345678901234567890ab", contact.toString())
    }
}
