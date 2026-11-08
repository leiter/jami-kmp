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

import net.jami.model.Uri

/**
 * The predicate behind the "Block unknown contacts" privacy setting
 * (`PrivacySettings.blockUnknownContacts`, labelled "Only accept messages from your contacts").
 *
 * Asks the daemon rather than a client-side cache. `ContactService`'s cache is the obvious
 * alternative, but `getContactFromCache` creates a placeholder `Contact` for any URI the app
 * merely *sees* — including the stranger now calling — so a cache hit does not mean "contact",
 * and a cold cache would make a real contact look like a stranger. The daemon owns the contact
 * list; `getContacts` reads it directly and returns a handful of maps, which is cheap enough
 * for one call per incoming call or request.
 *
 * Both `confirmed` and `banned` are checked: a banned peer is still *known* to the daemon and
 * is listed with `banned=true`, and must never count as an accepted contact.
 *
 * @return true when [uri] is a confirmed, unbanned contact of [accountId], and the peer should
 *   therefore be let through whatever the setting says.
 */
internal fun DaemonBridgeApi.isConfirmedContact(accountId: String, uri: Uri): Boolean {
    val wanted = uri.rawRingId
    if (wanted.isEmpty()) return false
    return getContacts(accountId).any { contact ->
        // "id", not "uri": the daemon keys a *contact* map by "id" and only a conversation
        // *member* map by "uri". Reading "uri" here always found null, so every contact fell
        // through the elvis below and the function could never return true — which turned
        // "block unknown contacts" into "block everyone", refusing calls from confirmed
        // contacts with SIP 603 and hiding their conversation requests. "uri" is still
        // accepted so a daemon that supplies it is not rejected.
        val listed = (contact["id"] ?: contact["uri"])
            ?.let { Uri.fromString(it).rawRingId } ?: return@any false
        listed.equals(wanted, ignoreCase = true) &&
            contact["confirmed"]?.toBoolean() == true &&
            contact["banned"]?.toBoolean() != true
    }
}
