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
 * Base class for conversation history storage.
 *
 * Ported from: jami-client-android libjamiclient
 * Changes: Removed ORMLite annotations (will use SQLDelight in KMP)
 */
open class ConversationHistory(
    var id: Int? = null,
    var participant: String? = null,
    var extraData: String? = null
) {
    constructor() : this(null, null, null)

    constructor(participant: String) : this(null, participant, null)

    constructor(id: Int, participant: String) : this(id, participant, null)

    companion object {
        const val TABLE_NAME = "conversations"
        const val COLUMN_CONVERSATION_ID = "id"
        const val COLUMN_PARTICIPANT = "participant"
        const val COLUMN_EXTRA_DATA = "extra_data"
    }
}
