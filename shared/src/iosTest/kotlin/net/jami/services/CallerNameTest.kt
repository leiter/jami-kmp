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

import kotlinx.coroutines.test.runTest
import net.jami.model.Account
import net.jami.model.Call
import net.jami.model.Contact
import net.jami.model.Profile
import net.jami.model.Uri
import net.jami.viewmodel.makeAccountService
import net.jami.viewmodel.makeContactService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * CallKit names the caller from the account's contact store, not from the call's own contact,
 * which for an incoming call is a nameless placeholder (the iOS half of e5af1df item 3).
 */
class CallerNameTest {

    private val peer = Uri.fromString("jami:d9f8c7f0bf76aaaa1111222233334444")

    private fun incomingCall(contact: Contact? = null) =
        Call("acc1", "call1", peer, isIncoming = true).apply { this.contact = contact }

    @Test
    fun theAccountStoreWinsOverThePlaceholderOnTheCall() = runTest {
        val account = Account("acc1")
        account.getContactFromCache(peer).loadedProfile = Profile("peace4us", null)
        val contacts = makeContactService(accountService = makeAccountService(scope = backgroundScope), scope = backgroundScope)

        val name = resolveCallerName(account, contacts, incomingCall(contact = Contact(peer)))

        assertEquals("peace4us", name)
    }

    @Test
    fun aUsernameIsUsedWhenThereIsNoDisplayName() = runTest {
        val account = Account("acc1")
        account.getContactFromCache(peer).username = "peace4us"
        val contacts = makeContactService(accountService = makeAccountService(scope = backgroundScope), scope = backgroundScope)

        assertEquals("peace4us", resolveCallerName(account, contacts, incomingCall()))
    }

    @Test
    fun aNamedContactOnTheCallIsUsedWhenTheStoresKnowNothing() = runTest {
        val contacts = makeContactService(accountService = makeAccountService(scope = backgroundScope), scope = backgroundScope)
        val named = Contact(peer).apply { loadedProfile = Profile("From the call", null) }

        assertEquals("From the call", resolveCallerName(Account("acc1"), contacts, incomingCall(named)))
    }

    @Test
    fun nobodyKnowingANameGivesNull() = runTest {
        val contacts = makeContactService(accountService = makeAccountService(scope = backgroundScope), scope = backgroundScope)

        assertNull(resolveCallerName(null, contacts, incomingCall(Contact(peer))))
    }
}
