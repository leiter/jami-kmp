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
package net.jami.services

import net.jami.model.MediaAttribute

/**
 * iOS implementation of DaemonBridge using Kotlin/Native Objective-C interop.
 *
 * ## Architecture
 *
 * This implementation uses the JamiBridgeWrapper Objective-C++ class from the
 * JamiBridge library. JamiBridgeWrapper provides a unified interface to libjami
 * with clean Objective-C types that cinterop can parse.
 *
 * ## Integration Steps
 *
 * 1. Build libjami for iOS (produces libjami.a)
 *    - Copy from gettogether: iosApp/Frameworks/libjami-arm64/libjami.a
 *    - Or build from jami-daemon source
 *
 * 2. Build the JamiBridge static library:
 *    ```bash
 *    cd shared/src/nativeInterop/cinterop/JamiBridge
 *    ./build-jamibridge.sh
 *    ```
 *
 * 3. Enable cinterop in build.gradle.kts:
 *    Set `enableJamiBridgeCinterop = true`
 *
 * ## Callback Pattern
 *
 * JamiBridgeWrapper uses the JamiBridgeDelegate protocol. This class implements
 * that protocol and forwards events to the DaemonCallbacks interface:
 *
 * ```kotlin
 * // When cinterop is enabled:
 * import net.jami.bridge.*
 *
 * class DaemonBridgeImpl : NSObject(), JamiBridgeDelegateProtocol {
 *     private val bridge = JamiBridgeWrapper.shared()
 *
 *     init {
 *         bridge.delegate = this
 *     }
 *
 *     override fun onRegistrationStateChanged(accountId: String, state: JBRegistrationState, ...) {
 *         callbacks?.onRegistrationStateChanged(accountId, state.toKotlin(), ...)
 *     }
 *
 *     override fun onIncomingCall(accountId: String, callId: String, ...) {
 *         callbacks?.onIncomingCall(accountId, callId, ...)
 *     }
 * }
 * ```
 *
 * ## Current Status: Stub Implementation
 *
 * This is currently a stub that returns empty/default values. Once the JamiBridge
 * library is built and cinterop is configured, the real implementation will call
 * JamiBridgeWrapper methods.
 *
 * ## JamiBridge API Coverage (71 methods)
 *
 * - Daemon Lifecycle: 4 methods (init, start, stop, isRunning)
 * - Account Management: 14 methods
 * - Contact Management: 8 methods
 * - Conversation Management: 11 methods
 * - Messaging: 4 methods
 * - Calls: 12 methods
 * - Conference Calls: 10 methods
 * - File Transfer: 4 methods
 * - Video: 5 methods
 * - Audio Settings: 4 methods
 */
