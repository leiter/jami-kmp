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
 * iOS implementation of DaemonBridge using JamiBridge Objective-C++ wrapper via cinterop.
 */
actual class DaemonBridge() : DaemonBridgeApi {
    private var isInitialized = false
    private var callbacks: DaemonCallbacks? = null
    private val bridge: JamiBridgeWrapper = JamiBridgeWrapper.shared()
    private var delegateImpl: JamiBridgeDelegateImpl? = null

    companion object {
        private const val TAG = "DaemonBridge.iOS"
    }

    // ==================== Lifecycle ====================

    override fun init(callbacks: DaemonCallbacks): Boolean {
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

    override fun start(): Boolean {
        if (!isInitialized) return false
        bridge.startDaemon()
        Log.i(TAG, "Daemon started")
        return true
    }

    override fun stop() {
        bridge.stopDaemon()
        isInitialized = false
        Log.i(TAG, "Daemon stopped")
    }

    override fun isRunning(): Boolean = bridge.isDaemonRunning()

    // ==================== Account Operations ====================

    override fun addAccount(details: Map<String, String>): String {
        val displayName = details["Account.displayName"] ?: ""
        val password = details["Account.archivePassword"] ?: ""
        return bridge.createAccountWithDisplayName(displayName, password = password) ?: ""
    }

    override fun removeAccount(accountId: String) {
        bridge.deleteAccount(accountId)
    }

    override fun getAccountDetails(accountId: String): Map<String, String> {
        return bridge.getAccountDetails(accountId)?.toKotlinMap() ?: emptyMap()
    }

    override fun setAccountDetails(accountId: String, details: Map<String, String>) {
        bridge.setAccountDetails(accountId, details = details.toNSDictionary())
    }

    override fun getAccountList(): List<String> {
        return bridge.getAccountIds()?.toKotlinList() ?: emptyList()
    }

    override fun setAccountActive(accountId: String, active: Boolean) {
        bridge.setAccountActive(accountId, active = active)
    }

    override fun getAccountTemplate(accountType: String): Map<String, String> =
        bridge.getAccountTemplate(accountType)?.toKotlinMap() ?: emptyMap()

    override fun getVolatileAccountDetails(accountId: String): Map<String, String> {
        return bridge.getVolatileAccountDetails(accountId)?.toKotlinMap() ?: emptyMap()
    }

    override fun sendRegister(accountId: String, enable: Boolean) {
        bridge.setAccountActive(accountId, active = enable)
    }

    override fun setAccountsOrder(order: String) {
        bridge.setAccountsOrder(order)
    }

    override fun changeAccountPassword(accountId: String, oldPassword: String, newPassword: String): Boolean =
        bridge.changeAccountPassword(accountId, oldPassword = oldPassword, newPassword = newPassword)

    override fun exportToFile(accountId: String, path: String, scheme: String, password: String): Boolean {
        return bridge.exportAccount(accountId, toDestinationPath = path, withPassword = password)
    }

    // ==================== Credentials ====================

    override fun getCredentials(accountId: String): List<Map<String, String>> =
        bridge.getCredentials(accountId)?.toKotlinListOfMaps() ?: emptyList()

    override fun setCredentials(accountId: String, credentials: List<Map<String, String>>) {
        bridge.setCredentials(accountId, credentials = credentials.toNSArrayOfDictionaries())
    }

    // ==================== Device Management ====================

    override fun getKnownRingDevices(accountId: String): Map<String, String> =
        bridge.getKnownRingDevices(accountId)?.toKotlinMap() ?: emptyMap()

    override fun revokeDevice(accountId: String, deviceId: String, scheme: String, password: String) {
        bridge.revokeDevice(accountId, deviceId = deviceId, scheme = scheme, password = password)
    }

    override fun addDevice(accountId: String, uri: String): Long =
        bridge.addDevice(accountId, uri = uri).toLong()

    override fun confirmAddDevice(accountId: String, opId: Long): Boolean =
        bridge.confirmAddDevice(accountId, opId = opId.toUInt())

    override fun cancelAddDevice(accountId: String, opId: Long): Boolean =
        bridge.cancelAddDevice(accountId, opId = opId.toUInt())

    override fun provideAccountAuthentication(accountId: String, password: String, scheme: String): Boolean =
        bridge.provideAccountAuthentication(accountId, password = password, scheme = scheme)

    override fun setDeviceName(accountId: String, deviceName: String) {
        // setDeviceName is not a direct libjami API; it's updated via setAccountDetails
        val details = bridge.getAccountDetails(accountId)?.toKotlinMap()?.toMutableMap() ?: return
        details["Account.deviceName"] = deviceName
        bridge.setAccountDetails(accountId, details = details.toNSDictionary())
    }

    // ==================== Profile ====================

    override fun updateProfile(accountId: String, displayName: String, avatar: String, fileType: String, flag: Int) {
        bridge.updateProfile(accountId, displayName = displayName, avatarPath = avatar.takeIf { it.isNotEmpty() })
    }

    // ==================== Call Operations ====================

    override fun placeCall(accountId: String, uri: String, mediaList: List<MediaAttribute>): String {
        val hasVideo = mediaList.any { it.mediaType.name == "VIDEO" && it.enabled }
        return bridge.placeCall(accountId, uri = uri, withVideo = hasVideo) ?: ""
    }

    override fun accept(accountId: String, callId: String, mediaList: List<MediaAttribute>) {
        val hasVideo = mediaList.any { it.mediaType.name == "VIDEO" && it.enabled }
        bridge.acceptCall(accountId, callId = callId, withVideo = hasVideo)
    }

    override fun refuse(accountId: String, callId: String) {
        bridge.refuse(accountId, callId = callId)
    }

    override fun hangUp(accountId: String, callId: String) {
        bridge.hangUp(accountId, callId = callId)
    }

    override fun hold(accountId: String, callId: String) {
        bridge.holdCall(accountId, callId = callId)
    }

    override fun unhold(accountId: String, callId: String) {
        bridge.unholdCall(accountId, callId = callId)
    }

    override fun resume(accountId: String, callId: String): Boolean {
        bridge.unholdCall(accountId, callId = callId)
        return true
    }

    override fun muteLocalMedia(accountId: String, callId: String, mediaType: String, mute: Boolean) {
        if (mediaType == "MEDIA_TYPE_AUDIO") {
            bridge.muteAudio(accountId, callId = callId, muted = mute)
        } else if (mediaType == "MEDIA_TYPE_VIDEO") {
            bridge.muteVideo(accountId, callId = callId, muted = mute)
        }
    }

    override fun playDtmf(key: String) { bridge.playDtmf(key) }
    override fun muteRingtone(mute: Boolean) { bridge.muteRingtone(mute) }
    override fun muteCapture(mute: Boolean) { bridge.muteCapture(mute) }
    override fun isCaptureMuted(): Boolean = bridge.isCaptureMuted()
    override fun restartAudioLayer() { Log.d(TAG, "restartAudioLayer") }
    override fun setNoiseSuppression(enabled: Boolean) {}
    override fun setEchoCancellation(enabled: Boolean) {}
    override fun transfer(accountId: String, callId: String, to: String): Boolean =
        bridge.transfer(accountId, callId = callId, to = to)
    override fun attendedTransfer(accountId: String, transferId: String, targetId: String): Boolean =
        bridge.attendedTransfer(accountId, callId = transferId, targetId = targetId)
    override fun getCallDetails(accountId: String, callId: String): Map<String, String> =
        bridge.getCallDetails(accountId, callId = callId)?.toKotlinMap() ?: emptyMap()

    // ==================== Conference Operations ====================
    override fun holdConference(accountId: String, confId: String): Boolean {
        return bridge.holdConference(accountId, conferenceId = confId)
    }

    override fun unholdConference(accountId: String, confId: String): Boolean {
        return bridge.unholdConference(accountId, conferenceId = confId)
    }

    override fun resumeConference(accountId: String, confId: String): Boolean {
        return bridge.resumeConference(accountId, conferenceId = confId)
    }

    override fun setActiveParticipant(accountId: String, confId: String, callId: String) {
        bridge.setActiveParticipant(accountId, conferenceId = confId, callId = callId)
    }

    override fun setConferenceLayout(accountId: String, confId: String, layout: Int) {
        val jbLayout = when (layout) {
            1    -> JBConferenceLayout.JBConferenceLayoutOneBig
            2    -> JBConferenceLayout.JBConferenceLayoutOneBigSmall
            else -> JBConferenceLayout.JBConferenceLayoutGrid
        }
        bridge.setConferenceLayout(accountId, conferenceId = confId, layout = jbLayout)
    }

    override fun hangUpConference(accountId: String, confId: String): Boolean {
        bridge.hangUpConference(accountId, conferenceId = confId)
        return true
    }
    override fun joinParticipant(accountId: String, selCallId: String, account2Id: String, dragCallId: String): Boolean {
        bridge.joinParticipant(accountId, callId = selCallId, accountId2 = account2Id, callId2 = dragCallId)
        return true
    }
    override fun addParticipant(accountId: String, callId: String, account2Id: String, confId: String): Boolean {
        bridge.addParticipantToConference(accountId, callId = callId, conferenceAccountId = account2Id, conferenceId = confId)
        return true
    }
    override fun addMainParticipant(accountId: String, confId: String): Boolean =
        bridge.addMainParticipant(accountId, conferenceId = confId)
    override fun detachParticipant(accountId: String, callId: String): Boolean =
        bridge.detachParticipant(accountId, callId = callId)
    override fun getParticipantList(accountId: String, confId: String): List<String> =
        bridge.getConferenceParticipants(accountId, conferenceId = confId)?.toKotlinList() ?: emptyList()
    override fun getConferenceDetails(accountId: String, confId: String): Map<String, String> =
        bridge.getConferenceDetails(accountId, conferenceId = confId)?.toKotlinMap() ?: emptyMap()

    override fun muteParticipantAudio(accountId: String, confId: String, participantId: String) {
        bridge.muteConferenceParticipant(accountId, conferenceId = confId, participantUri = participantId, muted = true)
    }

    override fun unmuteParticipantAudio(accountId: String, confId: String, participantId: String) {
        bridge.muteConferenceParticipant(accountId, conferenceId = confId, participantUri = participantId, muted = false)
    }

    override fun muteAllParticipants(accountId: String, confId: String) {
        bridge.setConferenceLayout(accountId, conferenceId = confId, layout = JBConferenceLayout.JBConferenceLayoutGrid)
    }

    override fun setConferenceLocked(accountId: String, confId: String, locked: Boolean) {
        bridge.setConferenceLayout(accountId, conferenceId = confId, layout = if (locked) JBConferenceLayout.JBConferenceLayoutOneBigSmall else JBConferenceLayout.JBConferenceLayoutGrid)
    }

    // ==================== Conversation Operations ====================

    override fun getConversations(accountId: String): List<String> {
        return bridge.getConversations(accountId)?.toKotlinList() ?: emptyList()
    }

    override fun startConversation(accountId: String): String {
        return bridge.startConversation(accountId) ?: ""
    }

    override fun sendMessage(
        accountId: String,
        conversationId: String,
        message: String,
        replyTo: String,
        flag: Int
    ) {
        bridge.sendMessage(accountId, conversationId = conversationId, message = message, replyTo = replyTo.takeIf { it.isNotEmpty() })
    }

    override fun loadConversation(
        accountId: String,
        conversationId: String,
        fromMessage: String,
        size: Int
    ) {
        bridge.loadConversationMessages(accountId, conversationId = conversationId, fromMessage = fromMessage, count = size)
    }

    override fun getConversationMembers(accountId: String, conversationId: String): List<Map<String, String>> {
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

    override fun getConversationInfo(accountId: String, conversationId: String): Map<String, String> {
        return bridge.getConversationInfo(accountId, conversationId = conversationId)?.toKotlinMap() ?: emptyMap()
    }

    override fun removeConversation(accountId: String, conversationId: String) {
        bridge.removeConversation(accountId, conversationId = conversationId)
    }

    override fun addConversationMember(accountId: String, conversationId: String, uri: String) {
        bridge.addConversationMember(accountId, conversationId = conversationId, contactUri = uri)
    }

    override fun removeConversationMember(accountId: String, conversationId: String, uri: String) {
        bridge.removeConversationMember(accountId, conversationId = conversationId, contactUri = uri)
    }

    override fun updateConversationInfo(accountId: String, conversationId: String, info: Map<String, String>) {
        bridge.updateConversationInfo(accountId, conversationId = conversationId, info = info.toNSDictionary())
    }

    override fun getConversationPreferences(accountId: String, conversationId: String): Map<String, String> =
        bridge.getConversationPreferences(accountId, conversationId = conversationId)?.toKotlinMap() ?: emptyMap()

    override fun setConversationPreferences(accountId: String, conversationId: String, prefs: Map<String, String>) {
        bridge.setConversationPreferences(accountId, conversationId = conversationId, prefs = prefs.toNSDictionary())
    }

    override fun setMessageDisplayed(accountId: String, conversationUri: String, messageId: String, status: Int) {
        bridge.setMessageDisplayed(accountId, conversationId = conversationUri, messageId = messageId)
    }

    override fun getActiveCalls(accountId: String, conversationId: String): List<Map<String, String>> {
        // JamiBridge getActiveCalls takes only accountId
        return emptyList()
    }

    // ==================== Conversation Requests ====================

    override fun getConversationRequests(accountId: String): List<Map<String, String>> {
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

    override fun acceptConversationRequest(accountId: String, conversationId: String) {
        bridge.acceptConversationRequest(accountId, conversationId = conversationId)
    }

    override fun declineConversationRequest(accountId: String, conversationId: String) {
        bridge.declineConversationRequest(accountId, conversationId = conversationId)
    }

    // ==================== Trust Requests ====================

    override fun getTrustRequests(accountId: String): List<Map<String, String>> {
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

    override fun acceptTrustRequest(accountId: String, uri: String) {
        bridge.acceptTrustRequest(accountId, uri = uri)
    }

    override fun discardTrustRequest(accountId: String, uri: String) {
        bridge.discardTrustRequest(accountId, uri = uri)
    }

    override fun sendTrustRequest(accountId: String, uri: String, payload: ByteArray) {
        bridge.addContact(accountId, uri = uri)
    }

    // ==================== Contact Operations ====================

    override fun addContact(accountId: String, uri: String) {
        bridge.addContact(accountId, uri = uri)
    }

    override fun removeContact(accountId: String, uri: String, ban: Boolean) {
        bridge.removeContact(accountId, uri = uri, ban = ban)
    }

    override fun getContacts(accountId: String): List<Map<String, String>> {
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

    override fun getContactDetails(accountId: String, uri: String): Map<String, String> {
        return bridge.getContactDetails(accountId, uri = uri)?.toKotlinMap() ?: emptyMap()
    }

    override fun subscribeBuddy(accountId: String, uri: String, subscribe: Boolean) {
        bridge.subscribeBuddy(accountId, uri = uri, flag = subscribe)
    }

    // ==================== Name Lookup ====================

    override fun lookupName(accountId: String, nameServiceUrl: String, name: String): Boolean {
        val result = bridge.lookupName(accountId, name = name)
        return result != null && result.state == JBLookupState.JBLookupStateSuccess
    }

    override fun lookupAddress(accountId: String, nameServiceUrl: String, address: String): Boolean {
        val result = bridge.lookupAddress(accountId, address = address)
        return result != null && result.state == JBLookupState.JBLookupStateSuccess
    }

    override fun registerName(accountId: String, name: String, scheme: String, password: String): Boolean {
        return bridge.registerName(accountId, name = name, password = password)
    }

    override fun searchUser(accountId: String, query: String): Boolean =
        bridge.searchUser(accountId, query = query)

    // ==================== Messaging ====================

    override fun sendTextMessage(accountId: String, callIdOrUri: String, message: String) {
        bridge.sendTextMessage(accountId, callId = callIdOrUri,
            messages = mapOf("text/plain" to message).toNSDictionary(),
            from = "", isMixed = false)
    }

    override fun setIsComposing(accountId: String, uri: String, isComposing: Boolean) {
        bridge.setIsComposing(accountId, conversationId = uri, isComposing = isComposing)
    }

    override fun cancelMessage(accountId: String, messageId: Long): Boolean =
        bridge.cancelMessage(accountId, messageId = messageId.toULong())

    override fun sendAccountTextMessage(accountId: String, conversationId: String, messages: Map<String, String>, flag: Int) {
        bridge.sendAccountTextMessage(accountId, conversationId = conversationId, messages = messages.toNSDictionary(), flag = flag)
    }

    // ==================== File Transfer ====================

    override fun sendFile(
        accountId: String,
        conversationId: String,
        filePath: String,
        displayName: String,
        parent: String
    ) {
        bridge.sendFile(accountId, conversationId = conversationId, filePath = filePath, displayName = displayName)
    }

    override fun downloadFile(
        accountId: String,
        conversationId: String,
        interactionId: String,
        fileId: String,
        path: String
    ) {
        bridge.acceptFileTransfer(accountId, conversationId = conversationId, interactionId = interactionId, fileId = fileId, destinationPath = path)
    }

    override fun cancelDataTransfer(accountId: String, conversationId: String, fileId: String) {
        bridge.cancelFileTransfer(accountId, conversationId = conversationId, fileId = fileId)
    }

    override fun fileTransferInfo(accountId: String, conversationId: String, fileId: String): FileTransferInfo? {
        val info = bridge.getFileTransferInfo(accountId, conversationId = conversationId, fileId = fileId) ?: return null
        return FileTransferInfo(
            path = info.path ?: "",
            totalSize = info.totalSize,
            bytesProgress = info.progress
        )
    }

    // ==================== Search & History ====================

    override fun searchConversation(accountId: String, conversationId: String, author: String, lastId: String, query: String, type: String, after: Long, before: Long, maxResult: Long, flag: Int): Long =
        bridge.searchConversation(accountId, conversationId = conversationId, author = author,
            lastId = lastId, regexSearch = query, type = type, after = after, before = before,
            maxResult = maxResult.toUInt(), flag = flag).toLong()

    override fun loadSwarmUntil(accountId: String, conversationId: String, fromMessage: String, toMessage: String): Long =
        bridge.loadSwarmUntil(accountId, conversationId = conversationId,
            fromMessage = fromMessage, toMessage = toMessage).toLong()

    // ==================== Codec Operations ====================

    override fun getCodecList(): List<Long> =
        bridge.getCodecList()?.toKotlinLongList() ?: emptyList()

    override fun getActiveCodecList(accountId: String): List<Long> =
        bridge.getActiveCodecList(accountId)?.toKotlinLongList() ?: emptyList()

    override fun setActiveCodecList(accountId: String, codecList: List<Long>) {
        bridge.setActiveCodecList(accountId, codecList = codecList.toNSNumberList())
    }

    override fun getCodecDetails(accountId: String, codecId: Long): Map<String, String> =
        bridge.getCodecDetails(accountId, codecId = codecId.toUInt())?.toKotlinMap() ?: emptyMap()

    // ==================== Push Notifications ====================

    override fun setPushNotificationToken(token: String) {
        // Not exposed in current JamiBridge header
    }

    override fun setPushNotificationConfig(config: Map<String, String>) {
        // Not exposed
    }

    override fun pushNotificationReceived(from: String, data: Map<String, String>) {
        // Not exposed
    }

    // ==================== Video Device Management ====================

    override fun addVideoDevice(deviceId: String) {
        bridge.addVideoDevice(deviceId)
    }

    override fun removeVideoDevice(deviceId: String) {
        bridge.removeVideoDevice(deviceId)
    }

    override fun setDefaultDevice(deviceId: String) {
        bridge.setDefaultVideoDevice(deviceId)
    }

    override fun setDeviceOrientation(deviceId: String, rotation: Int) {
        bridge.setDeviceOrientation(deviceId, angle = rotation)
    }

    override fun applySettings(deviceId: String, settings: Map<String, String>) {
        bridge.applyVideoSettings(deviceId, settings = settings.toNSDictionary())
    }

    // ==================== Video Frame Capture ====================

    override fun captureVideoFrame(uri: String, data: ByteArray, rotation: Int) {
        // TODO: Implement via JamiBridge cinterop
    }

    override fun captureVideoPacket(
        uri: String,
        data: Any,
        size: Int,
        offset: Int,
        isKeyFrame: Boolean,
        timestamp: Long,
        rotation: Int
    ) {
        // TODO: Implement via JamiBridge cinterop
    }

    // ==================== Native Window Management ====================

    override fun acquireNativeWindow(surface: Any): Long {
        // TODO: Implement via JamiBridge cinterop
        return 0L
    }

    override fun releaseNativeWindow(windowId: Long) {
        // TODO: Implement via JamiBridge cinterop
    }

    override fun setNativeWindowGeometry(windowId: Long, width: Int, height: Int) {
        // TODO: Implement via JamiBridge cinterop
    }

    // ==================== Video Callback Registration ====================

    override fun registerVideoCallback(id: String, windowId: Long): Boolean {
        // TODO: Implement via JamiBridge cinterop
        return false
    }

    override fun unregisterVideoCallback(id: String, windowId: Long) {
        // TODO: Implement via JamiBridge cinterop
    }

    // ==================== Video Input Switching ====================

    override fun switchVideoInput(accountId: String, callId: String, uri: String) {
        bridge.switchVideoInput(accountId, callId = callId, uri = uri)
    }

    override fun requestMediaChange(accountId: String, callId: String, mediaList: List<Map<String, String>>) {
        bridge.requestMediaChange(accountId, callId = callId, mediaList = mediaList.toNSArrayOfDictionaries())
    }

    override fun answerMediaChangeRequest(accountId: String, callId: String, mediaList: List<Map<String, String>>) {
        bridge.answerMediaChangeRequest(accountId, callId = callId, mediaList = mediaList.toNSArrayOfDictionaries())
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

@Suppress("UNCHECKED_CAST")
private fun Any?.toKotlinListOfMaps(): List<Map<String, String>> {
    val array = this as? List<*> ?: return emptyList()
    return array.mapNotNull { item ->
        val map = item as? Map<*, *> ?: return@mapNotNull null
        map.entries.mapNotNull { (k, v) ->
            val key = k as? String ?: return@mapNotNull null
            val value = v as? String ?: return@mapNotNull null
            key to value
        }.toMap()
    }
}

@Suppress("UNCHECKED_CAST")
private fun List<Map<String, String>>.toNSArrayOfDictionaries(): List<Map<Any?, *>> {
    return this.map { it as Map<Any?, *> }
}

@Suppress("UNCHECKED_CAST")
private fun Any?.toKotlinLongList(): List<Long> {
    val array = this as? List<*> ?: return emptyList()
    return array.mapNotNull { (it as? Number)?.toLong() }
}

private fun List<Long>.toNSNumberList(): List<*> = this

// ==================== Delegate Implementation ====================

/**
 * Implementation of JamiBridgeDelegate using NSObject.
 */
private class JamiBridgeDelegateImpl(
    private val callbacks: DaemonCallbacks
) : NSObject(), JamiBridgeDelegateProtocol {

    // Account Events
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
        // DaemonCallbacks expects: onProfileReceived(accountId, peerId, vcardPath)
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

    // addDevice events not yet exposed in JamiBridgeProtocol — no-op stub
    // When the C bridge exposes onAddDeviceStateChanged, add the override here.

    // Call Events
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

    override fun onMediaChangeRequested(
        accountId: String,
        callId: String,
        mediaList: List<*>
    ) {
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

    override fun onConferenceCreated(
        accountId: String,
        conversationId: String,
        conferenceId: String
    ) {
        callbacks.onConferenceCreated(accountId, conversationId, conferenceId)
    }

    override fun onConferenceChanged(accountId: String, conferenceId: String, state: String) {
        callbacks.onConferenceChanged(accountId, conferenceId, state)
    }

    override fun onConferenceRemoved(accountId: String, conferenceId: String) {
        callbacks.onConferenceRemoved(accountId, conferenceId)
    }

    override fun onConferenceInfoUpdated(conferenceId: String, participantInfos: List<*>) {
        // Not directly mapped to DaemonCallbacks
    }

    // Conversation Events
    override fun onConversationReady(accountId: String, conversationId: String) {
        callbacks.onConversationReady(accountId, conversationId)
    }

    override fun onConversationRemoved(accountId: String, conversationId: String) {
        callbacks.onConversationRemoved(accountId, conversationId)
    }

    override fun onConversationRequestReceived(
        accountId: String,
        conversationId: String,
        metadata: Map<Any?, *>
    ) {
        @Suppress("UNCHECKED_CAST")
        val metadataMap = (metadata as? Map<String, String>) ?: emptyMap()
        callbacks.onConversationRequestReceived(accountId, conversationId, metadataMap)
    }

    override fun onMessageReceived(accountId: String, conversationId: String, message: JBSwarmMessage) {
        val swarmMessage = message.toKotlinSwarmMessage()
        callbacks.onMessageReceived(accountId, conversationId, swarmMessage)
    }

    override fun onMessageUpdated(accountId: String, conversationId: String, message: JBSwarmMessage) {
        val swarmMessage = message.toKotlinSwarmMessage()
        callbacks.onMessageUpdated(accountId, conversationId, swarmMessage)
    }

    override fun onMessagesLoaded(
        requestId: Int,
        accountId: String,
        conversationId: String,
        messages: List<*>
    ) {
        val swarmMessages = messages.mapNotNull {
            (it as? JBSwarmMessage)?.toKotlinSwarmMessage()
        }
        callbacks.onSwarmLoaded(requestId.toLong(), accountId, conversationId, swarmMessages)
    }

    override fun onConversationMemberEvent(
        accountId: String,
        conversationId: String,
        memberUri: String,
        event: JBMemberEventType
    ) {
        val eventInt = when (event) {
            JBMemberEventType.JBMemberEventTypeJoin -> 0
            JBMemberEventType.JBMemberEventTypeLeave -> 1
            JBMemberEventType.JBMemberEventTypeBan -> 2
            JBMemberEventType.JBMemberEventTypeUnban -> 3
            else -> 0
        }
        callbacks.onConversationMemberEvent(accountId, conversationId, memberUri, eventInt)
    }

    override fun onComposingStatusChanged(
        accountId: String,
        conversationId: String,
        from: String,
        isComposing: Boolean
    ) {
        callbacks.onComposingStatusChanged(accountId, conversationId, from, if (isComposing) 1 else 0)
    }

    override fun onConversationProfileUpdated(
        accountId: String,
        conversationId: String,
        profile: Map<Any?, *>
    ) {
        @Suppress("UNCHECKED_CAST")
        val profileMap = (profile as? Map<String, String>) ?: emptyMap()
        callbacks.onConversationProfileUpdated(accountId, conversationId, profileMap)
    }

    override fun onReactionAdded(
        accountId: String,
        conversationId: String,
        messageId: String,
        reaction: Map<Any?, *>
    ) {
        @Suppress("UNCHECKED_CAST")
        val reactionMap = (reaction as? Map<String, String>) ?: emptyMap()
        callbacks.onReactionAdded(accountId, conversationId, messageId, reactionMap)
    }

    override fun onReactionRemoved(
        accountId: String,
        conversationId: String,
        messageId: String,
        reactionId: String
    ) {
        callbacks.onReactionRemoved(accountId, conversationId, messageId, reactionId)
    }

    // Contact Events
    override fun onContactAdded(accountId: String, uri: String, confirmed: Boolean) {
        callbacks.onContactAdded(accountId, uri, confirmed)
    }

    override fun onContactRemoved(accountId: String, uri: String, banned: Boolean) {
        callbacks.onContactRemoved(accountId, uri, banned)
    }

    override fun onIncomingTrustRequest(
        accountId: String,
        conversationId: String,
        from: String,
        payload: NSData,
        received: Long
    ) {
        // Convert NSData to ByteArray
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

    override fun onPresenceChanged(accountId: String, uri: String, isOnline: Boolean) {
        callbacks.onNewBuddyNotification(accountId, uri, if (isOnline) 1 else 0, "")
    }
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
