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
package net.jami.ui.viewmodel

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import net.jami.model.Account
import net.jami.services.AccountService
import net.jami.services.expect.HardwareService

/**
 * Why messages written here will not reach anyone, if they will not.
 *
 * Mirrors the reference's two error panels (`ConversationView.displayNetworkErrorPanel` /
 * `displayAccountOfflineErrorPanel`), including their precedence: a missing network is reported
 * even when the account is also disabled, because it is the more immediate cause.
 */
enum class ConnectivityBanner {
    /** No network on the device, and the account is not registered either. */
    NoNetwork,

    /** The network is fine but this account is switched off, so nothing will be sent. */
    AccountOffline,
}

/**
 * The reference's precedence: no network first, then a switched-off account.
 *
 * The `isConnected || isRegistered` tolerance is deliberate and comes straight from
 * `ConversationPresenter.kt:194`: a registered account is reachable even if the platform's
 * connectivity callback has not caught up, and the reference would rather say nothing than cry
 * offline at a working conversation. The cost is that the banner can lag a network drop by
 * however long the daemon takes to notice — it appears when *either* signal says so, not the
 * instant the interface goes down.
 */
fun connectivityBannerFor(isConnected: Boolean, account: Account?): ConnectivityBanner? =
    when {
        !(isConnected || account?.isRegistered == true) -> ConnectivityBanner.NoNetwork
        account != null && !account.isEnabled -> ConnectivityBanner.AccountOffline
        else -> null
    }

/**
 * The banner to show, recomputed whenever device connectivity or the account changes.
 *
 * Registration changes are taken from [AccountService.accountEvents], **not** from
 * `accountService.accounts`: that `StateFlow` is re-assigned with a fresh `List` holding the same
 * mutable [Account] instances, so the new value compares equal to the old one and the flow never
 * emits. `accountEvents` always emits, and replays each account's current registration state on
 * subscription, so the first value is correct rather than a guess. Any event re-reads the
 * account, since what matters is its state afterwards and not which signal carried it.
 *
 * [hardwareService] may be absent (tests, and platforms without a connectivity source), in which
 * case connectivity reads as **up** — never as down. Claiming "no network" because nothing is
 * reporting one would be a lie.
 *
 * [accountId] is resolved per emission rather than captured, so a screen that follows the current
 * account (the conversation list) and one pinned to a conversation's account (a chat) can share
 * this.
 */
fun connectivityBannerFlow(
    hardwareService: HardwareService?,
    accountService: AccountService,
    accountId: () -> String?,
): Flow<ConnectivityBanner?> =
    combine(
        hardwareService?.connectivityState ?: flowOf(true),
        accountService.accountEvents.map { }.onStart { emit(Unit) },
    ) { isConnected, _ ->
        connectivityBannerFor(isConnected, accountId()?.let(accountService::getAccount))
    }.distinctUntilChanged()
