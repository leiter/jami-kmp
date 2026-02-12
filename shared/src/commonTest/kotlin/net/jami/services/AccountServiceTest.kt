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

import net.jami.model.Account
import net.jami.model.AccountConfig
import net.jami.model.ConfigKey
import net.jami.model.Conversation
import net.jami.model.TrustRequest
import net.jami.model.Uri
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AccountServiceTest {

    // ==================== AccountEvent Tests ====================

    @Test
    fun testAccountEventAccountsChanged() {
        val event = AccountEvent.AccountsChanged
        assertNotNull(event)
    }

    @Test
    fun testAccountEventRegistrationStateChanged() {
        val event = AccountEvent.RegistrationStateChanged(
            accountId = "acc123",
            state = "REGISTERED",
            code = 200,
            detail = "OK"
        )
        assertEquals("acc123", event.accountId)
        assertEquals("REGISTERED", event.state)
        assertEquals(200, event.code)
        assertEquals("OK", event.detail)
    }

    @Test
    fun testAccountEventDetailsChanged() {
        val details = mapOf("key1" to "value1", "key2" to "value2")
        val event = AccountEvent.DetailsChanged("acc123", details)
        assertEquals("acc123", event.accountId)
        assertEquals(2, event.details.size)
        assertEquals("value1", event.details["key1"])
    }

    @Test
    fun testAccountEventVolatileDetailsChanged() {
        val details = mapOf("status" to "REGISTERED")
        val event = AccountEvent.VolatileDetailsChanged("acc123", details)
        assertEquals("acc123", event.accountId)
        assertEquals("REGISTERED", event.details["status"])
    }

    @Test
    fun testAccountEventKnownDevicesChanged() {
        val devices = mapOf("device1" to "My Phone", "device2" to "My Laptop")
        val event = AccountEvent.KnownDevicesChanged("acc123", devices)
        assertEquals("acc123", event.accountId)
        assertEquals(2, event.devices.size)
        assertEquals("My Phone", event.devices["device1"])
    }

    @Test
    fun testAccountEventDeviceRevocationEnded() {
        val event = AccountEvent.DeviceRevocationEnded("acc123", "device123", 0)
        assertEquals("acc123", event.accountId)
        assertEquals("device123", event.deviceId)
        assertEquals(0, event.state)
    }

    @Test
    fun testAccountEventMigrationEnded() {
        val event = AccountEvent.MigrationEnded("acc123", "COMPLETED")
        assertEquals("acc123", event.accountId)
        assertEquals("COMPLETED", event.state)
    }

    @Test
    fun testAccountEventNameRegistrationEnded() {
        val event = AccountEvent.NameRegistrationEnded("acc123", 0, "myusername")
        assertEquals("acc123", event.accountId)
        assertEquals(0, event.state)
        assertEquals("myusername", event.name)
    }

    @Test
    fun testAccountEventRegisteredNameFound() {
        val event = AccountEvent.RegisteredNameFound(
            accountId = "acc123",
            state = 0,
            address = "abc123def",
            name = "testuser"
        )
        assertEquals("acc123", event.accountId)
        assertEquals(0, event.state)
        assertEquals("abc123def", event.address)
        assertEquals("testuser", event.name)
    }

    @Test
    fun testAccountEventUserSearchEnded() {
        val results = listOf(
            mapOf("username" to "user1", "uri" to "abc123"),
            mapOf("username" to "user2", "uri" to "def456")
        )
        val event = AccountEvent.UserSearchEnded("acc123", 0, "test", results)
        assertEquals("acc123", event.accountId)
        assertEquals(0, event.state)
        assertEquals("test", event.query)
        assertEquals(2, event.results.size)
    }

    @Test
    fun testAccountEventContactAdded() {
        val event = AccountEvent.ContactAdded("acc123", "contact456", confirmed = true)
        assertEquals("acc123", event.accountId)
        assertEquals("contact456", event.uri)
        assertTrue(event.confirmed)
    }

    @Test
    fun testAccountEventContactRemoved() {
        val event = AccountEvent.ContactRemoved("acc123", "contact456", banned = true)
        assertEquals("acc123", event.accountId)
        assertEquals("contact456", event.uri)
        assertTrue(event.banned)
    }

    @Test
    fun testAccountEventIncomingTrustRequest() {
        val request = TrustRequest(
            accountId = "acc123",
            from = Uri.fromId("abc123"),
            timestamp = 1234567890L,
            conversationUri = Uri.fromString("swarm:conv123"),
            mode = Conversation.Mode.OneToOne
        )
        val event = AccountEvent.IncomingTrustRequest("acc123", request)
        assertEquals("acc123", event.accountId)
        assertEquals(request, event.request)
    }

    @Test
    fun testAccountEventProfileReceived() {
        val event = AccountEvent.ProfileReceived("acc123", "Test User", "photo_base64_data")
        assertEquals("acc123", event.accountId)
        assertEquals("Test User", event.name)
        assertEquals("photo_base64_data", event.photo)
    }

    // ==================== IncomingMessage Tests ====================

    @Test
    fun testIncomingMessage() {
        val messages = mapOf("text/plain" to "Hello, World!")
        val msg = IncomingMessage(
            accountId = "acc123",
            messageId = "msg456",
            callId = null,
            from = "peer456",
            messages = messages
        )
        assertEquals("acc123", msg.accountId)
        assertEquals("msg456", msg.messageId)
        assertEquals(null, msg.callId)
        assertEquals("peer456", msg.from)
        assertEquals("Hello, World!", msg.messages["text/plain"])
    }

    @Test
    fun testIncomingMessageWithCallId() {
        val messages = mapOf("text/plain" to "In-call message")
        val msg = IncomingMessage(
            accountId = "acc123",
            messageId = "msg789",
            callId = "call123",
            from = "peer456",
            messages = messages
        )
        assertEquals("call123", msg.callId)
    }

    // ==================== Account Registration State Tests ====================

    @Test
    fun testRegistrationStateFromString() {
        assertEquals(Account.RegistrationState.REGISTERED, Account.RegistrationState.fromString("REGISTERED"))
        assertEquals(Account.RegistrationState.TRYING, Account.RegistrationState.fromString("TRYING"))
        assertEquals(Account.RegistrationState.ERROR_AUTH, Account.RegistrationState.fromString("ERROR_AUTH"))
        assertEquals(Account.RegistrationState.UNREGISTERED, Account.RegistrationState.fromString("UNKNOWN_STATE"))
    }

    @Test
    fun testAccountIsRegisteredTrue() {
        val account = Account(
            accountId = "test",
            volatileDetails = mutableMapOf(
                ConfigKey.ACCOUNT_REGISTRATION_STATUS.key to "REGISTERED"
            )
        )
        assertTrue(account.isRegistered)
    }

    @Test
    fun testAccountIsRegisteredFalse() {
        val account = Account(
            accountId = "test",
            volatileDetails = mutableMapOf(
                ConfigKey.ACCOUNT_REGISTRATION_STATUS.key to "UNREGISTERED"
            )
        )
        assertFalse(account.isRegistered)
    }

    @Test
    fun testAccountRegistrationTrying() {
        val account = Account(
            accountId = "test",
            volatileDetails = mutableMapOf(
                ConfigKey.ACCOUNT_REGISTRATION_STATUS.key to "TRYING"
            )
        )
        assertEquals(Account.RegistrationState.TRYING, account.registrationState)
        assertFalse(account.isRegistered)
    }

    @Test
    fun testAccountRegistrationError() {
        val account = Account(
            accountId = "test",
            volatileDetails = mutableMapOf(
                ConfigKey.ACCOUNT_REGISTRATION_STATUS.key to "ERROR_AUTH"
            )
        )
        assertEquals(Account.RegistrationState.ERROR_AUTH, account.registrationState)
        assertFalse(account.isRegistered)
    }

    @Test
    fun testAccountRegistrationErrorNetwork() {
        val account = Account(
            accountId = "test",
            volatileDetails = mutableMapOf(
                ConfigKey.ACCOUNT_REGISTRATION_STATUS.key to "ERROR_NETWORK"
            )
        )
        assertEquals(Account.RegistrationState.ERROR_NETWORK, account.registrationState)
    }

    @Test
    fun testAccountRegistrationStateInitializing() {
        val account = Account(
            accountId = "test",
            volatileDetails = mutableMapOf(
                ConfigKey.ACCOUNT_REGISTRATION_STATUS.key to "INITIALIZING"
            )
        )
        assertEquals(Account.RegistrationState.INITIALIZING, account.registrationState)
    }

    // ==================== Account Type Detection Tests ====================

    @Test
    fun testAccountIsJami() {
        val jamiAccount = createTestAccount("jami1", AccountConfig.ACCOUNT_TYPE_JAMI)
        assertTrue(jamiAccount.isJami)
        assertFalse(jamiAccount.isSip)
    }

    @Test
    fun testAccountIsSip() {
        val sipAccount = createTestAccount("sip1", AccountConfig.ACCOUNT_TYPE_SIP)
        assertFalse(sipAccount.isJami)
        assertTrue(sipAccount.isSip)
    }

    // ==================== Account Properties Tests ====================

    @Test
    fun testAccountEnabled() {
        val enabledAccount = Account(
            accountId = "test",
            details = mutableMapOf(ConfigKey.ACCOUNT_ENABLE.key to "true")
        )
        assertTrue(enabledAccount.isEnabled)

        val disabledAccount = Account(
            accountId = "test",
            details = mutableMapOf(ConfigKey.ACCOUNT_ENABLE.key to "false")
        )
        assertFalse(disabledAccount.isEnabled)
    }

    @Test
    fun testAccountEnabledDefault() {
        // By default, accounts should be enabled
        val account = Account(accountId = "test")
        assertTrue(account.isEnabled)
    }

    @Test
    fun testAccountDisplayName() {
        val account = Account(
            accountId = "test",
            details = mutableMapOf(ConfigKey.ACCOUNT_DISPLAYNAME.key to "Test User")
        )
        assertEquals("Test User", account.displayName)
    }

    @Test
    fun testAccountAlias() {
        val account = Account(
            accountId = "test",
            details = mutableMapOf(ConfigKey.ACCOUNT_ALIAS.key to "myalias")
        )
        assertEquals("myalias", account.alias)
    }

    @Test
    fun testAccountUsername() {
        val account = Account(
            accountId = "test",
            details = mutableMapOf(ConfigKey.ACCOUNT_USERNAME.key to "abc123def")
        )
        assertEquals("abc123def", account.username)
    }

    @Test
    fun testAccountDisplayUsername() {
        // Prefers alias over username
        val accountWithAlias = Account(
            accountId = "test1",
            details = mutableMapOf(
                ConfigKey.ACCOUNT_ALIAS.key to "myalias",
                ConfigKey.ACCOUNT_USERNAME.key to "myusername"
            )
        )
        assertEquals("myalias", accountWithAlias.displayUsername)

        // Falls back to username
        val accountWithUsername = Account(
            accountId = "test2",
            details = mutableMapOf(ConfigKey.ACCOUNT_USERNAME.key to "myusername")
        )
        assertEquals("myusername", accountWithUsername.displayUsername)

        // Falls back to accountId
        val accountWithId = Account(accountId = "test3")
        assertEquals("test3", accountWithId.displayUsername)
    }

    @Test
    fun testAccountHost() {
        val account = Account(
            accountId = "test",
            details = mutableMapOf(ConfigKey.ACCOUNT_HOSTNAME.key to "sip.example.com")
        )
        assertEquals("sip.example.com", account.host)
    }

    @Test
    fun testAccountRegisteredName() {
        val account = Account(
            accountId = "test",
            volatileDetails = mutableMapOf(ConfigKey.ACCOUNT_REGISTERED_NAME.key to "testuser")
        )
        assertEquals("testuser", account.registeredName)
        assertTrue(account.hasRegisteredName)
    }

    @Test
    fun testAccountNoRegisteredName() {
        val account = Account(accountId = "test")
        assertEquals("", account.registeredName)
        assertFalse(account.hasRegisteredName)
    }

    // ==================== Account DHT Proxy Tests ====================

    @Test
    fun testAccountDhtProxyEnabled() {
        val account = Account(
            accountId = "test",
            details = mutableMapOf(ConfigKey.ACCOUNT_PROXY_ENABLED.key to "true")
        )
        assertTrue(account.isDhtProxyEnabled)
    }

    @Test
    fun testAccountDhtProxyDisabled() {
        val account = Account(
            accountId = "test",
            details = mutableMapOf(ConfigKey.ACCOUNT_PROXY_ENABLED.key to "false")
        )
        assertFalse(account.isDhtProxyEnabled)
    }

    @Test
    fun testAccountDhtProxyDefault() {
        val account = Account(accountId = "test")
        assertFalse(account.isDhtProxyEnabled)
    }

    @Test
    fun testAccountDhtProxySetEnabled() {
        val account = Account(accountId = "test")
        assertFalse(account.isDhtProxyEnabled)

        account.isDhtProxyEnabled = true
        assertTrue(account.isDhtProxyEnabled)

        account.isDhtProxyEnabled = false
        assertFalse(account.isDhtProxyEnabled)
    }

    // ==================== LookupState Tests ====================

    @Test
    fun testLookupStateValues() {
        assertEquals(0, LookupState.Success.value)
        assertEquals(1, LookupState.Invalid.value)
        assertEquals(2, LookupState.NotFound.value)
        assertEquals(3, LookupState.NetworkError.value)
    }

    // ==================== Helper Methods ====================

    private fun createTestAccount(
        accountId: String,
        accountType: String = AccountConfig.ACCOUNT_TYPE_JAMI
    ): Account {
        return Account(
            accountId = accountId,
            details = mutableMapOf(ConfigKey.ACCOUNT_TYPE.key to accountType)
        )
    }
}
