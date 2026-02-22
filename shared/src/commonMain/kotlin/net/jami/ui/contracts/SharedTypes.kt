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
package net.jami.ui.contracts

import androidx.compose.runtime.Immutable
import net.jami.model.Contact

/**
 * Shared UI model types used across multiple screens and contracts.
 * These are @Immutable versions of types previously embedded in ViewModel files.
 */

@Immutable
data class ConversationItem(
    val id: String,
    val displayName: String,
    val lastMessage: String,
    val timestamp: Long,
    val unreadCount: Int,
    val avatarUri: String?,
    val isOnline: Boolean,
)

enum class MessageType {
    Text,
    System,
    Call,
    Transfer,
}

@Immutable
data class MessageItem(
    val id: String,
    val text: String,
    val author: String,
    val timestamp: Long,
    val isOutgoing: Boolean,
    val type: MessageType = MessageType.Text,
)

@Immutable
data class ContactItem(
    val uri: String,
    val displayName: String,
    val username: String,
    val presenceStatus: Contact.PresenceStatus,
    val avatarUri: String?,
)

@Immutable
data class DeviceItem(
    val deviceId: String,
    val deviceName: String,
    val isCurrent: Boolean,
)
