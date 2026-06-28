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

import net.jami.e2e.protocol.AccountsSnapshot
import net.jami.e2e.protocol.CreateJamiAccount
import net.jami.e2e.protocol.Directive
import net.jami.e2e.protocol.DomainEvent
import net.jami.e2e.protocol.GetAccounts
import net.jami.e2e.protocol.Ping
import net.jami.e2e.protocol.Pong
import net.jami.e2e.protocol.RemoveAccount
import net.jami.services.AccountService
import net.jami.ui.viewmodel.AccountCreationViewModel
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
        }
    }
}
