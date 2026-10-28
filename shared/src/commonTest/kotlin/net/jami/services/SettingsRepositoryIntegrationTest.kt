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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.jami.model.Account
import net.jami.model.ConfigKey
import net.jami.model.settings.SettingsKeys
import net.jami.model.settings.Theme
import net.jami.repository.SettingsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Integration tests for SettingsRepository using StubDaemonBridge.
 * Tests settings persistence via daemon account details.
 */
class SettingsRepositoryIntegrationTest {

    @Test
    fun initialUiSettingsAreDefaults() = runTest {
        val stub = StubDaemonBridge()
        val repo = SettingsRepository(stub, this)

        assertEquals(Theme.SYSTEM, repo.uiSettings.value.theme)
        assertEquals(1.0f, repo.uiSettings.value.fontScale)
        assertEquals("", repo.uiSettings.value.language)
    }

    @Test
    fun updateThemeChangesStateFlow() = runTest {
        val stub = StubDaemonBridge()
        stub.accountDetails["acc1"] = mutableMapOf()
        val repo = SettingsRepository(stub, this)
        repo.observeAccount("acc1")

        repo.updateTheme(Theme.DARK)

        assertEquals(Theme.DARK, repo.uiSettings.value.theme)
    }

    @Test
    fun updateReadReceiptsChangesPrivacySettings() = runTest {
        val stub = StubDaemonBridge()
        stub.accountDetails["acc1"] = mutableMapOf()
        val repo = SettingsRepository(stub, this)
        repo.observeAccount("acc1")

        assertTrue(repo.privacySettings.value.readReceipts)
        repo.updateReadReceipts(false)
        assertFalse(repo.privacySettings.value.readReceipts)
    }

    @Test
    fun stopObservingResetsToDefaults() = runTest {
        val stub = StubDaemonBridge()
        stub.accountDetails["acc1"] = mutableMapOf()
        val repo = SettingsRepository(stub, this)
        repo.observeAccount("acc1")
        repo.updateTheme(Theme.DARK)

        repo.stopObserving()

        assertEquals(Theme.SYSTEM, repo.uiSettings.value.theme)
    }

    @Test
    fun updateNotificationsEnabledChangesState() = runTest {
        val stub = StubDaemonBridge()
        stub.accountDetails["acc1"] = mutableMapOf()
        val repo = SettingsRepository(stub, this)
        repo.observeAccount("acc1")

        assertTrue(repo.notificationSettings.value.enabled)
        repo.updateNotificationsEnabled(false)
        assertFalse(repo.notificationSettings.value.enabled)
    }

    @Test
    fun updateVideoEnabledChangesCallSettings() = runTest {
        val stub = StubDaemonBridge()
        stub.accountDetails["acc1"] = mutableMapOf()
        val repo = SettingsRepository(stub, this)
        repo.observeAccount("acc1")

        assertTrue(repo.callSettings.value.videoEnabled)
        repo.updateVideoEnabled(false)
        assertFalse(repo.callSettings.value.videoEnabled)
    }

    @Test
    fun muteConversationUpdatesConversationSettings() = runTest {
        val stub = StubDaemonBridge()
        stub.accountDetails["acc1"] = mutableMapOf()
        val repo = SettingsRepository(stub, this)
        repo.observeAccount("acc1")

        assertFalse(repo.isConversationMuted("conv123"))
        repo.muteConversation("conv123")
        assertTrue(repo.isConversationMuted("conv123"))
    }

    @Test
    fun unmuteConversationClearsMute() = runTest {
        val stub = StubDaemonBridge()
        stub.accountDetails["acc1"] = mutableMapOf()
        val repo = SettingsRepository(stub, this)
        repo.observeAccount("acc1")

        repo.muteConversation("conv123")
        assertTrue(repo.isConversationMuted("conv123"))

        repo.unmuteConversation("conv123")
        assertFalse(repo.isConversationMuted("conv123"))
    }

    // ==================== Account following ====================

    /**
     * Nothing called observeAccount() in production until 2026-10-28, so currentAccountId stayed
     * null and both loadSettings() and saveSettings() returned early: every KMP.* setting lived
     * in memory for the session and was lost on restart.
     */
    @Test
    fun followingTheAccountFlowMakesSettingsPersist() = runTest {
        val stub = StubDaemonBridge()
        stub.accountDetails["acc1"] = mutableMapOf()
        val accounts = MutableStateFlow<Account?>(null)
        // follow() collects for as long as the repository lives, so it gets a scope that is not
        // a child of the test body's job — otherwise runTest waits for a collector that never
        // finishes.
        val repoScope = kotlinx.coroutines.CoroutineScope(coroutineContext + kotlinx.coroutines.SupervisorJob())
        val repo = SettingsRepository(stub, repoScope)
        repo.follow(accounts)

        repo.updateTheme(Theme.DARK)
        advanceUntilIdle()
        assertFalse(
            stub.accountDetails["acc1"]!!.keys.any { SettingsKeys.isKmpKey(it) },
            "with no account there is nowhere to store settings"
        )

        accounts.value = Account("acc1", mutableMapOf(ConfigKey.ACCOUNT_TYPE.key to "RING"))
        advanceUntilIdle()
        // A different value, because updateTheme() short-circuits when the in-memory state
        // already matches — which is how the missing persistence stayed invisible.
        repo.updateTheme(Theme.LIGHT)
        advanceUntilIdle()

        assertTrue(stub.accountDetails["acc1"]!!.containsKey(SettingsKeys.UI))
    }

    @Test
    fun switchingAccountsDoesNotCarryTheOldAccountsSettingsOver() = runTest {
        val stub = StubDaemonBridge()
        stub.accountDetails["acc1"] = mutableMapOf(
            SettingsKeys.UI to """{"theme":"DARK"}"""
        )
        stub.accountDetails["acc2"] = mutableMapOf()
        val repo = SettingsRepository(stub, this)

        repo.observeAccount("acc1")
        advanceUntilIdle()
        assertEquals(Theme.DARK, repo.uiSettings.value.theme)

        repo.observeAccount("acc2")
        advanceUntilIdle()

        assertEquals(Theme.SYSTEM, repo.uiSettings.value.theme)
    }
}
