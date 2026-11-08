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
package net.jami.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.jami.utils.Log

/** One conversation as the launcher needs it. */
data class ConversationShortcut(
    val accountId: String,
    val conversationId: String,
    val title: String,
    val avatar: ByteArray?,
) {
    // Hand-written because of the ByteArray: the default would compare it by identity and
    // republish every shortcut on every smartlist emission.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ConversationShortcut) return false
        return accountId == other.accountId &&
            conversationId == other.conversationId &&
            title == other.title &&
            avatar.contentEquals(other.avatar)
    }

    override fun hashCode(): Int {
        var result = accountId.hashCode()
        result = 31 * result + conversationId.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + (avatar?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * Publishes recent conversations to the platform's launcher, so a long-press on the app icon
 * opens a chat directly.
 *
 * Android only in practice — the reference client declares these in its manifest and KMP had
 * none. Everywhere else [NoConversationShortcuts] is bound, because no other target this app
 * runs on has a launcher that takes them.
 */
interface ConversationShortcutService {
    /** Replaces the published set. Called with the most recent conversations, in order. */
    fun publish(shortcuts: List<ConversationShortcut>)

    /** Removes everything published, e.g. when the last account goes away. */
    fun clear()
}

/** The binding for every platform without launcher shortcuts. */
object NoConversationShortcuts : ConversationShortcutService {
    override fun publish(shortcuts: List<ConversationShortcut>) = Unit
    override fun clear() = Unit
}

/**
 * Keeps [service] in step with the smartlist.
 *
 * Driven from the conversation list rather than from send/receive events: the launcher wants
 * "the conversations this person is in", which is exactly what the smartlist already is, and
 * it is already ordered by recency.
 */
class ConversationShortcutPublisher(
    private val conversationFacade: ConversationFacade,
    private val service: ConversationShortcutService,
    private val scope: CoroutineScope,
) {
    /** Four is what a long-press menu shows before the launcher starts dropping them. */
    private val limit = 4

    private var lastPublished: List<ConversationShortcut>? = null

    fun start() {
        scope.launch {
            conversationFacade.conversationList.collectLatest { list ->
                val shortcuts = list.conversations
                    .take(limit)
                    .map { conversation ->
                        ConversationShortcut(
                            accountId = conversation.accountId,
                            conversationId = conversation.uri.rawRingId,
                            title = conversation.title,
                            avatar = conversation.profileFlow.value.avatar,
                        )
                    }
                // The smartlist re-emits on every message; the launcher does not need to hear
                // about a conversation whose name and picture have not changed.
                if (shortcuts == lastPublished) return@collectLatest
                lastPublished = shortcuts
                Log.d(TAG, "Publishing ${shortcuts.size} conversation shortcuts")
                if (shortcuts.isEmpty()) service.clear() else service.publish(shortcuts)
            }
        }
    }

    private companion object {
        const val TAG = "ConversationShortcuts"
    }
}
