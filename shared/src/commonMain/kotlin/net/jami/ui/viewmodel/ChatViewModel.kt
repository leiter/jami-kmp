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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import net.jami.services.AccountEvent
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import net.jami.model.CallHistory
import net.jami.model.Contact
import net.jami.model.ContactEvent
import net.jami.model.ContactLocation
import net.jami.model.DataTransfer
import net.jami.model.Interaction
import net.jami.model.Uri
import net.jami.repository.DraftRepository
import net.jami.services.AccountService
import net.jami.services.ConversationFacade
import net.jami.services.ConversationEvent
import net.jami.services.DeviceRuntimeService
import net.jami.services.LocationUpdate
import net.jami.utils.Log
import net.jami.utils.VCardUtils

/**
 * Type of message content.
 */
enum class MessageType {
    Text,
    System,
    Call,
    Transfer,
    DateSeparator
}

/**
 * Item representing a single message or list decoration in the chat.
 *
 * All timestamps are in milliseconds.
 */
data class MessageItem(
    val id: String,
    val text: String,
    val author: String,
    val timestamp: Long,
    val isOutgoing: Boolean,
    val type: MessageType = MessageType.Text,
    // Call-specific: filled when type == Call
    val isMissed: Boolean = false,
    val callDuration: Long = 0L,        // milliseconds; 0 = missed/unknown
    // Contact event: filled when type == System; resolved to localised string in composable
    val contactEventType: ContactEvent.Event? = null,
    // Transfer-specific: filled when type == Transfer
    val transferStatus: Interaction.TransferStatus = Interaction.TransferStatus.INVALID,
    val totalSize: Long = 0L,
    val bytesProgress: Long = 0L,
    val fileId: String? = null,
    val isPicture: Boolean = false,
    val isAudio: Boolean = false,
    val isVideo: Boolean = false,
    /** Local path of the downloaded file; non-null only when TRANSFER_FINISHED. */
    val destinationPath: String? = null,
)

/**
 * Information about a contact's shared location.
 */
data class ContactSharingInfo(
    val displayName: String,
    val location: ContactLocation,
)

/**
 * State for the chat / conversation detail screen.
 */
