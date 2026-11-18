package net.jami.model


import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountIsOwnTest {

    private val own = "66d57f55227639b58fdcc6d74ec090446ffa9bc3"

    private fun account() = Account(
        "acc",
        mutableMapOf(
            ConfigKey.ACCOUNT_TYPE.key to "RING",
            ConfigKey.ACCOUNT_USERNAME.key to "ring:$own",
        ),
    )

    @Test
    fun recognisesTheOwnIdInEveryForm() {
        val account = account()
        assertTrue(account.isOwn(own))
        assertTrue(account.isOwn("jami:$own"))
        assertTrue(account.isOwn(own.uppercase()))
        assertTrue(account.isOwn(Uri.fromString("ring:$own")))
    }

    @Test
    fun anotherIdIsNotOwn() {
        val account = account()
        assertFalse(account.isOwn("d9f8c7f0bf768178542d402c6ae233072bfff6df"))
        assertFalse(account.isOwn(""))
    }

    @Test
    fun anAccountWithoutUsernameOwnsNothing() {
        val account = Account("acc", mutableMapOf(ConfigKey.ACCOUNT_TYPE.key to "RING"))
        assertFalse(account.isOwn(own))
    }
}
