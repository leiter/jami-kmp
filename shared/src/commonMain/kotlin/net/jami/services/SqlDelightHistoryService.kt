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
import net.jami.model.Account
import net.jami.model.Conversation
import net.jami.model.Interaction
import net.jami.model.Uri
import net.jami.database.Interaction as DbInteraction

/**
 * SQLDelight-based implementation of HistoryService.
 *
 * This implementation uses the JamiDatabase to persist and retrieve
 * conversation history across all platforms.
 */
class SqlDelightHistoryService(
    private val database: JamiDatabase
) : HistoryService {

    override suspend fun getSmartlist(accountId: String): List<Interaction> {
        return database.interactionQueries
            .selectSmartlist(accountId)
            .executeAsList()
            .map { it.toDomain() }
    }

    override suspend fun getConversationHistory(
        accountId: String,
        conversationId: Long
    ): List<Interaction> {
        // Note: The interface uses Long for conversationId, but SQLDelight uses String.
        // We convert to String here. If the caller has a numeric ID, it works.
        // For swarm conversations with UUID-style IDs, the caller should use the string version.
        return database.interactionQueries
            .selectByConversation(conversationId.toString(), accountId)
            .executeAsList()
            .map { it.toDomain() }
    }

    /**
     * Get conversation history by string conversation ID (for swarm conversations).
     */
    suspend fun getConversationHistory(
        accountId: String,
        conversationId: String
    ): List<Interaction> {
        return database.interactionQueries
            .selectByConversation(conversationId, accountId)
            .executeAsList()
            .map { it.toDomain() }
    }

    /**
     * Get conversation history with pagination.
     */
    suspend fun getConversationHistoryPaged(
        accountId: String,
        conversationId: String,
        limit: Long,
        offset: Long
    ): List<Interaction> {
        return database.interactionQueries
            .selectByConversationPaged(conversationId, accountId, limit, offset)
            .executeAsList()
            .map { it.toDomain() }
    }

    override suspend fun insertInteraction(
        accountId: String,
        conversation: Conversation,
        interaction: Interaction
    ) {
        val conversationId = interaction.conversationId ?: conversation.uri.uri

        database.interactionQueries.insert(
            daemon_id = interaction.messageId ?: interaction.daemonIdString,
            account_id = accountId,
            conversation_id = conversationId,
            author = interaction.author,
            timestamp = interaction.timestamp,
            type = interaction.type.name,
            status = interaction.status.name,
            body = interaction.body,
            is_read = if (interaction.isRead) 1L else 0L,
            is_notified = if (interaction.isNotified) 1L else 0L,
            extra_data = interaction.extraFlag,
            parent_id = interaction.parentId,
            duration = null, // Call duration - can be added later
            transfer_status = interaction.transferStatus.takeIf { it != Interaction.TransferStatus.INVALID }?.name,
            file_path = null, // File path for transfers
            display_name = null // Display name for transfers
        )

        // Get the auto-generated ID and set it on the interaction
        val lastId = database.interactionQueries.lastInsertRowId().executeAsOne()
        interaction.id = lastId.toInt()
    }

    override suspend fun updateInteraction(interaction: Interaction, accountId: String) {
        database.interactionQueries.updateStatus(
            status = interaction.status.name,
            id = interaction.id.toLong()
        )
    }

    /**
     * Update interaction body (for message edits).
     */
    suspend fun updateInteractionBody(interactionId: Long, body: String?) {
        database.interactionQueries.updateBody(body, interactionId)
    }

    /**
     * Update file transfer status.
     */
    suspend fun updateTransferStatus(
        interactionId: Long,
        status: Interaction.TransferStatus,
        filePath: String?
    ) {
        database.interactionQueries.updateTransferStatus(
            transfer_status = status.name,
            file_path = filePath,
            id = interactionId
        )
    }

    override suspend fun deleteInteraction(interactionId: Long, accountId: String) {
        database.interactionQueries.deleteById(interactionId)
    }

    /**
     * Delete interaction by daemon ID.
     */
    suspend fun deleteInteractionByDaemonId(daemonId: String, accountId: String) {
        database.interactionQueries.deleteByDaemonId(daemonId, accountId)
    }

    override suspend fun clearHistory(
        contactUri: String,
        accountId: String,
        deleteConversation: Boolean
    ) {
        // Always delete interactions first (foreign key constraints may not be enforced)
        database.interactionQueries.deleteByConversation(contactUri, accountId)

        if (deleteConversation) {
            database.conversationQueries.deleteById(contactUri, accountId)
        }
    }

    override suspend fun clearHistory(accounts: List<Account>) {
        accounts.forEach { account ->
            database.interactionQueries.deleteByAccount(account.accountId)
            database.conversationQueries.deleteByAccount(account.accountId)
        }
    }

    override fun setMessageNotified(accountId: String, conversationUri: Uri, messageId: String) {
        database.interactionQueries.markAsNotifiedByDaemonId(messageId, accountId)
    }

    override fun getLastMessageNotified(accountId: String, conversationUri: Uri): String? {
        return database.interactionQueries
            .selectLastNotified(conversationUri.uri, accountId)
            .executeAsOneOrNull()
            ?.daemon_id
    }

    /**
     * Mark an interaction as read.
     */
    fun markAsRead(interactionId: Long) {
        database.interactionQueries.markAsRead(interactionId)
    }

    /**
     * Mark all interactions in a conversation as read.
     */
    fun markAllAsRead(conversationId: String, accountId: String) {
        database.interactionQueries.markAllAsRead(conversationId, accountId)
    }

    /**
     * Get unread interactions for a conversation.
     */
    suspend fun getUnreadInteractions(
        conversationId: String,
        accountId: String
    ): List<Interaction> {
        return database.interactionQueries
            .selectUnreadByConversation(conversationId, accountId)
            .executeAsList()
            .map { it.toDomain() }
    }

    /**
     * Get count of unread interactions for a conversation.
     */
    fun getUnreadCount(conversationId: String, accountId: String): Long {
        return database.interactionQueries
            .countUnreadByConversation(conversationId, accountId)
            .executeAsOne()
    }

    /**
     * Get interaction by ID.
     */
    suspend fun getInteractionById(interactionId: Long): Interaction? {
        return database.interactionQueries
            .selectById(interactionId)
            .executeAsOneOrNull()
            ?.toDomain()
    }

    /**
     * Get interaction by daemon ID.
     */
    suspend fun getInteractionByDaemonId(daemonId: String, accountId: String): Interaction? {
        return database.interactionQueries
            .selectByDaemonId(daemonId, accountId)
            .executeAsOneOrNull()
            ?.toDomain()
    }

    /**
     * Get the last interaction for a conversation.
     */
    suspend fun getLastInteraction(conversationId: String, accountId: String): Interaction? {
        return database.interactionQueries
            .selectLastByConversation(conversationId, accountId)
            .executeAsOneOrNull()
            ?.toDomain()
    }

    // ==================== Mapping Functions ====================

    /**
     * Convert SQLDelight Interaction to domain Interaction.
     */
    private fun DbInteraction.toDomain(): Interaction {
        return Interaction().apply {
            this.id = this@toDomain.id.toInt()
            this.account = this@toDomain.account_id
            this.author = this@toDomain.author
            this.timestamp = this@toDomain.timestamp
            this.body = this@toDomain.body
            this.type = Interaction.InteractionType.fromString(this@toDomain.type)
            this.status = Interaction.InteractionStatus.fromString(this@toDomain.status)
            this.daemonId = this@toDomain.daemon_id?.toLongOrNull()
            this.mIsRead = this@toDomain.is_read.toInt()
            this.isNotified = this@toDomain.is_notified == 1L
            this.extraFlag = this@toDomain.extra_data ?: "{}"

            // Set swarm info
            this@toDomain.daemon_id?.let { messageId ->
                setSwarmInfo(
                    this@toDomain.conversation_id,
                    messageId,
                    this@toDomain.parent_id
                )
            } ?: setSwarmInfo(this@toDomain.conversation_id)

            // Transfer status
            this@toDomain.transfer_status?.let { status ->
                this.transferStatus = Interaction.TransferStatus.entries
                    .firstOrNull { it.name == status }
                    ?: Interaction.TransferStatus.INVALID
            }
        }
    }

    companion object {
        /**
         * Helper to convert domain Interaction to database values.
         * Useful for testing and debugging.
         */
        fun Interaction.toDbValues(): Map<String, Any?> = mapOf(
            "daemon_id" to (messageId ?: daemonIdString),
            "account_id" to account,
            "conversation_id" to conversationId,
            "author" to author,
            "timestamp" to timestamp,
            "type" to type.name,
            "status" to status.name,
            "body" to body,
            "is_read" to if (isRead) 1L else 0L,
            "is_notified" to if (isNotified) 1L else 0L,
            "extra_data" to extraFlag,
            "parent_id" to parentId,
            "transfer_status" to transferStatus.takeIf { it != Interaction.TransferStatus.INVALID }?.name
        )
    }
}
