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

import net.jami.utils.currentTimeMillis

/**
 * Represents a text message interaction in a conversation.
 *
 * Ported from: jami-client-android libjamiclient
 * Changes: System.currentTimeMillis() → expect/actual currentTimeMillis()
 */
class TextMessage : Interaction {

    constructor(
        author: String?,
        account: String,
        daemonId: String?,
        conversation: ConversationHistory?,
        message: String
    ) : super() {
        this.author = author
        this.account = account
        if (daemonId != null) {
            this.daemonId = daemonId.toLongOrNull()
                ?: daemonId.toLongOrNull(16)
                ?: 0L
        }
        timestamp = currentTimeMillis()
        type = InteractionType.TEXT
        this.conversation = conversation
        isIncoming = author != null
        body = message
    }

    constructor(
        author: String?,
        account: String,
        timestamp: Long,
        conversation: ConversationHistory?,
        message: String,
        isIncoming: Boolean,
        replyToId: String? = null
    ) : super() {
        this.author = author
        this.account = account
        this.timestamp = timestamp
        type = InteractionType.TEXT
        this.conversation = conversation
        this.isIncoming = isIncoming
        body = message
        this.replyToId = replyToId
    }

    constructor(interaction: Interaction) : super() {
        id = interaction.id
        author = interaction.author
        timestamp = interaction.timestamp
        type = InteractionType.TEXT
        status = interaction.status
        conversation = interaction.conversation
        isIncoming = author != null
        daemonId = interaction.daemonId
        body = interaction.body
        mIsRead = if (interaction.isRead) 1 else 0
        account = interaction.account
        contact = interaction.contact
    }
}
