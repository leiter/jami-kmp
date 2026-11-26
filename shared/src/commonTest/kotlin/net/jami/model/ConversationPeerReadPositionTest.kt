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

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A peer's "read by" marker after a restart comes from the daemon's member list
 * ("lastDisplayed"), not from live DISPLAYED statuses. Lost on restart until 2026-11-26.
 */
class ConversationPeerReadPositionTest {

    private val peerId = "peer000000000000000000000000000000000001"
    private val peer = Contact(Uri.fromString(peerId))

    private fun conversation(): Conversation =
        Conversation("acc", Uri(Uri.SWARM_SCHEME, "conv1"), Conversation.Mode.OneToOne).apply {
            addContact(peer, MemberRole.MEMBER)
        }

    private fun ours(id: String, parent: String?) =
        TextMessage(null, "acc", null, null, id).apply { setSwarmInfo("conv1", id, parent) }

    @Test
    fun aPositionWhoseMessageIsLoadedAppliesAtOnce() = runTest {
        val conv = conversation()
        conv.addSwarmElement(ours("m1", null), false)

        conv.restorePeerReadPosition(peerId, "m1")

        assertEquals("m1", conv.lastDisplayedMessages[peerId])
    }

    /** Members are read when the conversation loads, usually before its history. */
    @Test
    fun aPositionWaitsForItsMessageToLoad() = runTest {
        val conv = conversation()

        conv.restorePeerReadPosition(peerId, "m2")
        assertNull(conv.lastDisplayedMessages[peerId])

        conv.addSwarmElement(ours("m1", null), false)
        assertNull(conv.lastDisplayedMessages[peerId])
        conv.addSwarmElement(ours("m2", "m1"), false)
        assertEquals("m2", conv.lastDisplayedMessages[peerId])
    }
}
