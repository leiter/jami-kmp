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
import net.jami.model.AccountConfig
import net.jami.model.ConfigKey
import net.jami.model.Contact
import net.jami.model.Uri
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for ContactService using StubDaemonBridge.
 * Tests actual contact loading, caching, and daemon interactions.
 */
class ContactServiceIntegrationTest {

    private fun makeServices(
        stub: StubDaemonBridge,
        scope: kotlinx.coroutines.test.TestScope
    ): Pair<AccountService, ContactService> {
        val accountService = AccountService(stub, net.jami.services.StubHardwareService(), StubDeviceRuntimeService(), kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()))
        val contactService = ContactService(scope, accountService, stub)
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

    @Test
    fun clearCacheRemovesAllContacts() = runTest {
        val stub = StubDaemonBridge()
        val (accountService, contactService) = makeServices(stub, this)

        // Pre-populate cache
        contactService.getContactFromCache("acc1", Uri.fromId("abc123"))
        contactService.getContactFromCache("acc1", Uri.fromId("def456"))
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
    fun removeContactCallsDaemonBridge() = runTest {
        val stub = StubDaemonBridge()
        val (accountService, contactService) = makeServices(stub, this)

        // Should not crash — calls through to stub
        contactService.removeContact("acc1", Uri.fromId("abc123"), ban = false)
    }
}
