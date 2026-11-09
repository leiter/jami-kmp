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
import net.jami.model.settings.NotificationSettings
import net.jami.model.settings.SettingsKeys
import net.jami.repository.SettingsRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * SettingsRepository.loadedAccountId tells "the user's settings" from "defaults, because
 * nothing has loaded yet". iOS mirrors quiet hours into NSUserDefaults only when it is set;
 * mirroring the defaults would switch quiet hours off for the next push-woken call.
 */
class SettingsLoadedAccountTest {

    private fun storeWithQuietHours(accountId: String) = InMemoryClientSettingsStore().apply {
        put(accountId, SettingsKeys.NOTIFICATIONS, Json.encodeToString(NotificationSettings(quietHoursEnabled = true)))
    }

    @Test
    fun nullBeforeAnyAccountIsObserved() = runTest {
        val repo = SettingsRepository(StubDaemonBridge(), InMemoryClientSettingsStore(), this)
        assertNull(repo.loadedAccountId.value)
    }

    @Test
    fun namesTheAccountOnceItsSettingsHaveLoaded() = runTest {
        val repo = SettingsRepository(StubDaemonBridge(), storeWithQuietHours("acc1"), this)
        repo.observeAccount("acc1")
        testScheduler.advanceUntilIdle()

        assertEquals("acc1", repo.loadedAccountId.value)
        assertTrue(repo.notificationSettings.value.quietHoursEnabled)
    }

    @Test
    fun clearedWhileASwitchedToAccountIsStillLoading() = runTest {
        val repo = SettingsRepository(StubDaemonBridge(), storeWithQuietHours("acc1"), this)
        repo.observeAccount("acc1")
        testScheduler.advanceUntilIdle()

        // The switch resets the flows to defaults synchronously; the load is still queued.
        repo.observeAccount("acc2")
        assertNull(repo.loadedAccountId.value)

        testScheduler.advanceUntilIdle()
        assertEquals("acc2", repo.loadedAccountId.value)
    }

    @Test
    fun clearedWhenObservingStops() = runTest {
        val repo = SettingsRepository(StubDaemonBridge(), storeWithQuietHours("acc1"), this)
        repo.observeAccount("acc1")
        testScheduler.advanceUntilIdle()

        repo.stopObserving()
        assertNull(repo.loadedAccountId.value)
    }
}
