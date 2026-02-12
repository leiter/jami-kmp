package net.jami.model

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConversationTest {

    @Test
    fun testConversationCreation() {
        val uri = Uri.fromString("swarm:conv123")
        val conversation = Conversation("account1", uri, mode = Conversation.Mode.OneToOne)

        assertEquals("account1", conversation.accountId)
        assertEquals(uri, conversation.uri)
        assertEquals(Conversation.Mode.OneToOne, conversation.mode)
        assertTrue(conversation.contacts.isEmpty())
    }

    @Test
    fun testConversationFromContact() {
        val contact = Contact(Uri.fromString("jami:peer123"))
        val conversation = Conversation("account1", contact)

        assertEquals("account1", conversation.accountId)
        assertEquals(contact.uri, conversation.uri)
        assertEquals(Conversation.Mode.Legacy, conversation.mode)
        assertEquals(1, conversation.contacts.size)
        assertEquals(contact, conversation.contact)
    }

    @Test
    fun testIsSwarm() {
        val swarmConv = Conversation(
            "account1",
            Uri(Uri.SWARM_SCHEME, "conv123"),
            mode = Conversation.Mode.OneToOne
        )
        assertTrue(swarmConv.isSwarm)

        val legacyContact = Contact(Uri.fromString("jami:peer123"))
        val legacyConv = Conversation("account1", legacyContact)
        assertFalse(legacyConv.isSwarm)
    }

    @Test
    fun testIsLegacy() {
        val contact = Contact(Uri.fromString("jami:peer123"))
        val conversation = Conversation("account1", contact)

        assertTrue(conversation.isLegacy)
        assertEquals(Conversation.Mode.Legacy, conversation.mode)
    }

    @Test
    fun testAddContact() = runTest {
        val conversation = Conversation(
            "account1",
            Uri(Uri.SWARM_SCHEME, "conv123"),
            mode = Conversation.Mode.InvitesOnly
        )

        val contact = Contact(Uri.fromString("jami:peer123"))
        conversation.addContact(contact, MemberRole.MEMBER)

        assertEquals(1, conversation.contacts.size)
        assertEquals(contact, conversation.contacts[0])
        assertEquals(MemberRole.MEMBER, conversation.roles[contact.uri.uri])

        // Verify flow emission
        val contactList = conversation.contactUpdates.first()
        assertEquals(1, contactList.size)
    }

    @Test
    fun testAddBlockedContactNotAdded() {
        val conversation = Conversation(
            "account1",
            Uri(Uri.SWARM_SCHEME, "conv123"),
            mode = Conversation.Mode.InvitesOnly
        )

        val contact = Contact(Uri.fromString("jami:peer123"))
        conversation.addContact(contact, MemberRole.BLOCKED)

        // Blocked contacts should not be added to the list
        assertTrue(conversation.contacts.isEmpty())
        // But the role should be tracked
        assertEquals(MemberRole.BLOCKED, conversation.roles[contact.uri.uri])
    }

    @Test
    fun testRemoveContact() = runTest {
        val conversation = Conversation(
            "account1",
            Uri(Uri.SWARM_SCHEME, "conv123"),
            mode = Conversation.Mode.InvitesOnly
        )

        val contact = Contact(Uri.fromString("jami:peer123"))
        conversation.addContact(contact, MemberRole.MEMBER)
        assertEquals(1, conversation.contacts.size)

        conversation.removeContact(contact, MemberRole.LEFT)
        assertTrue(conversation.contacts.isEmpty())
        assertEquals(MemberRole.LEFT, conversation.roles[contact.uri.uri])
    }

    @Test
    fun testFindContact() {
        val conversation = Conversation(
            "account1",
            Uri(Uri.SWARM_SCHEME, "conv123"),
            mode = Conversation.Mode.InvitesOnly
        )

        val uri = Uri.fromString("jami:peer123")
        val contact = Contact(uri)
        conversation.addContact(contact, MemberRole.MEMBER)

        assertEquals(contact, conversation.findContact(uri))
        assertNull(conversation.findContact(Uri.fromString("jami:other")))
    }

    @Test
    fun testSetMode() = runTest {
        val conversation = Conversation(
            "account1",
            Uri(Uri.SWARM_SCHEME, "conv123"),
            mode = Conversation.Mode.Syncing
        )

        assertEquals(Conversation.Mode.Syncing, conversation.mode)
        assertTrue(conversation.isSyncing)

        conversation.setMode(Conversation.Mode.OneToOne)
        assertEquals(Conversation.Mode.OneToOne, conversation.mode)
        assertFalse(conversation.isSyncing)
    }

    @Test
    fun testComposingStatus() = runTest {
        val conversation = Conversation(
            "account1",
            Uri(Uri.SWARM_SCHEME, "conv123"),
            mode = Conversation.Mode.OneToOne
        )

        assertEquals(Conversation.ComposingStatus.Idle, conversation.composingStatusFlow.value)

        val contact = Contact(Uri.fromString("jami:peer123"))
        conversation.composingStatusChanged(contact, Conversation.ComposingStatus.Active)

        assertEquals(Conversation.ComposingStatus.Active, conversation.composingStatusFlow.value)
    }

    @Test
    fun testProfile() = runTest {
        val conversation = Conversation(
            "account1",
            Uri(Uri.SWARM_SCHEME, "conv123"),
            mode = Conversation.Mode.OneToOne
        )

        assertEquals(Profile.EMPTY_PROFILE, conversation.profileFlow.value)

        val profile = Profile("Group Name", null)
        conversation.setProfile(profile)

        assertEquals(profile, conversation.profileFlow.value)
    }

    @Test
    fun testPreferences() = runTest {
        val conversation = Conversation(
            "account1",
            Uri(Uri.SWARM_SCHEME, "conv123"),
            mode = Conversation.Mode.OneToOne
        )

        assertEquals(0, conversation.colorFlow.value)
        assertEquals("", conversation.symbolFlow.value)
        assertTrue(conversation.notificationEnabledFlow.value)

        conversation.updatePreferences(mapOf(
            Conversation.KEY_PREFERENCE_CONVERSATION_COLOR to "#FF5500",
            Conversation.KEY_PREFERENCE_CONVERSATION_SYMBOL to "🎉",
            Conversation.KEY_PREFERENCE_CONVERSATION_NOTIFICATION to "false"
        ))

        // Color should have alpha added
        val expectedColor = 0xFF5500.or(0xFF000000.toInt())
        assertEquals(expectedColor, conversation.colorFlow.value)
        assertEquals("🎉", conversation.symbolFlow.value)
        assertFalse(conversation.notificationEnabledFlow.value)
    }

    @Test
    fun testVisibility() = runTest {
        val conversation = Conversation(
            "account1",
            Uri(Uri.SWARM_SCHEME, "conv123"),
            mode = Conversation.Mode.OneToOne
        )

        assertFalse(conversation.isVisible)

        conversation.isVisible = true
        assertTrue(conversation.isVisible)
        assertTrue(conversation.visibleFlow.value)
    }

    @Test
    fun testConferenceManagement() = runTest {
        val conversation = Conversation(
            "account1",
            Uri(Uri.SWARM_SCHEME, "conv123"),
            mode = Conversation.Mode.OneToOne
        )

        assertNull(conversation.currentCall)

        val call = Call("account1", "call123", Uri.fromString("jami:peer"), false)
        val conference = Conference(call)
        conversation.addConference(conference)

        assertNotNull(conversation.currentCall)
        assertEquals(conference, conversation.currentCall)
        assertEquals(1, conversation.callsFlow.value.size)

        // Verify getConference
        assertEquals(conference, conversation.getConference(conference.id))

        conversation.removeConference(conference)
        assertNull(conversation.currentCall)
        assertTrue(conversation.callsFlow.value.isEmpty())
    }

    @Test
    fun testAddElement() = runTest {
        val conversation = Conversation(
            "account1",
            Uri(Uri.SWARM_SCHEME, "conv123"),
            mode = Conversation.Mode.OneToOne
        )

        val interaction = Interaction("account1").apply {
            type = Interaction.InteractionType.TEXT
            body = "Hello"
            timestamp = 1000L
        }

        conversation.addElement(interaction)

        val history = conversation.getSortedHistory()
        assertEquals(1, history.size)
        assertEquals(interaction, history[0])
    }

    @Test
    fun testLastEvent() = runTest {
        val conversation = Conversation(
            "account1",
            Uri(Uri.SWARM_SCHEME, "conv123"),
            mode = Conversation.Mode.OneToOne
        )

        assertNull(conversation.lastEvent)

        val interaction = Interaction("account1").apply {
            type = Interaction.InteractionType.TEXT
            body = "Hello"
        }
        conversation.lastEvent = interaction

        assertEquals(interaction, conversation.lastEvent)
        assertEquals(interaction, conversation.lastEventFlow.value)
    }

    @Test
    fun testIsGroup() {
        val conversation = Conversation(
            "account1",
            Uri(Uri.SWARM_SCHEME, "conv123"),
            mode = Conversation.Mode.InvitesOnly
        )

        // Less than 3 contacts, not a group
        assertFalse(conversation.isGroup)

        // Add 3 contacts
        conversation.addContact(Contact(Uri.fromString("jami:user1"), isUser = true))
        conversation.addContact(Contact(Uri.fromString("jami:peer1")))
        conversation.addContact(Contact(Uri.fromString("jami:peer2")))

        assertTrue(conversation.isGroup)
    }

    @Test
    fun testEquality() {
        val uri = Uri(Uri.SWARM_SCHEME, "conv123")
        val conv1 = Conversation("account1", uri, mode = Conversation.Mode.OneToOne)
        val conv2 = Conversation("account1", uri, mode = Conversation.Mode.OneToOne)
        val conv3 = Conversation("account2", uri, mode = Conversation.Mode.OneToOne)

        assertEquals(conv1, conv2)
        assertEquals(conv1.hashCode(), conv2.hashCode())
        assertFalse(conv1 == conv3)
    }

    @Test
    fun testMemberRoleFromString() {
        assertEquals(MemberRole.ADMIN, MemberRole.fromString("admin"))
        assertEquals(MemberRole.MEMBER, MemberRole.fromString("member"))
        assertEquals(MemberRole.INVITED, MemberRole.fromString("invited"))
        assertEquals(MemberRole.BLOCKED, MemberRole.fromString("banned"))
        assertEquals(MemberRole.LEFT, MemberRole.fromString("left"))
        assertEquals(MemberRole.UNKNOWN, MemberRole.fromString(""))
        assertEquals(MemberRole.UNKNOWN, MemberRole.fromString("invalid"))
    }

    @Test
    fun testModeProperties() {
        assertTrue(Conversation.Mode.OneToOne.isSwarm)
        assertTrue(Conversation.Mode.InvitesOnly.isSwarm)
        assertTrue(Conversation.Mode.Public.isSwarm)
        assertFalse(Conversation.Mode.Legacy.isSwarm)
        assertFalse(Conversation.Mode.Syncing.isSwarm)

        assertTrue(Conversation.Mode.AdminInvitesOnly.isGroup)
        assertTrue(Conversation.Mode.InvitesOnly.isGroup)
        assertTrue(Conversation.Mode.Public.isGroup)
        assertFalse(Conversation.Mode.OneToOne.isGroup)
    }

    @Test
    fun testActiveCall() {
        val activeCall = Conversation.ActiveCall("conf123", "jami:peer", "device1")
        assertEquals("conf123", activeCall.confId)
        assertEquals("jami:peer", activeCall.uri)
        assertEquals("device1", activeCall.device)

        val fromMap = Conversation.ActiveCall(mapOf(
            "id" to "conf456",
            "uri" to "jami:peer2",
            "device" to "device2"
        ))
        assertEquals("conf456", fromMap.confId)
        assertEquals("jami:peer2", fromMap.uri)
        assertEquals("device2", fromMap.device)
    }
}
