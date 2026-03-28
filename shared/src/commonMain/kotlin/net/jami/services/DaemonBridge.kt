package net.jami.services

import net.jami.model.MediaAttribute
import net.jami.model.SwarmMessage

/**
 * Platform-agnostic interface to the Jami daemon.
 *
 * Services and repositories depend on this interface rather than the concrete
 * [DaemonBridge] expect class, enabling [StubDaemonBridge] to be used in tests.
 */
interface DaemonBridgeApi {
    // ==================== Lifecycle ====================
    fun init(callbacks: DaemonCallbacks): Boolean
    fun start(): Boolean
    fun stop()
    fun isRunning(): Boolean

    // ==================== Account Operations ====================
    fun addAccount(details: Map<String, String>): String
    fun removeAccount(accountId: String)
    fun getAccountDetails(accountId: String): Map<String, String>
    fun setAccountDetails(accountId: String, details: Map<String, String>)
    fun getAccountList(): List<String>
    fun setAccountActive(accountId: String, active: Boolean)
    fun getAccountTemplate(accountType: String): Map<String, String>
    fun getVolatileAccountDetails(accountId: String): Map<String, String>
    fun sendRegister(accountId: String, enable: Boolean)
    fun setAccountsOrder(order: String)
    fun changeAccountPassword(accountId: String, oldPassword: String, newPassword: String): Boolean
    fun exportToFile(accountId: String, path: String, scheme: String, password: String): Boolean

    // ==================== Credentials ====================
    fun getCredentials(accountId: String): List<Map<String, String>>
    fun setCredentials(accountId: String, credentials: List<Map<String, String>>)

    // ==================== Device Management ====================
    fun getKnownRingDevices(accountId: String): Map<String, String>
    fun revokeDevice(accountId: String, deviceId: String, scheme: String, password: String)
    fun setDeviceName(accountId: String, deviceName: String)

    // ==================== Profile ====================
    fun updateProfile(accountId: String, displayName: String, avatar: String, fileType: String, flag: Int)

    // ==================== Call Operations ====================
    fun placeCall(accountId: String, uri: String, mediaList: List<MediaAttribute>): String
    fun accept(accountId: String, callId: String, mediaList: List<MediaAttribute>)
    fun hangUp(accountId: String, callId: String)
    fun hold(accountId: String, callId: String)
    fun unhold(accountId: String, callId: String)
    fun muteLocalMedia(accountId: String, callId: String, mediaType: String, mute: Boolean)

    // ==================== Conference Operations ====================
    fun holdConference(accountId: String, confId: String): Boolean
    fun unholdConference(accountId: String, confId: String): Boolean
    fun setActiveParticipant(accountId: String, confId: String, callId: String)
    fun setConferenceLayout(accountId: String, confId: String, layout: Int)

    // ==================== Conversation Operations ====================
    fun getConversations(accountId: String): List<String>
    fun startConversation(accountId: String): String
    fun sendMessage(accountId: String, conversationId: String, message: String, replyTo: String, flag: Int)
    fun loadConversation(accountId: String, conversationId: String, fromMessage: String, size: Int)
    fun getConversationMembers(accountId: String, conversationId: String): List<Map<String, String>>
    fun getConversationInfo(accountId: String, conversationId: String): Map<String, String>
    fun removeConversation(accountId: String, conversationId: String)
    fun addConversationMember(accountId: String, conversationId: String, uri: String)
    fun removeConversationMember(accountId: String, conversationId: String, uri: String)
    fun updateConversationInfo(accountId: String, conversationId: String, info: Map<String, String>)
    fun getConversationPreferences(accountId: String, conversationId: String): Map<String, String>
    fun setConversationPreferences(accountId: String, conversationId: String, prefs: Map<String, String>)
    fun setMessageDisplayed(accountId: String, conversationUri: String, messageId: String, status: Int)
    fun getActiveCalls(accountId: String, conversationId: String): List<Map<String, String>>

