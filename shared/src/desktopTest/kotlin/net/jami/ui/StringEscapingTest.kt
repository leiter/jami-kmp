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

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Guards the one escaping rule the Compose resource reader does not share with AAPT.
 *
 * The catalogue was imported from `jami-client-android`, where AAPT unescapes `\'` and `\"`
 * before a string reaches the app. Compose Multiplatform's reader does not, so an imported
 * `\'` was *rendered*: users saw "the contact\'s name". It hid for a long time because the
 * device everything is tested on is de-DE and German has no escaped apostrophes — it surfaced
 * only when the in-app language picker was used to switch to French, which had 273 of them.
 * 2009 occurrences across 28 locale folders were unescaped; neither `'` nor `"` needs escaping
 * in XML text content.
 *
 * Asserted against the XML rather than through `getString`, deliberately: `getString` resolves
 * the *JVM default locale*, so a resource-level test only ever examines whichever translation
 * the build machine happens to use — on this one, German, which is exactly the locale that
 * cannot fail. Reading the catalogue covers all locales on any machine.
 */
class StringEscapingTest {

    private fun resourceDir(): File {
        // Gradle runs tests with the module directory as the working directory, but don't rely
        // on it: walk up until the repository's resource folder is found.
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val candidate = File(dir, "shared/src/commonMain/composeResources")
            if (candidate.isDirectory) return candidate
            val here = File(dir, "src/commonMain/composeResources")
            if (here.isDirectory) return here
            dir = dir.parentFile
        }
        fail("could not locate composeResources from ${File(".").absolutePath}")
    }

    private fun stringFiles(): List<File> =
        resourceDir().listFiles { f: File -> f.isDirectory && f.name.startsWith("values") }
            .orEmpty()
            .flatMap { it.listFiles { f: File -> f.extension == "xml" }.orEmpty().toList() }

    @Test
    fun catalogueIsPresent() {
        val files = stringFiles()
        assertTrue(files.size > 100, "expected the full catalogue, found ${files.size} files")
    }

    @Test
    fun noStringEscapesAnApostrophe() {
        val offenders = stringFiles()
            .map { it to it.readText().split("\\'").size - 1 }
            .filter { (_, n) -> n > 0 }
        if (offenders.isNotEmpty()) {
            val total = offenders.sumOf { it.second }
            fail(
                "$total escaped apostrophe(s) would render as a literal backslash. " +
                    "Use a bare ' in XML. Offenders: " +
                    offenders.joinToString { (f, n) -> "${f.parentFile.name}/${f.name}=$n" }
            )
        }
    }

    /**
     * `\"`, `\@` and `\?` are the same class of defect. Established 2026-11-11 from the
     * Compose Gradle plugin (1.9.0, `PrepareComposeResourcesKt.handleSpecialCharacters`): the
     * build step unescapes only `\n`, `\t`, `\uXXXX` and `\\`, so any other backslash renders.
     * The 352 `\"` that remained (the English `share_contact_body` among them, so users saw
     * `contact \"Alice\"`) were unescaped that day. `scripts/sync_android_strings.py` applies the
     * same rule to everything it imports.
     */
    @Test
    fun noStringEscapesAQuoteAtOrQuestionMark() {
        val pattern = Regex("""(?<!\\)\\["@?]""")
        val offenders = stringFiles()
            .map { it to pattern.findAll(it.readText()).count() }
            .filter { (_, n) -> n > 0 }
        if (offenders.isNotEmpty()) {
            fail(
                "${offenders.sumOf { it.second }} escaped \", @ or ? would render with the " +
                    "backslash. Use the bare character in XML. Offenders: " +
                    offenders.joinToString { (f, n) -> "${f.parentFile.name}/${f.name}=$n" }
            )
        }
    }

    /**
     * Compose resources (1.9.0, `StringResourcesUtils.replaceWithArgs`) substitutes only
     * numbered placeholders, `%1$d` / `%1$s`; a bare `%d` or `%s` is rendered as-is. Found
     * 2026-11-14 in a two-simulator run: the home banner read "%d ausstehende Einladung". 296
     * bare `%d` across three plurals and 65 files were numbered that day.
     */
    @Test
    fun everyPlaceholderIsNumbered() {
        val pattern = Regex("""(?<!%)%[ds]""")
        val offenders = stringFiles()
            .map { it to pattern.findAll(it.readText()).count() }
            .filter { (_, n) -> n > 0 }
        if (offenders.isNotEmpty()) {
            fail(
                "${offenders.sumOf { it.second }} bare %d/%s would render literally; use " +
                    "%1\$d / %1\$s. Offenders: " +
                    offenders.joinToString { (f, n) -> "${f.parentFile.name}/${f.name}=$n" }
            )
        }
    }
}
