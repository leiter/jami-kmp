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
package net.jami.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.jami.model.settings.*
import net.jami.services.DaemonBridge
import net.jami.utils.Log

/**
 * Repository for managing user settings stored in daemon account details.
 *
 * Settings are stored as JSON in daemon account details with KMP.* prefix keys.
 * This enables automatic sync across devices via DHT.
 *
 * ## Usage
 * ```kotlin
 * val repo = SettingsRepository(daemonBridge, scope)
 * repo.observeAccount("accountId")
 *
 * // Update settings
 * repo.updateTheme(Theme.DARK)
 * repo.updateReadReceipts(false)
 *
 * // Observe settings
 * repo.uiSettings.collect { settings ->
 *     // React to changes
 * }
 * ```
 */
class SettingsRepository(
    private val daemonBridge: DaemonBridge,
    private val scope: CoroutineScope
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    /** Currently observed account ID */
    private var currentAccountId: String? = null

    // ==================== StateFlows ====================

    private val _meta = MutableStateFlow(KmpMeta())
    /** Metadata about settings schema and sync */
    val meta: StateFlow<KmpMeta> = _meta.asStateFlow()

    private val _uiSettings = MutableStateFlow(UiSettings())
    /** UI settings (theme, font size, language) */
    val uiSettings: StateFlow<UiSettings> = _uiSettings.asStateFlow()

    private val _privacySettings = MutableStateFlow(PrivacySettings())
    /** Privacy settings (read receipts, typing indicators) */
    val privacySettings: StateFlow<PrivacySettings> = _privacySettings.asStateFlow()

    private val _notificationSettings = MutableStateFlow(NotificationSettings())
    /** Notification settings */
    val notificationSettings: StateFlow<NotificationSettings> = _notificationSettings.asStateFlow()

    private val _callSettings = MutableStateFlow(CallSettings())
    /** Call settings (video, auto-answer, hardware acceleration) */
    val callSettings: StateFlow<CallSettings> = _callSettings.asStateFlow()

    private val _fileTransferSettings = MutableStateFlow(FileTransferSettings())
    /** File transfer settings */
    val fileTransferSettings: StateFlow<FileTransferSettings> = _fileTransferSettings.asStateFlow()

    private val _conversationSettings = MutableStateFlow<Map<String, ConversationSettings>>(emptyMap())
    /** Per-conversation settings */
    val conversationSettings: StateFlow<Map<String, ConversationSettings>> = _conversationSettings.asStateFlow()

    // ==================== Account Observation ====================

    /**
     * Start observing settings for the given account.
     * Loads existing settings from daemon account details.
     *
     * @param accountId Account to observe
     */
    fun observeAccount(accountId: String) {
        currentAccountId = accountId
        loadSettings(accountId)
    }

    /**
     * Stop observing the current account.
     * Resets all settings to defaults.
     */
    fun stopObserving() {
        currentAccountId = null
        _meta.value = KmpMeta()
        _uiSettings.value = UiSettings()
        _privacySettings.value = PrivacySettings()
        _notificationSettings.value = NotificationSettings()
        _callSettings.value = CallSettings()
        _fileTransferSettings.value = FileTransferSettings()
        _conversationSettings.value = emptyMap()
    }

    /**
     * Handle account details changed event from daemon.
     * Called by DaemonCallbacksImpl when settings are updated remotely.
     *
     * @param accountId Account that was updated
     * @param details Updated account details
     */
    fun onAccountDetailsChanged(accountId: String, details: Map<String, String>) {
        if (accountId != currentAccountId) return

        // Only reload if KMP settings were changed
        val hasKmpChanges = details.keys.any { SettingsKeys.isKmpKey(it) }
        if (hasKmpChanges) {
            loadSettings(accountId)
        }
    }

    // ==================== UI Settings ====================

    /**
     * Update theme setting.
     */
    fun updateTheme(theme: Theme) {
        val current = _uiSettings.value
        if (current.theme != theme) {
            updateUiSettings(current.copy(theme = theme))
        }
    }

    /**
     * Update font scale.
     */
    fun updateFontScale(scale: Float) {
        val current = _uiSettings.value
        if (current.fontScale != scale) {
            updateUiSettings(current.copy(fontScale = scale))
        }
    }

    /**
     * Update language setting.
     */
    fun updateLanguage(language: String) {
        val current = _uiSettings.value
        if (current.language != language) {
            updateUiSettings(current.copy(language = language))
        }
    }

    /**
     * Update conversation sort order.
     */
    fun updateConversationSort(sort: ConversationSort) {
        val current = _uiSettings.value
        if (current.conversationSort != sort) {
            updateUiSettings(current.copy(conversationSort = sort))
        }
    }

    /**
     * Update compact mode setting.
     */
    fun updateCompactMode(enabled: Boolean) {
        val current = _uiSettings.value
        if (current.compactMode != enabled) {
            updateUiSettings(current.copy(compactMode = enabled))
        }
    }

    private fun updateUiSettings(settings: UiSettings) {
        _uiSettings.value = settings
        saveSettings(SettingsKeys.UI) { json.encodeToString(settings) }
    }

    // ==================== Privacy Settings ====================

    /**
     * Update read receipts setting.
     */
    fun updateReadReceipts(enabled: Boolean) {
        val current = _privacySettings.value
        if (current.readReceipts != enabled) {
            updatePrivacySettings(current.copy(readReceipts = enabled))
        }
    }

    /**
     * Update typing indicators setting.
     */
    fun updateTypingIndicators(enabled: Boolean) {
        val current = _privacySettings.value
        if (current.typingIndicators != enabled) {
            updatePrivacySettings(current.copy(typingIndicators = enabled))
        }
    }

    /**
     * Update block unknown contacts setting.
     */
    fun updateBlockUnknownContacts(enabled: Boolean) {
        val current = _privacySettings.value
        if (current.blockUnknownContacts != enabled) {
            updatePrivacySettings(current.copy(blockUnknownContacts = enabled))
        }
    }

    /**
     * Block a contact.
     */
    fun blockContact(contactUri: String) {
        val current = _privacySettings.value
        if (contactUri !in current.blockedContacts) {
            updatePrivacySettings(current.copy(
                blockedContacts = current.blockedContacts + contactUri
            ))
        }
    }

    /**
     * Unblock a contact.
     */
    fun unblockContact(contactUri: String) {
        val current = _privacySettings.value
        if (contactUri in current.blockedContacts) {
            updatePrivacySettings(current.copy(
                blockedContacts = current.blockedContacts - contactUri
            ))
        }
    }

    /**
     * Update link preview setting.
     */
    fun updateShowLinkPreviews(enabled: Boolean) {
        val current = _privacySettings.value
        if (current.showLinkPreviews != enabled) {
            updatePrivacySettings(current.copy(showLinkPreviews = enabled))
        }
    }

    private fun updatePrivacySettings(settings: PrivacySettings) {
        _privacySettings.value = settings
        saveSettings(SettingsKeys.PRIVACY) { json.encodeToString(settings) }
    }

    // ==================== Notification Settings ====================

    /**
     * Update master notifications toggle.
     */
    fun updateNotificationsEnabled(enabled: Boolean) {
        val current = _notificationSettings.value
        if (current.enabled != enabled) {
            updateNotificationSettings(current.copy(enabled = enabled))
        }
    }

    /**
     * Update call notifications setting.
     */
    fun updateCallNotifications(enabled: Boolean) {
        val current = _notificationSettings.value
        if (current.callNotifications != enabled) {
            updateNotificationSettings(current.copy(callNotifications = enabled))
        }
    }

    /**
     * Update message notifications setting.
     */
    fun updateMessageNotifications(enabled: Boolean) {
        val current = _notificationSettings.value
        if (current.messageNotifications != enabled) {
            updateNotificationSettings(current.copy(messageNotifications = enabled))
        }
    }

    /**
     * Update notification sound setting.
     */
    fun updateNotificationSound(soundUri: String) {
        val current = _notificationSettings.value
        if (current.soundUri != soundUri) {
            updateNotificationSettings(current.copy(soundUri = soundUri))
        }
    }

    /**
     * Update vibration setting.
     */
    fun updateVibration(enabled: Boolean) {
        val current = _notificationSettings.value
        if (current.vibrationEnabled != enabled) {
            updateNotificationSettings(current.copy(vibrationEnabled = enabled))
        }
    }

    /**
     * Update quiet hours settings.
     */
    fun updateQuietHours(enabled: Boolean, startMinutes: Int = 1380, endMinutes: Int = 420) {
        val current = _notificationSettings.value
        updateNotificationSettings(current.copy(
            quietHoursEnabled = enabled,
            quietHoursStart = startMinutes,
            quietHoursEnd = endMinutes
        ))
    }

    private fun updateNotificationSettings(settings: NotificationSettings) {
        _notificationSettings.value = settings
        saveSettings(SettingsKeys.NOTIFICATIONS) { json.encodeToString(settings) }
    }

    // ==================== Call Settings ====================

    /**
     * Update video enabled by default setting.
     */
    fun updateVideoEnabled(enabled: Boolean) {
        val current = _callSettings.value
        if (current.videoEnabled != enabled) {
            updateCallSettings(current.copy(videoEnabled = enabled))
        }
    }

    /**
     * Update auto-answer setting.
     */
    fun updateAutoAnswer(enabled: Boolean, delaySeconds: Int = 0) {
        val current = _callSettings.value
        updateCallSettings(current.copy(
            autoAnswer = enabled,
            autoAnswerDelay = delaySeconds
        ))
    }

    /**
     * Update hardware acceleration setting.
     */
    fun updateHardwareAcceleration(enabled: Boolean) {
        val current = _callSettings.value
        if (current.hardwareAcceleration != enabled) {
            updateCallSettings(current.copy(hardwareAcceleration = enabled))
        }
    }

    /**
     * Update ringtone setting.
     */
    fun updateRingtone(ringtoneUri: String) {
        val current = _callSettings.value
        if (current.ringtone != ringtoneUri) {
            updateCallSettings(current.copy(ringtone = ringtoneUri))
        }
    }

    /**
     * Update noise suppression setting.
     */
    fun updateNoiseSuppression(enabled: Boolean) {
        val current = _callSettings.value
        if (current.noiseSuppression != enabled) {
            updateCallSettings(current.copy(noiseSuppression = enabled))
        }
    }

    private fun updateCallSettings(settings: CallSettings) {
        _callSettings.value = settings
        saveSettings(SettingsKeys.CALLS) { json.encodeToString(settings) }
    }

    // ==================== File Transfer Settings ====================

    /**
     * Update max auto-accept file size.
     */
    fun updateMaxAutoAcceptSize(bytes: Long) {
        val current = _fileTransferSettings.value
        if (current.maxAutoAcceptSize != bytes) {
            updateFileTransferSettings(current.copy(maxAutoAcceptSize = bytes))
        }
    }

    /**
     * Update auto-download settings.
     */
    fun updateAutoDownload(mobile: Boolean, wifi: Boolean) {
        val current = _fileTransferSettings.value
        updateFileTransferSettings(current.copy(
            autoDownloadMobile = mobile,
            autoDownloadWifi = wifi
        ))
    }

    private fun updateFileTransferSettings(settings: FileTransferSettings) {
        _fileTransferSettings.value = settings
        saveSettings(SettingsKeys.FILE_TRANSFER) { json.encodeToString(settings) }
    }

    // ==================== Conversation Settings ====================

    /**
     * Mute a conversation.
     *
     * @param conversationId Conversation to mute
     * @param until Timestamp when mute expires (0 = forever)
     */
    fun muteConversation(conversationId: String, until: Long = 0L) {
        val current = _conversationSettings.value
        val convSettings = current[conversationId] ?: ConversationSettings(conversationId)
        val updated = convSettings.copy(muted = true, muteUntil = until)
        updateConversationSettings(conversationId, updated)
    }

    /**
     * Unmute a conversation.
     */
    fun unmuteConversation(conversationId: String) {
        val current = _conversationSettings.value
        val convSettings = current[conversationId] ?: return
        val updated = convSettings.copy(muted = false, muteUntil = -1)
        updateConversationSettings(conversationId, updated)
    }

    /**
     * Pin a conversation.
     */
    fun pinConversation(conversationId: String, pinned: Boolean = true) {
        val current = _conversationSettings.value
        val convSettings = current[conversationId] ?: ConversationSettings(conversationId)
        val updated = convSettings.copy(pinned = pinned)
        updateConversationSettings(conversationId, updated)
    }

    /**
     * Set custom notification sound for a conversation.
     */
    fun setConversationNotificationSound(conversationId: String, soundUri: String?) {
        val current = _conversationSettings.value
        val convSettings = current[conversationId] ?: ConversationSettings(conversationId)
        val updated = convSettings.copy(customNotificationSound = soundUri)
        updateConversationSettings(conversationId, updated)
    }

    /**
     * Set color tag for a conversation.
     */
    fun setConversationColorTag(conversationId: String, colorTag: String) {
        val current = _conversationSettings.value
        val convSettings = current[conversationId] ?: ConversationSettings(conversationId)
        val updated = convSettings.copy(colorTag = colorTag)
        updateConversationSettings(conversationId, updated)
    }

    /**
     * Get settings for a specific conversation.
     */
    fun getConversationSettings(conversationId: String): ConversationSettings {
        return _conversationSettings.value[conversationId] ?: ConversationSettings(conversationId)
    }

    /**
     * Check if a conversation is muted.
     */
    fun isConversationMuted(conversationId: String): Boolean {
        val settings = _conversationSettings.value[conversationId] ?: return false
        if (!settings.muted) return false
        if (settings.muteUntil == 0L) return true // Forever
        return settings.muteUntil > currentTimeMillis()
    }

    private fun updateConversationSettings(conversationId: String, settings: ConversationSettings) {
        val current = _conversationSettings.value.toMutableMap()
        current[conversationId] = settings
        _conversationSettings.value = current
        saveSettings(SettingsKeys.CONVERSATIONS) { json.encodeToString(current) }
    }

    // ==================== All Settings ====================

    /**
     * Get all settings as a combined object.
     */
    fun getAllSettings(): AllSettings {
        return AllSettings(
            meta = _meta.value,
            ui = _uiSettings.value,
            privacy = _privacySettings.value,
            notifications = _notificationSettings.value,
            calls = _callSettings.value,
            fileTransfer = _fileTransferSettings.value,
            conversations = _conversationSettings.value
        )
    }

    /**
     * Import all settings from a combined object.
     * Useful for migration or restore.
     */
    fun importAllSettings(settings: AllSettings) {
        val accountId = currentAccountId ?: return

        _meta.value = settings.meta
        _uiSettings.value = settings.ui
        _privacySettings.value = settings.privacy
        _notificationSettings.value = settings.notifications
        _callSettings.value = settings.calls
        _fileTransferSettings.value = settings.fileTransfer
        _conversationSettings.value = settings.conversations

        // Save all to daemon
        scope.launch {
            try {
                val details = mutableMapOf(
                    SettingsKeys.META to json.encodeToString(settings.meta),
                    SettingsKeys.UI to json.encodeToString(settings.ui),
                    SettingsKeys.PRIVACY to json.encodeToString(settings.privacy),
                    SettingsKeys.NOTIFICATIONS to json.encodeToString(settings.notifications),
                    SettingsKeys.CALLS to json.encodeToString(settings.calls),
                    SettingsKeys.FILE_TRANSFER to json.encodeToString(settings.fileTransfer),
                    SettingsKeys.CONVERSATIONS to json.encodeToString(settings.conversations)
                )

                // Get existing account details and merge
                val existingDetails = daemonBridge.getAccountDetails(accountId).toMutableMap()
                existingDetails.putAll(details)
                daemonBridge.setAccountDetails(accountId, existingDetails)

                Log.d(TAG, "Imported all settings for account $accountId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to import settings: ${e.message}")
            }
        }
    }

    // ==================== Private Helpers ====================

    private fun loadSettings(accountId: String) {
        scope.launch {
            try {
                val details = daemonBridge.getAccountDetails(accountId)

                // Load each settings group
                details[SettingsKeys.META]?.let { jsonStr ->
                    _meta.value = runCatching { json.decodeFromString<KmpMeta>(jsonStr) }
                        .getOrDefault(KmpMeta())
                }

                details[SettingsKeys.UI]?.let { jsonStr ->
                    _uiSettings.value = runCatching { json.decodeFromString<UiSettings>(jsonStr) }
                        .getOrDefault(UiSettings())
                }

                details[SettingsKeys.PRIVACY]?.let { jsonStr ->
                    _privacySettings.value = runCatching { json.decodeFromString<PrivacySettings>(jsonStr) }
                        .getOrDefault(PrivacySettings())
                }

                details[SettingsKeys.NOTIFICATIONS]?.let { jsonStr ->
                    _notificationSettings.value = runCatching { json.decodeFromString<NotificationSettings>(jsonStr) }
                        .getOrDefault(NotificationSettings())
                }

                details[SettingsKeys.CALLS]?.let { jsonStr ->
                    _callSettings.value = runCatching { json.decodeFromString<CallSettings>(jsonStr) }
                        .getOrDefault(CallSettings())
                }

                details[SettingsKeys.FILE_TRANSFER]?.let { jsonStr ->
                    _fileTransferSettings.value = runCatching { json.decodeFromString<FileTransferSettings>(jsonStr) }
                        .getOrDefault(FileTransferSettings())
                }

                details[SettingsKeys.CONVERSATIONS]?.let { jsonStr ->
                    _conversationSettings.value = runCatching {
                        json.decodeFromString<Map<String, ConversationSettings>>(jsonStr)
                    }.getOrDefault(emptyMap())
                }

                Log.d(TAG, "Loaded settings for account $accountId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load settings for account $accountId: ${e.message}")
            }
        }
    }

    private fun saveSettings(key: String, encode: () -> String) {
        val accountId = currentAccountId ?: return

        scope.launch {
            try {
                val jsonStr = encode()
                val details = daemonBridge.getAccountDetails(accountId).toMutableMap()
                details[key] = jsonStr

                // Update meta timestamp
                val updatedMeta = _meta.value.copy(
                    lastUpdated = currentTimeMillis(),
                    lastUpdatedBy = getDeviceId()
                )
                _meta.value = updatedMeta
                details[SettingsKeys.META] = json.encodeToString(updatedMeta)

                daemonBridge.setAccountDetails(accountId, details)
                Log.d(TAG, "Saved settings [$key] for account $accountId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save settings [$key]: ${e.message}")
            }
        }
    }

    private fun currentTimeMillis(): Long {
        return net.jami.utils.currentTimeMillis()
    }

    private fun getDeviceId(): String {
        // This would ideally come from DeviceRuntimeService
        return "kmp-device"
    }

    companion object {
        private const val TAG = "SettingsRepository"
    }
}
