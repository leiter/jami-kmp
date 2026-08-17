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
package net.jami.e2e.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Wire schema for the end-to-end test harness coordination channel.
 *
 * This is the **out-of-band** control/observability protocol between the host runner
 * and the on-device agent (see doc/end2endTesting.md). It carries NO Jami payload —
 * the real daemon-to-daemon traffic flows separately over the DHT.
 *
 * One source of truth, shared by both `:e2e-runner` (host) and the Android `harness`
 * flavor (device).
 */

/** Top-level WebSocket text frame, both directions. */
@Serializable
sealed interface Envelope

/** device → server: announce presence and (optionally) request a role. */
@Serializable
@SerialName("hello")
data class Hello(val requestedRole: String? = null) : Envelope

/** device → server: a domain event observed on the device, stamped with the device clock. */
@Serializable
@SerialName("report")
data class ReportFrame(
    val role: String? = null,
    val event: DomainEvent,
    val deviceTsMillis: Long,
) : Envelope

/** server → device: a command for the device to execute via its real app entry points. */
@Serializable
@SerialName("command")
data class CommandFrame(
    val commandId: String,
    val directive: Directive,
) : Envelope

/** Normalized, serializable events the device reports back. */
@Serializable
sealed interface DomainEvent

@Serializable
@SerialName("pong")
data object Pong : DomainEvent

@Serializable
@SerialName("accountsSnapshot")
data class AccountsSnapshot(val ids: List<String>) : DomainEvent

@Serializable
@SerialName("accountAdded")
data class AccountAdded(val accountId: String) : DomainEvent

@Serializable
@SerialName("accountRemoved")
data class AccountRemoved(val accountId: String) : DomainEvent

@Serializable
@SerialName("registrationStateChanged")
data class RegistrationStateChanged(
    val accountId: String,
    val state: String,
    val code: Int,
) : DomainEvent

@Serializable
@SerialName("error")
data class ErrorEvent(val message: String) : DomainEvent

/** Response to [GetAccountUri]: the account's own Jami address (fingerprint). */
@Serializable
@SerialName("accountUri")
data class AccountUri(val accountId: String, val uri: String) : DomainEvent

/**
 * Observed when a peer's contact/trust request arrives over the real DHT channel.
 * [conversationUri] is the daemon's own request handle — a swarm URI (`swarm:<id>`) for a
 * modern swarm-native request, or a bare fingerprint for a legacy pre-swarm request — and is
 * what must be passed to [AcceptContactRequest] (the real UI accepts via `TrustRequest
 * .conversationUri`, never the bare peer URI; passing the peer URI instead sends the request
 * down the legacy accept path even when the daemon already issued a swarm request, which
 * confirms the contact but never joins the swarm conversation — see doc/end2endTesting.md).
 */
@Serializable
@SerialName("incomingContactRequest")
data class IncomingContactRequest(
    val accountId: String,
    val fromUri: String,
    val conversationUri: String,
) : DomainEvent

/** Observed when a contact is added; [confirmed] true once the handshake is two-sided. */
@Serializable
@SerialName("contactAdded")
data class ContactAdded(
    val accountId: String,
    val peerUri: String,
    val confirmed: Boolean,
) : DomainEvent

/**
 * Result of a username registration on the name server. [state] == 0 is success
 * (mirrors the daemon's name-registration result code).
 */
@Serializable
@SerialName("nameRegistrationEnded")
data class NameRegistrationEnded(
    val accountId: String,
    val state: Int,
    val name: String,
) : DomainEvent

/**
 * Result of [ExportAccount]: the account archive was written to [fileName] (relative to
 * the device app's private `filesDir`), ready to be pulled over the coordination channel.
 */
@Serializable
@SerialName("accountExported")
data class AccountExported(
    val accountId: String,
    val fileName: String,
    val success: Boolean,
) : DomainEvent

/**
 * Result of [ChangePassword]: the daemon's `changeAccountPassword` return value. [success]
 * is false when the supplied *old* password does not unlock the archive (so it doubles as a
 * password check), and true once the local archive has been re-encrypted under the new one.
 */
@Serializable
@SerialName("passwordChanged")
data class PasswordChanged(val accountId: String, val success: Boolean) : DomainEvent

/**
 * Result of [LookupName]: the name server's answer for [query]. [state] mirrors the daemon's
 * `LookupState` — `0`=success, `1`=invalid, `2`=not-found, `3`=network-error — plus a harness
 * sentinel `-1` when the daemon gave no answer within the handler's timeout. On success [name]
 * echoes the resolved name and [address] is the owner's Jami fingerprint.
 */
@Serializable
@SerialName("nameLookupResult")
data class NameLookupResult(
    val accountId: String,
    val query: String,
    val name: String,
    val address: String,
    val state: Int,
) : DomainEvent

