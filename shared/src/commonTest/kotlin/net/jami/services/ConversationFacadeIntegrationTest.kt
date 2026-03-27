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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.jami.model.AccountConfig
import net.jami.model.ConfigKey
import net.jami.model.Conversation
import net.jami.model.Uri
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for ConversationFacade using StubDaemonBridge.
 * Tests conversation operations with real service wiring.
 */
class ConversationFacadeIntegrationTest {

    /**
     * Creates a scope that inherits the test scheduler but won't cause
     * UncompletedCoroutinesError from ConversationFacade's init collectors.
     */
    private fun kotlinx.coroutines.test.TestScope.facadeScope(): CoroutineScope =
        CoroutineScope(coroutineContext + SupervisorJob())

    private fun makeFacade(
        stub: StubDaemonBridge,
        scope: kotlinx.coroutines.test.TestScope
    ): Triple<AccountService, ContactService, ConversationFacade> {
        val accountService = AccountService(stub, scope)
        val callService = CallService(stub, accountService, scope)
        val contactService = ContactService(scope, accountService, stub)
        val facadeScope = scope.facadeScope()
        val facade = ConversationFacade(
            historyService = StubHistoryService(),
            callService = callService,
            accountService = accountService,
            contactService = contactService,
            notificationService = StubNotificationService(),
            hardwareService = StubHardwareService(),
            deviceRuntimeService = StubDeviceRuntimeService(),
            preferencesService = StubPreferencesService(),
            daemonBridge = stub,
            scope = facadeScope
        )
        return Triple(accountService, contactService, facade)
    }

    @Test
    fun initialConversationListIsEmpty() = runTest {
        val stub = StubDaemonBridge()
        val (accountService, _, facade) = makeFacade(stub, this)

        assertTrue(facade.conversationList.value.conversations.isEmpty())
    }

    @Test
    fun getConversationReturnsNullForUnknown() = runTest {
        val stub = StubDaemonBridge()
        val (accountService, _, facade) = makeFacade(stub, this)

        val result = facade.getConversation("acc1", Uri.fromString("swarm:unknown"))
        assertNull(result)
    }

    @Test
    fun startConversationWithContactCreatesConversation() = runTest {
        val stub = StubDaemonBridge()
        stub.accountIds = listOf("acc1")
        stub.accountDetails["acc1"] = mapOf(ConfigKey.ACCOUNT_TYPE.key to AccountConfig.ACCOUNT_TYPE_JAMI)
        stub.startConversationResult = "conv_001"
        val (accountService, _, facade) = makeFacade(stub, this)
        accountService.loadAccounts()
        advanceUntilIdle()

        val conversation = facade.startConversation("acc1", Uri.fromId("peer123"))
        assertNotNull(conversation)
    }

    @Test
    fun setConversationPreferencesDoesNotCrash() = runTest {
        val stub = StubDaemonBridge()
        val (accountService, _, facade) = makeFacade(stub, this)

        facade.setConversationPreferences(
            "acc1",
            Uri.fromString("swarm:conv123"),
            mapOf("color" to "#FF0000")
        )
        // No crash
    }

    @Test
    fun setIsComposingDoesNotCrash() = runTest {
        val stub = StubDaemonBridge()
        val (accountService, _, facade) = makeFacade(stub, this)

        facade.setIsComposing("acc1", Uri.fromString("swarm:conv123"), true)
        // No crash
    }

    @Test
    fun searchResultIsInitiallyEmpty() = runTest {
        val stub = StubDaemonBridge()
        val (accountService, _, facade) = makeFacade(stub, this)

        val list = facade.conversationList.value
        assertEquals("", list.latestQuery)
    }
}
