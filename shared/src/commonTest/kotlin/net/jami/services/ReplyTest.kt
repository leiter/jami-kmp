package net.jami.services

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.jami.model.ConfigKey
import net.jami.model.SwarmMessage
import net.jami.repository.DraftRepository
import net.jami.testAudioRecorderService
import net.jami.ui.viewmodel.ChatViewModel
import net.jami.ui.viewmodel.MessageItem
import net.jami.viewmodel.TestServiceStack
import net.jami.viewmodel.makeSendQueue
import net.jami.viewmodel.makeTestServiceStack
import net.jami.viewmodel.viewModelScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Replying to a message: composer state, the sent reply-to, and the quoted original in bubbles. */
class ReplyTest {

    private companion object {
        const val ACC = "acc1"
        const val CONV = "conv1"
        const val ME = "me"
        const val BOB = "bob"
    }

    private class Env(val stack: TestServiceStack, val vm: ChatViewModel) {
        val stub get() = stack.stub
        fun message(id: String): MessageItem = assertNotNull(vm.state.value.messages.firstOrNull { it.id == id })
    }

    private fun text(id: String, author: String, body: String, replyTo: String? = null) = SwarmMessage(
        id = id,
        type = "text/plain",
        linearizedParent = "",
        body = buildMap {
            put("author", author); put("body", body); put("timestamp", "1")
            replyTo?.let { put("reply-to", it) }
        },
    )

    /** A live 1:1 chat with [history] loaded, as after opening it. */
    private suspend fun TestScope.openChat(vararg history: SwarmMessage): Env {
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
            mapOf("uri" to BOB, "role" to "member"),
        )
        val stack = makeTestServiceStack(stub, this)
        val queue = makeSendQueue(stub, stack.accountService, stack.conversationFacade, this)
        stack.accountService.loadAccounts()
        advanceUntilIdle()
        stack.conversationFacade.onConversationReady(ACC, CONV)
        advanceUntilIdle()

        val vmScope = viewModelScope()
        val vm = ChatViewModel(
            stack.conversationFacade, stack.accountService, StubDeviceRuntimeService(),
            DraftRepository(stub, vmScope), testAudioRecorderService(), queue, vmScope,
        )
        vm.loadConversation(CONV)
        advanceUntilIdle()
        stack.conversationFacade.onSwarmLoaded(1L, ACC, CONV, history.toList())
        stack.accountService.resolveSwarmLoaded(1L, ACC, CONV, history.toList())
        advanceUntilIdle()
        return Env(stack, vm)
    }

    @Test
    fun replyIsSentWithReplyToAndQuotedInTheBubble() = runTest {
        val env = openChat(text("m1", BOB, "hello"))
        val original = env.message("m1")

        env.vm.startReply("m1")
        val replying = assertNotNull(env.vm.state.value.replyingTo)
        assertEquals("m1", replying.messageId)
        assertEquals(original.author, replying.author)
        assertEquals("hello", replying.text)

        env.vm.updateInput("hi back")
        env.vm.sendMessage()
        advanceUntilIdle()

        val sent = env.stub.sentMessages.last { it.flag == 0 }
        assertEquals("hi back", sent.message)
        assertEquals("m1", sent.replyTo)
        assertNull(env.vm.state.value.replyingTo, "the reply bar closes once sent")
        val bubble = env.vm.state.value.messages.single { it.text == "hi back" }
        assertEquals("m1", bubble.replyTo?.messageId)
        assertEquals("hello", bubble.replyTo?.text)
    }

    @Test
    fun cancelledReplySendsAPlainMessage() = runTest {
        val env = openChat(text("m1", BOB, "hello"))
        env.vm.startReply("m1")
        env.vm.cancelReply()
        env.vm.updateInput("plain")
        env.vm.sendMessage()
        advanceUntilIdle()
        assertEquals("", env.stub.sentMessages.last { it.flag == 0 }.replyTo)
    }

    @Test
    fun replyInHistoryQuotesTheOriginal() = runTest {
        val env = openChat(text("m1", ME, "my question"), text("m2", BOB, "the answer", replyTo = "m1"))
        val quote = assertNotNull(env.message("m2").replyTo)
        assertEquals("m1", quote.messageId)
        assertNull(quote.author, "own original is shown as \"You\"")
        assertEquals("my question", quote.text)
        assertTrue(quote.isLoaded)
        assertTrue(env.stub.loadSwarmUntilRequests.isEmpty())
    }

    @Test
    fun missingOriginalIsLoadedOnceAndThenQuoted() = runTest {
        val env = openChat(text("m2", BOB, "the answer", replyTo = "old"))
        assertFalse(assertNotNull(env.message("m2").replyTo).isLoaded)
        assertEquals(listOf("old"), env.stub.loadSwarmUntilRequests)

        // The daemon answers loadSwarmUntil; the history rebuild fills the quote in.
        env.stack.conversationFacade.onSwarmLoaded(2L, ACC, CONV, listOf(text("old", BOB, "older message")))
        advanceUntilIdle()
        val quote = assertNotNull(env.message("m2").replyTo)
        assertTrue(quote.isLoaded)
        assertEquals("older message", quote.text)
        assertEquals(listOf("old"), env.stub.loadSwarmUntilRequests, "requested only once")
    }

    @Test
    fun liveReplyIsQuoted() = runTest {
        val env = openChat(text("m1", ME, "ping"))
        env.stack.conversationFacade.onMessageReceived(ACC, CONV, text("m2", BOB, "pong", replyTo = "m1"))
        advanceUntilIdle()
        assertEquals("ping", env.message("m2").replyTo?.text)
    }

    @Test
    fun onlyCommittedMessagesCanBeRepliedTo() = runTest {
        val env = openChat(text("m1", BOB, "hello"))
        assertTrue(env.vm.isReplyable(env.message("m1")))
        val pending = env.message("m1").copy(id = "pending-1")
        assertFalse(env.vm.isReplyable(pending))

        env.vm.startReply("date_1")
        assertNull(env.vm.state.value.replyingTo)
    }
}
