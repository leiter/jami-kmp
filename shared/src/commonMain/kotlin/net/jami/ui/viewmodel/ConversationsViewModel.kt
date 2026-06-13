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
import net.jami.model.CallHistory
import net.jami.model.Contact
import net.jami.model.DataTransfer
import net.jami.model.Interaction
import net.jami.model.TextMessage
import net.jami.model.Uri
import net.jami.services.AccountEvent
import net.jami.services.AccountService
import net.jami.services.ContactEvent
import net.jami.services.ContactService
import net.jami.services.ConversationFacade
import net.jami.services.ConversationEvent
import net.jami.services.DeviceRuntimeService
import net.jami.services.VCardService
import net.jami.utils.Log
import jami_kmp.shared.generated.resources.Res
import jami_kmp.shared.generated.resources.*
import org.jetbrains.compose.resources.getString

/**
 * Item representing an account in the account switcher.
 */
data class AccountItem(
    val accountId: String,
    val displayName: String,
    val subtitle: String,
    val avatarBytes: ByteArray?,
    val isCurrentAccount: Boolean,
    val isOnline: Boolean,
)

enum class ConversationFilter { ALL, UNREAD, GROUPS, REQUESTS }

/**
 * Item representing a conversation in the list.
 */
data class ConversationItem(
    val id: String,
    /** The contact's ring ID — used for presence matching. Null for group conversations. */
    val contactId: String?,
    val displayName: String,
    val lastMessage: String,
    val timestamp: Long,
    val unreadCount: Int,
    val avatarBytes: ByteArray?,
    val isOnline: Boolean,
    /** False when the last interaction has not been read yet — drives bold styling. */
    val isRead: Boolean,
    val isGroup: Boolean = false,
)

/**
 * State for the conversations list screen.
 */
