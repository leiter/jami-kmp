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
import net.jami.model.settings.ConversationSort
import net.jami.services.StubDaemonBridge
import net.jami.ui.viewmodel.AppSettingsViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppSettingsViewModelTest {

    private fun makeVm(runTest: kotlinx.coroutines.test.TestScope): AppSettingsViewModel {
        val stub = StubDaemonBridge()
        val repo = makeSettingsRepository(stub, runTest.viewModelScope())
        return AppSettingsViewModel(repo, runTest.viewModelScope())
    }

    // ==================== Initial state ====================

    @Test
    fun initialStateDefaultsAreCorrect() = runTest {
        val vm = makeVm(this)
        advanceUntilIdle()
        // Appearance
        assertFalse(vm.state.value.isDarkTheme)
        assertFalse(vm.state.value.isCompactMode)
        assertEquals(ConversationSort.LAST_ACTIVITY, vm.state.value.conversationSort)
        // Privacy
        assertTrue(vm.state.value.isReadReceipts)
        assertTrue(vm.state.value.isTypingIndicators)
        assertFalse(vm.state.value.isBlockUnknown)
        assertFalse(vm.state.value.isScreenshotBlocking)
        assertTrue(vm.state.value.isLinkPreview)
        // Notifications
        assertTrue(vm.state.value.isPushNotifications)
        assertTrue(vm.state.value.isCallNotifications)
        assertTrue(vm.state.value.isMessageNotifications)
        assertTrue(vm.state.value.isNotificationSound)
        assertTrue(vm.state.value.isVibration)
        assertFalse(vm.state.value.isQuietHours)
        // Calls
        assertTrue(vm.state.value.isVideoEnabled)
        assertTrue(vm.state.value.isHardwareAcceleration)
        assertTrue(vm.state.value.isNoiseSuppression)
        assertTrue(vm.state.value.isEchoCancellation)
        assertFalse(vm.state.value.isAutoAnswer)
        // File Transfers
        assertEquals(30, vm.state.value.maxAutoAcceptMb)
        assertTrue(vm.state.value.isAutoDownloadWifi)
        assertFalse(vm.state.value.isAutoDownloadMobile)
        // System
        assertFalse(vm.state.value.isStartOnBoot)
        assertFalse(vm.state.value.isRunInBackground)
    }

    // ==================== Appearance toggles ====================

    @Test
    fun toggleDarkThemeFlipsValue() = runTest {
        val vm = makeVm(this)
        advanceUntilIdle()
        val before = vm.state.value.isDarkTheme
        vm.toggleDarkTheme()
        assertTrue(vm.state.value.isDarkTheme != before)
    }

    @Test
    fun toggleDarkThemeTwiceRestoresOriginal() = runTest {
        val vm = makeVm(this)
        advanceUntilIdle()
        val before = vm.state.value.isDarkTheme
        vm.toggleDarkTheme()
        vm.toggleDarkTheme()
        assertTrue(vm.state.value.isDarkTheme == before)
    }

    @Test
    fun toggleCompactModeFlipsValue() = runTest {
        val vm = makeVm(this)
        advanceUntilIdle()
        assertFalse(vm.state.value.isCompactMode)
        vm.toggleCompactMode()
        assertTrue(vm.state.value.isCompactMode)
    }

    @Test
    fun setConversationSortUpdatesState() = runTest {
        val vm = makeVm(this)
        advanceUntilIdle()
        vm.setConversationSort(ConversationSort.ALPHABETICAL)
        assertEquals(ConversationSort.ALPHABETICAL, vm.state.value.conversationSort)
        vm.setConversationSort(ConversationSort.UNREAD_FIRST)
        assertEquals(ConversationSort.UNREAD_FIRST, vm.state.value.conversationSort)
        vm.setConversationSort(ConversationSort.LAST_ACTIVITY)
        assertEquals(ConversationSort.LAST_ACTIVITY, vm.state.value.conversationSort)
    }

    // ==================== Privacy toggles ====================

    @Test
    fun toggleReadReceiptsFlipsValue() = runTest {
        val vm = makeVm(this)
        advanceUntilIdle()
        val before = vm.state.value.isReadReceipts
        vm.toggleReadReceipts()
        assertTrue(vm.state.value.isReadReceipts != before)
    }

    @Test
    fun toggleTypingIndicatorsFlipsValue() = runTest {
        val vm = makeVm(this)
        advanceUntilIdle()
        val before = vm.state.value.isTypingIndicators
        vm.toggleTypingIndicators()
        assertTrue(vm.state.value.isTypingIndicators != before)
    }

    @Test
    fun toggleBlockUnknownFlipsValue() = runTest {
        val vm = makeVm(this)
        advanceUntilIdle()
        assertFalse(vm.state.value.isBlockUnknown)
        vm.toggleBlockUnknown()
        assertTrue(vm.state.value.isBlockUnknown)
    }

    @Test
    fun toggleScreenshotBlockingFlipsValue() = runTest {
        val vm = makeVm(this)
        assertFalse(vm.state.value.isScreenshotBlocking)
        vm.toggleScreenshotBlocking()
        assertTrue(vm.state.value.isScreenshotBlocking)
    }

    @Test
    fun toggleLinkPreviewFlipsValue() = runTest {
        val vm = makeVm(this)
        advanceUntilIdle()
        val before = vm.state.value.isLinkPreview
        vm.toggleLinkPreview()
        assertTrue(vm.state.value.isLinkPreview != before)
    }

    // ==================== Notification toggles ====================

    @Test
    fun togglePushNotificationsFlipsValue() = runTest {
        val vm = makeVm(this)
        advanceUntilIdle()
        val before = vm.state.value.isPushNotifications
        vm.togglePushNotifications()
        assertTrue(vm.state.value.isPushNotifications != before)
    }

    @Test
    fun toggleCallNotificationsFlipsValue() = runTest {
        val vm = makeVm(this)
        advanceUntilIdle()
        val before = vm.state.value.isCallNotifications
        vm.toggleCallNotifications()
        assertTrue(vm.state.value.isCallNotifications != before)
    }

    @Test
    fun toggleMessageNotificationsFlipsValue() = runTest {
        val vm = makeVm(this)
        advanceUntilIdle()
        val before = vm.state.value.isMessageNotifications
        vm.toggleMessageNotifications()
        assertTrue(vm.state.value.isMessageNotifications != before)
    }

    @Test
    fun toggleNotificationSoundFlipsValue() = runTest {
        val vm = makeVm(this)
        advanceUntilIdle()
        val before = vm.state.value.isNotificationSound
        vm.toggleNotificationSound()
        assertTrue(vm.state.value.isNotificationSound != before)
    }

    @Test
    fun toggleVibrationFlipsValue() = runTest {
        val vm = makeVm(this)
        advanceUntilIdle()
        val before = vm.state.value.isVibration
        vm.toggleVibration()
        assertTrue(vm.state.value.isVibration != before)
    }

    @Test
    fun toggleQuietHoursFlipsValue() = runTest {
        val vm = makeVm(this)
        advanceUntilIdle()
        assertFalse(vm.state.value.isQuietHours)
        vm.toggleQuietHours()
        assertTrue(vm.state.value.isQuietHours)
    }

    // ==================== Call toggles ====================

    @Test
    fun toggleVideoEnabledFlipsValue() = runTest {
        val vm = makeVm(this)
        advanceUntilIdle()
        val before = vm.state.value.isVideoEnabled
        vm.toggleVideoEnabled()
        assertTrue(vm.state.value.isVideoEnabled != before)
    }

    @Test
    fun toggleHardwareAccelerationFlipsValue() = runTest {
        val vm = makeVm(this)
        advanceUntilIdle()
        val before = vm.state.value.isHardwareAcceleration
        vm.toggleHardwareAcceleration()
        assertTrue(vm.state.value.isHardwareAcceleration != before)
    }

    @Test
    fun toggleNoiseSuppressionFlipsValue() = runTest {
        val vm = makeVm(this)
        advanceUntilIdle()
        val before = vm.state.value.isNoiseSuppression
        vm.toggleNoiseSuppression()
        assertTrue(vm.state.value.isNoiseSuppression != before)
    }

    @Test
    fun toggleEchoCancellationFlipsValue() = runTest {
        val vm = makeVm(this)
        advanceUntilIdle()
        val before = vm.state.value.isEchoCancellation
        vm.toggleEchoCancellation()
        assertTrue(vm.state.value.isEchoCancellation != before)
    }

    @Test
    fun toggleAutoAnswerFlipsValue() = runTest {
        val vm = makeVm(this)
        advanceUntilIdle()
        assertFalse(vm.state.value.isAutoAnswer)
        vm.toggleAutoAnswer()
        assertTrue(vm.state.value.isAutoAnswer)
    }

    // ==================== File transfer ====================

    @Test
    fun setMaxAutoAcceptMbUpdatesState() = runTest {
        val vm = makeVm(this)
        advanceUntilIdle()
        vm.setMaxAutoAcceptMb(50)
        assertEquals(50, vm.state.value.maxAutoAcceptMb)
        vm.setMaxAutoAcceptMb(0)
        assertEquals(0, vm.state.value.maxAutoAcceptMb)
        vm.setMaxAutoAcceptMb(100)
        assertEquals(100, vm.state.value.maxAutoAcceptMb)
    }

    @Test
    fun toggleAutoDownloadWifiFlipsValue() = runTest {
        val vm = makeVm(this)
        advanceUntilIdle()
        val before = vm.state.value.isAutoDownloadWifi
        vm.toggleAutoDownloadWifi()
        assertTrue(vm.state.value.isAutoDownloadWifi != before)
    }

    @Test
    fun toggleAutoDownloadMobileFlipsValue() = runTest {
        val vm = makeVm(this)
        advanceUntilIdle()
        assertFalse(vm.state.value.isAutoDownloadMobile)
        vm.toggleAutoDownloadMobile()
        assertTrue(vm.state.value.isAutoDownloadMobile)
    }

    // ==================== System toggles ====================

    @Test
    fun toggleStartOnBootFlipsValue() = runTest {
        val vm = makeVm(this)
        assertFalse(vm.state.value.isStartOnBoot)
        vm.toggleStartOnBoot()
        assertTrue(vm.state.value.isStartOnBoot)
    }

    @Test
    fun toggleRunInBackgroundFlipsValue() = runTest {
        val vm = makeVm(this)
        assertFalse(vm.state.value.isRunInBackground)
        vm.toggleRunInBackground()
        assertTrue(vm.state.value.isRunInBackground)
    }

    // ==================== Lifecycle ====================

    @Test
    fun onClearedDoesNotThrow() = runTest {
        val stub = StubDaemonBridge()
        val repo = makeSettingsRepository(stub, this)
        val vm = AppSettingsViewModel(repo, disposableScope())
        vm.onCleared()
    }
}
