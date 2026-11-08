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

import net.jami.ui.utils.MessageBody
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessageBodyTest {

    private fun rendered(text: String, members: List<String> = emptyList()) =
        MessageBody.tokenize(text, members)

    private fun flat(text: String, members: List<String> = emptyList()) =
        rendered(text, members).joinToString("") { it.text }

    @Test
    fun plainTextIsOneToken() {
        assertEquals(listOf(MessageBody.Token("just text")), rendered("just text"))
    }

    @Test
    fun theFourInlineStylesAreRecognised() {
        assertTrue(rendered("**x**").single().bold)
        assertTrue(rendered("*x*").single().italic)
        assertTrue(rendered("_x_").single().italic)
        assertTrue(rendered("~~x~~").single().strikethrough)
        assertTrue(rendered("`x`").single().code)
    }

    @Test
    fun delimitersAreRemovedFromWhatIsDrawn() {
        assertEquals("bold and italic", flat("**bold** and *italic*"))
    }

    @Test
    fun stylesNest() {
        val inner = rendered("**bold with *italic* inside**")
        assertTrue(inner.all { it.bold })
        assertTrue(inner.single { it.text == "italic" }.italic)
    }

    /** Code is verbatim — that is what writing it is for. */
    @Test
    fun codeSwallowsEverythingInsideIt() {
        val token = rendered("`**not bold** @anna http://x.test`", listOf("anna")).single()
        assertTrue(token.code)
        assertFalse(token.bold)
        assertFalse(token.mention)
        assertEquals(null, token.url)
        assertEquals("**not bold** @anna http://x.test", token.text)
    }

    /**
     * The regression this guards: eating someone's asterisks. "2 * 3 * 4" is arithmetic, and
     * an unclosed delimiter is a character the author typed.
     */
    @Test
    fun unmatchedDelimitersStayAsTyped() {
        assertEquals("2 * 3", flat("2 * 3"))
        assertEquals("**unclosed", flat("**unclosed"))
        assertEquals("a_b", flat("a_b"))
    }

    @Test
    fun emptyDelimitersAreNotMarkup() {
        assertEquals("****", flat("****"))
    }

    @Test
    fun linksSurviveMarkdownAndCarryTheirUrl() {
        val token = rendered("see **https://jami.net** now").single { it.url != null }
        assertEquals("https://jami.net", token.url)
        assertTrue(token.bold, "a link inside bold text is still bold")
    }

    @Test
    fun mentionsAreMarkedInsideStyledRuns() {
        val token = rendered("*hi @anna*", listOf("anna")).single { it.mention }
        assertEquals("@anna", token.text)
        assertTrue(token.italic)
    }

    @Test
    fun aBodyWithEverythingKeepsItsText() {
        val text = "**hey** @anna see https://jami.net and `code`"
        assertEquals("hey @anna see https://jami.net and code", flat(text, listOf("anna")))
    }

    @Test
    fun emptyTokensAreDropped() {
        assertTrue(rendered("").isEmpty())
        assertTrue(rendered("**x**").none { it.text.isEmpty() })
    }
}
