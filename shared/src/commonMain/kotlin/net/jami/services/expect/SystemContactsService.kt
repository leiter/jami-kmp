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
package net.jami.services.expect

/**
 * A system (platform address book) contact matched against a Jami identifier.
 */
data class SystemContactMatch(
    /** Platform-specific system contact id (e.g. Android ContactsContract.Contacts._ID). */
    val id: Long,
    /** Stable re-query key (e.g. Android's LOOKUP_KEY), if the platform has one. */
    val lookupKey: String?,
    val displayName: String,
    /** Platform-specific photo reference (e.g. Android PHOTO_ID); 0 if none. */
    val photoId: Long,
    /** Additional phone/SIP numbers found on the matched system contact. */
    val phoneNumbers: List<SystemContactNumber> = emptyList(),
)

data class SystemContactNumber(
    val number: String,
    val category: Int,
    val label: String?,
    val isSip: Boolean,
)

/**
 * Looks up platform address-book contacts that reference a Jami identity, so the app can
 * enrich a Jami [net.jami.model.Contact] with the display name/photo/phone numbers a user
 * already has stored for that person.
 *
 * Ported from jami-client-android's ContactServiceImpl.findContactBySipNumberFromSystem() /
 * findContactByNumberFromSystem() — read-only enrichment, not a bidirectional sync adapter
 * (the reference app itself has no SyncAdapter; writing Jami contacts back into the system
 * address book is a separate, still-unported feature).
 *
 * Android: queries ContactsContract. Other platforms: no-op stub (no address-book
 * integration yet), matching this codebase's existing per-platform-effort precedent.
 */
expect class SystemContactsService {
    /** True if the platform currently grants read access to system contacts. */
    fun hasPermission(): Boolean

    /**
     * Find a system contact whose stored SIP/IM address or phone number matches [query]
     * (a Jami [net.jami.model.Contact]'s raw URI string — hex ID, username, or number).
     * Returns null if there's no permission or no match.
     */
    suspend fun findSystemContact(query: String): SystemContactMatch?
}
