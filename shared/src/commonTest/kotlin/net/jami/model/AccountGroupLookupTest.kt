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
import kotlin.test.assertNull

/**
 * Account.getByContact walks every conversation the account holds, so it must not use
 * Conversation.contact, which throws for a group. Found on a device 2026-10-30: placing a call
 * in a 3-member group crashed in ConversationFacade.onCallStateChange →
 * findConversationForCall → getByContact with "getContact() called for group conversation of
 * size 3". See [GroupConversationContactTest] for the accessor itself.
 */
class AccountGroupLookupTest {

    private fun contact(id: String, isUser: Boolean = false) =
        Contact(Uri.fromString(id), isUser = isUser)

    private fun conversation(id: String, vararg members: Contact) =
        Conversation(
            "acc1",
            Uri(Uri.SWARM_SCHEME, id),
            members.toMutableList(),
            Conversation.Mode.InvitesOnly
        )

    private fun accountWithAGroup(): Pair<Account, Contact> {
        val account = Account("acc1")
        val me = contact("me", isUser = true)
        val bob = contact("bbbb")
        account.conversationStarted(conversation("group1", me, bob, contact("cccc")))
        return account to bob
    }

    @Test
    fun lookingUpAGroupMemberDoesNotThrow() {
        val (account, bob) = accountWithAGroup()
        assertNull(account.getByContact(bob.uri), "a group is not a 1:1 conversation with bob")
    }

    @Test
    fun aOneToOneIsStillFoundWithAGroupInTheList() {
        val (account, bob) = accountWithAGroup()
        val oneToOne = Conversation(
            "acc1",
            Uri(Uri.SWARM_SCHEME, "conv1"),
            mutableListOf(contact("me", isUser = true), bob),
            Conversation.Mode.OneToOne
        )
        account.conversationStarted(oneToOne)
        assertEquals(oneToOne, account.getByContact(bob.uri))
    }
}
