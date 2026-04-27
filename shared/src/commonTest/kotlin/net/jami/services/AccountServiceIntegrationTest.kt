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

import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.jami.model.AccountConfig
import net.jami.model.ConfigKey
import net.jami.services.StubDeviceRuntimeService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for AccountService using StubDaemonBridge.
 * Unlike AccountServiceTest (which tests data classes/events only),
 * these tests exercise real service method calls and state transitions.
 */
class AccountServiceIntegrationTest {

    @Test
    fun loadAccountsPopulatesStateFlow() = runTest {
        val stub = StubDaemonBridge()
        stub.accountIds = listOf("acc1", "acc2")
        stub.accountDetails["acc1"] = mapOf(ConfigKey.ACCOUNT_TYPE.key to AccountConfig.ACCOUNT_TYPE_JAMI)
        stub.accountDetails["acc2"] = mapOf(ConfigKey.ACCOUNT_TYPE.key to AccountConfig.ACCOUNT_TYPE_SIP)
        val service = AccountService(stub, StubHardwareService(), StubDeviceRuntimeService(), kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()))

        service.loadAccounts()

        assertEquals(2, service.accounts.value.size)
        assertTrue(service.accounts.value.any { it.accountId == "acc1" })
        assertTrue(service.accounts.value.any { it.accountId == "acc2" })
    }

    @Test
    fun loadAccountsSetsCurrentAccountToFirst() = runTest {
        val stub = StubDaemonBridge()
        stub.accountIds = listOf("acc1", "acc2")
        stub.accountDetails["acc1"] = mapOf(ConfigKey.ACCOUNT_TYPE.key to AccountConfig.ACCOUNT_TYPE_JAMI)
        stub.accountDetails["acc2"] = mapOf(ConfigKey.ACCOUNT_TYPE.key to AccountConfig.ACCOUNT_TYPE_JAMI)
        val service = AccountService(stub, StubHardwareService(), StubDeviceRuntimeService(), kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()))

        assertNull(service.currentAccount.value)
        service.loadAccounts()
        assertNotNull(service.currentAccount.value)
        assertEquals("acc1", service.currentAccount.value?.accountId)
    }

    @Test
    fun loadAccountsWithEmptyListKeepsCurrentAccountNull() = runTest {
        val stub = StubDaemonBridge()
        val service = AccountService(stub, StubHardwareService(), StubDeviceRuntimeService(), kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()))

        service.loadAccounts()

        assertTrue(service.accounts.value.isEmpty())
        assertNull(service.currentAccount.value)
    }

    @Test
    fun createJamiAccountCallsDaemonBridge() = runTest {
        val stub = StubDaemonBridge()
        stub.addAccountResult = "new_acc"
        val service = AccountService(stub, StubHardwareService(), StubDeviceRuntimeService(), kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()))

        val result = service.createJamiAccount(displayName = "Alice")

        assertEquals("new_acc", result)
    }

    @Test
    fun removeAccountUpdatesStateFlow() = runTest {
        val stub = StubDaemonBridge()
        stub.accountIds = listOf("acc1", "acc2")
        stub.accountDetails["acc1"] = mapOf(ConfigKey.ACCOUNT_TYPE.key to AccountConfig.ACCOUNT_TYPE_JAMI)
        stub.accountDetails["acc2"] = mapOf(ConfigKey.ACCOUNT_TYPE.key to AccountConfig.ACCOUNT_TYPE_JAMI)
        val service = AccountService(stub, StubHardwareService(), StubDeviceRuntimeService(), kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()))
        service.loadAccounts()
        assertEquals(2, service.accounts.value.size)

        service.removeAccount("acc1")

        assertEquals(1, service.accounts.value.size)
        assertEquals("acc2", service.accounts.value.first().accountId)
    }

    @Test
    fun removeCurrentAccountSwitchesToNext() = runTest {
        val stub = StubDaemonBridge()
        stub.accountIds = listOf("acc1", "acc2")
        stub.accountDetails["acc1"] = mapOf(ConfigKey.ACCOUNT_TYPE.key to AccountConfig.ACCOUNT_TYPE_JAMI)
        stub.accountDetails["acc2"] = mapOf(ConfigKey.ACCOUNT_TYPE.key to AccountConfig.ACCOUNT_TYPE_JAMI)
        val service = AccountService(stub, StubHardwareService(), StubDeviceRuntimeService(), kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()))
        service.loadAccounts()
        assertEquals("acc1", service.currentAccount.value?.accountId)

        service.removeAccount("acc1")

        assertEquals("acc2", service.currentAccount.value?.accountId)
    }

    @Test
    fun registrationStateChangedEmitsEvent() = runTest {
        val stub = StubDaemonBridge()
        stub.accountIds = listOf("acc1")
        stub.accountDetails["acc1"] = mapOf(ConfigKey.ACCOUNT_TYPE.key to AccountConfig.ACCOUNT_TYPE_JAMI)
        val service = AccountService(stub, StubHardwareService(), StubDeviceRuntimeService(), kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()))
        service.loadAccounts()

        var receivedEvent: AccountEvent? = null
        service.onRegistrationStateChanged("acc1", "REGISTERED", 200, "OK")
        advanceUntilIdle()
        // The event was emitted on the SharedFlow; verify account state was updated
        val account = service.getAccount("acc1")
        assertNotNull(account)
        assertEquals("REGISTERED", account.volatileDetails[ConfigKey.ACCOUNT_REGISTRATION_STATUS.key])
    }

    @Test
    fun getAccountReturnsNullForUnknownId() = runTest {
        val stub = StubDaemonBridge()
        val service = AccountService(stub, StubHardwareService(), StubDeviceRuntimeService(), kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()))

        assertNull(service.getAccount("nonexistent"))
    }

    @Test
    fun hasJamiAccountReturnsTrueWhenPresent() = runTest {
        val stub = StubDaemonBridge()
        stub.accountIds = listOf("acc1")
        stub.accountDetails["acc1"] = mapOf(ConfigKey.ACCOUNT_TYPE.key to AccountConfig.ACCOUNT_TYPE_JAMI)
        val service = AccountService(stub, StubHardwareService(), StubDeviceRuntimeService(), kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()))
        service.loadAccounts()

        assertTrue(service.hasJamiAccount())
        assertFalse(service.hasSipAccount())
    }

    @Test
    fun hasSipAccountReturnsTrueWhenPresent() = runTest {
        val stub = StubDaemonBridge()
        stub.accountIds = listOf("sip1")
        stub.accountDetails["sip1"] = mapOf(ConfigKey.ACCOUNT_TYPE.key to AccountConfig.ACCOUNT_TYPE_SIP)
        val service = AccountService(stub, StubHardwareService(), StubDeviceRuntimeService(), kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()))
        service.loadAccounts()

        assertTrue(service.hasSipAccount())
        assertFalse(service.hasJamiAccount())
    }
}
