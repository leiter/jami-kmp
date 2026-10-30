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
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Conversation.contact throws for a group, which crashed every screen that walks the whole
 * conversation list (found on a device 2026-10-29: creating one 3-member group made the
 * new-conversation screen crash-loop). contactOrNull is the accessor those walks use.
 */
class GroupConversationContactTest {

    private fun contact(id: String, isUser: Boolean = false) =
        Contact(Uri.fromString(id), isUser = isUser)

    private fun conversation(vararg members: Contact) =
        Conversation("acc1", Uri(Uri.SWARM_SCHEME, "conv1"), members.toMutableList())

    @Test
    fun aOneToOneConversationHasItsContactEitherWay() {
        val bob = contact("bbbb")
        val conv = conversation(contact("me", isUser = true), bob)
        assertEquals(bob, conv.contact)
        assertEquals(bob, conv.contactOrNull)
    }

    /** The throw is kept: it is a guard for code that already knows it holds a 1:1 conversation. */
    @Test
    fun aGroupStillThrowsFromContact() {
        val group = conversation(contact("me", isUser = true), contact("bbbb"), contact("cccc"))
        assertFailsWith<IllegalStateException> { group.contact }
    }

    @Test
    fun aGroupHasNoSingleContact() {
        val group = conversation(contact("me", isUser = true), contact("bbbb"), contact("cccc"))
        assertNull(group.contactOrNull, "a group has no other party, and must not throw here")
    }

    /**
     * The shape that crashed: a list holding one group among 1:1 conversations, walked as a list.
     * A `?:` or `?.` on `contact` catches nothing, because it raises rather than returning null.
     */
    // ==================== Title (reference ConversationItemViewModel.getTitle) ====================

    @Test
    fun aOneToOneIsTitledByTheOtherParty() {
        val conv = conversation(contact("me", isUser = true), contact("bbbb"))
        assertEquals("bbbb", conv.title)
    }

    /** A group with no name is shown by who is in it, not by its swarm id. */
    @Test
    fun anUnnamedGroupIsTitledByItsMembers() {
        val group = conversation(contact("me", isUser = true), contact("bbbb"), contact("cccc"))
        assertEquals("bbbb, cccc", group.title)
    }

    @Test
    fun aLongGroupNamesThreeMembersAndCountsTheRest() {
        val group = conversation(
            contact("me", isUser = true),
            contact("aaaa"), contact("bbbb"), contact("cccc"), contact("dddd"), contact("eeee"),
        )
        assertEquals("aaaa, bbbb, cccc + 2", group.title)
    }

    /**
     * A just-accepted invitation has no profile name and no members yet. The reference puts a
     * hardcoded, untranslated "(Syncing)" here; the model leaves the title empty instead and
     * flags the state, so the UI can supply a localized string (`conversation_title_syncing`).
     */
    @Test
    fun aSyncingConversationWithNoMembersHasNoTitleOfItsOwn() {
        val syncing = Conversation("acc1", Uri(Uri.SWARM_SCHEME, "conv1"), initialMode = Conversation.Mode.Syncing)
        assertEquals("", syncing.title)
        assertTrue(syncing.isUnnamedSyncing)
    }

    /** Not syncing and still nobody in it: the swarm id is all there is to show. */
    @Test
    fun anEmptyNonSyncingConversationFallsBackToItsId() {
        val empty = Conversation("acc1", Uri(Uri.SWARM_SCHEME, "conv1"), initialMode = Conversation.Mode.OneToOne)
        assertEquals("conv1", empty.title)
        assertFalse(empty.isUnnamedSyncing)
    }

    /** Once a member arrives the name takes over, and the placeholder must stop being used. */
    @Test
    fun aSyncingConversationWithAMemberIsNamedByThem() {
        val syncing = Conversation("acc1", Uri(Uri.SWARM_SCHEME, "conv1"), initialMode = Conversation.Mode.Syncing)
        syncing.addContact(contact("bbbb"), MemberRole.MEMBER)
        assertEquals("bbbb", syncing.title)
        assertFalse(syncing.isUnnamedSyncing)
    }

    @Test
    fun walkingAMixedListDoesNotThrow() {
        val bob = contact("bbbb")
        val conversations = listOf(
            conversation(contact("me", isUser = true), bob),
            conversation(contact("me", isUser = true), contact("cccc"), contact("dddd")),
        )
        val contacts = conversations.mapNotNull { it.contactOrNull }
        assertEquals(1, contacts.size)
        assertEquals(bob, assertNotNull(contacts.firstOrNull()))
    }
}
