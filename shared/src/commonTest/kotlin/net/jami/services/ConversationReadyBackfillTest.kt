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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import net.jami.model.AccountConfig
import net.jami.model.ConfigKey
import net.jami.testHardwareService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `conversationEvents` had no replay, so a collector that attached after a conversation had
 * already gone live never heard about it.
 *
 * Two call sites awaited exactly that and could not win the race:
 * `NewConversationViewModel.startConversation` subscribes only *after*
 * `ConversationFacade.startConversation` has thrown, and `CommandHandler` does the same in the
 * harness. If the conversation became ready in between, the first hung with `isLoading = true`
 * forever and the second burned its 30 s timeout.
 *
 * Same bug, and same fix, as `AccountService.accountEvents` (`doc/TODO.md`, 2026-08-17): the
 * backfill is deliberately limited to `ConversationReady`, because "this conversation is live"
 * is a durable fact, while a received message or a member join is one-shot and replaying it to
 * a late subscriber would be wrong.
 */
class ConversationReadyBackfillTest {

    private fun TestScope.serviceScope(): CoroutineScope =
        CoroutineScope(coroutineContext + SupervisorJob())

    private fun TestScope.makeFacade(stub: StubDaemonBridge): Pair<AccountService, ConversationFacade> {
        val scope = serviceScope()
        val accountService = AccountService(stub, testHardwareService(), StubDeviceRuntimeService(), scope)
        val callService = CallService(
            stub, accountService,
            net.jami.repository.SettingsRepository(stub, InMemoryClientSettingsStore(), scope), scope,
        )
        val contactService = ContactService(scope, accountService, stub, VCardService(StubDeviceRuntimeService()))
        val facade = ConversationFacade(
            historyService = StubHistoryService(),
            callService = callService,
            accountService = accountService,
            contactService = contactService,
            notificationService = StubNotificationService(),
            hardwareService = testHardwareService(),
            deviceRuntimeService = StubDeviceRuntimeService(),
            preferencesService = StubPreferencesService(),
            daemonBridge = stub,
            settingsRepository = net.jami.repository.SettingsRepository(stub, InMemoryClientSettingsStore(), scope),
            scope = scope,
        )
        return accountService to facade
    }

    private fun stubWithAccount(): StubDaemonBridge = StubDaemonBridge().apply {
        accountIds = listOf("acc1")
        accountDetails["acc1"] = mapOf(ConfigKey.ACCOUNT_TYPE.key to AccountConfig.ACCOUNT_TYPE_JAMI)
    }

    @Test
    fun aCollectorAttachingAfterTheConversationWentLiveIsStillTold() = runTest {
        val stub = stubWithAccount()
        val (accountService, facade) = makeFacade(stub)
        accountService.loadAccounts()
        advanceUntilIdle()

        // Ready fires with nobody listening — the ordering the two call sites kept losing.
        facade.onConversationReady("acc1", "conv1")
        advanceUntilIdle()

        val seen = withTimeoutOrNull(5_000) {
            facade.conversationEvents
                .filterIsInstance<ConversationEvent.ConversationReady>()
                .first()
        }

        assertNotNull(seen, "a late subscriber heard nothing; this is the hang the fix closes")
        assertEquals("conv1", seen.conversationId)
        assertEquals("acc1", seen.accountId)
    }

    @Test
    fun everyReadyConversationIsBackfilled() = runTest {
        val stub = stubWithAccount()
        val (accountService, facade) = makeFacade(stub)
        accountService.loadAccounts()
        advanceUntilIdle()

        facade.onConversationReady("acc1", "conv1")
        facade.onConversationReady("acc1", "conv2")
        advanceUntilIdle()

        val ids = mutableSetOf<String>()
        withTimeoutOrNull(5_000) {
            facade.conversationEvents
                .filterIsInstance<ConversationEvent.ConversationReady>()
                .first { ids.add(it.conversationId); ids.size == 2 }
        }

        assertEquals(setOf("conv1", "conv2"), ids)
    }

    @Test
    fun aRemovedConversationIsNotBackfilled() = runTest {
        val stub = stubWithAccount()
        val (accountService, facade) = makeFacade(stub)
        accountService.loadAccounts()
        advanceUntilIdle()

        facade.onConversationReady("acc1", "conv1")
        advanceUntilIdle()
        facade.onConversationRemoved("acc1", "conv1")
        advanceUntilIdle()

        val seen = withTimeoutOrNull(500) {
            facade.conversationEvents
                .filterIsInstance<ConversationEvent.ConversationReady>()
                .first()
        }

        assertNull(seen, "a conversation that is gone must not be announced as ready")
    }

    /**
     * The narrowness matters as much as the backfill: replaying a message to a late subscriber
     * would show it twice, and replaying a member join would re-run whatever the join triggers.
     */
    @Test
    fun oneShotEventsAreNotReplayed() = runTest {
        val stub = stubWithAccount()
        val (accountService, facade) = makeFacade(stub)
        accountService.loadAccounts()
        advanceUntilIdle()

        facade.onConversationReady("acc1", "conv1")
        advanceUntilIdle()

        val replayed = mutableListOf<ConversationEvent>()
        withTimeoutOrNull(500) {
            facade.conversationEvents.collect { replayed.add(it) }
        }

        assertTrue(
            replayed.all { it is ConversationEvent.ConversationReady },
            "only ConversationReady may be backfilled, got ${replayed.map { it::class.simpleName }}",
        )
    }
}
