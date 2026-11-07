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
 * The first attempt at this judged a syncing conversation by [Conversation.requestMode], on the
 * assumption that it holds the daemon's real mode. A device run on 2026-11-07 disproved that: a
 * 1:1 stuck cloning reported `requestMode=InvitesOnly`, because the daemon cannot know a mode it
 * has not cloned yet. Membership is judged instead — it comes from `getConversationMembers`,
 * not from the uncloned repo.
 */
class SyncingConversationShapeTest {

    private fun contact(id: String, isUser: Boolean = false) =
        Contact(Uri.fromString(id), isUser = isUser)

    private fun syncingConversation(
        realMode: Conversation.Mode?,
        members: List<Contact> = listOf(contact("me", isUser = true), contact("bbbb")),
    ) = Conversation(
        "acc1",
        Uri(Uri.SWARM_SCHEME, "conv1"),
        members.toMutableList(),
        Conversation.Mode.Syncing,
    ).also { it.requestMode = realMode }

    @Test
    fun aSyncingOneToOneIsNotAGroup() {
        assertFalse(syncingConversation(Conversation.Mode.OneToOne).isSwarmGroup)
    }

    /**
     * The regression this file exists for, in the shape the device actually produced it:
     * two members, and a mode the daemon invented because it had nothing to read it from.
     */
    @Test
    fun aSyncingOneToOneIsNotAGroupEvenWhenTheDaemonClaimsInvitesOnly() {
        assertFalse(syncingConversation(Conversation.Mode.InvitesOnly).isSwarmGroup)
        assertFalse(syncingConversation(Conversation.Mode.AdminInvitesOnly).isSwarmGroup)
        assertFalse(syncingConversation(Conversation.Mode.Public).isSwarmGroup)
    }

    @Test
    fun aSyncingGroupIsStillAGroup() {
        val members = listOf(
            contact("me", isUser = true), contact("bbbb"), contact("cccc"),
        )
        assertTrue(syncingConversation(Conversation.Mode.InvitesOnly, members).isSwarmGroup)
        // Membership decides even when the mode is missing entirely.
        assertTrue(syncingConversation(null, members).isSwarmGroup)
    }

    /** An unknown mode means 1:1, which is what the facade assumes when the daemon reports none. */
    @Test
    fun aSyncingConversationOfUnknownModeIsNotAGroup() {
        assertFalse(syncingConversation(null).isSwarmGroup)
    }

    /** With no members loaded yet there is nothing to judge by, so the claimed mode wins. */
    @Test
    fun aSyncingConversationWithNoMembersFallsBackToTheClaimedMode() {
        assertTrue(syncingConversation(Conversation.Mode.InvitesOnly, emptyList()).isSwarmGroup)
        assertFalse(syncingConversation(Conversation.Mode.OneToOne, emptyList()).isSwarmGroup)
        assertFalse(syncingConversation(null, emptyList()).isSwarmGroup)
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
