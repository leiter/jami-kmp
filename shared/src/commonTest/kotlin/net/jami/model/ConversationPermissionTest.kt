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
package net.jami.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConversationPermissionTest {

    private fun swarm(vararg granted: ConversationPermission): Conversation =
        Conversation("acc1", Uri.fromString("swarm:conv1"), mode = Conversation.Mode.OneToOne)
            .apply { setPermissions(granted.map { it.key }) }

    private fun legacy(): Conversation =
        Conversation("acc1", Contact(Uri.fromString("jami:peer1")))

    // ==================== Wire keys ====================

    @Test
    fun keysMatchTheDaemonsOwnStrings() {
        // These are wire values shared with jami-client-android's Conversation.Permission;
        // renaming one silently stops matching what the daemon reports.
        assertEquals("text", ConversationPermission.Text.key)
        assertEquals("textReply", ConversationPermission.TextReply.key)
        assertEquals("file", ConversationPermission.File.key)
        assertEquals("fileReply", ConversationPermission.FileReply.key)
        assertEquals("reaction", ConversationPermission.Reaction.key)
        assertEquals("edit", ConversationPermission.Edit.key)
        assertEquals("delete", ConversationPermission.Delete.key)
        assertEquals("deleteFile", ConversationPermission.DeleteFile.key)
        assertEquals("call", ConversationPermission.Call.key)
        assertEquals("hostConference", ConversationPermission.HostConference.key)
        assertEquals("addMember", ConversationPermission.AddMember.key)
        assertEquals("banUnbanMember", ConversationPermission.BanUnbanMember.key)
        assertEquals("updateProfile", ConversationPermission.UpdateProfile.key)
        assertEquals("createDocument", ConversationPermission.CreateDocument.key)
        assertEquals(14, ConversationPermission.entries.size)
    }

    @Test
    fun fromKeyRoundTripsEveryEntryAndRejectsOthers() {
        for (permission in ConversationPermission.entries) {
            assertEquals(permission, ConversationPermission.fromKey(permission.key))
        }
        assertNull(ConversationPermission.fromKey("notAPermission"))
    }

    // ==================== Unknown is permissive ====================

    @Test
    fun anUnreportedSwarmGrantsEverything() {
        // The regression this guards: a daemon without getConversationPermissions reports
        // nothing, and reading that as "deny" would disable sending in every conversation.
        val conversation = Conversation("acc1", Uri.fromString("swarm:conv1"), mode = Conversation.Mode.OneToOne)

        assertNull(conversation.permissionsFlow.value)
        for (permission in ConversationPermission.entries) {
            assertTrue(conversation.hasPermission(permission), "expected ${permission.key} granted")
        }
        assertTrue(conversation.canSendText())
        assertTrue(conversation.canSendFile())
        assertTrue(conversation.canPlaceCall())
    }

    @Test
    fun aNonSwarmConversationGrantsEverything() {
        // SIP and legacy conversations have no permission model; an empty set must not gate them.
        val conversation = legacy().apply { setPermissions(emptyList()) }

        assertFalse(conversation.isSwarm)
        for (permission in ConversationPermission.entries) {
            assertTrue(conversation.hasPermission(permission), "expected ${permission.key} granted")
        }
    }

    @Test
    fun anEmptyReportedSetGrantsNothingInASwarm() {
        // Distinct from null: the daemon answered, and it grants nothing.
        val conversation = swarm()

        assertEquals(emptySet(), conversation.permissionsFlow.value)
        for (permission in ConversationPermission.entries) {
            assertFalse(conversation.hasPermission(permission), "expected ${permission.key} denied")
        }
    }

    @Test
    fun clearingPermissionsReturnsToUnknown() {
        val conversation = swarm()
        assertFalse(conversation.canSendText())

        conversation.setPermissions(null)

        assertNull(conversation.permissionsFlow.value)
        assertTrue(conversation.canSendText())
    }

    // ==================== Reply is a separate grant ====================

    @Test
    fun replyingIsGrantedSeparatelyFromSending() {
        val conversation = swarm(ConversationPermission.Text, ConversationPermission.File)

        assertTrue(conversation.canSendText(null))
        assertFalse(conversation.canSendText("someMessageId"))
        assertTrue(conversation.canSendFile(null))
        assertFalse(conversation.canSendFile("someMessageId"))
    }

    @Test
    fun aReplyOnlyConversationRefusesNewMessages() {
        val conversation = swarm(ConversationPermission.TextReply, ConversationPermission.FileReply)

        assertFalse(conversation.canSendText(null))
        assertTrue(conversation.canSendText("someMessageId"))
        assertFalse(conversation.canSendFile(null))
        assertTrue(conversation.canSendFile("someMessageId"))
    }

    @Test
    fun anEmptyReplyIdCountsAsANewMessage() {
        val conversation = swarm(ConversationPermission.Text)

        assertTrue(conversation.canSendText(""))
    }

    // ==================== Composite predicates ====================

    @Test
    fun hostingAConferenceIsEnoughToPlaceACall() {
        assertTrue(swarm(ConversationPermission.Call).canPlaceCall())
        assertTrue(swarm(ConversationPermission.HostConference).canPlaceCall())
        assertFalse(swarm(ConversationPermission.Text).canPlaceCall())
    }

    @Test
    fun onlyAGroupsProfileIsPermissionGated() {
        // A 1:1's profile is local business, so the grant does not apply to it.
        val oneToOne = swarm()
        assertFalse(oneToOne.isSwarmGroup)
        assertTrue(oneToOne.canUpdateProfile())
    }

    @Test
    fun aGroupsProfileNeedsTheGrant() {
        val group = Conversation("acc1", Uri.fromString("swarm:conv1"), mode = Conversation.Mode.InvitesOnly)
        group.addContact(Contact(Uri.fromString("jami:a")), MemberRole.ADMIN)
        group.addContact(Contact(Uri.fromString("jami:b")), MemberRole.MEMBER)
        group.addContact(Contact(Uri.fromString("jami:c")), MemberRole.MEMBER)

        group.setPermissions(emptyList())
        assertTrue(group.isSwarmGroup)
        assertFalse(group.canUpdateProfile())

        group.setPermissions(listOf(ConversationPermission.UpdateProfile.key))
        assertTrue(group.canUpdateProfile())
    }

    // ==================== Deletion depends on what is deleted ====================

    @Test
    fun deletingAFileTransferUsesItsOwnGrant() {
        val conversation = swarm(ConversationPermission.Delete)

        assertTrue(conversation.canDeleteMessage(Interaction.InteractionType.TEXT))
        assertFalse(conversation.canDeleteMessage(Interaction.InteractionType.DATA_TRANSFER))
    }

    @Test
    fun deletingAFileTransferIsAllowedWithTheFileGrant() {
        val conversation = swarm(ConversationPermission.DeleteFile)

        assertFalse(conversation.canDeleteMessage(Interaction.InteractionType.TEXT))
        assertTrue(conversation.canDeleteMessage(Interaction.InteractionType.DATA_TRANSFER))
    }

    @Test
    fun aCallEntryIsGovernedByNoGrant() {
        // Not a commit anyone deletes; the reference maps it to no permission at all.
        val conversation = swarm()

        assertTrue(conversation.canDeleteMessage(Interaction.InteractionType.CALL))
        conversation.requireMessageDeletion(Interaction.InteractionType.CALL)
    }

    // ==================== require* throws ====================

    @Test
    fun requirePermissionThrowsNamingTheRefusedGrant() {
        val conversation = swarm(ConversationPermission.Text)

        conversation.requirePermission(ConversationPermission.Text)
        val failure = assertFailsWith<ConversationPermissionException> {
            conversation.requirePermission(ConversationPermission.Reaction)
        }
        assertEquals(ConversationPermission.Reaction, failure.permission)
        assertTrue(failure.message!!.contains("reaction"))
    }

    @Test
    fun requireMessageDeletionThrowsForTheRightKind() {
        val conversation = swarm(ConversationPermission.Delete)

        conversation.requireMessageDeletion(Interaction.InteractionType.TEXT)
        val failure = assertFailsWith<ConversationPermissionException> {
            conversation.requireMessageDeletion(Interaction.InteractionType.DATA_TRANSFER)
        }
        assertEquals(ConversationPermission.DeleteFile, failure.permission)
    }
}
