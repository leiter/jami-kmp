package net.jami.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TextMessageTest {

    @Test
    fun testTextMessageCreation() {
        val conversation = ConversationHistory(1, "participant1")
        val message = TextMessage(
            author = "author1",
            account = "account1",
            daemonId = "12345",
            conversation = conversation,
            message = "Hello world"
        )

        assertEquals("author1", message.author)
        assertEquals("account1", message.account)
        assertEquals(12345L, message.daemonId)
        assertEquals(conversation, message.conversation)
        assertEquals("Hello world", message.body)
        assertEquals(Interaction.InteractionType.TEXT, message.type)
        assertTrue(message.isIncoming)
        assertTrue(message.timestamp > 0)
    }

    @Test
    fun testTextMessageWithHexDaemonId() {
        val message = TextMessage(
            author = "author1",
            account = "account1",
            daemonId = "FF",
            conversation = null,
            message = "Test"
        )

        assertEquals(255L, message.daemonId)
    }

    @Test
    fun testTextMessageOutgoing() {
        val message = TextMessage(
            author = null,
            account = "account1",
            daemonId = null,
            conversation = null,
            message = "Outgoing message"
        )

        assertFalse(message.isIncoming)
    }

    @Test
    fun testTextMessageWithTimestamp() {
        val timestamp = 1234567890000L
        val message = TextMessage(
            author = "author1",
            account = "account1",
            timestamp = timestamp,
            conversation = null,
            message = "Test message",
            isIncoming = true,
            replyToId = "parent123"
        )

        assertEquals(timestamp, message.timestamp)
        assertEquals("parent123", message.replyToId)
        assertTrue(message.isIncoming)
    }

    @Test
    fun testTextMessageFromInteraction() {
        val interaction = Interaction("account1").apply {
            id = 42
            author = "author1"
            timestamp = 1000L
            body = "Original message"
            status = Interaction.InteractionStatus.SUCCESS
            daemonId = 999L
            mIsRead = 1
        }

        val message = TextMessage(interaction)

        assertEquals(42, message.id)
        assertEquals("author1", message.author)
        assertEquals(1000L, message.timestamp)
        assertEquals("Original message", message.body)
        assertEquals(Interaction.InteractionType.TEXT, message.type)
        assertEquals(Interaction.InteractionStatus.SUCCESS, message.status)
        assertEquals(999L, message.daemonId)
        assertTrue(message.isRead)
        assertTrue(message.isIncoming)
    }
}
