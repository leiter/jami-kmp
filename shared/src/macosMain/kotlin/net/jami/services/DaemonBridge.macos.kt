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
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package net.jami.services

import kotlinx.cinterop.*
import net.jami.bridge.*
import net.jami.model.MediaAttribute
import net.jami.model.SwarmMessage
import net.jami.utils.Log
import platform.Foundation.*
import platform.darwin.NSObject

/**
 * macOS implementation of DaemonBridge using JamiBridge Objective-C++ wrapper via cinterop.
 */
actual class DaemonBridge actual constructor() {
    private var isInitialized = false
    private var callbacks: DaemonCallbacks? = null
    private val bridge: JamiBridgeWrapper = JamiBridgeWrapper.shared()
    private var delegateImpl: JamiBridgeDelegateImpl? = null

    companion object {
        private const val TAG = "DaemonBridge.macOS"
    }

    // ==================== Lifecycle ====================

    actual fun init(callbacks: DaemonCallbacks): Boolean {
        this.callbacks = callbacks

        // Create and set delegate
        delegateImpl = JamiBridgeDelegateImpl(callbacks)
        bridge.delegate = delegateImpl

        // Get data path from app support directory
        val paths = NSSearchPathForDirectoriesInDomains(
            NSApplicationSupportDirectory,
            NSUserDomainMask,
            true
        )
        @Suppress("UNCHECKED_CAST")
        val pathsList = paths as? List<String> ?: return false
        val appSupportPath = pathsList.firstOrNull() ?: return false
        val dataPath = "$appSupportPath/jami"

        // Create directory if needed
        val fileManager = NSFileManager.defaultManager
        if (!fileManager.fileExistsAtPath(dataPath)) {
            fileManager.createDirectoryAtPath(
                dataPath,
                withIntermediateDirectories = true,
                attributes = null,
                error = null
            )
        }

        bridge.initDaemonWithDataPath(dataPath)
        isInitialized = true
        Log.i(TAG, "DaemonBridge initialized with path: $dataPath")
        return true
    }

    actual fun start(): Boolean {
        if (!isInitialized) return false
        bridge.startDaemon()
        Log.i(TAG, "Daemon started")
        return true
    }

    actual fun stop() {
        bridge.stopDaemon()
        isInitialized = false
        Log.i(TAG, "Daemon stopped")
    }

    actual fun isRunning(): Boolean = bridge.isDaemonRunning()

    // ==================== Account Operations ====================

    actual fun addAccount(details: Map<String, String>): String {
        val displayName = details["Account.displayName"] ?: ""
        val password = details["Account.archivePassword"] ?: ""
        return bridge.createAccountWithDisplayName(displayName, password = password) ?: ""
    }

    actual fun removeAccount(accountId: String) {
        bridge.deleteAccount(accountId)
    }

    actual fun getAccountDetails(accountId: String): Map<String, String> {
        return bridge.getAccountDetails(accountId)?.toKotlinMap() ?: emptyMap()
    }

    actual fun setAccountDetails(accountId: String, details: Map<String, String>) {
        bridge.setAccountDetails(accountId, details = details.toNSDictionary())
    }

    actual fun getAccountList(): List<String> {
        return bridge.getAccountIds()?.toKotlinList() ?: emptyList()
    }

    actual fun setAccountActive(accountId: String, active: Boolean) {
        bridge.setAccountActive(accountId, active = active)
    }

    actual fun getAccountTemplate(accountType: String): Map<String, String> {
        return emptyMap()
    }

    actual fun getVolatileAccountDetails(accountId: String): Map<String, String> {
        return bridge.getVolatileAccountDetails(accountId)?.toKotlinMap() ?: emptyMap()
    }

    actual fun sendRegister(accountId: String, enable: Boolean) {
        bridge.setAccountActive(accountId, active = enable)
    }

    actual fun setAccountsOrder(order: String) {
        // Not exposed in JamiBridge
    }

    actual fun changeAccountPassword(accountId: String, oldPassword: String, newPassword: String): Boolean {
        return false
    }

    actual fun exportToFile(accountId: String, path: String, scheme: String, password: String): Boolean {
        return bridge.exportAccount(accountId, toDestinationPath = path, withPassword = password)
    }

    // ==================== Credentials ====================

    actual fun getCredentials(accountId: String): List<Map<String, String>> {
        return emptyList()
    }

    actual fun setCredentials(accountId: String, credentials: List<Map<String, String>>) {
        // Not exposed in JamiBridge
    }

    // ==================== Device Management ====================

    actual fun getKnownRingDevices(accountId: String): Map<String, String> {
        return emptyMap()
    }

    actual fun revokeDevice(accountId: String, deviceId: String, scheme: String, password: String) {
        // Not exposed in JamiBridge
    }

    actual fun setDeviceName(accountId: String, deviceName: String) {
        // Not exposed in JamiBridge
    }

    // ==================== Profile ====================

    actual fun updateProfile(accountId: String, displayName: String, avatar: String, fileType: String, flag: Int) {
        bridge.updateProfile(accountId, displayName = displayName, avatarPath = avatar.takeIf { it.isNotEmpty() })
    }

    // ==================== Call Operations ====================

    actual fun placeCall(accountId: String, uri: String, mediaList: List<MediaAttribute>): String {
        val hasVideo = mediaList.any { it.mediaType.name == "VIDEO" && it.enabled }
        return bridge.placeCall(accountId, uri = uri, withVideo = hasVideo) ?: ""
    }

    actual fun accept(accountId: String, callId: String, mediaList: List<MediaAttribute>) {
        val hasVideo = mediaList.any { it.mediaType.name == "VIDEO" && it.enabled }
        bridge.acceptCall(accountId, callId = callId, withVideo = hasVideo)
    }

    actual fun hangUp(accountId: String, callId: String) {
        bridge.hangUp(accountId, callId = callId)
    }

    actual fun hold(accountId: String, callId: String) {
        bridge.holdCall(accountId, callId = callId)
    }

    actual fun unhold(accountId: String, callId: String) {
        bridge.unholdCall(accountId, callId = callId)
    }

    actual fun muteLocalMedia(accountId: String, callId: String, mediaType: String, mute: Boolean) {
        if (mediaType == "MEDIA_TYPE_AUDIO") {
            bridge.muteAudio(accountId, callId = callId, muted = mute)
        } else if (mediaType == "MEDIA_TYPE_VIDEO") {
            bridge.muteVideo(accountId, callId = callId, muted = mute)
        }
    }

    // ==================== Conversation Operations ====================

    actual fun getConversations(accountId: String): List<String> {
        return bridge.getConversations(accountId)?.toKotlinList() ?: emptyList()
    }

    actual fun startConversation(accountId: String): String {
        return bridge.startConversation(accountId) ?: ""
    }

    actual fun sendMessage(
        accountId: String,
        conversationId: String,
        message: String,
        replyTo: String,
        flag: Int
    ) {
        bridge.sendMessage(accountId, conversationId = conversationId, message = message, replyTo = replyTo.takeIf { it.isNotEmpty() })
    }

    actual fun loadConversation(
        accountId: String,
        conversationId: String,
        fromMessage: String,
        size: Int
    ) {
        bridge.loadConversationMessages(accountId, conversationId = conversationId, fromMessage = fromMessage, count = size)
    }

    actual fun getConversationMembers(accountId: String, conversationId: String): List<Map<String, String>> {
        val members = bridge.getConversationMembers(accountId, conversationId = conversationId) ?: return emptyList()
        @Suppress("UNCHECKED_CAST")
        val memberList = members as? List<Any> ?: return emptyList()
        return memberList.mapNotNull { member ->
            val jbMember = member as? JBConversationMember ?: return@mapNotNull null
            mapOf(
                "uri" to (jbMember.uri ?: ""),
                "role" to jbMember.role.toString()
            )
        }
    }

    actual fun getConversationInfo(accountId: String, conversationId: String): Map<String, String> {
        return bridge.getConversationInfo(accountId, conversationId = conversationId)?.toKotlinMap() ?: emptyMap()
    }

    actual fun removeConversation(accountId: String, conversationId: String) {
        bridge.removeConversation(accountId, conversationId = conversationId)
    }

    actual fun addConversationMember(accountId: String, conversationId: String, uri: String) {
        bridge.addConversationMember(accountId, conversationId = conversationId, contactUri = uri)
    }

    actual fun removeConversationMember(accountId: String, conversationId: String, uri: String) {
        bridge.removeConversationMember(accountId, conversationId = conversationId, contactUri = uri)
    }

    actual fun updateConversationInfo(accountId: String, conversationId: String, info: Map<String, String>) {
        bridge.updateConversationInfo(accountId, conversationId = conversationId, info = info.toNSDictionary())
    }

    actual fun getConversationPreferences(accountId: String, conversationId: String): Map<String, String> {
        return emptyMap()
    }

    actual fun setConversationPreferences(accountId: String, conversationId: String, prefs: Map<String, String>) {
        // Not exposed in JamiBridge
    }

    actual fun setMessageDisplayed(accountId: String, conversationUri: String, messageId: String, status: Int) {
        bridge.setMessageDisplayed(accountId, conversationId = conversationUri, messageId = messageId)
    }

    actual fun getActiveCalls(accountId: String, conversationId: String): List<Map<String, String>> {
        return emptyList()
    }

    // ==================== Conversation Requests ====================

    actual fun getConversationRequests(accountId: String): List<Map<String, String>> {
        val requests = bridge.getConversationRequests(accountId) ?: return emptyList()
        @Suppress("UNCHECKED_CAST")
        val requestList = requests as? List<Any> ?: return emptyList()
        return requestList.mapNotNull { request ->
            val jbRequest = request as? JBConversationRequest ?: return@mapNotNull null
            mapOf(
                "conversationId" to (jbRequest.conversationId ?: ""),
                "from" to (jbRequest.from ?: ""),
                "received" to jbRequest.received.toString()
            )
        }
    }

    actual fun acceptConversationRequest(accountId: String, conversationId: String) {
        bridge.acceptConversationRequest(accountId, conversationId = conversationId)
    }

    actual fun declineConversationRequest(accountId: String, conversationId: String) {
        bridge.declineConversationRequest(accountId, conversationId = conversationId)
    }

    // ==================== Trust Requests ====================

    actual fun getTrustRequests(accountId: String): List<Map<String, String>> {
        val requests = bridge.getTrustRequests(accountId) ?: return emptyList()
        @Suppress("UNCHECKED_CAST")
        val requestList = requests as? List<Any> ?: return emptyList()
        return requestList.mapNotNull { request ->
            val jbRequest = request as? JBTrustRequest ?: return@mapNotNull null
            mapOf(
                "from" to (jbRequest.from ?: ""),
                "conversationId" to (jbRequest.conversationId ?: ""),
                "received" to jbRequest.received.toString()
            )
        }
    }

    actual fun acceptTrustRequest(accountId: String, uri: String) {
        bridge.acceptTrustRequest(accountId, uri = uri)
    }

    actual fun discardTrustRequest(accountId: String, uri: String) {
        bridge.discardTrustRequest(accountId, uri = uri)
    }

    actual fun sendTrustRequest(accountId: String, uri: String, payload: ByteArray) {
        bridge.addContact(accountId, uri = uri)
    }

    // ==================== Contact Operations ====================

    actual fun addContact(accountId: String, uri: String) {
        bridge.addContact(accountId, uri = uri)
    }

    actual fun removeContact(accountId: String, uri: String, ban: Boolean) {
        bridge.removeContact(accountId, uri = uri, ban = ban)
    }

    actual fun getContacts(accountId: String): List<Map<String, String>> {
        val contacts = bridge.getContacts(accountId) ?: return emptyList()
        @Suppress("UNCHECKED_CAST")
        val contactList = contacts as? List<Any> ?: return emptyList()
        return contactList.mapNotNull { contact ->
            val jbContact = contact as? JBContact ?: return@mapNotNull null
            mapOf(
                "uri" to (jbContact.uri ?: ""),
                "displayName" to (jbContact.displayName ?: ""),
                "confirmed" to jbContact.isConfirmed.toString(),
                "banned" to jbContact.isBanned.toString()
            )
        }
    }

    actual fun getContactDetails(accountId: String, uri: String): Map<String, String> {
        return bridge.getContactDetails(accountId, uri = uri)?.toKotlinMap() ?: emptyMap()
    }

    actual fun subscribeBuddy(accountId: String, uri: String, subscribe: Boolean) {
        bridge.subscribeBuddy(accountId, uri = uri, flag = subscribe)
    }

    // ==================== Name Lookup ====================

    actual fun lookupName(accountId: String, nameServiceUrl: String, name: String): Boolean {
        val result = bridge.lookupName(accountId, name = name)
        return result != null && result.state == JBLookupState.JBLookupStateSuccess
    }

    actual fun lookupAddress(accountId: String, nameServiceUrl: String, address: String): Boolean {
        val result = bridge.lookupAddress(accountId, address = address)
        return result != null && result.state == JBLookupState.JBLookupStateSuccess
    }

    actual fun registerName(accountId: String, name: String, scheme: String, password: String): Boolean {
        return bridge.registerName(accountId, name = name, password = password)
    }

    actual fun searchUser(accountId: String, query: String): Boolean {
        return false
    }

    // ==================== Messaging ====================

    actual fun sendTextMessage(accountId: String, callIdOrUri: String, message: String) {
        // Not exposed in JamiBridge
    }

    actual fun setIsComposing(accountId: String, uri: String, isComposing: Boolean) {
        bridge.setIsComposing(accountId, conversationId = uri, isComposing = isComposing)
    }

    actual fun cancelMessage(accountId: String, messageId: Long): Boolean {
        return false
    }

    // ==================== File Transfer ====================

    actual fun sendFile(
        accountId: String,
        conversationId: String,
        filePath: String,
        displayName: String,
        parent: String
    ) {
        bridge.sendFile(accountId, conversationId = conversationId, filePath = filePath, displayName = displayName)
    }

    actual fun downloadFile(
        accountId: String,
        conversationId: String,
        interactionId: String,
        fileId: String,
        path: String
    ) {
        bridge.acceptFileTransfer(accountId, conversationId = conversationId, interactionId = interactionId, fileId = fileId, destinationPath = path)
    }

    actual fun cancelDataTransfer(accountId: String, conversationId: String, fileId: String) {
        bridge.cancelFileTransfer(accountId, conversationId = conversationId, fileId = fileId)
    }

    actual fun fileTransferInfo(accountId: String, conversationId: String, fileId: String): FileTransferInfo? {
        val info = bridge.getFileTransferInfo(accountId, conversationId = conversationId, fileId = fileId) ?: return null
        return FileTransferInfo(
            path = info.path ?: "",
            totalSize = info.totalSize,
            bytesProgress = info.progress
        )
    }

    // ==================== Codec Operations ====================

    actual fun getCodecList(): List<Long> {
        return emptyList()
    }

    actual fun getActiveCodecList(accountId: String): List<Long> {
        return emptyList()
    }

    actual fun setActiveCodecList(accountId: String, codecList: List<Long>) {
        // Not exposed
    }

    actual fun getCodecDetails(accountId: String, codecId: Long): Map<String, String> {
        return emptyMap()
    }

    // ==================== Push Notifications ====================

    actual fun setPushNotificationToken(token: String) {
        // Not exposed
    }

    actual fun setPushNotificationConfig(config: Map<String, String>) {
        // Not exposed
    }

    actual fun pushNotificationReceived(from: String, data: Map<String, String>) {
        // Not exposed
    }
}

