/*
 *  Copyright (C) 2004-2026 Savoir-faire Linux Inc.
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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import net.jami.model.AccountConfig
import net.jami.model.ConfigKey
import net.jami.model.Conversation
import net.jami.model.ConversationPermission
import net.jami.model.ConversationPermissionException
import net.jami.model.MemberRole
import net.jami.model.Uri
import net.jami.testHardwareService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * `AccountService` is the last line of defence before a mutation reaches the daemon: the UI hides
 * what is refused, and these tests pin that a send which slips through anyway is dropped here
 * rather than handed to a daemon that would reject it.
 */
class ConversationPermissionEnforcementTest {

    private val conversationUri = Uri.fromString("swarm:conv1")

    private fun TestScope.testServiceScope(): CoroutineScope =
        CoroutineScope(coroutineContext + SupervisorJob())

    /**
     * An account holding one 1:1 swarm, with [granted] as the daemon's answer. Passing null
     * leaves the permissions unreported, which is the permissive case.
     */
    private fun TestScope.serviceWithSwarm(
        granted: List<ConversationPermission>?,
    ): Pair<AccountService, StubDaemonBridge> {
        val stub = StubDaemonBridge()
        stub.accountIds = listOf("acc1")
        stub.accountDetails["acc1"] =
            mapOf(ConfigKey.ACCOUNT_TYPE.key to AccountConfig.ACCOUNT_TYPE_JAMI)
        val service = AccountService(
            stub, testHardwareService(), StubDeviceRuntimeService(), testServiceScope()
        )
        service.loadAccounts()
        val account = requireNotNull(service.getAccount("acc1"))
        val conversation = account.newSwarm("conv1", Conversation.Mode.OneToOne)
        conversation.setPermissions(granted?.map { it.key })
        return service to stub
    }

    // ==================== Text ====================

    @Test
    fun aDeniedTextSendNeverReachesTheDaemon() = runTest {
        val (service, stub) = serviceWithSwarm(emptyList())

        assertFailsWith<ConversationPermissionException> {
            service.sendConversationMessage("acc1", conversationUri, "hello")
        }
        assertTrue(stub.sentMessages.isEmpty())
    }

    @Test
    fun aGrantedTextSendReachesTheDaemon() = runTest {
        val (service, stub) = serviceWithSwarm(listOf(ConversationPermission.Text))

        service.sendConversationMessage("acc1", conversationUri, "hello")

        assertEquals(1, stub.sentMessages.size)
        assertEquals("hello", stub.sentMessages.single().message)
    }

    @Test
    fun theTextGrantDoesNotCoverAReply() = runTest {
        val (service, stub) = serviceWithSwarm(listOf(ConversationPermission.Text))

        assertFailsWith<ConversationPermissionException> {
            service.sendConversationMessage("acc1", conversationUri, "hello", replyTo = "msg1")
        }
        assertTrue(stub.sentMessages.isEmpty())
    }

    @Test
    fun theReplyGrantCoversAReply() = runTest {
        val (service, stub) = serviceWithSwarm(listOf(ConversationPermission.TextReply))

        service.sendConversationMessage("acc1", conversationUri, "hello", replyTo = "msg1")

        assertEquals("msg1", stub.sentMessages.single().replyTo)
    }

    // ==================== Edit, delete, react ====================

    @Test
    fun anEditNeedsTheEditGrant() = runTest {
        val (service, stub) = serviceWithSwarm(listOf(ConversationPermission.Text))

        assertFailsWith<ConversationPermissionException> {
            service.editConversationMessage("acc1", conversationUri, "fixed", "msg1")
        }
        assertTrue(stub.sentMessages.isEmpty())

        val (granted, grantedStub) = serviceWithSwarm(listOf(ConversationPermission.Edit))
        granted.editConversationMessage("acc1", conversationUri, "fixed", "msg1")
        assertEquals(1, grantedStub.sentMessages.single().flag)
    }

    @Test
    fun aDeleteNeedsTheDeleteGrantNotTheEditGrant() = runTest {
        // Deletion is an edit to an empty body on the wire, flag 1 either way, but the daemon
        // gates the two separately — an edit grant must not let a message be deleted.
        val (service, stub) = serviceWithSwarm(listOf(ConversationPermission.Edit))

        assertFailsWith<ConversationPermissionException> {
            service.deleteConversationMessage("acc1", conversationUri, "msg1")
        }
        assertTrue(stub.sentMessages.isEmpty())

        val (granted, grantedStub) = serviceWithSwarm(listOf(ConversationPermission.Delete))
        granted.deleteConversationMessage("acc1", conversationUri, "msg1")
        assertEquals("", grantedStub.sentMessages.single().message)
    }

