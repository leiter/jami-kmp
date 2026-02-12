/*
 *  Copyright (C) 2004-2025 Savoir-faire Linux Inc.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package net.jami.services

import kotlinx.coroutines.test.runTest
import net.jami.database.JamiDatabase
import net.jami.model.Conversation
import net.jami.model.Interaction
import net.jami.model.Uri
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for SqlDelightHistoryService.
 *
 * These tests use the platform-specific test driver:
 * - JVM/Desktop: JdbcSqliteDriver with in-memory database
 * - Native (iOS/macOS): NativeSqliteDriver with in-memory database
 * - Android Unit Tests / JS: Tests are skipped (no driver available)
 */
class SqlDelightHistoryServiceTest {

    /**
     * Creates a test database if the driver is available.
     * Returns null if the platform doesn't support SQLite in tests.
     *
     * Note: The test driver already has the schema created,
     * so we don't need to call JamiDatabase.Schema.create().
     */
    private fun createTestDatabase(): JamiDatabase? {
        return try {
            val driver = createTestDriver()
            // Schema is already created by createTestDriver()
            JamiDatabase(driver)
        } catch (e: UnsupportedOperationException) {
            // Driver not available on this platform (Android unit tests, JS)
            null
        }
    }

    /**
     * Helper to run a database test, skipping if driver unavailable.
     */
    private suspend fun withDatabase(
        block: suspend (database: JamiDatabase, service: SqlDelightHistoryService) -> Unit
    ) {
        val database = createTestDatabase() ?: return // Skip if no driver
        val service = SqlDelightHistoryService(database)
        block(database, service)
    }

    // ==================== Insert Tests ====================

    @Test
    fun testInsertInteraction() = runTest {
        withDatabase { database, service ->
            val conversation = createTestConversation()
            val interaction = createTestInteraction(
                type = Interaction.InteractionType.TEXT,
                body = "Hello, World!"
            )

            setupConversation(database, conversation)
            service.insertInteraction(TEST_ACCOUNT_ID, conversation, interaction)

            // Verify insertion
            assertTrue(interaction.id > 0)

            val retrieved = service.getInteractionById(interaction.id.toLong())
            assertNotNull(retrieved)
            assertEquals("Hello, World!", retrieved.body)
            assertEquals(Interaction.InteractionType.TEXT, retrieved.type)
        }
    }

    @Test
    fun testInsertMultipleInteractions() = runTest {
        withDatabase { database, service ->
            val conversation = createTestConversation()
            setupConversation(database, conversation)

            // Insert multiple messages
            val messages = listOf("Message 1", "Message 2", "Message 3")
            messages.forEachIndexed { index, body ->
                val interaction = createTestInteraction(
                    type = Interaction.InteractionType.TEXT,
                    body = body,
                    timestamp = 1000L + index * 1000
                )
                service.insertInteraction(TEST_ACCOUNT_ID, conversation, interaction)
            }

            // Retrieve history
            val history = service.getConversationHistory(TEST_ACCOUNT_ID, conversation.uri.uri)
            assertEquals(3, history.size)
            assertEquals("Message 1", history[0].body)
            assertEquals("Message 3", history[2].body)
        }
    }

    // ==================== Query Tests ====================

    @Test
    fun testGetConversationHistory() = runTest {
        withDatabase { database, service ->
            val conversation = createTestConversation()
            setupConversation(database, conversation)

            // Insert interactions
            repeat(5) { i ->
                val interaction = createTestInteraction(
                    type = Interaction.InteractionType.TEXT,
                    body = "Message $i",
                    timestamp = 1000L + i * 1000
                )
                service.insertInteraction(TEST_ACCOUNT_ID, conversation, interaction)
            }

            val history = service.getConversationHistory(TEST_ACCOUNT_ID, conversation.uri.uri)
            assertEquals(5, history.size)

            // Verify order (ascending by timestamp)
            for (i in 0 until history.size - 1) {
                assertTrue(history[i].timestamp <= history[i + 1].timestamp)
            }
        }
    }

