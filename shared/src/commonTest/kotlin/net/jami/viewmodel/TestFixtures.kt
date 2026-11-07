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

import net.jami.testHardwareService

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.TestScope
import net.jami.model.ConfigKey
import net.jami.repository.SettingsRepository
import net.jami.services.InMemoryClientSettingsStore
import net.jami.services.AccountService
import net.jami.services.CallService
import net.jami.services.ContactService
import net.jami.services.VCardService
import net.jami.services.ConversationFacade
import net.jami.services.ConversationFileStore
import net.jami.services.DeviceRuntimeService
import net.jami.services.CertificateService
import net.jami.services.DaemonCallbacks
import net.jami.services.DaemonCallbacksImpl
import net.jami.services.PeerServicesService
import net.jami.services.StubDaemonBridge
import net.jami.services.StubDeviceRuntimeService
import net.jami.services.expect.HardwareService
import net.jami.services.StubHistoryService
import net.jami.services.NotificationService
import net.jami.services.PreferencesService
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
 * Isolates a service's coroutines from the enclosing TestScope.
 *
 * Services launch long-lived flow collectors. If they run as children of the TestScope,
 * runTest waits for them and every test fails with UncompletedCoroutinesError after a
 * one-minute timeout. Inheriting the coroutineContext keeps the test scheduler — so
 * advanceUntilIdle still drives them — while the fresh SupervisorJob detaches them from
 * the test's own Job. makeAccountService already did this; the rest did not.
 */
private fun CoroutineScope.isolated(): CoroutineScope =
    CoroutineScope(coroutineContext + SupervisorJob())


/**
 * Creates an AccountService wired to the given stub and scope.
 * Uses a child SupervisorJob but inherits the test's dispatcher so advanceUntilIdle works.
 */
fun makeAccountService(
    stub: StubDaemonBridge = StubDaemonBridge(),
    scope: CoroutineScope
): AccountService {
    // Use a child SupervisorJob so the service's infinite flow collectors don't
    // leave uncompleted coroutines in the enclosing TestScope.
    // But inherit the test's coroutine context so advanceUntilIdle() works.
    return AccountService(stub, testHardwareService(), StubDeviceRuntimeService(), scope.isolated())
}

/**
 * Creates a CallService wired to the given stub, account service, and scope.
 */
fun makeCallService(
    stub: StubDaemonBridge = StubDaemonBridge(),
    accountService: AccountService,
    settingsRepository: SettingsRepository = SettingsRepository(stub, InMemoryClientSettingsStore(), CoroutineScope(SupervisorJob())),
    scope: CoroutineScope
): CallService = CallService(stub, accountService, settingsRepository, scope.isolated())

/**
 * Creates a ContactService wired to the given stub, account service, and scope.
 */
fun makeContactService(
    stub: StubDaemonBridge = StubDaemonBridge(),
    accountService: AccountService,
    scope: CoroutineScope
): ContactService = ContactService(scope.isolated(), accountService, stub, VCardService(StubDeviceRuntimeService()))

/**
 * Creates a SettingsRepository wired to the given stub and scope.
 */
fun makeSettingsRepository(
    stub: StubDaemonBridge = StubDaemonBridge(),
    scope: CoroutineScope
): SettingsRepository = SettingsRepository(stub, InMemoryClientSettingsStore(), scope.isolated())

/**
 * Creates a ConversationFacade using all stub service implementations.
 * Pass real services to override specific dependencies.
 */
fun makeConversationFacade(
    stub: StubDaemonBridge = StubDaemonBridge(),
    accountService: AccountService,
    callService: CallService,
    contactService: ContactService,
    scope: CoroutineScope,
    deviceRuntimeService: DeviceRuntimeService = StubDeviceRuntimeService(),
    fileStore: ConversationFileStore = RecordingFileStore(),
    settingsRepository: SettingsRepository = SettingsRepository(stub, InMemoryClientSettingsStore(), scope),
    notificationService: NotificationService = StubNotificationService(),
    preferencesService: PreferencesService = StubPreferencesService(),
): ConversationFacade = ConversationFacade(
    historyService = StubHistoryService(),
    callService = callService,
    accountService = accountService,
    contactService = contactService,
    notificationService = notificationService,
    hardwareService = testHardwareService(),
    deviceRuntimeService = deviceRuntimeService,
    preferencesService = preferencesService,
    daemonBridge = stub,
    settingsRepository = settingsRepository,
    scope = scope.isolated(),
    fileStore = fileStore,
)

