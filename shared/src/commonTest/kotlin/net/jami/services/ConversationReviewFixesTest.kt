package net.jami.services

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.jami.model.ConfigKey
import net.jami.model.ContactEvent
import net.jami.model.Conversation
import net.jami.model.DataTransfer
import net.jami.model.Interaction
import net.jami.model.Interaction.TransferStatus
import net.jami.model.MemberRole
import net.jami.model.SwarmMessage
import net.jami.model.Uri
import net.jami.repository.DraftRepository
import net.jami.testAudioRecorderService
import net.jami.ui.viewmodel.ChatViewModel
import net.jami.viewmodel.TestServiceStack
import net.jami.viewmodel.makeTestServiceStack
import net.jami.viewmodel.viewModelScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * ConversationFacade / ChatViewModel behaviour fixed on 2026-09-18
 * (doc/conversation-facade-review-2026-09-18.md and follow-ups): data-transfer status mapping,
 * progress refresh and transition guard, incoming-file auto-accept, member events and roles,
 * member/initial history events, and reaction ids / removal.
 */
class ConversationReviewFixesTest {

    private companion object {
        const val ACC = "acc1"
        const val CONV = "conv1"
        const val ME = "me"
        const val BOB = "bob"
    }

    /**
     * One Jami account whose own id is [ME], and one swarm [CONV] made ready through
     * onConversationReady (mode "0" = 1:1, "2" = invites-only group).
     */
    private suspend fun TestScope.setUp(
        mode: String = "0",
        members: List<String> = listOf(ME, BOB),
    ): TestServiceStack {
        val stub = StubDaemonBridge()
        stub.accountIds = listOf(ACC)
        stub.accountDetails[ACC] = mapOf(
            ConfigKey.ACCOUNT_TYPE.key to "RING",
            ConfigKey.ACCOUNT_USERNAME.key to ME,
        )
        stub.conversations[ACC] = listOf(CONV)
        stub.conversationInfo[CONV] = mapOf("mode" to mode)
        stub.conversationMembers[CONV] = members.map {
            mapOf("uri" to it, "role" to if (it == ME) "admin" else "member")
        }
        val stack = makeTestServiceStack(stub, this)
        stack.accountService.loadAccounts()
        advanceUntilIdle()
        stack.conversationFacade.onConversationReady(ACC, CONV)
        advanceUntilIdle()
        return stack
    }

    private fun TestServiceStack.conversation(): Conversation =
        assertNotNull(conversationFacade.getConversation(ACC, Uri(Uri.SWARM_SCHEME, CONV)))

    private fun fileMessage(id: String, fileId: String, author: String, totalSize: Long = 1000) = SwarmMessage(
        id = id,
        type = "application/data-transfer+json",
        linearizedParent = "",
        body = mapOf(
            "author" to author, "fileId" to fileId, "displayName" to "file.bin",
            "totalSize" to totalSize.toString(), "timestamp" to "1",
        ),
    )

    private fun textMessage(id: String, author: String, reactionEntries: List<Map<String, String>> = emptyList()) =
        SwarmMessage(
            id = id,
            type = "text/plain",
            linearizedParent = "",
            body = mapOf("author" to author, "body" to "hello", "timestamp" to "1"),
            reactionEntries = reactionEntries,
        )

    /**
     * Bounded replacement for advanceUntilIdle() in transfer tests: while a transfer is ongoing the
     * progress refresh polls forever, so a broken status mapping would make advanceUntilIdle()
     * hang instead of failing the test.
     */
    private fun TestScope.settle() {
        advanceTimeBy(5_000)
        runCurrent()
    }

    // ==================== Data transfers ====================

    @Test
    fun transferEventCodesSetStatusAndProgressIsRefreshed() = runTest {
        val stack = setUp()
        val facade = stack.conversationFacade
        facade.onSwarmLoaded(1L, ACC, CONV, listOf(fileMessage("m1", "f1", BOB)))
        val transfer = assertIs<DataTransfer>(stack.conversation().getMessage("m1"))
        assertEquals(TransferStatus.FILE_AVAILABLE, transfer.transferStatus)

        // 5 = libjami "ongoing" (previously mapped to TRANSFER_ERROR).
        stack.stub.fileTransferInfos["f1"] = FileTransferInfo("/data/f1", 1000, 100)
        facade.onDataTransferEvent(ACC, CONV, "m1", "f1", 5)
        runCurrent()
        assertEquals(TransferStatus.TRANSFER_ONGOING, transfer.transferStatus)
        assertEquals(100, transfer.bytesProgress)

        // No daemon event carries progress: the refresh polls fileTransferInfo.
        stack.stub.fileTransferInfos["f1"] = FileTransferInfo("/data/f1", 1000, 600)
        advanceTimeBy(600)
        runCurrent()
        assertEquals(600, transfer.bytesProgress)

        // 6 = libjami "finished" (previously mapped to TRANSFER_UNJOINABLE_PEER).
        stack.stub.fileTransferInfos["f1"] = FileTransferInfo("/data/f1", 1000, 1000)
        facade.onDataTransferEvent(ACC, CONV, "m1", "f1", 6)
        runCurrent()
        assertEquals(TransferStatus.TRANSFER_FINISHED, transfer.transferStatus)
        assertEquals("/data/f1", transfer.destinationPath)
        settle()
        assertEquals(TransferStatus.TRANSFER_FINISHED, transfer.transferStatus)
    }

