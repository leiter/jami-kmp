package net.jami.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InteractionTest {

    @Test
    fun testInteractionCreation() {
        val interaction = Interaction("account1")

        assertEquals("account1", interaction.account)
        assertEquals(Interaction.InteractionType.INVALID, interaction.type)
        assertEquals(Interaction.InteractionStatus.INVALID, interaction.status)
        assertFalse(interaction.isRead)
        assertFalse(interaction.isSwarm)
    }

    @Test
    fun testInteractionRead() {
        val interaction = Interaction("account1")

        assertFalse(interaction.isRead)
        interaction.read()
        assertTrue(interaction.isRead)
    }

    @Test
    fun testInteractionStatusSetsRead() {
        val interaction = Interaction("account1")

        assertFalse(interaction.isRead)
        interaction.status = Interaction.InteractionStatus.DISPLAYED
        assertTrue(interaction.isRead)
    }

    @Test
    fun testSwarmInfo() {
        val interaction = Interaction("account1")

        assertFalse(interaction.isSwarm)
        assertNull(interaction.messageId)
        assertNull(interaction.parentId)

        interaction.setSwarmInfo("conv123", "msg456", "parentMsg")

        assertTrue(interaction.isSwarm)
        assertEquals("conv123", interaction.conversationId)
        assertEquals("msg456", interaction.messageId)
        assertEquals("parentMsg", interaction.parentId)
    }

    @Test
    fun testSwarmInfoWithoutParent() {
        val interaction = Interaction("account1")

        interaction.setSwarmInfo("conv123")

        assertNull(interaction.messageId)
        assertNull(interaction.parentId)
        assertEquals("conv123", interaction.conversationId)
    }

    @Test
    fun testReactions() {
        val interaction = Interaction("account1")

        assertTrue(interaction.reactions.isEmpty())

        val reaction1 = Interaction("account1").apply {
            setSwarmInfo("conv", "react1", null)
        }
        interaction.addReaction(reaction1)
        assertEquals(1, interaction.reactions.size)

        val reaction2 = Interaction("account1").apply {
            setSwarmInfo("conv", "react2", null)
        }
        interaction.addReactions(listOf(reaction2))
        assertEquals(2, interaction.reactions.size)

        interaction.removeReaction("react1")
        assertEquals(1, interaction.reactions.size)

        interaction.replaceReactions(emptyList())
        assertTrue(interaction.reactions.isEmpty())
    }

    @Test
    fun testEdits() {
        val interaction = Interaction("account1")

        // Initially contains self
        assertEquals(1, interaction.history.size)

        val edit1 = Interaction("account1")
        interaction.addEdit(edit1, newMessage = true)
        assertEquals(2, interaction.history.size)

        val edit2 = Interaction("account1")
        interaction.addEdit(edit2, newMessage = false)
        // edit2 should be at the beginning
        assertEquals(edit2, interaction.history[0])

        interaction.replaceEdits(listOf(interaction))
        assertEquals(1, interaction.history.size)
    }

    @Test
    fun testInteractionTypes() {
        assertEquals(Interaction.InteractionType.TEXT, Interaction.InteractionType.fromString("TEXT"))
        assertEquals(Interaction.InteractionType.CALL, Interaction.InteractionType.fromString("CALL"))
        assertEquals(Interaction.InteractionType.CONTACT, Interaction.InteractionType.fromString("CONTACT"))
        assertEquals(Interaction.InteractionType.DATA_TRANSFER, Interaction.InteractionType.fromString("DATA_TRANSFER"))
        assertEquals(Interaction.InteractionType.INVALID, Interaction.InteractionType.fromString("UNKNOWN"))
    }

    @Test
    fun testInteractionStatus() {
        assertEquals(Interaction.InteractionStatus.UNKNOWN, Interaction.InteractionStatus.fromString("UNKNOWN"))
        assertEquals(Interaction.InteractionStatus.SENDING, Interaction.InteractionStatus.fromString("SENDING"))
        assertEquals(Interaction.InteractionStatus.SUCCESS, Interaction.InteractionStatus.fromString("SUCCESS"))
        assertEquals(Interaction.InteractionStatus.DISPLAYED, Interaction.InteractionStatus.fromString("DISPLAYED"))
        assertEquals(Interaction.InteractionStatus.INVALID, Interaction.InteractionStatus.fromString("INVALID"))
        assertEquals(Interaction.InteractionStatus.FAILURE, Interaction.InteractionStatus.fromString("FAILURE"))
        assertEquals(Interaction.InteractionStatus.INVALID, Interaction.InteractionStatus.fromString("bad_value"))
    }

    @Test
    fun testMessageStates() {
        assertEquals(Interaction.MessageStates.UNKNOWN, Interaction.MessageStates.fromInt(0))
        assertEquals(Interaction.MessageStates.SENDING, Interaction.MessageStates.fromInt(1))
        assertEquals(Interaction.MessageStates.SUCCESS, Interaction.MessageStates.fromInt(2))
        assertEquals(Interaction.MessageStates.DISPLAYED, Interaction.MessageStates.fromInt(3))
        assertEquals(Interaction.MessageStates.INVALID, Interaction.MessageStates.fromInt(4))
        assertEquals(Interaction.MessageStates.FAILURE, Interaction.MessageStates.fromInt(5))
        assertEquals(Interaction.MessageStates.CANCELLED, Interaction.MessageStates.fromInt(6))
        assertEquals(Interaction.MessageStates.INVALID, Interaction.MessageStates.fromInt(99))
    }

    @Test
    fun testTransferStatus() {
        assertEquals(Interaction.TransferStatus.INVALID, Interaction.TransferStatus.fromIntFile(0))
        assertEquals(Interaction.TransferStatus.TRANSFER_CREATED, Interaction.TransferStatus.fromIntFile(1))
        assertEquals(Interaction.TransferStatus.TRANSFER_ERROR, Interaction.TransferStatus.fromIntFile(2))
        assertEquals(Interaction.TransferStatus.TRANSFER_AWAITING_PEER, Interaction.TransferStatus.fromIntFile(3))
        assertEquals(Interaction.TransferStatus.TRANSFER_AWAITING_HOST, Interaction.TransferStatus.fromIntFile(4))
        assertEquals(Interaction.TransferStatus.TRANSFER_ONGOING, Interaction.TransferStatus.fromIntFile(5))
        assertEquals(Interaction.TransferStatus.TRANSFER_FINISHED, Interaction.TransferStatus.fromIntFile(6))
        assertEquals(Interaction.TransferStatus.TRANSFER_TIMEOUT_EXPIRED, Interaction.TransferStatus.fromIntFile(11))
    }

    @Test
    fun testTransferStatusProperties() {
        assertTrue(Interaction.TransferStatus.TRANSFER_ERROR.isError)
        assertTrue(Interaction.TransferStatus.TRANSFER_CANCELED.isError)
        assertTrue(Interaction.TransferStatus.TRANSFER_UNJOINABLE_PEER.isError)
        assertTrue(Interaction.TransferStatus.FAILURE.isError)
        assertFalse(Interaction.TransferStatus.TRANSFER_ONGOING.isError)

        assertTrue(Interaction.TransferStatus.TRANSFER_FINISHED.isOver)
        assertTrue(Interaction.TransferStatus.TRANSFER_ERROR.isOver)
        assertFalse(Interaction.TransferStatus.TRANSFER_ONGOING.isOver)
    }

    @Test
    fun testInteractionCompare() {
        val interaction1 = Interaction("account1").apply { timestamp = 1000L }
        val interaction2 = Interaction("account1").apply { timestamp = 2000L }

        assertTrue(Interaction.compare(interaction1, interaction2) < 0)
        assertTrue(Interaction.compare(interaction2, interaction1) > 0)
        assertEquals(0, Interaction.compare(interaction1, interaction1))
        assertTrue(Interaction.compare(null, interaction1) < 0)
        assertTrue(Interaction.compare(interaction1, null) > 0)
        assertEquals(0, Interaction.compare(null, null))
    }

    @Test
    fun testUpdateParent() {
        val interaction = Interaction("account1")
        interaction.setSwarmInfo("conv", "msg", "parent1")

        assertEquals("parent1", interaction.parentId)

        interaction.updateParent("parent2")
        assertEquals("parent2", interaction.parentId)
    }

    @Test
    fun testDaemonIdString() {
        val interaction = Interaction("account1")

        assertNull(interaction.daemonIdString)

        interaction.daemonId = 12345L
        assertEquals("12345", interaction.daemonIdString)
    }

    @Test
    fun testFullConstructor() {
        val conversation = ConversationHistory(1, "participant1")
        val interaction = Interaction(
            id = "42",
            author = "author1",
            conversation = conversation,
            timestamp = "1234567890",
            body = "Hello world",
            type = "TEXT",
            status = "SUCCESS",
            daemonId = "999",
            isRead = "1",
            extraFlag = "{}"
        )

        assertEquals(42, interaction.id)
        assertEquals("author1", interaction.author)
        assertEquals(conversation, interaction.conversation)
        assertEquals(1234567890L, interaction.timestamp)
        assertEquals("Hello world", interaction.body)
        assertEquals(Interaction.InteractionType.TEXT, interaction.type)
        assertEquals(Interaction.InteractionStatus.SUCCESS, interaction.status)
        assertEquals(999L, interaction.daemonId)
        assertTrue(interaction.isRead)
    }
}
