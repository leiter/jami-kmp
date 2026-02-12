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
 * This implementation uses the Objective-C++ adapters from jami-client-ios as the
 * bridging layer between Kotlin/Native and the C++ libjami library. The adapters
 * (AccountAdapter, CallsAdapter, etc.) wrap C++ calls and convert STL types to
 * Foundation types (NSDictionary, NSArray).
 *
 * ## Integration Steps
 *
 * 1. Build libjami for iOS (produces libjami.a)
 * 2. Build the adapter framework (JamiAdapters.framework) containing:
 *    - DRingAdapter, AccountAdapter, CallsAdapter, etc.
 *    - Utils.mm for type conversions
 * 3. Configure cinterop in build.gradle.kts:
 *    ```kotlin
 *    iosArm64 {
 *        compilations.getByName("main") {
 *            cinterops {
 *                create("JamiAdapters") {
 *                    defFile("src/nativeInterop/cinterop/libjami.def")
 *                }
 *            }
 *        }
 *    }
 *    ```
 *
 * ## Callback Pattern
 *
 * The adapters use @objc delegate protocols. This class implements those protocols
 * and forwards events to the DaemonCallbacks interface:
 *
 * ```kotlin
 * // When adapters are available:
 * class DaemonBridge : NSObject(), AccountAdapterDelegateProtocol, CallsAdapterDelegateProtocol {
 *     override fun accountsChanged() {
 *         callbacks?.onAccountsChanged()
 *     }
 *     override fun didChangeCallStateWithCallId(callId: String, state: String, ...) {
 *         callbacks?.onCallStateChanged(...)
 *     }
 * }
 * ```
 *
 * ## Current Status: Stub Implementation
 *
 * This is currently a stub that returns empty/default values. Once the adapter
 * framework is built and cinterop is configured, the real implementation will
 * call the Objective-C adapters.
 */
actual class DaemonBridge actual constructor() {
    private var isInitialized = false
    private var callbacks: DaemonCallbacks? = null

    // When adapters are available, these will be instances of the Objective-C adapter classes:
    // private var dringAdapter: DRingAdapter? = null
    // private var accountAdapter: AccountAdapter? = null
    // private var callsAdapter: CallsAdapter? = null
    // private var conversationsAdapter: ConversationsAdapter? = null
    // private var contactsAdapter: ContactsAdapter? = null
    // private var presenceAdapter: PresenceAdapter? = null
    // private var dataTransferAdapter: DataTransferAdapter? = null

    // ==================== Lifecycle ====================

    actual fun init(callbacks: DaemonCallbacks): Boolean {
        this.callbacks = callbacks
        // TODO: When adapters are available:
        // dringAdapter = DRingAdapter()
        // accountAdapter = AccountAdapter()
        // accountAdapter?.delegate = this  // Set self as delegate for callbacks
        // callsAdapter = CallsAdapter()
        // callsAdapter?.delegate = this
        // ... etc for all adapters
        // return dringAdapter?.initDaemon() ?: false
        isInitialized = true
        return true
    }

    actual fun start(): Boolean {
        // TODO: return dringAdapter?.startDaemon() ?: false
        return isInitialized
    }

    actual fun stop() {
        // TODO: dringAdapter?.fini()
        isInitialized = false
    }

    actual fun isRunning(): Boolean {
        // TODO: return dringAdapter?.isRunning() ?: false
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

    // ==================== Callback Delegate Methods ====================
    // When adapters are available, this class will implement the delegate protocols:
    //
    // AccountAdapterDelegateProtocol:
    // - accountsChanged()
    // - accountDetailsChanged(accountId: String, details: NSDictionary)
    // - registrationStateChanged(accountId: String, state: String, code: Int, details: String)
    // - knownDevicesChanged(accountId: String, devices: NSDictionary)
    // - exportOnRingEnded(accountId: String, code: Int, pin: String)
    // - nameRegistrationEnded(accountId: String, state: Int, name: String)
    // - registeredNameFound(accountId: String, state: Int, address: String, name: String)
    //
    // CallsAdapterDelegateProtocol:
    // - didChangeCallState(callId: String, state: String, accountId: String, stateCode: Int)
    // - receivingCall(accountId: String, callId: String, fromUri: String, withMedia: NSArray)
    // - incomingMessage(callId: String, from: String, message: NSDictionary)
    // - callPlacedOnHold(callId: String, hold: Bool)
    // - audioMuted(callId: String, muted: Bool)
    // - videoMuted(callId: String, muted: Bool)
    //
    // ConversationsAdapterDelegateProtocol:
    // - conversationLoaded(conversationId: String, accountId: String, messages: NSArray)
    // - conversationReady(accountId: String, conversationId: String)
    // - messageReceived(accountId: String, conversationId: String, message: SwarmMessageWrap)
    // - conversationRequestReceived(accountId: String, conversationId: String, metadata: NSDictionary)
}