data class ChatState(
    val messages: List<MessageItem> = emptyList(),
    val inputText: String = "",
    val conversationTitle: String = "",
    val contactAvatarBytes: ByteArray? = null,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMoreHistory: Boolean = true,
    val isContactTyping: Boolean = false,
    val isContactOnline: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<MessageItem> = emptyList(),
    val isSearchActive: Boolean = false,
    /** Message to scroll to and briefly highlight after closing search. */
    val highlightedMessageId: String? = null,
    /** Whether a contact in this conversation is sharing their location. */
    val contactSharingLocation: ContactSharingInfo? = null,
    /**
     * The peer's ring ID URI (e.g. "ring:abc123…") for 1-to-1 conversations.
     * Empty for group conversations. Used as the call target instead of the
     * conversation ID — passing a conversation ID to placeCallWithMedia causes
     * "Found 0 device(s)" because the daemon treats it as an unknown ring ID.
     */
    val peerUri: String = "",
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
    private val deviceRuntimeService: DeviceRuntimeService,
    private val draftRepository: DraftRepository,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val scope = scope

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private var currentConversationId: String? = null
    private var currentAccountId: String? = null
    private var searchJob: kotlinx.coroutines.Job? = null
    private var presenceJob: kotlinx.coroutines.Job? = null

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
                    is ConversationEvent.DataTransferEvent -> {
                        if (event.conversationId == convId) {
                            loadMessagesFromHistory()
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

                // Observe drafts for this account
                draftRepository.observeAccount(account.accountId)

                val conversationUri = Uri(Uri.SWARM_SCHEME, conversationId)
                val conversation = conversationFacade.getConversation(account.accountId, conversationUri)

                val title = conversation?.contact?.displayUsername ?: conversationId
                val avatarBytes = conversation?.contact?.let { contact ->
                    VCardUtils.loadPeerProfileFromDisk(
                        filesDir = deviceRuntimeService.getDataPath(),
                        accountId = account.accountId,
                        peerUri = contact.uri.rawRingId
                    )
                }
                val peerUri = conversation?.contact?.uri?.uri ?: ""
                Log.d(TAG, "loadConversation: id=$conversationId contact=${conversation?.contact?.uri} peerUri=$peerUri isGroup=${conversation?.isGroup}")
                _state.value = _state.value.copy(
                    conversationTitle = title,
                    contactAvatarBytes = avatarBytes,
                    hasMoreHistory = true,
                    isLoadingMore = false,
                    peerUri = peerUri,
                )

                // Mark conversation as visible and read all pending messages.
                // Mirrors Android ConversationPresenter.resume().
                if (conversation != null) {
                    conversation.isVisible = true
                    conversationFacade.loadConversationHistory(conversation)
                    conversationFacade.readMessages(account, conversation, cancelNotification = true)
                }

                // Load saved draft for this conversation
                val draft = draftRepository.getDraft(conversationId)
                if (draft != null && draft.text.isNotEmpty()) {
                    _state.value = _state.value.copy(inputText = draft.text)
                }

                loadMessagesFromHistory()

                // Subscribe to presence updates for the contact
                conversation?.contact?.let { contact ->
                    subscribeToPresenceUpdates(contact)
                }

                // Subscribe to location updates for this conversation
                subscribeToLocationUpdates(account.accountId, conversationId)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    /**
     * Subscribe to location updates for the current conversation.
     */
    private fun subscribeToLocationUpdates(accountId: String, conversationId: String) {
        scope.launch {
            // Load any existing contact locations
            val existingLocations = accountService.getContactLocations(accountId, conversationId)
            if (existingLocations.isNotEmpty()) {
                // Get the first contact's location (for 1-to-1 conversations)
                val (contactUri, location) = existingLocations.entries.first()
                val account = accountService.getAccount(accountId) ?: return@launch
                val contact = account.getContactFromCache(Uri.fromId(contactUri))
                _state.value = _state.value.copy(
                    contactSharingLocation = ContactSharingInfo(contact.displayUsername, location)
                )
            }

            // Subscribe to location updates
            accountService.locationUpdates
                .filter { it.accountId == accountId && it.conversationId == conversationId }
                .collect { update ->
                    when (update) {
                        is LocationUpdate.Position -> {
                            val account = accountService.getAccount(accountId) ?: return@collect
                            val contact = account.getContactFromCache(Uri.fromId(update.contactUri))
                            _state.value = _state.value.copy(
                                contactSharingLocation = ContactSharingInfo(
                                    displayName = contact.displayUsername,
                                    location = update.location,
                                )
                            )
                        }
                        is LocationUpdate.Stop -> {
                            _state.value = _state.value.copy(contactSharingLocation = null)
                        }
                    }
                }
        }
    }

    /**
     * Subscribe to presence updates for the conversation contact.
     */
    private fun subscribeToPresenceUpdates(contact: Contact) {
        presenceJob?.cancel()
        presenceJob = scope.launch {
            contact.presenceStatus.collect { status ->
                val isOnline = status != Contact.PresenceStatus.OFFLINE
                _state.value = _state.value.copy(isContactOnline = isOnline)
            }
        }
    }

    /**
     * Send the current input text as a message.
     */
    fun sendMessage() {
        val accountId = currentAccountId ?: return
        val conversationId = currentConversationId ?: return
        val conversationUri = Uri(Uri.SWARM_SCHEME, conversationId)

        var textToSend: String = ""
        _state.update { current ->
            textToSend = current.inputText.trim()
            if (textToSend.isEmpty()) return@update current

            val optimisticId = "pending-${Clock.System.now().toEpochMilliseconds()}"
            val optimisticItem = MessageItem(
                id = optimisticId,
                text = textToSend,
                author = "",
                timestamp = Clock.System.now().toEpochMilliseconds(),
                isOutgoing = true,
                type = MessageType.Text,
            )
            current.copy(
                inputText = "",
                messages = current.messages + optimisticItem,
            )
        }

        if (textToSend.isEmpty()) return

        // Stop the typing indicator on the recipient side immediately.
        conversationFacade.setIsComposing(accountId, conversationUri, false)

        scope.launch {
            draftRepository.clearDraft(conversationId)
            accountService.sendConversationMessage(accountId, conversationUri, textToSend)
        }
    }

    /**
     * Send the default emoji (thumbs up) as a message.
     */
    fun sendEmoji() {
        scope.launch {
            val accountId = currentAccountId ?: return@launch
            val conversationId = currentConversationId ?: return@launch
            val conversationUri = Uri(Uri.SWARM_SCHEME, conversationId)

            // Send thumbs up emoji
            accountService.sendConversationMessage(accountId, conversationUri, "👍")
        }
    }

    /**
     * Check if camera permission is granted.
     */
    fun hasCameraPermission(): Boolean {
        return deviceRuntimeService.hasCameraPermission()
    }

    /**
     * Check if location permission is granted.
     */
    fun hasLocationPermission(): Boolean {
        return deviceRuntimeService.hasLocationPermission()
    }

    /**
     * Send an image file as a message.
     *
     * @param imagePath Absolute path to the image file.
     */
    fun sendImage(imagePath: String) {
        scope.launch {
            val accountId = currentAccountId ?: return@launch
            val conversationId = currentConversationId ?: return@launch
            val conversationUri = Uri(Uri.SWARM_SCHEME, conversationId)

            Log.i("ChatViewModel", "Sending image: $imagePath")

            // Get the conversation object
            val conversation = conversationFacade.getConversation(accountId, conversationUri)
            if (conversation != null) {
                conversationFacade.sendFile(conversation, conversationUri, imagePath)
            } else {
                Log.e("ChatViewModel", "Failed to send image: conversation not found")
            }
        }
    }

    /**
     * Send any file as a message.
     *
     * @param filePath Absolute path to the file.
     */
    fun sendFile(filePath: String) {
        scope.launch {
            val accountId = currentAccountId ?: return@launch
            val conversationId = currentConversationId ?: return@launch
            val conversationUri = Uri(Uri.SWARM_SCHEME, conversationId)

            Log.i("ChatViewModel", "Sending file: $filePath")

            // Get the conversation object
            val conversation = conversationFacade.getConversation(accountId, conversationUri)
            if (conversation != null) {
                conversationFacade.sendFile(conversation, conversationUri, filePath)
            } else {
                Log.e("ChatViewModel", "Failed to send file: conversation not found")
            }
        }
    }

    /**
     * Handle taking a picture with the camera.
     * This requires platform-specific implementation.
     */
    fun takePicture() {
        // Check camera permission
        if (!deviceRuntimeService.hasCameraPermission()) {
            // TODO: Request camera permission through platform-specific mechanism
            Log.w("ChatViewModel", "Camera permission not granted")
            return
        }

        // TODO: Platform-specific camera implementation
        // Android: Launch ACTION_IMAGE_CAPTURE intent
        // iOS: Use UIImagePickerController or Camera API
        // For now, this is a placeholder that demonstrates the flow
        Log.i("ChatViewModel", "takePicture called - platform-specific implementation needed")

        // Once image is captured, it should be sent via:
        // scope.launch {
        //     val accountId = currentAccountId ?: return@launch
        //     val conversationId = currentConversationId ?: return@launch
        //     val conversationUri = Uri(Uri.SWARM_SCHEME, conversationId)
        //     conversationFacade.sendFile(accountId, conversationUri, filePath)
        // }
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

        // Save draft (with debouncing handled by DraftRepository)
        draftRepository.updateDraft(conversationId, text)
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
     * Accept (download) an incoming file transfer.
     */
    fun acceptTransfer(messageId: String, fileId: String) {
        scope.launch {
            val accountId = currentAccountId ?: return@launch
            val conversationId = currentConversationId ?: return@launch
            val conversationUri = Uri(Uri.SWARM_SCHEME, conversationId)
            val conversation = conversationFacade.getConversation(accountId, conversationUri) ?: return@launch
            conversationFacade.acceptFileTransfer(conversation, messageId, fileId)
        }
    }

    /**
     * Cancel an ongoing or pending file transfer.
     */
    fun cancelTransfer(messageId: String, fileId: String) {
        scope.launch {
            val accountId = currentAccountId ?: return@launch
            val conversationId = currentConversationId ?: return@launch
            conversationFacade.cancelFileTransfer(
                accountId, Uri(Uri.SWARM_SCHEME, conversationId), messageId, fileId
            )
        }
    }

    /**
     * Clear the local history of the current conversation.
     */
    fun clearHistory() {
        scope.launch {
            val accountId = currentAccountId ?: return@launch
            val conversationId = currentConversationId ?: return@launch
            val conversationUri = Uri(Uri.SWARM_SCHEME, conversationId)
            val conversation = conversationFacade.getConversation(accountId, conversationUri) ?: return@launch
            conversation.clearHistory(delete = false)
            _state.value = _state.value.copy(messages = emptyList())
        }
    }

    /**
     * Load more (older) messages for the current conversation.
     * Called when user scrolls to the top of the message list.
     */
    fun loadMore() {
        val currentState = _state.value
        Log.d(TAG, "loadMore: isLoadingMore=${currentState.isLoadingMore} hasMoreHistory=${currentState.hasMoreHistory}")
        if (currentState.isLoadingMore || !currentState.hasMoreHistory) return

        scope.launch {
            val accountId = currentAccountId ?: return@launch
            val conversationId = currentConversationId ?: return@launch
            val conversationUri = Uri(Uri.SWARM_SCHEME, conversationId)
            val conversation = conversationFacade.getConversation(accountId, conversationUri)
            if (conversation != null) {
                val previousCount = _state.value.messages.size
                _state.value = _state.value.copy(isLoadingMore = true)
                try {
                    conversationFacade.loadConversationHistory(conversation)
                    loadMessagesFromHistory()
                    val newCount = _state.value.messages.size
                    val hasMore = newCount > previousCount
                    Log.d(TAG, "loadMore: previous=$previousCount new=$newCount hasMore=$hasMore")
                    _state.value = _state.value.copy(
                        isLoadingMore = false,
                        hasMoreHistory = hasMore
                    )
                } catch (e: Exception) {
                    _state.value = _state.value.copy(isLoadingMore = false)
                    Log.w(TAG, "Failed to load more history: ${e.message}")
                }
            } else {
                _state.value = _state.value.copy(isLoadingMore = false, hasMoreHistory = false)
            }
        }
    }

    /**
     * Reload messages from the conversation's in-memory history.
     * Converts Interaction objects to MessageItems with proper type mapping,
     * then injects date separator items at day boundaries.
     */
    private fun loadMessagesFromHistory() {
        val accountId = currentAccountId ?: return
        val conversationId = currentConversationId ?: return
        val conversationUri = Uri(Uri.SWARM_SCHEME, conversationId)
        val conversation = conversationFacade.getConversation(accountId, conversationUri)

        if (conversation != null) {
            val history = conversation.getSortedHistory()
            val items = history.mapNotNull { interaction ->
                interactionToMessageItem(interaction)
            }
            val withSeparators = injectDateSeparators(items)
            _state.value = _state.value.copy(messages = withSeparators, isLoading = false)
        } else {
            _state.value = _state.value.copy(isLoading = false)
        }
    }

    /**
     * Convert a single Interaction to a MessageItem, dispatching on interaction type.
     * Returns null for INVALID interactions (they should not be displayed).
     */
    private fun interactionToMessageItem(interaction: Interaction): MessageItem? {
        val id = interaction.messageId ?: interaction.id.toString()
        // Use contact's display name/username instead of raw Jami ID
        val author = interaction.contact?.displayUsername ?: interaction.author ?: ""
        val timestamp = interaction.timestamp  // already milliseconds
        val isOutgoing = interaction.author == null || interaction.contact?.isUser == true

        return when (interaction.type) {
            Interaction.InteractionType.TEXT -> MessageItem(
                id = id, text = interaction.body ?: "", author = author,
                timestamp = timestamp, isOutgoing = isOutgoing, type = MessageType.Text
            )
            Interaction.InteractionType.CALL -> {
                val call = interaction as? CallHistory
                MessageItem(
                    id = id, text = "", author = author,
                    timestamp = timestamp, isOutgoing = isOutgoing, type = MessageType.Call,
                    isMissed = call?.isMissed ?: true,
                    callDuration = call?.duration ?: 0L
                )
            }
            Interaction.InteractionType.CONTACT -> {
                val event = interaction as? ContactEvent
                MessageItem(
                    id = id, text = "", author = author,
                    timestamp = timestamp, isOutgoing = false, type = MessageType.System,
                    contactEventType = event?.event
                )
            }
            Interaction.InteractionType.DATA_TRANSFER -> {
                val transfer = interaction as? DataTransfer
                MessageItem(
                    id = id, text = interaction.body ?: "", author = author,
                    timestamp = timestamp, isOutgoing = isOutgoing, type = MessageType.Transfer,
                    transferStatus = transfer?.transferStatus ?: Interaction.TransferStatus.INVALID,
                    totalSize = transfer?.totalSize ?: 0L,
                    bytesProgress = transfer?.bytesProgress ?: 0L,
                    fileId = transfer?.fileId,
                    isPicture = transfer?.isPicture ?: false,
                    isAudio = transfer?.isAudio ?: false,
                    isVideo = transfer?.isVideo ?: false,
                    destinationPath = transfer?.destinationPath,
                )
            }
            Interaction.InteractionType.INVALID -> null
        }
    }

    /**
     * Insert DateSeparator items between messages that fall on different calendar days.
     * Separators are injected before the first message of each new day.
     */
    private fun injectDateSeparators(items: List<MessageItem>): List<MessageItem> {
        if (items.isEmpty()) return items
        val result = mutableListOf<MessageItem>()
        val tz = TimeZone.currentSystemDefault()
        var lastDate: LocalDate? = null
        for (item in items) {
            if (item.type == MessageType.DateSeparator) { result.add(item); continue }
            val itemDate = Instant.fromEpochMilliseconds(item.timestamp).toLocalDateTime(tz).date
            if (itemDate != lastDate) {
                result.add(
                    MessageItem(
                        id = "date_${item.timestamp}",
                        text = epochMillisToDateLabel(item.timestamp),
                        author = "", timestamp = item.timestamp,
                        isOutgoing = false, type = MessageType.DateSeparator
                    )
                )
                lastDate = itemDate
            }
            result.add(item)
        }
        return result
    }

    private fun epochMillisToDateLabel(timestamp: Long): String {
        val tz = TimeZone.currentSystemDefault()
        val date = Instant.fromEpochMilliseconds(timestamp).toLocalDateTime(tz).date
        val today = Clock.System.now().toLocalDateTime(tz).date
        val yesterday = today.minus(DatePeriod(days = 1))
        return when (date) {
            today     -> DATE_TODAY
            yesterday -> DATE_YESTERDAY
            else      -> "${date.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${date.dayOfMonth}, ${date.year}"
        }
    }

    /**
     * Resolve author ID (Jami URI) to display name using conversation contacts.
     */
    private fun resolveAuthorDisplayName(authorId: String): String {
        val accountId = currentAccountId ?: return authorId
        val conversationId = currentConversationId ?: return authorId

        val conversationUri = Uri(Uri.SWARM_SCHEME, conversationId)
        val conversation = conversationFacade.getConversation(accountId, conversationUri) ?: return authorId

        val contact = conversation.findContact(Uri.fromString(authorId))
        return contact?.displayUsername ?: authorId
    }

    /**
     * Append a newly received message directly to the state for immediate display.
     * The model is also updated by ConversationFacade.onMessageReceived() so that
     * a subsequent loadMessagesFromHistory() will include this message too.
     *
     * Note: SwarmMessage.timestamp is in seconds; we convert to milliseconds here.
     */
    private fun appendMessage(event: ConversationEvent.MessageReceived) {
        val msg = event.message
        val current = _state.value.messages

        // Drop duplicate: the message was already shown optimistically or via a prior callback.
        if (current.any { it.id == msg.id }) return

        val timestampMs = msg.timestamp * 1000L
        val displayName = resolveAuthorDisplayName(msg.author)

        // Determine outgoing by comparing the message author's ring ID against the
        // current account's Jami ID (account.username == the public-key hash).
        val accountUsername = currentAccountId
            ?.let { accountService.getAccount(it)?.username }
            ?: ""
        val isOutgoing = accountUsername.isNotEmpty() &&
            Uri.fromString(msg.author).rawRingId == accountUsername

        // For outgoing messages: remove the optimistic placeholder (pending-*) that has the
        // same text and was added right before the daemon call, replacing it with the real
        // daemon-assigned ID now that the echo has arrived.
        val withoutOptimistic = if (isOutgoing) {
            current.filterNot { it.id.startsWith("pending-") && it.text == msg.textContent }
        } else {
            current
        }

        val item = when {
            msg.isCall -> {
                val durationMs = (msg.body["duration"]?.toLongOrNull() ?: 0L) * 1000L
                MessageItem(
                    id = msg.id, text = "", author = displayName,
                    timestamp = timestampMs, isOutgoing = isOutgoing,
                    type = MessageType.Call,
                    isMissed = durationMs == 0L,
                    callDuration = durationMs
                )
            }
            msg.isMember -> {
                val action = msg.body["action"] ?: ""
                MessageItem(
                    id = msg.id, text = "", author = displayName,
                    timestamp = timestampMs, isOutgoing = isOutgoing,
                    type = MessageType.System,
                    contactEventType = ContactEvent.Event.fromConversationAction(action)
                )
            }
            msg.type == "initial" -> MessageItem(
                id = msg.id, text = "", author = displayName,
                timestamp = timestampMs, isOutgoing = false,
                type = MessageType.System,
                contactEventType = ContactEvent.Event.INVITED
            )
            else -> MessageItem(
                id = msg.id,
                text = msg.textContent,
                author = displayName,
                timestamp = timestampMs,
                isOutgoing = isOutgoing,
                type = if (msg.isText) MessageType.Text else MessageType.System
            )
        }
        _state.value = _state.value.copy(messages = withoutOptimistic + item)
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
     * Activate search mode without sending a query yet.
     * Called when the user taps the Search menu item.
     */
    fun openSearch() {
        _state.value = _state.value.copy(
            isSearchActive = true,
            searchQuery = "",
            searchResults = emptyList(),
        )
    }

    /**
     * Search for messages in the current conversation.
     * Debounces input to avoid excessive daemon calls.
     */
    fun searchConversation(query: String) {
        _state.value = _state.value.copy(searchQuery = query, isSearchActive = true)

        // Cancel previous search
        searchJob?.cancel()

        if (query.isBlank()) {
            _state.value = _state.value.copy(searchResults = emptyList())
            return
        }

        searchJob = scope.launch {
            // Debounce: wait before triggering search
            kotlinx.coroutines.delay(SEARCH_DEBOUNCE_MS)

            val accountId = currentAccountId ?: return@launch
            val conversationId = currentConversationId ?: return@launch
            val conversationUri = Uri(Uri.SWARM_SCHEME, conversationId)
            accountService.searchConversation(accountId, conversationUri, query.trim())
        }
    }

    /**
     * Close search and scroll the main message list to the given message,
     * briefly highlighting it.
     */
    fun scrollToMessage(messageId: String) {
        closeSearch()
        _state.value = _state.value.copy(highlightedMessageId = messageId)
        scope.launch {
            kotlinx.coroutines.delay(1500)
            _state.value = _state.value.copy(highlightedMessageId = null)
        }
    }

    /**
     * Close the search UI and clear results.
     */
    fun closeSearch() {
        _state.value = _state.value.copy(
            searchQuery = "",
            searchResults = emptyList(),
            isSearchActive = false,
        )
    }

    /**
     * Handle search results from the daemon.
     * Daemon timestamps are in seconds — convert to milliseconds.
     */
    private fun handleSearchResults(messages: List<Map<String, String>>) {
        val myId = currentAccountId ?: ""
        val items = messages.mapNotNull { msg ->
            val body = msg["body"] ?: return@mapNotNull null
            if (body.isEmpty()) return@mapNotNull null
            val author = msg["author"] ?: ""
            MessageItem(
                id = msg["id"] ?: "",
                text = body,
                author = author,
                timestamp = (msg["timestamp"]?.toLongOrNull() ?: 0L) * 1000L,
                isOutgoing = author.isEmpty() || author == myId,
                type = MessageType.Text
            )
        }
        _state.value = _state.value.copy(searchResults = items)
    }

    /**
     * Called when the user leaves the chat screen (navigates back).
     * Mirrors Android ConversationPresenter.pause().
     */
    fun onLeave() {
        val accountId = currentAccountId ?: return
        val conversationId = currentConversationId ?: return
        scope.launch {
            val account = accountService.currentAccount.value ?: return@launch
            val conversationUri = Uri(Uri.SWARM_SCHEME, conversationId)
            val conversation = conversationFacade.getConversation(accountId, conversationUri)
            conversation?.isVisible = false
        }
    }

    /**
     * Cancel the coroutine scope when this ViewModel is no longer needed.
     */
    fun onCleared() {
        onLeave()
        scope.cancel()
    }

    companion object {
        private const val TAG = "ChatViewModel"
        private const val DATE_TODAY     = "Today"
        private const val DATE_YESTERDAY = "Yesterday"
        private const val SEARCH_DEBOUNCE_MS = 300L
    }
}
