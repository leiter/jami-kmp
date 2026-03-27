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
import net.jami.ui.viewmodel.AppState
import net.jami.ui.viewmodel.AppViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AppViewModelTest {

    @Test
    fun initialStateIsLoading() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        // Do NOT advance — state should still be Loading before coroutines run
        val vm = AppViewModel(accountService, viewModelScope())
        assertIs<AppState.Loading>(vm.appState.value)
    }

    @Test
    fun emptyAccountListTransitionsToNoAccounts() = runTest {
        val stub = StubDaemonBridge()
        // accountIds defaults to empty list
        val accountService = makeAccountService(stub, this)
        val vm = AppViewModel(accountService, viewModelScope())
        advanceUntilIdle()
        assertIs<AppState.NoAccounts>(vm.appState.value)
    }

    @Test
    fun oneAccountTransitionsToHasAccounts() = runTest {
        val stub = StubDaemonBridge()
        stub.accountIds = listOf(TEST_ACCOUNT_ID)
        stub.accountDetails[TEST_ACCOUNT_ID] = mapOf(
            ConfigKey.ACCOUNT_TYPE.key to "RING"
        )
        val accountService = makeAccountService(stub, this)
        val vm = AppViewModel(accountService, viewModelScope())
        advanceUntilIdle()
        assertIs<AppState.HasAccounts>(vm.appState.value)
    }

    @Test
    fun hasAccountsWithoutMigrationFlagFalse() = runTest {
        val stub = StubDaemonBridge()
        stub.accountIds = listOf(TEST_ACCOUNT_ID)
        stub.accountDetails[TEST_ACCOUNT_ID] = mapOf(
            ConfigKey.ACCOUNT_TYPE.key to "RING",
            ConfigKey.ACCOUNT_REGISTRATION_STATUS.key to "REGISTERED"
        )
        val accountService = makeAccountService(stub, this)
        val vm = AppViewModel(accountService, viewModelScope())
        advanceUntilIdle()
        val state = vm.appState.value
        assertIs<AppState.HasAccounts>(state)
        assertEquals(false, state.needsMigration)
    }

    @Test
    fun multipleAccountsAllTransitionToHasAccounts() = runTest {
        val stub = StubDaemonBridge()
        val ids = listOf("acc1", "acc2", "acc3")
        stub.accountIds = ids
        ids.forEach { id ->
            stub.accountDetails[id] = mapOf(ConfigKey.ACCOUNT_TYPE.key to "RING")
        }
        val accountService = makeAccountService(stub, this)
        val vm = AppViewModel(accountService, viewModelScope())
        advanceUntilIdle()
        assertIs<AppState.HasAccounts>(vm.appState.value)
    }

    @Test
    fun onClearedDoesNotThrow() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val vm = AppViewModel(accountService, disposableScope())
        vm.onCleared()
    }
}
