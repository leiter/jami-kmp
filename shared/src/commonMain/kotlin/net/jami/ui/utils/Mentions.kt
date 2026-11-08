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
package net.jami.ui.utils

/**
 * `@name` mentions in group chat.
 *
 * There is no mention field on the wire: a Jami swarm message is text, and neither reference
 * client implements mentions at all (checked 2026-11-08 — the premise on file said the
 * Android one did). So a mention *is* the literal text `@name`, which every other client
 * shows as the plain text the author typed, and this file is only about recognising it again
 * at the far end.
 *
 * Recognised only against the conversation's actual members. Matching any `@word` would light
 * up email addresses, handles from other services and the `@` in a quoted command line.
 */
object Mentions {

    /**
     * The character ranges of [text] that mention one of [memberNames], including the `@`.
     *
     * Longest name first, so "@Ann Marie" is one mention rather than a mention of "Ann"
     * followed by stray text. Case-insensitive: the author typed what they remembered.
     */
    fun findRanges(text: String, memberNames: Collection<String>): List<IntRange> {
        if (text.isEmpty() || memberNames.isEmpty()) return emptyList()
        val names = memberNames.filter { it.isNotBlank() }.sortedByDescending { it.length }
        val ranges = mutableListOf<IntRange>()
        var i = 0
        while (i < text.length) {
            if (text[i] != '@' || !isBoundaryBefore(text, i)) {
                i++
                continue
            }
            val match = names.firstOrNull { name ->
                text.regionMatches(i + 1, name, 0, name.length, ignoreCase = true) &&
                    isBoundaryAfter(text, i + 1 + name.length)
            }
            if (match == null) {
                i++
            } else {
                ranges.add(i..(i + match.length))
                i += match.length + 1
            }
        }
        return ranges
    }

    /** Whether [text] mentions [name] — used to mark a message that names the reader. */
    fun mentions(text: String, name: String): Boolean =
        name.isNotBlank() && findRanges(text, listOf(name)).isNotEmpty()

    /**
     * The mention being typed at the end of [text], without its `@`, or null when the user is
     * not in the middle of one.
     *
     * Deliberately only at the end: the composer holds a plain `String`, not a
     * `TextFieldValue`, so there is no caret to consult. Typing `@` in the middle of an
     * already-written line offers no suggestions, which is a gap, not a decision — it wants a
     * caret-aware composer, which is a larger change than this.
     *
     * Returns an empty string right after a bare `@`, which means "offer every member".
     */
    fun trailingQuery(text: String): String? {
        val at = text.lastIndexOf('@')
        if (at == -1) return null
        if (!isBoundaryBefore(text, at)) return null
        val query = text.substring(at + 1)
        // A space ends a mention: the user typed a name and moved on.
        if (query.contains(' ') || query.contains('\n')) return null
        return query
    }

    /**
     * [text] with the mention being typed completed to [name], ready for the next word.
     *
     * A trailing space because the next thing typed is almost never part of the name, and
     * because it closes the suggestion list — [trailingQuery] stops matching.
     */
    fun complete(text: String, name: String): String {
        val at = text.lastIndexOf('@')
        if (at == -1) return text
        return text.substring(0, at) + "@" + name + " "
    }

    /** Members whose name starts with, or contains, what has been typed so far. */
    fun suggestions(query: String, memberNames: Collection<String>, limit: Int = 8): List<String> {
        val needle = query.trim().lowercase()
        val names = memberNames.filter { it.isNotBlank() }.distinct()
        if (needle.isEmpty()) return names.take(limit)
        // Prefix matches first: someone typing "an" wants Anna before Susanna.
        val (prefix, rest) = names
            .filter { it.lowercase().contains(needle) }
            .partition { it.lowercase().startsWith(needle) }
        return (prefix + rest).take(limit)
    }

    private fun isBoundaryBefore(text: String, index: Int): Boolean =
        index == 0 || !text[index - 1].isLetterOrDigit()

    private fun isBoundaryAfter(text: String, index: Int): Boolean =
        index >= text.length || !text[index].isLetterOrDigit()
}
