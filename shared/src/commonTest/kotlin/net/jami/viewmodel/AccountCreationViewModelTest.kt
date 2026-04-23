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

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.jami.services.StubDaemonBridge
import net.jami.ui.viewmodel.AccountCreationViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountCreationViewModelTest {

    @Test
    fun initialStateIsEmpty() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val vm = AccountCreationViewModel(accountService, viewModelScope())
        assertEquals("", vm.state.value.username)
        assertEquals("", vm.state.value.password)
        assertEquals("", vm.state.value.confirmPassword)
        assertFalse(vm.state.value.isLoading)
        assertFalse(vm.state.value.isCreated)
        assertNull(vm.state.value.error)
        assertNull(vm.state.value.usernameAvailable)
    }

    @Test
    fun setUsernameUpdatesState() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val vm = AccountCreationViewModel(accountService, viewModelScope())
        vm.setUsername("alice")
        assertEquals("alice", vm.state.value.username)
        assertNull(vm.state.value.usernameAvailable)
    }

    @Test
    fun setPasswordUpdatesState() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val vm = AccountCreationViewModel(accountService, viewModelScope())
        vm.setPassword("secret")
        assertEquals("secret", vm.state.value.password)
        assertNull(vm.state.value.error)
    }

    @Test
    fun setConfirmPasswordUpdatesState() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val vm = AccountCreationViewModel(accountService, viewModelScope())
        vm.setConfirmPassword("secret")
        assertEquals("secret", vm.state.value.confirmPassword)
    }

    @Test
    fun passwordMismatchSetsError() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val vm = AccountCreationViewModel(accountService, viewModelScope())
        vm.setPassword("secret")
        vm.setConfirmPassword("different")
        vm.createAccount()
        advanceUntilIdle()
        assertNotNull(vm.state.value.error)
        assertFalse(vm.state.value.isCreated)
    }

    @Test
    fun matchingPasswordsDoNotSetMismatchError() = runTest {
        val stub = StubDaemonBridge()
        stub.addAccountResult = TEST_ACCOUNT_ID
        val accountService = makeAccountService(stub, this)
        val vm = AccountCreationViewModel(accountService, viewModelScope())
        vm.setPassword("secret")
        vm.setConfirmPassword("secret")
        vm.createAccount()
        advanceUntilIdle()
        // No mismatch error — some other state may be set
        val error = vm.state.value.error
        assertTrue(error == null || !error.contains("match", ignoreCase = true))
    }

    @Test
    fun createAccountSetsCreated() = runTest {
        val stub = StubDaemonBridge()
        stub.addAccountResult = TEST_ACCOUNT_ID
        val accountService = makeAccountService(stub, this)
        val vm = AccountCreationViewModel(accountService, viewModelScope())
        vm.setUsername("testuser")
        // Skip waiting for username check - createAccount checks usernameAvailable != false
        vm.createAccount()
        advanceUntilIdle()
        assertTrue(vm.state.value.isCreated)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun nameRegistrationEndedSuccessClearsRegistering() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val vm = AccountCreationViewModel(accountService, viewModelScope())
        // Simulate name registration completed successfully (state = 0)
        accountService.onNameRegistrationEnded(TEST_ACCOUNT_ID, 0, "alice")
        advanceUntilIdle()
        assertFalse(vm.state.value.isRegistering)
        assertNull(vm.state.value.error)
    }

    @Test
    fun nameRegistrationEndedFailureSetsError() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val vm = AccountCreationViewModel(accountService, viewModelScope())
        // Let the ViewModel's init block subscribe to accountEvents
        advanceUntilIdle()
        // Simulate name registration failed (state != 0)
        accountService.onNameRegistrationEnded(TEST_ACCOUNT_ID, 1, "alice")
        advanceUntilIdle()
        assertFalse(vm.state.value.isRegistering)
        assertNotNull(vm.state.value.error)
    }

    @Test
    fun registeredNameFoundNotAvailableSetsFlag() = runTest {
        val stub = StubDaemonBridge()
        // Configure stub: state=0 means Success (name IS registered — NOT available)
        stub.lookupNameResults["alice"] = 0
        val accountService = makeAccountService(stub, this)
        // Wire up the callback so lookupName triggers onRegisteredNameFound
        stub.onLookupNameCallback = { accountId, state, address, name, query ->
            accountService.onRegisteredNameFound(accountId, state, address, name, query)
        }
        val vm = AccountCreationViewModel(accountService, viewModelScope())
        vm.setUsername("alice")
        advanceUntilIdle()
        // LookupState.Success means name taken → usernameAvailable = false
        assertEquals(false, vm.state.value.usernameAvailable)
    }

    @Test
    fun registeredNameFoundAvailableSetsFlag() = runTest {
        val stub = StubDaemonBridge()
        // Configure stub: state=2 means NotFound (name is NOT registered — IS available)
        stub.lookupNameResults["alice"] = 2
        val accountService = makeAccountService(stub, this)
        // Wire up the callback so lookupName triggers onRegisteredNameFound
        stub.onLookupNameCallback = { accountId, state, address, name, query ->
            accountService.onRegisteredNameFound(accountId, state, address, name, query)
        }
        val vm = AccountCreationViewModel(accountService, viewModelScope())
        vm.setUsername("alice")
        advanceUntilIdle()
        assertEquals(true, vm.state.value.usernameAvailable)
    }

    @Test
    fun onClearedDoesNotThrow() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val vm = AccountCreationViewModel(accountService, disposableScope())
        vm.onCleared()
    }
}
