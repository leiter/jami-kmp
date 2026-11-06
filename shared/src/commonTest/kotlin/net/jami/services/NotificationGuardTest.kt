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

import kotlinx.coroutines.test.runTest
import net.jami.viewmodel.makeSettingsRepository
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Quiet hours are read off the wall clock, which the guard takes from `Clock.System` and these
 * tests cannot move. A window of 00:00–24:00 is in quiet hours at every instant, so
 * [ALL_DAY_START]/[ALL_DAY_END] make the in-quiet-hours case deterministic without a clock
 * seam; the out-of-quiet-hours case is expressed by turning the feature off.
 */
class NotificationGuardTest {

    /**
     * The regression this file exists for: quiet hours used to be a term in
     * shouldShowCallNotification(), so an incoming call during the window produced no
     * notification at all and could not be answered.
     */
    @Test
    fun quietHoursSilenceACallInsteadOfHidingIt() = runTest {
        val repo = makeSettingsRepository(scope = this)
        val guard = NotificationGuard(repo)

        repo.updateQuietHours(true, ALL_DAY_START, ALL_DAY_END)

        assertTrue(
            guard.shouldShowCallNotification(),
            "a call during quiet hours must still be shown, or it cannot be answered"
        )
        assertTrue(guard.shouldSilenceCallNotification())
    }

    /** Messages and contact requests keep the old behaviour: suppressed outright. */
    @Test
    fun quietHoursStillSuppressMessagesAndRequests() = runTest {
        val repo = makeSettingsRepository(scope = this)
        val guard = NotificationGuard(repo)

        repo.updateQuietHours(true, ALL_DAY_START, ALL_DAY_END)

        assertFalse(guard.shouldShowMessageNotification())
        assertFalse(guard.shouldShowRequestNotification())
    }

    @Test
    fun outsideQuietHoursACallIsNotSilenced() = runTest {
        val repo = makeSettingsRepository(scope = this)
        val guard = NotificationGuard(repo)

        repo.updateQuietHours(false)

        assertTrue(guard.shouldShowCallNotification())
        assertFalse(guard.shouldSilenceCallNotification())
    }

    /** The per-type switch still hides call notifications; only quiet hours changed. */
    @Test
    fun theCallNotificationsSwitchStillHidesThem() = runTest {
        val repo = makeSettingsRepository(scope = this)
        val guard = NotificationGuard(repo)

        repo.updateCallNotifications(false)

        assertFalse(guard.shouldShowCallNotification())
    }

    @Test
    fun theMasterSwitchStillHidesCallNotifications() = runTest {
        val repo = makeSettingsRepository(scope = this)
        val guard = NotificationGuard(repo)

        repo.updateNotificationsEnabled(false)

        assertFalse(guard.shouldShowCallNotification())
    }

    private companion object {
        /** Midnight to midnight — in quiet hours whatever the clock says. */
        const val ALL_DAY_START = 0
        const val ALL_DAY_END = 24 * 60
    }
}