    @Test
    fun testGetConversationHistoryPaged() = runTest {
        withDatabase { database, service ->
            val conversation = createTestConversation()
            setupConversation(database, conversation)

            // Insert 10 interactions
            repeat(10) { i ->
                val interaction = createTestInteraction(
                    type = Interaction.InteractionType.TEXT,
                    body = "Message $i",
                    timestamp = 1000L + i * 1000
                )
                service.insertInteraction(TEST_ACCOUNT_ID, conversation, interaction)
            }

            // Get first page (5 items)
            val page1 = service.getConversationHistoryPaged(
                TEST_ACCOUNT_ID, conversation.uri.uri, limit = 5, offset = 0
            )
            assertEquals(5, page1.size)

            // Get second page
            val page2 = service.getConversationHistoryPaged(
                TEST_ACCOUNT_ID, conversation.uri.uri, limit = 5, offset = 5
            )
            assertEquals(5, page2.size)

            // Verify no overlap
            val allIds = (page1 + page2).map { it.id }
            assertEquals(10, allIds.toSet().size)
        }
    }

    @Test
    fun testGetSmartlist() = runTest {
        withDatabase { database, service ->
            // Create two conversations
            val conv1 = createTestConversation(uri = "conv1")
            val conv2 = createTestConversation(uri = "conv2")

            setupConversation(database, conv1)
            setupConversation(database, conv2)

            // Insert messages in each conversation
            val interaction1 = createTestInteraction(body = "Last in conv1", timestamp = 2000L)
            service.insertInteraction(TEST_ACCOUNT_ID, conv1, interaction1)

            val interaction2 = createTestInteraction(body = "Last in conv2", timestamp = 3000L)
            service.insertInteraction(TEST_ACCOUNT_ID, conv2, interaction2)

            val smartlist = service.getSmartlist(TEST_ACCOUNT_ID)
            assertEquals(2, smartlist.size)

            // Most recent should be first
            assertEquals("Last in conv2", smartlist[0].body)
            assertEquals("Last in conv1", smartlist[1].body)
        }
    }

    @Test
    fun testGetLastInteraction() = runTest {
        withDatabase { database, service ->
            val conversation = createTestConversation()
            setupConversation(database, conversation)

            // Insert multiple messages
            repeat(5) { i ->
                val interaction = createTestInteraction(
                    body = "Message $i",
                    timestamp = 1000L + i * 1000
                )
                service.insertInteraction(TEST_ACCOUNT_ID, conversation, interaction)
            }

            val last = service.getLastInteraction(conversation.uri.uri, TEST_ACCOUNT_ID)
            assertNotNull(last)
            assertEquals("Message 4", last.body)
        }
    }

    // ==================== Update Tests ====================

    @Test
    fun testUpdateInteractionStatus() = runTest {
        withDatabase { database, service ->
            val conversation = createTestConversation()
            setupConversation(database, conversation)

            val interaction = createTestInteraction(
                body = "Test message",
                status = Interaction.InteractionStatus.SENDING
            )
            service.insertInteraction(TEST_ACCOUNT_ID, conversation, interaction)

            // Update status
            interaction.status = Interaction.InteractionStatus.SUCCESS
            service.updateInteraction(interaction, TEST_ACCOUNT_ID)

            // Verify
            val retrieved = service.getInteractionById(interaction.id.toLong())
            assertNotNull(retrieved)
            assertEquals(Interaction.InteractionStatus.SUCCESS, retrieved.status)
        }
    }

