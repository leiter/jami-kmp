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
 * A message body, cut into the runs a bubble draws.
 *
 * The three things a bubble marks up — inline Markdown, http(s) links and `@mentions` — all
 * have to agree about one string, and Markdown is the awkward one: its delimiters are removed,
 * so every offset after the first `**` shifts. Doing the three in sequence over ranges would
 * mean re-deriving those offsets twice. Instead this produces the final runs in one pass and
 * the bubble just draws them.
 *
 * Pure, so the whole of it is testable without a composition.
 */
object MessageBody {

    /** One run of body text with everything the bubble needs to know about it. */
    data class Token(
        val text: String,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val strikethrough: Boolean = false,
        val code: Boolean = false,
        /** The URL to open when tapped; null unless this run is a link. */
        val url: String? = null,
        /** True when this run is `@someone` and someone is a member of the conversation. */
        val mention: Boolean = false,
    )

    /**
     * Cuts [text] into tokens, applying inline Markdown, then links and mentions inside what
     * is left.
     *
     * Inline only: `**bold**`, `*italic*`, `_italic_`, `~~strikethrough~~` and `` `code` ``.
     * No headings, lists, block quotes or tables — those are a document's furniture and a chat
     * bubble is one paragraph, which is also where the reference client's Markwon config
     * stops. Nothing nests except inside code, which nests nothing: a backticked run is taken
     * literally, delimiters and all, which is the whole point of writing one.
     *
     * Unmatched delimiters stay as typed. Someone writing "2 * 3 * 4" has not asked for
     * italics, and silently eating their asterisks would be worse than printing them.
     */
    fun tokenize(text: String, memberNames: Collection<String> = emptyList()): List<Token> {
        val styled = markdownRuns(text)
        return styled.flatMap { run ->
            // Code is verbatim: no link, no mention, no further markup.
            if (run.code) listOf(run) else splitLinksAndMentions(run, memberNames)
        }.filter { it.text.isNotEmpty() }
    }

    // ==================== Markdown ====================

    private data class Delimiter(val token: String, val apply: (Token) -> Token)

    private val DELIMITERS = listOf(
        Delimiter("```") { it.copy(code = true) },
        Delimiter("**") { it.copy(bold = true) },
        Delimiter("~~") { it.copy(strikethrough = true) },
        Delimiter("`") { it.copy(code = true) },
        Delimiter("*") { it.copy(italic = true) },
        Delimiter("_") { it.copy(italic = true) },
    )

    private fun markdownRuns(text: String): List<Token> {
        val out = mutableListOf<Token>()
        val plain = StringBuilder()
        var i = 0

        fun flush() {
            if (plain.isNotEmpty()) {
                out.add(Token(plain.toString()))
                plain.clear()
            }
        }

        while (i < text.length) {
            val delimiter = DELIMITERS.firstOrNull { text.startsWith(it.token, i) }
            if (delimiter == null) {
                plain.append(text[i])
                i++
                continue
            }
            val contentStart = i + delimiter.token.length
            val close = text.indexOf(delimiter.token, contentStart)
            // No closing delimiter, or nothing between them: not markup, just characters.
            if (close == -1 || close == contentStart) {
                plain.append(delimiter.token)
                i += delimiter.token.length
                continue
            }
            flush()
            val inner = text.substring(contentStart, close)
            val styled = delimiter.apply(Token(inner))
            // Code is verbatim; everything else may carry more markup inside it.
            if (styled.code) {
                out.add(styled)
            } else {
                out.addAll(markdownRuns(inner).map { nested -> merge(styled, nested) })
            }
            i = close + delimiter.token.length
        }
        flush()
        return out
    }

    private fun merge(outer: Token, inner: Token) = inner.copy(
        bold = inner.bold || outer.bold,
        italic = inner.italic || outer.italic,
        strikethrough = inner.strikethrough || outer.strikethrough,
        code = inner.code || outer.code,
    )

    // ==================== Links and mentions ====================

    private fun splitLinksAndMentions(run: Token, memberNames: Collection<String>): List<Token> {
        val text = run.text
        val urls = findUrlRanges(text)
        val mentions = Mentions.findRanges(text, memberNames)
        if (urls.isEmpty() && mentions.isEmpty()) return listOf(run)

        val out = mutableListOf<Token>()
        var last = 0
        for (range in (urls + mentions).sortedBy { it.first }) {
            // A mention cannot start inside a URL, but a malformed one could overlap; the
            // first range wins rather than producing crossed spans.
            if (range.first < last) continue
            if (range.first > last) out.add(run.copy(text = text.substring(last, range.first)))
            val piece = text.substring(range.first, range.last + 1)
            out.add(
                if (range in urls) run.copy(text = piece, url = piece)
                else run.copy(text = piece, mention = true)
            )
            last = range.last + 1
        }
        if (last < text.length) out.add(run.copy(text = text.substring(last)))
        return out
    }
}