actual class DaemonBridge actual constructor() {
    private var isInitialized = false
    private var callbacks: DaemonCallbacks? = null

    // When JamiBridge cinterop is enabled, use:
    // import net.jami.bridge.*
    // private val bridge: JamiBridgeWrapper by lazy { JamiBridgeWrapper.shared() }

    // ==================== Lifecycle ====================

    actual fun init(callbacks: DaemonCallbacks): Boolean {
        this.callbacks = callbacks
        // TODO: When JamiBridge cinterop is enabled:
        // bridge.delegate = createDelegate(callbacks)
        // bridge.initDaemonWithDataPath(getDataPath())
        isInitialized = true
        return true
    }

    actual fun start(): Boolean {
        // TODO: bridge.startDaemon()
        return isInitialized
    }

    actual fun stop() {
        // TODO: bridge.stopDaemon()
        isInitialized = false
    }

    actual fun isRunning(): Boolean {
        // TODO: return bridge.isDaemonRunning()
        return isInitialized
    }

    // ==================== Account Operations ====================

    actual fun addAccount(details: Map<String, String>): String {
        // TODO: return accountAdapter?.addAccount(details.toNSDictionary()) ?: ""
        return ""
    }

    actual fun removeAccount(accountId: String) {
        // TODO: accountAdapter?.removeAccount(accountId)
    }

    actual fun getAccountDetails(accountId: String): Map<String, String> {
        // TODO: return accountAdapter?.getAccountDetails(accountId)?.toKotlinMap() ?: emptyMap()
        return emptyMap()
    }

    actual fun setAccountDetails(accountId: String, details: Map<String, String>) {
        // TODO: accountAdapter?.setAccountDetails(details.toNSDictionary(), forAccountId: accountId)
    }

    actual fun getAccountList(): List<String> {
        // TODO: return accountAdapter?.getAccountList()?.toKotlinList() ?: emptyList()
        return emptyList()
    }

    actual fun setAccountActive(accountId: String, active: Boolean) {
        // TODO: accountAdapter?.setAccountActive(accountId, active: active)
    }

    actual fun getAccountTemplate(accountType: String): Map<String, String> {
        // TODO: return accountAdapter?.getAccountTemplate(accountType)?.toKotlinMap() ?: emptyMap()
        return emptyMap()
    }

    actual fun getVolatileAccountDetails(accountId: String): Map<String, String> {
        // TODO: return accountAdapter?.getVolatileAccountDetails(accountId)?.toKotlinMap() ?: emptyMap()
        return emptyMap()
    }

    actual fun sendRegister(accountId: String, enable: Boolean) {
        // TODO: accountAdapter?.sendRegister(accountId, enable: enable)
    }

    actual fun setAccountsOrder(order: String) {
        // TODO: accountAdapter?.setAccountsOrder(order)
    }

    actual fun changeAccountPassword(accountId: String, oldPassword: String, newPassword: String): Boolean {
        // TODO: return accountAdapter?.changeAccountPassword(accountId, oldPassword: oldPassword, newPassword: newPassword) ?: false
        return false
    }

    actual fun exportToFile(accountId: String, path: String, scheme: String, password: String): Boolean {
        // TODO: return accountAdapter?.exportToFile(accountId, toPath: path, withScheme: scheme, password: password) ?: false
        return false
    }

    // ==================== Credentials ====================

    actual fun getCredentials(accountId: String): List<Map<String, String>> {
        // TODO: return accountAdapter?.getCredentials(accountId)?.toKotlinListOfMaps() ?: emptyList()
        return emptyList()
    }

    actual fun setCredentials(accountId: String, credentials: List<Map<String, String>>) {
        // TODO: accountAdapter?.setCredentials(credentials.toNSArray(), forAccountId: accountId)
    }

    // ==================== Device Management ====================

    actual fun getKnownRingDevices(accountId: String): Map<String, String> {
        // TODO: return accountAdapter?.getKnownRingDevices(accountId)?.toKotlinMap() ?: emptyMap()
        return emptyMap()
    }

    actual fun revokeDevice(accountId: String, deviceId: String, scheme: String, password: String) {
        // TODO: accountAdapter?.revokeDevice(accountId, deviceId: deviceId, scheme: scheme, password: password)
    }

    actual fun setDeviceName(accountId: String, deviceName: String) {
        // TODO: accountAdapter?.setDeviceName(accountId, name: deviceName)
    }

    // ==================== Profile ====================

    actual fun updateProfile(accountId: String, displayName: String, avatar: String, fileType: String, flag: Int) {
        // TODO: profilesAdapter?.updateProfile(accountId, displayName: displayName, avatar: avatar, fileType: fileType, flag: flag)
    }

    // ==================== Call Operations ====================

    actual fun placeCall(accountId: String, uri: String, mediaList: List<MediaAttribute>): String {
        // TODO: Convert mediaList to NSArray of NSDictionary
        // return callsAdapter?.placeCall(accountId, toUri: uri, withMedia: mediaList.toNSArray()) ?: ""
        return ""
    }

    actual fun accept(accountId: String, callId: String, mediaList: List<MediaAttribute>) {
        // TODO: callsAdapter?.accept(accountId, callId: callId, withMedia: mediaList.toNSArray())
    }

    actual fun hangUp(accountId: String, callId: String) {
        // TODO: callsAdapter?.hangUp(accountId, callId: callId)
    }

    actual fun hold(accountId: String, callId: String) {
        // TODO: callsAdapter?.hold(accountId, callId: callId)
    }

    actual fun unhold(accountId: String, callId: String) {
        // TODO: callsAdapter?.unhold(accountId, callId: callId)
    }

    actual fun muteLocalMedia(accountId: String, callId: String, mediaType: String, mute: Boolean) {
        // TODO: callsAdapter?.muteLocalMedia(accountId, callId: callId, mediaType: mediaType, mute: mute)
    }

    // ==================== Conversation Operations ====================

    actual fun getConversations(accountId: String): List<String> {
        // TODO: return conversationsAdapter?.getConversations(accountId)?.toKotlinList() ?: emptyList()
        return emptyList()
    }

    actual fun startConversation(accountId: String): String {
        // TODO: return conversationsAdapter?.startConversation(accountId) ?: ""
        return ""
    }

    actual fun sendMessage(accountId: String, conversationId: String, message: String, replyTo: String, flag: Int) {
        // TODO: conversationsAdapter?.sendMessage(accountId, conversationId: conversationId, message: message, replyTo: replyTo, flag: flag)
    }

    actual fun loadConversation(accountId: String, conversationId: String, fromMessage: String, size: Int) {
        // TODO: conversationsAdapter?.loadConversation(accountId, conversationId: conversationId, fromMessage: fromMessage, size: size)
    }

    actual fun getConversationMembers(accountId: String, conversationId: String): List<Map<String, String>> {
        // TODO: return conversationsAdapter?.getConversationMembers(accountId, conversationId: conversationId)?.toKotlinListOfMaps() ?: emptyList()
        return emptyList()
    }

    actual fun getConversationInfo(accountId: String, conversationId: String): Map<String, String> {
        // TODO: return conversationsAdapter?.getConversationInfo(accountId, conversationId: conversationId)?.toKotlinMap() ?: emptyMap()
        return emptyMap()
    }

    actual fun removeConversation(accountId: String, conversationId: String) {
        // TODO: conversationsAdapter?.removeConversation(accountId, conversationId: conversationId)
    }

    actual fun addConversationMember(accountId: String, conversationId: String, uri: String) {
        // TODO: conversationsAdapter?.addConversationMember(accountId, conversationId: conversationId, uri: uri)
    }

    actual fun removeConversationMember(accountId: String, conversationId: String, uri: String) {
        // TODO: conversationsAdapter?.removeConversationMember(accountId, conversationId: conversationId, uri: uri)
    }

    actual fun updateConversationInfo(accountId: String, conversationId: String, info: Map<String, String>) {
        // TODO: conversationsAdapter?.updateConversationInfo(accountId, conversationId: conversationId, info: info.toNSDictionary())
    }

    actual fun getConversationPreferences(accountId: String, conversationId: String): Map<String, String> {
        // TODO: return conversationsAdapter?.getConversationPreferences(accountId, conversationId: conversationId)?.toKotlinMap() ?: emptyMap()
        return emptyMap()
    }

    actual fun setConversationPreferences(accountId: String, conversationId: String, prefs: Map<String, String>) {
        // TODO: conversationsAdapter?.setConversationPreferences(accountId, conversationId: conversationId, prefs: prefs.toNSDictionary())
    }

    actual fun setMessageDisplayed(accountId: String, conversationUri: String, messageId: String, status: Int) {
        // TODO: conversationsAdapter?.setMessageDisplayed(accountId, conversationUri: conversationUri, messageId: messageId, status: status)
    }

    actual fun getActiveCalls(accountId: String, conversationId: String): List<Map<String, String>> {
        // TODO: return conversationsAdapter?.getActiveCalls(accountId, conversationId: conversationId)?.toKotlinListOfMaps() ?: emptyList()
        return emptyList()
    }

    // ==================== Conversation Requests ====================

    actual fun getConversationRequests(accountId: String): List<Map<String, String>> {
        // TODO: return requestsAdapter?.getConversationRequests(accountId)?.toKotlinListOfMaps() ?: emptyList()
        return emptyList()
    }

    actual fun acceptConversationRequest(accountId: String, conversationId: String) {
        // TODO: requestsAdapter?.acceptConversationRequest(accountId, conversationId: conversationId)
    }

    actual fun declineConversationRequest(accountId: String, conversationId: String) {
        // TODO: requestsAdapter?.declineConversationRequest(accountId, conversationId: conversationId)
    }

    // ==================== Trust Requests ====================

    actual fun getTrustRequests(accountId: String): List<Map<String, String>> {
        // TODO: return contactsAdapter?.getTrustRequests(accountId)?.toKotlinListOfMaps() ?: emptyList()
        return emptyList()
    }

    actual fun acceptTrustRequest(accountId: String, uri: String) {
        // TODO: contactsAdapter?.acceptTrustRequest(accountId, fromUri: uri)
    }

    actual fun discardTrustRequest(accountId: String, uri: String) {
        // TODO: contactsAdapter?.discardTrustRequest(accountId, fromUri: uri)
    }

    actual fun sendTrustRequest(accountId: String, uri: String, payload: ByteArray) {
        // TODO: contactsAdapter?.sendTrustRequest(accountId, toUri: uri, payload: payload.toNSData())
    }

    // ==================== Contact Operations ====================

    actual fun addContact(accountId: String, uri: String) {
        // TODO: contactsAdapter?.addContact(accountId, uri: uri)
    }

    actual fun removeContact(accountId: String, uri: String, ban: Boolean) {
        // TODO: contactsAdapter?.removeContact(accountId, uri: uri, ban: ban)
    }

    actual fun getContacts(accountId: String): List<Map<String, String>> {
        // TODO: return contactsAdapter?.getContacts(accountId)?.toKotlinListOfMaps() ?: emptyList()
        return emptyList()
    }

    actual fun getContactDetails(accountId: String, uri: String): Map<String, String> {
        // TODO: return contactsAdapter?.getContactDetails(accountId, uri: uri)?.toKotlinMap() ?: emptyMap()
        return emptyMap()
    }

    actual fun subscribeBuddy(accountId: String, uri: String, subscribe: Boolean) {
        // TODO: presenceAdapter?.subscribeBuddy(accountId, uri: uri, subscribe: subscribe)
    }

    // ==================== Name Lookup ====================

    actual fun lookupName(accountId: String, nameServiceUrl: String, name: String): Boolean {
        // TODO: return accountAdapter?.lookupName(accountId, nameServiceUrl: nameServiceUrl, name: name) ?: false
        return false
    }

    actual fun lookupAddress(accountId: String, nameServiceUrl: String, address: String): Boolean {
        // TODO: return accountAdapter?.lookupAddress(accountId, nameServiceUrl: nameServiceUrl, address: address) ?: false
        return false
    }

    actual fun registerName(accountId: String, name: String, scheme: String, password: String): Boolean {
        // TODO: return accountAdapter?.registerName(accountId, name: name, scheme: scheme, password: password) ?: false
        return false
    }

    actual fun searchUser(accountId: String, query: String): Boolean {
        // TODO: return accountAdapter?.searchUser(accountId, query: query) ?: false
        return false
    }

    // ==================== Messaging ====================

    actual fun sendTextMessage(accountId: String, callIdOrUri: String, message: String) {
        // TODO: conversationsAdapter?.sendTextMessage(accountId, to: callIdOrUri, message: message)
    }

    actual fun setIsComposing(accountId: String, uri: String, isComposing: Boolean) {
        // TODO: conversationsAdapter?.setIsComposing(accountId, uri: uri, isComposing: isComposing)
    }

    actual fun cancelMessage(accountId: String, messageId: Long): Boolean {
        // TODO: return conversationsAdapter?.cancelMessage(accountId, messageId: messageId) ?: false
        return false
    }

    // ==================== File Transfer ====================

    actual fun sendFile(accountId: String, conversationId: String, filePath: String, displayName: String, parent: String) {
        // TODO: dataTransferAdapter?.sendFile(accountId, conversationId: conversationId, filePath: filePath, displayName: displayName, parent: parent)
    }

    actual fun downloadFile(accountId: String, conversationId: String, interactionId: String, fileId: String, path: String) {
        // TODO: dataTransferAdapter?.downloadFile(accountId, conversationId: conversationId, interactionId: interactionId, fileId: fileId, path: path)
    }

    actual fun cancelDataTransfer(accountId: String, conversationId: String, fileId: String) {
        // TODO: dataTransferAdapter?.cancelDataTransfer(accountId, conversationId: conversationId, fileId: fileId)
    }

    actual fun fileTransferInfo(accountId: String, conversationId: String, fileId: String): FileTransferInfo? {
        // TODO: return dataTransferAdapter?.fileTransferInfo(accountId, conversationId: conversationId, fileId: fileId)?.toKotlin()
        return null
    }

    // ==================== Codec Operations ====================

    actual fun getCodecList(): List<Long> {
        // TODO: return audioAdapter?.getCodecList()?.map { it.longValue } ?: emptyList()
        return emptyList()
    }

    actual fun getActiveCodecList(accountId: String): List<Long> {
        // TODO: return audioAdapter?.getActiveCodecList(accountId)?.map { it.longValue } ?: emptyList()
        return emptyList()
    }

    actual fun setActiveCodecList(accountId: String, codecList: List<Long>) {
        // TODO: audioAdapter?.setActiveCodecList(accountId, codecList: codecList.map { NSNumber(long: it) }.toNSArray())
    }

    actual fun getCodecDetails(accountId: String, codecId: Long): Map<String, String> {
        // TODO: return audioAdapter?.getCodecDetails(accountId, codecId: codecId)?.toKotlinMap() ?: emptyMap()
        return emptyMap()
    }

    // ==================== Push Notifications ====================

    actual fun setPushNotificationToken(token: String) {
        // TODO: accountAdapter?.setPushNotificationToken(token)
    }

    actual fun setPushNotificationConfig(config: Map<String, String>) {
        // TODO: accountAdapter?.setPushNotificationConfig(config.toNSDictionary())
    }

    actual fun pushNotificationReceived(from: String, data: Map<String, String>) {
        // TODO: accountAdapter?.pushNotificationReceived(from, data: data.toNSDictionary())
    }

    // ==================== JamiBridgeDelegate Implementation ====================
    //
    // When JamiBridge cinterop is enabled, create a delegate that implements
    // JamiBridgeDelegateProtocol and forwards events to DaemonCallbacks:
    //
    // private fun createDelegate(callbacks: DaemonCallbacks) = object : NSObject(), JamiBridgeDelegateProtocol {
    //
    //     // Account Events
    //     override fun onRegistrationStateChanged(accountId: String, state: JBRegistrationState, code: Int, detail: String) {
    //         val stateStr = when(state) {
    //             JBRegistrationState.JBRegistrationStateRegistered -> "REGISTERED"
    //             JBRegistrationState.JBRegistrationStateTrying -> "TRYING"
    //             else -> "UNREGISTERED"
    //         }
    //         callbacks.onRegistrationStateChanged(accountId, stateStr, code, detail)
    //     }
    //
    //     override fun onAccountDetailsChanged(accountId: String, details: Map<Any?, *>) {
    //         callbacks.onAccountDetailsChanged(accountId, details.toStringMap())
    //     }
    //
    //     // Call Events
    //     override fun onIncomingCall(accountId: String, callId: String, peerId: String, peerDisplayName: String, hasVideo: Boolean) {
    //         callbacks.onIncomingCall(accountId, callId, peerId)
    //     }
    //
    //     override fun onCallStateChanged(accountId: String, callId: String, state: JBCallState, code: Int) {
    //         val stateStr = when(state) {
    //             JBCallState.JBCallStateCurrent -> "CURRENT"
    //             JBCallState.JBCallStateRinging -> "RINGING"
    //             JBCallState.JBCallStateIncoming -> "INCOMING"
    //             JBCallState.JBCallStateHungup -> "HUNGUP"
    //             JBCallState.JBCallStateOver -> "OVER"
    //             else -> "INACTIVE"
    //         }
    //         callbacks.onCallStateChanged(accountId, callId, stateStr, code)
    //     }
    //
    //     // Conversation Events
    //     override fun onConversationReady(accountId: String, conversationId: String) {
    //         callbacks.onConversationReady(accountId, conversationId)
    //     }
    //
    //     override fun onMessageReceived(accountId: String, conversationId: String, message: JBSwarmMessage) {
    //         callbacks.onMessageReceived(accountId, conversationId, message.toKotlinSwarmMessage())
    //     }
    //
    //     // ... implement remaining delegate methods
    // }
}
