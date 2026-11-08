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

import net.jami.ui.platform.ShippedLocales
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The language picker offers exactly this list, so a malformed entry is a language nobody can
 * select. The list is generated (`scripts/generate_shipped_locales.py`); these tests are what
 * catches a hand-edit or a generator change that produces Android folder syntax rather than
 * BCP-47 tags.
 */
class ShippedLocalesTest {

    @Test
    fun everyTagIsBcp47NotAnAndroidFolderName() {
        val malformed = ShippedLocales.tags.filter { !TAG.matches(it) }
        assertTrue(malformed.isEmpty(), "not BCP-47 tags: $malformed")
    }

    /** `values-pt-rBR` is the folder; `pt-BR` is the tag. The `r` must not survive. */
    @Test
    fun regionSuffixesAreConverted() {
        assertTrue(ShippedLocales.tags.contains("pt-BR"), "expected pt-BR in ${ShippedLocales.tags}")
        assertFalse(ShippedLocales.tags.any { it.contains("-r") })
    }

    @Test
    fun thereAreNoDuplicates() {
        assertEquals(ShippedLocales.tags.size, ShippedLocales.tags.toSet().size)
    }

    /**
     * English is the unsuffixed `values/` folder, so it is not in the list — the picker adds
     * it. An "en" here would mean a `values-en/` nobody maintains.
     */
    @Test
    fun englishIsNotListed() {
        assertFalse(ShippedLocales.tags.contains("en"))
    }

    private companion object {
        val TAG = Regex("""[a-z]{2,3}(-[A-Z]{2})?""")
    }
}
