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
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TrustRequestTest {

    @Test
    fun testTrustRequestCreation() {
        val from = Uri.fromString("jami:abc123def456")
        val conversationUri = Uri.fromString("jami:conv789")

        val request = TrustRequest(
            accountId = "account123",
            from = from,
            timestamp = 1234567890L,
            conversationUri = conversationUri,
            mode = Conversation.Mode.OneToOne
        )

        assertEquals("account123", request.accountId)
        assertEquals(from, request.from)
        assertEquals(1234567890L, request.timestamp)
        assertEquals(conversationUri, request.conversationUri)
        assertEquals(Conversation.Mode.OneToOne, request.mode)
        assertNull(request.profile)
    }

    @Test
    fun testTrustRequestWithProfile() {
        val from = Uri.fromString("jami:abc123")
        val conversationUri = Uri.fromString("jami:conv456")
        val profile = Profile(displayName = "John Doe", avatar = null)

        val request = TrustRequest(
            accountId = "account123",
            from = from,
            timestamp = 1234567890L,
            conversationUri = conversationUri,
            profile = profile,
            mode = Conversation.Mode.OneToOne
        )

        assertNotNull(request.profile)
        assertEquals("John Doe", request.profile?.displayName)
    }

    @Test
    fun testTrustRequestWithGroupMode() {
        val from = Uri.fromString("jami:abc123")
        val conversationUri = Uri.fromString("swarm:group789")

        val request = TrustRequest(
            accountId = "account123",
            from = from,
            timestamp = 1234567890L,
            conversationUri = conversationUri,
            mode = Conversation.Mode.Syncing
        )

        assertEquals(Conversation.Mode.Syncing, request.mode)
    }

    @Test
    fun testDataClassEquality() {
        val from = Uri.fromString("jami:abc123")
        val conversationUri = Uri.fromString("jami:conv456")

        val request1 = TrustRequest(
            accountId = "account123",
            from = from,
            timestamp = 1234567890L,
            conversationUri = conversationUri,
            mode = Conversation.Mode.OneToOne
        )

        val request2 = TrustRequest(
            accountId = "account123",
            from = from,
            timestamp = 1234567890L,
            conversationUri = conversationUri,
            mode = Conversation.Mode.OneToOne
        )

        assertEquals(request1, request2)
        assertEquals(request1.hashCode(), request2.hashCode())
    }

    @Test
    fun testDataClassInequality() {
        val from = Uri.fromString("jami:abc123")
        val conversationUri = Uri.fromString("jami:conv456")

        val request1 = TrustRequest(
            accountId = "account123",
            from = from,
            timestamp = 1234567890L,
            conversationUri = conversationUri,
            mode = Conversation.Mode.OneToOne
        )

        val request2 = TrustRequest(
            accountId = "account456", // Different account
            from = from,
            timestamp = 1234567890L,
            conversationUri = conversationUri,
            mode = Conversation.Mode.OneToOne
        )

        assertNotEquals(request1, request2)
    }

    @Test
    fun testDifferentTimestamps() {
        val from = Uri.fromString("jami:abc123")
        val conversationUri = Uri.fromString("jami:conv456")

        val request1 = TrustRequest(
            accountId = "account123",
            from = from,
            timestamp = 1000L,
            conversationUri = conversationUri,
            mode = Conversation.Mode.OneToOne
        )

        val request2 = TrustRequest(
            accountId = "account123",
            from = from,
            timestamp = 2000L, // Different timestamp
            conversationUri = conversationUri,
            mode = Conversation.Mode.OneToOne
        )

        assertNotEquals(request1, request2)
    }

    @Test
    fun testCopyFunction() {
        val from = Uri.fromString("jami:abc123")
        val conversationUri = Uri.fromString("jami:conv456")

        val original = TrustRequest(
            accountId = "account123",
            from = from,
            timestamp = 1234567890L,
            conversationUri = conversationUri,
            mode = Conversation.Mode.OneToOne
        )

        val withProfile = original.copy(profile = Profile(displayName = "Jane", avatar = null))

        assertEquals(original.accountId, withProfile.accountId)
        assertEquals(original.from, withProfile.from)
        assertEquals(original.timestamp, withProfile.timestamp)
        assertNull(original.profile)
        assertNotNull(withProfile.profile)
        assertEquals("Jane", withProfile.profile?.displayName)
    }
}
