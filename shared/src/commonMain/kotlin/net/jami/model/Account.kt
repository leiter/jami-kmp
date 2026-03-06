package net.jami.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.jami.utils.Log

/**
 * Represents a Jami account.
 *
 * Ported from: jami-client-android/jami-android/libjamiclient/src/main/kotlin/net/jami/model/Account.kt
 * Original uses RxJava Subjects, this uses Kotlin StateFlow.
 */
class Account(
    val accountId: String,
    val details: MutableMap<String, String> = mutableMapOf(),
    val volatileDetails: MutableMap<String, String> = mutableMapOf(),
    val credentials: MutableList<AccountCredentials> = mutableListOf(),
    val devices: MutableMap<String, String> = mutableMapOf()
) {
    // ==================== Conversation Maps ====================

    /** Active swarm conversations keyed by conversation ID */
    val swarmConversations: MutableMap<String, Conversation> = mutableMapOf()

    /** Active conversations keyed by URI string */
    val conversations: MutableMap<String, Conversation> = mutableMapOf()

    /** Pending conversation requests keyed by URI string */
    val pending: MutableMap<String, Conversation> = mutableMapOf()

    /** Lazy-loaded conversation cache keyed by URI string */
    val cache: MutableMap<String, Conversation> = mutableMapOf()

    // ==================== Contact Caches ====================

    /** Confirmed contacts keyed by URI string */
    val mContacts: MutableMap<String, Contact> = mutableMapOf()

    /** All known contacts (including unconfirmed) keyed by URI string */
    val mContactCache: MutableMap<String, Contact> = mutableMapOf()

    // ==================== State Flows ====================

    private val _conversationsSubject = MutableStateFlow<List<Conversation>>(emptyList())
    val conversationsSubject: StateFlow<List<Conversation>> = _conversationsSubject.asStateFlow()

    private val _pendingSubject = MutableStateFlow<List<Conversation>>(emptyList())
    val pendingSubject: StateFlow<List<Conversation>> = _pendingSubject.asStateFlow()

    /** Whether conversation history has been loaded from daemon */
    var historyLoaded: Boolean = false

    // ==================== Registration state ====================

    val registrationState: RegistrationState
        get() = RegistrationState.fromString(volatileDetails[ConfigKey.ACCOUNT_REGISTRATION_STATUS.key] ?: "")

    val isRegistered: Boolean
        get() = registrationState == RegistrationState.REGISTERED

    val needsMigration: Boolean
        get() = registrationState == RegistrationState.ERROR_NEED_MIGRATION

    val isEnabled: Boolean
        get() = details[ConfigKey.ACCOUNT_ENABLE.key]?.toBoolean() ?: true

    // Account type
    val isJami: Boolean
        get() = details[ConfigKey.ACCOUNT_TYPE.key] == AccountConfig.ACCOUNT_TYPE_JAMI

    val isSip: Boolean
        get() = details[ConfigKey.ACCOUNT_TYPE.key] == AccountConfig.ACCOUNT_TYPE_SIP

    // Display info
    val displayName: String
        get() = details[ConfigKey.ACCOUNT_DISPLAYNAME.key] ?: ""

    val username: String
        get() = details[ConfigKey.ACCOUNT_USERNAME.key] ?: ""

    val alias: String
        get() = details[ConfigKey.ACCOUNT_ALIAS.key] ?: ""

    val displayUsername: String
        get() = alias.ifEmpty { username.ifEmpty { accountId } }

    // Host/Server
    val host: String
        get() = details[ConfigKey.ACCOUNT_HOSTNAME.key] ?: ""

    // Registered name (for Jami accounts)
    val registeredName: String
        get() = volatileDetails[ConfigKey.ACCOUNT_REGISTERED_NAME.key] ?: ""

    val hasRegisteredName: Boolean
        get() = registeredName.isNotEmpty()

    // DHT Proxy
    var isDhtProxyEnabled: Boolean
        get() = details[ConfigKey.ACCOUNT_PROXY_ENABLED.key]?.toBoolean() ?: false
        set(value) { details[ConfigKey.ACCOUNT_PROXY_ENABLED.key] = value.toString() }

    // State for username registration in progress
    var registeringUsername: Boolean = false

    // ==================== Conversation Lookup ====================

    /**
     * Find a conversation by URI. Searches conversations, pending, and cache.
     */
    fun getByUri(uri: Uri?): Conversation? {
        if (uri == null) return null
        val key = uri.uri
        if (uri.isSwarm) {
            swarmConversations[uri.rawRingId]?.let { return it }
        }
        conversations[key]?.let { return it }
        pending[key]?.let { return it }
        cache[key]?.let { return it }
        return null
    }

    /**
     * Get or create a conversation from cache for a contact URI.
     */
    fun getByKey(key: Uri): Conversation {
        return cache.getOrPut(key.uri) {
            Conversation(accountId, getContactFromCache(key))
        }
    }

    /**
     * Get a swarm conversation by conversation ID.
     */
    fun getSwarm(conversationId: String): Conversation? {
        return swarmConversations[conversationId]
    }

    /**
     * Create a new swarm conversation or return existing one.
     */
    fun newSwarm(conversationId: String, mode: Conversation.Mode): Conversation {
        val uri = Uri(Uri.SWARM_SCHEME, conversationId)
        val existing = swarmConversations[conversationId]
        if (existing != null) {
            return existing
        }
        val conversation = Conversation(accountId, uri, mode)
        swarmConversations[conversationId] = conversation
        return conversation
    }

    /**
     * Remove a swarm conversation.
     */
    fun removeSwarm(conversationId: String) {
        val conversation = swarmConversations.remove(conversationId) ?: return
        val key = conversation.uri.uri
        conversations.remove(key)
        pending.remove(key)

        // Restore contact's conversationUri if needed (for 1:1)
        if (conversation.mode == Conversation.Mode.OneToOne) {
            conversation.contact?.let { contact ->
                contact.setConversationUri(contact.uri)
            }
        }
        conversationChanged()
    }

    /**
     * Register a conversation as started. Moves it from cache/pending to active conversations.
     */
    fun conversationStarted(conversation: Conversation, newMode: Conversation.Mode? = null) {
        val key = conversation.uri.uri

        if (conversation.isSwarm) {
            swarmConversations[conversation.uri.rawRingId] = conversation
        }

        newMode?.let { conversation.setMode(it) }

        when (conversation.mode) {
            Conversation.Mode.Request -> {
                pending[key] = conversation
                conversations.remove(key)
                pendingChanged()
            }
            else -> {
                conversations[key] = conversation
                pending.remove(key)
                cache.remove(key)

                // For OneToOne, update contact's conversation URI
                if (conversation.mode == Conversation.Mode.OneToOne) {
                    conversation.contact?.let { contact ->
                        contact.setConversationUri(conversation.uri)
                        // Move from cache if it was there under the contact's URI
                        val contactKey = contact.uri.uri
                        if (contactKey != key) {
                            cache.remove(contactKey)
                        }
                    }
                }
                conversationChanged()
            }
        }
    }

    /**
     * Notify that the active conversations list has changed.
     */
    fun conversationChanged() {
        if (historyLoaded) {
            val sorted = conversations.values
                .sortedByDescending { it.lastEvent?.timestamp ?: 0L }
            _conversationsSubject.value = sorted
        }
    }

    /**
     * Notify that the pending conversations list has changed.
     */
    fun pendingChanged() {
        if (historyLoaded) {
            val sorted = pending.values
                .sortedByDescending { it.lastEvent?.timestamp ?: 0L }
            _pendingSubject.value = sorted
        }
    }

    // ==================== Contact Management ====================

    /**
     * Get or create a contact from the cache.
     */
    fun getContactFromCache(uri: Uri): Contact {
        return mContactCache.getOrPut(uri.uri) {
            Contact(uri)
        }
    }

    /**
     * Get or create a contact from the cache by raw ID string.
     */
    fun getContactFromCache(id: String): Contact {
        return getContactFromCache(Uri.fromId(id))
    }

    /**
     * Get a confirmed contact by URI.
     */
    fun getContact(uri: Uri): Contact? {
        return mContacts[uri.uri]
    }

    /**
     * Add a contact to the confirmed contacts map.
     */
    fun addContact(id: String, confirmed: Boolean) {
        val uri = Uri.fromId(id)
        val contact = getContactFromCache(uri)
        contact.status = if (confirmed) Contact.Status.CONFIRMED else Contact.Status.REQUEST_SENT
        mContacts[uri.uri] = contact
    }

    /**
     * Remove a contact.
     */
    fun removeContact(id: String, blocked: Boolean) {
        val uri = Uri.fromId(id)
        if (blocked) {
            val contact = getContactFromCache(uri)
            contact.status = Contact.Status.BLOCKED
        }
        mContacts.remove(uri.uri)
    }

    /**
     * Set contacts from daemon data.
     */
    fun setContacts(contacts: List<Map<String, String>>) {
        mContacts.clear()
        for (contactMap in contacts) {
            val uriString = contactMap["uri"] ?: continue
            val uri = Uri.fromString(uriString)
            val contact = getContactFromCache(uri)
            contactMap["added"]?.toLongOrNull()?.let { contact.addedDate = it }
            val isBanned = contactMap["banned"]?.toBoolean() ?: false
            val isConfirmed = contactMap["confirmed"]?.toBoolean() ?: false
            contact.status = when {
                isBanned -> Contact.Status.BLOCKED
                isConfirmed -> Contact.Status.CONFIRMED
                else -> Contact.Status.NO_REQUEST
            }
            if (!isBanned) {
                mContacts[uri.uri] = contact
            }
        }
    }

    // ==================== Trust Request Management ====================

    /**
     * Add a trust request to the pending map.
     */
    fun addRequest(request: TrustRequest) {
        val key = request.conversationUri.uri
        val contact = getContactFromCache(request.from)
        contact.addedDate = request.timestamp

        val conversation = pending.getOrPut(key) {
            if (request.conversationUri.isSwarm) {
                val c = Conversation(accountId, request.conversationUri, Conversation.Mode.Request)
                c.addContact(contact)
                swarmConversations[request.conversationUri.rawRingId] = c
                c
            } else {
                Conversation(accountId, contact).also {
                    it.setMode(Conversation.Mode.Request)
                }
            }
        }
        conversation.request = request
        conversation.requestMode = request.mode

        // Update contact's conversationUri for swarm requests
        if (request.conversationUri.isSwarm) {
            contact.setConversationUri(request.conversationUri)
        }

        pendingChanged()
    }

    /**
     * Remove a trust request from pending.
     */
    fun removeRequest(conversationUri: Uri): TrustRequest? {
        val key = conversationUri.uri
        val conversation = pending.remove(key)
        val request = conversation?.request
        conversation?.request = null

        if (conversationUri.isSwarm) {
            swarmConversations.remove(conversationUri.rawRingId)
        }

        // Reset contact's conversationUri
        conversation?.contact?.let { contact ->
            contact.setConversationUri(contact.uri)
        }

        pendingChanged()
        return request
    }

    // ==================== Conversation Queries ====================

    /**
     * Get all active conversations.
     */
    fun getConversations(): Collection<Conversation> = conversations.values

    /**
     * Get all pending conversations.
     */
    fun getPending(): Collection<Conversation> = pending.values

    /**
     * Mark a conversation as updated (re-sort and re-emit).
     */
    fun updated(conversation: Conversation) {
        val key = conversation.uri.uri
        when {
            conversations.containsKey(key) -> conversationChanged()
            pending.containsKey(key) -> pendingChanged()
            cache.containsKey(key) -> {
                // Move from cache to conversations or pending
                cache.remove(key)
                if (conversation.mode == Conversation.Mode.Request) {
                    pending[key] = conversation
                    pendingChanged()
                } else {
                    conversations[key] = conversation
                    conversationChanged()
                }
            }
        }
    }

    // ==================== Clear History ====================

    /**
     * Clear conversation history for a contact.
     */
    fun clearHistory(contactUri: Uri?, delete: Boolean) {
        if (contactUri == null) return
        val conversation = getByUri(contactUri) ?: return
        // Clear is handled by conversation itself
    }

    /**
     * Clear all conversation history.
     */
    fun clearAllHistory() {
        for (conversation in conversations.values) {
            conversation.removeAll()
        }
        for (conversation in pending.values) {
            conversation.removeAll()
        }
        for (conversation in cache.values) {
            conversation.removeAll()
        }
        conversationChanged()
        pendingChanged()
    }

    // ==================== Enums ====================

    enum class RegistrationState {
        UNREGISTERED,
        TRYING,
        REGISTERED,
        ERROR_GENERIC,
        ERROR_AUTH,
        ERROR_NETWORK,
        ERROR_HOST,
        ERROR_SERVICE_UNAVAILABLE,
        ERROR_NEED_MIGRATION,
        INITIALIZING;

        companion object {
            fun fromString(state: String): RegistrationState = when (state) {
                "UNREGISTERED" -> UNREGISTERED
                "TRYING" -> TRYING
                "REGISTERED" -> REGISTERED
                "ERROR_GENERIC" -> ERROR_GENERIC
                "ERROR_AUTH" -> ERROR_AUTH
                "ERROR_NETWORK" -> ERROR_NETWORK
                "ERROR_HOST" -> ERROR_HOST
                "ERROR_SERVICE_UNAVAILABLE" -> ERROR_SERVICE_UNAVAILABLE
                "ERROR_NEED_MIGRATION" -> ERROR_NEED_MIGRATION
                "INITIALIZING" -> INITIALIZING
                else -> UNREGISTERED
            }
        }
    }

    companion object {
        private const val TAG = "Account"
    }
}

data class AccountCredentials(
    val username: String,
    val password: String,
    val realm: String = "*"
) {
    fun toMap(): Map<String, String> = mapOf(
        ConfigKey.ACCOUNT_USERNAME.key to username,
        ConfigKey.ACCOUNT_PASSWORD.key to password,
        ConfigKey.ACCOUNT_REALM.key to realm
    )

    companion object {
        fun fromMap(map: Map<String, String>): AccountCredentials = AccountCredentials(
            username = map[ConfigKey.ACCOUNT_USERNAME.key] ?: "",
            password = map[ConfigKey.ACCOUNT_PASSWORD.key] ?: "",
            realm = map[ConfigKey.ACCOUNT_REALM.key] ?: "*"
        )
    }
}

object AccountConfig {
    const val ACCOUNT_TYPE_JAMI = "RING"
    const val ACCOUNT_TYPE_SIP = "SIP"
}
