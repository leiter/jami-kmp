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

import net.jami.testHardwareService

import kotlinx.coroutines.test.runTest
import net.jami.model.AccountConfig
import net.jami.model.ConfigKey
import net.jami.model.Contact
import net.jami.model.Uri
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.jami.services.VCardService

/**
 * Integration tests for ContactService using StubDaemonBridge.
 * Tests actual contact loading, caching, and daemon interactions.
 */
class ContactServiceIntegrationTest {

    /**
     * A scope sharing the test scheduler but with its own Job.
     *
     * These previously used CoroutineScope(SupervisorJob()), which runs on real dispatchers:
     * advanceUntilIdle() does not wait for it, so assertions raced the service's coroutines
     * and failed intermittently depending on timing and test order.
     */
    private fun kotlinx.coroutines.test.TestScope.testServiceScope(): kotlinx.coroutines.CoroutineScope =
        kotlinx.coroutines.CoroutineScope(coroutineContext + kotlinx.coroutines.SupervisorJob())

    private fun makeServices(
        stub: StubDaemonBridge,
        scope: kotlinx.coroutines.test.TestScope
    ): Pair<AccountService, ContactService> {
        val accountService = AccountService(stub, testHardwareService(), StubDeviceRuntimeService(), scope.testServiceScope())
        val contactService = ContactService(scope, accountService, stub, VCardService(StubDeviceRuntimeService()))
        return accountService to contactService
    }

    @Test
    fun loadContactsFromStubPopulatesCache() = runTest {
        val stub = StubDaemonBridge()
        stub.accountIds = listOf("acc1")
        stub.accountDetails["acc1"] = mapOf(ConfigKey.ACCOUNT_TYPE.key to AccountConfig.ACCOUNT_TYPE_JAMI)
        stub.contacts["acc1"] = listOf(
            mapOf("uri" to "abc123", "confirmed" to "true"),
            mapOf("uri" to "def456", "confirmed" to "false")
        )
        val (accountService, contactService) = makeServices(stub, this)
        accountService.loadAccounts()

        contactService.loadContacts("acc1")

        val contacts = contactService.getCachedContacts("acc1")
        assertEquals(2, contacts.size)
    }

    @Test
    fun getContactFromCacheCreatesIfMissing() = runTest {
        val stub = StubDaemonBridge()
        val (accountService, contactService) = makeServices(stub, this)

        val uri = Uri.fromId("abc123")
        val contact = contactService.getContactFromCache("acc1", uri)

        assertNotNull(contact)
        assertEquals(uri, contact.uri)
    }

    @Test
    fun findContactInCacheReturnsNullIfMissing() = runTest {
        val stub = StubDaemonBridge()
        val (accountService, contactService) = makeServices(stub, this)

        val result = contactService.findContactInCache("acc1", Uri.fromId("nonexistent"))
        assertNull(result)
    }

    private fun stubWithTwoContacts() = StubDaemonBridge().apply {
        accountIds = listOf("acc1")
        accountDetails["acc1"] = mapOf(ConfigKey.ACCOUNT_TYPE.key to AccountConfig.ACCOUNT_TYPE_JAMI)
        contacts["acc1"] = listOf(
            mapOf("uri" to "abc123", "confirmed" to "true"),
            mapOf("uri" to "def456", "confirmed" to "true", "banned" to "true"),
        )
    }

    // One Contact per peer (2026-11-25): ContactService hands out the account's objects, so a name
    // or profile written through either path reaches every screen.
    @Test
    fun contactServiceAndAccountShareOneContactPerPeer() = runTest {
        val stub = stubWithTwoContacts()
        val (accountService, contactService) = makeServices(stub, this)
        accountService.loadAccounts()
        contactService.loadContacts("acc1")
        val account = accountService.getAccount("acc1")!!
        val uri = Uri.fromId("abc123")

        assertTrue(contactService.findContact("acc1", uri) === account.getContactFromCache(uri))
        assertTrue(contactService.findContactInCache("acc1", uri) === account.getContactFromCache(uri))
        // A peer first seen by the account (a call, a group member) is the same object too.
        val stranger = Uri.fromId("0badc0de")
        assertTrue(account.getContactFromCache(stranger) === contactService.getContactFromCache("acc1", stranger))
        // The daemon's blocked state reaches the object conversations hold.
        assertTrue(account.getContactFromCache(Uri.fromId("def456")).isBlocked)
    }

