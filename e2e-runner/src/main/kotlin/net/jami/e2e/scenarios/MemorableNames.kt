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
package net.jami.e2e.scenarios

import net.jami.e2e.memory.MemoryStore

/**
 * Human-memorable, namespaced usernames for name-registration tests — e.g. `e2e-brave-otter`
 * instead of an opaque `e2ekmp1782882662n` timestamp. Readable in the registry descriptor
 * filenames, the burn log, and on the name server itself.
 *
 * Names burn permanently and globally, so each must be unique: we pick an adjective–animal pair
 * (Docker/Heroku style), skip any we've already burned locally ([MemoryStore.burnedNames]), and
 * fall back to a numeric suffix only if the word space is somehow exhausted. Charset (lowercase
 * letters, digits, hyphen) matches Jami's registered-name filter `[\p{L}\p{N}_-]`
 * (`RegisteredNameFilter.kt` in jami-client-android), and the `e2e-` prefix keeps test names
 * clearly namespaced apart from real users.
 */
object MemorableNames {
    const val PREFIX = "e2e"

    private val ADJECTIVES = listOf(
        "brave", "calm", "clever", "bright", "swift", "quiet", "gentle", "bold",
        "cosmic", "sunny", "mellow", "nimble", "lucky", "witty", "eager", "jolly",
        "amber", "azure", "coral", "olive", "ruby", "silver", "teal", "violet",
    )
    private val NOUNS = listOf(
        "otter", "fox", "panda", "heron", "lynx", "koala", "raven", "gecko",
        "falcon", "badger", "marten", "ibex", "tapir", "quokka", "narwhal", "puffin",
        "wombat", "meerkat", "lemur", "osprey", "pika", "civet", "dingo", "salmon",
    )

    /** Next unused memorable name (`e2e-<adjective>-<noun>`), deduped against local burns. */
    fun next(memory: MemoryStore): String {
        val used = memory.burnedNames().map { it.name }.toSet()
        for (adj in ADJECTIVES.shuffled()) {
            for (noun in NOUNS.shuffled()) {
                val name = "$PREFIX-$adj-$noun"
                if (name !in used) return name
            }
        }
        // Word space exhausted (576 combos) — append an incrementing suffix.
        var i = 2
        while (true) {
            val name = "$PREFIX-${ADJECTIVES.random()}-${NOUNS.random()}-$i"
            if (name !in used) return name
            i++
        }
    }
}
