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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import net.jami.model.ConfigKey
import net.jami.model.settings.NotificationSettings
import net.jami.model.settings.SettingsKeys
import net.jami.repository.SettingsRepository
import net.jami.ui.platform.LocalPrefs
import net.jami.viewmodel.makeAccountService
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** CallKit rings on iOS, never the daemon; quiet hours swap in a silent CallKit ringtone. */
class IOSCallRingingTest {

    @BeforeTest
    @AfterTest
    fun clearMirror() {
        LocalPrefs.setBoolean(IOSCallRinging.KEY_QUIET_ENABLED, false)
        LocalPrefs.setInt(IOSCallRinging.KEY_QUIET_START, NotificationSettings().quietHoursStart)
        LocalPrefs.setInt(IOSCallRinging.KEY_QUIET_END, NotificationSettings().quietHoursEnd)
    }

    private fun ringing(stub: StubDaemonBridge, scope: kotlinx.coroutines.CoroutineScope, store: ClientSettingsStore = InMemoryClientSettingsStore()) =
        IOSCallRinging(
            accountService = makeAccountService(stub, scope),
            daemonBridge = stub,
            settingsRepository = SettingsRepository(stub, store, scope),
            scope = scope,
        )

    @Test
    fun switchesTheDaemonRingtoneOffOnEveryAccount() = runTest {
        val stub = StubDaemonBridge()
        stub.accountIds = listOf("on", "unset", "off")
        stub.accountDetails["on"] = mapOf(ConfigKey.RINGTONE_ENABLED.key to "true", "keep" to "me")
        stub.accountDetails["unset"] = mapOf("keep" to "me")
        stub.accountDetails["off"] = mapOf(ConfigKey.RINGTONE_ENABLED.key to "false")

        ringing(stub, backgroundScope).disableDaemonRingtone()

        for (id in stub.accountIds) {
            assertEquals("false", stub.accountDetails[id]!![ConfigKey.RINGTONE_ENABLED.key], id)
        }
        // The rest of the account is written back untouched.
        assertEquals("me", stub.accountDetails["on"]!!["keep"])
    }

    @Test
    fun ringsWithTheBundledToneOutsideQuietHours() {
        assertEquals(IOSCallRinging.RINGTONE_SOUND, IOSCallRinging.ringtoneSoundNow())
    }

    @Test
    fun ringsSilentlyInsideTheMirroredWindow() = runTest {
        // A window covering the whole day makes "now" deterministic without a clock seam.
        ringing(StubDaemonBridge(), backgroundScope)
            .mirrorQuietHours(NotificationSettings(quietHoursEnabled = true, quietHoursStart = 0, quietHoursEnd = 1440))

        assertEquals(IOSCallRinging.SILENT_SOUND, IOSCallRinging.ringtoneSoundNow())
    }

    @Test
    fun mirrorsOnlyOnceAnAccountsSettingsHaveLoaded() = runTest {
        val stub = StubDaemonBridge()
        val store = InMemoryClientSettingsStore().apply {
            put("acc1", SettingsKeys.NOTIFICATIONS, Json.encodeToString(
                NotificationSettings(quietHoursEnabled = true, quietHoursStart = 0, quietHoursEnd = 1440)))
        }
        // Not backgroundScope: advanceUntilIdle() stops once only background work is left, so
        // the settings load would never run. A foreground child scope, cancelled at the end.
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val accountService = makeAccountService(stub, scope)
        val repo = SettingsRepository(stub, store, scope)
        IOSCallRinging(accountService, stub, repo, scope).start()

        // Defaults only, nothing loaded: the mirror must not be written.
        LocalPrefs.setBoolean(IOSCallRinging.KEY_QUIET_ENABLED, true)
        testScheduler.advanceUntilIdle()
        assertEquals(true, LocalPrefs.getBoolean(IOSCallRinging.KEY_QUIET_ENABLED, false))
        clearMirror()

        repo.observeAccount("acc1")
        testScheduler.advanceUntilIdle()
        assertEquals(IOSCallRinging.SILENT_SOUND, IOSCallRinging.ringtoneSoundNow())

        repo.updateQuietHours(enabled = false)
        testScheduler.advanceUntilIdle()
        assertFalse(LocalPrefs.getBoolean(IOSCallRinging.KEY_QUIET_ENABLED, true))
        scope.cancel()
    }
}
