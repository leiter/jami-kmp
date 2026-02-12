package net.jami.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileTest {

    @Test
    fun testProfileCreation() {
        val avatar = byteArrayOf(1, 2, 3, 4, 5)
        val profile = Profile("Alice", avatar, "Hello!")

        assertEquals("Alice", profile.displayName)
        assertTrue(profile.avatar?.contentEquals(avatar) == true)
        assertEquals("Hello!", profile.description)
    }

    @Test
    fun testProfileWithNulls() {
        val profile = Profile(null, null, null)

        assertNull(profile.displayName)
        assertNull(profile.avatar)
        assertNull(profile.description)
    }

    @Test
    fun testEmptyProfile() {
        val empty = Profile.EMPTY_PROFILE

        assertNull(empty.displayName)
        assertNull(empty.avatar)
        assertNull(empty.description)
    }

    @Test
    fun testProfileMergeWith() {
        val primary = Profile("Alice", byteArrayOf(1, 2, 3), "Primary description")
        val custom = Profile("Alice Smith", null, null)

        val merged = primary.mergeWith(custom)

        // Custom values should override primary where non-null
        assertEquals("Alice Smith", merged.displayName)
        // Avatar should come from primary since custom is null
        assertTrue(merged.avatar?.contentEquals(byteArrayOf(1, 2, 3)) == true)
        // Description should come from primary since custom is null
        assertEquals("Primary description", merged.description)
    }

    @Test
    fun testProfileMergeWithFullCustom() {
        val primary = Profile("Alice", byteArrayOf(1, 2, 3), "Primary")
        val custom = Profile("Bob", byteArrayOf(4, 5, 6), "Custom")

        val merged = primary.mergeWith(custom)

        assertEquals("Bob", merged.displayName)
        assertTrue(merged.avatar?.contentEquals(byteArrayOf(4, 5, 6)) == true)
        assertEquals("Custom", merged.description)
    }

    @Test
    fun testProfileToString() {
        val profile = Profile("Alice", byteArrayOf(1, 2, 3))
        val str = profile.toString()

        assertTrue(str.contains("Alice"))
        assertTrue(str.contains("hasAvatar=true"))

        val noAvatarProfile = Profile("Bob", null)
        val noAvatarStr = noAvatarProfile.toString()
        assertTrue(noAvatarStr.contains("hasAvatar=false"))
    }
}

class ContactViewModelTest {

    @Test
    fun testContactViewModelCreation() {
        val contact = Contact(Uri.fromString("jami:abc123"))
        val profile = Profile("Alice", null)
        val vm = ContactViewModel(contact, profile, "alice", Contact.PresenceStatus.AVAILABLE)

        assertEquals(contact, vm.contact)
        assertEquals(profile, vm.profile)
        assertEquals("alice", vm.registeredName)
        assertEquals(Contact.PresenceStatus.AVAILABLE, vm.presence)
    }

    @Test
    fun testDisplayUri() {
        val contact = Contact(Uri.fromString("jami:abc123"))
        val profile = Profile("Alice", null)

        // With registered name
        val vm1 = ContactViewModel(contact, profile, "alice")
        assertEquals("alice", vm1.displayUri)

        // Without registered name
        val vm2 = ContactViewModel(contact, profile)
        assertEquals("jami:abc123", vm2.displayUri)
    }

    @Test
    fun testDisplayName() {
        val contact = Contact(Uri.fromString("jami:abc123"))

        // With profile display name
        val vm1 = ContactViewModel(contact, Profile("Alice Smith", null), "alice")
        assertEquals("Alice Smith", vm1.displayName)

        // With blank profile display name, falls back to displayUri
        val vm2 = ContactViewModel(contact, Profile("", null), "alice")
        assertEquals("alice", vm2.displayName)

        // With null profile display name, falls back to displayUri
        val vm3 = ContactViewModel(contact, Profile(null, null), "alice")
        assertEquals("alice", vm3.displayName)
    }

    @Test
    fun testMatches() {
        val contact = Contact(Uri.fromString("jami:abc123xyz"))
        val profile = Profile("Alice Smith", null)
        val vm = ContactViewModel(contact, profile, "alicesmith")

        // Match on display name
        assertTrue(vm.matches("alice"))
        assertTrue(vm.matches("ALICE")) // case insensitive
        assertTrue(vm.matches("smith"))

        // Match on registered name
        assertTrue(vm.matches("alicesmith"))

        // Match on URI
        assertTrue(vm.matches("abc123"))
        assertTrue(vm.matches("xyz"))

        // No match
        assertFalse(vm.matches("bob"))
        assertFalse(vm.matches("unknown"))
    }

    @Test
    fun testEmptyViewModel() {
        val empty = ContactViewModel.EMPTY

        assertEquals("", empty.contact.uri.host)
        assertEquals(Profile.EMPTY_PROFILE, empty.profile)
        assertNull(empty.registeredName)
        assertEquals(Contact.PresenceStatus.OFFLINE, empty.presence)
    }

    @Test
    fun testToString() {
        val contact = Contact(Uri.fromString("jami:abc123"))
        val profile = Profile("Alice", null)
        val vm = ContactViewModel(contact, profile, "alice")

        assertEquals("alice", vm.toString())
    }
}
