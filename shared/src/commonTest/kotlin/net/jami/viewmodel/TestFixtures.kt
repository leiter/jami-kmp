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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.TestScope
import net.jami.model.ConfigKey
import net.jami.repository.SettingsRepository
import net.jami.services.AccountService
import net.jami.services.CallService
import net.jami.services.ContactService
import net.jami.services.ConversationFacade
import net.jami.services.StubDaemonBridge
import net.jami.services.StubDeviceRuntimeService
import net.jami.services.StubHardwareService
import net.jami.services.StubHistoryService
import net.jami.services.StubNotificationService
import net.jami.services.StubPreferencesService

// ==================== Disposable scope for onCleared tests ====================

/**
 * Creates an independent CoroutineScope that can be cancelled (via ViewModel.onCleared)
 * without cancelling the enclosing TestScope.
 */
fun disposableScope(): CoroutineScope = CoroutineScope(SupervisorJob())

/**
 * Creates a CoroutineScope that inherits the test's [TestCoroutineScheduler]
 * (so [advanceUntilIdle] advances its coroutines) but uses its own [SupervisorJob]
 * (so infinite SharedFlow collectors don't cause [UncompletedCoroutinesError]).
 *
 * Use this instead of [backgroundScope] when constructing ViewModels in tests.
 */
fun TestScope.viewModelScope(): CoroutineScope =
    CoroutineScope(coroutineContext + SupervisorJob())

// ==================== Default test account ID ====================

const val TEST_ACCOUNT_ID = "acc_test_001"

// ==================== Service factory functions ====================

/**
 * Creates an AccountService wired to the given stub and scope.
 */
fun makeAccountService(
    stub: StubDaemonBridge = StubDaemonBridge(),
    scope: CoroutineScope
): AccountService = AccountService(stub, scope)

/**
 * Creates a CallService wired to the given stub, account service, and scope.
 */
fun makeCallService(
    stub: StubDaemonBridge = StubDaemonBridge(),
    accountService: AccountService,
    scope: CoroutineScope
): CallService = CallService(stub, accountService, scope)

/**
 * Creates a ContactService wired to the given stub, account service, and scope.
 */
fun makeContactService(
    stub: StubDaemonBridge = StubDaemonBridge(),
    accountService: AccountService,
    scope: CoroutineScope
): ContactService = ContactService(scope, accountService, stub)

/**
 * Creates a SettingsRepository wired to the given stub and scope.
 */
fun makeSettingsRepository(
    stub: StubDaemonBridge = StubDaemonBridge(),
    scope: CoroutineScope
): SettingsRepository = SettingsRepository(stub, scope)

/**
 * Creates a ConversationFacade using all stub service implementations.
 * Pass real services to override specific dependencies.
 */
fun makeConversationFacade(
    stub: StubDaemonBridge = StubDaemonBridge(),
    accountService: AccountService,
    callService: CallService,
    contactService: ContactService,
    scope: CoroutineScope
): ConversationFacade = ConversationFacade(
    historyService = StubHistoryService(),
    callService = callService,
    accountService = accountService,
    contactService = contactService,
    notificationService = StubNotificationService(),
    hardwareService = StubHardwareService(),
    deviceRuntimeService = StubDeviceRuntimeService(),
    preferencesService = StubPreferencesService(),
    daemonBridge = stub,
    settingsRepository = SettingsRepository(stub, scope),
    scope = scope
)

// ==================== Convenience: full service stack from one stub ====================

/**
 * Creates a full service stack from a single StubDaemonBridge and scope.
 * Useful when tests need all services but don't care about their interactions.
 */
data class TestServiceStack(
    val stub: StubDaemonBridge,
    val accountService: AccountService,
    val callService: CallService,
    val contactService: ContactService,
    val conversationFacade: ConversationFacade,
    val settingsRepository: SettingsRepository
)

fun makeTestServiceStack(
    stub: StubDaemonBridge = StubDaemonBridge(),
    scope: CoroutineScope
): TestServiceStack {
    val accountService = makeAccountService(stub, scope)
    val callService = makeCallService(stub, accountService, scope)
    val contactService = makeContactService(stub, accountService, scope)
    val conversationFacade = makeConversationFacade(stub, accountService, callService, contactService, scope)
    val settingsRepository = makeSettingsRepository(stub, scope)
    return TestServiceStack(stub, accountService, callService, contactService, conversationFacade, settingsRepository)
}

// ==================== Convenience: pre-loaded test account ====================

/**
 * Configures the stub with one JAMI test account and calls loadAccounts()
 * so that AccountService.currentAccount is populated.
 */
fun prepareAccountInService(
    stub: StubDaemonBridge,
    accountService: AccountService,
    accountId: String = TEST_ACCOUNT_ID,
    displayName: String = "Test User"
) {
    stub.accountIds = listOf(accountId)
    stub.accountDetails[accountId] = mapOf(
        ConfigKey.ACCOUNT_TYPE.key to "RING",
        ConfigKey.ACCOUNT_DISPLAYNAME.key to displayName
    )
    accountService.loadAccounts()
}
