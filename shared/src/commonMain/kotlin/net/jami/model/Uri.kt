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
package net.jami.model

/**
 * Represents a Jami/SIP URI.
 *
 * Supports various URI schemes:
 * - jami: / ring: - Jami protocol URIs
 * - sip: - SIP protocol URIs
 * - swarm: - Swarm conversation URIs
 *
 * Ported from: jami-client-android libjamiclient
 */
data class Uri(
    val scheme: String?,
    val username: String?,
    val host: String,
    val port: String?
) {
    constructor(scheme: String?, host: String) : this(scheme, null, host, null)

    val rawRingId: String
        get() = username ?: host

    val uri: String
        get() = when {
            isSwarm -> scheme + rawRingId
            isHexId -> rawRingId
            else -> toString()
        }

    val rawUriString: String
        get() = when {
            isSwarm -> scheme + rawRingId
            isHexId -> DEFAULT_CONTACT_SCHEME + rawRingId
            else -> toString()
        }

    override fun toString(): String = buildString {
        if (!scheme.isNullOrEmpty()) append(scheme)
        if (!username.isNullOrEmpty()) append(username).append('@')
        if (host.isNotEmpty()) append(host)
        if (!port.isNullOrEmpty()) append(':').append(port)
    }

    val isSingleIp: Boolean
        get() = username.isNullOrEmpty() && isIpAddress(host)

    val isHexId: Boolean
        get() = HEX_ID_REGEX.matches(host) ||
                (username != null && HEX_ID_REGEX.matches(username))

    val isSwarm: Boolean
        get() = scheme == SWARM_SCHEME

    val isJami: Boolean
        get() = scheme == JAMI_URI_SCHEME ||
                scheme == RING_URI_SCHEME ||
                (scheme.isNullOrEmpty() && isHexId)

    val isEmpty: Boolean
        get() = username.isNullOrEmpty() && host.isEmpty()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Uri) return false
        return username == other.username && host == other.host
    }

    override fun hashCode(): Int {
        var result = scheme?.hashCode() ?: 0
        result = 31 * result + (username?.hashCode() ?: 0)
        result = 31 * result + host.hashCode()
        result = 31 * result + (port?.hashCode() ?: 0)
        return result
    }

    companion object {
        private val HEX_ID_REGEX = Regex("^[0-9a-fA-F]{40}$")
        private val URI_REGEX = Regex("^\\s*(\\w+:)?(?:([\\w.]+)@)?(?:([\\d\\w.\\-]+)(?::(\\d+))?)\\s*$")
        private val ANGLE_BRACKETS_REGEX = Regex("^\\s*([^<>]+)?\\s*<([^<>]+)>\\s*$")
        private val IPV4_REGEX = Regex("^(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])$")
        private val IPV6_REGEX = Regex("^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$")

        const val RING_URI_SCHEME = "ring:"
        const val JAMI_URI_SCHEME = "jami:"
        const val SIP_URI_SCHEME = "sip:"
        const val DEFAULT_CONTACT_SCHEME = JAMI_URI_SCHEME
        const val SWARM_SCHEME = "swarm:"

        val EMPTY = Uri(null, null, "", null)

        fun fromString(uri: String): Uri {
            val match = URI_REGEX.find(uri)
            return if (match != null) {
                Uri(
                    scheme = match.groupValues[1].ifEmpty { null },
                    username = match.groupValues[2].ifEmpty { null },
                    host = match.groupValues[3],
                    port = match.groupValues[4].ifEmpty { null }
                )
            } else {
                Uri(null, null, uri.trim(), null)
            }
        }

        fun fromStringWithName(uriString: String): Pair<Uri, String?> {
            val match = ANGLE_BRACKETS_REGEX.find(uriString)
            return if (match != null) {
                Pair(fromString(match.groupValues[2]), match.groupValues[1].ifEmpty { null })
            } else {
                Pair(fromString(uriString), null)
            }
        }

        fun fromId(conversationId: String): Uri = Uri(null, conversationId)

        fun isIpAddress(ipAddress: String): Boolean =
            IPV4_REGEX.matches(ipAddress) || IPV6_REGEX.matches(ipAddress)
    }
}
