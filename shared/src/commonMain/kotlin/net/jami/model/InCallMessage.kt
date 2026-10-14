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
 * A message exchanged inside an ongoing call or conference.
 *
 * In-call chat is not a swarm conversation: the daemon carries these on the call's own channel
 * (SIP MESSAGE for SIP calls), they are not committed to any repository, and they live only as
 * long as the call. CallService keeps them in memory and drops them when the call ends.
 */
data class InCallMessage(
    /** Call or conference ID the message belongs to. */
    val callId: String,
    /** Author URI; our own URI for messages we sent. */
    val author: String,
    val body: String,
    val timestamp: Long,
    val isOutgoing: Boolean,
)