    // ==================== Conversation Requests ====================
    fun getConversationRequests(accountId: String): List<Map<String, String>>
    fun acceptConversationRequest(accountId: String, conversationId: String)
    fun declineConversationRequest(accountId: String, conversationId: String)

    // ==================== Trust Requests ====================
    fun getTrustRequests(accountId: String): List<Map<String, String>>
    fun acceptTrustRequest(accountId: String, uri: String)
    fun discardTrustRequest(accountId: String, uri: String)
    fun sendTrustRequest(accountId: String, uri: String, payload: ByteArray)

    // ==================== Contact Operations ====================
    fun addContact(accountId: String, uri: String)
    fun removeContact(accountId: String, uri: String, ban: Boolean)
    fun getContacts(accountId: String): List<Map<String, String>>
    fun getContactDetails(accountId: String, uri: String): Map<String, String>
    fun subscribeBuddy(accountId: String, uri: String, subscribe: Boolean)

    // ==================== Name Lookup ====================
    fun lookupName(accountId: String, nameServiceUrl: String, name: String): Boolean
    fun lookupAddress(accountId: String, nameServiceUrl: String, address: String): Boolean
    fun registerName(accountId: String, name: String, scheme: String, password: String): Boolean
    fun searchUser(accountId: String, query: String): Boolean

    // ==================== Messaging ====================
    fun sendTextMessage(accountId: String, callIdOrUri: String, message: String)
    fun setIsComposing(accountId: String, uri: String, isComposing: Boolean)
    fun cancelMessage(accountId: String, messageId: Long): Boolean

    // ==================== File Transfer ====================
    fun sendFile(accountId: String, conversationId: String, filePath: String, displayName: String, parent: String)
    fun downloadFile(accountId: String, conversationId: String, interactionId: String, fileId: String, path: String)
    fun cancelDataTransfer(accountId: String, conversationId: String, fileId: String)
    fun fileTransferInfo(accountId: String, conversationId: String, fileId: String): FileTransferInfo?

    // ==================== Codec Operations ====================
    fun getCodecList(): List<Long>
    fun getActiveCodecList(accountId: String): List<Long>
    fun setActiveCodecList(accountId: String, codecList: List<Long>)
    fun getCodecDetails(accountId: String, codecId: Long): Map<String, String>

    // ==================== Search & History ====================
    fun searchConversation(accountId: String, conversationId: String, author: String, lastId: String, query: String, type: String, after: Long, before: Long, maxResult: Long, flag: Int): Long
    fun loadSwarmUntil(accountId: String, conversationId: String, fromMessage: String, toMessage: String): Long

    // ==================== Push Notifications ====================
    fun setPushNotificationToken(token: String)
    fun setPushNotificationConfig(config: Map<String, String>)
    fun pushNotificationReceived(from: String, data: Map<String, String>)
}

/**
 * Platform-specific daemon bridge (JNI, cinterop, or REST).
 *
 * Each platform provides its own implementation:
 * - Android/Desktop: JNI via SWIG-generated bindings
 * - iOS/macOS: Kotlin/Native cinterop to libjami C headers
 * - Web: REST/WebSocket bridge to a daemon server
 */
expect class DaemonBridge : DaemonBridgeApi

/**
 * File transfer information from the daemon.
 */
data class FileTransferInfo(
    val path: String,
    val totalSize: Long,
    val bytesProgress: Long
)

/**
 * Callback interface for daemon events.
 * Implementations convert these callbacks to Kotlin Flow emissions.
 */
interface DaemonCallbacks {
    // ==================== Account Callbacks ====================
    fun onAccountsChanged()
    fun onAccountDetailsChanged(accountId: String, details: Map<String, String>)
    fun onRegistrationStateChanged(accountId: String, state: String, code: Int, detail: String)
    fun onVolatileAccountDetailsChanged(accountId: String, details: Map<String, String>)
    fun onKnownDevicesChanged(accountId: String, devices: Map<String, String>)
    fun onDeviceRevocationEnded(accountId: String, deviceId: String, state: Int)
    fun onMigrationEnded(accountId: String, state: String)
    fun onAccountProfileReceived(accountId: String, name: String, photo: String)

