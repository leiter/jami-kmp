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
import net.jami.services.StubDaemonBridge
import net.jami.services.StubDeviceRuntimeService
import net.jami.model.settings.ConversationSort
import net.jami.ui.viewmodel.ConversationsViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.jami.services.VCardService

class ConversationsViewModelTest {

    @Test
    fun initialStateHasEmptyConversations() = runTest {
        val stub = StubDaemonBridge()
        val services = makeTestServiceStack(stub, this)
        val vm = ConversationsViewModel(services.accountService, services.conversationFacade, StubDeviceRuntimeService(), services.contactService, services.vCardService, services.settingsRepository, viewModelScope())
        advanceUntilIdle()
        assertTrue(vm.state.value.conversations.isEmpty())
    }

    @Test
    fun initialSearchQueryIsEmpty() = runTest {
        val stub = StubDaemonBridge()
        val services = makeTestServiceStack(stub, this)
        val vm = ConversationsViewModel(services.accountService, services.conversationFacade, StubDeviceRuntimeService(), services.contactService, services.vCardService, services.settingsRepository, viewModelScope())
        assertEquals("", vm.state.value.searchQuery)
    }

    @Test
    fun loadConversationsWithNoAccountReturnsEmpty() = runTest {
        val stub = StubDaemonBridge()
        val services = makeTestServiceStack(stub, this)
        val vm = ConversationsViewModel(services.accountService, services.conversationFacade, StubDeviceRuntimeService(), services.contactService, services.vCardService, services.settingsRepository, viewModelScope())
        vm.loadConversations()
        advanceUntilIdle()
        assertTrue(vm.state.value.conversations.isEmpty())
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun searchUpdatesQuery() = runTest {
        val stub = StubDaemonBridge()
        val services = makeTestServiceStack(stub, this)
        val vm = ConversationsViewModel(services.accountService, services.conversationFacade, StubDeviceRuntimeService(), services.contactService, services.vCardService, services.settingsRepository, viewModelScope())
        vm.search("alice")
        assertEquals("alice", vm.state.value.searchQuery)
    }

    @Test
    fun searchWithAccountDoesNotCrash() = runTest {
        val stub = StubDaemonBridge()
        val services = makeTestServiceStack(stub, this)
        prepareAccountInService(stub, services.accountService)
        val vm = ConversationsViewModel(services.accountService, services.conversationFacade, StubDeviceRuntimeService(), services.contactService, services.vCardService, services.settingsRepository, viewModelScope())
        advanceUntilIdle()
        vm.search("alice")
        advanceUntilIdle()
        assertEquals("alice", vm.state.value.searchQuery)
    }

    @Test
    fun refreshDoesNotCrash() = runTest {
        val stub = StubDaemonBridge()
        val services = makeTestServiceStack(stub, this)
        val vm = ConversationsViewModel(services.accountService, services.conversationFacade, StubDeviceRuntimeService(), services.contactService, services.vCardService, services.settingsRepository, viewModelScope())
        vm.refresh()
        advanceUntilIdle()
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun removeConversationWithNoAccountDoesNotCrash() = runTest {
        val stub = StubDaemonBridge()
        val services = makeTestServiceStack(stub, this)
        val vm = ConversationsViewModel(services.accountService, services.conversationFacade, StubDeviceRuntimeService(), services.contactService, services.vCardService, services.settingsRepository, viewModelScope())
        vm.removeConversation("conv123")
        advanceUntilIdle()
        // No crash
    }

    @Test
    fun loadConversationsWithAccountSucceeds() = runTest {
        val stub = StubDaemonBridge()
        val services = makeTestServiceStack(stub, this)
        prepareAccountInService(stub, services.accountService)
        val vm = ConversationsViewModel(services.accountService, services.conversationFacade, StubDeviceRuntimeService(), services.contactService, services.vCardService, services.settingsRepository, viewModelScope())
        advanceUntilIdle()
        vm.loadConversations()
        advanceUntilIdle()
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun onClearedDoesNotThrow() = runTest {
        val stub = StubDaemonBridge()
        val services = makeTestServiceStack(stub, this)
        val vm = ConversationsViewModel(services.accountService, services.conversationFacade, StubDeviceRuntimeService(), services.contactService, services.vCardService, services.settingsRepository, disposableScope())
        vm.onCleared()
    }

    @Test
    fun conversationsAreSortedByTimestampDescending() = runTest {
        val stub = StubDaemonBridge()
        val services = makeTestServiceStack(stub, this)
        prepareAccountInService(stub, services.accountService)
        advanceUntilIdle()
        // Re-fetch from the service rather than using prepareAccountInService's return value:
        // it falls back to constructing a detached Account when loadAccounts() has not
        // settled yet, and conversations added to that orphan are invisible to the ViewModel.
        val account = services.accountService.getAccount(TEST_ACCOUNT_ID)!!

        // Create conversations with different timestamps
        val conv1 = account.newSwarm("conv1", net.jami.model.Conversation.Mode.OneToOne)
        val conv2 = account.newSwarm("conv2", net.jami.model.Conversation.Mode.OneToOne)
        val conv3 = account.newSwarm("conv3", net.jami.model.Conversation.Mode.OneToOne)

        // Add text messages with different timestamps
        val msg1 = net.jami.model.TextMessage(null, account.accountId, null, conv1, "oldest")
        msg1.timestamp = 1000L
        conv1.addElement(msg1)

        val msg2 = net.jami.model.TextMessage(null, account.accountId, null, conv2, "newest")
        msg2.timestamp = 3000L
        conv2.addElement(msg2)

        val msg3 = net.jami.model.TextMessage(null, account.accountId, null, conv3, "middle")
        msg3.timestamp = 2000L
        conv3.addElement(msg3)

        // addElement only appends to history; it does not set lastEvent, and lastEvent is
        // what ConversationItem.timestamp reads. Without this all three sort as timestamp 0
        // and keep insertion order, so the test proved nothing about sorting.
        // Marked incoming deliberately. getLastEventSummary prefixes outgoing messages with
        // a localized "You:" via Compose's suspending getString, which never completes under
        // runTest — buildConversationItems then hangs and the list stays empty.
        msg1.isIncoming = true
        msg2.isIncoming = true
        msg3.isIncoming = true

        conv1.lastEvent = msg1
        conv2.lastEvent = msg2
        conv3.lastEvent = msg3

        account.conversationStarted(conv1)
        account.conversationStarted(conv2)
        account.conversationStarted(conv3)

        val vm = ConversationsViewModel(services.accountService, services.conversationFacade, StubDeviceRuntimeService(), services.contactService, services.vCardService, services.settingsRepository, viewModelScope())
        // Load explicitly rather than relying on the init-time currentAccount observer, as
        // the other tests in this class do.
        vm.loadConversations()
        advanceUntilIdle()

        // Verify conversations are sorted by timestamp descending (most recent first)
        val conversations = vm.state.value.conversations
        assertTrue(conversations.size >= 3, "Expected at least 3 conversations")

        // Find our test conversations
        val items = conversations.filter { it.id in listOf("conv1", "conv2", "conv3") }
        assertEquals(3, items.size, "Expected 3 test conversations")

        // Verify order: conv2 (3000) > conv3 (2000) > conv1 (1000)
        assertEquals("conv2", items[0].id, "Expected conv2 (newest) first")
        assertEquals("conv3", items[1].id, "Expected conv3 (middle) second")
        assertEquals("conv1", items[2].id, "Expected conv1 (oldest) third")
    }

    /**
     * Two invitations from the same person are two separate rows. They share a `ringId`
     * (the sender), so anything that identifies a row by `ringId` collapses them — in the
     * UI that surfaced as `IllegalArgumentException: Key "<ringId>" was already used` from
     * the pending-requests LazyColumn the moment a second invite from a known contact
     * arrived. Rows must be identified by `conversationUri`, which is what the loader
     * already de-duplicates on.
     */
    @Test
    fun twoRequestsFromTheSameContactStayTwoDistinctRows() = runTest {
        val stub = StubDaemonBridge()
        val services = makeTestServiceStack(stub, this)
        prepareAccountInService(stub, services.accountService)
        val sender = "66d57f55227639b58fdcc6d74ec090446ffa9bc3"
        stub.conversationRequests += mapOf("id" to "conva", "from" to sender)
        stub.conversationRequests += mapOf("id" to "convb", "from" to sender)

        val vm = ConversationsViewModel(services.accountService, services.conversationFacade, StubDeviceRuntimeService(), services.contactService, services.vCardService, services.settingsRepository, viewModelScope())
        vm.loadConversations()
        advanceUntilIdle()

        val requests = vm.state.value.pendingRequestItems
        assertEquals(2, requests.size, "Both invitations from the same contact must be listed")
        assertEquals(1, requests.map { it.ringId }.distinct().size, "Both come from the same sender")
        assertEquals(2, requests.map { it.conversationUri }.distinct().size, "Conversation URIs must differ")
    }

    /**
     * Helper for the sort tests: three 1:1 swarms whose ids double as their display names
     * (Conversation.title falls back to the swarm id when no member has a name), with
     * timestamps deliberately in the opposite order to the alphabet — so a test can only
     * pass by actually honouring the sort setting.
     */
    private suspend fun seedNamedConversations(account: net.jami.model.Account) {
        // name -> timestamp, reverse-alphabetical by recency: charlie newest, alice oldest.
        listOf("alice" to 1000L, "bob" to 2000L, "charlie" to 3000L).forEach { (name, ts) ->
            val conv = account.newSwarm(name, net.jami.model.Conversation.Mode.OneToOne)
            val msg = net.jami.model.TextMessage(null, account.accountId, null, conv, name)
            msg.timestamp = ts
            // Incoming: getLastEventSummary prefixes outgoing messages through Compose's
            // suspending getString, which never completes under runTest.
            msg.isIncoming = true
            conv.addElement(msg)
            conv.lastEvent = msg
            account.conversationStarted(conv)
        }
    }

    private fun List<net.jami.ui.viewmodel.ConversationItem>.seeded() =
        filter { it.id in listOf("alice", "bob", "charlie") }.map { it.id }

    /**
     * B15: `conversationSort` was stored and shown in Settings but `ConversationsViewModel`
     * sorted with a hardcoded `pinned desc, timestamp desc` comparator, so picking
     * "alphabetical" changed nothing.
     */
    @Test
    fun alphabeticalSortOrdersConversationsByName() = runTest {
        val stub = StubDaemonBridge()
        val services = makeTestServiceStack(stub, this)
        prepareAccountInService(stub, services.accountService)
        advanceUntilIdle()
        seedNamedConversations(services.accountService.getAccount(TEST_ACCOUNT_ID)!!)
        services.settingsRepository.updateConversationSort(ConversationSort.ALPHABETICAL)

        val vm = ConversationsViewModel(services.accountService, services.conversationFacade, StubDeviceRuntimeService(), services.contactService, services.vCardService, services.settingsRepository, viewModelScope())
        vm.loadConversations()
        advanceUntilIdle()

        assertEquals(listOf("alice", "bob", "charlie"), vm.state.value.conversations.seeded())
    }

    /** The default sort is unchanged by the B15 fix: most recent activity first. */
    @Test
    fun lastActivitySortRemainsTheDefault() = runTest {
        val stub = StubDaemonBridge()
        val services = makeTestServiceStack(stub, this)
        prepareAccountInService(stub, services.accountService)
        advanceUntilIdle()
        seedNamedConversations(services.accountService.getAccount(TEST_ACCOUNT_ID)!!)

        val vm = ConversationsViewModel(services.accountService, services.conversationFacade, StubDeviceRuntimeService(), services.contactService, services.vCardService, services.settingsRepository, viewModelScope())
        vm.loadConversations()
        advanceUntilIdle()

        assertEquals(listOf("charlie", "bob", "alice"), vm.state.value.conversations.seeded())
    }

    /**
     * Unread-first sorts on "has unread messages", then falls back to recency inside each
     * group. All three seeded conversations end up unread, so the fallback is what the
     * order shows — the point being that the mode is a total order, not a partial one that
     * reshuffles equal rows between reloads.
     */
    @Test
    fun unreadFirstSortKeepsRecencyWithinTheUnreadGroup() = runTest {
        val stub = StubDaemonBridge()
        val services = makeTestServiceStack(stub, this)
        prepareAccountInService(stub, services.accountService)
        advanceUntilIdle()
        seedNamedConversations(services.accountService.getAccount(TEST_ACCOUNT_ID)!!)
        services.settingsRepository.updateConversationSort(ConversationSort.UNREAD_FIRST)

        val vm = ConversationsViewModel(services.accountService, services.conversationFacade, StubDeviceRuntimeService(), services.contactService, services.vCardService, services.settingsRepository, viewModelScope())
        vm.loadConversations()
        advanceUntilIdle()

        assertEquals(listOf("charlie", "bob", "alice"), vm.state.value.conversations.seeded())
    }

    /**
     * The setting is DHT-synced, so it can change while the list is on screen (from this
     * device's Settings screen or another device). That must re-sort what is already loaded,
     * without a daemon round-trip.
     */
    @Test
    fun changingTheSortSettingResortsTheLoadedList() = runTest {
        val stub = StubDaemonBridge()
        val services = makeTestServiceStack(stub, this)
        prepareAccountInService(stub, services.accountService)
        advanceUntilIdle()
        seedNamedConversations(services.accountService.getAccount(TEST_ACCOUNT_ID)!!)

        val vm = ConversationsViewModel(services.accountService, services.conversationFacade, StubDeviceRuntimeService(), services.contactService, services.vCardService, services.settingsRepository, viewModelScope())
        vm.loadConversations()
        advanceUntilIdle()
        assertEquals(listOf("charlie", "bob", "alice"), vm.state.value.conversations.seeded())

        services.settingsRepository.updateConversationSort(ConversationSort.ALPHABETICAL)
        advanceUntilIdle()

        assertEquals(listOf("alice", "bob", "charlie"), vm.state.value.conversations.seeded())
    }

    /**
     * B16: `compactMode` was stored and rendered as a toggle, but no composable read it.
     * The list density now comes from this state field, which mirrors the synced setting.
     */
    @Test
    fun compactModeSettingReachesTheListState() = runTest {
        val stub = StubDaemonBridge()
        val services = makeTestServiceStack(stub, this)
        val vm = ConversationsViewModel(services.accountService, services.conversationFacade, StubDeviceRuntimeService(), services.contactService, services.vCardService, services.settingsRepository, viewModelScope())
        advanceUntilIdle()
        assertFalse(vm.state.value.isCompactMode, "Compact mode is off by default")

        services.settingsRepository.updateCompactMode(true)
        advanceUntilIdle()
        assertTrue(vm.state.value.isCompactMode)

        services.settingsRepository.updateCompactMode(false)
        advanceUntilIdle()
        assertFalse(vm.state.value.isCompactMode)
    }
}
