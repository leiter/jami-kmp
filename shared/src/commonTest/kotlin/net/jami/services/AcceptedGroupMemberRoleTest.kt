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

import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.jami.model.ConfigKey
import net.jami.model.MemberRole
import net.jami.viewmodel.makeAccountService
import net.jami.viewmodel.makeCallService
import net.jami.viewmodel.makeContactService
import net.jami.viewmodel.makeConversationFacade
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * Accepting a group invitation as a plain member.
 *
 * The pending request is seeded with its sender as a contact but no role, so the pending list
 * can name them. When the conversation became ready, members already present were skipped, so
 * the sender never got a role; with no peer holding a role, [net.jami.model.Conversation.isEnded]
 * read the group as "everyone left" and the chat showed "Conversation ended." instead of a
 * composer until the app was restarted (two-simulator run, 2026-11-24).
 */
class AcceptedGroupMemberRoleTest {

    private companion object {
        const val ACC = "acc1"
        const val CONV = "8afc944573d67dc46a4d3c1203b9bd783a25bc47"
        const val ME = "e34f5e36698ea732aa9ba8e670b5503605481787"
        const val ALICE = "e9edb5325bf16ac1ad8923ba854faaf1ba38248c"
    }

    @Test
    fun aGroupAcceptedAsMemberIsNotEndedAndTheInviterKeepsTheirRole() = runTest {
        val stub = StubDaemonBridge()
        stub.accountIds = listOf(ACC)
        stub.accountDetails[ACC] = mapOf(
            ConfigKey.ACCOUNT_TYPE.key to "RING",
            ConfigKey.ACCOUNT_USERNAME.key to ME,
        )
        val accountService = makeAccountService(stub, this)
        val contactService = makeContactService(stub, accountService, this)
        val callService = makeCallService(stub, accountService, scope = this)
        val facade = makeConversationFacade(stub, accountService, callService, contactService, this)
        accountService.loadAccounts()
        advanceUntilIdle()

        // The invitation arrives: the sender is seeded as a contact, without a role.
        facade.onConversationRequestReceived(ACC, CONV, mapOf("from" to ALICE, "mode" to "2"))
        advanceUntilIdle()

        // Accepted: the daemon now holds an invites-only group where Alice is the admin.
        stub.conversations[ACC] = listOf(CONV)
        stub.conversationInfo[CONV] = mapOf("mode" to "2")
        stub.conversationMembers[CONV] = listOf(
            mapOf("uri" to ALICE, "role" to "admin"),
            mapOf("uri" to ME, "role" to "member"),
        )
        facade.onConversationReady(ACC, CONV)
        advanceUntilIdle()

        val conversation = assertNotNull(accountService.getAccount(ACC)?.getSwarm(CONV))
        assertEquals(
            MemberRole.ADMIN,
            conversation.roles[ALICE],
            "the inviter was already a contact from the request and must still get the daemon's role"
        )
        assertFalse(
            conversation.isEnded,
            "a peer is still in the group, so the composer must be offered, not 'Conversation ended.'"
        )
    }
}
