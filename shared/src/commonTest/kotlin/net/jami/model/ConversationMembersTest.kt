package net.jami.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Member bookkeeping used by ConversationFacade.onConversationMemberEvent
 * (review 2026-09-18 #5): roles follow the event, re-adding never duplicates, and only a group
 * member who LEFT is dropped — as in libjamiclient.
 */
class ConversationMembersTest {

    private val bob = Contact(Uri.fromString("bob"))

    private fun group() = Conversation("acc", Uri(Uri.SWARM_SCHEME, "g1"), Conversation.Mode.InvitesOnly)
    private fun oneToOne() = Conversation("acc", Uri(Uri.SWARM_SCHEME, "c1"), Conversation.Mode.OneToOne)

    @Test
    fun reAddingAMemberOnlyUpdatesItsRole() {
        val conv = group()
        conv.addContact(bob, MemberRole.INVITED)
        conv.addContact(bob, MemberRole.MEMBER)
        assertEquals(1, conv.contacts.count { it == bob })
        assertEquals(MemberRole.MEMBER, conv.roles[bob.uri.uri])
    }

    @Test
    fun groupMemberWhoLeftIsRemoved() {
        val conv = group()
        conv.addContact(bob, MemberRole.MEMBER)
        conv.removeContact(bob, MemberRole.LEFT)
        assertFalse(bob in conv.contacts)
        assertEquals(MemberRole.LEFT, conv.roles[bob.uri.uri])
    }

    @Test
    fun blockedGroupMemberStaysListedAsBlocked() {
        val conv = group()
        conv.addContact(bob, MemberRole.MEMBER)
        conv.removeContact(bob, MemberRole.BLOCKED)
        assertTrue(bob in conv.contacts)
        assertEquals(MemberRole.BLOCKED, conv.roles[bob.uri.uri])
    }

    @Test
    fun oneToOnePeerIsKeptWhenTheyLeave() {
        val conv = oneToOne()
        conv.addContact(bob, MemberRole.MEMBER)
        conv.removeContact(bob, MemberRole.LEFT)
        assertTrue(bob in conv.contacts)
    }

    @Test
    fun removeWithoutRoleRemovesTheContact() {
        val conv = oneToOne()
        conv.addContact(bob, MemberRole.MEMBER)
        conv.removeContact(bob)
        assertFalse(bob in conv.contacts)
    }
}
