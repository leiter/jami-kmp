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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import net.jami.model.Contact
import net.jami.model.TextMessage
import net.jami.model.Uri
import net.jami.services.AccountEvent
import net.jami.services.AccountService
import net.jami.services.ConversationFacade
import net.jami.services.ConversationEvent
import net.jami.services.DeviceRuntimeService
import net.jami.utils.VCardUtils

/**
 * Item representing a conversation in the list.
 */
data class ConversationItem(
    val id: String,
    val displayName: String,
    val lastMessage: String,
    val timestamp: Long,
    val unreadCount: Int,
    val avatarBytes: ByteArray?,
    val isOnline: Boolean
)

/**
 * State for the conversations list screen.
 */
data class ConversationsState(
    val conversations: List<ConversationItem> = emptyList(),
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val pendingRequests: Int = 0,
    val currentAccountAvatarBytes: ByteArray? = null
)

/**
 * ViewModel for the conversations list screen.
 *
 * Observes account changes and conversation events to keep the conversation
 * list up to date. Supports search filtering and pull-to-refresh.
 */
class ConversationsViewModel(
    private val accountService: AccountService,
    private val conversationFacade: ConversationFacade,
    private val deviceRuntimeService: DeviceRuntimeService,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val scope = scope

    private val _state = MutableStateFlow(ConversationsState())
    val state: StateFlow<ConversationsState> = _state.asStateFlow()

    init {
        // Observe current account changes and reload conversations
        scope.launch {
            accountService.currentAccount.filterNotNull().collect { account ->
                loadConversations()
            }
        }

        // Observe conversation events to refresh the list
        scope.launch {
            conversationFacade.conversationEvents.collect { event ->
                when (event) {
                    is ConversationEvent.MessageReceived,
                    is ConversationEvent.MessageUpdated,
                    is ConversationEvent.MessageStatusChanged,
                    is ConversationEvent.ConversationReady,
                    is ConversationEvent.ConversationRemoved,
                    is ConversationEvent.ConversationRequestReceived -> {
                        loadConversations()
                    }
                    else -> { /* Other events don't require list refresh */ }
                }
            }
        }

        // Own account profile received from another device.
        // Fast path: decode base64 photo from the event payload.
        // Slow path: reload conversations (reads updated profile.vcf the daemon wrote to disk).
        scope.launch {
            accountService.accountEvents.collect { event ->
                if (event is AccountEvent.ProfileReceived) {
                    if (event.photo.isNotEmpty()) {
                        val avatarBytes = decodeProfilePhoto(event.photo)
                        if (avatarBytes != null) {
                            _state.value = _state.value.copy(currentAccountAvatarBytes = avatarBytes)
                        }
                    }
                    // Also reload from disk — the daemon writes profile.vcf before firing this callback.
                    loadConversations()
                }
            }
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun decodeProfilePhoto(photo: String): ByteArray? = try {
        Base64.decode(photo.replace("\\s".toRegex(), ""))
    } catch (e: Exception) {
        null
    }

    /**
     * Load conversations for the current account.
     */
    fun loadConversations() {
        scope.launch {
            _state.value = _state.value.copy(isLoading = true)
            try {
                val account = accountService.currentAccount.value ?: return@launch
                val accountId = account.accountId
                val query = _state.value.searchQuery.lowercase()

                // Get conversation requests count
                val requests = accountService.getConversationRequests(accountId)
                val pendingCount = requests.size

                // Load current account avatar from local VCard
                val filesDir = deviceRuntimeService.getDataPath()
                val accountAvatarBytes = VCardUtils.loadLocalProfileFromDisk(filesDir, accountId)

                // Build conversation items from the facade
                val conversations = buildConversationItems(accountId, query, filesDir)

                _state.value = _state.value.copy(
                    conversations = conversations,
                    isLoading = false,
                    pendingRequests = pendingCount,
                    currentAccountAvatarBytes = accountAvatarBytes ?: _state.value.currentAccountAvatarBytes
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    /**
     * Search conversations by query string.
     */
    fun search(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
        loadConversations()
    }

    /**
     * Pull-to-refresh: reload conversations from daemon.
     */
    fun refresh() {
        loadConversations()
    }

    /**
     * Remove a conversation by its ID.
     */
    fun removeConversation(conversationId: String) {
        scope.launch {
            val accountId = accountService.currentAccount.value?.accountId ?: return@launch
            val conversationUri = Uri(Uri.SWARM_SCHEME, conversationId)
            conversationFacade.removeConversation(accountId, conversationUri)
            loadConversations()
        }
    }

    /**
     * Build conversation item list from the current account.
     */
    private fun buildConversationItems(accountId: String, query: String, filesDir: String): List<ConversationItem> {
        val account = accountService.getAccount(accountId) ?: return emptyList()
        val conversations = account.getConversations()

        return conversations.mapNotNull { conversation ->
            val contact = conversation.contact
            val displayName = conversation.profileFlow.value.displayName?.takeIf { it.isNotBlank() }
                ?: contact?.displayUsername
                ?: conversation.uri.rawRingId

            // Filter by query
            if (query.isNotEmpty() && !displayName.lowercase().contains(query)) {
                return@mapNotNull null
            }

            val lastEvent = conversation.lastEvent
            val lastMessage = if (lastEvent is TextMessage) lastEvent.body ?: "" else ""
            val timestamp = lastEvent?.timestamp ?: 0L

            // Load contact avatar from peer VCard stored by the daemon
            val avatarBytes = contact?.let { c ->
                VCardUtils.loadPeerProfileFromDisk(filesDir, accountId, c.uri.rawRingId)
            }

            ConversationItem(
                id = conversation.uri.rawRingId,
                displayName = displayName,
                lastMessage = lastMessage,
                timestamp = timestamp,
                unreadCount = 0,
                avatarBytes = avatarBytes,
                isOnline = contact?.isOnline == true
            )
        }.sortedByDescending { it.timestamp }
    }

    /**
     * Cancel the coroutine scope when this ViewModel is no longer needed.
     */
    fun onCleared() {
        scope.cancel()
    }
}