    @Test
    fun aReactionNeedsTheReactionGrant() = runTest {
        val (service, stub) = serviceWithSwarm(listOf(ConversationPermission.Text))

        assertFailsWith<ConversationPermissionException> {
            service.sendConversationReaction("acc1", conversationUri, "👍", "msg1")
        }
        assertTrue(stub.sentMessages.isEmpty())

        val (granted, grantedStub) = serviceWithSwarm(listOf(ConversationPermission.Reaction))
        granted.sendConversationReaction("acc1", conversationUri, "👍", "msg1")
        assertEquals(2, grantedStub.sentMessages.single().flag)
    }

    // ==================== Files ====================

    @Test
    fun aDeniedFileSendNeverReachesTheDaemon() = runTest {
        val (service, stub) = serviceWithSwarm(emptyList())

        assertFailsWith<ConversationPermissionException> {
            service.sendFile("acc1", "conv1", "/tmp/a.png", "a.png")
        }
        assertTrue(stub.sentFiles.isEmpty())
    }

    @Test
    fun aFileReplyUsesTheReplyGrant() = runTest {
        val (service, stub) = serviceWithSwarm(listOf(ConversationPermission.File))

        // parent names the message replied to, so the plain file grant does not cover it.
        assertFailsWith<ConversationPermissionException> {
            service.sendFile("acc1", "conv1", "/tmp/a.png", "a.png", parent = "msg1")
        }
        assertTrue(stub.sentFiles.isEmpty())

        service.sendFile("acc1", "conv1", "/tmp/a.png", "a.png")
        assertEquals(1, stub.sentFiles.size)
    }

    // ==================== Members and profile ====================

    @Test
    fun addingAMemberNeedsTheAddGrant() = runTest {
        val (service, stub) = serviceWithSwarm(emptyList())

        assertFailsWith<ConversationPermissionException> {
            service.addConversationMembers("acc1", "conv1", listOf(Uri.fromString("jami:peer1")))
        }
        assertTrue(stub.addedConversationMembers.isEmpty())
    }

    @Test
    fun reAddingABlockedMemberNeedsTheBanGrantNotTheAddGrant() = runTest {
        // Unbanning is re-adding, and the daemon charges it to a different grant.
        val (service, stub) = serviceWithSwarm(listOf(ConversationPermission.AddMember))
        val blocked = Uri.fromString("jami:peer1")
        requireNotNull(service.getAccount("acc1")?.getSwarm("conv1"))
            .roles[blocked.uri] = MemberRole.BLOCKED

        assertFailsWith<ConversationPermissionException> {
            service.addConversationMembers("acc1", "conv1", listOf(blocked))
        }
        assertTrue(stub.addedConversationMembers.isEmpty())
    }

    @Test
    fun removingAMemberNeedsTheBanGrant() = runTest {
        val (service, _) = serviceWithSwarm(emptyList())

        assertFailsWith<ConversationPermissionException> {
            service.removeConversationMember("acc1", "conv1", Uri.fromString("jami:peer1"))
        }
    }

    @Test
    fun aOneToOneProfileUpdateIsNotGated() = runTest {
        // Only a group's profile is permission-gated; this swarm has two parties.
        val (service, _) = serviceWithSwarm(emptyList())

        service.updateConversationInfo("acc1", "conv1", mapOf("title" to "x"))
    }

    // ==================== Unknown permissions stay permissive ====================

    @Test
    fun anUnreportedConversationStillSends() = runTest {
        // The daemon shipped with this client has no getConversationPermissions at all, so this
        // is the live path today: nothing must be blocked.
        val (service, stub) = serviceWithSwarm(null)

        service.sendConversationMessage("acc1", conversationUri, "hello")
        service.sendConversationReaction("acc1", conversationUri, "👍", "msg1")
        service.sendFile("acc1", "conv1", "/tmp/a.png", "a.png")
        service.addConversationMembers("acc1", "conv1", listOf(Uri.fromString("jami:peer1")))

        assertEquals(2, stub.sentMessages.size)
        assertEquals(1, stub.sentFiles.size)
        assertEquals(1, stub.addedConversationMembers.size)
    }

    @Test
    fun anUnknownConversationIsLeftToTheDaemon() = runTest {
        // A stale screen must not crash the send path; this is a permission check, not a
        // validity check.
        val (service, stub) = serviceWithSwarm(emptyList())

        service.sendConversationMessage("acc1", Uri.fromString("swarm:nosuch"), "hello")

        assertEquals(1, stub.sentMessages.size)
    }
}
