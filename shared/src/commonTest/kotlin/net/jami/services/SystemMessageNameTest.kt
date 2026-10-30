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

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.jami.model.ConfigKey
import net.jami.model.ContactEvent
import net.jami.model.SwarmMessage
import net.jami.model.Uri
import net.jami.repository.DraftRepository
import net.jami.testAudioRecorderService
import net.jami.ui.viewmodel.ChatViewModel
import net.jami.ui.viewmodel.MessageItem
import net.jami.viewmodel.makeSendQueue
import net.jami.viewmodel.makeTestServiceStack
import net.jami.viewmodel.viewModelScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The "X joined" / "X was invited" rows in a group chat. Two things have to be right: the row
 * must name the member the event is *about* (not whoever authored the commit), and it must show
 * that member's registered name rather than their 40-hex Jami id.
 */
class SystemMessageNameTest {

    private companion object {
        const val ACC = "acc1"
        const val CONV = "conv1"
        const val ME = "me"
        /** The inviter: has a registered name from the start. */
        const val ALICE = "alice0000000000000000000000000000000000"
        /** The invitee: unknown to the name service until the lookup answers. */
        const val BOB = "bob00000000000000000000000000000000000b"
    }

    private fun member(id: String, action: String, uri: String, author: String) = SwarmMessage(
        id = id,
        type = "member",
        linearizedParent = "",
        body = mapOf("action" to action, "uri" to uri, "author" to author, "timestamp" to "1"),
    )

    private class Env(
        val stack: net.jami.viewmodel.TestServiceStack,
        val vm: ChatViewModel,
    ) {
        fun message(id: String): MessageItem =
            assertNotNull(vm.state.value.messages.firstOrNull { it.id == id })
    }

    /** A group chat of ME + ALICE, open on screen. */
    private suspend fun TestScope.openGroup(
        stub: StubDaemonBridge = StubDaemonBridge(),
        ownDisplayName: String? = null,
    ): Env {
        stub.accountIds = listOf(ACC)
        stub.accountDetails[ACC] = buildMap {
            put(ConfigKey.ACCOUNT_TYPE.key, "RING")
            put(ConfigKey.ACCOUNT_USERNAME.key, ME)
            ownDisplayName?.let { put(ConfigKey.ACCOUNT_DISPLAYNAME.key, it) }
        }
        stub.conversations[ACC] = listOf(CONV)
        stub.conversationInfo[CONV] = mapOf("mode" to "1")
        stub.conversationMembers[CONV] = listOf(
            mapOf("uri" to ME, "role" to "admin"),
            mapOf("uri" to ALICE, "role" to "admin"),
        )
        val runtime = StubDeviceRuntimeService()
        val stack = makeTestServiceStack(stub, this, deviceRuntimeService = runtime)
        val queue = makeSendQueue(stub, stack.accountService, stack.conversationFacade, this)
        stack.accountService.loadAccounts()
        advanceUntilIdle()
        stack.conversationFacade.onConversationReady(ACC, CONV)
        advanceUntilIdle()
        // Alice's registered name is already known; Bob's is not.
        stack.accountService.onRegisteredNameFound(ACC, 0, ALICE, "alice")
        advanceUntilIdle()

        val vmScope = viewModelScope()
        val vm = ChatViewModel(
            stack.conversationFacade, stack.accountService, runtime,
            DraftRepository(InMemoryClientSettingsStore(), vmScope), testAudioRecorderService(), queue, vmScope,
        )
        vm.loadConversation(CONV)
        advanceUntilIdle()
        return Env(stack, vm)
    }

    /**
     * A member row is about the member in `body["uri"]`. The commit author is whoever performed
     * the action — for an invite that is the *inviter*, so naming the author made the row read
     * "alice was invited" when Alice had in fact invited Bob. The history path already used
     * `body["uri"]`; only the live path disagreed, so the row changed identity on reload.
     */
    @Test
    fun aLiveMemberRowNamesTheMemberNotTheCommitAuthor() = runTest {
        val env = openGroup()
        env.stack.conversationFacade.onMessageReceived(ACC, CONV, member("m1", "add", BOB, ALICE))
        advanceUntilIdle()

        val row = env.message("m1")
        assertEquals(ContactEvent.Event.INVITED, row.contactEventType)
        assertEquals(BOB, row.author, "the row is about Bob, who was invited, not Alice who invited him")
    }

    /**
     * An id with no registered name yet must not be left on screen as 40 hex characters: the
     * lookup is requested, and the row picks the name up when the answer arrives.
     */
    @Test
    fun anUnresolvedMemberNameIsLookedUpAndFilledInWhenItArrives() = runTest {
        val stub = StubDaemonBridge()
        val env = openGroup(stub)
        env.stack.conversationFacade.onMessageReceived(ACC, CONV, member("m1", "join", BOB, BOB))
        advanceUntilIdle()

        assertEquals(BOB, env.message("m1").author, "nothing is known about Bob yet")
        assertTrue(BOB in stub.lookedUpAddresses, "an unresolved id must trigger a name lookup")

        env.stack.accountService.onRegisteredNameFound(ACC, 0, BOB, "bob")
        advanceUntilIdle()

        assertEquals("bob", env.message("m1").author, "the row shows the name once it is known")
    }

