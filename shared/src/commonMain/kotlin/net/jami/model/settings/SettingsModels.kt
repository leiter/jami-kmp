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
package net.jami.model.settings

import kotlinx.serialization.Serializable

/**
 * Metadata for KMP settings stored in daemon account details.
 * Used for schema versioning and migrations.
 */
@Serializable
data class KmpMeta(
    /** Schema version for migration support */
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    /** Timestamp of last settings update (epoch millis) */
    val lastUpdated: Long = 0L,
    /** Device ID that last updated settings (for conflict detection) */
    val lastUpdatedBy: String = ""
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

/**
 * UI-related settings that sync across devices.
 */
@Serializable
data class UiSettings(
    /** Current theme: SYSTEM, LIGHT, or DARK */
    val theme: Theme = Theme.SYSTEM,
    /** Font scale multiplier (1.0 = default) */
    val fontScale: Float = 1.0f,
    /** Language code (empty = system default) */
    val language: String = "",
    /** Show contact avatars in conversation list */
    val showAvatars: Boolean = true,
    /** Use compact conversation list layout */
    val compactMode: Boolean = false,
    /** Sort conversations by: LAST_ACTIVITY, ALPHABETICAL */
    val conversationSort: ConversationSort = ConversationSort.LAST_ACTIVITY
)

/**
 * Theme options.
 */
@Serializable
enum class Theme {
    SYSTEM,
    LIGHT,
    DARK
}

/**
 * Conversation sorting options.
 */
@Serializable
enum class ConversationSort {
    LAST_ACTIVITY,
    ALPHABETICAL,
    UNREAD_FIRST
}

/**
 * Privacy-related settings that sync across devices.
 */
@Serializable
data class PrivacySettings(
    /** Send read receipts when messages are read */
    val readReceipts: Boolean = true,
    /** Show typing indicators */
    val typingIndicators: Boolean = true,
    /** Block messages from unknown contacts */
    val blockUnknownContacts: Boolean = false,
    /** List of blocked contact URIs */
    val blockedContacts: List<String> = emptyList(),
    /** Allow contact requests */
    val allowContactRequests: Boolean = true,
    /** Show link previews in messages */
    val showLinkPreviews: Boolean = true
)

/**
 * Notification settings that sync across devices.
 */
@Serializable
data class NotificationSettings(
    /** Master switch for all notifications */
    val enabled: Boolean = true,
    /** Show call notifications */
    val callNotifications: Boolean = true,
    /** Show message notifications */
    val messageNotifications: Boolean = true,
    /** Show contact request notifications */
    val contactRequestNotifications: Boolean = true,
    /** Enable notification sound */
    val soundEnabled: Boolean = true,
    /** Custom notification sound URI (empty = default) */
    val soundUri: String = "",
    /** Enable vibration */
    val vibrationEnabled: Boolean = true,
    /** LED notification color (0 = default) */
    val ledColor: Int = 0,
    /** Quiet hours enabled */
    val quietHoursEnabled: Boolean = false,
    /** Quiet hours start time (minutes from midnight, e.g., 1380 = 23:00) */
    val quietHoursStart: Int = 1380, // 23:00
    /** Quiet hours end time (minutes from midnight, e.g., 420 = 07:00) */
    val quietHoursEnd: Int = 420 // 07:00
)

/**
 * Call-related settings that sync across devices.
 */
@Serializable
data class CallSettings(
    /** Enable video by default for outgoing calls */
    val videoEnabled: Boolean = true,
    /** Automatically answer incoming calls */
    val autoAnswer: Boolean = false,
    /** Auto-answer delay in seconds (0 = immediate) */
    val autoAnswerDelay: Int = 0,
    /** Enable hardware video acceleration */
    val hardwareAcceleration: Boolean = true,
    /** Custom ringtone URI (empty = default) */
    val ringtone: String = "",
    /** Enable proximity sensor during calls */
    val proximityEnabled: Boolean = true,
    /** Enable noise suppression */
    val noiseSuppression: Boolean = true,
    /** Enable echo cancellation */
    val echoCancellation: Boolean = true
)

/**
 * File transfer settings.
 */
@Serializable
data class FileTransferSettings(
    /** Maximum file size to auto-accept (bytes, 0 = never auto-accept) */
    val maxAutoAcceptSize: Long = 20 * 1024 * 1024, // 20MB
    /** Auto-download files on mobile data */
    val autoDownloadMobile: Boolean = false,
    /** Auto-download files on WiFi */
    val autoDownloadWifi: Boolean = true,
    /** Download directory path (empty = default) */
    val downloadPath: String = ""
)

/**
 * Per-conversation settings.
 */
@Serializable
data class ConversationSettings(
    /** Conversation ID (URI) */
    val conversationId: String,
    /** Whether conversation is muted */
    val muted: Boolean = false,
    /** Mute expiration timestamp (0 = forever, -1 = not muted) */
    val muteUntil: Long = -1,
    /** Whether conversation is pinned */
    val pinned: Boolean = false,
    /** Custom notification settings for this conversation (null = use global) */
    val customNotificationSound: String? = null,
    /** Whether to show in conversation list */
    val visible: Boolean = true,
    /** Color tag for organization (empty = none) */
    val colorTag: String = ""
)

/**
 * Message draft with metadata.
 */
@Serializable
data class Draft(
    /** Draft text content */
    val text: String = "",
    /** Reply-to message ID (empty = not replying) */
    val replyTo: String = "",
    /** Timestamp when draft was last modified */
    val lastModified: Long = 0L,
    /** Pending attachments (file paths or URIs) */
    val attachments: List<String> = emptyList()
)

/**
 * Container for all message drafts.
 * Key: conversationId (URI)
 */
@Serializable
data class DraftsContainer(
    /** Map of conversation ID to draft */
    val drafts: Map<String, Draft> = emptyMap(),
    /** Timestamp of last sync */
    val lastSynced: Long = 0L
)

/**
 * Combined settings object for full sync.
 */
@Serializable
data class AllSettings(
    val meta: KmpMeta = KmpMeta(),
    val ui: UiSettings = UiSettings(),
    val privacy: PrivacySettings = PrivacySettings(),
    val notifications: NotificationSettings = NotificationSettings(),
    val calls: CallSettings = CallSettings(),
    val fileTransfer: FileTransferSettings = FileTransferSettings(),
    /** Per-conversation settings, keyed by conversation ID */
    val conversations: Map<String, ConversationSettings> = emptyMap()
)

/**
 * Constants for daemon account detail keys.
 * These keys are used to store settings as JSON in daemon account details.
 * Settings are automatically synced across devices via DHT.
 */
object SettingsKeys {
    /** Prefix for all KMP settings keys */
    const val PREFIX = "KMP."

    /** Schema metadata */
    const val META = "${PREFIX}Meta"

    /** UI settings */
    const val UI = "${PREFIX}Settings.UI"

    /** Privacy settings */
    const val PRIVACY = "${PREFIX}Settings.Privacy"

    /** Notification settings */
    const val NOTIFICATIONS = "${PREFIX}Settings.Notifications"

    /** Call settings */
    const val CALLS = "${PREFIX}Settings.Calls"

    /** File transfer settings */
    const val FILE_TRANSFER = "${PREFIX}Settings.FileTransfer"

    /** Per-conversation settings */
    const val CONVERSATIONS = "${PREFIX}Settings.Conversations"

    /** Message drafts */
    const val DRAFTS = "${PREFIX}Drafts"

    /** Check if a key is a KMP settings key */
    fun isKmpKey(key: String): Boolean = key.startsWith(PREFIX)
}
