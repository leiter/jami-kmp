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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import net.jami.model.Account
import net.jami.model.Call
import net.jami.model.Call.CallStatus
import net.jami.model.CallHistory
import net.jami.model.Conference
import net.jami.model.Contact
import net.jami.model.ContactViewModel
import net.jami.model.Conversation
import net.jami.model.MemberRole
import net.jami.model.DataTransfer
import net.jami.model.Interaction
import net.jami.model.Media
import net.jami.model.Profile
import net.jami.model.SwarmMessage
import net.jami.model.TextMessage
import net.jami.model.Uri
import net.jami.utils.Log
import net.jami.utils.currentTimeMillis

/**
 * Facade for conversation operations, coordinating between multiple services.
 *
 * This class handles:
 * - Message sending and receiving
 * - File transfers
 * - Conversation history loading
 * - Call state management
 * - Notifications
 *
 * Ported from: jami-client-android libjamiclient
 * Changes:
 * - RxJava Observable/Single/Completable → Kotlin Flow/suspend functions
 * - CompositeDisposable → CoroutineScope
 * - Schedulers → Dispatchers
 */
class ConversationFacade(
    private val historyService: HistoryService,
    private val callService: CallService,
    private val accountService: AccountService,
    private val contactService: ContactService,
    private val notificationService: NotificationService,
    private val hardwareService: HardwareService,
    private val deviceRuntimeService: DeviceRuntimeService,
    private val preferencesService: PreferencesService,
    private val daemonBridge: DaemonBridgeApi,
    private val scope: CoroutineScope
) {
    private val _currentAccount = MutableStateFlow<Account?>(null)
    val currentAccountFlow: StateFlow<Account?> = _currentAccount.asStateFlow()

    private val _conversationEvents = MutableSharedFlow<ConversationEvent>()
    val conversationEvents: SharedFlow<ConversationEvent> = _conversationEvents.asSharedFlow()

    private val _conversationList = MutableStateFlow(ConversationList())
    val conversationList: StateFlow<ConversationList> = _conversationList.asStateFlow()

    init {
        // Subscribe to account changes
        scope.launch {
            accountService.currentAccount.collect { account ->
                if (account != null) {
                    loadSmartlist(account)
                }
                _currentAccount.value = account
            }
        }

        // Subscribe to call updates
        scope.launch {
            callService.callUpdates.collect { call ->
                onCallStateChange(call)
            }
        }

        // Subscribe to conference updates
        scope.launch {
            callService.conferenceUpdates.collect { conference ->
                onConfStateChange(conference)
            }
        }
    }

    // ==================== Conversation Preferences ====================

    /**
     * Set conversation preferences.
     * For swarm conversations, sends to daemon to sync across devices.
     * For legacy conversations, saves locally.
     */
    fun setConversationPreferences(
        accountId: String,
        conversationUri: Uri,
        preferences: Map<String, String>
    ) {
        // For swarm conversations, we would send to daemon
        // For now, just save locally
        preferencesService.setConversationPreferences(accountId, conversationUri, preferences)
    }

    // ==================== Conversation Access ====================

    /**
     * Get a conversation by account ID and URI.
     */
    fun getConversation(accountId: String, conversationUri: Uri): Conversation? {
        val account = accountService.getAccount(accountId)
        return findConversation(account, conversationUri)
    }

    /**
     * Start or get an existing conversation.
     */
    suspend fun startConversation(accountId: String, contactUri: Uri): Conversation {
        val account = getAccountWithSmartlist(accountId)
        return findConversation(account, contactUri)
            ?: throw IllegalStateException("Conversation not found for $contactUri")
    }

    /**
     * Get an account with its smartlist loaded.
     */
    suspend fun getAccountWithSmartlist(accountId: String): Account {
        val account = accountService.getAccount(accountId)
            ?: throw IllegalArgumentException("Account not found: $accountId")
        loadSmartlist(account)
        return account
    }

    // ==================== Message Operations ====================

    /**
     * Mark a message as notified.
     */
    fun messageNotified(accountId: String, conversationUri: Uri, messageId: String) {
        val conversation = getConversation(accountId, conversationUri) ?: return
        conversation.getMessage(messageId)?.let { message ->
            message.isNotified = true
        }
        historyService.setMessageNotified(accountId, conversationUri, messageId)
    }

    /**
     * Mark messages as read.
     */
    suspend fun readMessages(accountId: String, contactUri: Uri): String? {
        val account = accountService.getAccount(accountId) ?: return null
        val conversation = findConversation(account, contactUri) ?: return null
        return readMessages(account, conversation, true)
    }

    /**
     * Mark messages as read for a conversation.
     */
    suspend fun readMessages(
        account: Account,
        conversation: Conversation,
        cancelNotification: Boolean
    ): String? {
        val lastMessage = readMessagesInternal(conversation) ?: return null

        // Mark the message as read (daemon will handle read receipts)
        accountService.setMessageDisplayed(account.accountId, conversation.uri, lastMessage)

        if (cancelNotification) {
            notificationService.cancelTextNotification(account.accountId, conversation.uri)
        }
        return lastMessage
    }

    private suspend fun readMessagesInternal(conversation: Conversation): String? {
        val messages = conversation.readMessages()
        var lastRead: String? = null
        for (message in messages) {
            if (conversation.isSwarm) {
                historyService.setMessageNotified(conversation.accountId, conversation.uri, message.messageId!!)
                lastRead = message.messageId
            } else {
                val did = message.daemonId
                if (lastRead == null && did != null && did != 0L) {
                    lastRead = did.toString(16)
                }
                historyService.updateInteraction(message, conversation.accountId)
            }
        }
        return lastRead
    }

    /**
     * Send a text message to a conversation.
     */
    suspend fun sendTextMessage(
        conversation: Conversation,
        to: Uri,
        text: String,
        replyTo: String? = null
    ) {
        if (conversation.isSwarm) {
            accountService.sendConversationMessage(conversation.accountId, conversation.uri, text, replyTo)
            return
        }

        // For legacy: send via daemon and store locally
        // val id = mCallService.sendAccountTextMessage(conversation.accountId, to.rawUriString, text)
        val message = TextMessage(
            author = null,
            account = conversation.accountId,
            daemonId = null,
            conversation = conversation,
            message = text
        )
        if (conversation.isVisible) message.read()
        historyService.insertInteraction(conversation.accountId, conversation, message)
        conversation.addElement(message)
    }

    /**
     * Send a text message in a conference call.
     */
    suspend fun sendTextMessage(conversation: Conversation, conf: Conference, text: String) {
        callService.sendTextMessage(conf.accountId, conf.id, text)
        val message = TextMessage(
            author = null,
            account = conf.accountId,
            daemonId = conf.id,
            conversation = conversation,
            message = text
        )
        message.read()
        historyService.insertInteraction(conversation.accountId, conversation, message)
        conversation.addElement(message)
    }

    /**
     * Set composing status for a conversation.
     */
    fun setIsComposing(accountId: String, conversationUri: Uri, isComposing: Boolean) {
        callService.setIsComposing(accountId, conversationUri.uri, isComposing)
    }

    // ==================== File Transfer Operations ====================

    /**
     * Send a file to a conversation.
     */
    suspend fun sendFile(conversation: Conversation, to: Uri, filePath: String) {
        if (!deviceRuntimeService.fileExists(filePath)) {
            throw IllegalArgumentException("File not found or not readable: $filePath")
        }

        if (conversation.isSwarm) {
            val fileName = filePath.substringAfterLast('/')
            val destPath = deviceRuntimeService.getNewConversationPath(
                conversation.accountId,
                conversation.uri.rawRingId,
                fileName
            )
            net.jami.utils.FileUtils.copyFile(filePath, destPath)
            accountService.sendFile(conversation.accountId, conversation.uri.rawRingId, destPath, fileName)
        }
    }

    /**
     * Delete a conversation file.
     */
    suspend fun deleteConversationFile(conversation: Conversation, transfer: DataTransfer) {
        if (transfer.transferStatus == Interaction.TransferStatus.TRANSFER_ONGOING) {
            accountService.cancelDataTransfer(
                conversation.accountId,
                conversation.uri.rawRingId,
                transfer.fileId ?: return
            )
        } else {
            val path = deviceRuntimeService.getConversationPath(
                conversation.accountId,
                conversation.uri.rawRingId,
                transfer.storagePath
            )
            if (conversation.isSwarm) {
                try {
                    deviceRuntimeService.deleteFile(path)
                    transfer.bytesProgress = 0
                    transfer.transferStatus = Interaction.TransferStatus.FILE_AVAILABLE
                    conversation.updateInteraction(transfer)
                } catch (e: Exception) {
                    Log.e(TAG, "Can't delete file", e)
                }
            }
        }
    }

    /**
     * Delete a conversation item (message or file).
     */
    suspend fun deleteConversationItem(conversation: Conversation, element: Interaction) {
        if (conversation.isSwarm) {
            if (element is DataTransfer) {
                if (element.transferStatus == Interaction.TransferStatus.TRANSFER_ONGOING) {
                    element.fileId?.let { fileId ->
                        accountService.cancelDataTransfer(conversation.accountId, conversation.uri.rawRingId, fileId)
                    }
                }
                // Delete actual file
                val path = deviceRuntimeService.getConversationPath(
                    conversation.accountId,
                    conversation.uri.rawRingId,
                    element.storagePath
                )
                try {
                    deviceRuntimeService.deleteFile(path)
                    element.bytesProgress = 0
                    element.transferStatus = Interaction.TransferStatus.FILE_REMOVED
                } catch (e: Exception) {
                    Log.e(TAG, "Can't delete file", e)
                }
            }
            accountService.deleteConversationMessage(conversation.accountId, conversation.uri, element.messageId!!)
        } else {
            try {
                historyService.deleteInteraction(element.id.toLong(), element.account!!)
                conversation.removeInteraction(element)
            } catch (e: Exception) {
                Log.e(TAG, "Can't delete message", e)
            }
        }
    }

    /**
     * Cancel a pending message.
     */
    suspend fun cancelMessage(conversation: Conversation, message: Interaction) {
        val accountId = message.account ?: return
        if (conversation.isSwarm) return

        try {
            callService.cancelMessage(accountId, message.id.toLong())
            conversation.removeInteraction(message)
        } catch (e: Exception) {
            Log.e(TAG, "Can't cancel message sending", e)
        }
    }

    /**
     * Cancel a file transfer.
     */
    fun cancelFileTransfer(accountId: String, conversationId: Uri, messageId: String?, fileId: String?) {
        if (fileId == null) return
        accountService.cancelDataTransfer(accountId, conversationId.rawRingId, fileId)
        notificationService.removeTransferNotification(accountId, conversationId, fileId)
    }

    /**
     * Accept/download an incoming file transfer.
     */
    fun acceptFileTransfer(conversation: Conversation, interactionId: String, fileId: String) {
        val destPath = deviceRuntimeService.getNewConversationPath(
            conversation.accountId,
            conversation.uri.rawRingId,
            fileId
        )
        accountService.downloadFile(
            conversation.accountId,
            conversation.uri.rawRingId,
            interactionId,
            fileId,
            destPath
        )
    }

    /**
     * Get file transfer progress.
     */
    fun getFileTransferProgress(accountId: String, conversationId: String, fileId: String): FileTransferInfo? {
        return accountService.fileTransferInfo(accountId, conversationId, fileId)
    }

    // ==================== Trust Request Operations ====================

    /**
     * Accept a trust/conversation request.
     */
    fun acceptRequest(conversation: Conversation) {
        if (conversation.mode == Conversation.Mode.Request) {
            scope.launch {
                conversation.clearHistory(true)
            }
            conversation.setMode(Conversation.Mode.Syncing)
        }
        acceptRequest(conversation.accountId, conversation.uri)
    }

    private fun acceptRequest(accountId: String, contactUri: Uri) {
        preferencesService.removeRequestPreferences(accountId, contactUri.rawRingId)
        accountService.acceptTrustRequest(accountId, contactUri)
    }

    /**
     * Discard a trust request.
     */
    fun discardRequest(accountId: String, contactUri: Uri) {
        preferencesService.removeRequestPreferences(accountId, contactUri.rawRingId)
        accountService.discardTrustRequest(accountId, contactUri)
    }

    // ==================== Conversation Management ====================

    /**
     * Remove a conversation.
     */
    suspend fun removeConversation(
        accountId: String,
        conversationUri: Uri,
        shouldClearConversation: Boolean = false
    ) {
        val account = accountService.getAccount(accountId)
            ?: throw IllegalArgumentException("Unknown account")

        if (conversationUri.isSwarm) {
            val conversation = findSwarmConversation(account, conversationUri.rawRingId)
            if (conversation != null && conversation.mode == Conversation.Mode.OneToOne) {
                if (!shouldClearConversation) {
                    accountService.removeContact(accountId, conversation.contact!!.uri.rawRingId, false)
                } else {
                    accountService.removeConversation(accountId, conversationUri)
                }
            } else {
                accountService.removeConversation(accountId, conversationUri)
            }
        } else {
            historyService.clearHistory(conversationUri.uri, accountId, true)
            accountService.removeContact(accountId, conversationUri.rawRingId, false)
        }
    }

    /**
     * Block a conversation.
     */
    suspend fun blockConversation(accountId: String, conversationUri: Uri) {
        if (conversationUri.isSwarm) {
            try {
                val conversation = startConversation(accountId, conversationUri)
                val contact = conversation.contact
                accountService.removeContact(accountId, contact!!.uri.rawRingId, true)
            } catch (e: Exception) {
                Log.e(TAG, "Error blocking conversation", e)
                accountService.removeConversation(accountId, conversationUri)
            }
        } else {
            accountService.removeContact(accountId, conversationUri.rawRingId, true)
        }
    }

    /**
     * Create a new group conversation.
     */
    suspend fun createConversation(accountId: String, contacts: Collection<Contact>): Conversation {
        val contactIds = contacts.map { it.primaryNumber }
        val conversationId = accountService.startConversation(accountId, contactIds)
        return startConversation(accountId, Uri(Uri.SWARM_SCHEME, conversationId))
    }

    /**
     * Clear conversation history.
     */
    suspend fun clearHistory(accountId: String, contactUri: Uri) {
        accountService.getAccount(accountId)?.let { account ->
            clearHistoryForAccount(account, contactUri)
        }
        historyService.clearHistory(contactUri.uri, accountId, false)
    }

    private fun clearHistoryForAccount(account: Account, contactUri: Uri) {
        // Clear history in the account's conversation cache
    }

    /**
     * Clear all history across all accounts.
     */
    suspend fun clearAllHistory() {
        val accounts = accountService.accounts.value
        historyService.clearHistory(accounts)
        for (account in accounts) {
            // Clear all conversations in each account
        }
    }

    // ==================== History Loading ====================

    /**
     * Load conversation history.
     */
    suspend fun loadConversationHistory(conversation: Conversation): Conversation {
        if ((!conversation.isSwarm && conversation.id == null) ||
            (conversation.isSwarm && conversation.mode == Conversation.Mode.Request)
        ) {
            return conversation
        }

        return if (conversation.isSwarm) {
            accountService.loadMore(conversation)
            conversation
        } else {
            getConversationHistory(conversation)
        }
    }

    private suspend fun getConversationHistory(conversation: Conversation): Conversation {
        val history = historyService.getConversationHistory(
            conversation.accountId,
            conversation.id!!.toLong()
        )
        conversation.clearHistory(true)
        conversation.setHistory(history)
        return conversation
    }

    /**
     * Load the smartlist (recent conversations) for an account.
     */
    private suspend fun loadSmartlist(account: Account) {
        if (account.isJami) {
            // Load swarm conversations from daemon
            val conversationIds = daemonBridge.getConversations(account.accountId)
            for (convId in conversationIds) {
                try {
                    val info = daemonBridge.getConversationInfo(account.accountId, convId)
                    val mode = when (info["mode"]) {
                        "0" -> Conversation.Mode.OneToOne
                        "1" -> Conversation.Mode.AdminInvitesOnly
                        "2" -> Conversation.Mode.InvitesOnly
                        "3" -> Conversation.Mode.Public
                        else -> Conversation.Mode.OneToOne
                    }
                    val conversation = account.getSwarm(convId)
                        ?: account.newSwarm(convId, mode)
                    conversation.setMode(mode)

                    // Load members
                    val members = daemonBridge.getConversationMembers(account.accountId, convId)
                    for (member in members) {
                        val memberUri = member["uri"] ?: continue
                        val memberUriParsed = Uri.fromString(memberUri)
                        if (conversation.findContact(memberUriParsed) == null) {
                            val contact = account.getContactFromCache(memberUriParsed)
                            val role = MemberRole.fromString(member["role"] ?: "")
                            conversation.addContact(contact, role)
                        }
                    }

                    // Set title if available
                    val title = info["title"]
                    if (!title.isNullOrEmpty()) {
                        conversation.setProfile(Profile(title, null))
                    }

                    account.conversationStarted(conversation)

                    // Subscribe to presence and resolve registered names for each contact
                    for (contact in conversation.contacts) {
                        if (!contact.isUser) {
                            contactService.subscribeBuddy(account.accountId, contact.uri, true)
                            if (contact.username.isNullOrEmpty()) {
                                accountService.lookupAddress(account.accountId, contact.uri.rawRingId)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "loadSmartlist: failed to load conversation $convId", e)
                }
            }
        } else {
            // Load history for non-swarm (SIP) conversations
            val interactions = historyService.getSmartlist(account.accountId)
            // Process interactions and update conversations
        }

        // Publish updated conversation list
        val conversations = account.getConversations().toList()
        _conversationList.value = ConversationList(conversations = conversations)
    }

    // ==================== Profile/Contact Operations ====================

    /**
     * Get loaded contacts for a conversation.
     */
    suspend fun getLoadedContact(
        accountId: String,
        conversation: Conversation?,
        contactIds: Collection<String>
    ): List<ContactViewModel> {
        val account = getAccountWithSmartlist(accountId)
        val contacts = contactIds.map { id ->
            conversation?.findContact(Uri.fromId(id))
                ?: getContactFromCache(account, Uri.fromId(id))
        }
        return contactService.observeContacts(accountId, contacts, false).first()
    }

    // ==================== Search ====================

    /**
     * Get search results for conversations.
     */
    fun getSearchResults(
        query: Flow<String>,
        currentAccount: Flow<Account> = currentAccountFlow.filterNotNull()
    ): Flow<ConversationList> {
        return currentAccount.flatMapLatest { account ->
            query.map { q ->
                val conversations = getConversationsForAccount(account)
                val filtered = if (q.isBlank()) {
                    emptyList()
                } else {
                    val lq = q.lowercase()
                    conversations.filter { conv ->
                        // Simple filter - full implementation would use contactService
                        conv.uri.uri.lowercase().contains(lq)
                    }
                }
                ConversationList(filtered, SearchResult(q, emptyList()), q)
            }
        }
    }

    /**
     * Get full conversation list with optional search.
     */
    fun getFullConversationList(
        currentAccount: Flow<Account>,
        query: Flow<String>,
        withBlocked: Boolean = false
    ): Flow<ConversationList> {
        return currentAccount.flatMapLatest { account ->
            query.map { q ->
                val conversations = getConversationsForAccount(account, withBlocked)
                val filtered = if (q.isBlank()) {
                    conversations
                } else {
                    val lq = q.lowercase()
                    conversations.filter { conv ->
                        conv.uri.uri.lowercase().contains(lq)
                    }
                }
                ConversationList(filtered, SearchResult.EMPTY_RESULT, q)
            }
        }
    }

    /**
     * Get conversation list for the current account.
     */
    fun getConversationList(currentAccount: Flow<Account>): Flow<ConversationList> {
        return currentAccount.map { account ->
            ConversationList(getConversationsForAccount(account))
        }
    }

    // ==================== Call State Handling ====================

    private suspend fun onCallStateChange(call: Call) {
        if (call.daemonId == null && call.confId == null) return

        val newState = call.callStatus
        val incomingCall = newState == CallStatus.RINGING && call.isIncoming
        val account = accountService.getAccount(call.account) ?: return
        val contact = call.contact
        val conversationUri = call.conversationUri

        val conversation = findConversationForCall(account, conversationUri, contact)
        val conference = findOrCreateConference(conversation, call, newState)

        hardwareService.updateAudioState(conference, call, incomingCall, call.hasVideo())

        if ((newState.isRinging || newState == CallStatus.CURRENT) && call.timestamp == 0L) {
            call.timestamp = currentTimeMillis()
        }

        when {
            incomingCall -> {
                notificationService.handleCallNotification(conference!!, false)
                hardwareService.setPreviewSettings()
            }
            newState == CallStatus.CURRENT || newState == CallStatus.RINGING -> {
                notificationService.handleCallNotification(conference!!, false)
            }
            newState.isOver -> {
                handleCallEnded(call, conference, conversation, account)
            }
        }
    }

    private suspend fun handleCallEnded(
        call: Call,
        conference: Conference?,
        conversation: Conversation?,
        account: Account
    ) {
        if (conference != null) {
            notificationService.handleCallNotification(conference, true)
        } else {
            notificationService.removeCallNotification()
        }
        hardwareService.closeAudioState()

        val now = currentTimeMillis()
        if (call.timestamp == 0L) call.timestamp = now
        if (call.timestampEnd == 0L) call.timestampEnd = now

        if (conference != null && conference.removeParticipant(call) && conversation != null && !conversation.isSwarm) {
            val callHistory = CallHistory(call)
            historyService.insertInteraction(account.accountId, conversation, callHistory)
            conversation.addCall(callHistory)

            if (call.isIncoming && call.isMissed) {
                notificationService.showMissedCallNotification(call)
            }
        }

        if (conversation != null && conference != null &&
            conference.participants.isEmpty() && conference.hostCall == null) {
            conversation.removeConference(conference)
        }
    }

    private fun onConfStateChange(conference: Conference) {
        Log.d(TAG, "onConfStateChange: ${conference.id}")
    }

    // ==================== Helper Functions ====================

    private fun findConversation(account: Account?, uri: Uri): Conversation? {
        if (account == null) return null
        return account.getByUri(uri) ?: account.getByContact(uri)
    }

    private fun findSwarmConversation(account: Account, swarmId: String): Conversation? {
        return account.getSwarm(swarmId)
    }

    private fun findConversationForCall(
        account: Account,
        conversationUri: Uri?,
        contact: Contact?
    ): Conversation? {
        return if (conversationUri == null) {
            if (contact == null) null
            else findConversation(account, contact.uri)
        } else {
            findConversation(account, conversationUri)
        }
    }

    private fun findOrCreateConference(
        conversation: Conversation?,
        call: Call,
        newState: CallStatus
    ): Conference? {
        if (conversation == null) return null
        return conversation.getConference(call.confId ?: call.daemonId) ?: run {
            if (newState == CallStatus.OVER) return null
            Conference(call).also { conference ->
                conversation.addConference(conference)
            }
        }
    }

    private fun getContactFromCache(account: Account, uri: Uri): Contact {
        return Contact(uri)
    }

    private fun getConversationsForAccount(account: Account, withBlocked: Boolean = false): List<Conversation> {
        val allConversations = account.getConversations()
        return if (withBlocked) {
            allConversations.toList()
        } else {
            // Filter out blocked contacts (except in group conversations)
            allConversations.filter { conversation ->
                conversation.isGroup || conversation.contact?.isBlocked != true
            }
        }
    }

    // ==================== Daemon Callback Handlers ====================

    /**
     * Called when a conversation is ready to use.
     */
    internal fun onConversationReady(accountId: String, conversationId: String) {
        Log.d(TAG, "onConversationReady: $conversationId")

        val account = accountService.getAccount(accountId) ?: run {
            Log.w(TAG, "onConversationReady: account not found: $accountId")
            return
        }

        // Get or create the conversation
        var conversation = account.getSwarm(conversationId)
        if (conversation == null) {
            // Create new swarm conversation with default mode
            conversation = account.newSwarm(conversationId, Conversation.Mode.OneToOne)
        }

        // Mark as started (adds to active conversations)
        account.conversationStarted(conversation)

        // Load conversation info and members from daemon
        scope.launch {
            try {
                val info = daemonBridge.getConversationInfo(accountId, conversationId)
                val mode = when (info["mode"]) {
                    "0" -> Conversation.Mode.OneToOne
                    "1" -> Conversation.Mode.AdminInvitesOnly
                    "2" -> Conversation.Mode.InvitesOnly
                    "3" -> Conversation.Mode.Public
                    else -> Conversation.Mode.OneToOne
                }
                conversation.setMode(mode)

                // Set title if available
                val title = info["title"]
                if (!title.isNullOrEmpty()) {
                    conversation.setProfile(Profile(title, null))
                }

                // Load members
                val members = daemonBridge.getConversationMembers(accountId, conversationId)
                for (member in members) {
                    val memberUri = member["uri"] ?: continue
                    val memberUriParsed = Uri.fromString(memberUri)
                    if (conversation.findContact(memberUriParsed) == null) {
                        val contact = account.getContactFromCache(memberUriParsed)
                        val role = MemberRole.fromString(member["role"] ?: "")
                        conversation.addContact(contact, role)
                    }
                }

                _conversationEvents.emit(ConversationEvent.ConversationReady(accountId, conversationId))
                Log.d(TAG, "onConversationReady: loaded conversation $conversationId")
            } catch (e: Exception) {
                Log.e(TAG, "onConversationReady: failed to load conversation info", e)
            }
        }
    }

    /**
     * Called when a conversation is removed.
     */
    internal fun onConversationRemoved(accountId: String, conversationId: String) {
        Log.d(TAG, "onConversationRemoved: $conversationId")

        val account = accountService.getAccount(accountId) ?: run {
            Log.w(TAG, "onConversationRemoved: account not found: $accountId")
            return
        }

        // Remove from swarm conversations
        account.removeSwarm(conversationId)
        scope.launch {
            _conversationEvents.emit(ConversationEvent.ConversationRemoved(accountId, conversationId))
        }
        Log.d(TAG, "onConversationRemoved: removed conversation $conversationId")
    }

    /**
     * Called when a conversation request is received.
     */
    internal fun onConversationRequestReceived(accountId: String, conversationId: String, metadata: Map<String, String>) {
        Log.d(TAG, "onConversationRequestReceived: $conversationId")

        val account = accountService.getAccount(accountId) ?: run {
            Log.w(TAG, "onConversationRequestReceived: account not found: $accountId")
            return
        }

        val conversation = account.getSwarm(conversationId)
            ?: account.newSwarm(conversationId, Conversation.Mode.Request)
        conversation.setMode(Conversation.Mode.Request)

        // Set title/description from metadata if available
        val title = metadata["title"]
        if (!title.isNullOrEmpty()) {
            conversation.setProfile(Profile(title, null))
        }

        account.addPendingConversation(conversation)
        scope.launch {
            _conversationEvents.emit(ConversationEvent.ConversationRequestReceived(accountId, conversationId, metadata))
        }
    }

    /**
     * Called when a conversation request is declined.
     */
    internal fun onConversationRequestDeclined(accountId: String, conversationId: String) {
        Log.d(TAG, "onConversationRequestDeclined: $conversationId")

        val account = accountService.getAccount(accountId) ?: return
        account.removePendingConversation(Uri(Uri.SWARM_SCHEME, conversationId))
    }

    /**
     * Called when a conversation member event occurs.
     */
    internal fun onConversationMemberEvent(accountId: String, conversationId: String, memberId: String, event: Int) {
        Log.d(TAG, "onConversationMemberEvent: $conversationId member=$memberId event=$event")

        val account = accountService.getAccount(accountId) ?: return
        val conversation = account.getSwarm(conversationId) ?: return

        scope.launch {
            try {
                val members = daemonBridge.getConversationMembers(accountId, conversationId)
                // Rebuild contact list from daemon member data
                for (member in members) {
                    val memberUri = member["uri"] ?: continue
                    val memberUriParsed = Uri.fromString(memberUri)
                    if (conversation.findContact(memberUriParsed) == null) {
                        val contact = account.getContactFromCache(memberUriParsed)
                        val role = MemberRole.fromString(member["role"] ?: "")
                        conversation.addContact(contact, role)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "onConversationMemberEvent: failed to refresh members", e)
            }
        }
    }

    /**
     * Called when a message is received.
     */
    internal fun onMessageReceived(accountId: String, conversationId: String, message: net.jami.model.SwarmMessage) {
        Log.d(TAG, "onMessageReceived: $conversationId msgId=${message.id}")
        scope.launch {
            _conversationEvents.emit(ConversationEvent.MessageReceived(accountId, conversationId, message))
        }
    }

    /**
     * Called when a message is updated.
     */
    internal fun onMessageUpdated(accountId: String, conversationId: String, message: net.jami.model.SwarmMessage) {
        Log.d(TAG, "onMessageUpdated: $conversationId msgId=${message.id}")
        scope.launch {
            _conversationEvents.emit(ConversationEvent.MessageUpdated(accountId, conversationId, message))
        }
    }

    /**
     * Called when messages are found from search.
     */
    internal fun onMessagesFound(messageId: Int, accountId: String, conversationId: String, messages: List<Map<String, String>>) {
        Log.d(TAG, "onMessagesFound: $conversationId found=${messages.size}")
        scope.launch {
            _conversationEvents.emit(ConversationEvent.MessagesFound(accountId, conversationId, messages))
        }
    }

    /**
     * Called when swarm messages are loaded.
     */
    internal fun onSwarmLoaded(id: Long, accountId: String, conversationId: String, messages: List<net.jami.model.SwarmMessage>) {
        Log.d(TAG, "onSwarmLoaded: $conversationId messages=${messages.size}")
        scope.launch {
            _conversationEvents.emit(ConversationEvent.SwarmLoaded(accountId, conversationId, messages))
        }
    }

    /**
     * Called when conversation profile is updated.
     */
    internal fun onConversationProfileUpdated(accountId: String, conversationId: String, profile: Map<String, String>) {
        Log.d(TAG, "onConversationProfileUpdated: $conversationId")

        val account = accountService.getAccount(accountId) ?: return
        val conversation = account.getSwarm(conversationId) ?: return

        val title = profile["title"] ?: ""
        val description = profile["description"] ?: ""
        conversation.setProfile(Profile(title, null))
    }

    /**
     * Called when conversation preferences are updated.
     */
    internal fun onConversationPreferencesUpdated(accountId: String, conversationId: String, preferences: Map<String, String>) {
        Log.d(TAG, "onConversationPreferencesUpdated: $conversationId")

        preferencesService.setConversationPreferences(accountId, Uri(Uri.SWARM_SCHEME, conversationId), preferences)
    }

    /**
     * Called when a reaction is added to a message.
     */
    internal fun onReactionAdded(accountId: String, conversationId: String, messageId: String, reaction: Map<String, String>) {
        Log.d(TAG, "onReactionAdded: $conversationId msgId=$messageId")
        scope.launch {
            _conversationEvents.emit(ConversationEvent.ReactionAdded(accountId, conversationId, messageId, reaction))
        }
    }

    /**
     * Called when a reaction is removed from a message.
     */
    internal fun onReactionRemoved(accountId: String, conversationId: String, messageId: String, reactionId: String) {
        Log.d(TAG, "onReactionRemoved: $conversationId msgId=$messageId")
        scope.launch {
            _conversationEvents.emit(ConversationEvent.ReactionRemoved(accountId, conversationId, messageId, reactionId))
        }
    }

    /**
     * Called when active calls in a conversation change.
     */
    internal fun onActiveCallsChanged(accountId: String, conversationId: String, activeCalls: List<Map<String, String>>) {
        Log.d(TAG, "onActiveCallsChanged: $conversationId calls=${activeCalls.size}")

        val account = accountService.getAccount(accountId) ?: return
        val conversation = account.getSwarm(conversationId) ?: return

        val calls = activeCalls.map { Conversation.ActiveCall(it) }
        conversation.setActiveCalls(calls)

        scope.launch {
            _conversationEvents.emit(ConversationEvent.ActiveCallsChanged(accountId, conversationId, calls))
        }
    }

    /**
     * Called when account message status changes.
     */
    internal fun onAccountMessageStatusChanged(accountId: String, conversationId: String, messageId: String, contactId: String, status: Int) {
        Log.d(TAG, "onAccountMessageStatusChanged: $messageId status=$status")
        scope.launch {
            _conversationEvents.emit(ConversationEvent.MessageStatusChanged(accountId, conversationId, messageId, contactId, status))
        }
    }

    /**
     * Called when composing status changes.
     */
    internal fun onComposingStatusChanged(accountId: String, conversationId: String, contactUri: String, status: Int) {
        Log.d(TAG, "onComposingStatusChanged: $contactUri status=$status")
        scope.launch {
            _conversationEvents.emit(ConversationEvent.ComposingStatusChanged(accountId, conversationId, contactUri, status))
        }
    }

    /**
     * Called when a data transfer event occurs.
     */
    internal fun onDataTransferEvent(accountId: String, conversationId: String, interactionId: String, fileId: String, eventCode: Int) {
        Log.d(TAG, "onDataTransferEvent: $fileId event=$eventCode")

        scope.launch {
            // Update the transfer status in the conversation model
            val account = accountService.getAccount(accountId)
            val conversation = account?.getSwarm(conversationId)
            if (conversation != null) {
                val transfer = conversation.getMessage(interactionId) as? DataTransfer
                if (transfer != null) {
                    val newStatus = when (eventCode) {
                        0 -> Interaction.TransferStatus.TRANSFER_CREATED
                        1 -> Interaction.TransferStatus.TRANSFER_AWAITING_HOST
                        2 -> Interaction.TransferStatus.TRANSFER_AWAITING_PEER
                        3 -> Interaction.TransferStatus.TRANSFER_ONGOING
                        4 -> Interaction.TransferStatus.TRANSFER_FINISHED
                        5 -> Interaction.TransferStatus.TRANSFER_ERROR
                        6 -> Interaction.TransferStatus.TRANSFER_UNJOINABLE_PEER
                        7 -> Interaction.TransferStatus.TRANSFER_TIMEOUT_EXPIRED
                        else -> null
                    }
                    if (newStatus != null) {
                        transfer.transferStatus = newStatus
                        // Update progress from daemon
                        val info = accountService.fileTransferInfo(accountId, conversationId, fileId)
                        if (info != null) {
                            transfer.totalSize = info.totalSize
                            transfer.bytesProgress = info.bytesProgress
                        }
                        conversation.updateInteraction(transfer)
                    }
                }
            }

            _conversationEvents.emit(ConversationEvent.DataTransferEvent(accountId, conversationId, interactionId, fileId, eventCode))
        }
    }

    companion object {
        private const val TAG = "ConversationFacade"
    }
}

/**
 * Holds search results for conversation search.
 */
data class SearchResult(
    val query: String,
    val result: List<Conversation>
) {
    companion object {
        val EMPTY_RESULT = SearchResult("", emptyList())
    }
}

/**
 * Holds a list of conversations with optional search results.
 */
data class ConversationList(
    val conversations: List<Conversation> = emptyList(),
    val searchResult: SearchResult = SearchResult.EMPTY_RESULT,
    val latestQuery: String = ""
) {
    fun isEmpty(): Boolean = conversations.isEmpty() && searchResult.result.isEmpty()

    fun getCombinedSize(): Int {
        if (searchResult.result.isEmpty()) return conversations.size
        if (conversations.isEmpty()) return searchResult.result.size + 1
        return conversations.size + searchResult.result.size + 2
    }

    operator fun get(index: Int): Conversation? {
        return if (searchResult.result.isEmpty()) {
            conversations.getOrNull(index)
        } else if (conversations.isEmpty() || index < searchResult.result.size + 1) {
            searchResult.result.getOrNull(index - 1)
        } else {
            conversations.getOrNull(index - searchResult.result.size - 2)
        }
    }

    fun getHeader(index: Int): ConversationItemViewModel.Title {
        return if (searchResult.result.isEmpty()) {
            ConversationItemViewModel.Title.None
        } else if (index == 0) {
            ConversationItemViewModel.Title.PublicDirectory
        } else if (conversations.isNotEmpty() && index == searchResult.result.size + 1) {
            ConversationItemViewModel.Title.Conversations
        } else {
            ConversationItemViewModel.Title.None
        }
    }
}

/**
 * Events emitted by ConversationFacade for daemon callbacks.
 */
sealed class ConversationEvent {
    data class ConversationReady(
        val accountId: String,
        val conversationId: String
    ) : ConversationEvent()

    data class ConversationRemoved(
        val accountId: String,
        val conversationId: String
    ) : ConversationEvent()

    data class ConversationRequestReceived(
        val accountId: String,
        val conversationId: String,
        val metadata: Map<String, String>
    ) : ConversationEvent()

    data class MessageReceived(
        val accountId: String,
        val conversationId: String,
        val message: SwarmMessage
    ) : ConversationEvent()

    data class MessageUpdated(
        val accountId: String,
        val conversationId: String,
        val message: SwarmMessage
    ) : ConversationEvent()

    data class SwarmLoaded(
        val accountId: String,
        val conversationId: String,
        val messages: List<SwarmMessage>
    ) : ConversationEvent()

    data class ReactionAdded(
        val accountId: String,
        val conversationId: String,
        val messageId: String,
        val reaction: Map<String, String>
    ) : ConversationEvent()

    data class ReactionRemoved(
        val accountId: String,
        val conversationId: String,
        val messageId: String,
        val reactionId: String
    ) : ConversationEvent()

    data class MessageStatusChanged(
        val accountId: String,
        val conversationId: String,
        val messageId: String,
        val contactId: String,
        val status: Int
    ) : ConversationEvent()

    data class ComposingStatusChanged(
        val accountId: String,
        val conversationId: String,
        val contactUri: String,
        val status: Int
    ) : ConversationEvent()

    data class DataTransferEvent(
        val accountId: String,
        val conversationId: String,
        val interactionId: String,
        val fileId: String,
        val eventCode: Int
    ) : ConversationEvent()

    data class ActiveCallsChanged(
        val accountId: String,
        val conversationId: String,
        val activeCalls: List<Conversation.ActiveCall>
    ) : ConversationEvent()

    data class MessagesFound(
        val accountId: String,
        val conversationId: String,
        val messages: List<Map<String, String>>
    ) : ConversationEvent()
}
