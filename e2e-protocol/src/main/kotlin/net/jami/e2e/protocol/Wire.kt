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

@Serializable
@SerialName("removeAccount")
data class RemoveAccount(val accountId: String) : Directive

/** Shared Json instance — sealed hierarchies use the `type` discriminator + @SerialName. */
val HarnessJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    classDiscriminator = "type"
}
