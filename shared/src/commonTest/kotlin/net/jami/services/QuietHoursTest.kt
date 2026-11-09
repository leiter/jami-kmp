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

/** The pure quiet-hours rule iOS reads from NSUserDefaults for a PushKit cold start. */
class QuietHoursTest {

    @Test
    fun disabledIsNeverActive() {
        assertFalse(QuietHours.isActive(enabled = false, start = 0, end = 1440, minuteOfDay = 600))
    }

    @Test
    fun aSameDayWindowIncludesItsStartAndExcludesItsEnd() {
        // 08:00 to 22:00
        assertTrue(QuietHours.isActive(true, 480, 1320, 480))
        assertTrue(QuietHours.isActive(true, 480, 1320, 1319))
        assertFalse(QuietHours.isActive(true, 480, 1320, 1320))
        assertFalse(QuietHours.isActive(true, 480, 1320, 479))
    }

    @Test
    fun aWindowAcrossMidnightCoversBothSides() {
        // 23:00 to 07:00, the default
        assertTrue(QuietHours.isActive(true, 1380, 420, 1380))
        assertTrue(QuietHours.isActive(true, 1380, 420, 0))
        assertTrue(QuietHours.isActive(true, 1380, 420, 419))
        assertFalse(QuietHours.isActive(true, 1380, 420, 420))
        assertFalse(QuietHours.isActive(true, 1380, 420, 720))
    }
}
