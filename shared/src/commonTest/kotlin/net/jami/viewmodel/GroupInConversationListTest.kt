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
package net.jami.viewmodel

import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.jami.model.Uri
import net.jami.ui.viewmodel.ConversationsViewModel
import net.jami.services.StubDaemonBridge
import net.jami.services.StubDeviceRuntimeService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Until 2026-11-07 no shared fixture put a group in a conversation list, and that single hole
 * is why 918 passing tests said nothing while six call sites walked a list calling
 * `Conversation.contact` — an accessor that *throws* for a group, by design, as a guard for
 * code that already knows it holds a 1:1.
 *
 * The crash was found by opening the app on a device instead: a group conversation took down
 * every screen that walked the list (`bbba9fb`), and then the incoming-call path did the same
 * through `Account.getByContact` (`69ab199`). `AccountGroupLookupTest` covers the model
 * accessor; this file covers the layer above it, where a *list* holding both shapes is walked.
 *
 * Any new code that walks a conversation list belongs here, with
 * [net.jami.viewmodel.seedMixedConversationList], so the suite enforces this instead of a
 * future grep having to.
 */
class GroupInConversationListTest {

    @Test
    fun theConversationsViewModelLoadsAListHoldingAGroup() = runTest {
        val stub = StubDaemonBridge()
        val services = makeTestServiceStack(stub, this)
        val account = prepareAccountInService(stub, services.accountService)
        advanceUntilIdle()
        seedMixedConversationList(account)

        val vm = ConversationsViewModel(
            services.accountService, services.conversationFacade, StubDeviceRuntimeService(),
            services.contactService, services.vCardService, services.settingsRepository,
            viewModelScope(),
        )
        vm.loadConversations()
        advanceUntilIdle()

        // Reaching this line at all is the regression guard — the mapping *threw* on the
        // group, it did not produce a wrong row — but assert the content too, so the test
        // cannot quietly start passing on an empty list.
        val ids = vm.state.value.conversations.map { it.id }
        assertEquals(listOf("group1", "oneToOne1"), ids)
        val groupRow = vm.state.value.conversations.single { it.id == "group1" }
        assertTrue(groupRow.isGroup, "the group row must be marked as a group")
    }

    @Test
    fun aGroupInTheListDoesNotHideTheOneToOneBesideIt() = runTest {
        val stub = StubDaemonBridge()
        val services = makeTestServiceStack(stub, this)
        val account = prepareAccountInService(stub, services.accountService)
        advanceUntilIdle()
        val (group, oneToOne) = seedMixedConversationList(account)

        // getByContact walks groups first here, which is the ordering that crashed on device.
        val found = account.getByContact(Uri.fromString("bbbb"))
        assertNotNull(found, "the 1:1 must still be found with a group ahead of it in the list")
        assertEquals(oneToOne.uri, found.uri)

        // And a member of the group is not mistaken for a 1:1 peer.
        assertNull(account.getByContact(Uri.fromString("peer1")))
        assertTrue(group.isGroup)
    }

    @Test
    fun theFacadeResolvesBothShapesFromTheSameList() = runTest {
        val stub = StubDaemonBridge()
        val services = makeTestServiceStack(stub, this)
        val account = prepareAccountInService(stub, services.accountService)
        advanceUntilIdle()
        val (group, oneToOne) = seedMixedConversationList(account)

        assertEquals(
            group.uri,
            services.conversationFacade.getConversation(account.accountId, group.uri)?.uri,
        )
        assertEquals(
            oneToOne.uri,
            services.conversationFacade.getConversation(account.accountId, oneToOne.uri)?.uri,
        )
    }

    /**
     * A group created with one invitee has the same members as a 1:1 with that invitee. It must
     * still read as a group: in the "Groups" filter, without the peer's contact on the row, and
     * never returned by [net.jami.model.Account.getByContact] in place of the real 1:1
     * (two simulators, 2026-11-24).
     */
    @Test
    fun aTwoMemberGroupIsStillAGroup() = runTest {
        val stub = StubDaemonBridge()
        val services = makeTestServiceStack(stub, this)
        val account = prepareAccountInService(stub, services.accountService)
        advanceUntilIdle()
        val group = net.jami.model.Conversation(
            account.accountId,
            Uri(Uri.SWARM_SCHEME, "group2"),
            mutableListOf(
                net.jami.model.Contact(Uri.fromString("me"), isUser = true),
                net.jami.model.Contact(Uri.fromString("bbbb")),
            ),
            net.jami.model.Conversation.Mode.InvitesOnly,
        )
        account.conversationStarted(group)
        val oneToOne = oneToOneConversation(account.accountId, "oneToOne1", "bbbb")
        account.conversationStarted(oneToOne)

        assertTrue(group.isGroup)
        assertNull(group.contactOrNull)
        assertEquals(oneToOne.uri, account.getByContact(Uri.fromString("bbbb"))?.uri)

        val vm = ConversationsViewModel(
            services.accountService, services.conversationFacade, StubDeviceRuntimeService(),
            services.contactService, services.vCardService, services.settingsRepository,
            viewModelScope(),
        )
        vm.loadConversations()
        advanceUntilIdle()
        vm.setFilter(net.jami.ui.viewmodel.ConversationFilter.GROUPS)
        advanceUntilIdle()

        assertEquals(listOf("group2"), vm.state.value.conversations.map { it.id })
        assertNull(vm.state.value.conversations.single().contactId)
    }

    /** A 1:1 still cloning may claim InvitesOnly; only the stated mode makes a group. */
    @Test
    fun aSyncingOneToOneIsNotAGroup() {
        val syncing = net.jami.model.Conversation(
            TEST_ACCOUNT_ID,
            Uri(Uri.SWARM_SCHEME, "syncing1"),
            mutableListOf(
                net.jami.model.Contact(Uri.fromString("me"), isUser = true),
                net.jami.model.Contact(Uri.fromString("bbbb")),
            ),
            net.jami.model.Conversation.Mode.Syncing,
        )
        syncing.requestMode = net.jami.model.Conversation.Mode.InvitesOnly
        assertTrue(!syncing.isGroup)
        assertEquals("bbbb", syncing.contactOrNull?.uri?.rawRingId)
    }
}
