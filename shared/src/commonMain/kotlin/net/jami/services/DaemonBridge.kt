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
    /** Initiate linking a new device using its device-request URI. Returns an operation ID ≥ 0, or negative on error. */
    fun addDevice(accountId: String, uri: String): Long
    /** Confirm the peer identity during the export-side add-device handshake. */
    fun confirmAddDevice(accountId: String, opId: Long): Boolean
    /** Cancel an in-progress add-device operation. */
    fun cancelAddDevice(accountId: String, opId: Long): Boolean
    /** Provide authentication (password) to unlock an account being imported on this device. */
    fun provideAccountAuthentication(accountId: String, password: String, scheme: String): Boolean

    // ==================== Profile ====================
    fun updateProfile(accountId: String, displayName: String, avatar: String, fileType: String, flag: Int)

    // ==================== Call Operations ====================
    fun placeCall(accountId: String, uri: String, mediaList: List<MediaAttribute>): String
    fun accept(accountId: String, callId: String, mediaList: List<MediaAttribute>)
    fun refuse(accountId: String, callId: String)
    fun hangUp(accountId: String, callId: String)
    fun hold(accountId: String, callId: String)
    fun unhold(accountId: String, callId: String)
    fun resume(accountId: String, callId: String): Boolean
    fun muteLocalMedia(accountId: String, callId: String, mediaType: String, mute: Boolean)

    fun playDtmf(key: String)
    fun muteRingtone(mute: Boolean)
    fun muteCapture(mute: Boolean)
    fun isCaptureMuted(): Boolean
    fun transfer(accountId: String, callId: String, to: String): Boolean
    fun attendedTransfer(accountId: String, transferId: String, targetId: String): Boolean
    fun getCallDetails(accountId: String, callId: String): Map<String, String>

    // ==================== Conference Operations ====================
    fun holdConference(accountId: String, confId: String): Boolean
    fun unholdConference(accountId: String, confId: String): Boolean
    fun resumeConference(accountId: String, confId: String): Boolean
    fun setActiveParticipant(accountId: String, confId: String, callId: String)
    fun setConferenceLayout(accountId: String, confId: String, layout: Int)
    fun hangUpConference(accountId: String, confId: String): Boolean
    fun joinParticipant(accountId: String, selCallId: String, account2Id: String, dragCallId: String): Boolean
    fun addParticipant(accountId: String, callId: String, account2Id: String, confId: String): Boolean
    fun addMainParticipant(accountId: String, confId: String): Boolean
    fun detachParticipant(accountId: String, callId: String): Boolean
    fun getParticipantList(accountId: String, confId: String): List<String>
    fun getConferenceDetails(accountId: String, confId: String): Map<String, String>

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

    /**
     * Send a message with multiple mime types to a conversation.
     * Used for geolocation sharing and other special message types.
     *
     * @param accountId The account ID
     * @param conversationId The conversation ID
     * @param messages Map of mime type to content (e.g., "application/geo" -> JSON)
     * @param flag Message flag (0 = normal, 1 = edit/delete, etc.)
     */
    fun sendAccountTextMessage(accountId: String, conversationId: String, messages: Map<String, String>, flag: Int)

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

    // ==================== Video Device Management ====================
    /**
     * Register a video capture device with the daemon.
     * Called when a camera becomes available.
     */
    fun addVideoDevice(deviceId: String)

    /**
     * Unregister a video capture device from the daemon.
     * Called when a camera is disconnected or unavailable.
     */
    fun removeVideoDevice(deviceId: String)

    /**
     * Set the default video capture device.
     */
    fun setDefaultDevice(deviceId: String)

    /**
     * Set the device orientation for video rotation correction.
     *
     * @param deviceId Camera device ID
     * @param rotation Rotation in degrees (0, 90, 180, 270)
     */
    fun setDeviceOrientation(deviceId: String, rotation: Int)

    /**
     * Apply camera settings to a device.
     *
     * @param deviceId Camera device ID
     * @param settings Map of setting key-value pairs
     */
    fun applySettings(deviceId: String, settings: Map<String, String>)

    // ==================== Video Frame Capture ====================
    /**
     * Send a raw video frame to the daemon for encoding.
     * Used when software encoding is needed.
     *
     * @param uri Video input URI (e.g., "camera://0")
     * @param data Raw frame data (typically NV21 or YUV format)
     * @param rotation Frame rotation in degrees
     */
    fun captureVideoFrame(uri: String, data: ByteArray, rotation: Int)

    /**
     * Send an encoded video packet to the daemon.
     * Used with hardware encoding (MediaCodec on Android).
     *
     * @param uri Video input URI
     * @param data Encoded packet data
     * @param size Packet size in bytes
     * @param offset Offset into the data buffer
     * @param isKeyFrame Whether this is a keyframe
     * @param timestamp Presentation timestamp in microseconds
     * @param rotation Frame rotation in degrees
     */
    fun captureVideoPacket(
        uri: String,
        data: Any,
        size: Int,
        offset: Int,
        isKeyFrame: Boolean,
        timestamp: Long,
        rotation: Int
    )

    // ==================== Native Window Management ====================
    /**
     * Acquire a native window for video rendering.
     *
     * @param surface Platform-specific surface (SurfaceHolder on Android)
     * @return Native window handle, or 0 on failure
     */
    fun acquireNativeWindow(surface: Any): Long

    /**
     * Release a native window.
     *
     * @param windowId Native window handle from acquireNativeWindow
     */
    fun releaseNativeWindow(windowId: Long)

    /**
     * Set the geometry of a native window.
     *
     * @param windowId Native window handle
     * @param width Window width in pixels
     * @param height Window height in pixels
     */
    fun setNativeWindowGeometry(windowId: Long, width: Int, height: Int)

    // ==================== Video Callback Registration ====================
    /**
     * Register for video callbacks from a specific sink.
     *
     * @param id Video sink identifier
     * @param windowId Native window handle to render to
     * @return true if registration succeeded
     */
    fun registerVideoCallback(id: String, windowId: Long): Boolean

    /**
     * Unregister video callbacks for a sink.
     *
     * @param id Video sink identifier
     * @param windowId Native window handle
     */
    fun unregisterVideoCallback(id: String, windowId: Long)

    // ==================== Video Input Switching ====================
    /**
     * Switch the video input source for an active call.
     *
     * @param accountId Account ID
     * @param callId Call ID
     * @param uri New video input URI (e.g., "camera://1" or "camera://desktop")
     */
    fun switchVideoInput(accountId: String, callId: String, uri: String)

    /**
     * Request a media change for an active call (renegotiates the media set with the peer).
     * Used for screen sharing — changes the video source without adding a new call leg.
     *
     * @param accountId Account ID
     * @param callId Call/conference ID
     * @param mediaList New media attributes list (each entry is a String→String map)
     */
    fun requestMediaChange(accountId: String, callId: String, mediaList: List<Map<String, String>>)

    /**
     * Answer an incoming media-change request from the peer (or the daemon).
     * Called in response to the onMediaChangeRequested callback to confirm or
     * adjust the proposed media set and let the daemon complete the renegotiation.
     *
     * @param accountId Account ID
     * @param callId    Call ID the request applies to
     * @param mediaList Accepted/adjusted media list (each entry is a String→String map)
     */
    fun answerMediaChangeRequest(accountId: String, callId: String, mediaList: List<Map<String, String>>)

    // ==================== Conference Participant Controls ====================
    /**
     * Mute audio for all participants in a conference.
     *
     * @param accountId Account ID
     * @param confId Conference ID
     */
    fun muteAllParticipants(accountId: String, confId: String)

    /**
     * Lock or unlock a conference.
     *
     * @param accountId Account ID
     * @param confId Conference ID
     * @param locked true to lock, false to unlock
     */
    fun setConferenceLocked(accountId: String, confId: String, locked: Boolean)

    /**
     * Mute audio for a specific participant in a conference.
     *
     * @param accountId Account ID
     * @param confId Conference ID
     * @param participantId Participant ID
     */
    fun muteParticipantAudio(accountId: String, confId: String, participantId: String)

    /**
     * Unmute audio for a specific participant in a conference.
     *
     * @param accountId Account ID
     * @param confId Conference ID
     * @param participantId Participant ID
     */
    fun unmuteParticipantAudio(accountId: String, confId: String, participantId: String)

    /**
     * Disable video for a specific participant in a conference.
     *
     * @param accountId Account ID
     * @param confId Conference ID
     * @param participantId Participant ID
     */
    fun disableParticipantVideo(accountId: String, confId: String, participantId: String)

    /**
     * Enable video for a specific participant in a conference.
     *
     * @param accountId Account ID
     * @param confId Conference ID
     * @param participantId Participant ID
     */
    fun enableParticipantVideo(accountId: String, confId: String, participantId: String)
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
    fun onAddDeviceStateChanged(accountId: String, opId: Long, state: Int, details: Map<String, String>)
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
    fun onConferenceInfoUpdated(confId: String, info: List<Map<String, String>>)

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

    // Configurable lookup results: name -> LookupState (0=Success/taken, 1=Invalid, 2=NotFound/available, 3=NetworkError)
    var lookupNameResults: MutableMap<String, Int> = mutableMapOf()
    // Callback for when lookupName is called - set this to accountService.onRegisteredNameFound in tests
    var onLookupNameCallback: ((accountId: String, state: Int, address: String, name: String, query: String) -> Unit)? = null

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
    override fun addDevice(accountId: String, uri: String): Long = 0L
    override fun confirmAddDevice(accountId: String, opId: Long): Boolean = true
    override fun cancelAddDevice(accountId: String, opId: Long): Boolean = true
    override fun provideAccountAuthentication(accountId: String, password: String, scheme: String): Boolean = true

    override fun updateProfile(accountId: String, displayName: String, avatar: String, fileType: String, flag: Int) {}

    override fun placeCall(accountId: String, uri: String, mediaList: List<MediaAttribute>): String = placeCallResult
    override fun accept(accountId: String, callId: String, mediaList: List<MediaAttribute>) {}
    override fun refuse(accountId: String, callId: String) {}
    override fun hangUp(accountId: String, callId: String) {}
    override fun hold(accountId: String, callId: String) {}
    override fun unhold(accountId: String, callId: String) {}
    override fun resume(accountId: String, callId: String): Boolean = true
    override fun muteLocalMedia(accountId: String, callId: String, mediaType: String, mute: Boolean) {}
    override fun playDtmf(key: String) {}
    override fun muteRingtone(mute: Boolean) {}
    override fun muteCapture(mute: Boolean) {}
    override fun isCaptureMuted(): Boolean = false
    override fun transfer(accountId: String, callId: String, to: String): Boolean = true
    override fun attendedTransfer(accountId: String, transferId: String, targetId: String): Boolean = true
    override fun getCallDetails(accountId: String, callId: String): Map<String, String> = emptyMap()

    override fun holdConference(accountId: String, confId: String): Boolean = true
    override fun unholdConference(accountId: String, confId: String): Boolean = true
    override fun resumeConference(accountId: String, confId: String): Boolean = true
    override fun setActiveParticipant(accountId: String, confId: String, callId: String) {}
    override fun setConferenceLayout(accountId: String, confId: String, layout: Int) {}
    override fun hangUpConference(accountId: String, confId: String): Boolean = true
    override fun joinParticipant(accountId: String, selCallId: String, account2Id: String, dragCallId: String): Boolean = true
    override fun addParticipant(accountId: String, callId: String, account2Id: String, confId: String): Boolean = true
    override fun addMainParticipant(accountId: String, confId: String): Boolean = true
    override fun detachParticipant(accountId: String, callId: String): Boolean = true
    override fun getParticipantList(accountId: String, confId: String): List<String> = emptyList()
    override fun getConferenceDetails(accountId: String, confId: String): Map<String, String> = emptyMap()

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

    override fun lookupName(accountId: String, nameServiceUrl: String, name: String): Boolean {
        val state = lookupNameResults[name] ?: 2  // Default to NotFound (available)
        val address = if (state == 0) "0x${name.hashCode().toString(16)}" else ""
        onLookupNameCallback?.invoke(accountId, state, address, name, name)
        return true
    }
    override fun lookupAddress(accountId: String, nameServiceUrl: String, address: String): Boolean = true
    override fun registerName(accountId: String, name: String, scheme: String, password: String): Boolean = true
    override fun searchUser(accountId: String, query: String): Boolean = true

    override fun sendTextMessage(accountId: String, callIdOrUri: String, message: String) {}
    override fun setIsComposing(accountId: String, uri: String, isComposing: Boolean) {}
    override fun cancelMessage(accountId: String, messageId: Long): Boolean = true
    override fun sendAccountTextMessage(accountId: String, conversationId: String, messages: Map<String, String>, flag: Int) {}

    override fun sendFile(accountId: String, conversationId: String, filePath: String, displayName: String, parent: String) {}
    override fun downloadFile(accountId: String, conversationId: String, interactionId: String, fileId: String, path: String) {}
    override fun cancelDataTransfer(accountId: String, conversationId: String, fileId: String) {}
    override fun fileTransferInfo(accountId: String, conversationId: String, fileId: String): FileTransferInfo? = null

    override fun getCodecList(): List<Long> = codecs
    override fun getActiveCodecList(accountId: String): List<Long> = codecs
    override fun setActiveCodecList(accountId: String, codecList: List<Long>) {}
    override fun getCodecDetails(accountId: String, codecId: Long): Map<String, String> = emptyMap()

    private var nextTaskId: Long = 1L
    override fun searchConversation(accountId: String, conversationId: String, author: String, lastId: String, query: String, type: String, after: Long, before: Long, maxResult: Long, flag: Int): Long = nextTaskId++
    override fun loadSwarmUntil(accountId: String, conversationId: String, fromMessage: String, toMessage: String): Long = nextTaskId++

    override fun setPushNotificationToken(token: String) {}
    override fun setPushNotificationConfig(config: Map<String, String>) {}
    override fun pushNotificationReceived(from: String, data: Map<String, String>) {}

    // Video stubs
    override fun addVideoDevice(deviceId: String) {}
    override fun removeVideoDevice(deviceId: String) {}
    override fun setDefaultDevice(deviceId: String) {}
    override fun setDeviceOrientation(deviceId: String, rotation: Int) {}
    override fun applySettings(deviceId: String, settings: Map<String, String>) {}
    override fun captureVideoFrame(uri: String, data: ByteArray, rotation: Int) {}
    override fun captureVideoPacket(uri: String, data: Any, size: Int, offset: Int, isKeyFrame: Boolean, timestamp: Long, rotation: Int) {}
    override fun acquireNativeWindow(surface: Any): Long = 0L
    override fun releaseNativeWindow(windowId: Long) {}
    override fun setNativeWindowGeometry(windowId: Long, width: Int, height: Int) {}
    override fun registerVideoCallback(id: String, windowId: Long): Boolean = false
    override fun unregisterVideoCallback(id: String, windowId: Long) {}
    override fun switchVideoInput(accountId: String, callId: String, uri: String) {}
    override fun requestMediaChange(accountId: String, callId: String, mediaList: List<Map<String, String>>) {}
    override fun answerMediaChangeRequest(accountId: String, callId: String, mediaList: List<Map<String, String>>) {}

    override fun muteAllParticipants(accountId: String, confId: String) {}
    override fun setConferenceLocked(accountId: String, confId: String, locked: Boolean) {}
    override fun muteParticipantAudio(accountId: String, confId: String, participantId: String) {}
    override fun unmuteParticipantAudio(accountId: String, confId: String, participantId: String) {}
    override fun disableParticipantVideo(accountId: String, confId: String, participantId: String) {}
    override fun enableParticipantVideo(accountId: String, confId: String, participantId: String) {}
}
