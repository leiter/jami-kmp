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
package net.jami.ui

import net.jami.ui.utils.Mentions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MentionsTest {

    private val members = listOf("Anna", "Ann Marie", "Bob")

    @Test
    fun aMemberNameAfterAnAtIsAMention() {
        val text = "hello @Anna how are you"
        assertEquals(listOf(6..10), Mentions.findRanges(text, members))
        assertEquals("@Anna", text.substring(6, 11))
    }

    /** Longest first, or "@Ann Marie" is read as a mention of Ann plus stray text. */
    @Test
    fun theLongestMatchingNameWins() {
        val text = "@Ann Marie said so"
        assertEquals(listOf(0..9), Mentions.findRanges(text, members))
    }

    @Test
    fun matchingIsCaseInsensitive() {
        assertEquals(1, Mentions.findRanges("hi @anna", members).size)
    }

    /**
     * The reason mentions are matched against members and not against any `@word`: an email
     * address is not a mention, and neither is a handle from somewhere else.
     */
    @Test
    fun strangersAndEmailAddressesAreNotMentions() {
        assertTrue(Mentions.findRanges("write to bob@example.com", members).isEmpty())
        assertTrue(Mentions.findRanges("ask @Charlie", members).isEmpty())
    }

    /** A name that only prefixes a longer word is not a mention of that member. */
    @Test
    fun aPartialWordIsNotAMention() {
        assertTrue(Mentions.findRanges("@Annabel is someone else", listOf("Anna")).isEmpty())
    }

    @Test
    fun severalMentionsInOneMessageAreAllFound() {
        assertEquals(2, Mentions.findRanges("@Anna and @Bob", members).size)
    }

    @Test
    fun mentionsReportsWhetherTheReaderWasNamed() {
        assertTrue(Mentions.mentions("ping @Bob", "Bob"))
        assertFalse(Mentions.mentions("ping @Anna", "Bob"))
        assertFalse(Mentions.mentions("ping @Bob", ""))
    }

    // ==================== Composing one ====================

    @Test
    fun aBareAtOffersEveryMember() {
        assertEquals("", Mentions.trailingQuery("hello @"))
        assertEquals(members, Mentions.suggestions("", members))
    }

    @Test
    fun typingNarrowsTheSuggestions() {
        assertEquals("an", Mentions.trailingQuery("hello @an"))
        assertEquals(listOf("Anna", "Ann Marie"), Mentions.suggestions("an", members))
    }

    /** Prefix matches come first: typing "an" wants Anna before Susanna. */
    @Test
    fun prefixMatchesOutrankContainedOnes() {
        assertEquals(
            listOf("Anna", "Susanna"),
            Mentions.suggestions("an", listOf("Susanna", "Anna")),
        )
    }

    @Test
    fun aFinishedMentionStopsSuggesting() {
        assertNull(Mentions.trailingQuery("hello @Anna "))
        assertNull(Mentions.trailingQuery("no mention here"))
    }

    @Test
    fun completingReplacesWhatWasTypedAndLeavesASpace() {
        assertEquals("hello @Ann Marie ", Mentions.complete("hello @an", "Ann Marie"))
        assertEquals("@Bob ", Mentions.complete("@", "Bob"))
    }
}
