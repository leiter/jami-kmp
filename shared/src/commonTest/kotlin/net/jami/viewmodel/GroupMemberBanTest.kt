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

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.jami.model.ConfigKey
import net.jami.model.MemberRole
import net.jami.services.StubDaemonBridge
import net.jami.services.StubDeviceRuntimeService
import net.jami.ui.viewmodel.ContactDetailsViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Blocking and unblocking a swarm member (messaging plan F12).
 *
 * In a swarm these are not their own daemon calls: `removeConversationMember` *is* the ban (the
 * daemon resolves the kick vote as a ban and disconnects the peer) and `addConversationMember` on
 * a banned member is routed to the unban vote. So what is tested here is that the right existing
 * call is made, and that the row survives the ban — the unblock action can only be reached from
 * it.
 */
class GroupMemberBanTest {

    private companion object {
        const val ACC = "acc1"
        const val CONV = "conv1"
        const val ME = "me"
        const val BOB = "bob"
    }

    private suspend fun TestScope.viewModel(): Pair<ContactDetailsViewModel, StubDaemonBridge> {
        val stub = StubDaemonBridge()
        stub.accountIds = listOf(ACC)
        stub.accountDetails[ACC] = mapOf(
            ConfigKey.ACCOUNT_TYPE.key to "RING",
            ConfigKey.ACCOUNT_USERNAME.key to ME,
        )
        stub.conversations[ACC] = listOf(CONV)
        // "2" = invites-only group, so the member section applies.
        stub.conversationInfo[CONV] = mapOf("mode" to "2")
        stub.conversationMembers[CONV] = listOf(
            mapOf("uri" to ME, "role" to "admin"),
            mapOf("uri" to BOB, "role" to "member"),
        )
        val accountService = makeAccountService(stub, this)
        val contactService = makeContactService(stub, accountService, this)
        val callService = makeCallService(stub, accountService, scope = this)
        val facade = makeConversationFacade(stub, accountService, callService, contactService, this)
        accountService.loadAccounts()
        advanceUntilIdle()
        facade.onConversationReady(ACC, CONV)
        advanceUntilIdle()

        val vm = ContactDetailsViewModel(
            accountService, contactService, facade, StubDeviceRuntimeService(), viewModelScope()
        )
        vm.loadContact(CONV)
        advanceUntilIdle()
        return vm to stub
    }

    @Test
    fun theGroupLoadsWithItsMembersAndTheUserAsAdmin() = runTest {
        val (vm, _) = viewModel()

        assertTrue(vm.state.value.isAdmin)
        assertTrue(BOB in vm.state.value.memberUris)
        assertEquals(MemberRole.MEMBER, vm.state.value.memberRoles[BOB])
    }

    /**
     * The row must stay, marked blocked: it used to be filtered out of memberUris, which left the
     * ban irreversible from the UI because the unblock action lives on that row.
     */
    @Test
    fun aBlockedMemberStaysListedAsBlocked() = runTest {
        val (vm, _) = viewModel()

        vm.removeMember(BOB)
        advanceUntilIdle()

        assertTrue(BOB in vm.state.value.memberUris)
        assertEquals(MemberRole.BLOCKED, vm.state.value.memberRoles[BOB])
    }

    @Test
    fun unblockingPutsTheMemberBack() = runTest {
        val (vm, _) = viewModel()

        vm.removeMember(BOB)
        advanceUntilIdle()
        vm.unbanMember(BOB)
        advanceUntilIdle()

        assertTrue(BOB in vm.state.value.memberUris)
        assertEquals(MemberRole.MEMBER, vm.state.value.memberRoles[BOB])
    }

    /** Neither action does anything without a swarm, and neither crashes. */
    @Test
    fun theActionsAreNoOpsWithoutASwarm() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val contactService = makeContactService(stub, accountService, this)
        val callService = makeCallService(stub, accountService, scope = this)
        val facade = makeConversationFacade(stub, accountService, callService, contactService, this)
        val vm = ContactDetailsViewModel(
            accountService, contactService, facade, StubDeviceRuntimeService(), viewModelScope()
        )

        vm.removeMember(BOB)
        vm.unbanMember(BOB)
        advanceUntilIdle()

        assertTrue(vm.state.value.memberUris.isEmpty())
    }
}