// ==================== Extension Functions ====================

@Suppress("UNCHECKED_CAST")
private fun Any?.toKotlinMap(): Map<String, String> {
    val dict = this as? Map<*, *> ?: return emptyMap()
    val result = mutableMapOf<String, String>()
    for ((key, value) in dict) {
        val keyStr = key as? String ?: continue
        val valueStr = value as? String ?: continue
        result[keyStr] = valueStr
    }
    return result
}

@Suppress("UNCHECKED_CAST")
private fun Any?.toKotlinList(): List<String> {
    val array = this as? List<*> ?: return emptyList()
    return array.mapNotNull { it as? String }
}

@Suppress("UNCHECKED_CAST")
private fun Map<String, String>.toNSDictionary(): Map<Any?, *> {
    return this as Map<Any?, *>
}

// ==================== Delegate Implementation ====================

private class JamiBridgeDelegateImpl(
    private val callbacks: DaemonCallbacks
) : NSObject(), JamiBridgeDelegateProtocol {

    override fun onRegistrationStateChanged(
        accountId: String,
        state: JBRegistrationState,
        code: Int,
        detail: String
    ) {
        val stateStr = when (state) {
            JBRegistrationState.JBRegistrationStateUnregistered -> "UNREGISTERED"
            JBRegistrationState.JBRegistrationStateTrying -> "TRYING"
            JBRegistrationState.JBRegistrationStateRegistered -> "REGISTERED"
            JBRegistrationState.JBRegistrationStateErrorGeneric -> "ERROR_GENERIC"
            JBRegistrationState.JBRegistrationStateErrorAuth -> "ERROR_AUTH"
            JBRegistrationState.JBRegistrationStateErrorNetwork -> "ERROR_NETWORK"
            JBRegistrationState.JBRegistrationStateErrorHost -> "ERROR_HOST"
            JBRegistrationState.JBRegistrationStateErrorServiceUnavailable -> "ERROR_SERVICE_UNAVAILABLE"
            JBRegistrationState.JBRegistrationStateErrorNeedMigration -> "ERROR_NEED_MIGRATION"
            JBRegistrationState.JBRegistrationStateInitializing -> "INITIALIZING"
            else -> "UNKNOWN"
        }
        callbacks.onRegistrationStateChanged(accountId, stateStr, code, detail)
    }

    override fun onAccountDetailsChanged(accountId: String, details: Map<Any?, *>) {
        @Suppress("UNCHECKED_CAST")
        val detailsMap = (details as? Map<String, String>) ?: emptyMap()
        callbacks.onAccountDetailsChanged(accountId, detailsMap)
    }

    override fun onProfileReceived(
        accountId: String,
        from: String,
        displayName: String,
        avatarPath: String?
    ) {
        callbacks.onProfileReceived(accountId, from, avatarPath ?: displayName)
    }

    override fun onNameRegistrationEnded(accountId: String, state: Int, name: String) {
        callbacks.onNameRegistrationEnded(accountId, state, name)
    }

    override fun onRegisteredNameFound(
        accountId: String,
        state: JBLookupState,
        address: String,
        name: String
    ) {
        val stateInt = when (state) {
            JBLookupState.JBLookupStateSuccess -> 0
            JBLookupState.JBLookupStateNotFound -> 1
            JBLookupState.JBLookupStateInvalid -> 2
            JBLookupState.JBLookupStateError -> 3
            else -> 3
        }
        callbacks.onRegisteredNameFound(accountId, stateInt, address, name)
    }

    override fun onKnownDevicesChanged(accountId: String, devices: Map<Any?, *>) {
        @Suppress("UNCHECKED_CAST")
        val devicesMap = (devices as? Map<String, String>) ?: emptyMap()
        callbacks.onKnownDevicesChanged(accountId, devicesMap)
    }

    override fun onIncomingCall(
        accountId: String,
        callId: String,
        peerId: String,
        peerDisplayName: String,
        hasVideo: Boolean
    ) {
        val mediaList = if (hasVideo) {
            listOf(
                mapOf("MEDIA_TYPE" to "MEDIA_TYPE_AUDIO", "MUTED" to "false"),
                mapOf("MEDIA_TYPE" to "MEDIA_TYPE_VIDEO", "MUTED" to "false")
            )
        } else {
            listOf(mapOf("MEDIA_TYPE" to "MEDIA_TYPE_AUDIO", "MUTED" to "false"))
        }
        callbacks.onIncomingCallWithMedia(accountId, callId, peerId, mediaList)
    }

    override fun onCallStateChanged(
        accountId: String,
        callId: String,
        state: JBCallState,
        code: Int
    ) {
        val stateStr = when (state) {
            JBCallState.JBCallStateInactive -> "INACTIVE"
            JBCallState.JBCallStateIncoming -> "INCOMING"
            JBCallState.JBCallStateConnecting -> "CONNECTING"
            JBCallState.JBCallStateRinging -> "RINGING"
            JBCallState.JBCallStateCurrent -> "CURRENT"
            JBCallState.JBCallStateHungup -> "HUNGUP"
            JBCallState.JBCallStateBusy -> "BUSY"
            JBCallState.JBCallStateFailure -> "FAILURE"
            JBCallState.JBCallStateHold -> "HOLD"
            JBCallState.JBCallStateUnhold -> "UNHOLD"
            JBCallState.JBCallStateOver -> "OVER"
            else -> "UNKNOWN"
        }
        callbacks.onCallStateChanged(accountId, callId, stateStr, code)
    }

    override fun onMediaChangeRequested(accountId: String, callId: String, mediaList: List<*>) {
        @Suppress("UNCHECKED_CAST")
        val media = (mediaList as? List<Map<String, String>>) ?: emptyList()
        callbacks.onMediaChangeRequested(accountId, callId, media)
    }

    override fun onAudioMuted(callId: String, muted: Boolean) {
        callbacks.onAudioMuted(callId, muted)
    }

    override fun onVideoMuted(callId: String, muted: Boolean) {
        callbacks.onVideoMuted(callId, muted)
    }

    override fun onConferenceCreated(accountId: String, conversationId: String, conferenceId: String) {
        callbacks.onConferenceCreated(accountId, conversationId, conferenceId)
    }

    override fun onConferenceChanged(accountId: String, conferenceId: String, state: String) {
        callbacks.onConferenceChanged(accountId, conferenceId, state)
    }

    override fun onConferenceRemoved(accountId: String, conferenceId: String) {
        callbacks.onConferenceRemoved(accountId, conferenceId)
    }

    override fun onConferenceInfoUpdated(conferenceId: String, participantInfos: List<*>) {}

    override fun onConversationReady(accountId: String, conversationId: String) {
        callbacks.onConversationReady(accountId, conversationId)
    }

    override fun onConversationRemoved(accountId: String, conversationId: String) {
        callbacks.onConversationRemoved(accountId, conversationId)
    }

    override fun onConversationRequestReceived(accountId: String, conversationId: String, metadata: Map<Any?, *>) {
        @Suppress("UNCHECKED_CAST")
        val metadataMap = (metadata as? Map<String, String>) ?: emptyMap()
        callbacks.onConversationRequestReceived(accountId, conversationId, metadataMap)
    }

    override fun onMessageReceived(accountId: String, conversationId: String, message: JBSwarmMessage) {
        callbacks.onMessageReceived(accountId, conversationId, message.toKotlinSwarmMessage())
    }

    override fun onMessageUpdated(accountId: String, conversationId: String, message: JBSwarmMessage) {
        callbacks.onMessageUpdated(accountId, conversationId, message.toKotlinSwarmMessage())
    }

    override fun onMessagesLoaded(requestId: Int, accountId: String, conversationId: String, messages: List<*>) {
        val swarmMessages = messages.mapNotNull { (it as? JBSwarmMessage)?.toKotlinSwarmMessage() }
        callbacks.onSwarmLoaded(requestId.toLong(), accountId, conversationId, swarmMessages)
    }

    override fun onConversationMemberEvent(accountId: String, conversationId: String, memberUri: String, event: JBMemberEventType) {
        val eventInt = when (event) {
            JBMemberEventType.JBMemberEventTypeJoin -> 0
            JBMemberEventType.JBMemberEventTypeLeave -> 1
            JBMemberEventType.JBMemberEventTypeBan -> 2
            JBMemberEventType.JBMemberEventTypeUnban -> 3
            else -> 0
        }
        callbacks.onConversationMemberEvent(accountId, conversationId, memberUri, eventInt)
    }

    override fun onComposingStatusChanged(accountId: String, conversationId: String, from: String, isComposing: Boolean) {
        callbacks.onComposingStatusChanged(accountId, conversationId, from, if (isComposing) 1 else 0)
    }

    override fun onConversationProfileUpdated(accountId: String, conversationId: String, profile: Map<Any?, *>) {
        @Suppress("UNCHECKED_CAST")
        val profileMap = (profile as? Map<String, String>) ?: emptyMap()
        callbacks.onConversationProfileUpdated(accountId, conversationId, profileMap)
    }

    override fun onReactionAdded(accountId: String, conversationId: String, messageId: String, reaction: Map<Any?, *>) {
        @Suppress("UNCHECKED_CAST")
        val reactionMap = (reaction as? Map<String, String>) ?: emptyMap()
        callbacks.onReactionAdded(accountId, conversationId, messageId, reactionMap)
    }

    override fun onReactionRemoved(accountId: String, conversationId: String, messageId: String, reactionId: String) {
        callbacks.onReactionRemoved(accountId, conversationId, messageId, reactionId)
    }

    override fun onContactAdded(accountId: String, uri: String, confirmed: Boolean) {
        callbacks.onContactAdded(accountId, uri, confirmed)
    }

    override fun onContactRemoved(accountId: String, uri: String, banned: Boolean) {
        callbacks.onContactRemoved(accountId, uri, banned)
    }

    override fun onIncomingTrustRequest(accountId: String, conversationId: String, from: String, payload: NSData, received: Long) {
        val length = payload.length.toInt()
        val bytes = if (length > 0) {
            ByteArray(length).also { arr ->
                arr.usePinned { pinned ->
                    platform.posix.memcpy(pinned.addressOf(0), payload.bytes, length.toULong())
                }
            }
        } else {
            ByteArray(0)
        }
        callbacks.onIncomingTrustRequest(accountId, conversationId, from, bytes, received)
    }

    override fun onPresenceChanged(accountId: String, uri: String, isOnline: Boolean) {}
}

private fun JBSwarmMessage.toKotlinSwarmMessage(): SwarmMessage {
    // Convert body NSDictionary to Map<String, String>
    @Suppress("UNCHECKED_CAST")
    val bodyMap = mutableMapOf<String, String>()
    val nsBody = this.body as? Map<*, *>
    nsBody?.forEach { (key, value) ->
        val keyStr = key as? String ?: return@forEach
        val valueStr = value as? String ?: return@forEach
        bodyMap[keyStr] = valueStr
    }

    // Convert status map
    val statusMap = mutableMapOf<String, Int>()
    @Suppress("UNCHECKED_CAST")
    val nsStatus = this.status as? Map<*, *>
    nsStatus?.forEach { (key, value) ->
        val keyStr = key as? String ?: return@forEach
        if (value is Number) {
            statusMap[keyStr] = value.toInt()
        }
    }

    return SwarmMessage(
        id = this.messageId ?: "",
        type = this.type ?: "",
        linearizedParent = this.replyTo ?: "",
        body = bodyMap,
        reactions = emptyMap(),
        editions = emptyList(),
        status = statusMap
    )
}
