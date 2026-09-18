package net.jami.services

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.jami.model.ConfigKey
import net.jami.model.Conversation
import net.jami.model.Uri
import net.jami.repository.DraftRepository
import net.jami.testAudioRecorderService
import net.jami.ui.viewmodel.ChatViewModel
import net.jami.ui.viewmodel.DeliveryStatus
import net.jami.viewmodel.TestServiceStack
import net.jami.viewmodel.makeSendQueue
import net.jami.viewmodel.makeTestServiceStack
import net.jami.viewmodel.viewModelScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Durable outbox (doc/plan_daemon_stability_sync.md Phase 4, finding F4): messages sent before a
 * conversation can deliver them are held — not handed to the daemon — and sent once it is live,
 * also after an app restart.
 */
class SendQueueServiceTest {

    private companion object {
        const val ACC = "acc1"
        const val CONV = "conv1"
        const val ME = "me"
        const val BOB = "bob"
    }

    private class Env(val stack: TestServiceStack, val queue: SendQueueService) {
        val stub get() = stack.stub
        val sent get() = stub.sentMessages.filter { it.flag == 0 }
    }

    /** One account ([ME]) with a 1:1 swarm [CONV] whose peer [BOB] has the daemon role [peerRole]. */
    private suspend fun TestScope.setUp(
        peerRole: String,
        store: OutboxStore = InMemoryOutboxStore(),
    ): Env {
        val stub = StubDaemonBridge()
        stub.accountIds = listOf(ACC)
        stub.accountDetails[ACC] = mapOf(
            ConfigKey.ACCOUNT_TYPE.key to "RING",
            ConfigKey.ACCOUNT_USERNAME.key to ME,
        )
        stub.conversations[ACC] = listOf(CONV)
        stub.conversationInfo[CONV] = mapOf("mode" to "0")
        stub.conversationMembers[CONV] = listOf(
            mapOf("uri" to ME, "role" to "admin"),
            mapOf("uri" to BOB, "role" to peerRole),
        )
        val stack = makeTestServiceStack(stub, this)
        val queue = makeSendQueue(stub, stack.accountService, stack.conversationFacade, this, store)
        stack.accountService.loadAccounts()
        advanceUntilIdle()
        stack.conversationFacade.onConversationReady(ACC, CONV)
        advanceUntilIdle()
        return Env(stack, queue)
    }

    private fun Env.peerJoins() {
        stub.conversationMembers[CONV] = listOf(
            mapOf("uri" to ME, "role" to "admin"),
            mapOf("uri" to BOB, "role" to "member"),
        )
        stack.conversationFacade.onConversationMemberEvent(ACC, CONV, BOB, 1)
    }

    private val convUri = Uri(Uri.SWARM_SCHEME, CONV)

    @Test
    fun oneToOneWithOnlyInvitedPeerIsHeld() = runTest {
        val env = setUp(peerRole = "invited")
        assertTrue(env.queue.shouldHold(ACC, convUri))
    }

    @Test
    fun oneToOneWithJoinedPeerIsNotHeld() = runTest {
        val env = setUp(peerRole = "member")
        assertFalse(env.queue.shouldHold(ACC, convUri))
    }

    @Test
    fun syncingConversationIsHeld() = runTest {
        val env = setUp(peerRole = "member")
        env.stack.conversationFacade.getConversation(ACC, convUri)!!.setMode(Conversation.Mode.Syncing)
        assertTrue(env.queue.shouldHold(ACC, convUri))
    }

    @Test
    fun heldMessageIsSentOnlyWhenThePeerJoins() = runTest {
        val env = setUp(peerRole = "invited")

        env.queue.enqueue(ACC, CONV, "hello")
        advanceUntilIdle()
        assertTrue(env.sent.isEmpty(), "must not be handed to the daemon before the peer joins")
        assertEquals(1, env.queue.queued(ACC, CONV).size)

        env.peerJoins()
        advanceUntilIdle()
        assertEquals(listOf("hello"), env.sent.map { it.message })
        assertTrue(env.queue.queued(ACC, CONV).isEmpty())
    }

