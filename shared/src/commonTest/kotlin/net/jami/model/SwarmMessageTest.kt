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
package net.jami.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SwarmMessageTest {

    @Test
    fun testTextMessage() {
        val message = SwarmMessage(
            id = "msg123",
            type = "text/plain",
            linearizedParent = "parent456",
            body = mapOf(
                "author" to "jami:abc123",
                "timestamp" to "1234567890",
                "body" to "Hello, world!"
            )
        )

        assertEquals("msg123", message.id)
        assertEquals("text/plain", message.type)
        assertEquals("parent456", message.linearizedParent)
        assertEquals("jami:abc123", message.author)
        assertEquals(1234567890L, message.timestamp)
        assertEquals("Hello, world!", message.textContent)
        assertTrue(message.isText)
        assertFalse(message.isCall)
        assertFalse(message.isMember)
        assertFalse(message.isReply)
    }

    @Test
    fun testCallHistoryMessage() {
        val message = SwarmMessage(
            id = "call123",
            type = "application/call-history+json",
            linearizedParent = "",
            body = mapOf(
                "author" to "jami:def456",
                "timestamp" to "1234567890"
            )
        )

        assertTrue(message.isCall)
        assertFalse(message.isText)
        assertFalse(message.isMember)
    }

    @Test
    fun testDataTransferMessage() {
        val message = SwarmMessage(
            id = "transfer123",
            type = "application/data-transfer+json",
            linearizedParent = "",
            body = mapOf(
                "author" to "jami:ghi789",
                "body" to "file.txt"
            )
        )

        assertTrue(message.isText) // Data transfers are also considered "text" type
        assertFalse(message.isCall)
    }

    @Test
    fun testMemberMessage() {
        val message = SwarmMessage(
            id = "member123",
            type = "member",
            linearizedParent = "",
            body = mapOf("author" to "jami:xyz")
        )

        assertTrue(message.isMember)
        assertFalse(message.isText)
        assertFalse(message.isCall)
    }

    @Test
    fun testReplyMessage() {
        val message = SwarmMessage(
            id = "reply123",
            type = "text/plain",
            linearizedParent = "",
            body = mapOf(
                "author" to "jami:abc",
                "body" to "This is a reply",
                "reply-to" to "original123"
            )
        )

        assertTrue(message.isReply)
        assertEquals("original123", message.replyTo)
    }

    @Test
    fun testNonReplyMessage() {
        val message = SwarmMessage(
            id = "msg123",
            type = "text/plain",
            linearizedParent = "",
            body = mapOf("author" to "jami:abc", "body" to "Not a reply")
        )

        assertFalse(message.isReply)
        assertEquals("", message.replyTo)
    }

    @Test
    fun testEmptyBody() {
        val message = SwarmMessage(
            id = "empty123",
            type = "text/plain",
            linearizedParent = "",
            body = emptyMap()
        )

        assertEquals("", message.author)
        assertEquals(0L, message.timestamp)
        assertEquals("", message.textContent)
        assertEquals("", message.replyTo)
    }

    @Test
    fun testInvalidTimestamp() {
        val message = SwarmMessage(
            id = "invalid123",
            type = "text/plain",
            linearizedParent = "",
            body = mapOf("timestamp" to "not-a-number")
        )

        assertEquals(0L, message.timestamp)
    }

    @Test
    fun testReactions() {
        val reactions = mapOf(
            "👍" to listOf("jami:user1", "jami:user2"),
            "❤️" to listOf("jami:user3")
        )

        val message = SwarmMessage(
            id = "msg123",
            type = "text/plain",
            linearizedParent = "",
            body = mapOf("body" to "Hello"),
            reactions = reactions
        )

        assertEquals(2, message.reactions.size)
        assertEquals(listOf("jami:user1", "jami:user2"), message.reactions["👍"])
        assertEquals(listOf("jami:user3"), message.reactions["❤️"])
    }

    @Test
    fun testEditions() {
        val editions = listOf(
            mapOf("body" to "Original text", "timestamp" to "1000"),
            mapOf("body" to "Edited text", "timestamp" to "2000")
        )

        val message = SwarmMessage(
            id = "msg123",
            type = "text/plain",
            linearizedParent = "",
            body = mapOf("body" to "Current text"),
            editions = editions
        )

        assertEquals(2, message.editions.size)
        assertEquals("Original text", message.editions[0]["body"])
        assertEquals("Edited text", message.editions[1]["body"])
    }

    @Test
    fun testStatus() {
        val status = mapOf(
            "jami:user1" to 1, // Sent
            "jami:user2" to 2  // Delivered
        )

        val message = SwarmMessage(
            id = "msg123",
            type = "text/plain",
            linearizedParent = "",
            body = mapOf("body" to "Hello"),
            status = status
        )

        assertEquals(2, message.status.size)
        assertEquals(1, message.status["jami:user1"])
        assertEquals(2, message.status["jami:user2"])
    }

    @Test
    fun testFromDaemonMap() {
        val map: Map<String, Any?> = mapOf(
            "id" to "msg123",
            "type" to "text/plain",
            "linearizedParent" to "parent456",
            "body" to mapOf("author" to "jami:abc", "body" to "Hello"),
            "reactions" to mapOf("👍" to listOf("jami:user1")),
            "editions" to listOf(mapOf("body" to "Old text")),
            "status" to mapOf("jami:user1" to 1)
        )

        val message = SwarmMessage.fromDaemonMap(map)

        assertEquals("msg123", message.id)
        assertEquals("text/plain", message.type)
        assertEquals("parent456", message.linearizedParent)
        assertEquals("jami:abc", message.author)
        assertEquals("Hello", message.textContent)
    }

    @Test
    fun testFromDaemonMapWithMissingFields() {
        val map: Map<String, Any?> = mapOf(
            "id" to null,
            "type" to null
        )

        val message = SwarmMessage.fromDaemonMap(map)

        assertEquals("", message.id)
        assertEquals("", message.type)
        assertEquals("", message.linearizedParent)
        assertTrue(message.body.isEmpty())
        assertTrue(message.reactions.isEmpty())
        assertTrue(message.editions.isEmpty())
        assertTrue(message.status.isEmpty())
    }

    @Test
    fun testFromDaemonMapWithEmptyMap() {
        val message = SwarmMessage.fromDaemonMap(emptyMap())

        assertEquals("", message.id)
        assertEquals("", message.type)
        assertEquals("", message.linearizedParent)
    }

    @Test
    fun testDataClassEquality() {
        val message1 = SwarmMessage(
            id = "msg123",
            type = "text/plain",
            linearizedParent = "",
            body = mapOf("body" to "Hello")
        )

        val message2 = SwarmMessage(
            id = "msg123",
            type = "text/plain",
            linearizedParent = "",
            body = mapOf("body" to "Hello")
        )

        assertEquals(message1, message2)
        assertEquals(message1.hashCode(), message2.hashCode())
    }
}
