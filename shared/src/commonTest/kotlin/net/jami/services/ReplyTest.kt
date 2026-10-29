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

    private class Env(
        val stack: TestServiceStack,
        val vm: ChatViewModel,
        val runtime: StubDeviceRuntimeService,
    ) {
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

    private fun file(
        id: String, author: String, name: String, fileId: String, replyTo: String? = null,
    ) = SwarmMessage(
        id = id,
        type = "application/data-transfer+json",
        linearizedParent = "",
        body = buildMap {
            put("author", author); put("displayName", name); put("fileId", fileId)
            put("totalSize", "10"); put("timestamp", "1")
            replyTo?.let { put("reply-to", it) }
        },
    )

    /** A live 1:1 chat with [history] loaded, as after opening it. */
    private suspend fun TestScope.openChat(vararg history: SwarmMessage): Env =
        openChat(StubDaemonBridge(), *history)

    private suspend fun TestScope.openChat(stub: StubDaemonBridge, vararg history: SwarmMessage): Env {
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
        val runtime = StubDeviceRuntimeService()
        val stack = makeTestServiceStack(stub, this, deviceRuntimeService = runtime)
        val queue = makeSendQueue(stub, stack.accountService, stack.conversationFacade, this)
        stack.accountService.loadAccounts()
        advanceUntilIdle()
        stack.conversationFacade.onConversationReady(ACC, CONV)
        advanceUntilIdle()

        val vmScope = viewModelScope()
        val vm = ChatViewModel(
            stack.conversationFacade, stack.accountService, runtime,
            DraftRepository(InMemoryClientSettingsStore(), vmScope), testAudioRecorderService(), queue, vmScope,
        )
        vm.loadConversation(CONV)
        advanceUntilIdle()
        stack.conversationFacade.onSwarmLoaded(1L, ACC, CONV, history.toList())
        stack.accountService.resolveSwarmLoaded(1L, ACC, CONV, history.toList())
        advanceUntilIdle()
        return Env(stack, vm, runtime)
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

    // ==================== Replying with a file (mini-plan messaging F4) ====================

    /**
     * The picker, the camera and the recorder all end in ChatViewModel.sendFile(), which used to
     * drop the open reply: the file arrived as an unrelated message and the reply bar stayed up.
     * The daemon has taken a reply-to as sendFile's `parent` all along.
     */
    @Test
    fun fileSentWhileReplyingCarriesTheReplyTo() = runTest {
        val env = openChat(text("m1", BOB, "can you send the slides?"))
        env.runtime.existingFiles += "/downloads/slides.pdf"

        env.vm.startReply("m1")
        env.vm.sendFile("/downloads/slides.pdf")
        advanceUntilIdle()

        val sent = assertNotNull(env.stub.sentFiles.lastOrNull())
        assertEquals("slides.pdf", sent.displayName)
        assertEquals("m1", sent.parent)
        assertNull(env.vm.state.value.replyingTo, "the reply bar closes once the file is sent")
    }

    @Test
    fun imageSentWhileReplyingCarriesTheReplyTo() = runTest {
        val env = openChat(text("m1", BOB, "a photo?"))
        env.runtime.existingFiles += "/downloads/photo.jpg"

        env.vm.startReply("m1")
        env.vm.sendImage("/downloads/photo.jpg")
        advanceUntilIdle()

        assertEquals("m1", assertNotNull(env.stub.sentFiles.lastOrNull()).parent)
    }

    @Test
    fun fileSentWithoutReplyingHasNoParent() = runTest {
        val env = openChat(text("m1", BOB, "hello"))
        env.runtime.existingFiles += "/downloads/slides.pdf"

        env.vm.sendFile("/downloads/slides.pdf")
        advanceUntilIdle()

        assertEquals("", assertNotNull(env.stub.sentFiles.lastOrNull()).parent)
    }

    /** One reply, one file: a second file picked afterwards is not a reply to the same message. */
    @Test
    fun theReplyIsConsumedByTheFirstFile() = runTest {
        val env = openChat(text("m1", BOB, "hello"))
        env.runtime.existingFiles += "/downloads/one.pdf"
        env.runtime.existingFiles += "/downloads/two.pdf"

        env.vm.startReply("m1")
        env.vm.sendFile("/downloads/one.pdf")
        env.vm.sendFile("/downloads/two.pdf")
        advanceUntilIdle()

        assertEquals(listOf("m1", ""), env.stub.sentFiles.map { it.parent })
    }

    /** A retried transfer is the same message, so it keeps the reply the first attempt carried. */
    @Test
    fun retryingAFailedFileKeepsTheReply() = runTest {
        val stub = StubDaemonBridge()
        stub.fileTransferInfos["f1"] = FileTransferInfo("/conv/slides.pdf", 10L, 0L)
        val env = openChat(
            stub,
            text("m1", BOB, "the slides?"),
            file("f2", ME, "slides.pdf", fileId = "f1", replyTo = "m1"),
        )
        // Event code 2 is the daemon's transfer error (Interaction.TransferStatus.fromIntFile).
        env.stack.conversationFacade.onDataTransferEvent(ACC, CONV, "f2", "f1", 2)
        advanceUntilIdle()
        assertTrue(env.message("f2").transferStatus.isError)

        env.vm.retryFileTransfer("f2")
        advanceUntilIdle()

        val sent = assertNotNull(env.stub.sentFiles.lastOrNull())
        assertEquals("/conv/slides.pdf", sent.filePath, "the stored copy is re-sent, not re-copied")
        assertEquals("m1", sent.parent)
    }
}