    @Test
    fun heldMessagesAreSentInOrder() = runTest {
        val env = setUp(peerRole = "invited")
        env.queue.enqueue(ACC, CONV, "one")
        env.queue.enqueue(ACC, CONV, "two")
        env.peerJoins()
        advanceUntilIdle()
        assertEquals(listOf("one", "two"), env.sent.map { it.message })
    }

    @Test
    fun heldMessageIsSentWhenSyncingConversationBecomesReady() = runTest {
        val env = setUp(peerRole = "member")
        env.stack.conversationFacade.getConversation(ACC, convUri)!!.setMode(Conversation.Mode.Syncing)
        env.queue.enqueue(ACC, CONV, "hi")
        advanceUntilIdle()
        assertTrue(env.sent.isEmpty())

        // The daemon reports the conversation ready (mode 0 = 1:1): ConversationReady -> flush.
        env.stack.conversationFacade.onConversationReady(ACC, CONV)
        advanceUntilIdle()
        assertEquals(listOf("hi"), env.sent.map { it.message })
    }

    @Test
    fun heldMessageSurvivesARestartAndIsSentAfterTheFirstSmartlistLoad() = runTest {
        val store = InMemoryOutboxStore() // stands in for the persisted table across "restarts"
        val before = setUp(peerRole = "invited", store = store)
        before.queue.enqueue(ACC, CONV, "sent before the restart")
        advanceUntilIdle()
        assertTrue(before.sent.isEmpty())

        // New process: fresh services, same store; the peer has joined meanwhile. The smartlist
        // load at startup (ConversationsLoaded) sends it.
        val after = setUp(peerRole = "member", store = store)
        advanceUntilIdle()
        assertEquals(listOf("sent before the restart"), after.sent.map { it.message })
        assertTrue(store.all().isEmpty())
    }

    @Test
    fun chatShowsHeldMessageAsWaitingThenSendingWhenThePeerJoins() = runTest {
        val env = setUp(peerRole = "invited")
        val vmScope = viewModelScope()
        val vm = ChatViewModel(
            env.stack.conversationFacade, env.stack.accountService, StubDeviceRuntimeService(),
            DraftRepository(env.stub, vmScope), testAudioRecorderService(), env.queue, vmScope,
        )
        vm.loadConversation(CONV)
        advanceUntilIdle()

        vm.updateInput("hello")
        vm.sendMessage()
        advanceUntilIdle()
        val held = vm.state.value.messages.single { it.text == "hello" }
        assertTrue(held.id.startsWith("outbox-"))
        assertEquals(DeliveryStatus.WAITING_TO_SYNC, held.deliveryStatus)
        assertTrue(env.sent.isEmpty())

        env.peerJoins()
        advanceUntilIdle()
        assertEquals(listOf("hello"), env.sent.map { it.message })
        // Still pending until the daemon echoes it back; SEND_TIMEOUT_MS later it would be FAILED.
        val bubble = vm.state.value.messages.single { it.text == "hello" }
        assertTrue(bubble.deliveryStatus == DeliveryStatus.SENDING || bubble.deliveryStatus == DeliveryStatus.FAILED)
    }

    @Test
    fun chatRestoresHeldMessagesWhenOpened() = runTest {
        val store = InMemoryOutboxStore()
        store.insert(ACC, CONV, "from last session", null, 5L)
        val env = setUp(peerRole = "invited", store = store)
        val vmScope = viewModelScope()
        val vm = ChatViewModel(
            env.stack.conversationFacade, env.stack.accountService, StubDeviceRuntimeService(),
            DraftRepository(env.stub, vmScope), testAudioRecorderService(), env.queue, vmScope,
        )
        vm.loadConversation(CONV)
        advanceUntilIdle()
        // loadConversation waits for the history load; complete it like DaemonCallbacksImpl does.
        env.stack.conversationFacade.onSwarmLoaded(1L, ACC, CONV, emptyList())
        env.stack.accountService.resolveSwarmLoaded(1L, ACC, CONV, emptyList())
        advanceUntilIdle()

        val restored = vm.state.value.messages.single { it.text == "from last session" }
        assertEquals(DeliveryStatus.WAITING_TO_SYNC, restored.deliveryStatus)
    }
}
