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

import net.jami.ui.viewmodel.MessageItem
import net.jami.ui.viewmodel.MessageType
import net.jami.ui.viewmodel.ReplyPreview
import net.jami.ui.viewmodel.SEQUENCE_GAP_MS
import net.jami.ui.viewmodel.applySequencingTo
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Message sequencing.
 *
 * The chat had no notion of a run of messages: every bubble was its own block, so a group
 * showed no sender avatars and a 1:1 repeated the peer's name above every message. The rules
 * here are `ConversationAdapter.isSeqBreak` / `isAlwaysSingleMsg` from the reference client.
 *
 * Each case states the shape it expects as a string of F/M/L/S — first, middle, last, single —
 * which is the reference's `SequenceType` and reads like the column it describes.
 */
class MessageSequencingTest {

    private var clock = 0L

    private fun msg(
        author: String,
        text: String = "hello",
        outgoing: Boolean = false,
        type: MessageType = MessageType.Text,
        gap: Long = 1000L,
        replyTo: ReplyPreview? = null,
    ): MessageItem {
        clock += gap
        return MessageItem(
            id = "id$clock",
            text = text,
            author = author,
            authorUri = if (outgoing) "" else author,
            timestamp = clock,
            isOutgoing = outgoing,
            type = type,
            replyTo = replyTo,
        )
    }

    /** F/M/L/S per message, so an expectation reads as the column it describes. */
    private fun shape(items: List<MessageItem>): String = applySequencingTo(items)
        .map {
            when {
                it.isFirstInSequence && it.isLastInSequence -> 'S'
                it.isFirstInSequence -> 'F'
                it.isLastInSequence -> 'L'
                else -> 'M'
            }
        }
        .joinToString("")

    @Test
    fun `three messages from one author are one run`() {
        assertEquals("FML", shape(listOf(msg("alice"), msg("alice"), msg("alice"))))
    }

    @Test
    fun `a lone message is a run of one`() {
        assertEquals("S", shape(listOf(msg("alice"))))
    }

    @Test
    fun `a different author breaks the run`() {
        assertEquals("SS", shape(listOf(msg("alice"), msg("bob"))))
    }

    @Test
    fun `two people alternating never group`() {
        assertEquals(
            "SSSS",
            shape(listOf(msg("alice"), msg("bob"), msg("alice"), msg("bob"))),
        )
    }

    @Test
    fun `the direction changing breaks the run`() {
        assertEquals(
            "SS",
            shape(listOf(msg("alice"), msg("me", outgoing = true))),
        )
    }

    @Test
    fun `one display name shared by two people does not merge them`() {
        // Both are called "Alex"; only authorUri tells them apart. Grouping on the display name
        // would draw one run with one avatar, attributing both messages to whoever is last.
        val first = msg("Alex").copy(authorUri = "aaa")
        val second = msg("Alex").copy(authorUri = "bbb")
        assertEquals("SS", shape(listOf(first, second)))
    }

    @Test
    fun `a gap longer than the window breaks the run`() {
        assertEquals(
            "SS",
            shape(listOf(msg("alice"), msg("alice", gap = SEQUENCE_GAP_MS + 1))),
        )
    }

    @Test
    fun `a gap inside the window keeps the run`() {
        assertEquals(
            "FL",
            shape(listOf(msg("alice"), msg("alice", gap = SEQUENCE_GAP_MS - 1))),
        )
    }

    @Test
    fun `a reply starts its own run because its quote needs the full header`() {
        val reply = msg("alice", replyTo = ReplyPreview("x", "bob", "original"))
        assertEquals("SS", shape(listOf(msg("alice"), reply)))
    }

    @Test
    fun `a date separator breaks the run on both sides`() {
        val separator = msg("", type = MessageType.DateSeparator)
        assertEquals(
            "SSS",
            shape(listOf(msg("alice"), separator, msg("alice"))),
        )
    }

    @Test
    fun `a file transfer never joins a run`() {
        assertEquals(
            "SSS",
            shape(listOf(msg("alice"), msg("alice", type = MessageType.Transfer), msg("alice"))),
        )
    }

    @Test
    fun `an emoji-only message stands alone`() {
        assertEquals(
            "SSS",
            shape(listOf(msg("alice"), msg("alice", text = "👍"), msg("alice"))),
        )
    }

    @Test
    fun `a run resumes after an interruption`() {
        assertEquals(
            "FMLSFL",
            shape(
                listOf(
                    msg("alice"), msg("alice"), msg("alice"),
                    msg("bob"),
                    msg("alice"), msg("alice"),
                ),
            ),
        )
    }

    @Test
    fun `an empty list is returned untouched`() {
        assertEquals(emptyList(), applySequencingTo(emptyList()))
    }

    @Test
    fun `sequencing is idempotent`() {
        val once = applySequencingTo(listOf(msg("alice"), msg("alice"), msg("bob")))
        assertEquals(once, applySequencingTo(once))
    }
}