    @Test
    fun peersTheAppOnlyAskedAboutAreNotContacts() = runTest {
        val stub = stubWithTwoContacts()
        val (accountService, contactService) = makeServices(stub, this)
        accountService.loadAccounts()
        contactService.loadContacts("acc1")
        // A call peer or group member gets a Contact in the account's cache, but is not on the
        // daemon's contact list, so it must not show in Contacts or Blocked.
        contactService.findContact("acc1", Uri.fromId("0badc0de"))
        accountService.getAccount("acc1")!!.getContactFromCache(Uri.fromId("feedface"))

        val listed = contactService.getCachedContacts("acc1").map { it.uri.rawRingId }.toSet()
        assertEquals(setOf("abc123", "def456"), listed)
    }

    @Test
    fun contactSignalsKeepTheListCurrent() = runTest {
        val stub = stubWithTwoContacts()
        val (accountService, contactService) = makeServices(stub, this)
        accountService.loadAccounts()
        contactService.loadContacts("acc1")
        val account = accountService.getAccount("acc1")!!

        contactService.onContactAdded("acc1", "cafe0001", confirmed = true)
        contactService.onContactRemoved("acc1", "abc123", banned = false)
        contactService.onContactRemoved("acc1", "cafe0001", banned = true)

        val byId = contactService.getCachedContacts("acc1").associateBy { it.uri.rawRingId }
        assertEquals(setOf("def456", "cafe0001"), byId.keys)
        assertEquals(Contact.Status.BLOCKED, byId.getValue("cafe0001").status)
        // The removed peer's object survives for whoever holds it, no longer a contact.
        assertEquals(Contact.Status.NO_REQUEST, account.getContactFromCache(Uri.fromId("abc123")).status)
    }

    @Test
    fun clearCacheRemovesAllContacts() = runTest {
        val stub = stubWithTwoContacts()
        val (accountService, contactService) = makeServices(stub, this)
        accountService.loadAccounts()
        contactService.loadContacts("acc1")
        assertEquals(2, contactService.getCachedContacts("acc1").size)

        contactService.clearCache("acc1")
        assertTrue(contactService.getCachedContacts("acc1").isEmpty())
    }

    @Test
    fun addContactCallsDaemonBridge() = runTest {
        val stub = StubDaemonBridge()
        val (accountService, contactService) = makeServices(stub, this)

        // Should not crash — calls through to stub
        contactService.addContact("acc1", Uri.fromId("abc123"))
    }

    @Test
    fun addingAContactSendsTheInvitation() = runTest {
        // addContact alone only records the peer locally; the invitation is sendTrustRequest.
        // Without it neither phone ever saw the other's request (TestFlight build 40).
        val stub = StubDaemonBridge()
        val (_, contactService) = makeServices(stub, this)

        contactService.addContact("acc1", Uri.fromId("abc123"))

        assertEquals(listOf("acc1" to "abc123"), stub.addedContacts)
        assertEquals(listOf("acc1" to "abc123"), stub.sentTrustRequests.map { it.first to it.second })
    }

    @Test
    fun aContactWhoAlreadyAcceptedIsNotInvitedAgain() = runTest {
        val stub = StubDaemonBridge()
        stub.contactDetails["acc1:abc123"] = mapOf("id" to "abc123", "confirmed" to "true")
        val (accountService, _) = makeServices(stub, this)

        accountService.addContact("acc1", "jami:abc123")

        assertEquals(listOf("acc1" to "jami:abc123"), stub.addedContacts)
        assertTrue(stub.sentTrustRequests.isEmpty())
    }

    @Test
    fun removeContactCallsDaemonBridge() = runTest {
        val stub = StubDaemonBridge()
        val (accountService, contactService) = makeServices(stub, this)

        // Should not crash — calls through to stub
        contactService.removeContact("acc1", Uri.fromId("abc123"), ban = false)
    }
}