/**
 * A [NotificationService] that records what it was asked to show, so tests can assert on
 * notifications without a platform. Everything it does not record is delegated to
 * [StubNotificationService].
 */
class RecordingNotificationService(
    private val delegate: NotificationService = StubNotificationService()
) : NotificationService by delegate {
    /** One entry per shown contact-request notification, in order, by account id. */
    val shownTrustRequests: MutableList<String> = mutableListOf()

    /** One entry per cancelled contact-request notification, in order, by account id. */
    val cancelledTrustRequests: MutableList<String> = mutableListOf()

    override fun showIncomingTrustRequestNotification(account: net.jami.model.Account) {
        shownTrustRequests += account.accountId
    }

    override fun cancelTrustRequestNotification(accountId: String) {
        cancelledTrustRequests += accountId
    }
}

/**
 * A [ConversationFileStore] that reports success without touching the disk, so tests can reach
 * the daemon call at the end of [ConversationFacade.sendFile].
 */
class RecordingFileStore(private val succeeds: Boolean = true) : ConversationFileStore {
    val moves: MutableList<Pair<String, String>> = mutableListOf()
    val copies: MutableList<Pair<String, String>> = mutableListOf()

    override fun move(sourcePath: String, destinationPath: String): Boolean {
        moves += sourcePath to destinationPath
        return succeeds
    }

    override fun copy(sourcePath: String, destinationPath: String): Boolean {
        copies += sourcePath to destinationPath
        return succeeds
    }
}

/**
 * Creates a SendQueueService (durable outbox) backed by an in-memory store and started, so it
 * reacts to the facade's conversation events like in the app.
 */
fun makeSendQueue(
    stub: StubDaemonBridge,
    accountService: AccountService,
    conversationFacade: ConversationFacade,
    scope: CoroutineScope,
    store: net.jami.services.OutboxStore = net.jami.services.InMemoryOutboxStore(),
): net.jami.services.SendQueueService =
    net.jami.services.SendQueueService(store, accountService, conversationFacade, stub, scope.isolated())
        .also { it.start() }

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
    val settingsRepository: SettingsRepository,
    val vCardService: VCardService,
    /** The same instance the facade notifies, so tests can assert on what it was told. */
    val notificationService: RecordingNotificationService,
    /** Shared with the facade, so request-notification dedupe is testable. */
    val preferencesService: PreferencesService,
)

fun makeTestServiceStack(
    stub: StubDaemonBridge = StubDaemonBridge(),
    scope: CoroutineScope,
    deviceRuntimeService: DeviceRuntimeService = StubDeviceRuntimeService(),
    fileStore: ConversationFileStore = RecordingFileStore(),
): TestServiceStack {
    // The services launch long-lived collectors. Handing them the TestScope directly makes
    // runTest wait on those collectors and fail every test with UncompletedCoroutinesError
    // after a one-minute timeout. This keeps the test scheduler — so advanceUntilIdle still
    // drives them — but gives them their own Job so runTest does not await them. Same
    // reasoning as viewModelScope() above.
    val accountService = makeAccountService(stub, scope)
    // One SettingsRepository for the whole stack. Each service used to build its own, so a
    // test that changed a setting through `stack.settingsRepository` was writing to an
    // instance no service read — the facade and the call service kept their own defaults, and
    // any settings-driven behaviour was untestable while looking covered.
    val settingsRepository = makeSettingsRepository(stub, scope)
    val callService = makeCallService(stub, accountService, settingsRepository, scope)
    val contactService = makeContactService(stub, accountService, scope)
    val notificationService = RecordingNotificationService()
    val preferencesService = StubPreferencesService()
    val conversationFacade = makeConversationFacade(
        stub, accountService, callService, contactService, scope, deviceRuntimeService, fileStore,
        settingsRepository, notificationService, preferencesService,
    )
    val vCardService = VCardService(StubDeviceRuntimeService())
    return TestServiceStack(
        stub, accountService, callService, contactService, conversationFacade, settingsRepository,
        vCardService, notificationService, preferencesService,
    )
}

