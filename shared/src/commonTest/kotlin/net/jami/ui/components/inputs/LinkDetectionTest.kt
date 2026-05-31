package net.jami.ui.components.inputs

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LinkDetectionTest {

    // ── findUrlRanges ────────────────────────────────────────────────────────

    @Test
    fun emptyString_noRanges() {
        assertTrue(findUrlRanges("").isEmpty())
    }

    @Test
    fun plainText_noRanges() {
        assertTrue(findUrlRanges("Hello, how are you?").isEmpty())
    }

    @Test
    fun standaloneHttpsUrl_rangeCoversFullUrl() {
        val url = "https://example.com"
        val ranges = findUrlRanges(url)
        assertEquals(1, ranges.size)
        assertEquals(0, ranges[0].first)
        assertEquals(url.lastIndex, ranges[0].last)
    }

    @Test
    fun standaloneHttpUrl_detected() {
        val url = "http://example.com"
        val ranges = findUrlRanges(url)
        assertEquals(1, ranges.size)
        assertEquals(0, ranges[0].first)
        assertEquals(url.lastIndex, ranges[0].last)
    }

    @Test
    fun embeddedUrl_rangeStartsAtUrlNotText() {
        val text = "hello https://example.com world"
        val ranges = findUrlRanges(text)
        assertEquals(1, ranges.size)
        assertEquals(text.indexOf("https"), ranges[0].first)
        assertEquals(text.indexOf("https") + "https://example.com".length - 1, ranges[0].last)
    }

    @Test
    fun twoUrls_twoRanges() {
        val ranges = findUrlRanges("https://one.com and https://two.org")
        assertEquals(2, ranges.size)
    }

    @Test
    fun missingScheme_noRange() {
        assertTrue(findUrlRanges("ttps://example.com").isEmpty())
        assertTrue(findUrlRanges("example.com").isEmpty())
        assertTrue(findUrlRanges("www.example.com").isEmpty())
    }

    @Test
    fun hRemovedFromHttps_noRange() {
        // Core user requirement: removing 'h' kills the match
        assertTrue(findUrlRanges("ttps://example.com").isEmpty())
    }

    @Test
    fun urlWithPathQueryFragment_fullyIncluded() {
        val url = "https://example.com/path?q=1&r=2#anchor"
        val ranges = findUrlRanges(url)
        assertEquals(1, ranges.size)
        assertEquals(0, ranges[0].first)
        assertEquals(url.lastIndex, ranges[0].last)
    }

    @Test
    fun urlOnSecondLine_correctOffset() {
        val text = "line one\nhttps://a.com\nline three"
        val ranges = findUrlRanges(text)
        assertEquals(1, ranges.size)
        assertEquals(text.indexOf("https"), ranges[0].first)
    }

    // ── LinkVisualTransformation.filter ──────────────────────────────────────

    @Test
    fun plainText_noSpansAdded() {
        val result = LinkVisualTransformation().filter(AnnotatedString("just plain text"))
        assertTrue(result.text.spanStyles.isEmpty())
    }

    @Test
    fun withUrl_blueUnderlineSpanAtUrlPosition() {
        val prefix = "visit "
        val url = "https://jami.net"
        val text = "$prefix$url for info"
        val result = LinkVisualTransformation().filter(AnnotatedString(text))

        assertEquals(1, result.text.spanStyles.size)
        val span = result.text.spanStyles[0]
        assertEquals(prefix.length, span.start)
        assertEquals(prefix.length + url.length, span.end)
        assertEquals(Color.Blue, span.item.color)
        assertEquals(TextDecoration.Underline, span.item.textDecoration)
    }

    @Test
    fun sameInput_cachedResultReturnedByIdentity() {
        val transformation = LinkVisualTransformation()
        val input = AnnotatedString("https://example.com")
        val first = transformation.filter(input)
        val second = transformation.filter(input)
        assertSame(first, second)
    }

    @Test
    fun inputTextChanges_cacheInvalidated() {
        val transformation = LinkVisualTransformation()
        val withUrl = transformation.filter(AnnotatedString("https://example.com"))
        val withoutUrl = transformation.filter(AnnotatedString("plain text"))
        assertTrue(withUrl.text.spanStyles.isNotEmpty())
        assertTrue(withoutUrl.text.spanStyles.isEmpty())
    }

    @Test
    fun offsetMappingIsIdentity_originalToTransformed() {
        val transformation = LinkVisualTransformation()
        val result = transformation.filter(AnnotatedString("check https://jami.net ok"))
        // Identity mapping: every offset maps to itself
        for (i in 0..25) {
            assertEquals(i, result.offsetMapping.originalToTransformed(i))
            assertEquals(i, result.offsetMapping.transformedToOriginal(i))
        }
    }
}
