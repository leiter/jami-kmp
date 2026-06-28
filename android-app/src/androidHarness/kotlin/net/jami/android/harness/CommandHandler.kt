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
package net.jami.android.harness

import net.jami.e2e.protocol.AcceptContactRequest
import net.jami.e2e.protocol.AccountUri
import net.jami.e2e.protocol.AccountsSnapshot
import net.jami.e2e.protocol.CreateJamiAccount
import net.jami.e2e.protocol.Directive
import net.jami.e2e.protocol.DomainEvent
import net.jami.e2e.protocol.GetAccountUri
import net.jami.e2e.protocol.GetAccounts
import net.jami.e2e.protocol.Ping
import net.jami.e2e.protocol.Pong
import net.jami.e2e.protocol.RemoveAccount
import net.jami.e2e.protocol.SendContactRequest
import net.jami.model.ConfigKey
import net.jami.model.Uri
import net.jami.services.AccountService
import net.jami.services.DaemonBridgeApi
import net.jami.ui.viewmodel.AccountCreationViewModel
import kotlinx.coroutines.delay
import org.koin.core.Koin

/**
 * Maps a [Directive] from the host runner to a real app action, driving the same
 * ViewModel/service entry points the UI uses. Side-effect events (account added/removed,
 * registration changes) are observed separately via service Flows in [HarnessAgent];
 * only synchronous request/response events (Pong, AccountsSnapshot) are emitted here.
 */
class CommandHandler(private val koin: Koin) {

    suspend fun handle(directive: Directive, emit: suspend (DomainEvent) -> Unit) {
        when (directive) {
            is Ping -> emit(Pong)

            is GetAccounts -> {
                val ids = koin.get<AccountService>().accounts.value.map { it.accountId }
                emit(AccountsSnapshot(ids))
            }

            is CreateJamiAccount -> {
                // Drive the real account-creation ViewModel (factory-scoped in Koin).
                val vm = koin.get<AccountCreationViewModel>()
                vm.setUsername(directive.username)
                vm.createAccount()
            }

            is RemoveAccount -> koin.get<AccountService>().removeAccount(directive.accountId)

            is GetAccountUri -> {
                // A Jami account's own address is its public-key fingerprint, which the
                // daemon assigns under Account.username shortly after creation. Read it
                // live from the daemon (the cached Account may briefly lag REGISTERED),
                // with a short retry. Relayed out-of-band, never over DHT.
                val bridge = koin.get<DaemonBridgeApi>()
                var uri = bridge.getAccountDetails(directive.accountId)[ConfigKey.ACCOUNT_USERNAME.key].orEmpty()
                var tries = 0
                while (uri.isBlank() && tries < 20) {
                    delay(100)
                    uri = bridge.getAccountDetails(directive.accountId)[ConfigKey.ACCOUNT_USERNAME.key].orEmpty()
                    tries++
                }
                emit(AccountUri(directive.accountId, uri))
            }

            is SendContactRequest -> {
                // Initiator side: real trust request — flows peer-to-peer over the DHT.
                koin.get<AccountService>()
                    .sendTrustRequest(directive.accountId, Uri.fromString(directive.peerUri))
            }

            is AcceptContactRequest -> {
                koin.get<AccountService>()
                    .acceptTrustRequest(directive.accountId, Uri.fromString(directive.peerUri))
            }
        }
    }
}
