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
import net.jami.e2e.protocol.ChangePassword
import net.jami.e2e.protocol.CreateBareAccount
import net.jami.e2e.protocol.CreateJamiAccount
import net.jami.e2e.protocol.Directive
import net.jami.e2e.protocol.DomainEvent
import net.jami.e2e.protocol.ExportAccount
import net.jami.e2e.protocol.GetAccountUri
import net.jami.e2e.protocol.GetAccounts
import net.jami.e2e.protocol.GetKnownDevices
import net.jami.e2e.protocol.ImportAccount
import net.jami.e2e.protocol.KnownDevices
import net.jami.e2e.protocol.LookupName
import net.jami.e2e.protocol.NameLookupResult
import net.jami.e2e.protocol.PasswordChanged
import net.jami.e2e.protocol.Ping
import net.jami.e2e.protocol.Pong
import net.jami.e2e.protocol.RegisterName
import net.jami.e2e.protocol.RemoveAccount
import net.jami.e2e.protocol.RenameDevice
import net.jami.e2e.protocol.SeedConversationMessages
import net.jami.e2e.protocol.SendContactRequest
import net.jami.e2e.protocol.SendMessage
import net.jami.e2e.protocol.SetAccountEnabled
import net.jami.e2e.protocol.SetProfile
import net.jami.database.JamiDatabase
import net.jami.model.ConfigKey
import net.jami.model.Uri
import net.jami.services.AccountService
import net.jami.services.ConversationEvent
import net.jami.services.ConversationFacade
import net.jami.services.DaemonBridgeApi
import net.jami.ui.viewmodel.AccountCreationViewModel
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
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

            is ChangePassword -> {
                // Local archive re-encryption via the real service path. The daemon returns
                // false when oldPassword doesn't unlock the archive (also a password check).
                val ok = koin.get<AccountService>().changeAccountPassword(
                    directive.accountId, directive.oldPassword, directive.newPassword,
                )
                emit(PasswordChanged(directive.accountId, ok))
            }

            is SetAccountEnabled -> {
                // Toggle registration (sendRegister); the resulting UNREGISTERED/REGISTERED
                // transition is observed via the RegistrationStateChanged flow in HarnessAgent.
                koin.get<AccountService>().setAccountEnabled(directive.accountId, directive.enabled)
            }

            is LookupName -> {
                // Awaitable name-server read. findRegistrationByName suspends until the daemon
                // answers; a bounded wait maps a silent name server to the -1 sentinel instead
                // of hanging the command.
                val result = withTimeoutOrNull(20_000) {
                    koin.get<AccountService>().findRegistrationByName(directive.accountId, "", directive.name)
                }
                emit(
                    if (result == null) {
                        NameLookupResult(directive.accountId, directive.name, "", "", state = -1)
                    } else {
                        NameLookupResult(
                            directive.accountId, result.query, result.name, result.address, result.state.value,
                        )
                    },
                )
            }

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

            is GetKnownDevices -> {
                // Synchronous read of the daemon's own device registry (deviceId → name).
                val devices = koin.get<AccountService>().getKnownRingDevices(directive.accountId)
                emit(KnownDevices(directive.accountId, devices))
            }

            is RenameDevice -> {
                // Real settings path: rewrites ACCOUNT_DEVICE_NAME via setAccountDetails.
                // Returns nothing, so the runner proves it with a GetKnownDevices read-back.
                koin.get<AccountService>().renameDevice(directive.accountId, directive.newName)
            }

            is RegisterName -> {
                // Register a name on the name server; result observed via NameRegistrationEnded.
                koin.get<AccountService>().registerName(
                    directive.accountId, directive.name, scheme = "", password = directive.password,
                )
            }

            is SendContactRequest -> {
                // Initiator side: real trust request — flows peer-to-peer over the DHT.
                //
                // NOTE (investigated, not (yet) fixed here): swapping this for
                // AccountService.addContact() — the call NewConversationViewModel actually uses
                // for "add a new contact" — reproducibly made the peer never observe the
                // request at all (two consecutive 120s timeouts), whereas sendTrustRequest
                // reliably delivers. Left as sendTrustRequest so this scenario (and
                // two-device-contact) stay on the proven-reliable path. Separately, once B's
                // ContactAdded(confirmed=true) fires, B's own ConversationFacade never builds a
                // local swarm Conversation object for it — getConversation(accountId, peerUri)
                // never resolves even after 30s, even though B's daemon is visibly receiving
                // swarm commits for that exact conversationId (observed via onMessageReceived).
                // The real UI's equivalent flow (NewConversationViewModel.createConversation)
                // calls addContact() then conversationFacade.startConversation(), falling back
                // to awaiting ConversationEvent.ConversationReady — this harness doesn't
                // replicate that dance for the initiator side. Likely explains "message sending
                // broken": a contact you *sent* a request to may never get a locally-resolvable
                // conversation without this extra step. Worth a focused follow-up.
                koin.get<AccountService>()
                    .sendTrustRequest(directive.accountId, Uri.fromString(directive.peerUri))
            }

            is AcceptContactRequest -> {
                // Must be the request's own conversationUri (swarm URI for a modern swarm
                // request), matching the real UI's PendingRequestsViewModel/ConversationsViewModel
                // — accepting via the bare peer URI takes the legacy path even on a swarm
                // request, confirming the contact without ever joining the swarm conversation.
                koin.get<AccountService>()
                    .acceptTrustRequest(directive.accountId, Uri.fromString(directive.conversationUri))
            }

            is SendMessage -> {
                // Same pattern as the real UI's NewConversationViewModel.createConversation():
                // startConversation() resolves immediately if the swarm conversation is already
                // known locally; if it isn't yet (still propagating over the DHT), it throws and
                // the fallback awaits the daemon's own ConversationReady signal for this account,
                // then re-resolves. A blind getConversation() poll was tried first and never
                // resolved even after 30s+ despite the daemon visibly delivering swarm commits
                // for the conversation — this is the actual mechanism the app relies on.
                val conversationFacade = koin.get<ConversationFacade>()
                val peer = Uri.fromString(directive.peerUri)
                val conversation = try {
                    conversationFacade.startConversation(directive.accountId, peer)
                } catch (e: Exception) {
                    withTimeoutOrNull(30_000) {
                        conversationFacade.conversationEvents
                            .filterIsInstance<ConversationEvent.ConversationReady>()
                            .filter { it.accountId == directive.accountId }
                            .map { conversationFacade.getConversation(directive.accountId, peer) }
                            .filterNotNull()
                            .first()
                    }
                }
                if (conversation == null) {
                    emit(net.jami.e2e.protocol.ErrorEvent("no conversation with ${directive.peerUri}"))
                } else {
                    conversationFacade.sendTextMessage(conversation, peer, directive.text)
                }
            }

            is SetProfile -> {
                // Real profile-edit path: AccountService.updateProfile → daemon → the account's
                // own ProfileReceived echo (observed separately in HarnessAgent). flag=1: avatar
                // bytes are supplied as base64, not a file path.
                koin.get<AccountService>().updateProfile(
                    accountId = directive.accountId,
                    displayName = directive.displayName,
                    avatar = directive.avatarBase64,
                    fileType = directive.fileType,
                    flag = if (directive.avatarBase64.isEmpty()) 0 else 1,
                )
            }

            is SeedConversationMessages -> {
                // Bypass the daemon/swarm entirely: write straight into the local SQLDelight
                // history DB (the same store ConversationFacade reads from), so fixtures don't
                // depend on the currently-broken real send path. Each device seeds its own copy.
                val db = koin.get<JamiDatabase>()
                db.conversationQueries.insert(
                    id = directive.conversationId,
                    account_id = directive.accountId,
                    participant = directive.peerUri,
                    mode = "OneToOne",
                    extra_data = null,
                    last_event_timestamp = directive.baseTimestampMs,
                    is_syncing = 0L,
                    created_at = directive.baseTimestampMs,
                )
                for (msg in directive.messages) {
                    db.interactionQueries.insert(
                        daemon_id = null,
                        account_id = directive.accountId,
                        conversation_id = directive.conversationId,
                        author = msg.authorUri,
                        timestamp = directive.baseTimestampMs + msg.timestampOffsetMs,
                        type = "TEXT",
                        status = "SUCCESS",
                        body = msg.body,
                        is_read = 1L,
                        is_notified = 1L,
                        extra_data = null,
                        parent_id = null,
                        duration = null,
                        transfer_status = null,
                        file_path = null,
                        display_name = null,
                    )
                }
            }
        }
    }
}
