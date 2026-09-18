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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.jami.model.Conversation
import net.jami.model.Uri
import net.jami.utils.Log
import net.jami.utils.currentTimeMillis

/**
 * Durable outbox for messages sent into a conversation that cannot take them yet
 * (doc/plan_daemon_stability_sync.md Phase 4, finding F4).
 *
 * libjami does not deliver a message committed into a 1:1 swarm that has no peer device yet, not
 * even once the swarm bootstraps seconds later (reproduced by the `send-before-member-join` e2e
 * scenario). Such messages are therefore *not* handed to the daemon: they are stored here and sent
 * once the conversation is live — on ConversationReady, on the peer joining (member event 1), and
 * after every smartlist load (so also at app start). A row is deleted as soon as it has been
 * handed to the daemon; only never-sent messages are stored, so a replay cannot duplicate one.
 *
 * Runs independently of any screen: [start] is called at app start, before accounts load.
 */
class SendQueueService(
    private val store: OutboxStore,
    private val accountService: AccountService,
    private val conversationFacade: ConversationFacade,
    private val daemonBridge: DaemonBridgeApi,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private var started = false

    private val _dispatched = MutableSharedFlow<OutboxEntry>(extraBufferCapacity = 64)

    /** Entries just handed to the daemon (e.g. so an open chat can show them as "sending"). */
    val dispatched: SharedFlow<OutboxEntry> = _dispatched.asSharedFlow()

    fun start() {
        if (started) return
        started = true
        scope.launch {
            conversationFacade.conversationEvents.collect { event ->
                when (event) {
                    is ConversationEvent.ConversationReady -> flush(event.accountId, event.conversationId)
                    // action 1 = the peer joined: the swarm now has a device to deliver to.
                    is ConversationEvent.MemberEvent ->
                        if (event.action == MEMBER_JOINED) flush(event.accountId, event.conversationId)
                    is ConversationEvent.ConversationsLoaded -> flushAll(event.accountId)
                    else -> Unit
                }
            }
        }
    }

    /**
     * True when a message sent into [conversationUri] now would be lost: the swarm is still
     * syncing, or it is a 1:1 whose peer the daemon still lists as only invited (they have not
     * joined, so the swarm has no peer device). The peer's role is read from the daemon each time.
     */
    fun shouldHold(accountId: String, conversationUri: Uri): Boolean =
        !isLive(accountId, conversationUri.rawRingId)

    /** Store a message to be sent once its conversation is live. */
    suspend fun enqueue(accountId: String, conversationId: String, body: String, replyTo: String? = null): OutboxEntry {
        val entry = mutex.withLock {
            store.insert(accountId, conversationId, body, replyTo, currentTimeMillis())
        }
        Log.i(TAG, "held message ${entry.id} for $conversationId until it is live")
        // It may have gone live in the meantime (no further event would then arrive).
        flush(accountId, conversationId)
        return entry
    }

    /** Messages still waiting for [conversationId], oldest first. */
    fun queued(accountId: String, conversationId: String): List<OutboxEntry> =
        store.forConversation(accountId, conversationId)

    /** Send every held message of [conversationId] if it is live now. */
    suspend fun flush(accountId: String, conversationId: String) {
        mutex.withLock {
            val entries = store.forConversation(accountId, conversationId)
            if (entries.isEmpty() || !isLive(accountId, conversationId)) return
            Log.i(TAG, "conversation $conversationId is live; sending ${entries.size} held message(s)")
            val uri = Uri(Uri.SWARM_SCHEME, conversationId)
            for (entry in entries) {
                accountService.sendConversationMessage(accountId, uri, entry.body, entry.replyTo)
                store.delete(entry.id)
                _dispatched.emit(entry)
            }
        }
    }

    private suspend fun flushAll(accountId: String) {
        store.all()
            .filter { it.accountId == accountId }
            .map { it.conversationId }
            .distinct()
            .forEach { flush(accountId, it) }
    }

    private fun isLive(accountId: String, conversationId: String): Boolean {
        val conversation = conversationFacade.getConversation(accountId, Uri(Uri.SWARM_SCHEME, conversationId))
            ?: return false
        return when (conversation.mode) {
            Conversation.Mode.Syncing, Conversation.Mode.Request -> false
            Conversation.Mode.OneToOne -> {
                val ownId = accountService.getAccount(accountId)?.username?.let { Uri.fromString(it).rawRingId }
                val members = try {
                    daemonBridge.getConversationMembers(accountId, conversationId)
                } catch (e: Exception) {
                    Log.w(TAG, "getConversationMembers failed for $conversationId", e)
                    return true // don't hold on a bridge error: that is the pre-outbox behaviour
                }
                val peer = members.firstOrNull { m ->
                    val uri = m["uri"] ?: return@firstOrNull false
                    Uri.fromString(uri).rawRingId != ownId
                }
                peer?.get("role") != ROLE_INVITED
            }
            else -> true
        }
    }

    companion object {
        private const val TAG = "SendQueueService"
        private const val MEMBER_JOINED = 1
        private const val ROLE_INVITED = "invited"
    }
}