    @Test
    fun finishedTransferIgnoresLateOngoingEvent() = runTest {
        val stack = setUp()
        val facade = stack.conversationFacade
        facade.onSwarmLoaded(1L, ACC, CONV, listOf(fileMessage("m1", "f1", BOB)))
        stack.stub.fileTransferInfos["f1"] = FileTransferInfo("/data/f1", 1000, 1000)
        facade.onDataTransferEvent(ACC, CONV, "m1", "f1", 6)
        settle()

        facade.onDataTransferEvent(ACC, CONV, "m1", "f1", 5)
        settle()

        val transfer = assertIs<DataTransfer>(stack.conversation().getMessage("m1"))
        assertEquals(TransferStatus.TRANSFER_FINISHED, transfer.transferStatus)
    }

    @Test
    fun retryAfterFailureAcceptsTheNewTransfersEvents() = runTest {
        val stack = setUp()
        val facade = stack.conversationFacade
        facade.onSwarmLoaded(1L, ACC, CONV, listOf(fileMessage("m1", "f1", BOB)))
        facade.onDataTransferEvent(ACC, CONV, "m1", "f1", 10) // unjoinable_peer
        settle()
        val transfer = assertIs<DataTransfer>(stack.conversation().getMessage("m1"))
        assertEquals(TransferStatus.TRANSFER_UNJOINABLE_PEER, transfer.transferStatus)

        facade.acceptFileTransfer(stack.conversation(), "m1", "f1")
        assertEquals(TransferStatus.FILE_AVAILABLE, transfer.transferStatus)
        assertTrue(Triple(CONV, "m1", "f1") in stack.stub.downloadedFiles)

        stack.stub.fileTransferInfos["f1"] = FileTransferInfo("/data/f1", 1000, 1000)
        facade.onDataTransferEvent(ACC, CONV, "m1", "f1", 6)
        settle()
        assertEquals(TransferStatus.TRANSFER_FINISHED, transfer.transferStatus)
    }

    @Test
    fun incomingFileMessageIsAutoAccepted() = runTest {
        val stack = setUp()
        stack.conversationFacade.onMessageReceived(ACC, CONV, fileMessage("m2", "f2", BOB))
        settle()
        assertTrue(Triple(CONV, "m2", "f2") in stack.stub.downloadedFiles)
    }

    @Test
    fun ownFileMessageIsNotDownloaded() = runTest {
        val stack = setUp()
        stack.conversationFacade.onMessageReceived(ACC, CONV, fileMessage("m3", "f3", ME))
        settle()
        assertTrue(stack.stub.downloadedFiles.none { it.third == "f3" })
    }

    // ==================== Members ====================

    @Test
    fun memberEventsApplyRolesLikeTheReference() = runTest {
        val stack = setUp(mode = "2", members = listOf(ME, BOB, "carol"))
        val facade = stack.conversationFacade

        facade.onConversationMemberEvent(ACC, CONV, "dave", 0)  // Add
        facade.onConversationMemberEvent(ACC, CONV, "carol", 2) // Remove
        facade.onConversationMemberEvent(ACC, CONV, BOB, 3)     // Block
        advanceUntilIdle()

        val conv = stack.conversation()
        val uris = conv.contacts.map { it.uri.rawRingId }
        assertTrue("dave" in uris)
        assertEquals(MemberRole.INVITED, conv.roles[Uri.fromString("dave").uri])
        assertFalse("carol" in uris)
        assertEquals(MemberRole.LEFT, conv.roles[Uri.fromString("carol").uri])
        assertTrue(BOB in uris)
        assertEquals(MemberRole.BLOCKED, conv.roles[Uri.fromString(BOB).uri])
    }

