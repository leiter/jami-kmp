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
package net.jami.ui.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.jami.model.settings.Theme
import net.jami.repository.SettingsRepository

/**
 * State for the application settings screen.
 */
data class AppSettingsState(
    val isDarkTheme: Boolean = false,
    val isTypingIndicators: Boolean = true,
    val isLinkPreview: Boolean = true,
    val isScreenshotBlocking: Boolean = false,
    val isStartOnBoot: Boolean = false,
    val isRunInBackground: Boolean = false,
    val isPushNotifications: Boolean = true
)

/**
 * ViewModel for the application-wide settings screen.
 *
 * Manages global preferences such as theme, typing indicators, link
 * previews, and notification settings. Settings are persisted via the
 * SettingsRepository which stores them in daemon account details for
 * cross-device synchronization.
 */
class AppSettingsViewModel(
    private val settingsRepository: SettingsRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(AppSettingsState())
    val state: StateFlow<AppSettingsState> = _state.asStateFlow()

    init {
        // Load initial settings from repository
        scope.launch {
            settingsRepository.uiSettings.collect { uiSettings ->
                _state.value = _state.value.copy(
                    isDarkTheme = uiSettings.theme == Theme.DARK
                )
            }
        }
        scope.launch {
            settingsRepository.privacySettings.collect { privacySettings ->
                _state.value = _state.value.copy(
                    isTypingIndicators = privacySettings.typingIndicators,
                    isLinkPreview = privacySettings.showLinkPreviews
                )
            }
        }
        scope.launch {
            settingsRepository.notificationSettings.collect { notifSettings ->
                _state.value = _state.value.copy(
                    isPushNotifications = notifSettings.enabled
                )
            }
        }
    }

    /**
     * Toggle the dark theme setting.
     */
    fun toggleDarkTheme() {
        val newValue = !_state.value.isDarkTheme
        _state.value = _state.value.copy(isDarkTheme = newValue)
        settingsRepository.updateTheme(if (newValue) Theme.DARK else Theme.LIGHT)
    }

    /**
     * Toggle the typing indicators setting.
     */
    fun toggleTypingIndicators() {
        val newValue = !_state.value.isTypingIndicators
        _state.value = _state.value.copy(isTypingIndicators = newValue)
        settingsRepository.updateTypingIndicators(newValue)
    }

    /**
     * Toggle the link preview setting.
     */
    fun toggleLinkPreview() {
        val newValue = !_state.value.isLinkPreview
        _state.value = _state.value.copy(isLinkPreview = newValue)
        settingsRepository.updateShowLinkPreviews(newValue)
    }

    /**
     * Toggle the screenshot blocking setting.
     * This is a local-only setting (not synced via daemon).
     */
    fun toggleScreenshotBlocking() {
        val newValue = !_state.value.isScreenshotBlocking
        _state.value = _state.value.copy(isScreenshotBlocking = newValue)
    }

    /**
     * Toggle the start-on-boot setting.
     * This is a local-only setting (platform-specific behavior).
     */
    fun toggleStartOnBoot() {
        val newValue = !_state.value.isStartOnBoot
        _state.value = _state.value.copy(isStartOnBoot = newValue)
    }

    /**
     * Toggle the run-in-background setting.
     * This is a local-only setting (platform-specific behavior).
     */
    fun toggleRunInBackground() {
        val newValue = !_state.value.isRunInBackground
        _state.value = _state.value.copy(isRunInBackground = newValue)
    }

    /**
     * Toggle the push notifications setting.
     */
    fun togglePushNotifications() {
        val newValue = !_state.value.isPushNotifications
        _state.value = _state.value.copy(isPushNotifications = newValue)
        settingsRepository.updateNotificationsEnabled(newValue)
    }

    /**
     * Cancel the coroutine scope when this ViewModel is no longer needed.
     */
    fun onCleared() {
        scope.cancel()
    }
}