    // ==================== Call Callbacks ====================
    fun onCallStateChanged(accountId: String, callId: String, state: String, code: Int)
    fun onIncomingCall(accountId: String, callId: String, from: String)
    fun onIncomingCallWithMedia(accountId: String, callId: String, from: String, mediaList: List<Map<String, String>>)
    fun onMediaChangeRequested(accountId: String, callId: String, mediaList: List<Map<String, String>>)
    fun onAudioMuted(callId: String, muted: Boolean)
    fun onVideoMuted(callId: String, muted: Boolean)
    fun onMediaNegotiationStatus(callId: String, event: String, mediaList: List<Map<String, String>>)
    fun onConferenceCreated(accountId: String, conversationId: String, confId: String)
    fun onConferenceChanged(accountId: String, confId: String, state: String)
    fun onConferenceRemoved(accountId: String, confId: String)

    // ==================== Conversation Callbacks ====================
    fun onConversationReady(accountId: String, conversationId: String)
    fun onConversationRemoved(accountId: String, conversationId: String)
    fun onConversationRequestReceived(accountId: String, conversationId: String, metadata: Map<String, String>)
    fun onConversationRequestDeclined(accountId: String, conversationId: String)
    fun onConversationMemberEvent(accountId: String, conversationId: String, memberId: String, event: Int)
    fun onMessageReceived(accountId: String, conversationId: String, message: SwarmMessage)
    fun onMessageUpdated(accountId: String, conversationId: String, message: SwarmMessage)
    fun onMessagesFound(messageId: Int, accountId: String, conversationId: String, messages: List<Map<String, String>>)
    fun onSwarmLoaded(id: Long, accountId: String, conversationId: String, messages: List<SwarmMessage>)
    fun onConversationProfileUpdated(accountId: String, conversationId: String, profile: Map<String, String>)
    fun onConversationPreferencesUpdated(accountId: String, conversationId: String, preferences: Map<String, String>)
    fun onReactionAdded(accountId: String, conversationId: String, messageId: String, reaction: Map<String, String>)
    fun onReactionRemoved(accountId: String, conversationId: String, messageId: String, reactionId: String)
    fun onActiveCallsChanged(accountId: String, conversationId: String, activeCalls: List<Map<String, String>>)

    // ==================== Presence Callbacks ====================
    fun onNewBuddyNotification(accountId: String, buddyUri: String, status: Int, lineStatus: String)

    // ==================== Contact Callbacks ====================
    fun onContactAdded(accountId: String, uri: String, confirmed: Boolean)
    fun onContactRemoved(accountId: String, uri: String, banned: Boolean)
    fun onIncomingTrustRequest(accountId: String, conversationId: String, from: String, payload: ByteArray, receiveTime: Long)
    fun onProfileReceived(accountId: String, peerId: String, vcardPath: String)

    // ==================== Message Callbacks ====================
    fun onIncomingAccountMessage(accountId: String, messageId: String?, callId: String?, from: String, messages: Map<String, String>)
    fun onAccountMessageStatusChanged(accountId: String, conversationId: String, messageId: String, contactId: String, status: Int)
    fun onComposingStatusChanged(accountId: String, conversationId: String, contactUri: String, status: Int)

    // ==================== Name Service Callbacks ====================
    fun onNameRegistrationEnded(accountId: String, state: Int, name: String)
    fun onRegisteredNameFound(accountId: String, state: Int, address: String, name: String, query: String = "")
    fun onUserSearchEnded(accountId: String, state: Int, query: String, results: List<Map<String, String>>)

    // ==================== Data Transfer Callbacks ====================
    fun onDataTransferEvent(accountId: String, conversationId: String, interactionId: String, fileId: String, eventCode: Int)
}

