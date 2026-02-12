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
package net.jami.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine

/**
 * Represents a conversation in Jami.
 *
 * A conversation can be:
 * - Legacy: A 1:1 contact view (not a swarm)
 * - Swarm: A group or 1:1 conversation with message history
 *
 * Ported from: jami-client-android libjamiclient
 * Changes:
 * - RxJava → Kotlin Flow
 * - Simplified interaction handling (full types in Task #5)
 * - Removed synchronized blocks (using coroutine-safe structures)
 */
class Conversation(
    val accountId: String,
    val uri: Uri,
    val contacts: MutableList<Contact> = mutableListOf(),
    initialMode: Mode = Mode.Legacy
) : ConversationHistory() {

    // ==================== Mode ====================

    private val _mode = MutableStateFlow(initialMode)
    val modeFlow: StateFlow<Mode> = _mode.asStateFlow()
    val mode: Mode get() = _mode.value

    var requestMode: Mode? = null
        internal set

    // ==================== Roles ====================

    val roles: MutableMap<String, MemberRole> = mutableMapOf()

    // ==================== History ====================

    private val rawHistory: MutableMap<Long, Interaction> = mutableMapOf()
    private val aggregateHistory: MutableList<Interaction> = mutableListOf()
    private val messages: MutableMap<String, Interaction> = mutableMapOf()

    private var dirty: Boolean = false

    // ==================== Calls ====================

    private val currentCalls: MutableList<Conference> = mutableListOf()

    private val _calls = MutableStateFlow<List<Conference>>(emptyList())
    val callsFlow: StateFlow<List<Conference>> = _calls.asStateFlow()

    private val _activeCalls = MutableStateFlow<List<ActiveCall>>(emptyList())
    val activeCallsFlow: StateFlow<List<ActiveCall>> = _activeCalls.asStateFlow()

    val currentCall: Conference?
        get() = currentCalls.firstOrNull()

    // ==================== Updates ====================

    private val _updatedElements = MutableSharedFlow<Pair<Interaction, ElementStatus>>()
    val updatedElements: SharedFlow<Pair<Interaction, ElementStatus>> = _updatedElements.asSharedFlow()

    private val _cleared = MutableSharedFlow<List<Interaction>>()
    val cleared: SharedFlow<List<Interaction>> = _cleared.asSharedFlow()

    private val _contactUpdates = MutableStateFlow<List<Contact>>(contacts.toList())
    val contactUpdates: StateFlow<List<Contact>> = _contactUpdates.asStateFlow()

    // ==================== Last Event ====================

    private val _lastEvent = MutableStateFlow<Interaction?>(null)
    val lastEventFlow: StateFlow<Interaction?> = _lastEvent.asStateFlow()

    var lastEvent: Interaction?
        get() = _lastEvent.value
        set(value) { _lastEvent.value = value }

    val currentStateFlow: Flow<Pair<Interaction?, Boolean>> = combine(
        _lastEvent,
        _calls
    ) { event, calls ->
        val hasCurrentCall = calls.any { it.isOnGoing }
        Pair(event, hasCurrentCall)
    }

    // ==================== Message Tracking ====================

    val lastDisplayedMessages: MutableMap<String, String> = mutableMapOf()
    var lastRead: String? = null
        private set
    var lastNotified: String? = null
        private set
    var lastSent: String? = null
        private set

    // ==================== Composing ====================

    private val _composingStatus = MutableStateFlow(ComposingStatus.Idle)
    val composingStatusFlow: StateFlow<ComposingStatus> = _composingStatus.asStateFlow()

    // ==================== Profile ====================

    private val _profile = MutableStateFlow(Profile.EMPTY_PROFILE)
    val profileFlow: StateFlow<Profile> = _profile.asStateFlow()

    // ==================== Preferences ====================

    private val _color = MutableStateFlow(0)
    val colorFlow: StateFlow<Int> = _color.asStateFlow()

    private val _symbol = MutableStateFlow("")
    val symbolFlow: StateFlow<String> = _symbol.asStateFlow()

    private val _notificationEnabled = MutableStateFlow(true)
    val notificationEnabledFlow: StateFlow<Boolean> = _notificationEnabled.asStateFlow()
    val isNotificationEnabled: Boolean get() = _notificationEnabled.value

    // ==================== Visibility ====================

    private val _visible = MutableStateFlow(false)
    val visibleFlow: StateFlow<Boolean> = _visible.asStateFlow()

    var isVisible: Boolean
        get() = _visible.value
        set(value) { _visible.value = value }

    var isBubble: Boolean = false

    // ==================== Trust Request ====================

    var request: TrustRequest? = null

    // ==================== Constructors ====================

    constructor(accountId: String, contact: Contact) : this(
        accountId = accountId,
        uri = contact.uri,
        contacts = mutableListOf(contact),
        initialMode = Mode.Legacy
    ) {
        participant = contact.uri.uri
        _contactUpdates.value = contacts.toList()
    }

    constructor(accountId: String, uri: Uri, mode: Mode) : this(
        accountId = accountId,
        uri = uri,
        contacts = mutableListOf(),
        initialMode = mode
    )

    // ==================== Properties ====================

    val isSwarm: Boolean
        get() = uri.scheme == Uri.SWARM_SCHEME

    val contact: Contact?
        get() {
            if (contacts.size == 1) return contacts[0]
            if (isSwarm && contacts.size > 2) {
                throw IllegalStateException("getContact() called for group conversation of size ${contacts.size}")
            }
            return contacts.firstOrNull { !it.isUser }
        }

    val isGroup: Boolean
        get() = isSwarm && contacts.size > 2

    val isLegacy: Boolean
        get() = mode == Mode.Legacy

    val isSyncing: Boolean
        get() = mode == Mode.Syncing

    val isSwarmGroup: Boolean
        get() = isSwarm && when (mode) {
            Mode.Request -> request?.mode != Mode.OneToOne
            else -> mode != Mode.OneToOne
        }

    val isEnded: Boolean
        get() {
            val user = contacts.firstOrNull { it.isUser }
            val nonUserRoles = roles.filterKeys { it != user?.uri?.uri }.values
            if (nonUserRoles.isEmpty()) {
                return isSwarmGroup && !isUserGroupAdmin()
            }
            val allPeersGone = nonUserRoles.all { it == MemberRole.LEFT || it == MemberRole.BLOCKED }
            if (!allPeersGone) return false
            if (!isSwarmGroup) return true
            return !isUserGroupAdmin()
        }

    // ==================== Contact Management ====================

    fun addContact(contact: Contact, memberRole: MemberRole? = null) {
        memberRole?.let { roles[contact.uri.uri] = it }

        val allowContactAdding = when (mode) {
            Mode.OneToOne -> memberRole != MemberRole.BLOCKED
            else -> memberRole != MemberRole.BLOCKED && memberRole != MemberRole.LEFT
        }

        if (allowContactAdding) {
            contacts.add(contact)
        }
        _contactUpdates.value = contacts.toList()
    }

    fun removeContact(contact: Contact, memberRole: MemberRole? = null) {
        memberRole?.let { roles[contact.uri.uri] = it }
        if (mode != Mode.OneToOne) {
            contacts.remove(contact)
        }
        _contactUpdates.value = contacts.toList()
    }

    fun findContact(uri: Uri): Contact? = contacts.firstOrNull { it.uri == uri }

    fun getUser(): Contact? = contacts.firstOrNull { it.isUser }

    fun isUserGroupAdmin(): Boolean {
        if (!isSwarmGroup) return false
        return getUser()?.let { roles[it.uri.uri] == MemberRole.ADMIN } ?: false
    }

    // ==================== Mode ====================

    fun setMode(mode: Mode) {
        _mode.value = mode
    }

    // ==================== Composing ====================

    fun composingStatusChanged(contact: Contact, composing: ComposingStatus) {
        _composingStatus.value = composing
    }

    // ==================== Profile ====================

    fun setProfile(profile: Profile) {
        _profile.value = profile
    }

    // ==================== Calls ====================

    fun addConference(conference: Conference) {
        val existingIndex = currentCalls.indexOfFirst { it.id == conference.id }
        if (existingIndex >= 0) {
            currentCalls[existingIndex] = conference
        } else if (currentCalls.none { it === conference }) {
            currentCalls.add(conference)
        }
        _calls.value = currentCalls.toList()
    }

    fun removeConference(conference: Conference) {
        currentCalls.remove(conference)
        _calls.value = currentCalls.toList()
    }

    fun getConference(confId: String?): Conference? {
        val id = confId ?: return null
        return currentCalls.firstOrNull { it.id == id || it.getCallById(id) != null }
    }

    fun setActiveCalls(activeCalls: List<ActiveCall>) {
        _activeCalls.value = activeCalls
    }

    /**
     * Add a call history entry to the conversation.
     */
    suspend fun addCall(callHistory: CallHistory) {
        addElement(callHistory)
    }

    // ==================== Message Tracking ====================

    fun getMessage(messageId: String): Interaction? = messages[messageId]

    fun setLastMessageRead(lastMessageRead: String?) {
        lastRead = lastMessageRead
    }

    fun setLastMessageNotified(lastMessage: String?) {
        lastNotified = lastMessage
    }

    // ==================== History Operations ====================

    suspend fun readMessages(): List<Interaction> {
        val interactions = mutableListOf<Interaction>()
        if (isSwarm) {
            if (aggregateHistory.isNotEmpty()) {
                var n = aggregateHistory.size
                do {
                    if (n == 0) break
                    val i = aggregateHistory[--n]
                    if (!i.isRead) {
                        i.read()
                        interactions.add(i)
                        lastRead = i.messageId
                    }
                } while (i.type == Interaction.InteractionType.INVALID)
            }
        } else {
            for (e in rawHistory.values.reversed()) {
                if (e.type != Interaction.InteractionType.TEXT) continue
                if (e.isRead) break
                e.read()
                interactions.add(e)
            }
        }
        interactions.firstOrNull { it.type != Interaction.InteractionType.INVALID }?.let {
            lastEvent = it
        }
        return interactions
    }

    fun getSortedHistory(): List<Interaction> {
        sortHistory()
        return aggregateHistory.toList()
    }

    private fun sortHistory() {
        if (dirty) {
            if (!isSwarm) {
                aggregateHistory.sortBy { it.timestamp }
            }
            lastEvent = aggregateHistory.lastOrNull { it.type != Interaction.InteractionType.INVALID }
            dirty = false
        }
    }

    suspend fun setHistory(loadedConversation: List<Interaction>) {
        dirty = true
        aggregateHistory.clear()
        for (interaction in loadedConversation) {
            setInteractionProperties(interaction)
            aggregateHistory.add(interaction)
            rawHistory[interaction.timestamp] = interaction
        }
        sortHistory()
    }

    suspend fun addElement(interaction: Interaction) {
        setInteractionProperties(interaction)
        dirty = true
        aggregateHistory.add(interaction)
        if (!isSwarm) {
            rawHistory[interaction.timestamp] = interaction
        }
        _updatedElements.emit(Pair(interaction, ElementStatus.ADD))
    }

    suspend fun addSwarmElement(interaction: Interaction, newMessage: Boolean) {
        val id = interaction.messageId ?: return
        messages[id] = interaction

        // Update lastDisplayedMessages and lastSent
        interaction.statusMap.entries.forEach { (key, value) ->
            val contact = findContact(Uri.fromString(key)) ?: return@forEach
            if (!contact.isUser) {
                if (value == Interaction.MessageStates.DISPLAYED) {
                    setLastMessageDisplayed(key, id)
                }
                if (value == Interaction.MessageStates.SUCCESS) {
                    setLastMessageSent(id)
                }
            }
        }

        if (lastRead != null && lastRead == id) interaction.read()
        if (lastNotified != null && lastNotified == id) interaction.isNotified = true

        var newLeaf = false
        var added = false

        if (aggregateHistory.isEmpty() || aggregateHistory.last().messageId == interaction.parentId) {
            added = true
            newLeaf = true
            aggregateHistory.add(interaction)
            _updatedElements.emit(Pair(interaction, ElementStatus.ADD))
        } else {
            // Try to insert at correct position
            for (i in aggregateHistory.indices) {
                if (id == aggregateHistory[i].parentId) {
                    aggregateHistory.add(i, interaction)
                    _updatedElements.emit(Pair(interaction, ElementStatus.ADD))
                    added = true
                    newLeaf = !aggregateHistory.drop(i + 1)
                        .any { it.type != Interaction.InteractionType.INVALID }
                    break
                }
            }
            if (!added) {
                for (i in aggregateHistory.indices.reversed()) {
                    if (aggregateHistory[i].messageId == interaction.parentId) {
                        added = true
                        newLeaf = true
                        aggregateHistory.add(i + 1, interaction)
                        _updatedElements.emit(Pair(interaction, ElementStatus.ADD))
                        break
                    }
                }
            }
        }

        if (newLeaf) {
            if (isVisible) {
                interaction.read()
                setLastMessageRead(id)
            }
            if (interaction.type != Interaction.InteractionType.INVALID) {
                lastEvent = interaction
            }
        }
    }

    suspend fun updateInteraction(element: Interaction) {
        if (isSwarm) {
            val e = messages[element.messageId]
            if (e != null) {
                e.status = element.status
                _updatedElements.emit(Pair(e, ElementStatus.UPDATE))
            }
        } else {
            setInteractionProperties(element)
            val time = element.timestamp
            rawHistory[time]?.let { txt ->
                if (txt.id == element.id) {
                    txt.status = element.status
                    _updatedElements.emit(Pair(txt, ElementStatus.UPDATE))
                }
            }
        }
    }

    suspend fun removeInteraction(interaction: Interaction) {
        val removed = if (isSwarm) {
            messages.remove(interaction.messageId)?.let {
                aggregateHistory.remove(it)
                true
            } ?: false
        } else {
            aggregateHistory.removeAll { it.id.toLong() == interaction.id.toLong() }
        }
        if (removed) {
            _updatedElements.emit(Pair(interaction, ElementStatus.REMOVE))
        }
    }

    suspend fun clearHistory(delete: Boolean) {
        aggregateHistory.clear()
        rawHistory.clear()
        dirty = false
        if (!delete && !isSwarm && contacts.size == 1) {
            // Re-add contact event placeholder
        }
        _cleared.emit(aggregateHistory.toList())
    }

    fun removeAll() {
        aggregateHistory.clear()
        currentCalls.clear()
        rawHistory.clear()
        dirty = true
    }

    private fun setInteractionProperties(interaction: Interaction) {
        interaction.account = accountId
        if (interaction.contact == null) {
            if (contacts.size == 1) {
                interaction.contact = contacts[0]
            } else if (interaction.author != null) {
                interaction.contact = findContact(Uri.fromString(interaction.author!!))
            }
        }
    }

    private fun setLastMessageDisplayed(contactId: String, messageId: String) {
        val currentLast = lastDisplayedMessages[contactId]?.let { getMessage(it) }
        val newMessage = getMessage(messageId)

        if (newMessage?.type != Interaction.InteractionType.INVALID &&
            newMessage?.type != null &&
            (currentLast == null || isAfter(currentLast, newMessage))
        ) {
            lastDisplayedMessages[contactId] = messageId
        }
    }

    private fun setLastMessageSent(messageId: String) {
        val currentLast = lastSent?.let { getMessage(it) }
        val newMessage = getMessage(messageId)

        if (newMessage?.type != Interaction.InteractionType.INVALID &&
            newMessage?.type != null &&
            (currentLast == null || isAfter(currentLast, newMessage))
        ) {
            lastSent = messageId
        }
    }

    private fun isAfter(previous: Interaction, query: Interaction?): Boolean {
        var current = query
        return if (isSwarm) {
            while (current?.parentId != null) {
                if (current.parentId == previous.messageId) return true
                current = messages[current.parentId]
            }
            false
        } else {
            previous.timestamp < (query?.timestamp ?: 0L)
        }
    }

    // ==================== Preferences ====================

    fun setColor(color: Int) {
        _color.value = color
    }

    fun setSymbol(symbol: String) {
        _symbol.value = symbol
    }

    fun setNotification(enable: Boolean) {
        _notificationEnabled.value = enable
    }

    fun updatePreferences(preferences: Map<String, String>) {
        preferences[KEY_PREFERENCE_CONVERSATION_COLOR]?.let { colorValue ->
            // Color format is #RRGGBB, convert to AARRGGBB
            val colorInt = colorValue.removePrefix("#").toIntOrNull(16)
            _color.value = colorInt?.or(0xFF000000.toInt()) ?: 0
        } ?: run { _color.value = 0 }

        preferences[KEY_PREFERENCE_CONVERSATION_SYMBOL]?.let { symbolValue ->
            _symbol.value = symbolValue
        }

        preferences[KEY_PREFERENCE_CONVERSATION_NOTIFICATION]?.let { notifValue ->
            _notificationEnabled.value = notifValue.toBoolean()
        }
    }

    // ==================== Reactions ====================

    fun addReaction(reactionInteraction: Interaction, reactTo: String) {
        getMessage(reactTo)?.addReaction(reactionInteraction)
    }

    fun removeReaction(reactTo: String, id: String) {
        getMessage(reactTo)?.removeReaction(id)
    }

    // ==================== Equality ====================

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Conversation) return false
        return accountId == other.accountId && uri == other.uri
    }

    override fun hashCode(): Int {
        var result = accountId.hashCode()
        result = 31 * result + uri.hashCode()
        return result
    }

    override fun toString(): String =
        "Conversation(accountId=$accountId, uri=$uri, mode=$mode, contacts=${contacts.size})"

    // ==================== Nested Types ====================

    enum class ElementStatus {
        UPDATE, REMOVE, ADD
    }

    enum class Mode {
        OneToOne,
        AdminInvitesOnly,
        InvitesOnly,
        Syncing,
        Public,
        Legacy,
        Request;

        val isSwarm: Boolean
            get() = this == OneToOne || this == InvitesOnly || this == Public

        val isGroup: Boolean
            get() = this == AdminInvitesOnly || this == InvitesOnly || this == Public
    }

    enum class ComposingStatus {
        Idle, Active;

        companion object {
            fun fromBoolean(composing: Boolean): ComposingStatus =
                if (composing) Active else Idle
        }
    }

    data class ActiveCall(
        val confId: String,
        val uri: String,
        val device: String
    ) {
        constructor(map: Map<String, String>) : this(
            confId = map[KEY_CONF_ID] ?: "",
            uri = map[KEY_URI] ?: "",
            device = map[KEY_DEVICE] ?: ""
        )

        companion object {
            const val KEY_CONF_ID = "id"
            const val KEY_URI = "uri"
            const val KEY_DEVICE = "device"
        }
    }

    interface ConversationActionCallback {
        fun removeConversation(accountId: String, conversationUri: Uri)
        fun clearConversation(accountId: String, conversationUri: Uri)
        fun copyContactNumberToClipboard(contactNumber: String)
    }

    companion object {
        const val KEY_PREFERENCE_CONVERSATION_COLOR = "color"
        const val KEY_PREFERENCE_CONVERSATION_SYMBOL = "symbol"
        const val KEY_PREFERENCE_CONVERSATION_NOTIFICATION = "notification"
    }
}

/**
 * Member role in a group conversation.
 */
enum class MemberRole {
    ADMIN, MEMBER, INVITED, BLOCKED, LEFT, UNKNOWN;

    companion object {
        fun fromString(value: String): MemberRole = when (value) {
            "admin" -> ADMIN
            "member" -> MEMBER
            "invited" -> INVITED
            "banned" -> BLOCKED
            "left" -> LEFT
            "" -> UNKNOWN
            else -> UNKNOWN
        }
    }
}