    @Test
    fun testMarkAsRead() = runTest {
        withDatabase { database, service ->
            val conversation = createTestConversation()
            setupConversation(database, conversation)

            // Verify initially no unread messages
            assertEquals(0, service.getUnreadCount(conversation.uri.uri, TEST_ACCOUNT_ID).toInt())

            // Insert unread incoming messages using direct database insertion
            repeat(3) { i ->
                database.interactionQueries.insert(
                    daemon_id = "incoming$i",
                    account_id = TEST_ACCOUNT_ID,
                    conversation_id = conversation.uri.uri,
                    author = TEST_PARTICIPANT,
                    timestamp = 1000L + i,
                    type = "TEXT",
                    status = "SUCCESS",
                    body = "Incoming message $i",
                    is_read = 0,
                    is_notified = 0,
                    extra_data = null,
                    parent_id = null,
                    duration = null,
                    transfer_status = null,
                    file_path = null,
                    display_name = null
                )
            }

            assertEquals(3, service.getUnreadCount(conversation.uri.uri, TEST_ACCOUNT_ID).toInt())

            // Mark all as read
            service.markAllAsRead(conversation.uri.uri, TEST_ACCOUNT_ID)

            assertEquals(0, service.getUnreadCount(conversation.uri.uri, TEST_ACCOUNT_ID).toInt())
        }
    }

    @Test
    fun testUpdateInteractionBody() = runTest {
        withDatabase { database, service ->
            val conversation = createTestConversation()
            setupConversation(database, conversation)

            val interaction = createTestInteraction(body = "Original message")
            service.insertInteraction(TEST_ACCOUNT_ID, conversation, interaction)

            // Update body
            service.updateInteractionBody(interaction.id.toLong(), "Edited message")

            // Verify
            val retrieved = service.getInteractionById(interaction.id.toLong())
            assertNotNull(retrieved)
            assertEquals("Edited message", retrieved.body)
        }
    }

    // ==================== Delete Tests ====================

    @Test
    fun testDeleteInteraction() = runTest {
        withDatabase { database, service ->
            val conversation = createTestConversation()
            setupConversation(database, conversation)

            val interaction = createTestInteraction(body = "To be deleted")
            service.insertInteraction(TEST_ACCOUNT_ID, conversation, interaction)

            assertNotNull(service.getInteractionById(interaction.id.toLong()))

            service.deleteInteraction(interaction.id.toLong(), TEST_ACCOUNT_ID)

            assertNull(service.getInteractionById(interaction.id.toLong()))
        }
    }

    @Test
    fun testClearConversationHistory() = runTest {
        withDatabase { database, service ->
            val conversation = createTestConversation()
            setupConversation(database, conversation)

            // Insert messages
            repeat(5) { i ->
                val interaction = createTestInteraction(body = "Message $i")
                service.insertInteraction(TEST_ACCOUNT_ID, conversation, interaction)
            }

            val historyBefore = service.getConversationHistory(TEST_ACCOUNT_ID, conversation.uri.uri)
            assertEquals(5, historyBefore.size)

            // Clear history (keep conversation)
            service.clearHistory(conversation.uri.uri, TEST_ACCOUNT_ID, deleteConversation = false)

            val historyAfter = service.getConversationHistory(TEST_ACCOUNT_ID, conversation.uri.uri)
            assertTrue(historyAfter.isEmpty())

            // Conversation should still exist
            val convResult = database.conversationQueries
                .selectById(conversation.uri.uri, TEST_ACCOUNT_ID)
                .executeAsOneOrNull()
            assertNotNull(convResult)
        }
    }

    @Test
    fun testClearHistoryAndDeleteConversation() = runTest {
        withDatabase { database, service ->
            val conversation = createTestConversation()
            setupConversation(database, conversation)

            // Insert messages
            repeat(3) { i ->
                val interaction = createTestInteraction(body = "Message $i")
                service.insertInteraction(TEST_ACCOUNT_ID, conversation, interaction)
            }

            // Clear history and delete conversation
            service.clearHistory(conversation.uri.uri, TEST_ACCOUNT_ID, deleteConversation = true)

            // Both history and conversation should be gone
            val historyAfter = service.getConversationHistory(TEST_ACCOUNT_ID, conversation.uri.uri)
            assertTrue(historyAfter.isEmpty())

            val convResult = database.conversationQueries
                .selectById(conversation.uri.uri, TEST_ACCOUNT_ID)
                .executeAsOneOrNull()
            assertNull(convResult)
        }
    }

    // ==================== Notification Tests ====================

