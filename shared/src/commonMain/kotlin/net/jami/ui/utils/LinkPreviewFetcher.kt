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

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.withTimeout

data class LinkPreview(
    val url: String,
    val title: String,
    val description: String?,
)

private val URL_REGEX = Regex("""https?://[^\s<>"')\]]+""")

/** Returns distinct URLs found in [text], up to [limit]. */
fun extractUrls(text: String, limit: Int = 1): List<String> =
    URL_REGEX.findAll(text).map { it.value }.distinct().take(limit).toList()

// Session-level cache — null value means "tried and failed"
private val previewCache = mutableMapOf<String, LinkPreview?>()

private val httpClient by lazy { HttpClient() }

/**
 * Fetches and parses Open Graph / title metadata for [url].
 * Returns null on network error or if no usable title can be extracted.
 * Results are cached in-memory for the lifetime of the process.
 */
suspend fun fetchLinkPreview(url: String): LinkPreview? {
    if (previewCache.containsKey(url)) return previewCache[url]

    val preview = try {
        withTimeout(5_000L) {
            val html = httpClient.get(url) {
                headers {
                    append("User-Agent", "Mozilla/5.0 (compatible; Jami/1.0)")
                    append("Accept", "text/html")
                }
            }.bodyAsText()
            parsePreview(url, html)
        }
    } catch (_: Exception) {
        null
    }

    previewCache[url] = preview
    return preview
}

private fun parsePreview(url: String, html: String): LinkPreview? {
    val title = metaContent(html, "og:title")
        ?: metaContent(html, "twitter:title")
        ?: Regex("""<title[^>]*>([^<]{1,200})</title>""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.getOrNull(1)
        ?: return null

    val description = metaContent(html, "og:description")
        ?: metaContent(html, "twitter:description")

    return LinkPreview(
        url = url,
        title = decodeHtmlEntities(title.trim()),
        description = description?.let { decodeHtmlEntities(it.trim()).takeIf { s -> s.isNotEmpty() } },
    )
}

/** Extracts content="..." for a given meta property or name, handling both attribute orderings. */
private fun metaContent(html: String, property: String): String? {
    val prop = Regex.escape(property)
    return listOf(
        Regex("""<meta[^>]+(?:property|name)=["']$prop["'][^>]+content=["']([^"']{1,300})["']""", RegexOption.IGNORE_CASE),
        Regex("""<meta[^>]+content=["']([^"']{1,300})["'][^>]+(?:property|name)=["']$prop["']""", RegexOption.IGNORE_CASE),
    ).firstNotNullOfOrNull { it.find(html)?.groupValues?.getOrNull(1) }
}

private fun decodeHtmlEntities(text: String): String =
    text.replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&nbsp;", " ")
        .replace(Regex("&#(\\d+);")) { m ->
            m.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: m.value
        }
