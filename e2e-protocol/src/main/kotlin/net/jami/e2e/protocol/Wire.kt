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

/** Observed when a peer's contact/trust request arrives over the real DHT channel. */
@Serializable
@SerialName("incomingContactRequest")
data class IncomingContactRequest(val accountId: String, val fromUri: String) : DomainEvent

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

/** Accept a pending contact/trust request from [peerUri] (the receiver side). */
@Serializable
@SerialName("acceptContactRequest")
data class AcceptContactRequest(val accountId: String, val peerUri: String) : Directive

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

/** Shared Json instance — sealed hierarchies use the `type` discriminator + @SerialName. */
val HarnessJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    classDiscriminator = "type"
}
