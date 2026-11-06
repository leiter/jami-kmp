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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `Mode.Syncing` says the swarm's git repo has not been cloned yet — it says nothing about the
 * conversation's shape. Reading it as "not OneToOne, therefore a group" made every still-cloning
 * 1:1 a group, and a group call is placed on `swarm:<conversationId>`: the daemon answers that
 * with an empty call id, so the call screen waited on "Calling…" with no error (found on a
 * device 2026-11-06).
 *
 * While syncing, the daemon's real mode is kept in [Conversation.requestMode].
 */
class SyncingConversationShapeTest {

    private fun contact(id: String, isUser: Boolean = false) =
        Contact(Uri.fromString(id), isUser = isUser)

    private fun syncingConversation(realMode: Conversation.Mode?) =
        Conversation(
            "acc1",
            Uri(Uri.SWARM_SCHEME, "conv1"),
            mutableListOf(contact("me", isUser = true), contact("bbbb")),
            Conversation.Mode.Syncing,
        ).also { it.requestMode = realMode }

    @Test
    fun aSyncingOneToOneIsNotAGroup() {
        assertFalse(syncingConversation(Conversation.Mode.OneToOne).isSwarmGroup)
    }

    @Test
    fun aSyncingGroupIsStillAGroup() {
        assertTrue(syncingConversation(Conversation.Mode.InvitesOnly).isSwarmGroup)
        assertTrue(syncingConversation(Conversation.Mode.AdminInvitesOnly).isSwarmGroup)
        assertTrue(syncingConversation(Conversation.Mode.Public).isSwarmGroup)
    }

    /** An unknown mode means 1:1, which is what the facade assumes when the daemon reports none. */
    @Test
    fun aSyncingConversationOfUnknownModeIsNotAGroup() {
        assertFalse(syncingConversation(null).isSwarmGroup)
    }

    /** The settled modes are unchanged. */
    @Test
    fun settledModesKeepTheirShape() {
        fun conv(mode: Conversation.Mode) = Conversation(
            "acc1",
            Uri(Uri.SWARM_SCHEME, "conv1"),
            mutableListOf(contact("me", isUser = true), contact("bbbb")),
            mode,
        )
        assertFalse(conv(Conversation.Mode.OneToOne).isSwarmGroup)
        assertTrue(conv(Conversation.Mode.InvitesOnly).isSwarmGroup)
        assertTrue(conv(Conversation.Mode.Public).isSwarmGroup)
    }
}
