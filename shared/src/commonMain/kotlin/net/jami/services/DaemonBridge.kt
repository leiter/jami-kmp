package net.jami.services

import net.jami.model.MediaAttribute
import net.jami.model.SwarmMessage

/**
 * Platform-agnostic interface to the Jami daemon.
 *
 * Each platform provides its own implementation:
 * - Android/Desktop: JNI via SWIG-generated bindings
 * - iOS/macOS: Kotlin/Native cinterop to libjami C headers
 * - Web: REST/WebSocket bridge to a daemon server
 */
expect class DaemonBridge() {
    // Lifecycle
    fun init(callbacks: DaemonCallbacks): Boolean
    fun start(): Boolean
    fun stop()
    fun isRunning(): Boolean

    // Account operations
    fun addAccount(details: Map<String, String>): String
    fun removeAccount(accountId: String)
    fun getAccountDetails(accountId: String): Map<String, String>
    fun setAccountDetails(accountId: String, details: Map<String, String>)
    fun getAccountList(): List<String>
    fun setAccountActive(accountId: String, active: Boolean)

    // Call operations
    fun placeCall(accountId: String, uri: String, mediaList: List<MediaAttribute>): String
    fun accept(accountId: String, callId: String, mediaList: List<MediaAttribute>)
    fun hangUp(accountId: String, callId: String)
    fun hold(accountId: String, callId: String)
    fun unhold(accountId: String, callId: String)
    fun muteLocalMedia(accountId: String, callId: String, mediaType: String, mute: Boolean)

    // Conversation operations
    fun getConversations(accountId: String): List<String>
    fun startConversation(accountId: String): String
    fun sendMessage(accountId: String, conversationId: String, message: String, replyTo: String, flag: Int)
    fun loadConversation(accountId: String, conversationId: String, fromMessage: String, size: Int)
    fun getConversationMembers(accountId: String, conversationId: String): List<Map<String, String>>

    // Contact operations
    fun addContact(accountId: String, uri: String)
    fun removeContact(accountId: String, uri: String, ban: Boolean)
    fun getContacts(accountId: String): List<Map<String, String>>

    // Name lookup
    fun lookupName(accountId: String, nameServiceUrl: String, name: String): Boolean
    fun lookupAddress(accountId: String, nameServiceUrl: String, address: String): Boolean
    fun registerName(accountId: String, name: String, scheme: String, password: String): Boolean
}

/**
 * Callback interface for daemon events.
 * Implementations convert these callbacks to Kotlin Flow emissions.
 */
interface DaemonCallbacks {
    // Account callbacks
    fun onAccountsChanged()
    fun onAccountDetailsChanged(accountId: String, details: Map<String, String>)
    fun onRegistrationStateChanged(accountId: String, state: String, code: Int, detail: String)
    fun onVolatileAccountDetailsChanged(accountId: String, details: Map<String, String>)
    fun onKnownDevicesChanged(accountId: String, devices: Map<String, String>)

    // Call callbacks
    fun onCallStateChanged(accountId: String, callId: String, state: String, code: Int)
    fun onIncomingCall(accountId: String, callId: String, from: String)
    fun onMediaChangeRequested(accountId: String, callId: String, mediaList: List<Map<String, String>>)
    fun onConferenceCreated(accountId: String, conversationId: String, confId: String)
    fun onConferenceChanged(accountId: String, confId: String, state: String)
    fun onConferenceRemoved(accountId: String, confId: String)

    // Conversation callbacks
    fun onConversationReady(accountId: String, conversationId: String)
    fun onConversationRemoved(accountId: String, conversationId: String)
    fun onConversationRequestReceived(accountId: String, conversationId: String, metadata: Map<String, String>)
    fun onConversationMemberEvent(accountId: String, conversationId: String, memberId: String, event: Int)
    fun onMessageReceived(accountId: String, conversationId: String, message: SwarmMessage)
    fun onMessagesFound(messageId: Int, accountId: String, conversationId: String, messages: List<Map<String, String>>)
    fun onConversationProfileUpdated(accountId: String, conversationId: String, profile: Map<String, String>)

    // Contact callbacks
    fun onContactAdded(accountId: String, uri: String, confirmed: Boolean)
    fun onContactRemoved(accountId: String, uri: String, banned: Boolean)
    fun onIncomingTrustRequest(accountId: String, conversationId: String, from: String, payload: ByteArray, receiveTime: Long)

    // Name service callbacks
    fun onNameRegistrationEnded(accountId: String, state: Int, name: String)
    fun onRegisteredNameFound(accountId: String, state: Int, address: String, name: String)
}
