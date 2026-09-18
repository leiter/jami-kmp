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

import net.jami.database.JamiDatabase

/** A message held in the durable outbox (see Outbox.sq). */
data class OutboxEntry(
    val id: Long,
    val accountId: String,
    val conversationId: String,
    val body: String,
    val replyTo: String?,
    val createdAt: Long,
)

/** Persistence for [SendQueueService]. */
interface OutboxStore {
    fun insert(accountId: String, conversationId: String, body: String, replyTo: String?, createdAt: Long): OutboxEntry
    fun all(): List<OutboxEntry>
    fun forConversation(accountId: String, conversationId: String): List<OutboxEntry>
    fun delete(id: Long)
}

/** SQLDelight-backed outbox: survives app restarts (Android, iOS, macOS, desktop). */
class SqlDelightOutboxStore(database: JamiDatabase) : OutboxStore {
    private val queries = database.outboxQueries

    override fun insert(
        accountId: String, conversationId: String, body: String, replyTo: String?, createdAt: Long,
    ): OutboxEntry {
        val id = queries.transactionWithResult {
            queries.insert(accountId, conversationId, body, replyTo, createdAt)
            queries.lastInsertId().executeAsOne()
        }
        return OutboxEntry(id, accountId, conversationId, body, replyTo, createdAt)
    }

    override fun all(): List<OutboxEntry> = queries.selectAll().executeAsList().map { it.toEntry() }

    override fun forConversation(accountId: String, conversationId: String): List<OutboxEntry> =
        queries.selectByConversation(accountId, conversationId).executeAsList().map { it.toEntry() }

    override fun delete(id: Long) {
        queries.deleteById(id)
    }

    private fun net.jami.database.Outbox_message.toEntry() =
        OutboxEntry(id, account_id, conversation_id, body, reply_to, created_at)
}

/** Non-persistent outbox for platforms without the database (web) and for tests. */
class InMemoryOutboxStore : OutboxStore {
    private val entries = mutableListOf<OutboxEntry>()
    private var nextId = 1L

    override fun insert(
        accountId: String, conversationId: String, body: String, replyTo: String?, createdAt: Long,
    ): OutboxEntry = OutboxEntry(nextId++, accountId, conversationId, body, replyTo, createdAt)
        .also { entries.add(it) }

    override fun all(): List<OutboxEntry> = entries.toList()

    override fun forConversation(accountId: String, conversationId: String): List<OutboxEntry> =
        entries.filter { it.accountId == accountId && it.conversationId == conversationId }

    override fun delete(id: Long) {
        entries.removeAll { it.id == id }
    }
}
