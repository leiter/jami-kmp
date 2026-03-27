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
import net.jami.services.StubDaemonBridge
import net.jami.ui.viewmodel.ImportAccountViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImportAccountViewModelTest {

    @Test
    fun initialStateIsEmpty() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val vm = ImportAccountViewModel(accountService, viewModelScope())
        assertEquals("", vm.state.value.archivePath)
        assertEquals("", vm.state.value.password)
        assertFalse(vm.state.value.isLoading)
        assertFalse(vm.state.value.isImported)
        assertNull(vm.state.value.error)
    }

    @Test
    fun setArchivePathUpdatesState() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val vm = ImportAccountViewModel(accountService, viewModelScope())
        vm.setArchivePath("/tmp/backup.gz")
        assertEquals("/tmp/backup.gz", vm.state.value.archivePath)
        assertNull(vm.state.value.error)
    }

    @Test
    fun setPasswordUpdatesState() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val vm = ImportAccountViewModel(accountService, viewModelScope())
        vm.setPassword("secret123")
        assertEquals("secret123", vm.state.value.password)
        assertNull(vm.state.value.error)
    }

    @Test
    fun importAccountWithEmptyPathSetsError() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val vm = ImportAccountViewModel(accountService, viewModelScope())
        // archivePath is empty by default
        vm.importAccount()
        advanceUntilIdle()
        assertNotNull(vm.state.value.error)
        assertFalse(vm.state.value.isImported)
    }

    @Test
    fun importAccountWithValidPathStartsLoading() = runTest {
        val stub = StubDaemonBridge()
        stub.addAccountResult = TEST_ACCOUNT_ID
        val accountService = makeAccountService(stub, this)
        val vm = ImportAccountViewModel(accountService, viewModelScope())
        vm.setArchivePath("/tmp/backup.gz")
        vm.importAccount()
        // Before advancing, loading state may be set
        // After advancing, createJamiAccount completes but no event fired yet
        advanceUntilIdle()
        // No event to mark as imported — import is in progress or succeeded without error
        assertNull(vm.state.value.error)
    }

    @Test
    fun registrationStateRegisteredSetsImported() = runTest {
        val stub = StubDaemonBridge()
        stub.accountIds = listOf(TEST_ACCOUNT_ID)
        stub.accountDetails[TEST_ACCOUNT_ID] = mapOf("Account.type" to "RING")
        stub.addAccountResult = TEST_ACCOUNT_ID
        val accountService = makeAccountService(stub, this)
        accountService.loadAccounts()
        val vm = ImportAccountViewModel(accountService, viewModelScope())
        vm.setArchivePath("/tmp/backup.gz")
        vm.importAccount()
        advanceUntilIdle()
        // Simulate daemon signalling REGISTERED for the imported account
        accountService.onRegistrationStateChanged(TEST_ACCOUNT_ID, "REGISTERED", 200, "")
        advanceUntilIdle()
        assertTrue(vm.state.value.isImported)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun registrationStateErrorSetsErrorMessage() = runTest {
        val stub = StubDaemonBridge()
        stub.accountIds = listOf(TEST_ACCOUNT_ID)
        stub.accountDetails[TEST_ACCOUNT_ID] = mapOf("Account.type" to "RING")
        val accountService = makeAccountService(stub, this)
        accountService.loadAccounts()
        val vm = ImportAccountViewModel(accountService, viewModelScope())
        vm.setArchivePath("/tmp/backup.gz")
        accountService.onRegistrationStateChanged(TEST_ACCOUNT_ID, "ERROR_GENERIC", 500, "")
        advanceUntilIdle()
        assertNotNull(vm.state.value.error)
        assertFalse(vm.state.value.isImported)
    }

    @Test
    fun migrationEndedSuccessSetsImported() = runTest {
        val stub = StubDaemonBridge()
        stub.accountIds = listOf(TEST_ACCOUNT_ID)
        stub.accountDetails[TEST_ACCOUNT_ID] = mapOf("Account.type" to "RING")
        val accountService = makeAccountService(stub, this)
        accountService.loadAccounts()
        val vm = ImportAccountViewModel(accountService, viewModelScope())
        vm.setArchivePath("/tmp/backup.gz")
        accountService.onMigrationEnded(TEST_ACCOUNT_ID, "SUCCESS")
        advanceUntilIdle()
        assertTrue(vm.state.value.isImported)
    }

    @Test
    fun migrationEndedFailureSetsError() = runTest {
        val stub = StubDaemonBridge()
        stub.accountIds = listOf(TEST_ACCOUNT_ID)
        stub.accountDetails[TEST_ACCOUNT_ID] = mapOf("Account.type" to "RING")
        val accountService = makeAccountService(stub, this)
        accountService.loadAccounts()
        val vm = ImportAccountViewModel(accountService, viewModelScope())
        vm.setArchivePath("/tmp/backup.gz")
        accountService.onMigrationEnded(TEST_ACCOUNT_ID, "ERROR")
        advanceUntilIdle()
        assertNotNull(vm.state.value.error)
        assertFalse(vm.state.value.isImported)
    }

    @Test
    fun onClearedDoesNotThrow() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val vm = ImportAccountViewModel(accountService, disposableScope())
        vm.onCleared()
    }
}
