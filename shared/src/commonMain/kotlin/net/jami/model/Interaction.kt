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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Base class for all conversation interactions (messages, calls, transfers, etc.).
 *
 * Ported from: jami-client-android libjamiclient
 * Changes:
 * - Removed ORMLite annotations (will use SQLDelight in KMP)
 * - Removed Gson (use kotlinx.serialization if needed)
 * - RxJava → Kotlin Flow
 */
open class Interaction {
    var account: String? = null
    var isIncoming: Boolean = false
    var contact: Contact? = null
    var replyToId: String? = null
    var edit: String? = null
    var reactToId: String? = null

    var reactions: MutableList<Interaction> = mutableListOf()
    var history: MutableList<Interaction> = mutableListOf<Interaction>().apply { add(this@Interaction) }
    var statusMap: Map<String, MessageStates> = emptyMap()

    private val _reactions = MutableStateFlow<List<Interaction>>(emptyList())
    val reactionsFlow: StateFlow<List<Interaction>> = _reactions.asStateFlow()

    private val _history = MutableStateFlow<List<Interaction>>(listOf(this))
    val historyFlow: StateFlow<List<Interaction>> = _history.asStateFlow()

    var id: Int = 0
    var author: String? = null
    var conversation: ConversationHistory? = null
    var timestamp: Long = 0
    var body: String? = null
    var type: InteractionType = InteractionType.INVALID

    var status: InteractionStatus = InteractionStatus.INVALID
        set(value) {
            if (value == InteractionStatus.DISPLAYED) {
                mIsRead = 1
            }
            field = value
        }

    var transferStatus: TransferStatus = TransferStatus.INVALID
    var daemonId: Long? = null
    var mIsRead: Int = 0
    var extraFlag: String = "{}"
    var isNotified: Boolean = false

    // Swarm fields
    var conversationId: String? = null
        private set
    var messageId: String? = null
        private set
    var parentId: String? = null
        private set

    var preview: Any? = null

    constructor()

    constructor(accountId: String) {
        account = accountId
        type = InteractionType.INVALID
    }

    constructor(conversation: Conversation, type: InteractionType) {
        this.conversation = conversation
        this.account = conversation.accountId
        this.type = type
    }

    constructor(
        id: String,
        author: String?,
        conversation: ConversationHistory?,
        timestamp: String,
        body: String?,
        type: String,
        status: String,
        daemonId: String?,
        isRead: String,
        extraFlag: String
    ) {
        this.id = id.toIntOrNull() ?: 0
        this.author = author
        this.conversation = conversation
        this.timestamp = timestamp.toLongOrNull() ?: 0L
        this.body = body
        this.type = InteractionType.fromString(type)
        this.status = InteractionStatus.fromString(status)
        this.daemonId = daemonId?.toLongOrNull()
        this.mIsRead = isRead.toIntOrNull() ?: 0
        this.extraFlag = extraFlag
    }

    fun read() {
        mIsRead = 1
    }

    open val daemonIdString: String?
        get() = daemonId?.toString()

    val isRead: Boolean
        get() = mIsRead == 1

    val isSwarm: Boolean
        get() = !messageId.isNullOrEmpty()

    fun setSwarmInfo(conversationId: String) {
        this.conversationId = conversationId
        this.messageId = null
        this.parentId = null
    }

    fun setSwarmInfo(conversationId: String, messageId: String, parent: String?) {
        this.conversationId = conversationId
        this.messageId = messageId
        this.parentId = parent
    }

    fun addReaction(interaction: Interaction) {
        reactions.add(interaction)
        _reactions.value = reactions.toList()
    }

    fun addReactions(interactions: List<Interaction>) {
        reactions.addAll(interactions)
        _reactions.value = reactions.toList()
    }

    fun removeReaction(id: String) {
        reactions.removeAll { it.messageId == id }
        _reactions.value = reactions.toList()
    }

    fun replaceReactions(interactions: List<Interaction>) {
        reactions.clear()
        reactions.addAll(interactions)
        _reactions.value = reactions.toList()
    }