    @Test
    fun memberHistoryEventNamesTheAffectedMemberNotTheAuthor() = runTest {
        val stack = setUp(mode = "2")
        val message = SwarmMessage(
            id = "mm1", type = "member", linearizedParent = "",
            body = mapOf("author" to ME, "uri" to BOB, "action" to "add", "timestamp" to "1"),
        )
        stack.conversationFacade.onSwarmLoaded(1L, ACC, CONV, listOf(message))

        val event = assertIs<ContactEvent>(stack.conversation().getMessage("mm1"))
        assertEquals(BOB, event.contact?.uri?.rawRingId)
    }

    @Test
    fun initialEventShowsTheInvitedPeerInOneToOne() = runTest {
        val stack = setUp(mode = "0")
        val message = SwarmMessage(
            id = "i1", type = "initial", linearizedParent = "",
            body = mapOf("author" to ME, "invited" to BOB, "timestamp" to "1"),
        )
        stack.conversationFacade.onSwarmLoaded(1L, ACC, CONV, listOf(message))

        val event = assertIs<ContactEvent>(stack.conversation().getMessage("i1"))
        assertEquals(BOB, event.contact?.uri?.rawRingId)
    }

    @Test
    fun initialEventIsNotShownInGroups() = runTest {
        val stack = setUp(mode = "2")
        val message = SwarmMessage(
            id = "i1", type = "initial", linearizedParent = "",
            body = mapOf("author" to ME, "invited" to BOB, "timestamp" to "1"),
        )
        stack.conversationFacade.onSwarmLoaded(1L, ACC, CONV, listOf(message))

        val interaction = stack.conversation().getMessage("i1")
        assertFalse(interaction is ContactEvent)
        assertEquals(Interaction.InteractionType.INVALID, interaction?.type)
    }

    // ==================== Reactions ====================

    @Test
    fun historyReactionsKeepTheirMessageIds() = runTest {
        val stack = setUp()
        val message = textMessage(
            "t1", BOB,
            reactionEntries = listOf(mapOf("id" to "r1", "body" to "👍", "author" to ME)),
        )
        stack.conversationFacade.onSwarmLoaded(1L, ACC, CONV, listOf(message))

        val reaction = stack.conversation().getMessage("t1")?.reactions?.single()
        assertNotNull(reaction)
        assertEquals("r1", reaction.messageId)
        assertEquals("👍", reaction.body)
        assertFalse(reaction.isIncoming)
    }

    @Test
    fun liveReactionIsAddedWithItsIdAndRemoved() = runTest {
        val stack = setUp()
        val facade = stack.conversationFacade
        facade.onSwarmLoaded(1L, ACC, CONV, listOf(textMessage("t1", ME)))

        facade.onReactionAdded(ACC, CONV, "t1", mapOf("id" to "r2", "body" to "❤️", "author" to BOB))
        advanceUntilIdle()
        val reaction = stack.conversation().getMessage("t1")?.reactions?.single()
        assertEquals("r2", reaction?.messageId)
        assertTrue(reaction?.isIncoming == true)

        facade.onReactionRemoved(ACC, CONV, "t1", "r2")
        advanceUntilIdle()
        assertTrue(stack.conversation().getMessage("t1")?.reactions.isNullOrEmpty())
    }

    @Test
    fun removeReactionEditsOwnReactionToEmpty() = runTest {
        val stack = setUp()
        val vmScope = viewModelScope()
        val vm = ChatViewModel(
            stack.conversationFacade, stack.accountService, StubDeviceRuntimeService(),
            DraftRepository(stack.stub, vmScope), testAudioRecorderService(),
            net.jami.viewmodel.makeSendQueue(stack.stub, stack.accountService, stack.conversationFacade, this), vmScope,
        )
        val history = listOf(
            textMessage(
                "t1", BOB,
                reactionEntries = listOf(
                    mapOf("id" to "r1", "body" to "👍", "author" to ME),
                    mapOf("id" to "r9", "body" to "👍", "author" to BOB),
                ),
            )
        )
        vm.loadConversation(CONV)
        runCurrent()
        // Complete the history load the way DaemonCallbacksImpl does.
        stack.conversationFacade.onSwarmLoaded(1L, ACC, CONV, history)
        stack.accountService.resolveSwarmLoaded(1L, ACC, CONV, history)
        advanceUntilIdle()

        val group = vm.state.value.messages.first { it.id == "t1" }.reactions.single()
        assertEquals(2, group.count)
        assertEquals(listOf("r1"), group.myReactionIds)

        vm.removeReaction("t1", "👍")
        advanceUntilIdle()
        // Only the own reaction is removed: an edit (flag 1) to an empty body.
        assertEquals(
            listOf(StubDaemonBridge.SentMessage(ACC, CONV, "", "r1", 1)),
            stack.stub.sentMessages.filter { it.flag == 1 },
        )
    }
}
