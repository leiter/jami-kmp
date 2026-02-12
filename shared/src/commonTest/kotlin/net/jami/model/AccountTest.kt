package net.jami.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountTest {

    @Test
    fun testJamiAccountTypeDetection() {
        val account = Account(
            accountId = "test123",
            details = mutableMapOf(
                ConfigKey.ACCOUNT_TYPE.key to AccountConfig.ACCOUNT_TYPE_JAMI
            )
        )

        assertTrue(account.isJami)
        assertFalse(account.isSip)
    }

    @Test
    fun testSipAccountTypeDetection() {
        val account = Account(
            accountId = "test456",
            details = mutableMapOf(
                ConfigKey.ACCOUNT_TYPE.key to AccountConfig.ACCOUNT_TYPE_SIP
            )
        )

        assertFalse(account.isJami)
        assertTrue(account.isSip)
    }

    @Test
    fun testRegistrationState() {
        val account = Account(
            accountId = "test789",
            volatileDetails = mutableMapOf(
                ConfigKey.ACCOUNT_REGISTRATION_STATUS.key to "REGISTERED"
            )
        )

        assertEquals(Account.RegistrationState.REGISTERED, account.registrationState)
        assertTrue(account.isRegistered)
    }

    @Test
    fun testDisplayName() {
        val account = Account(
            accountId = "testABC",
            details = mutableMapOf(
                ConfigKey.ACCOUNT_DISPLAYNAME.key to "John Doe",
                ConfigKey.ACCOUNT_ALIAS.key to "john"
            )
        )

        assertEquals("John Doe", account.displayName)
        assertEquals("john", account.alias)
        assertEquals("john", account.displayUsername)
    }

    @Test
    fun testDisplayUsernameFallback() {
        val accountWithAlias = Account(
            accountId = "test1",
            details = mutableMapOf(
                ConfigKey.ACCOUNT_ALIAS.key to "myalias",
                ConfigKey.ACCOUNT_USERNAME.key to "myusername"
            )
        )
        assertEquals("myalias", accountWithAlias.displayUsername)

        val accountWithUsername = Account(
            accountId = "test2",
            details = mutableMapOf(
                ConfigKey.ACCOUNT_USERNAME.key to "myusername"
            )
        )
        assertEquals("myusername", accountWithUsername.displayUsername)

        val accountWithId = Account(accountId = "test3")
        assertEquals("test3", accountWithId.displayUsername)
    }

    @Test
    fun testAccountCredentials() {
        val credentials = AccountCredentials(
            username = "user@example.com",
            password = "secret123",
            realm = "sip.example.com"
        )

        val map = credentials.toMap()
        assertEquals("user@example.com", map[ConfigKey.ACCOUNT_USERNAME.key])
        assertEquals("secret123", map[ConfigKey.ACCOUNT_PASSWORD.key])
        assertEquals("sip.example.com", map[ConfigKey.ACCOUNT_REALM.key])

        val restored = AccountCredentials.fromMap(map)
        assertEquals(credentials, restored)
    }
}
