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
package net.jami.viewmodel

import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.jami.model.ConfigKey
import net.jami.model.settings.PresenceNote
import net.jami.model.settings.PresenceNoteError
import net.jami.services.StubDaemonBridge
import net.jami.ui.viewmodel.AccountSettingsViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountSettingsViewModelTest {

    @Test
    fun initialStateIsEmpty() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val repo = makeSettingsRepository(stub, this)
        val vm = AccountSettingsViewModel(accountService, repo, scope = viewModelScope())
        assertEquals("", vm.state.value.displayName)
        assertEquals("", vm.state.value.username)
        assertTrue(vm.state.value.devices.isEmpty())
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun loadAccountWithNoCurrentAccountDoesNotCrash() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val repo = makeSettingsRepository(stub, this)
        val vm = AccountSettingsViewModel(accountService, repo, scope = viewModelScope())
        vm.loadAccount()
        advanceUntilIdle()
        // currentAccount is null → early return
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun loadAccountWithCurrentAccountPopulatesDisplayName() = runTest {
        val stub = StubDaemonBridge()
        stub.accountIds = listOf(TEST_ACCOUNT_ID)
        stub.accountDetails[TEST_ACCOUNT_ID] = mapOf(
            ConfigKey.ACCOUNT_TYPE.key to "RING",
            ConfigKey.ACCOUNT_DISPLAYNAME.key to "Alice"
        )
        val accountService = makeAccountService(stub, this)
        accountService.loadAccounts()
        val repo = makeSettingsRepository(stub, this)
        val vm = AccountSettingsViewModel(accountService, repo, scope = viewModelScope())
        vm.loadAccount()
        advanceUntilIdle()
        assertEquals("Alice", vm.state.value.displayName)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun updateDisplayNameCallsDaemonAndUpdatesState() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        prepareAccountInService(stub, accountService)
        val repo = makeSettingsRepository(stub, this)
        val vm = AccountSettingsViewModel(accountService, repo, scope = viewModelScope())
        vm.updateDisplayName("Bob")
        advanceUntilIdle()
        assertEquals("Bob", vm.state.value.displayName)
    }

    @Test
    fun updateDisplayNameWithNoAccountDoesNotCrash() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val repo = makeSettingsRepository(stub, this)
        val vm = AccountSettingsViewModel(accountService, repo, scope = viewModelScope())
        vm.updateDisplayName("Bob")
        advanceUntilIdle()
        // No account → early return, displayName stays empty
        assertEquals("", vm.state.value.displayName)
    }

    @Test
    fun knownDevicesChangedEventUpdatesDeviceList() = runTest {
        val stub = StubDaemonBridge()
        stub.accountIds = listOf(TEST_ACCOUNT_ID)
        stub.accountDetails[TEST_ACCOUNT_ID] = mapOf(ConfigKey.ACCOUNT_TYPE.key to "RING")
        val accountService = makeAccountService(stub, this)
        accountService.loadAccounts()
        val repo = makeSettingsRepository(stub, this)
        val vm = AccountSettingsViewModel(accountService, repo, scope = viewModelScope())
        val devices = mapOf("device1" to "My Phone", "device2" to "My Laptop")
        accountService.onKnownDevicesChanged(TEST_ACCOUNT_ID, devices)
        advanceUntilIdle()
        assertEquals(2, vm.state.value.devices.size)
    }

    @Test
    fun revokeDeviceWithCurrentAccountDoesNotCrash() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        prepareAccountInService(stub, accountService)
        val repo = makeSettingsRepository(stub, this)
        val vm = AccountSettingsViewModel(accountService, repo, scope = viewModelScope())
        vm.revokeDevice("device1", "password")
        advanceUntilIdle()
        // No crash
    }

    @Test
    fun onClearedDoesNotThrow() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val repo = makeSettingsRepository(stub, this)
        val vm = AccountSettingsViewModel(accountService, repo, scope = disposableScope())
        vm.onCleared()
    }

    // ==================== Status note (daemon-audit F6) ====================

    /**
     * Each publish sends a presence message to every contact, so typing must not publish —
     * only the explicit commit does.
     */
    @Test
    fun typingAStatusNoteDoesNotPublishItUntilCommitted() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        prepareAccountInService(stub, accountService)
        val repo = makeSettingsRepository(stub, this)
        repo.observeAccount(TEST_ACCOUNT_ID)
        val vm = AccountSettingsViewModel(accountService, repo, scope = viewModelScope())
        advanceUntilIdle()

        vm.onStatusNoteChanged("aw")
        vm.onStatusNoteChanged("awa")
        vm.onStatusNoteChanged("away")
        advanceUntilIdle()

        assertEquals("away", vm.state.value.statusNote)
        assertTrue(stub.publishedPresenceNotes.isEmpty(), "typing must not reach the daemon")

        assertTrue(vm.commitStatusNote())
        advanceUntilIdle()

        assertEquals(listOf(Triple(TEST_ACCOUNT_ID, true, "away")), stub.publishedPresenceNotes)
        assertEquals("away", vm.state.value.publishedStatusNote)
        assertNull(vm.state.value.statusNoteError)
    }

    @Test
    fun anUndeliverableStatusNoteIsFlaggedAndNotPublished() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        prepareAccountInService(stub, accountService)
        val repo = makeSettingsRepository(stub, this)
        repo.observeAccount(TEST_ACCOUNT_ID)
        val vm = AccountSettingsViewModel(accountService, repo, scope = viewModelScope())
        advanceUntilIdle()

        vm.onStatusNoteChanged("back at 5pm!")
        advanceUntilIdle()

        assertEquals(PresenceNoteError.UNSUPPORTED_CHARACTERS, vm.state.value.statusNoteError)
        assertFalse(vm.commitStatusNote())
        advanceUntilIdle()
        assertTrue(stub.publishedPresenceNotes.isEmpty())
    }

    @Test
    fun clearingTheStatusNoteTellsContacts() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        prepareAccountInService(stub, accountService)
        val repo = makeSettingsRepository(stub, this)
        repo.observeAccount(TEST_ACCOUNT_ID)
        val vm = AccountSettingsViewModel(accountService, repo, scope = viewModelScope())
        advanceUntilIdle()

        vm.onStatusNoteChanged("away")
        vm.commitStatusNote()
        advanceUntilIdle()

        vm.clearStatusNote()
        advanceUntilIdle()

        assertEquals("", vm.state.value.statusNote)
        assertEquals(
            Triple(TEST_ACCOUNT_ID, true, PresenceNote.CLEARED),
            stub.publishedPresenceNotes.last(),
            "an empty note would leave peers showing the old one"
        )
    }

    /** A note published from another device arrives through the settings sync. */
    @Test
    fun aNoteFromAnotherDeviceShowsUpInTheField() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        prepareAccountInService(stub, accountService)
        val repo = makeSettingsRepository(stub, this)
        repo.observeAccount(TEST_ACCOUNT_ID)
        val vm = AccountSettingsViewModel(accountService, repo, scope = viewModelScope())
        advanceUntilIdle()

        repo.updateStatusNote("on holiday")
        advanceUntilIdle()

        assertEquals("on holiday", vm.state.value.statusNote)
        assertEquals("on holiday", vm.state.value.publishedStatusNote)
    }

    /** …but it must not overwrite what the user is in the middle of typing. */
    @Test
    fun anIncomingNoteDoesNotDiscardAnUnsavedEdit() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        prepareAccountInService(stub, accountService)
        val repo = makeSettingsRepository(stub, this)
        repo.observeAccount(TEST_ACCOUNT_ID)
        val vm = AccountSettingsViewModel(accountService, repo, scope = viewModelScope())
        advanceUntilIdle()

        vm.onStatusNoteChanged("typing this")
        repo.updateStatusNote("from elsewhere")
        advanceUntilIdle()

        assertEquals("typing this", vm.state.value.statusNote)
        assertEquals("from elsewhere", vm.state.value.publishedStatusNote)
    }
}