    @Test
    fun testSetMessageNotified() = runTest {
        withDatabase { database, service ->
            val conversation = createTestConversation()
            setupConversation(database, conversation)

            val messageId = "test-daemon-id-123"
            database.interactionQueries.insert(
                daemon_id = messageId,
                account_id = TEST_ACCOUNT_ID,
                conversation_id = conversation.uri.uri,
                author = TEST_PARTICIPANT,
                timestamp = 1000L,
                type = "TEXT",
                status = "SUCCESS",
                body = "Test message",
                is_read = 0,
                is_notified = 0,
                extra_data = null,
                parent_id = null,
                duration = null,
                transfer_status = null,
                file_path = null,
                display_name = null
            )

            // Mark as notified
            service.setMessageNotified(TEST_ACCOUNT_ID, conversation.uri, messageId)

            // Verify
            val lastNotified = service.getLastMessageNotified(TEST_ACCOUNT_ID, conversation.uri)
            assertEquals(messageId, lastNotified)
        }
    }

    // ==================== Type Conversion Tests ====================

    @Test
    fun testInteractionTypeConversion() = runTest {
        withDatabase { database, service ->
            val conversation = createTestConversation()
            setupConversation(database, conversation)

            // Test each interaction type
            val types = listOf(
                Interaction.InteractionType.TEXT,
                Interaction.InteractionType.CALL,
                Interaction.InteractionType.CONTACT,
                Interaction.InteractionType.DATA_TRANSFER
            )

            types.forEach { type ->
                val interaction = createTestInteraction(type = type, body = "Type: ${type.name}")
                service.insertInteraction(TEST_ACCOUNT_ID, conversation, interaction)

                val retrieved = service.getInteractionById(interaction.id.toLong())
                assertNotNull(retrieved)
                assertEquals(type, retrieved.type)
            }
        }
    }

    @Test
    fun testInteractionStatusConversion() = runTest {
        withDatabase { database, service ->
            val conversation = createTestConversation()
            setupConversation(database, conversation)

            val statuses = listOf(
                Interaction.InteractionStatus.UNKNOWN,
                Interaction.InteractionStatus.SENDING,
                Interaction.InteractionStatus.SUCCESS,
                Interaction.InteractionStatus.DISPLAYED,
                Interaction.InteractionStatus.FAILURE
            )

            statuses.forEach { status ->
                val interaction = createTestInteraction(
                    body = "Status: ${status.name}",
                    status = status
                )
                service.insertInteraction(TEST_ACCOUNT_ID, conversation, interaction)

                val retrieved = service.getInteractionById(interaction.id.toLong())
                assertNotNull(retrieved)
                assertEquals(status, retrieved.status)
            }
        }
    }

    // ==================== Helper Functions ====================

    private fun createTestConversation(uri: String = TEST_CONVERSATION_ID): Conversation {
        return Conversation(
            accountId = TEST_ACCOUNT_ID,
            uri = Uri.fromId(uri)
        )
    }

    private fun createTestInteraction(
        type: Interaction.InteractionType = Interaction.InteractionType.TEXT,
        body: String = "Test message",
        status: Interaction.InteractionStatus = Interaction.InteractionStatus.SUCCESS,
        timestamp: Long = 1000L,
        isIncoming: Boolean = false
    ): Interaction {
        return Interaction().apply {
            this.type = type
            this.body = body
            this.status = status
            this.timestamp = timestamp
            this.isIncoming = isIncoming
            this.author = if (isIncoming) TEST_PARTICIPANT else TEST_ACCOUNT_ID
        }
    }

    private fun setupConversation(database: JamiDatabase, conversation: Conversation) {
        database.conversationQueries.insert(
            id = conversation.uri.uri,
            account_id = TEST_ACCOUNT_ID,
            participant = TEST_PARTICIPANT,
            mode = "ONE_TO_ONE",
            extra_data = null,
            last_event_timestamp = 1000L,
            is_syncing = 0,
            created_at = 1000L
        )
    }

    companion object {
        private const val TEST_ACCOUNT_ID = "test-account-123"
        private const val TEST_CONVERSATION_ID = "test-conversation-456"
        private const val TEST_PARTICIPANT = "jami:test-participant-789"
    }
}
