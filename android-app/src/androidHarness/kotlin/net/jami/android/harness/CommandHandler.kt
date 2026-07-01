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

import android.content.Context
import net.jami.e2e.protocol.AcceptContactRequest
import net.jami.e2e.protocol.AccountExported
import net.jami.e2e.protocol.AccountUri
import net.jami.e2e.protocol.AccountsSnapshot
import net.jami.e2e.protocol.CreateBareAccount
import net.jami.e2e.protocol.CreateJamiAccount
import net.jami.e2e.protocol.Directive
import net.jami.e2e.protocol.DomainEvent
import net.jami.e2e.protocol.ExportAccount
import net.jami.e2e.protocol.GetAccountUri
import net.jami.e2e.protocol.GetAccounts
import net.jami.e2e.protocol.ImportAccount
import net.jami.e2e.protocol.Ping
import net.jami.e2e.protocol.Pong
import net.jami.e2e.protocol.RegisterName
import net.jami.e2e.protocol.RemoveAccount
import net.jami.e2e.protocol.SendContactRequest
import net.jami.model.ConfigKey
import net.jami.model.Uri
import net.jami.services.AccountService
import net.jami.services.DaemonBridgeApi
import net.jami.ui.viewmodel.AccountCreationViewModel
import java.io.File
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

            is CreateBareAccount -> {
                // No username → no ACCOUNT_REGISTERED_NAME → the daemon never contacts the
                // name server. Uses the real account-creation service path directly (the
                // ViewModel mandates a username, so it can't produce a bare account).
                koin.get<AccountService>().createJamiAccount(
                    displayName = directive.displayName,
                    password = directive.password,
                )
            }

            is RemoveAccount -> koin.get<AccountService>().removeAccount(directive.accountId)

            is ExportAccount -> {
                // Write the archive into the app's private filesDir, which the runner pulls
                // via `run-as` over adb. AccountService.exportToFile is the real backup path.
                // [password] is the account's *current* archive password (unlocks the key);
                // scheme follows the same convention as the rest of the app.
                val file = File(koin.get<Context>().filesDir, directive.fileName)
                val scheme = if (directive.password.isEmpty()) "" else "password"
                val ok = koin.get<AccountService>().exportToFile(
                    directive.accountId, file.absolutePath, scheme = scheme, password = directive.password,
                )
                emit(AccountExported(directive.accountId, directive.fileName, ok))
            }

            is ImportAccount -> {
                // Re-create the account from a previously-pushed archive; AccountAdded is
                // observed separately via the accounts Flow.
                val file = File(koin.get<Context>().filesDir, directive.fileName)
                koin.get<AccountService>().createJamiAccount(
                    displayName = directive.displayName,
                    password = directive.password,
                    archivePath = file.absolutePath,
                )
            }

            is GetAccountUri -> {
                // A Jami account's own address is its public-key fingerprint, which the
                // daemon assigns under Account.username shortly after creation. Read it
                // live from the daemon (the cached Account may briefly lag REGISTERED),
                // with a short retry. Relayed out-of-band, never over DHT.
                val bridge = koin.get<DaemonBridgeApi>()
                var uri = bridge.getAccountDetails(directive.accountId)[ConfigKey.ACCOUNT_USERNAME.key].orEmpty()
                var tries = 0
                // Key derivation can be slow (notably for password-protected accounts, where
                // the account lingers in INITIALIZING), so allow up to ~10s for the fingerprint.
                while (uri.isBlank() && tries < 100) {
                    delay(100)
                    uri = bridge.getAccountDetails(directive.accountId)[ConfigKey.ACCOUNT_USERNAME.key].orEmpty()
                    tries++
                }
                emit(AccountUri(directive.accountId, uri))
            }

            is RegisterName -> {
                // Register a name on the name server; result observed via NameRegistrationEnded.
                koin.get<AccountService>().registerName(
                    directive.accountId, directive.name, scheme = "", password = directive.password,
                )
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
