package net.jami.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
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

    @Test
    fun newSwarmIfAbsentCreatesOnlyOnce() {
        val account = Account(accountId = "acc")
        val created = account.newSwarmIfAbsent("conv1", Conversation.Mode.Syncing)
        assertEquals(Conversation.Mode.Syncing, created?.mode)
        assertSame(created, account.getSwarm("conv1"))
        assertNull(account.newSwarmIfAbsent("conv1", Conversation.Mode.OneToOne))
    }

    /**
     * The interleaving that left a live 1:1 stuck in Syncing (2026-11-14): onConversationReady
     * has indexed the swarm as OneToOne but not yet started it, so getByUri() misses it, and
     * onContactAdded's placeholder must not touch it.
     */
    @Test
    fun placeholderDoesNotDowngradeASwarmBeingSetUp() {
        val account = Account(accountId = "acc")
        val ready = account.newSwarm("conv1", Conversation.Mode.OneToOne)   // onConversationReady, step 1
        assertNull(account.getByUri(ready.uri))                              // not started yet

        val placeholder = account.newSwarmIfAbsent("conv1", Conversation.Mode.Syncing) // onContactAdded
        assertNull(placeholder)
        assertEquals(Conversation.Mode.OneToOne, ready.mode)

        account.conversationStarted(ready)                                   // onConversationReady, step 2
        assertEquals(Conversation.Mode.OneToOne, account.getByUri(ready.uri)?.mode)
    }
}