/**
 * No-op stub implementation of [DaemonBridgeApi] for use in tests.
 *
 * Controllable fields allow tests to set up specific scenarios:
 *
 * ```kotlin
 * val stub = StubDaemonBridge()
 * stub.accountList = listOf("acc1", "acc2")
 * stub.accountDetails["acc1"] = mapOf("Account.displayName" to "Alice")
 * ```
 */
class StubDaemonBridge : DaemonBridgeApi {
    // Controllable test state
    var accountIds: List<String> = emptyList()
    var accountDetails: MutableMap<String, Map<String, String>> = mutableMapOf()
    var volatileAccountDetails: MutableMap<String, Map<String, String>> = mutableMapOf()
    var accountTemplate: Map<String, String> = emptyMap()
    var conversations: MutableMap<String, List<String>> = mutableMapOf()
    var conversationMembers: MutableMap<String, List<Map<String, String>>> = mutableMapOf()
    var conversationInfo: MutableMap<String, Map<String, String>> = mutableMapOf()
    var contacts: MutableMap<String, List<Map<String, String>>> = mutableMapOf()
    var codecs: List<Long> = emptyList()
    var addAccountResult: String = ""
    var placeCallResult: String = ""
    var startConversationResult: String = ""
    var running: Boolean = false

    override fun init(callbacks: DaemonCallbacks): Boolean = true
    override fun start(): Boolean { running = true; return true }
    override fun stop() { running = false }
    override fun isRunning(): Boolean = running

    override fun addAccount(details: Map<String, String>): String = addAccountResult
    override fun removeAccount(accountId: String) {}
    override fun getAccountDetails(accountId: String): Map<String, String> = accountDetails[accountId] ?: emptyMap()
    override fun setAccountDetails(accountId: String, details: Map<String, String>) { accountDetails[accountId] = details }
    override fun getAccountList(): List<String> = accountIds
    override fun setAccountActive(accountId: String, active: Boolean) {}
    override fun getAccountTemplate(accountType: String): Map<String, String> = accountTemplate
    override fun getVolatileAccountDetails(accountId: String): Map<String, String> = volatileAccountDetails[accountId] ?: emptyMap()
    override fun sendRegister(accountId: String, enable: Boolean) {}
    override fun setAccountsOrder(order: String) {}
    override fun changeAccountPassword(accountId: String, oldPassword: String, newPassword: String): Boolean = true
    override fun exportToFile(accountId: String, path: String, scheme: String, password: String): Boolean = true

    override fun getCredentials(accountId: String): List<Map<String, String>> = emptyList()
    override fun setCredentials(accountId: String, credentials: List<Map<String, String>>) {}

    override fun getKnownRingDevices(accountId: String): Map<String, String> = emptyMap()
    override fun revokeDevice(accountId: String, deviceId: String, scheme: String, password: String) {}
    override fun setDeviceName(accountId: String, deviceName: String) {}

    override fun updateProfile(accountId: String, displayName: String, avatar: String, fileType: String, flag: Int) {}

    override fun placeCall(accountId: String, uri: String, mediaList: List<MediaAttribute>): String = placeCallResult
    override fun accept(accountId: String, callId: String, mediaList: List<MediaAttribute>) {}
    override fun hangUp(accountId: String, callId: String) {}
    override fun hold(accountId: String, callId: String) {}
    override fun unhold(accountId: String, callId: String) {}
    override fun muteLocalMedia(accountId: String, callId: String, mediaType: String, mute: Boolean) {}

    override fun holdConference(accountId: String, confId: String): Boolean = true
    override fun unholdConference(accountId: String, confId: String): Boolean = true
    override fun setActiveParticipant(accountId: String, confId: String, callId: String) {}
    override fun setConferenceLayout(accountId: String, confId: String, layout: Int) {}

