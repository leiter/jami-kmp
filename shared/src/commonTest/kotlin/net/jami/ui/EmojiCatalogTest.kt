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

import net.jami.ui.components.inputs.EmojiCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The catalogue is generated (`scripts/generate_emoji_catalog.py`), so what is worth holding
 * is the shape the picker depends on: unique keys for the grid, usable search, and the six
 * quick reactions still being the six the UI has always offered.
 */
class EmojiCatalogTest {

    /** `LazyVerticalGrid` keys on the character: a duplicate crashes the picker. */
    @Test
    fun everyEmojiAppearsOnce() {
        val all = EmojiCatalog.categories.flatMap { it.emoji }.map { it.char }
        assertEquals(all.size, all.toSet().size, "duplicate emoji would crash the grid")
    }

    @Test
    fun noCategoryIsEmpty() {
        val empty = EmojiCatalog.categories.filter { it.emoji.isEmpty() }.map { it.title }
        assertTrue(empty.isEmpty(), "empty category tabs: $empty")
    }

    @Test
    fun searchMatchesUnicodeNames() {
        assertTrue(EmojiCatalog.search("tears of joy").any { it.char == "😂" })
        assertTrue(EmojiCatalog.search("CAT").isNotEmpty(), "search must ignore case")
        assertTrue(EmojiCatalog.search("   ").isEmpty(), "a blank query matches nothing")
    }

    /** The quick row is unchanged by the picker landing beside it. */
    @Test
    fun theSixQuickReactionsAreUnchanged() {
        assertEquals(
            listOf("👍", "❤️", "😂", "😮", "😢", "👏"),
            EmojiCatalog.quick,
        )
    }
}
