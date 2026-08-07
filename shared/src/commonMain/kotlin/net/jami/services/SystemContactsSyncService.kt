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
package net.jami.services

import net.jami.model.Phone
import net.jami.services.expect.SystemContactsService
import net.jami.utils.Log

/**
 * Enriches Jami contacts with data from the platform's system address book (display name,
 * photo, phone numbers), when the user has opted into "sync system contacts".
 *
 * Read-only, on-demand lookup — ported from jami-client-android's
 * ContactServiceImpl.findContactBySipNumberFromSystem() / findContactByNumberFromSystem().
 * Not a bidirectional sync adapter: this does not write Jami contacts into the system address
 * book (the reference app doesn't either — it does that as a separate, explicit user action).
 */
class SystemContactsSyncService(
    private val contactService: ContactService,
    private val systemContactsService: SystemContactsService,
) {
    /**
     * For every cached Jami contact on [accountId], look up a matching system contact by its
     * raw Jami/SIP identifier and merge its display name/photo/phone numbers into the Contact.
     * No-op if the platform has no system-contacts permission (or no implementation).
     */
    suspend fun syncForAccount(accountId: String) {
        if (!systemContactsService.hasPermission()) return
        val contacts = contactService.getCachedContacts(accountId)
        for (contact in contacts) {
            val match = systemContactsService.findSystemContact(contact.uri.rawRingId) ?: continue
            contact.setSystemContactInfo(match.id, match.lookupKey, match.displayName, match.photoId)
            for (number in match.phoneNumbers) {
                contact.addNumber(
                    number.number,
                    number.category,
                    number.label,
                    if (number.isSip) Phone.NumberType.SIP else Phone.NumberType.TEL
                )
            }
            Log.d(TAG, "syncForAccount: matched system contact for ${contact.uri.rawRingId} -> ${match.displayName}")
        }
    }

    companion object {
        private const val TAG = "SystemContactsSyncService"
    }
}