    override fun getConversations(accountId: String): List<String> = conversations[accountId] ?: emptyList()
    override fun startConversation(accountId: String): String = startConversationResult
    override fun sendMessage(accountId: String, conversationId: String, message: String, replyTo: String, flag: Int) {}
    override fun loadConversation(accountId: String, conversationId: String, fromMessage: String, size: Int) {}
    override fun getConversationMembers(accountId: String, conversationId: String): List<Map<String, String>> = conversationMembers[conversationId] ?: emptyList()
    override fun getConversationInfo(accountId: String, conversationId: String): Map<String, String> = conversationInfo[conversationId] ?: emptyMap()
    override fun removeConversation(accountId: String, conversationId: String) {}
    override fun addConversationMember(accountId: String, conversationId: String, uri: String) {}
    override fun removeConversationMember(accountId: String, conversationId: String, uri: String) {}
    override fun updateConversationInfo(accountId: String, conversationId: String, info: Map<String, String>) {}
    override fun getConversationPreferences(accountId: String, conversationId: String): Map<String, String> = emptyMap()
    override fun setConversationPreferences(accountId: String, conversationId: String, prefs: Map<String, String>) {}
    override fun setMessageDisplayed(accountId: String, conversationUri: String, messageId: String, status: Int) {}
    override fun getActiveCalls(accountId: String, conversationId: String): List<Map<String, String>> = emptyList()

    override fun getConversationRequests(accountId: String): List<Map<String, String>> = emptyList()
    override fun acceptConversationRequest(accountId: String, conversationId: String) {}
    override fun declineConversationRequest(accountId: String, conversationId: String) {}

    override fun getTrustRequests(accountId: String): List<Map<String, String>> = emptyList()
    override fun acceptTrustRequest(accountId: String, uri: String) {}
    override fun discardTrustRequest(accountId: String, uri: String) {}
    override fun sendTrustRequest(accountId: String, uri: String, payload: ByteArray) {}

    override fun addContact(accountId: String, uri: String) {}
    override fun removeContact(accountId: String, uri: String, ban: Boolean) {}
    override fun getContacts(accountId: String): List<Map<String, String>> = contacts[accountId] ?: emptyList()
    override fun getContactDetails(accountId: String, uri: String): Map<String, String> = emptyMap()
    override fun subscribeBuddy(accountId: String, uri: String, subscribe: Boolean) {}

    override fun lookupName(accountId: String, nameServiceUrl: String, name: String): Boolean = true
    override fun lookupAddress(accountId: String, nameServiceUrl: String, address: String): Boolean = true
    override fun registerName(accountId: String, name: String, scheme: String, password: String): Boolean = true
    override fun searchUser(accountId: String, query: String): Boolean = true

    override fun sendTextMessage(accountId: String, callIdOrUri: String, message: String) {}
    override fun setIsComposing(accountId: String, uri: String, isComposing: Boolean) {}
    override fun cancelMessage(accountId: String, messageId: Long): Boolean = true

    override fun sendFile(accountId: String, conversationId: String, filePath: String, displayName: String, parent: String) {}
    override fun downloadFile(accountId: String, conversationId: String, interactionId: String, fileId: String, path: String) {}
    override fun cancelDataTransfer(accountId: String, conversationId: String, fileId: String) {}
    override fun fileTransferInfo(accountId: String, conversationId: String, fileId: String): FileTransferInfo? = null

    override fun getCodecList(): List<Long> = codecs
    override fun getActiveCodecList(accountId: String): List<Long> = codecs
    override fun setActiveCodecList(accountId: String, codecList: List<Long>) {}
    override fun getCodecDetails(accountId: String, codecId: Long): Map<String, String> = emptyMap()

    override fun searchConversation(accountId: String, conversationId: String, author: String, lastId: String, query: String, type: String, after: Long, before: Long, maxResult: Long, flag: Int): Long = 0L
    override fun loadSwarmUntil(accountId: String, conversationId: String, fromMessage: String, toMessage: String): Long = 0L

    override fun setPushNotificationToken(token: String) {}
    override fun setPushNotificationConfig(config: Map<String, String>) {}
    override fun pushNotificationReceived(from: String, data: Map<String, String>) {}
}