    fun addEdit(interaction: Interaction, newMessage: Boolean) {
        history.remove(interaction)
        if (newMessage) {
            history.add(interaction)
        } else {
            history.add(0, interaction)
        }
        _history.value = history.toList()
    }

    fun addEdits(interactions: List<Interaction>) {
        history.addAll(interactions)
        _history.value = history.toList()
    }

    fun replaceEdits(interactions: List<Interaction>) {
        history.clear()
        history.addAll(interactions)
        _history.value = history.toList()
    }

    fun updateParent(parentId: String) {
        this.parentId = parentId
    }

    // ==================== Enums ====================

    enum class MessageStates(val value: Int) {
        UNKNOWN(0),
        SENDING(1),
        SUCCESS(2),
        DISPLAYED(3),
        INVALID(4),
        FAILURE(5),
        CANCELLED(6);

        companion object {
            fun fromInt(value: Int): MessageStates =
                entries.getOrElse(value) { INVALID }
        }
    }

    enum class InteractionStatus {
        UNKNOWN, SENDING, SUCCESS, DISPLAYED, INVALID, FAILURE;

        companion object {
            fun fromString(str: String): InteractionStatus =
                entries.firstOrNull { it.name == str } ?: INVALID

            fun fromIntTextMessage(n: Int): InteractionStatus =
                entries.getOrElse(n) { INVALID }
        }
    }

    enum class TransferStatus {
        INVALID,
        FAILURE,
        TRANSFER_CREATED,
        TRANSFER_ACCEPTED,
        TRANSFER_CANCELED,
        TRANSFER_ERROR,
        TRANSFER_UNJOINABLE_PEER,
        TRANSFER_ONGOING,
        TRANSFER_AWAITING_PEER,
        TRANSFER_AWAITING_HOST,
        TRANSFER_TIMEOUT_EXPIRED,
        TRANSFER_FINISHED,
        FILE_AVAILABLE,
        FILE_REMOVED;

        val isError: Boolean
            get() = this == TRANSFER_ERROR ||
                    this == TRANSFER_UNJOINABLE_PEER ||
                    this == TRANSFER_CANCELED ||
                    this == TRANSFER_TIMEOUT_EXPIRED ||
                    this == FAILURE

        val isOver: Boolean
            get() = isError || this == TRANSFER_FINISHED

        companion object {
            fun fromIntFile(n: Int): TransferStatus = when (n) {
                0 -> INVALID
                1 -> TRANSFER_CREATED
                2, 9 -> TRANSFER_ERROR
                3 -> TRANSFER_AWAITING_PEER
                4 -> TRANSFER_AWAITING_HOST
                5 -> TRANSFER_ONGOING
                6 -> TRANSFER_FINISHED
                7, 8, 10 -> TRANSFER_UNJOINABLE_PEER
                11 -> TRANSFER_TIMEOUT_EXPIRED
                else -> INVALID
            }
        }
    }

    enum class InteractionType {
        INVALID, TEXT, CALL, CONTACT, DATA_TRANSFER;

        companion object {
            fun fromString(str: String): InteractionType =
                entries.firstOrNull { it.name == str } ?: INVALID
        }
    }

    companion object {
        const val TABLE_NAME = "interactions"
        const val COLUMN_ID = "id"
        const val COLUMN_AUTHOR = "author"
        const val COLUMN_CONVERSATION = "conversation"
        const val COLUMN_TIMESTAMP = "timestamp"
        const val COLUMN_BODY = "body"
        const val COLUMN_TYPE = "type"
        const val COLUMN_STATUS = "status"
        const val COLUMN_DAEMON_ID = "daemon_id"
        const val COLUMN_IS_READ = "is_read"
        const val COLUMN_EXTRA_FLAG = "extra_data"

        fun compare(a: Interaction?, b: Interaction?): Int {
            if (a == null) return if (b == null) 0 else -1
            return if (b == null) 1 else a.timestamp.compareTo(b.timestamp)
        }
    }
}