/**
 * Result of [GetKnownDevices]: the daemon's own device registry for the account, as
 * `deviceId → deviceName`. Read synchronously from `getKnownRingDevices`, so it is the
 * authoritative view rather than a cached UI copy — which makes it the read-back proof for
 * [RenameDevice]. A freshly imported account holds exactly one entry: this device.
 */
@Serializable
@SerialName("knownDevices")
data class KnownDevices(
    val accountId: String,
    val devices: Map<String, String>,
) : DomainEvent

/** Commands the host runner issues to control device program state. */
@Serializable
sealed interface Directive

@Serializable
@SerialName("ping")
data object Ping : Directive

@Serializable
@SerialName("getAccounts")
data object GetAccounts : Directive

@Serializable
@SerialName("createJamiAccount")
data class CreateJamiAccount(val username: String, val password: String = "") : Directive

/**
 * Create a Jami account with **no registered username** — a bare account that never hits
 * the name server. Drives the real account-creation service path directly (the creation
 * ViewModel mandates a username, so this is the service-level entry point).
 */
@Serializable
@SerialName("createBareAccount")
data class CreateBareAccount(
    val displayName: String = "Harness",
    val password: String = "",
) : Directive

/**
 * Export an account's archive to a device-local file under the app's private `filesDir`.
 * The runner then pulls it over the coordination channel (no Jami payload — the archive
 * stands in for an out-of-band backup/restore, like the account-reuse fixture pool).
 */
@Serializable
@SerialName("exportAccount")
data class ExportAccount(
    val accountId: String,
    val fileName: String,
    val password: String = "",
) : Directive

/**
 * Import an account from a previously-pushed archive file (relative to the app's private
 * `filesDir`). The created account reuses the archive's identity (same Jami fingerprint).
 */
@Serializable
@SerialName("importAccount")
data class ImportAccount(
    val fileName: String,
    val password: String = "",
    val displayName: String = "Harness",
) : Directive

@Serializable
@SerialName("removeAccount")
data class RemoveAccount(val accountId: String) : Directive

/**
 * Change an account's archive password via the real `AccountService.changeAccountPassword`
 * path — a purely local re-encryption (no DHT / name server). Following the daemon
 * convention: [oldPassword] `""` means the archive is currently unprotected (so this *adds*
 * a password) and [newPassword] `""` *removes* the password. Result arrives as
 * [PasswordChanged]; a wrong [oldPassword] yields `success=false` (a no-op).
 */
@Serializable
@SerialName("changePassword")
data class ChangePassword(
    val accountId: String,
    val oldPassword: String,
    val newPassword: String,
) : Directive

/**
 * Enable or disable an account's registration via `AccountService.setAccountEnabled`
 * (`sendRegister`). Disabling unregisters the account (→ `UNREGISTERED`); enabling
 * re-registers it (→ `TRYING` → `REGISTERED`). The transition is observed through the
 * existing [RegistrationStateChanged] flow, so there is no dedicated result event.
 */
@Serializable
@SerialName("setAccountEnabled")
data class SetAccountEnabled(val accountId: String, val enabled: Boolean) : Directive

/**
 * Look up a registered [name] on the name server (the **read** side) via the awaitable
 * `AccountService.findRegistrationByName`. Result arrives as [NameLookupResult]. Non-consuming
 * — a pure query, it never mutates account or name-server state.
 */
@Serializable
@SerialName("lookupName")
data class LookupName(val accountId: String, val name: String) : Directive

/** Ask the device for an account's own Jami address (relayed out-of-band, not over DHT). */
@Serializable
@SerialName("getAccountUri")
data class GetAccountUri(val accountId: String) : Directive

/**
 * Register [name] on the name server for [accountId] (the consuming operation — a name is
 * burned globally). Result arrives as [NameRegistrationEnded]. Used to register a name on a
 * reused, previously-unnamed account.
 */
@Serializable
@SerialName("registerName")
data class RegisterName(
    val accountId: String,
    val name: String,
    val password: String = "",
) : Directive

/** Initiate a real contact/trust request to [peerUri] over the DHT (the initiator side). */
@Serializable
@SerialName("sendContactRequest")
data class SendContactRequest(val accountId: String, val peerUri: String) : Directive

/**
 * Accept a pending contact/trust request (the receiver side). [conversationUri] must be the
 * request's own handle — [IncomingContactRequest.conversationUri] — not the bare peer URI; see
 * that event's doc for why the distinction matters.
 */
@Serializable
@SerialName("acceptContactRequest")
data class AcceptContactRequest(val accountId: String, val conversationUri: String) : Directive

