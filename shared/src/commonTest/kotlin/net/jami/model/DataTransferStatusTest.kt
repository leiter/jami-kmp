package net.jami.model

import net.jami.model.Interaction.TransferStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Transfer status mapping from libjami's DataTransferEventCode and the transition guard
 * (review 2026-09-18, doc/conversation-facade-review-2026-09-18.md #1).
 */
class DataTransferStatusTest {

    private fun transfer(status: TransferStatus) =
        DataTransfer(null, "peer", "acc", "file.png", false, 100L, 0L, "f1").apply {
            transferStatus = status
        }

    @Test
    fun fromIntFileMatchesLibjamiEventCodes() {
        // libjami DataTransferEventCode: invalid, created, unsupported, wait_peer_acceptance,
        // wait_host_acceptance, ongoing, finished, closed_by_host, closed_by_peer,
        // invalid_pathname, unjoinable_peer, timeout_expired.
        val expected = mapOf(
            0 to TransferStatus.INVALID,
            1 to TransferStatus.TRANSFER_CREATED,
            2 to TransferStatus.TRANSFER_ERROR,
            3 to TransferStatus.TRANSFER_AWAITING_PEER,
            4 to TransferStatus.TRANSFER_AWAITING_HOST,
            5 to TransferStatus.TRANSFER_ONGOING,
            6 to TransferStatus.TRANSFER_FINISHED,
            7 to TransferStatus.TRANSFER_UNJOINABLE_PEER,
            8 to TransferStatus.TRANSFER_UNJOINABLE_PEER,
            9 to TransferStatus.TRANSFER_ERROR,
            10 to TransferStatus.TRANSFER_UNJOINABLE_PEER,
            11 to TransferStatus.TRANSFER_TIMEOUT_EXPIRED,
            99 to TransferStatus.INVALID,
        )
        expected.forEach { (code, status) ->
            assertEquals(status, TransferStatus.fromIntFile(code), "event code $code")
        }
    }

    @Test
    fun inProgressTransferCanMoveToAnyStatus() {
        val inProgress = listOf(
            TransferStatus.FILE_AVAILABLE,
            TransferStatus.TRANSFER_CREATED,
            TransferStatus.TRANSFER_AWAITING_HOST,
            TransferStatus.TRANSFER_ONGOING,
        )
        for (from in inProgress) {
            for (to in TransferStatus.entries) {
                assertTrue(transfer(from).canTransitionTo(to), "$from -> $to")
            }
        }
    }

    @Test
    fun finishedTransferIgnoresLateEvents() {
        val t = transfer(TransferStatus.TRANSFER_FINISHED)
        assertTrue(t.canTransitionTo(TransferStatus.TRANSFER_FINISHED))
        assertFalse(t.canTransitionTo(TransferStatus.TRANSFER_ONGOING))
        assertFalse(t.canTransitionTo(TransferStatus.TRANSFER_ERROR))
    }

    @Test
    fun failedTransferStaysFailed() {
        for (error in listOf(
            TransferStatus.TRANSFER_ERROR,
            TransferStatus.TRANSFER_UNJOINABLE_PEER,
            TransferStatus.TRANSFER_TIMEOUT_EXPIRED,
            TransferStatus.TRANSFER_CANCELED,
        )) {
            val t = transfer(error)
            assertTrue(t.canTransitionTo(error), "$error -> same")
            assertFalse(t.canTransitionTo(TransferStatus.TRANSFER_ONGOING), "$error -> ongoing")
            assertFalse(t.canTransitionTo(TransferStatus.TRANSFER_FINISHED), "$error -> finished")
        }
    }

    @Test
    fun removedFileStaysRemoved() {
        val t = transfer(TransferStatus.FILE_REMOVED)
        assertTrue(t.canTransitionTo(TransferStatus.FILE_REMOVED))
        assertFalse(t.canTransitionTo(TransferStatus.TRANSFER_ONGOING))
    }
}
