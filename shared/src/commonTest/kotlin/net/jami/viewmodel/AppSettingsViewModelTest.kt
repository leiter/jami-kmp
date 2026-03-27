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
import net.jami.ui.viewmodel.AppSettingsViewModel
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppSettingsViewModelTest {

    @Test
    fun initialStateDefaultsAreCorrect() = runTest {
        val stub = StubDaemonBridge()
        val repo = makeSettingsRepository(stub, this)
        val vm = AppSettingsViewModel(repo, viewModelScope())
        advanceUntilIdle()
        assertFalse(vm.state.value.isDarkTheme)
        assertTrue(vm.state.value.isTypingIndicators)
        assertTrue(vm.state.value.isLinkPreview)
        assertFalse(vm.state.value.isScreenshotBlocking)
        assertFalse(vm.state.value.isStartOnBoot)
        assertFalse(vm.state.value.isRunInBackground)
        assertTrue(vm.state.value.isPushNotifications)
    }

    @Test
    fun toggleDarkThemeFlipsValue() = runTest {
        val stub = StubDaemonBridge()
        val repo = makeSettingsRepository(stub, this)
        val vm = AppSettingsViewModel(repo, viewModelScope())
        advanceUntilIdle()
        val before = vm.state.value.isDarkTheme
        vm.toggleDarkTheme()
        assertTrue(vm.state.value.isDarkTheme != before)
    }

    @Test
    fun toggleDarkThemeTwiceRestoresOriginal() = runTest {
        val stub = StubDaemonBridge()
        val repo = makeSettingsRepository(stub, this)
        val vm = AppSettingsViewModel(repo, viewModelScope())
        advanceUntilIdle()
        val before = vm.state.value.isDarkTheme
        vm.toggleDarkTheme()
        vm.toggleDarkTheme()
        assertTrue(vm.state.value.isDarkTheme == before)
    }

    @Test
    fun toggleTypingIndicatorsFlipsValue() = runTest {
        val stub = StubDaemonBridge()
        val repo = makeSettingsRepository(stub, this)
        val vm = AppSettingsViewModel(repo, viewModelScope())
        advanceUntilIdle()
        val before = vm.state.value.isTypingIndicators
        vm.toggleTypingIndicators()
        assertTrue(vm.state.value.isTypingIndicators != before)
    }

    @Test
    fun toggleLinkPreviewFlipsValue() = runTest {
        val stub = StubDaemonBridge()
        val repo = makeSettingsRepository(stub, this)
        val vm = AppSettingsViewModel(repo, viewModelScope())
        advanceUntilIdle()
        val before = vm.state.value.isLinkPreview
        vm.toggleLinkPreview()
        assertTrue(vm.state.value.isLinkPreview != before)
    }

    @Test
    fun toggleScreenshotBlockingFlipsValue() = runTest {
        val stub = StubDaemonBridge()
        val repo = makeSettingsRepository(stub, this)
        val vm = AppSettingsViewModel(repo, viewModelScope())
        assertFalse(vm.state.value.isScreenshotBlocking)
        vm.toggleScreenshotBlocking()
        assertTrue(vm.state.value.isScreenshotBlocking)
    }

    @Test
    fun toggleStartOnBootFlipsValue() = runTest {
        val stub = StubDaemonBridge()
        val repo = makeSettingsRepository(stub, this)
        val vm = AppSettingsViewModel(repo, viewModelScope())
        assertFalse(vm.state.value.isStartOnBoot)
        vm.toggleStartOnBoot()
        assertTrue(vm.state.value.isStartOnBoot)
    }

    @Test
    fun toggleRunInBackgroundFlipsValue() = runTest {
        val stub = StubDaemonBridge()
        val repo = makeSettingsRepository(stub, this)
        val vm = AppSettingsViewModel(repo, viewModelScope())
        assertFalse(vm.state.value.isRunInBackground)
        vm.toggleRunInBackground()
        assertTrue(vm.state.value.isRunInBackground)
    }

    @Test
    fun togglePushNotificationsFlipsValue() = runTest {
        val stub = StubDaemonBridge()
        val repo = makeSettingsRepository(stub, this)
        val vm = AppSettingsViewModel(repo, viewModelScope())
        advanceUntilIdle()
        val before = vm.state.value.isPushNotifications
        vm.togglePushNotifications()
        assertTrue(vm.state.value.isPushNotifications != before)
    }

    @Test
    fun onClearedDoesNotThrow() = runTest {
        val stub = StubDaemonBridge()
        val repo = makeSettingsRepository(stub, this)
        val vm = AppSettingsViewModel(repo, disposableScope())
        vm.onCleared()
    }
}