/**
 * Read the account's known-device registry from the daemon. Result arrives as [KnownDevices].
 * Non-consuming — a pure query.
 */
@Serializable
@SerialName("getKnownDevices")
data class GetKnownDevices(val accountId: String) : Directive

/**
 * Set this device's display name (`ACCOUNT_DEVICE_NAME`) to [newName]. Fire-and-forget: the
 * service call returns nothing, so the proof is a [GetKnownDevices] read-back. Non-consuming —
 * the name lives in the on-device account copy, which the scenario removes on teardown.
 */
@Serializable
@SerialName("renameDevice")
data class RenameDevice(val accountId: String, val newName: String) : Directive

/**
 * Send a real text message to the 1:1 swarm conversation with the confirmed contact
 * [peerUri]. The device resolves the conversation from the contact relationship (there is
 * no separate "start conversation" step for an already-confirmed 1:1 contact) and sends via
 * the real `ConversationFacade.sendTextMessage` path — daemon-to-daemon over the swarm
 * transport, exactly like the chat UI. Fire-and-forget: delivery/receipt is observed via
 * [MessageReceived].
 */
@Serializable
@SerialName("sendMessage")
data class SendMessage(val accountId: String, val peerUri: String, val text: String) : Directive

/**
 * Observed whenever a swarm message lands in a conversation — both the receiver's inbound
 * copy and the sender's own echo (the daemon reports a sent message back through the same
 * callback once its swarm commit is confirmed). [authorUri] is the message's author
 * fingerprint, so a scenario disambiguates "my own echo" from "the peer's message" by
 * comparing it against the known peer URI.
 */
@Serializable
@SerialName("messageReceived")
data class MessageReceived(
    val accountId: String,
    val conversationId: String,
    val authorUri: String,
    val text: String,
) : DomainEvent

/**
 * A member's role changed in a conversation's swarm git repo, as seen by *this* device — i.e.
 * once this device's daemon has actually pulled and processed the commit, not merely once the
 * member authored it locally. This is the non-racy join-confirmation signal: `ContactAdded
 * (confirmed=true)` and the accepter's own `ConversationReady` both fire as soon as the accepter
 * commits its own join locally, before any peer has necessarily seen it — [action]==1 observed
 * on the *peer's* device is the real proof the peer's daemon has caught up.
 *
 * [action] mirrors the daemon's `ConversationMemberEvent` signal
 * (`jami-daemon/src/jamidht/conversation.cpp:424-433`): `0`=add(invited), `1`=join, `2`=remove,
 * `3`=ban, `4`=unban.
 */
@Serializable
@SerialName("conversationMemberEvent")
data class ConversationMemberEvent(
    val accountId: String,
    val conversationId: String,
    val memberUri: String,
    val action: Int,
) : DomainEvent

/**
 * Set this account's display name and (optionally) avatar via the real
 * `AccountService.updateProfile` path — the same call the profile-edit UI uses. [avatarBase64],
 * when non-empty, is the raw image bytes base64-encoded (flag=1: base64 payload, not a file
 * path). Result observed as [ProfileUpdated].
 */
@Serializable
@SerialName("setProfile")
data class SetProfile(
    val accountId: String,
    val displayName: String,
    val avatarBase64: String = "",
    val fileType: String = "",
) : Directive

/** Observed once the daemon echoes back the account's own profile after [SetProfile]. */
@Serializable
@SerialName("profileUpdated")
data class ProfileUpdated(val accountId: String, val name: String, val hasPhoto: Boolean) : DomainEvent

/** One message to seed directly into a device's local history — see [SeedConversationMessages]. */
@Serializable
data class SeedMessage(val authorUri: String, val body: String, val timestampOffsetMs: Long)

/**
 * Seed a conversation transcript directly into the device's local SQLDelight history DB —
 * bypassing the daemon/swarm entirely. Used to build conversation fixtures without depending on
 * the real send path (see doc/end2endTesting.md's "Bug Findings (2026-08-14)" — the real
 * initiator-side send is currently broken, so fixtures route around it). [messages] are applied
 * in order with `timestamp = baseTimestampMs + timestampOffsetMs`; each device seeds its own
 * local copy independently, so [messages] should agree in content/order across both roles.
 * Fire-and-forget: no dedicated result event, the effect is verified by reading the conversation
 * back (or by the fixture-capture step succeeding).
 */
@Serializable
@SerialName("seedConversationMessages")
data class SeedConversationMessages(
    val accountId: String,
    val conversationId: String,
    val peerUri: String,
    val baseTimestampMs: Long,
    val messages: List<SeedMessage>,
) : Directive

/** Shared Json instance — sealed hierarchies use the `type` discriminator + @SerialName. */
val HarnessJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    classDiscriminator = "type"
}
