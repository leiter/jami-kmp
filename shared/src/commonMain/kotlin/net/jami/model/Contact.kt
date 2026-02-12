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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine

/**
 * Represents a contact in Jami.
 *
 * Ported from: jami-client-android libjamiclient
 * Changes: RxJava → Kotlin Flow, java.util.Date → Long timestamp
 */
class Contact(
    val uri: Uri,
    val isUser: Boolean = false
) {
    /**
     * Contact relationship status.
     */
    enum class Status {
        BLOCKED,
        REQUEST_SENT,
        CONFIRMED,
        NO_REQUEST
    }

    /**
     * Presence status of a contact.
     */
    enum class PresenceStatus {
        OFFLINE,
        AVAILABLE,
        CONNECTED
    }

    // Username (registered name)
    private val _username = MutableStateFlow<String?>(null)
    val usernameFlow: StateFlow<String?> = _username.asStateFlow()
    var username: String?
        get() = _username.value
        set(value) { _username.value = value }

    // Presence updates
    private val _presenceStatus = MutableStateFlow(PresenceStatus.OFFLINE)
    val presenceStatus: StateFlow<PresenceStatus> = _presenceStatus.asStateFlow()

    // Profile management
    private val _loadedProfile = MutableStateFlow(Profile.EMPTY_PROFILE)
    private val _customProfile = MutableStateFlow(Profile.EMPTY_PROFILE)

    /**
     * Combined profile merging loaded and custom profiles.
     * Custom profile values override loaded profile values.
     */
    val profileFlow: Flow<Profile> = combine(_loadedProfile, _customProfile) { loaded, custom ->
        mergeProfile(loaded, custom)
    }

    var loadedProfile: Profile
        get() = _loadedProfile.value
        set(value) { _loadedProfile.value = value }

    var customProfile: Profile
        get() = _customProfile.value
        set(value) { _customProfile.value = value }

    // System contact info
    var photoId: Long = 0
        private set

    val phones = mutableListOf<Phone>()

    var isStared: Boolean = false
        private set

    var isFromSystem: Boolean = false

    var status: Status = Status.NO_REQUEST

    /** Timestamp when contact was added (milliseconds since epoch) */
    var addedDate: Long? = null

    /** System contact ID */
    var id: Long = 0

    private var lookupKey: String? = null

    // Conversation URI (can change for swarm conversations)
    private val _conversationUri = MutableStateFlow(uri)
    val conversationUri: StateFlow<Uri> = _conversationUri.asStateFlow()

    // Contact update notifications
    private val _contactUpdates = MutableSharedFlow<Contact>(replay = 1)
    val updates: SharedFlow<Contact> = _contactUpdates.asSharedFlow()

    // ==================== Public Methods ====================

    fun setConversationUri(conversationUri: Uri) {
        _conversationUri.value = conversationUri
    }

    fun setPresence(present: PresenceStatus) {
        _presenceStatus.value = present
    }

    fun setSystemId(id: Long) {
        this.id = id
    }

    fun setSystemContactInfo(id: Long, key: String?, displayName: String, photoId: Long) {
        this.id = id
        this.lookupKey = key
        this.loadedProfile = Profile(displayName, null)
        this.photoId = photoId
        if (username == null && (displayName.startsWith(Uri.RING_URI_SCHEME) ||
                    displayName.startsWith(Uri.JAMI_URI_SCHEME))) {
            username = displayName
        }
    }

    fun setStared() {
        isStared = true
    }

    fun addPhoneNumber(tel: Uri, category: Int, label: String?) {
        if (!hasNumber(tel)) {
            phones.add(Phone(tel, category, label))
        }
    }

    fun addNumber(tel: String, category: Int, label: String?, type: Phone.NumberType) {
        val phoneUri = Uri.fromString(tel)
        if (!hasNumber(phoneUri)) {
            phones.add(Phone(phoneUri, category, label, type))
        }
    }

    fun addNumber(tel: Uri, category: Int, label: String?, type: Phone.NumberType) {
        if (!hasNumber(tel)) {
            phones.add(Phone(tel, category, label, type))
        }
    }

    /**
     * Notify observers that this contact has been updated.
     */
    suspend fun notifyUpdate() {
        _contactUpdates.emit(this)
    }

    // ==================== Computed Properties ====================

    val primaryNumber: String
        get() = uri.rawRingId

    val isBlocked: Boolean
        get() = status == Status.BLOCKED

    val isOnline: Boolean
        get() = _presenceStatus.value != PresenceStatus.OFFLINE

    val isJami: Boolean
        get() = uri.isJami

    val displayName: String?
        get() = _loadedProfile.value.displayName ?: _customProfile.value.displayName

    val displayUri: String
        get() = uri.toString()

    val displayUsername: String
        get() = displayName?.takeIf { it.isNotBlank() }
            ?: username?.takeIf { it.isNotBlank() }
            ?: uri.rawRingId

    // ==================== Private Methods ====================

    private fun hasNumber(number: Uri?): Boolean {
        if (number == null || number.isEmpty) return false
        return phones.any { it.number.toString() == number.toString() }
    }

    private fun mergeProfile(primary: Profile, custom: Profile): Profile = Profile(
        displayName = custom.displayName ?: primary.displayName,
        avatar = custom.avatar ?: primary.avatar,
        description = custom.description ?: primary.description
    )

    // ==================== Object Methods ====================

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Contact) return false
        return uri == other.uri
    }

    override fun hashCode(): Int = uri.hashCode()

    override fun toString(): String = uri.rawUriString

    companion object {
        const val UNKNOWN_ID = -1L
        const val DEFAULT_ID = 0L

        fun buildSIP(to: Uri): Contact = Contact(to).apply { username = "" }

        fun build(uri: String, isUser: Boolean = false): Contact =
            Contact(Uri.fromString(uri), isUser)

        fun fromUri(uri: Uri): Contact = Contact(uri)

        fun fromString(uriString: String): Contact = Contact(Uri.fromString(uriString))
    }
}
