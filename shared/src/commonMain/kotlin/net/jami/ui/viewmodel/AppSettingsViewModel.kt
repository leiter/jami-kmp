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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.jami.model.settings.ConnectivityMode
import net.jami.model.settings.ConversationSort
import net.jami.model.settings.NotificationVisibility
import net.jami.model.settings.Theme
import net.jami.repository.SettingsRepository
import net.jami.services.AccountService
import net.jami.services.ContactService
import net.jami.ui.platform.LocalPrefKeys
import net.jami.ui.platform.LocalPrefs

/**
 * State for the application settings screen.
 */
data class AppSettingsState(
    // --- Appearance ---
    val isDarkTheme: Boolean = false,
    val isCompactMode: Boolean = false,
    val conversationSort: ConversationSort = ConversationSort.LAST_ACTIVITY,

    // --- Privacy ---
    val isReadReceipts: Boolean = true,
    val isTypingIndicators: Boolean = true,
    val isBlockUnknown: Boolean = false,
    val isScreenshotBlocking: Boolean = false,
    val isLinkPreview: Boolean = true,

    // --- Notifications ---
    val isPushNotifications: Boolean = true,
    val isCallNotifications: Boolean = true,
    val isMessageNotifications: Boolean = true,
    val isNotificationSound: Boolean = true,
    val isVibration: Boolean = true,
    val isQuietHours: Boolean = false,

    // --- Calls ---
    val isVideoEnabled: Boolean = true,
    val isHardwareAcceleration: Boolean = true,
    val isNoiseSuppression: Boolean = true,
    val isEchoCancellation: Boolean = true,
    val isAutoAnswer: Boolean = false,
    /** Custom ringtone URI; empty string means system default. */
    val ringtone: String = "",

    // --- File Transfers ---
    /** Max auto-accept size in MB (stored as bytes in repository). */
    val maxAutoAcceptMb: Int = 30,
    val isAutoDownloadWifi: Boolean = true,
    val isAutoDownloadMobile: Boolean = false,

    // --- System ---
    val isStartOnBoot: Boolean = false,
    val isRunInBackground: Boolean = false,
    val isPlaceSystemCalls: Boolean = false,
    val isSystemContactsSync: Boolean = false,

    // --- Connectivity ---
    val connectivityMode: ConnectivityMode = ConnectivityMode.LOCAL_NODE,

    // --- Notification Visibility ---
    val notificationVisibility: Int = NotificationVisibility.PRIVATE,

    // --- Video Quality ---
    val videoBitrate: Int = 0,
    val videoResolution: Int = 480,
)

/**
 * ViewModel for the application-wide settings screen.
 *
 * Settings are loaded from three sources:
 * 1. [SettingsRepository] flows — daemon-synced settings (theme, privacy, notifications, calls,
 *    file transfers). These sync across devices via DHT.
 * 2. [LocalPrefs] — device-local settings that must NOT sync (screenshot blocking, boot behavior).
 */
class AppSettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val accountService: AccountService,
    private val contactService: ContactService,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val scope = scope

    private val _state = MutableStateFlow(AppSettingsState())
    val state: StateFlow<AppSettingsState> = _state.asStateFlow()

    init {
        // Load device-local settings once at startup (not reactive — these never change remotely)
        _state.update { it.copy(
            isScreenshotBlocking = LocalPrefs.getBoolean(LocalPrefKeys.SCREENSHOT_BLOCKING, false),
            isStartOnBoot = LocalPrefs.getBoolean(LocalPrefKeys.START_ON_BOOT, true),
            isRunInBackground = LocalPrefs.getBoolean(LocalPrefKeys.RUN_IN_BACKGROUND, false),
            isPlaceSystemCalls = LocalPrefs.getBoolean(LocalPrefKeys.PLACE_SYSTEM_CALLS, false),
            isSystemContactsSync = LocalPrefs.getBoolean(LocalPrefKeys.SYSTEM_CONTACTS_SYNC, false),
            connectivityMode = ConnectivityMode.entries.getOrElse(
                LocalPrefs.getInt(LocalPrefKeys.CONNECTIVITY_MODE, ConnectivityMode.LOCAL_NODE.ordinal)
            ) { ConnectivityMode.LOCAL_NODE },
            notificationVisibility = LocalPrefs.getInt(LocalPrefKeys.NOTIFICATION_VISIBILITY, NotificationVisibility.PRIVATE),
            videoBitrate = LocalPrefs.getInt(LocalPrefKeys.VIDEO_BITRATE, 0),
            videoResolution = LocalPrefs.getInt(LocalPrefKeys.VIDEO_RESOLUTION, 480),
        )}

        // Observe daemon-synced settings reactively
        scope.launch {
            settingsRepository.uiSettings.collect { ui ->
                _state.update { it.copy(
                    isDarkTheme = ui.theme == Theme.DARK,
                    isCompactMode = ui.compactMode,
                    conversationSort = ui.conversationSort,
                )}
            }
        }
        scope.launch {
            settingsRepository.privacySettings.collect { p ->
                _state.update { it.copy(
                    isReadReceipts = p.readReceipts,
                    isTypingIndicators = p.typingIndicators,
                    isBlockUnknown = p.blockUnknownContacts,
                    isLinkPreview = p.showLinkPreviews,
                )}
            }
        }
        scope.launch {
            settingsRepository.notificationSettings.collect { n ->
                _state.update { it.copy(
                    isPushNotifications = n.enabled,
                    isCallNotifications = n.callNotifications,
                    isMessageNotifications = n.messageNotifications,
                    isNotificationSound = n.soundEnabled,
                    isVibration = n.vibrationEnabled,
                    isQuietHours = n.quietHoursEnabled,
                )}
            }
        }
        scope.launch {
            settingsRepository.callSettings.collect { c ->
                _state.update { it.copy(
                    isVideoEnabled = c.videoEnabled,
                    isHardwareAcceleration = c.hardwareAcceleration,
                    isNoiseSuppression = c.noiseSuppression,
                    isEchoCancellation = c.echoCancellation,
                    isAutoAnswer = c.autoAnswer,
                    ringtone = c.ringtone,
                )}
            }
        }
        scope.launch {
            settingsRepository.fileTransferSettings.collect { f ->
                _state.update { it.copy(
                    maxAutoAcceptMb = (f.maxAutoAcceptSize / (1024L * 1024L)).toInt().coerceIn(0, 100),
                    isAutoDownloadWifi = f.autoDownloadWifi,
                    isAutoDownloadMobile = f.autoDownloadMobile,
                )}
            }
        }
    }

    // ==================== Appearance ====================

    fun toggleDarkTheme() {
        val new = !_state.value.isDarkTheme
        _state.update { it.copy(isDarkTheme = new) }
        settingsRepository.updateTheme(if (new) Theme.DARK else Theme.LIGHT)
    }

    fun toggleCompactMode() {
        val new = !_state.value.isCompactMode
        _state.update { it.copy(isCompactMode = new) }
        settingsRepository.updateCompactMode(new)
    }

    fun setConversationSort(sort: ConversationSort) {
        _state.update { it.copy(conversationSort = sort) }
        settingsRepository.updateConversationSort(sort)
    }

    // ==================== Privacy ====================

    fun toggleReadReceipts() {
        val new = !_state.value.isReadReceipts
        _state.update { it.copy(isReadReceipts = new) }
        settingsRepository.updateReadReceipts(new)
    }

    fun toggleTypingIndicators() {
        val new = !_state.value.isTypingIndicators
        _state.update { it.copy(isTypingIndicators = new) }
        settingsRepository.updateTypingIndicators(new)
    }

    fun toggleBlockUnknown() {
        val new = !_state.value.isBlockUnknown
        _state.update { it.copy(isBlockUnknown = new) }
        settingsRepository.updateBlockUnknownContacts(new)
    }

    fun toggleScreenshotBlocking() {
        val new = !_state.value.isScreenshotBlocking
        _state.update { it.copy(isScreenshotBlocking = new) }
        LocalPrefs.setBoolean(LocalPrefKeys.SCREENSHOT_BLOCKING, new)
    }

    fun toggleLinkPreview() {
        val new = !_state.value.isLinkPreview
        _state.update { it.copy(isLinkPreview = new) }
        settingsRepository.updateShowLinkPreviews(new)
    }

    // ==================== Notifications ====================

    fun togglePushNotifications() {
        val new = !_state.value.isPushNotifications
        _state.update { it.copy(isPushNotifications = new) }
        settingsRepository.updateNotificationsEnabled(new)
    }

    fun toggleCallNotifications() {
        val new = !_state.value.isCallNotifications
        _state.update { it.copy(isCallNotifications = new) }
        settingsRepository.updateCallNotifications(new)
    }

    fun toggleMessageNotifications() {
        val new = !_state.value.isMessageNotifications
        _state.update { it.copy(isMessageNotifications = new) }
        settingsRepository.updateMessageNotifications(new)
    }

    fun toggleNotificationSound() {
        val new = !_state.value.isNotificationSound
        _state.update { it.copy(isNotificationSound = new) }
        settingsRepository.updateNotificationSoundEnabled(new)
    }

    fun toggleVibration() {
        val new = !_state.value.isVibration
        _state.update { it.copy(isVibration = new) }
        settingsRepository.updateVibration(new)
    }

    fun toggleQuietHours() {
        val new = !_state.value.isQuietHours
        _state.update { it.copy(isQuietHours = new) }
        settingsRepository.updateQuietHours(new)
    }

    // ==================== Calls ====================

    fun toggleVideoEnabled() {
        val new = !_state.value.isVideoEnabled
        _state.update { it.copy(isVideoEnabled = new) }
        settingsRepository.updateVideoEnabled(new)
    }

    fun toggleHardwareAcceleration() {
        val new = !_state.value.isHardwareAcceleration
        _state.update { it.copy(isHardwareAcceleration = new) }
        settingsRepository.updateHardwareAcceleration(new)
    }

    fun toggleNoiseSuppression() {
        val new = !_state.value.isNoiseSuppression
        _state.update { it.copy(isNoiseSuppression = new) }
        settingsRepository.updateNoiseSuppression(new)
    }

    fun toggleEchoCancellation() {
        val new = !_state.value.isEchoCancellation
        _state.update { it.copy(isEchoCancellation = new) }
        settingsRepository.updateEchoCancellation(new)
    }

    fun toggleAutoAnswer() {
        val new = !_state.value.isAutoAnswer
        _state.update { it.copy(isAutoAnswer = new) }
        settingsRepository.updateAutoAnswer(new)
    }

    fun updateRingtone(uri: String) {
        settingsRepository.updateRingtone(uri)
    }

    // ==================== File Transfers ====================

    fun setMaxAutoAcceptMb(mb: Int) {
        _state.update { it.copy(maxAutoAcceptMb = mb) }
        settingsRepository.updateMaxAutoAcceptSize(mb.toLong() * 1024L * 1024L)
    }

    fun toggleAutoDownloadWifi() {
        val new = !_state.value.isAutoDownloadWifi
        _state.update { it.copy(isAutoDownloadWifi = new) }
        settingsRepository.updateAutoDownload(_state.value.isAutoDownloadMobile, new)
    }

    fun toggleAutoDownloadMobile() {
        val new = !_state.value.isAutoDownloadMobile
        _state.update { it.copy(isAutoDownloadMobile = new) }
        settingsRepository.updateAutoDownload(new, _state.value.isAutoDownloadWifi)
    }

    // ==================== System ====================

    fun toggleStartOnBoot() {
        val new = !_state.value.isStartOnBoot
        _state.update { it.copy(isStartOnBoot = new) }
        LocalPrefs.setBoolean(LocalPrefKeys.START_ON_BOOT, new)
    }

    fun toggleRunInBackground() {
        val new = !_state.value.isRunInBackground
        _state.update { it.copy(isRunInBackground = new) }
        LocalPrefs.setBoolean(LocalPrefKeys.RUN_IN_BACKGROUND, new)
    }

    fun togglePlaceSystemCalls() {
        val new = !_state.value.isPlaceSystemCalls
        _state.update { it.copy(isPlaceSystemCalls = new) }
        LocalPrefs.setBoolean(LocalPrefKeys.PLACE_SYSTEM_CALLS, new)
    }

    fun toggleSystemContactsSync() {
        val new = !_state.value.isSystemContactsSync
        _state.update { it.copy(isSystemContactsSync = new) }
        LocalPrefs.setBoolean(LocalPrefKeys.SYSTEM_CONTACTS_SYNC, new)
        if (new) {
            scope.launch {
                val accountId = accountService.currentAccount.value?.accountId ?: return@launch
                contactService.loadContacts(accountId)
            }
        }
    }

    // ==================== Connectivity ====================

    fun setConnectivityMode(mode: ConnectivityMode) {
        _state.update { it.copy(connectivityMode = mode) }
        LocalPrefs.setInt(LocalPrefKeys.CONNECTIVITY_MODE, mode.ordinal)
    }

    // ==================== Notification Visibility ====================

    fun setNotificationVisibility(visibility: Int) {
        _state.update { it.copy(notificationVisibility = visibility) }
        LocalPrefs.setInt(LocalPrefKeys.NOTIFICATION_VISIBILITY, visibility)
    }

    // ==================== Video Quality ====================

    fun setVideoBitrate(bitrate: Int) {
        _state.update { it.copy(videoBitrate = bitrate) }
        LocalPrefs.setInt(LocalPrefKeys.VIDEO_BITRATE, bitrate)
    }

    fun setVideoResolution(resolution: Int) {
        _state.update { it.copy(videoResolution = resolution) }
        LocalPrefs.setInt(LocalPrefKeys.VIDEO_RESOLUTION, resolution)
    }

    /**
     * Cancel the coroutine scope when this ViewModel is no longer needed.
     */
    fun onCleared() {
        scope.cancel()
    }
}
