package net.jami.model

import kotlin.test.Test
import kotlin.test.assertEquals

class ContactEventTest {

    @Test
    fun testContactEventFromInteraction() {
        val interaction = Interaction("account1").apply {
            id = 42
            author = "jami:peer123"
            timestamp = 1000L
            status = Interaction.InteractionStatus.SUCCESS
        }

        val event = ContactEvent(interaction)

        assertEquals(42, event.id)
        assertEquals("jami:peer123", event.author)
        assertEquals(1000L, event.timestamp)
        assertEquals(Interaction.InteractionType.CONTACT, event.type)
        assertEquals(ContactEvent.Event.ADDED, event.event)
        assertEquals(1, event.mIsRead)
    }

    @Test
    fun testContactEventIncomingRequest() {
        val interaction = Interaction("account1").apply {
            status = Interaction.InteractionStatus.UNKNOWN
        }

        val event = ContactEvent(interaction)
        assertEquals(ContactEvent.Event.INCOMING_REQUEST, event.event)
    }

    @Test
    fun testContactEventFromTime() {
        val time = 1234567890000L
        val event = ContactEvent(time)

        assertEquals(time, event.timestamp)
        assertEquals(ContactEvent.Event.ADDED, event.event)
        assertEquals(Interaction.InteractionType.CONTACT, event.type)
        assertEquals(Interaction.InteractionStatus.SUCCESS, event.status)
    }

    @Test
    fun testContactEventFromContact() {
        val contact = Contact(Uri.fromString("jami:peer123"))
        contact.addedDate = 1000L

        val event = ContactEvent("account1", contact)

        assertEquals("account1", event.account)
        assertEquals(contact, event.contact)
        assertEquals("jami:peer123", event.author)
        assertEquals(1000L, event.timestamp)
        assertEquals(ContactEvent.Event.ADDED, event.event)
    }

    @Test
    fun testContactEventFromTrustRequest() {
        val contact = Contact(Uri.fromString("jami:peer123"))
        val request = TrustRequest(
            accountId = "account1",
            from = Uri.fromString("jami:peer123"),
            timestamp = 2000L,
            conversationUri = Uri(Uri.SWARM_SCHEME, "conv123"),
            profile = null,
            mode = Conversation.Mode.OneToOne
        )

        val event = ContactEvent("account1", contact, request)

        assertEquals("account1", event.account)
        assertEquals(request, event.request)
        assertEquals(contact, event.contact)
        assertEquals(2000L, event.timestamp)
        assertEquals(ContactEvent.Event.INCOMING_REQUEST, event.event)
        assertEquals(Interaction.InteractionStatus.UNKNOWN, event.status)
    }

    @Test
    fun testSetEvent() {
        val event = ContactEvent(1000L)

        assertEquals(ContactEvent.Event.ADDED, event.event)

        event.setEvent(ContactEvent.Event.BLOCKED)
        assertEquals(ContactEvent.Event.BLOCKED, event.event)
    }

    @Test
    fun testEventFromConversationAction() {
        assertEquals(ContactEvent.Event.INVITED, ContactEvent.Event.fromConversationAction("add"))
        assertEquals(ContactEvent.Event.ADDED, ContactEvent.Event.fromConversationAction("join"))
        assertEquals(ContactEvent.Event.REMOVED, ContactEvent.Event.fromConversationAction("remove"))
        assertEquals(ContactEvent.Event.BLOCKED, ContactEvent.Event.fromConversationAction("ban"))
        assertEquals(ContactEvent.Event.UNBLOCKED, ContactEvent.Event.fromConversationAction("unban"))
        assertEquals(ContactEvent.Event.UNKNOWN, ContactEvent.Event.fromConversationAction("invalid"))
    }
}
