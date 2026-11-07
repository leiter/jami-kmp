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

import net.jami.ui.viewmodel.DeliveryIndicatorKind
import net.jami.ui.viewmodel.DeliveryStatus
import net.jami.ui.viewmodel.MessageItem
import net.jami.ui.viewmodel.SeenBy
import net.jami.ui.viewmodel.applyReadMarkersTo
import net.jami.ui.viewmodel.deliveryIndicatorFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Read markers (C3).
 *
 * jami-kmp put a tick on every outgoing message, so a screenful of sent messages was a column
 * of identical double ticks that said nothing about how far the other side had got. The
 * reference client (`ConversationAdapter.configureDisplayIndicator`) shows an indicator at most
 * twice in a conversation: a delivered tick on the last sent message, and each peer's avatar
 * parked at the last message that peer has read.
 *
 * The four cases below are that function's, in its order.
 */
class ReadMarkerTest {

    private fun outgoing(
        id: String,
        status: DeliveryStatus = DeliveryStatus.DELIVERED,
        isLastSent: Boolean = false,
        seenBy: List<SeenBy> = emptyList(),
    ) = MessageItem(
        id = id, text = "m$id", author = "me", timestamp = 0L, isOutgoing = true,
        deliveryStatus = status, isLastSent = isLastSent, seenBy = seenBy,
    )

    private fun peer(name: String) = SeenBy(uri = name, displayName = name, avatarBytes = null)

    // ---- case 1: nobody has it yet ----

    @Test
    fun anUnacknowledgedMessageShowsTheSendingTick() {
        assertEquals(
            DeliveryIndicatorKind.Sending,
            deliveryIndicatorFor(outgoing("1", DeliveryStatus.SENDING)),
        )
    }

    // ---- case 2: delivered, unread — only the newest one is marked ----

    @Test
    fun onlyTheLastSentMessageCarriesTheDeliveredTick() {
        assertEquals(
            DeliveryIndicatorKind.Delivered,
            deliveryIndicatorFor(outgoing("2", DeliveryStatus.DELIVERED, isLastSent = true)),
        )
    }

    @Test
    fun anOlderDeliveredMessageShowsNothing() {
        // This is the behaviour change: before, this was a grey double tick like every other.
        assertEquals(
            DeliveryIndicatorKind.None,
            deliveryIndicatorFor(outgoing("1", DeliveryStatus.DELIVERED, isLastSent = false)),
        )
    }

    // ---- case 3: a peer's read position sits here ----

    @Test
    fun aReadPositionShowsThatPeersAvatar() {
        assertEquals(
            DeliveryIndicatorKind.SeenBy,
            deliveryIndicatorFor(outgoing("3", DeliveryStatus.READ, seenBy = listOf(peer("bob")))),
        )
    }

    @Test
    fun avatarsWinOverTheTickOnTheSameMessage() {
        // The last sent message can also be someone's read position; the avatar says more.
        assertEquals(
            DeliveryIndicatorKind.SeenBy,
            deliveryIndicatorFor(
                outgoing("3", DeliveryStatus.DELIVERED, isLastSent = true, seenBy = listOf(peer("bob")))
            ),
        )
    }

    // ---- case 4: read, but someone else's marker is further on ----

    @Test
    fun aReadMessageBehindTheMarkerShowsNothing() {
        assertEquals(
            DeliveryIndicatorKind.None,
            deliveryIndicatorFor(outgoing("1", DeliveryStatus.READ)),
        )
    }

    // ---- states the reference handles separately and this client keeps ----

    @Test
    fun failureAndPendingSyncKeepTheirOwnGlyphs() {
        assertEquals(
            DeliveryIndicatorKind.Failed,
            deliveryIndicatorFor(outgoing("1", DeliveryStatus.FAILED)),
        )
        assertEquals(
            DeliveryIndicatorKind.WaitingToSync,
            deliveryIndicatorFor(outgoing("1", DeliveryStatus.WAITING_TO_SYNC)),
        )
    }

    @Test
    fun anIncomingMessageNeverCarriesAnIndicator() {
        val incoming = outgoing("1", DeliveryStatus.READ).copy(isOutgoing = false)
        assertEquals(DeliveryIndicatorKind.None, deliveryIndicatorFor(incoming))
    }

    // ---- the pass that stamps the two facts onto a list ----

    @Test
    fun theMarkerPassFlagsOnlyTheLastSentMessage() {
        val items = listOf(outgoing("1"), outgoing("2"), outgoing("3"))
        val marked = applyReadMarkersTo(items, lastSent = "3", readers = emptyMap())

        assertEquals(listOf(false, false, true), marked.map { it.isLastSent })
    }

    @Test
    fun theMarkerPassPlacesEachPeerAtItsOwnMessage() {
        val items = listOf(outgoing("1"), outgoing("2"), outgoing("3"))
        val marked = applyReadMarkersTo(
            items,
            lastSent = "3",
            readers = mapOf("1" to listOf(peer("bob")), "2" to listOf(peer("carol"), peer("dave"))),
        )

        assertEquals(listOf("bob"), marked[0].seenBy.map { it.displayName })
        assertEquals(listOf("carol", "dave"), marked[1].seenBy.map { it.displayName })
        assertTrue(marked[2].seenBy.isEmpty(), "nobody has read the newest message yet")
    }

    @Test
    fun theMarkerPassLeavesIncomingMessagesAlone() {
        val incoming = outgoing("1").copy(isOutgoing = false)
        val marked = applyReadMarkersTo(listOf(incoming), lastSent = "1", readers = emptyMap())

        assertEquals(false, marked.single().isLastSent)
    }

    @Test
    fun withNothingToMarkTheListIsReturnedUnchanged() {
        val items = listOf(outgoing("1"), outgoing("2"))
        assertEquals(items, applyReadMarkersTo(items, lastSent = null, readers = emptyMap()))
    }

    /**
     * `SeenBy` holds a `ByteArray`, so its equality is hand-written; if that regressed, Compose
     * would redraw every bubble on each state update.
     */
    @Test
    fun seenByComparesAvatarsByContentNotIdentity() {
        val a = SeenBy("bob", "Bob", byteArrayOf(1, 2, 3))
        val b = SeenBy("bob", "Bob", byteArrayOf(1, 2, 3))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertTrue(a != SeenBy("bob", "Bob", byteArrayOf(9)))
        assertTrue(a != SeenBy("bob", "Bob", null))
    }
}
