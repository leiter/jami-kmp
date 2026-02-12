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
package net.jami.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Represents a contact in Jami.
 *
 * Ported from: jami-client-android libjamiclient
 * Note: This is a simplified version. Full port in Task #2.
 */
class Contact(
    val uri: Uri,
    val isUser: Boolean = false
) {
    var username: String? = null
    var displayName: String? = null

    private val _presenceStatus = MutableStateFlow(PresenceStatus.OFFLINE)
    val presenceStatus: StateFlow<PresenceStatus> = _presenceStatus.asStateFlow()

    var isOnline: Boolean
        get() = _presenceStatus.value == PresenceStatus.ONLINE
        set(value) {
            _presenceStatus.value = if (value) PresenceStatus.ONLINE else PresenceStatus.OFFLINE
        }

    val primaryNumber: String
        get() = uri.rawRingId

    val displayUri: String
        get() = uri.toString()

    val displayUsername: String
        get() = displayName?.takeIf { it.isNotBlank() }
            ?: username?.takeIf { it.isNotBlank() }
            ?: uri.rawRingId

    val isJami: Boolean
        get() = uri.isJami

    enum class PresenceStatus {
        OFFLINE,
        ONLINE,
        AWAY,
        BUSY
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Contact) return false
        return uri == other.uri
    }

    override fun hashCode(): Int = uri.hashCode()

    override fun toString(): String = "Contact($displayUsername)"

    companion object {
        fun fromUri(uri: Uri): Contact = Contact(uri)
        fun fromString(uriString: String): Contact = Contact(Uri.fromString(uriString))
    }
}
