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

import net.jami.model.Account
import net.jami.model.Conversation
import net.jami.model.Interaction
import net.jami.model.Uri

/**
 * Service for managing conversation and call history.
 *
 * This is a stub interface for the ConversationFacade port.
 * Full implementation will be added when database layer is complete.
 *
 * Ported from: jami-client-android libjamiclient
 */
interface HistoryService {

    /**
     * Get the smartlist (recent conversations) for an account.
     */
    suspend fun getSmartlist(accountId: String): List<Interaction>

    /**
     * Get conversation history for a specific conversation.
     */
    suspend fun getConversationHistory(accountId: String, conversationId: Long): List<Interaction>

    /**
     * Insert an interaction into the history database.
     */
    suspend fun insertInteraction(accountId: String, conversation: Conversation, interaction: Interaction)

    /**
     * Update an existing interaction.
     */
    suspend fun updateInteraction(interaction: Interaction, accountId: String)

    /**
     * Delete an interaction by ID.
     */
    suspend fun deleteInteraction(interactionId: Long, accountId: String)

    /**
     * Clear conversation history.
     * @param contactUri The contact URI
     * @param accountId The account ID
     * @param deleteConversation Whether to delete the conversation entirely
     */
    suspend fun clearHistory(contactUri: String, accountId: String, deleteConversation: Boolean)

    /**
     * Clear all history for multiple accounts.
     */
    suspend fun clearHistory(accounts: List<Account>)

    /**
     * Mark a message as notified.
     */
    fun setMessageNotified(accountId: String, conversationUri: Uri, messageId: String)

    /**
     * Get the last notified message ID for a conversation.
     */
    fun getLastMessageNotified(accountId: String, conversationUri: Uri): String?
}

/**
 * Stub implementation of HistoryService for testing.
 */
class StubHistoryService : HistoryService {
    override suspend fun getSmartlist(accountId: String): List<Interaction> = emptyList()

    override suspend fun getConversationHistory(accountId: String, conversationId: Long): List<Interaction> = emptyList()

    override suspend fun insertInteraction(accountId: String, conversation: Conversation, interaction: Interaction) {}

    override suspend fun updateInteraction(interaction: Interaction, accountId: String) {}

    override suspend fun deleteInteraction(interactionId: Long, accountId: String) {}

    override suspend fun clearHistory(contactUri: String, accountId: String, deleteConversation: Boolean) {}

    override suspend fun clearHistory(accounts: List<Account>) {}

    override fun setMessageNotified(accountId: String, conversationUri: Uri, messageId: String) {}

    override fun getLastMessageNotified(accountId: String, conversationUri: Uri): String? = null
}