    /**
     * History is where unresolved names show most — a group's join/invite rows are rendered
     * from the swarm log before anything has looked the new members up — so that path must
     * request the lookup too, not just the live one.
     */
    @Test
    fun aMemberRowLoadedFromHistoryAlsoAsksForTheName() = runTest {
        val stub = StubDaemonBridge()
        val env = openGroup(stub)
        env.stack.conversationFacade.onSwarmLoaded(1L, ACC, CONV, listOf(member("m1", "join", BOB, BOB)))
        env.stack.accountService.resolveSwarmLoaded(1L, ACC, CONV, listOf(member("m1", "join", BOB, BOB)))
        advanceUntilIdle()

        assertEquals(BOB, env.message("m1").author)
        assertTrue(BOB in stub.lookedUpAddresses, "a hex id loaded from history must be looked up")

        env.stack.accountService.onRegisteredNameFound(ACC, 0, BOB, "bob")
        advanceUntilIdle()
        assertEquals("bob", env.message("m1").author)
    }

    /** A member whose name is already known is never shown as an id in the first place. */
    @Test
    fun aKnownMemberIsNamedImmediately() = runTest {
        val env = openGroup()
        env.stack.conversationFacade.onMessageReceived(ACC, CONV, member("m1", "join", ALICE, ALICE))
        advanceUntilIdle()

        assertEquals("alice", env.message("m1").author)
    }

    /**
     * "You joined" is the one row that can never be resolved by a lookup: the local user's own
     * Contact carries no profile, and asking the name service about yourself is pointless. It
     * has to come from the account, or the row stays 40 hex characters - which is exactly what
     * the Pixel 2 showed for its own join row.
     */
    @Test
    fun theLocalUsersOwnRowUsesTheAccountName() = runTest {
        val stub = StubDaemonBridge()
        val env = openGroup(stub, ownDisplayName = "peace4us")
        env.stack.conversationFacade.onMessageReceived(ACC, CONV, member("m1", "join", ME, ME))
        advanceUntilIdle()

        assertEquals("peace4us", env.message("m1").author)
        assertTrue(ME !in stub.lookedUpAddresses, "the local user is never looked up")
    }

    /**
     * The same applies to a row rebuilt from the swarm log, which is the path that actually
     * renders a just-accepted group's history - and the one that kept showing the Pixel 2's own
     * id after the live path had already been fixed.
     */
    @Test
    fun theLocalUsersOwnHistoryRowUsesTheAccountName() = runTest {
        val stub = StubDaemonBridge()
        val env = openGroup(stub, ownDisplayName = "peace4us")
        val log = listOf(member("m1", "join", ME, ME))
        env.stack.conversationFacade.onSwarmLoaded(1L, ACC, CONV, log)
        env.stack.accountService.resolveSwarmLoaded(1L, ACC, CONV, log)
        advanceUntilIdle()

        assertEquals("peace4us", env.message("m1").author)
        assertTrue(ME !in stub.lookedUpAddresses, "the local user is never looked up")
    }

    /** The lookup fires once per id, however many rows mention it. */
    @Test
    fun theSameUnresolvedIdIsLookedUpOnlyOnce() = runTest {
        val stub = StubDaemonBridge()
        val env = openGroup(stub)
        env.stack.conversationFacade.onMessageReceived(ACC, CONV, member("m1", "add", BOB, ALICE))
        env.stack.conversationFacade.onMessageReceived(ACC, CONV, member("m2", "join", BOB, BOB))
        advanceUntilIdle()

        assertEquals(1, stub.lookedUpAddresses.count { it == BOB })
    }

    /** A failed lookup (state != 0) must not write the empty name over the id. */
    @Test
    fun aFailedLookupLeavesTheIdInPlace() = runTest {
        val env = openGroup()
        env.stack.conversationFacade.onMessageReceived(ACC, CONV, member("m1", "join", BOB, BOB))
        advanceUntilIdle()

        env.stack.accountService.onRegisteredNameFound(ACC, 1, BOB, "")
        advanceUntilIdle()

        assertEquals(BOB, env.message("m1").author)
    }

    /** Uri form is irrelevant: a `jami:`-prefixed member uri resolves to the same contact. */
    @Test
    fun aSchemePrefixedMemberUriStillResolves() = runTest {
        val env = openGroup()
        env.stack.conversationFacade.onMessageReceived(
            ACC, CONV, member("m1", "join", Uri.fromId(ALICE).uri, ALICE)
        )
        advanceUntilIdle()

        assertEquals("alice", env.message("m1").author)
    }
}
