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
import net.jami.services.StubDaemonBridge
import net.jami.ui.viewmodel.AccountSettingsViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountSettingsViewModelTest {

    @Test
    fun initialStateIsEmpty() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val repo = makeSettingsRepository(stub, this)
        val vm = AccountSettingsViewModel(accountService, repo, null, viewModelScope())
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
        val vm = AccountSettingsViewModel(accountService, repo, null, viewModelScope())
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
        val vm = AccountSettingsViewModel(accountService, repo, null, viewModelScope())
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
        val vm = AccountSettingsViewModel(accountService, repo, null, viewModelScope())
        vm.updateDisplayName("Bob")
        advanceUntilIdle()
        assertEquals("Bob", vm.state.value.displayName)
    }

    @Test
    fun updateDisplayNameWithNoAccountDoesNotCrash() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val repo = makeSettingsRepository(stub, this)
        val vm = AccountSettingsViewModel(accountService, repo, null, viewModelScope())
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
        val vm = AccountSettingsViewModel(accountService, repo, null, viewModelScope())
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
        val vm = AccountSettingsViewModel(accountService, repo, null, viewModelScope())
        vm.revokeDevice("device1", "password")
        advanceUntilIdle()
        // No crash
    }

    @Test
    fun onClearedDoesNotThrow() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val repo = makeSettingsRepository(stub, this)
        val vm = AccountSettingsViewModel(accountService, repo, null, disposableScope())
        vm.onCleared()
    }
}
