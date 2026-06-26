package net.jami.ui.viewmodel

import androidx.lifecycle.ViewModel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.jami.model.Conversation
import net.jami.model.Uri
import net.jami.services.AccountEvent
import net.jami.services.AccountService
import net.jami.services.ConversationEvent
import net.jami.services.ConversationFacade
import net.jami.utils.Log

data class PendingRequestItem(
    val accountId: String,
    val conversationUri: Uri,
    val displayName: String,
    val ringId: String,
    val avatarBytes: ByteArray? = null,
) {
    // ByteArray equality would break equals; suppress
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PendingRequestItem) return false
        return accountId == other.accountId && conversationUri == other.conversationUri
    }
    override fun hashCode() = 31 * accountId.hashCode() + conversationUri.hashCode()
}

data class PendingRequestsState(
    val requests: List<PendingRequestItem> = emptyList(),
    val isLoading: Boolean = false,
)

class PendingRequestsViewModel(
    private val accountService: AccountService,
    private val conversationFacade: ConversationFacade,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : ViewModel() {
    private val scope = scope
    private val _state = MutableStateFlow(PendingRequestsState())
    val state: StateFlow<PendingRequestsState> = _state.asStateFlow()

    init {
        load()
        observeEvents()
    }

    private fun observeEvents() {
        // Reload when a new swarm conversation request arrives
        scope.launch {
            conversationFacade.conversationEvents.collect { event ->
                if (event is ConversationEvent.ConversationRequestReceived) {
                    load()
                }
            }
        }
        // Reload when an old-protocol trust request arrives
        scope.launch {
            accountService.accountEvents.collect { event ->
                if (event is AccountEvent.IncomingTrustRequest) {
                    load()
                }
            }
        }
    }

    fun load() {
        scope.launch {
            _state.value = _state.value.copy(isLoading = true)
            try {
                val account = accountService.currentAccount.value ?: return@launch
                val seen = mutableSetOf<String>()
                val pending = mutableListOf<PendingRequestItem>()

                // In-memory pending map (populated by onConversationRequestReceived while running)
                for (conv in account.getPending()) {
                    val contact = conv.contact ?: continue
                    val convId = conv.uri.rawRingId  // raw ID without scheme prefix
                    if (seen.add(convId)) {
                        pending.add(PendingRequestItem(
                            accountId = account.accountId,
                            conversationUri = conv.uri,
                            displayName = contact.displayUsername.ifEmpty { contact.uri.rawRingId },
                            ringId = contact.uri.rawRingId,
                        ))
                    }
                }

                // Daemon poll: catches requests that arrived before the app started
                // (startup race: onConversationRequestReceived may be dropped if account wasn't ready)
                val daemonRequests = accountService.getConversationRequests(account.accountId)
                for (req in daemonRequests) {
                    val convId = req["id"] ?: continue
                    val fromId = req["from"] ?: continue
                    if (seen.add(convId)) {
                        val convUri = Uri(Uri.SWARM_SCHEME, convId)
                        val fromUri = Uri.fromId(fromId)
                        val contact = account.getContactFromCache(fromUri)
                        pending.add(PendingRequestItem(
                            accountId = account.accountId,
                            conversationUri = convUri,
                            displayName = contact.displayUsername.ifEmpty { fromUri.rawRingId },
                            ringId = fromUri.rawRingId,
                        ))
                    }
                }

                Log.d(TAG, "load: found ${pending.size} pending request(s)")
                _state.value = PendingRequestsState(requests = pending, isLoading = false)
            } catch (e: Exception) {
                Log.e(TAG, "load: error", e)
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    fun accept(item: PendingRequestItem) {
        Log.d(TAG, "accept: ${item.ringId}")
        scope.launch {
            try {
                val account = accountService.currentAccount.value ?: return@launch
                val conv = account.getPending().firstOrNull { it.uri == item.conversationUri }
                if (conv != null) {
                    // Full path: has in-memory Conversation object, use facade for proper cleanup
                    conversationFacade.acceptRequest(conv)
                } else {
                    // Daemon-sourced: request wasn't in account.pending (startup race),
                    // accept directly via the daemon API
                    accountService.acceptTrustRequest(item.accountId, item.conversationUri)
                }
                load()
            } catch (e: Exception) {
                Log.e(TAG, "accept: error", e)
            }
        }
    }

    fun discard(item: PendingRequestItem) {
        Log.d(TAG, "discard: ${item.ringId}")
        scope.launch {
            try {
                conversationFacade.discardRequest(item.accountId, item.conversationUri)
                load()
            } catch (e: Exception) {
                Log.e(TAG, "discard: error", e)
            }
        }
    }

    fun block(item: PendingRequestItem) {
        Log.d(TAG, "block: ${item.ringId}")
        scope.launch {
            try {
                conversationFacade.blockConversation(item.accountId, item.conversationUri)
                load()
            } catch (e: Exception) {
                Log.e(TAG, "block: error", e)
            }
        }
    }

    public override fun onCleared() {
        scope.cancel()
    }

    companion object {
        private const val TAG = "PendingRequestsVM"
    }
}
