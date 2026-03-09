package net.jami.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Represents a Jami account.
 *
 * Ported from: jami-client-android/jami-android/libjamiclient/src/main/kotlin/net/jami/model/Account.kt
 * Original uses RxJava Subjects, this uses Kotlin StateFlow.
 *
 * NOTE: Converted from data class to regular class to support conversation caching.
 */
@Serializable
class Account(
    val accountId: String,
    val details: MutableMap<String, String> = mutableMapOf(),
    val volatileDetails: MutableMap<String, String> = mutableMapOf(),
    val credentials: MutableList<AccountCredentials> = mutableListOf(),
    val devices: MutableMap<String, String> = mutableMapOf()
) {
    // Conversation caches (not serialized - rebuilt from database on load)
    @Transient
    private val conversations = mutableMapOf<String, Conversation>()

    @Transient
    private val swarmConversations = mutableMapOf<String, Conversation>()

    @Transient
    private val pending = mutableMapOf<String, Conversation>()

    @Transient
    private val cache = mutableMapOf<String, Conversation>()

    @Transient
    private val contacts = mutableMapOf<String, Contact>()
    // Registration state
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
        get() = if (isJami) {
            details[ConfigKey.ACCOUNT_USERNAME.key] ?: ""
        } else {
            details[ConfigKey.ACCOUNT_USERNAME.key] ?: ""
        }

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

    // ==================== Conversation Management ====================

    /**
     * Get a swarm conversation by ID
     */
    fun getSwarm(conversationId: String): Conversation? =
        synchronized(conversations) { swarmConversations[conversationId] }

    /**
     * Create or update a swarm conversation
     */
    fun newSwarm(conversationId: String, mode: Conversation.Mode): Conversation {
        synchronized(conversations) {
            val existing = swarmConversations[conversationId]
            if (existing == null) {
                val conv = Conversation(accountId, Uri(Uri.SWARM_SCHEME, conversationId), mode)
                swarmConversations[conversationId] = conv
                return conv
            }
            // Update existing mode
            existing.mode = mode
            return existing
        }
    }

    /**
     * Mark a conversation as started (add to active conversations)
     */
    fun conversationStarted(conversation: Conversation, newMode: Conversation.Mode? = null) {
        synchronized(conversations) {
            if (conversation.isSwarm) {
                // Remove from pending requests
                pending.remove(conversation.uri.uri)
                swarmConversations[conversation.uri.rawRingId] = conversation
            }
            conversations[conversation.uri.uri] = conversation

            if (newMode != null) {
                conversation.mode = newMode
            }

            val mode = newMode ?: conversation.mode
            if (conversation.isSwarm && mode == Conversation.Mode.OneToOne) {
                try {
                    val contact = conversation.contact
                    if (contact != null) {
                        val key = contact.uri.uri
                        cache.remove(key)
                        conversations.remove(key)
                        contact.conversationUri = conversation.uri
                    }
                } catch (e: IllegalStateException) {
                    // Handle edge case where conversation has no primary contact
                }
            }
        }
    }

    /**
     * Remove a swarm conversation
     */
    fun removeSwarm(conversationId: String) {
        synchronized(conversations) {
            val conversation = swarmConversations.remove(conversationId)
            if (conversation != null) {
                try {
                    val removed = conversations.remove(conversation.uri.uri)
                    val contact = removed?.contact
                    if (contact != null && contact.conversationUri == conversation.uri) {
                        // Restore contact conversation
                        contact.conversationUri = contact.uri
                    }
                } catch (_: Exception) {
                    // Ignore cleanup errors
                }
            }
        }
    }

    /**
     * Get conversation by URI
     */
    fun getByUri(uri: Uri?): Conversation? {
        if (uri == null) return null
        val key = uri.uri
        synchronized(conversations) {
            return conversations[key] ?: pending[key] ?: cache[key]
        }
    }

    /**
     * Get conversation by contact
     */
    fun getByContact(contactUri: Uri): Conversation? {
        synchronized(conversations) {
            // First check if there's a direct match
            conversations[contactUri.uri]?.let { return it }

            // Then check conversations for matching contact
            conversations.values.find { conv ->
                conv.contact?.uri == contactUri
            }?.let { return it }

            // Check pending conversations
            pending.values.find { conv ->
                conv.contact?.uri == contactUri
            }?.let { return it }

            return null
        }
    }

    /**
     * Get all active conversations
     */
    fun getConversations(): Collection<Conversation> {
        synchronized(conversations) {
            return conversations.values.toList()
        }
    }

    /**
     * Get all pending conversations (requests)
     */
    fun getPending(): Collection<Conversation> {
        synchronized(pending) {
            return pending.values.toList()
        }
    }

    /**
     * Add a pending conversation request
     */
    fun addPendingConversation(conversation: Conversation) {
        synchronized(pending) {
            pending[conversation.uri.uri] = conversation
        }
    }

    /**
     * Remove a pending request
     */
    fun removePendingConversation(uri: Uri) {
        synchronized(pending) {
            pending.remove(uri.uri)
        }
    }

    /**
     * Check if a contact exists
     */
    fun isContact(uri: Uri): Boolean {
        synchronized(contacts) {
            return contacts.containsKey(uri.uri)
        }
    }

    /**
     * Get or create contact
     */
    fun getContact(uri: Uri): Contact {
        synchronized(contacts) {
            return contacts.getOrPut(uri.uri) { Contact(uri) }
        }
    }

    /**
     * Add contact to cache
     */
    fun addContact(contact: Contact) {
        synchronized(contacts) {
            contacts[contact.uri.uri] = contact
        }
    }

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
}

@Serializable
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
