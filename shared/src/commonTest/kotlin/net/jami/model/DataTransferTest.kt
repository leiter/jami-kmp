package net.jami.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DataTransferTest {

    @Test
    fun testDataTransferCreation() {
        val conversation = ConversationHistory(1, "participant1")
        val transfer = DataTransfer(
            conversation = conversation,
            peer = "jami:peer123",
            account = "account1",
            displayName = "photo.jpg",
            isOutgoing = false,
            totalSize = 1024L,
            bytesProgress = 0L,
            fileId = "12345"
        )

        assertEquals(conversation, transfer.conversation)
        assertEquals("jami:peer123", transfer.author)
        assertEquals("account1", transfer.account)
        assertEquals("photo.jpg", transfer.body)
        assertEquals(1024L, transfer.totalSize)
        assertEquals(0L, transfer.bytesProgress)
        assertEquals("12345", transfer.fileId)
        assertEquals(Interaction.InteractionType.DATA_TRANSFER, transfer.type)
        assertEquals(Interaction.TransferStatus.TRANSFER_CREATED, transfer.transferStatus)
        assertTrue(transfer.isIncoming)
        assertFalse(transfer.isOutgoing)
    }

    @Test
    fun testDataTransferOutgoing() {
        val transfer = DataTransfer(
            conversation = null,
            peer = "jami:peer123",
            account = "account1",
            displayName = "document.pdf",
            isOutgoing = true,
            totalSize = 2048L,
            bytesProgress = 512L,
            fileId = null
        )

        assertFalse(transfer.isIncoming)
        assertTrue(transfer.isOutgoing)
        assertEquals(null, transfer.author) // Outgoing has no author
    }

    @Test
    fun testDataTransferFromInteraction() {
        val interaction = Interaction("account1").apply {
            id = 42
            author = "jami:peer123"
            body = "file.txt"
            timestamp = 1000L
            transferStatus = Interaction.TransferStatus.TRANSFER_FINISHED
            type = Interaction.InteractionType.DATA_TRANSFER
            isIncoming = true
        }

        val transfer = DataTransfer(interaction)

        assertEquals(42, transfer.id)
        assertEquals("jami:peer123", transfer.author)
        assertEquals("file.txt", transfer.body)
        assertEquals(Interaction.TransferStatus.TRANSFER_FINISHED, transfer.transferStatus)
        assertTrue(transfer.isIncoming)
    }

    @Test
    fun testDataTransferWithTimestamp() {
        val transfer = DataTransfer(
            fileId = "abc123",
            accountId = "account1",
            peerUri = "jami:peer123",
            displayName = "video.mp4",
            isOutgoing = false,
            timestamp = 1234567890L,
            totalSize = 10000L,
            bytesProgress = 5000L
        )

        assertEquals("abc123", transfer.fileId)
        assertEquals("account1", transfer.account)
        assertEquals("jami:peer123", transfer.author)
        assertEquals("video.mp4", transfer.body)
        assertEquals(1234567890L, transfer.timestamp)
        assertEquals(10000L, transfer.totalSize)
        assertEquals(5000L, transfer.bytesProgress)
    }

    @Test
    fun testExtension() {
        val jpgTransfer = DataTransfer(
            conversation = null,
            peer = null,
            account = "account1",
            displayName = "photo.JPG",
            isOutgoing = true,
            totalSize = 100L,
            bytesProgress = 0L,
            fileId = null
        )
        assertEquals("jpg", jpgTransfer.extension)

        val noExtTransfer = DataTransfer(
            conversation = null,
            peer = null,
            account = "account1",
            displayName = "noextension",
            isOutgoing = true,
            totalSize = 100L,
            bytesProgress = 0L,
            fileId = null
        )
        assertEquals("", noExtTransfer.extension)
    }

    @Test
    fun testIsPicture() {
        val imageExtensions = listOf("jpg", "jpeg", "png", "gif", "webp", "svg", "bmp", "heic", "heif")

        for (ext in imageExtensions) {
            val transfer = DataTransfer(
                conversation = null,
                peer = null,
                account = "account1",
                displayName = "file.$ext",
                isOutgoing = true,
                totalSize = 100L,
                bytesProgress = 0L,
                fileId = null
            )
            assertTrue(transfer.isPicture, "Expected $ext to be a picture")
        }

        val nonImage = DataTransfer(
            conversation = null,
            peer = null,
            account = "account1",
            displayName = "file.txt",
            isOutgoing = true,
            totalSize = 100L,
            bytesProgress = 0L,
            fileId = null
        )
        assertFalse(nonImage.isPicture)
    }

    @Test
    fun testIsAudio() {
        val audioExtensions = listOf("ogg", "mp3", "aac", "flac", "m4a")

        for (ext in audioExtensions) {
            val transfer = DataTransfer(
                conversation = null,
                peer = null,
                account = "account1",
                displayName = "file.$ext",
                isOutgoing = true,
                totalSize = 100L,
                bytesProgress = 0L,
                fileId = null
            )
            assertTrue(transfer.isAudio, "Expected $ext to be audio")
        }
    }

    @Test
    fun testIsVideo() {
        val videoExtensions = listOf("webm", "mp4", "mkv")

        for (ext in videoExtensions) {
            val transfer = DataTransfer(
                conversation = null,
                peer = null,
                account = "account1",
                displayName = "file.$ext",
                isOutgoing = true,
                totalSize = 100L,
                bytesProgress = 0L,
                fileId = null
            )
            assertTrue(transfer.isVideo, "Expected $ext to be video")
        }
    }

    @Test
    fun testIsComplete() {
        val transfer = DataTransfer(
            conversation = null,
            peer = null,
            account = "account1",
            displayName = "file.txt",
            isOutgoing = true,
            totalSize = 100L,
            bytesProgress = 0L,
            fileId = null
        )

        assertFalse(transfer.isComplete)

        transfer.transferStatus = Interaction.TransferStatus.TRANSFER_FINISHED
        assertTrue(transfer.isComplete)
    }

    @Test
    fun testShowPicture() {
        val transfer = DataTransfer(
            conversation = null,
            peer = null,
            account = "account1",
            displayName = "photo.jpg",
            isOutgoing = true,
            totalSize = 100L,
            bytesProgress = 0L,
            fileId = null
        )

        assertFalse(transfer.showPicture()) // Not complete

        transfer.transferStatus = Interaction.TransferStatus.TRANSFER_FINISHED
        assertTrue(transfer.showPicture()) // Picture and complete
    }

    @Test
    fun testIsError() {
        val transfer = DataTransfer(
            conversation = null,
            peer = null,
            account = "account1",
            displayName = "file.txt",
            isOutgoing = true,
            totalSize = 100L,
            bytesProgress = 0L,
            fileId = null
        )

        assertFalse(transfer.isError)

        transfer.transferStatus = Interaction.TransferStatus.TRANSFER_ERROR
        assertTrue(transfer.isError)

        transfer.transferStatus = Interaction.TransferStatus.TRANSFER_CANCELED
        assertTrue(transfer.isError)
    }

    @Test
    fun testStoragePath() {
        val transfer1 = DataTransfer(
            conversation = null,
            peer = null,
            account = "account1",
            displayName = "document.pdf",
            isOutgoing = true,
            totalSize = 100L,
            bytesProgress = 0L,
            fileId = "file123"
        )
        assertEquals("document.pdf", transfer1.storagePath)

        val transfer2 = DataTransfer(
            fileId = "file456",
            accountId = "account1",
            peerUri = "peer",
            displayName = "",
            isOutgoing = true,
            timestamp = 0L,
            totalSize = 100L,
            bytesProgress = 0L
        )
        transfer2.body = null
        assertEquals("file456", transfer2.storagePath)
    }

    @Test
    fun testCanAutoAccept() {
        val transfer = DataTransfer(
            conversation = null,
            peer = null,
            account = "account1",
            displayName = "file.txt",
            isOutgoing = false,
            totalSize = 1000L,
            bytesProgress = 0L,
            fileId = null
        )

        assertTrue(transfer.canAutoAccept(2000)) // Max size > total size
        assertFalse(transfer.canAutoAccept(500)) // Max size < total size
        assertTrue(transfer.canAutoAccept(256 * 1024 * 1024)) // Unlimited size
    }

    @Test
    fun testDisplayName() {
        val transfer = DataTransfer(
            conversation = null,
            peer = null,
            account = "account1",
            displayName = "my_document.pdf",
            isOutgoing = true,
            totalSize = 100L,
            bytesProgress = 0L,
            fileId = null
        )

        assertEquals("my_document.pdf", transfer.displayName)
    }
}