/**
 * Registers a real [net.jami.services.DaemonCallbacksImpl] over [stack], the way
 * `DaemonLifecycleService` does in the app, and hands it to the stub.
 *
 * After this, `stack.stub.fireDaemonEvent { … }` plays the daemon against the real routing
 * code: the per-conversation channels, the registration-state handling, all of it. Before the
 * stub kept its callbacks (2026-11-08) none of that could run without a device and a peer.
 *
 * Remember that the callbacks are channel-based and asynchronous — `advanceUntilIdle()` after
 * firing, or the assertion runs before the event has been routed.
 */
fun connectDaemonCallbacks(
    stack: TestServiceStack,
    scope: CoroutineScope,
): DaemonCallbacks {
    val callbacks = DaemonCallbacksImpl(
        accountService = stack.accountService,
        callService = stack.callService,
        contactService = stack.contactService,
        conversationFacade = stack.conversationFacade,
        peerServicesService = PeerServicesService(stack.stub),
        certificateService = CertificateService(stack.stub),
        scope = scope,
    )
    stack.stub.init(callbacks)
    return callbacks
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
): net.jami.model.Account {
    stub.accountIds = listOf(accountId)
    stub.accountDetails[accountId] = mapOf(
        ConfigKey.ACCOUNT_TYPE.key to "RING",
        ConfigKey.ACCOUNT_DISPLAYNAME.key to displayName
    )
    accountService.loadAccounts()
    return accountService.getAccount(accountId)
        ?: net.jami.model.Account(accountId, mutableMapOf(ConfigKey.ACCOUNT_TYPE.key to "RING", ConfigKey.ACCOUNT_DISPLAYNAME.key to displayName))
}

// ==================== Convenience: a group in the conversation list ====================

/**
 * A swarm group conversation with [memberCount] members (the user plus others).
 *
 * No shared fixture held a group before 2026-11-07, which is exactly why a suite of 918 tests
 * stayed green while six call sites read [net.jami.model.Conversation.contact] — an accessor
 * that *throws* for a group — on every conversation in a list. The crash was found by opening
 * the app on a device instead (`bbba9fb`, `69ab199`). Put a group in the list whenever a test
 * walks one, so the suite enforces this rather than a future grep having to.
 */
fun groupConversation(
    accountId: String = TEST_ACCOUNT_ID,
    conversationId: String = "group1",
    memberCount: Int = 3,
    mode: net.jami.model.Conversation.Mode = net.jami.model.Conversation.Mode.InvitesOnly,
): net.jami.model.Conversation {
    require(memberCount >= 3) { "a group needs more than two members; $memberCount is a 1:1" }
    val members = buildList {
        add(net.jami.model.Contact(net.jami.model.Uri.fromString("me"), isUser = true))
        repeat(memberCount - 1) { i ->
            add(net.jami.model.Contact(net.jami.model.Uri.fromString("peer$i")))
        }
    }
    return net.jami.model.Conversation(
        accountId,
        net.jami.model.Uri(net.jami.model.Uri.SWARM_SCHEME, conversationId),
        members.toMutableList(),
        mode,
    )
}

/** A plain 1:1 swarm conversation with [peerId], for lists that should hold both shapes. */
fun oneToOneConversation(
    accountId: String = TEST_ACCOUNT_ID,
    conversationId: String = "oneToOne1",
    peerId: String = "bbbb",
): net.jami.model.Conversation = net.jami.model.Conversation(
    accountId,
    net.jami.model.Uri(net.jami.model.Uri.SWARM_SCHEME, conversationId),
    mutableListOf(
        net.jami.model.Contact(net.jami.model.Uri.fromString("me"), isUser = true),
        net.jami.model.Contact(net.jami.model.Uri.fromString(peerId)),
    ),
    net.jami.model.Conversation.Mode.OneToOne,
)

/**
 * Puts a group *and* a 1:1 into [account]'s conversation list, the shape that broke on a device:
 * anything walking the list meets the group first.
 */
fun seedMixedConversationList(
    account: net.jami.model.Account,
    groupId: String = "group1",
    oneToOneId: String = "oneToOne1",
    peerId: String = "bbbb",
): Pair<net.jami.model.Conversation, net.jami.model.Conversation> {
    val group = groupConversation(account.accountId, groupId)
    val oneToOne = oneToOneConversation(account.accountId, oneToOneId, peerId)
    account.conversationStarted(group)
    account.conversationStarted(oneToOne)
    return group to oneToOne
}
