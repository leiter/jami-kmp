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

/**
 * The unread count behind the conversation-list badge (messaging plan F7). It was hardcoded to
 * zero, so the badge could never appear.
 */
class ConversationUnreadTest {

    private val peer = Contact(Uri.fromString("peer000000000000000000000000000000000001"))

    private fun conversation(): Conversation =
        Conversation("acc", Uri(Uri.SWARM_SCHEME, "conv1"), Conversation.Mode.OneToOne).apply {
            addContact(peer, MemberRole.MEMBER)
        }

    /** An incoming message: TextMessage sets isIncoming from a non-null author. */
    private fun incoming(body: String) = TextMessage(peer.uri.uri, "acc", null, null, body)

    /** Our own message: no author, as the client records its own sends. */
    private fun outgoing(body: String) = TextMessage(null, "acc", null, null, body)

    @Test
    fun anEmptyConversationHasNothingUnread() {
        assertEquals(0, conversation().unreadMessageCount())
    }

    @Test
    fun incomingMessagesCountAsUnread() = runTest {
        val conv = conversation()
        conv.addElement(incoming("one"))
        conv.addElement(incoming("two"))

        assertEquals(2, conv.unreadMessageCount())
    }

    @Test
    fun aReadMessageEndsTheWalk() = runTest {
        val conv = conversation()
        conv.addElement(incoming("old").apply { read() })
        conv.addElement(incoming("new"))

        assertEquals(1, conv.unreadMessageCount())
    }

    /** Everything older than a read message has been seen too, so the walk stops there. */
    @Test
    fun messagesOlderThanAReadOneAreNotCounted() = runTest {
        val conv = conversation()
        conv.addElement(incoming("older"))
        conv.addElement(incoming("read").apply { read() })
        conv.addElement(incoming("newer"))

        assertEquals(1, conv.unreadMessageCount())
    }

    /** A message already notified counts as seen, as in libjamiclient. */
    @Test
    fun aNotifiedMessageAlsoEndsTheWalk() = runTest {
        val conv = conversation()
        conv.addElement(incoming("notified").apply { isNotified = true })
        conv.addElement(incoming("new"))

        assertEquals(1, conv.unreadMessageCount())
    }

    @Test
    fun ourOwnMessagesAreNeverUnread() = runTest {
        val conv = conversation()
        conv.addElement(outgoing("mine"))
        conv.addElement(outgoing("also mine"))

        assertEquals(0, conv.unreadMessageCount())
    }

    /**
     * Replying does not mark what came before as read — the outgoing message is skipped, not
     * treated as a boundary.
     */
    @Test
    fun anOutgoingReplyDoesNotHideOlderUnreadMessages() = runTest {
        val conv = conversation()
        conv.addElement(incoming("theirs"))
        conv.addElement(outgoing("my reply"))

        assertEquals(1, conv.unreadMessageCount())
    }

    @Test
    fun nonTextInteractionsAreNotCounted() = runTest {
        val conv = conversation()
        conv.addElement(incoming("theirs"))
        conv.addElement(ContactEvent("acc", peer))

        assertEquals(1, conv.unreadMessageCount())
    }
}
