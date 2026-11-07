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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The variant ids are the whole mechanism by which a sound or vibration setting reaches an
 * Android notification channel, because the platform fixes both at creation and a channel
 * recreated under an id it has held before comes back with its old settings. So what these
 * tests guard is: different settings must never share an id, and the same settings must
 * always produce the same one.
 */
class NotificationChannelIdsTest {

    @Test
    fun everyCombinationOfSettingsGetsItsOwnId() {
        val ids = buildSet {
            for (sound in listOf(
                NotificationSound.Silent,
                NotificationSound.Default,
                NotificationSound.Custom(SOUND_A),
                NotificationSound.Custom(SOUND_B),
            )) {
                for (vibrate in listOf(true, false)) {
                    add(NotificationChannelIds.variant(BASE, sound, vibrate))
                }
            }
        }
        assertEquals(8, ids.size, "two different settings would share a channel: $ids")
    }

    /**
     * What makes a user's own edits in system settings survive: turn a setting off and back
     * on, and the app returns to the channel it had rather than making a third one.
     */
    @Test
    fun theSameSettingsAlwaysProduceTheSameId() {
        assertEquals(
            NotificationChannelIds.variant(BASE, NotificationSound.Custom(SOUND_A), true),
            NotificationChannelIds.variant(BASE, NotificationSound.Custom(SOUND_A), true),
        )
        assertNotEquals(
            NotificationChannelIds.variant(BASE, NotificationSound.Silent, true),
            NotificationChannelIds.variant(BASE, NotificationSound.Default, true),
        )
    }

    /** Stale siblings are found by this, so it has to recognise every id variant() makes. */
    @Test
    fun everyVariantIsRecognisedAsOneOfItsBase() {
        for (sound in listOf(
            NotificationSound.Silent,
            NotificationSound.Default,
            NotificationSound.Custom(SOUND_A),
        )) {
            for (vibrate in listOf(true, false)) {
                assertTrue(
                    NotificationChannelIds.isVariantOf(
                        BASE, NotificationChannelIds.variant(BASE, sound, vibrate)
                    )
                )
            }
        }
    }

    /**
     * The plain base id counts as its own variant: that is the channel earlier versions of the
     * app created, and deleting it on migration is the only way the user stops seeing two
     * "Messages" entries in system settings.
     */
    @Test
    fun theLegacyUnsuffixedChannelCountsAsAVariant() {
        assertTrue(NotificationChannelIds.isVariantOf(BASE, BASE))
    }

    /** A different base must never be swept up by another's cleanup. */
    @Test
    fun anotherChannelsIdsAreNotVariants() {
        val other = NotificationChannelIds.variant(OTHER_BASE, NotificationSound.Default, true)
        assertFalse(NotificationChannelIds.isVariantOf(BASE, other))
        assertFalse(NotificationChannelIds.isVariantOf(BASE, OTHER_BASE))
    }

    private companion object {
        const val BASE = "jami_messages_v2"
        const val OTHER_BASE = "jami_requests"
        const val SOUND_A = "content://media/internal/audio/media/42"
        const val SOUND_B = "content://media/internal/audio/media/43"
    }
}
