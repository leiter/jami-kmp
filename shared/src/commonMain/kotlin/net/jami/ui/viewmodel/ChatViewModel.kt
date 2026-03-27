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
package net.jami.ui.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.jami.model.Uri
import net.jami.services.AccountService
import net.jami.services.ConversationFacade
import net.jami.services.ConversationEvent

/**
 * Type of message content.
 */
enum class MessageType {
    Text,
    System,
    Call,
    Transfer
}

/**
 * Item representing a single message in the chat.
 */
data class MessageItem(
    val id: String,
    val text: String,
    val author: String,
    val timestamp: Long,
    val isOutgoing: Boolean,
    val type: MessageType = MessageType.Text
)

/**
 * State for the chat / conversation detail screen.
 */
data class ChatState(
    val messages: List<MessageItem> = emptyList(),
    val inputText: String = "",
    val conversationTitle: String = "",
    val isLoading: Boolean = false,
    val isContactTyping: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<MessageItem> = emptyList(),
    val isSearchActive: Boolean = false
)

/**
 * ViewModel for the chat screen displaying messages in a single conversation.
 *
 * Handles message loading, sending, and real-time updates via
 * ConversationFacade event observation.
 */
class ChatViewModel(
    private val conversationFacade: ConversationFacade,
    private val accountService: AccountService,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val scope = scope

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private var currentConversationId: String? = null
    private var currentAccountId: String? = null

    init {
        // Observe incoming message events for the active conversation
        scope.launch {
            conversationFacade.conversationEvents.collect { event ->
                val convId = currentConversationId ?: return@collect
                when (event) {
                    is ConversationEvent.MessageReceived -> {
                        if (event.conversationId == convId) {
                            appendMessage(event)
                        }
                    }
                    is ConversationEvent.MessageUpdated -> {
                        if (event.conversationId == convId) {
                            updateMessage(event)
                        }
                    }
                    is ConversationEvent.SwarmLoaded -> {
                        if (event.conversationId == convId) {
                            loadMessagesFromHistory()
                        }
                    }
                    is ConversationEvent.ComposingStatusChanged -> {
                        if (event.conversationId == convId) {
                            _state.value = _state.value.copy(isContactTyping = event.status != 0)
                        }
                    }
                    is ConversationEvent.MessagesFound -> {
                        if (event.conversationId == convId) {
                            handleSearchResults(event.messages)
                        }
                    }
                    else -> { /* Handled elsewhere */ }
                }
            }
        }
    }

    /**
     * Load a conversation by its ID. Sets up observation and loads history.
     *
     * @param conversationId The swarm or legacy conversation ID.
     */
    fun loadConversation(conversationId: String) {
        scope.launch {
            _state.value = _state.value.copy(isLoading = true)
            try {
                val account = accountService.currentAccount.value ?: return@launch
                currentAccountId = account.accountId
                currentConversationId = conversationId

                val conversationUri = Uri(Uri.SWARM_SCHEME, conversationId)
                val conversation = conversationFacade.getConversation(account.accountId, conversationUri)

                val title = conversation?.contact?.displayUsername ?: conversationId
                _state.value = _state.value.copy(conversationTitle = title)

                // Load conversation history via the facade
                if (conversation != null) {
                    conversationFacade.loadConversationHistory(conversation)
                }

                loadMessagesFromHistory()
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    /**
     * Send the current input text as a message.
     */
    fun sendMessage() {
        val text = _state.value.inputText.trim()
        if (text.isEmpty()) return

        scope.launch {
            val accountId = currentAccountId ?: return@launch
            val conversationId = currentConversationId ?: return@launch
            val conversationUri = Uri(Uri.SWARM_SCHEME, conversationId)

            // Send via daemon
            accountService.sendConversationMessage(accountId, conversationUri, text)

            // Clear input
            _state.value = _state.value.copy(inputText = "")
        }
    }

    /**
     * Update the text input field.
     *
     * @param text New input text.
     */
    fun updateInput(text: String) {
        _state.value = _state.value.copy(inputText = text)
        val accountId = currentAccountId ?: return
        val conversationId = currentConversationId ?: return
        conversationFacade.setIsComposing(accountId, Uri(Uri.SWARM_SCHEME, conversationId), text.isNotEmpty())
    }

    /**
     * Delete a message by its ID.
     */
    fun deleteMessage(messageId: String) {
        scope.launch {
            val accountId = currentAccountId ?: return@launch
            val conversationId = currentConversationId ?: return@launch
            val conversationUri = Uri(Uri.SWARM_SCHEME, conversationId)
            accountService.deleteConversationMessage(accountId, conversationUri, messageId)
        }
    }

    /**
     * Edit a message by its ID.
     */
    fun editMessage(messageId: String, newText: String) {
        scope.launch {
            val accountId = currentAccountId ?: return@launch
            val conversationId = currentConversationId ?: return@launch
            val conversationUri = Uri(Uri.SWARM_SCHEME, conversationId)
            accountService.editConversationMessage(accountId, conversationUri, newText, messageId)
        }
    }

    /**
     * Load more (older) messages for the current conversation.
     */
    fun loadMore() {
        scope.launch {
            val accountId = currentAccountId ?: return@launch
            val conversationId = currentConversationId ?: return@launch
            val conversationUri = Uri(Uri.SWARM_SCHEME, conversationId)
            val conversation = conversationFacade.getConversation(accountId, conversationUri)
            if (conversation != null) {
                conversationFacade.loadConversationHistory(conversation)
            }
        }
    }

    /**
     * Reload messages from the conversation's in-memory history.
     */
    private fun loadMessagesFromHistory() {
        val accountId = currentAccountId ?: return
        val conversationId = currentConversationId ?: return
        val conversationUri = Uri(Uri.SWARM_SCHEME, conversationId)
        val conversation = conversationFacade.getConversation(accountId, conversationUri)

        if (conversation != null) {
            val history = conversation.getSortedHistory()
            val account = accountService.currentAccount.value
            val ownUri = account?.accountId ?: ""

            val items = history.map { interaction ->
                MessageItem(
                    id = interaction.messageId ?: interaction.id.toString(),
                    text = interaction.body ?: "",
                    author = interaction.author ?: "",
                    timestamp = interaction.timestamp,
                    isOutgoing = interaction.author == null || interaction.contact?.isUser == true,
                    type = MessageType.Text
                )
            }
            _state.value = _state.value.copy(messages = items, isLoading = false)
        } else {
            _state.value = _state.value.copy(isLoading = false)
        }
    }

    /**
     * Append a newly received message to the current list.
     */
    private fun appendMessage(event: ConversationEvent.MessageReceived) {
        val msg = event.message
        val account = accountService.currentAccount.value
        val item = MessageItem(
            id = msg.id,
            text = msg.textContent,
            author = msg.author,
            timestamp = msg.timestamp,
            isOutgoing = false,
            type = when {
                msg.isCall -> MessageType.Call
                msg.isText -> MessageType.Text
                else -> MessageType.System
            }
        )
        val current = _state.value.messages
        _state.value = _state.value.copy(messages = current + item)
    }

    /**
     * Update a message in the current list when it's been edited.
     */
    private fun updateMessage(event: ConversationEvent.MessageUpdated) {
        val msg = event.message
        val current = _state.value.messages
        val updated = current.map { item ->
            if (item.id == msg.id) {
                item.copy(text = msg.textContent)
            } else {
                item
            }
        }
        _state.value = _state.value.copy(messages = updated)
    }

    /**
     * Search for messages in the current conversation.
     */
    fun searchConversation(query: String) {
        _state.value = _state.value.copy(searchQuery = query, isSearchActive = query.isNotEmpty())
        if (query.isEmpty()) {
            _state.value = _state.value.copy(searchResults = emptyList())
            return
        }
        scope.launch {
            val accountId = currentAccountId ?: return@launch
            val conversationId = currentConversationId ?: return@launch
            val conversationUri = Uri(Uri.SWARM_SCHEME, conversationId)
            accountService.searchConversation(accountId, conversationUri, query)
        }
    }

    /**
     * Close the search UI and clear results.
     */
    fun closeSearch() {
        _state.value = _state.value.copy(
            searchQuery = "",
            searchResults = emptyList(),
            isSearchActive = false
        )
    }

    /**
     * Handle search results from the daemon.
     */
    private fun handleSearchResults(messages: List<Map<String, String>>) {
        val items = messages.map { msg ->
            MessageItem(
                id = msg["id"] ?: "",
                text = msg["body"] ?: "",
                author = msg["author"] ?: "",
                timestamp = msg["timestamp"]?.toLongOrNull() ?: 0L,
                isOutgoing = false,
                type = MessageType.Text
            )
        }
        _state.value = _state.value.copy(searchResults = items)
    }

    /**
     * Cancel the coroutine scope when this ViewModel is no longer needed.
     */
    fun onCleared() {
        scope.cancel()
    }
}
