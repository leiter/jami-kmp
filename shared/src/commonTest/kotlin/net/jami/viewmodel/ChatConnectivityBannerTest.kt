/*
 *  Copyright (C) 2004-2026 Savoir-faire Linux Inc.
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
package net.jami.viewmodel

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.jami.model.Account
import net.jami.model.AccountConfig
import net.jami.model.ConfigKey
import net.jami.ui.viewmodel.AccountStatus
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The account-status ladder behind the switch label, and the banner decision behind the chat
 * screen's error strip. Both mirror `jami-client-android`; see
 * `JamiAccountSummaryFragment.setSwitchStatus` and `ConversationPresenter.kt:194-205`.
 */
class ChatConnectivityBannerTest {

    private fun account(
        state: Account.RegistrationState,
        enabled: Boolean = true,
    ): Account = Account(
        "acc1",
        mutableMapOf(
            ConfigKey.ACCOUNT_TYPE.key to AccountConfig.ACCOUNT_TYPE_JAMI,
            ConfigKey.ACCOUNT_ENABLE.key to enabled.toString(),
        ),
    ).apply {
        volatileDetails[ConfigKey.ACCOUNT_REGISTRATION_STATUS.key] = state.name
    }

    // ==================== Account predicates ported from the reference ====================

    @Test
    fun isTryingOnlyWhileRegistrationIsInFlight() {
        assertEquals(true, account(Account.RegistrationState.TRYING).isTrying)
        assertEquals(false, account(Account.RegistrationState.REGISTERED).isTrying)
        assertEquals(false, account(Account.RegistrationState.UNREGISTERED).isTrying)
    }

    @Test
    fun isInErrorCoversTheFiveErrorStatesAndNoOthers() {
        for (state in listOf(
            Account.RegistrationState.ERROR_AUTH,
            Account.RegistrationState.ERROR_GENERIC,
            Account.RegistrationState.ERROR_HOST,
            Account.RegistrationState.ERROR_NETWORK,
            Account.RegistrationState.ERROR_SERVICE_UNAVAILABLE,
        )) {
            assertEquals(true, account(state).isInError, "expected $state to be an error")
        }
        // Migration has its own message and remedy, so the reference excludes it here.
        assertEquals(false, account(Account.RegistrationState.ERROR_NEED_MIGRATION).isInError)
        // A disabled account is UNREGISTERED; that is its normal state, not a failure.
        assertEquals(false, account(Account.RegistrationState.UNREGISTERED).isInError)
        assertEquals(false, account(Account.RegistrationState.REGISTERED).isInError)
    }

    // ==================== The status ladder ====================

    @Test
    fun aDisabledAccountReportsDisabledWhateverElseIsTrue() {
        // Disabled wins: the reference's outermost branch is `if (account.isEnabled)`.
        assertEquals(
            AccountStatus.Disabled,
            AccountStatus.of(account(Account.RegistrationState.ERROR_NETWORK, enabled = false)),
        )
        assertEquals(
            AccountStatus.Disabled,
            AccountStatus.of(account(Account.RegistrationState.REGISTERED, enabled = false)),
        )
    }

    @Test
    fun theLadderFollowsTheReferencesOrder() {
        assertEquals(AccountStatus.Connecting, AccountStatus.of(account(Account.RegistrationState.TRYING)))
        assertEquals(
            AccountStatus.NeedsMigration,
            AccountStatus.of(account(Account.RegistrationState.ERROR_NEED_MIGRATION)),
        )
        assertEquals(
            AccountStatus.ConnectionError,
            AccountStatus.of(account(Account.RegistrationState.ERROR_NETWORK)),
        )
        assertEquals(AccountStatus.Online, AccountStatus.of(account(Account.RegistrationState.REGISTERED)))
        assertEquals(AccountStatus.Offline, AccountStatus.of(account(Account.RegistrationState.UNREGISTERED)))
    }

    @Test
    fun tryingOutranksAnErrorBecauseItIsStillInFlight() {
        // A retry after a failure reports Connecting, not the stale error — the reference checks
        // isTrying before isInError.
        val retrying = account(Account.RegistrationState.TRYING)
        assertEquals(false, retrying.isInError)
        assertEquals(AccountStatus.Connecting, AccountStatus.of(retrying))
    }

    @Test
    fun aReadyAccountCountsAsOnline() {
        // Account::States::READY — a SIP/IP2IP account with no registrar is usable; reading it as
        // UNREGISTERED showed a working account as offline.
        val ready = Account(
            "acc1",
            mutableMapOf(ConfigKey.ACCOUNT_TYPE.key to AccountConfig.ACCOUNT_TYPE_JAMI),
        ).apply { volatileDetails[ConfigKey.ACCOUNT_REGISTRATION_STATUS.key] = "READY" }

        assertEquals(AccountStatus.Online, AccountStatus.of(ready))
    }

    // ==================== accounts StateFlow does not report registration ====================

    @Test
    fun registrationChangesDoNotReachTheAccountsStateFlow() = runTest {
        // Why the banner observes accountEvents and not accountService.accounts: the service
        // re-assigns that StateFlow with a fresh List holding the *same* mutable Account
        // instances, so the new value compares equal and nothing is emitted. This pins that
        // behaviour so the observer is not "simplified" back onto accounts later.
        val stub = net.jami.services.StubDaemonBridge()
        stub.accountIds = listOf("acc1")
        stub.accountDetails["acc1"] =
            mapOf(ConfigKey.ACCOUNT_TYPE.key to AccountConfig.ACCOUNT_TYPE_JAMI)
        val scope = kotlinx.coroutines.CoroutineScope(
            coroutineContext + kotlinx.coroutines.SupervisorJob()
        )
        val service = net.jami.services.AccountService(
            stub, net.jami.testHardwareService(), net.jami.services.StubDeviceRuntimeService(), scope
        )
        service.loadAccounts()
        advanceUntilIdle()

        val seen = mutableListOf<Account.RegistrationState>()
        val job = scope.launch {
            service.accounts.collect { accounts ->
                accounts.firstOrNull()?.let { seen.add(it.registrationState) }
            }
        }
        advanceUntilIdle()

        service.onRegistrationStateChanged("acc1", "REGISTERED", 0, "")
        advanceUntilIdle()

        // The account itself did change...
        assertEquals(
            Account.RegistrationState.REGISTERED,
            service.getAccount("acc1")?.registrationState,
        )
        // ...but the flow never re-emitted, so a collector of `accounts` never learned of it.
        assertEquals(1, seen.size)
        job.cancel()
    }
}
