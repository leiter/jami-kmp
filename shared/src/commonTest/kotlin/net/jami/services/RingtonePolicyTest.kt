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
package net.jami.services

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [RingtonePolicy.needsDefaultRingtone] — when an account's ringtone path must be rewritten.
 *
 * The case that matters is the third one. Every account created before the ringtones were
 * bundled carries libjami's compiled-in default, which points into the *official* client's
 * data directory (`/data/data/cx.ring/files/ringtones/default.opus`) and does not exist here.
 * A check of "is something configured?" would call that healthy and leave the account mute.
 */
class RingtonePolicyTest {

    private val existing = "/data/user/0/net.jami.android/files/ringtones/default.opus"
    private val missing = "/data/data/cx.ring/files/ringtones/default.opus"
    private val fileExists: (String) -> Boolean = { it == existing }

    @Test
    fun anUnsetPathNeedsTheDefault() {
        assertTrue(RingtonePolicy.needsDefaultRingtone(null, fileExists))
        assertTrue(RingtonePolicy.needsDefaultRingtone("", fileExists))
        assertTrue(RingtonePolicy.needsDefaultRingtone("   ", fileExists))
    }

    @Test
    fun aResolvablePathIsLeftAlone() {
        assertFalse(RingtonePolicy.needsDefaultRingtone(existing, fileExists))
    }

    @Test
    fun theStaleOfficialClientPathIsRepairedNotTrusted() {
        assertTrue(
            RingtonePolicy.needsDefaultRingtone(missing, fileExists),
            "a configured but unreachable ringtone must be replaced, not accepted"
        )
    }

    @Test
    fun aUsersOwnChoiceIsNotOverwritten() {
        val chosen = "/storage/emulated/0/Music/my_ringtone.opus"
        assertFalse(RingtonePolicy.needsDefaultRingtone(chosen) { it == chosen })
    }
}
