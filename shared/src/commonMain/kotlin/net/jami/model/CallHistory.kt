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
 * Represents a call history entry in a conversation.
 *
 * Ported from: jami-client-android libjamiclient
 * Changes:
 * - Removed Gson JSON handling (simplified duration storage)
 * - java.util.Locale formatting → simplified string formatting
 */
class CallHistory : Interaction {

    private var _daemonIdString: String? = null
    override val daemonIdString: String?
        get() = _daemonIdString ?: super.daemonIdString

    var duration: Long = 0L
        set(value) {
            if (value == field) return
            field = value
            if (value != 0L) {
                isMissed = false
            }
        }

    var isMissed: Boolean = true
        private set

    var contactNumber: String? = null
        private set

    var confId: String? = null
    var hostDevice: String? = null
    var hostUri: String? = null

    constructor(call: Call) : super() {
        account = call.account
        _daemonIdString = call.daemonId
        daemonId = call.daemonId?.toLongOrNull()
        isIncoming = call.isIncoming
        timestamp = call.timestamp
        type = InteractionType.CALL
        contact = call.contact
        isMissed = duration == 0L
    }

    constructor(interaction: Interaction) : super() {
        id = interaction.id
        author = interaction.author
        conversation = interaction.conversation
        isIncoming = author != null
        timestamp = interaction.timestamp
        type = InteractionType.CALL
        status = interaction.status
        daemonId = interaction.daemonId
        _daemonIdString = super.daemonIdString
        mIsRead = 1
        account = interaction.account
        contact = interaction.contact
        // Parse duration from extraFlag if present
        parseDurationFromExtraFlag(interaction.extraFlag)
        isMissed = duration == 0L
    }

    constructor(
        daemonId: String?,
        account: String?,
        contactNumber: String?,
        direction: Call.Direction,
        timestamp: Long
    ) : super() {
        _daemonIdString = daemonId
        this.daemonId = daemonId?.toLongOrNull()
        isIncoming = direction == Call.Direction.INCOMING
        this.account = account
        author = if (direction == Call.Direction.INCOMING) contactNumber else null
        this.contactNumber = contactNumber
        this.timestamp = timestamp
        type = InteractionType.CALL
        mIsRead = 1
    }

    val isConferenceParticipant: Boolean
        get() = confId != null

    val isGroupCall: Boolean
        get() = isConferenceParticipant && duration == 0L

    val durationString: String
        get() {
            val seconds = duration / 1000
            return when {
                seconds < 60 -> "${seconds.toString().padStart(2, '0')} secs"
                seconds < 3600 -> {
                    val mins = seconds / 60
                    val secs = seconds % 60
                    "${mins.toString().padStart(2, '0')} mins ${secs.toString().padStart(2, '0')} secs"
                }
                else -> {
                    val hours = seconds / 3600
                    val mins = (seconds % 3600) / 60
                    val secs = seconds % 60
                    "$hours h ${mins.toString().padStart(2, '0')} mins ${secs.toString().padStart(2, '0')} secs"
                }
            }
        }

    fun setEnded(end: CallHistory) {
        duration = end.duration
    }

    private fun parseDurationFromExtraFlag(extraFlag: String) {
        // Simple JSON parsing for duration field
        // Format: {"duration":12345}
        val durationRegex = """"duration"\s*:\s*(\d+)""".toRegex()
        durationRegex.find(extraFlag)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let {
            duration = it
        }
    }

    companion object {
        const val KEY_HAS_VIDEO = "HAS_VIDEO"
        const val KEY_DURATION = "duration"
    }
}
