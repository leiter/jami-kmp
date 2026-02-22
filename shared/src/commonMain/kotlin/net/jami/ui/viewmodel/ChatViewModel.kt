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
import net.jami.ui.contracts.ChatContract
import net.jami.ui.contracts.MessageItem
import net.jami.ui.contracts.MessageType

/**
 * ViewModel for the chat screen displaying messages in a single conversation.
 *
 * Exposes three separate state flows (Tier 1 split) so that keystroke
 * input changes do not recompose the message list or top bar.
 */
class ChatViewModel(
    private val conversationFacade: ConversationFacade,
    private val accountService: AccountService
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _topBarState = MutableStateFlow(ChatContract.TopBarState())
    val topBarState: StateFlow<ChatContract.TopBarState> = _topBarState.asStateFlow()

    private val _messagesState = MutableStateFlow(ChatContract.MessagesState())
    val messagesState: StateFlow<ChatContract.MessagesState> = _messagesState.asStateFlow()

    private val _inputState = MutableStateFlow(ChatContract.InputState())
    val inputState: StateFlow<ChatContract.InputState> = _inputState.asStateFlow()

    private var currentConversationId: String? = null
    private var currentAccountId: String? = null

    init {
        scope.launch {
            conversationFacade.conversationEvents.collect { event ->
                val convId = currentConversationId ?: return@collect
                when (event) {
                    is ConversationEvent.MessageReceived -> {
                        if (event.conversationId == convId) {
                            appendMessage(event)
                        }
                    }
                    is ConversationEvent.SwarmLoaded -> {
                        if (event.conversationId == convId) {
                            loadMessagesFromHistory()
                        }
                    }
                    else -> { /* Handled elsewhere */ }
                }
            }
        }
    }

    fun onAction(action: ChatContract.Action) {
        when (action) {
            is ChatContract.Action.UpdateInput -> {
                _inputState.value = ChatContract.InputState(text = action.text)
            }
            ChatContract.Action.SendMessage -> sendMessage()
            ChatContract.Action.LoadMore -> loadMore()
        }
    }

    fun loadConversation(conversationId: String) {
        scope.launch {
            _messagesState.value = _messagesState.value.copy(isLoading = true)
            try {
                val account = accountService.currentAccount.value ?: return@launch
                currentAccountId = account.accountId
                currentConversationId = conversationId

                val conversationUri = Uri(Uri.SWARM_SCHEME, conversationId)
                val conversation = conversationFacade.getConversation(account.accountId, conversationUri)

                val title = conversation?.contact?.displayUsername ?: conversationId
                _topBarState.value = ChatContract.TopBarState(conversationTitle = title)

                if (conversation != null) {
                    conversationFacade.loadConversationHistory(conversation)
                }

                loadMessagesFromHistory()
            } catch (e: Exception) {
                _messagesState.value = _messagesState.value.copy(isLoading = false)
            }
        }
    }

    private fun sendMessage() {
        val text = _inputState.value.text.trim()
        if (text.isEmpty()) return

        scope.launch {
            val accountId = currentAccountId ?: return@launch
            val conversationId = currentConversationId ?: return@launch
            val conversationUri = Uri(Uri.SWARM_SCHEME, conversationId)

            accountService.sendConversationMessage(accountId, conversationUri, text)

            _inputState.value = ChatContract.InputState(text = "")
        }
    }

    private fun loadMore() {
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

    private fun loadMessagesFromHistory() {
        val accountId = currentAccountId ?: return
        val conversationId = currentConversationId ?: return
        val conversationUri = Uri(Uri.SWARM_SCHEME, conversationId)
        val conversation = conversationFacade.getConversation(accountId, conversationUri)

        if (conversation != null) {
            val history = conversation.getSortedHistory()

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
            _messagesState.value = ChatContract.MessagesState(messages = items, isLoading = false)
        } else {
            _messagesState.value = _messagesState.value.copy(isLoading = false)
        }
    }

    private fun appendMessage(event: ConversationEvent.MessageReceived) {
        val msg = event.message
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
        val current = _messagesState.value.messages
        _messagesState.value = ChatContract.MessagesState(messages = current + item)
    }

    fun onCleared() {
        scope.cancel()
    }
}
