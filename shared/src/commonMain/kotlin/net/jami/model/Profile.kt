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

/**
 * Represents a user's profile with display name and avatar.
 *
 * Ported from: jami-client-android libjamiclient
 * Changes: RxJava Single → direct values
 */
open class Profile(
    val displayName: String?,
    val avatar: ByteArray?,
    val description: String? = null
) {
    companion object {
        val EMPTY_PROFILE = Profile(null, null)
    }

    /**
     * Merge this profile with another, preferring non-null values from the other profile.
     */
    fun mergeWith(other: Profile): Profile = Profile(
        displayName = other.displayName ?: this.displayName,
        avatar = other.avatar ?: this.avatar,
        description = other.description ?: this.description
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Profile) return false
        return displayName == other.displayName &&
                avatar?.contentEquals(other.avatar) ?: (other.avatar == null) &&
                description == other.description
    }

    override fun hashCode(): Int {
        var result = displayName?.hashCode() ?: 0
        result = 31 * result + (avatar?.contentHashCode() ?: 0)
        result = 31 * result + (description?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String = "Profile(displayName=$displayName, hasAvatar=${avatar != null})"
}

/**
 * View model combining contact information with profile data.
 *
 * Ported from: jami-client-android libjamiclient
 * Changes: RxJava Observable → direct object
 */
data class ContactViewModel(
    val contact: Contact,
    val profile: Profile,
    val registeredName: String? = null,
    val presence: Contact.PresenceStatus = Contact.PresenceStatus.OFFLINE
) {
    val displayUri: String
        get() = registeredName ?: contact.uri.toString()

    val displayName: String
        get() = profile.displayName?.takeIf { it.isNotBlank() } ?: displayUri

    fun matches(query: String): Boolean {
        val lowerQuery = query.lowercase()
        return (profile.displayName != null && profile.displayName.lowercase().contains(lowerQuery)) ||
                (registeredName != null && registeredName.lowercase().contains(lowerQuery)) ||
                contact.uri.toString().lowercase().contains(lowerQuery)
    }

    override fun toString(): String = displayUri

    companion object {
        val EMPTY = ContactViewModel(
            Contact(Uri.fromId("")),
            Profile.EMPTY_PROFILE
        )
    }
}