data class ConversationsState(
    val conversations: List<ConversationItem> = emptyList(),
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val pendingRequests: Int = 0,
    val pendingRequestItems: List<PendingRequestItem> = emptyList(),
    val currentAccountAvatarBytes: ByteArray? = null,
    /** True when the current account is registered with the daemon. */
    val isAccountOnline: Boolean = false,
    val accounts: List<AccountItem> = emptyList(),
    val activeFilter: ConversationFilter = ConversationFilter.ALL,
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
    private val contactService: ContactService,
    private val vCardService: VCardService,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    companion object {
        private const val TAG = "ConversationsVM"
    }

    private val scope = scope

    private val _state = MutableStateFlow(ConversationsState())
    val state: StateFlow<ConversationsState> = _state.asStateFlow()

    /** Full unfiltered list; preserved so filter changes don't require a daemon round-trip. */
    private var cachedConversations: List<ConversationItem> = emptyList()

    /** Cached pending request items; refreshed on every loadConversations(). */
    private var cachedRequests: List<PendingRequestItem> = emptyList()

    /** Tracks (accountId, contactRawRingId) pairs that have an active presence subscription. */
    private val subscribedBuddies = mutableSetOf<Pair<String, String>>()

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

        // Reload pending requests on incoming trust requests (old-protocol contacts)
        scope.launch {
            accountService.accountEvents.collect { event ->
                if (event is AccountEvent.IncomingTrustRequest) {
                    loadConversations()
                }
            }
        }

        // Registered name resolved for a contact — refresh to show username instead of ring ID.
        scope.launch {
            accountService.accountEvents.collect { event ->
                if (event is AccountEvent.RegisteredNameFound && event.state == 0 && event.name.isNotEmpty()) {
                    loadConversations()
                }
            }
        }

        // When account becomes REGISTERED: update own status dot and re-subscribe to all
        // contact presence. subscribeBuddy called pre-registration is queued by the daemon
        // but DHT presence replies only arrive once the account is on the DHT network.
        scope.launch {
            accountService.accountEvents.collect { event ->
                if (event is AccountEvent.RegistrationStateChanged) {
                    val account = accountService.currentAccount.value ?: return@collect
                    if (event.accountId == account.accountId) {
                        _state.value = _state.value.copy(isAccountOnline = account.isRegistered)
                        if (account.isRegistered) {
                            // Clear the subscription set so buildConversationItems() re-subscribes
                            subscribedBuddies.clear()
                            loadConversations()
                        }
                    }
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

        // React to presence changes: match on contactId (ring ID), not conversation swarm ID.
        scope.launch {
            Log.d(TAG, "Starting presence event collector")
            contactService.contactEvents.collect { event ->
                if (event is ContactEvent.PresenceUpdated) {
                    val contactRawId = event.contact.uri.rawRingId
                    val isOnline = event.contact.isOnline
                    Log.d(TAG, "PresenceUpdated received: contactRawId=$contactRawId isOnline=$isOnline")
                    val matchingItems = cachedConversations.filter { it.contactId == contactRawId }
                    Log.d(TAG, "Found ${matchingItems.size} matching conversation items for $contactRawId")
                    if (matchingItems.isEmpty()) {
                        Log.w(TAG, "No matching conversation for presence update. Available contactIds: ${cachedConversations.map { it.contactId }}")
                    }
                    cachedConversations = cachedConversations.map { item ->
                        if (item.contactId == contactRawId) item.copy(isOnline = isOnline)
                        else item
                    }
                    _state.value = _state.value.copy(
                        conversations = applyFilter(cachedConversations, _state.value.activeFilter)
                    )
                    Log.d(TAG, "State updated with presence change")
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

                // Build pending request items (union of in-memory pending + daemon-sourced)
                val seen = mutableSetOf<String>()
                val pending = mutableListOf<PendingRequestItem>()
                for (conv in account.getPending()) {
                    val contact = conv.contact ?: continue
                    val convId = conv.uri.rawRingId
                    if (seen.add(convId)) {
                        pending.add(PendingRequestItem(
                            accountId = accountId,
                            conversationUri = conv.uri,
                            displayName = contact.displayUsername.ifEmpty { contact.uri.rawRingId },
                            ringId = contact.uri.rawRingId,
                        ))
                    }
                }
                val daemonRequests = accountService.getConversationRequests(accountId)
                for (req in daemonRequests) {
                    val convId = req["id"] ?: continue
                    val fromId = req["from"] ?: continue
                    if (seen.add(convId)) {
                        val convUri = Uri(Uri.SWARM_SCHEME, convId)
                        val fromUri = Uri.fromId(fromId)
                        val contact = account.getContactFromCache(fromUri)
                        pending.add(PendingRequestItem(
                            accountId = accountId,
                            conversationUri = convUri,
                            displayName = contact.displayUsername.ifEmpty { fromUri.rawRingId },
                            ringId = fromUri.rawRingId,
                        ))
                    }
                }
                cachedRequests = pending
                val pendingCount = pending.size

                // Load current account avatar from local VCard (scaled + cached)
                val accountAvatarBytes = vCardService.loadLocalAvatar(accountId)

                // Build conversation items from the facade
                val conversations = buildConversationItems(accountId, query)

                // Re-sync presence from Contact objects. During buildConversationItems(),
                // presence subscriptions trigger daemon callbacks that update Contact.isOnline.
                // But buildConversationItems() may have read the value before the update.
                // Re-read now to get the latest presence state.
                val syncedConversations = conversations.map { item ->
                    if (item.contactId != null) {
                        val contact = account.getConversations()
                            .mapNotNull { it.contact }
                            .find { it.uri.rawRingId == item.contactId }
                        if (contact != null && contact.isOnline != item.isOnline) {
                            Log.d(TAG, "Re-syncing presence for ${item.contactId}: ${item.isOnline} -> ${contact.isOnline}")
                            item.copy(isOnline = contact.isOnline)
                        } else item
                    } else item
                }

                val accountItems = buildAccountItems()

                cachedConversations = syncedConversations
                _state.value = _state.value.copy(
                    conversations = applyFilter(syncedConversations, _state.value.activeFilter),
                    isLoading = false,
                    pendingRequests = pendingCount,
                    pendingRequestItems = cachedRequests,
                    currentAccountAvatarBytes = accountAvatarBytes ?: _state.value.currentAccountAvatarBytes,
                    isAccountOnline = account.isRegistered,
                    accounts = accountItems,
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
     * Switch the active filter. Re-applies immediately from the cached list without hitting the daemon.
     */
    fun setFilter(filter: ConversationFilter) {
        _state.value = _state.value.copy(
            activeFilter = filter,
            conversations = applyFilter(cachedConversations, filter),
            pendingRequestItems = if (filter == ConversationFilter.REQUESTS) cachedRequests else _state.value.pendingRequestItems,
        )
    }

    private fun applyFilter(all: List<ConversationItem>, filter: ConversationFilter): List<ConversationItem> =
        when (filter) {
            ConversationFilter.ALL -> all
            ConversationFilter.UNREAD -> all.filter { !it.isRead }
            ConversationFilter.GROUPS -> all.filter { it.isGroup }
            ConversationFilter.REQUESTS -> emptyList()
        }

    fun acceptRequest(item: PendingRequestItem) {
        scope.launch {
            try {
                val account = accountService.currentAccount.value ?: return@launch
                val conv = account.getPending().firstOrNull { it.uri == item.conversationUri }
                if (conv != null) {
                    conversationFacade.acceptRequest(conv)
                } else {
                    accountService.acceptTrustRequest(item.accountId, item.conversationUri)
                }
                loadConversations()
            } catch (e: Exception) {
                Log.e(TAG, "acceptRequest: error", e)
            }
        }
    }

    fun discardRequest(item: PendingRequestItem) {
        scope.launch {
            try {
                conversationFacade.discardRequest(item.accountId, item.conversationUri)
                loadConversations()
            } catch (e: Exception) {
                Log.e(TAG, "discardRequest: error", e)
            }
        }
    }

    fun blockRequest(item: PendingRequestItem) {
        scope.launch {
            try {
                conversationFacade.blockConversation(item.accountId, item.conversationUri)
                loadConversations()
            } catch (e: Exception) {
                Log.e(TAG, "blockRequest: error", e)
            }
        }
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
    private suspend fun buildConversationItems(accountId: String, query: String): List<ConversationItem> {
        val account = accountService.getAccount(accountId) ?: return emptyList()
        val conversations = account.getConversations()

        return conversations.mapNotNull { conversation ->
            val isGroup = conversation.isGroup
            // contact throws for group conversations with >2 members
            val contact = if (isGroup) null else conversation.contact

            // Subscribe to presence for this contact if not already subscribed.
            if (contact != null) {
                val key = accountId to contact.uri.rawRingId
                if (subscribedBuddies.add(key)) {
                    Log.d(TAG, "Subscribing to presence for contact ${contact.uri.rawRingId}")
                    contactService.subscribeBuddy(accountId, contact.uri, true)
                }
            }

            val displayName = conversation.profileFlow.value.displayName?.takeIf { it.isNotBlank() }
                ?: contact?.displayUsername
                ?: conversation.uri.rawRingId

            // Filter by query
            if (query.isNotEmpty() && !displayName.lowercase().contains(query)) {
                return@mapNotNull null
            }

            val lastEvent = conversation.lastEvent
            val lastMessage = if (lastEvent != null) getLastEventSummary(lastEvent) else ""
            val timestamp = lastEvent?.timestamp ?: 0L
            val isRead = lastEvent?.isRead ?: true

            // Load contact avatar (scaled + disk-cached via VCardService)
            val avatarBytes = contact?.let { c ->
                vCardService.loadPeerAvatar(accountId, c.uri.rawRingId)
            }

            ConversationItem(
                id = conversation.uri.rawRingId,
                contactId = contact?.uri?.rawRingId,
                displayName = displayName,
                lastMessage = lastMessage,
                timestamp = timestamp,
                unreadCount = 0,
                avatarBytes = avatarBytes,
                isOnline = contact?.isOnline == true,
                isRead = isRead,
                isGroup = isGroup,
            )
        }
            // Sort by timestamp descending (most recent first)
            // Note: ConversationFacade also sorts, but we keep this as defensive measure
            .sortedByDescending { it.timestamp }
    }

    /**
     * Switch the active account. The conversation list refreshes automatically via the
     * currentAccount Flow observer in init.
     */
    fun switchAccount(accountId: String) {
        val account = accountService.getAccount(accountId) ?: return
        accountService.setCurrentAccount(account)
    }

    private fun buildAccountItems(): List<AccountItem> {
        val currentId = accountService.currentAccount.value?.accountId
        return accountService.accounts.value.map { acc ->
            val avatarBytes = vCardService.loadLocalAvatar(acc.accountId)
            val name = acc.displayName.ifEmpty { acc.registeredName.ifEmpty { acc.alias.ifEmpty { acc.username } } }
            val subtitle = if (acc.registeredName.isNotEmpty()) acc.username else ""
            AccountItem(
                accountId = acc.accountId,
                displayName = name.ifEmpty { acc.accountId },
                subtitle = subtitle,
                avatarBytes = avatarBytes,
                isCurrentAccount = acc.accountId == currentId,
                isOnline = acc.isRegistered,
            )
        }
    }

    private suspend fun getLastEventSummary(event: Interaction): String = when (event.type) {
        Interaction.InteractionType.TEXT -> {
            val body = event.body ?: ""
            if (event.isIncoming) body
            else "${getString(Res.string.you_txt_prefix)} $body"
        }
        Interaction.InteractionType.CALL -> {
            val call = event as? CallHistory
            when {
                call == null -> getString(Res.string.notif_incoming_call)
                call.isMissed && call.isIncoming -> getString(Res.string.notif_missed_incoming_call)
                call.isMissed -> getString(Res.string.notif_missed_outgoing_call)
                call.isIncoming -> getString(Res.string.hist_in_call, call.durationString)
                else -> getString(Res.string.hist_out_call, call.durationString)
            }
        }
        Interaction.InteractionType.DATA_TRANSFER -> {
            if (event.isIncoming) getString(Res.string.hist_file_received)
            else getString(Res.string.hist_file_sent)
        }
        else -> ""
    }

    /**
     * Cancel the coroutine scope and release all presence subscriptions.
     */
    fun onCleared() {
        val accountId = accountService.currentAccount.value?.accountId
        if (accountId != null) {
            for ((subAccountId, rawRingId) in subscribedBuddies) {
                contactService.subscribeBuddy(subAccountId, Uri.fromString(rawRingId), false)
            }
        }
        subscribedBuddies.clear()
        scope.cancel()
    }
}
