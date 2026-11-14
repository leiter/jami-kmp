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
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.jami.services.StubDaemonBridge
import net.jami.ui.viewmodel.ProfileSetupViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileSetupViewModelTest {

    private fun makeVm(stub: StubDaemonBridge = StubDaemonBridge(), block: kotlinx.coroutines.test.TestScope.() -> Unit = {}) =
        kotlinx.coroutines.test.runTest {
            val accountService = makeAccountService(stub, this)
            block()
            ProfileSetupViewModel(accountService, this)
        }

    @Test
    fun initialStateHasEmptyDisplayName() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val vm = ProfileSetupViewModel(accountService, viewModelScope())
        assertEquals("", vm.state.value.displayName)
    }

    @Test
    fun initialStateHasNullAvatarPath() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val vm = ProfileSetupViewModel(accountService, viewModelScope())
        assertNull(vm.state.value.avatarPath)
    }

    @Test
    fun initialStateIsNotLoading() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val vm = ProfileSetupViewModel(accountService, viewModelScope())
        assertFalse(vm.state.value.isLoading)
        assertFalse(vm.state.value.isSaved)
    }

    @Test
    fun setDisplayNameUpdatesState() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val vm = ProfileSetupViewModel(accountService, viewModelScope())
        vm.setDisplayName("Alice")
        assertEquals("Alice", vm.state.value.displayName)
        assertNull(vm.state.value.error)
    }

    @Test
    fun setAvatarPathUpdatesState() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val vm = ProfileSetupViewModel(accountService, viewModelScope())
        vm.setAvatarPath("/tmp/avatar.png")
        assertEquals("/tmp/avatar.png", vm.state.value.avatarPath)
    }

    @Test
    fun setDisplayNameClearsError() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val vm = ProfileSetupViewModel(accountService, viewModelScope())
        // Trigger an error state by saving with no account
        vm.saveProfile()
        advanceUntilIdle()
        // Now set a display name — error should clear
        vm.setDisplayName("Bob")
        assertNull(vm.state.value.error)
    }

    @Test
    fun saveProfileWithNoCurrentAccountDoesNotCrash() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val vm = ProfileSetupViewModel(accountService, viewModelScope())
        vm.setDisplayName("Alice")
        vm.saveProfile()
        advanceUntilIdle()
        // With no account there is nothing to save, and saveProfile deliberately marks the
        // step complete so onboarding can move on — isSaved drives navigation in
        // ProfileSetupScreen, and leaving it false would strand the user on this screen.
        assertTrue(vm.state.value.isSaved)
    }

    @Test
    fun saveProfileWithCurrentAccountSetsSaved() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        prepareAccountInService(stub, accountService)
        val vm = ProfileSetupViewModel(accountService, viewModelScope())
        vm.setDisplayName("Alice")
        vm.saveProfile()
        advanceUntilIdle()
        assertTrue(vm.state.value.isSaved)
        assertFalse(vm.state.value.isLoading)
    }

    /**
     * libjami's updateProfile dereferences account info that only exists once the account has
     * finished initializing; calling it earlier crashed onboarding with SIGSEGV (2026-11-14).
     */
    @Test
    fun saveProfileWaitsUntilAccountIsInitialized() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        prepareAccountInService(stub, accountService)
        accountService.onRegistrationStateChanged(TEST_ACCOUNT_ID, "INITIALIZING", 0, "")
        runCurrent()

        val vm = ProfileSetupViewModel(accountService, viewModelScope())
        vm.setDisplayName("Alice")
        vm.saveProfile()
        runCurrent()
        assertTrue(stub.updateProfileCalls.isEmpty(), "must not reach the daemon while INITIALIZING")
        assertFalse(vm.state.value.isSaved)

        accountService.onRegistrationStateChanged(TEST_ACCOUNT_ID, "UNREGISTERED", 0, "")
        runCurrent()
        assertEquals(listOf(TEST_ACCOUNT_ID to "Alice"), stub.updateProfileCalls)
        assertTrue(vm.state.value.isSaved)
    }

    @Test
    fun onClearedDoesNotThrow() = runTest {
        val stub = StubDaemonBridge()
        val accountService = makeAccountService(stub, this)
        val vm = ProfileSetupViewModel(accountService, disposableScope())
        vm.onCleared()
    }
}
