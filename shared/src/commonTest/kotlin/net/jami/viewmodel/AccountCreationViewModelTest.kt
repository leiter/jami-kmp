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
import net.jami.model.ConfigKey
import net.jami.services.StubDaemonBridge
import net.jami.ui.viewmodel.AccountCreationViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.jami.ui.viewmodel.AccountCreationError

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
        assertTrue(error != AccountCreationError.PASSWORDS_DONT_MATCH)
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
    fun createAccountWithoutUsernameSucceedsAndRegistersNoName() = runTest {
        val stub = StubDaemonBridge()
        stub.addAccountResult = TEST_ACCOUNT_ID
        val accountService = makeAccountService(stub, this)
        val vm = AccountCreationViewModel(accountService, viewModelScope())
        // No username typed at all: the public directory must not be touched, and the
        // account must still be created (the wizard's "Skip choosing username").
        vm.createAccount()
        advanceUntilIdle()
        assertTrue(vm.state.value.isCreated)
        assertNull(vm.state.value.error)
        assertFalse(vm.state.value.isRegistering)
        val details = stub.addedAccountDetails.single()
        assertFalse(details.containsKey(ConfigKey.ACCOUNT_REGISTERED_NAME.key))
    }

    /**
     * Profile setup writes to the current account, so a newly created account that never becomes
     * current means the display name and avatar land on the previously selected account — seen
     * on a device 2026-11-07, where creating a second account renamed the first one.
     */
    @Test
    fun createAccountMakesTheNewAccountCurrent() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        prepareAccountInService(stub, accountService)
        assertEquals(TEST_ACCOUNT_ID, accountService.currentAccount.value?.accountId)

        val newId = "new_account_id"
        stub.addAccountResult = newId
        stub.accountIds = listOf(TEST_ACCOUNT_ID, newId)
        stub.accountDetails[newId] = mapOf(ConfigKey.ACCOUNT_TYPE.key to "RING")

        val vm = AccountCreationViewModel(accountService, viewModelScope())
        vm.createAccount()
        advanceUntilIdle()
        assertTrue(vm.state.value.isCreated)
        assertEquals(newId, accountService.currentAccount.value?.accountId)
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
