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
 * macOS implementation of DaemonBridge using Kotlin/Native Objective-C interop.
 *
 * ## Architecture
 *
 * This implementation shares the same approach as iOS - using the Objective-C++
 * adapters from jami-client-ios as the bridging layer. The adapters wrap C++
 * libjami calls and convert STL types to Foundation types.
 *
 * Note: jami-client-macos uses Objective-C++ directly (AccountsVC.mm, etc.),
 * but we can reuse the iOS adapter pattern for consistency in the KMP project.
 *
 * ## Integration Steps
 *
 * 1. Build libjami for macOS (produces libjami.dylib or libjami.a)
 * 2. Build the adapter framework (JamiAdapters.framework) containing:
 *    - DRingAdapter, AccountAdapter, CallsAdapter, etc.
 *    - Utils.mm for type conversions
 * 3. Configure cinterop in build.gradle.kts:
 *    ```kotlin
 *    macosArm64 {
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
 * Same as iOS - adapters use @objc delegate protocols. This class implements
 * those protocols and forwards events to the DaemonCallbacks interface.
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

    // When adapters are available, these will be instances of the Objective-C adapter classes
    // (same as iOS implementation)

    // ==================== Lifecycle ====================

    actual fun init(callbacks: DaemonCallbacks): Boolean {
        this.callbacks = callbacks
        // TODO: When adapters are available:
        // dringAdapter = DRingAdapter()
        // accountAdapter = AccountAdapter()
        // accountAdapter?.delegate = this
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
        // TODO: return accountAdapter?.changeAccountPassword(accountId, ...) ?: false
        return false
    }

    actual fun exportToFile(accountId: String, path: String, scheme: String, password: String): Boolean {
        // TODO: return accountAdapter?.exportToFile(accountId, ...) ?: false
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
        // TODO: accountAdapter?.revokeDevice(accountId, deviceId: deviceId, ...)
    }

    actual fun setDeviceName(accountId: String, deviceName: String) {
        // TODO: accountAdapter?.setDeviceName(accountId, name: deviceName)
    }

    // ==================== Profile ====================

    actual fun updateProfile(accountId: String, displayName: String, avatar: String, fileType: String, flag: Int) {
        // TODO: profilesAdapter?.updateProfile(...)
    }

    // ==================== Call Operations ====================

    actual fun placeCall(accountId: String, uri: String, mediaList: List<MediaAttribute>): String {
        // TODO: return callsAdapter?.placeCall(accountId, toUri: uri, withMedia: mediaList.toNSArray()) ?: ""
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
        // TODO: callsAdapter?.muteLocalMedia(...)
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
        // TODO: conversationsAdapter?.sendMessage(...)
    }

    actual fun loadConversation(accountId: String, conversationId: String, fromMessage: String, size: Int) {
        // TODO: conversationsAdapter?.loadConversation(...)
    }

    actual fun getConversationMembers(accountId: String, conversationId: String): List<Map<String, String>> {
        // TODO: return conversationsAdapter?.getConversationMembers(...)?.toKotlinListOfMaps() ?: emptyList()
        return emptyList()
    }

    actual fun getConversationInfo(accountId: String, conversationId: String): Map<String, String> {
        // TODO: return conversationsAdapter?.getConversationInfo(...)?.toKotlinMap() ?: emptyMap()
        return emptyMap()
    }

    actual fun removeConversation(accountId: String, conversationId: String) {
        // TODO: conversationsAdapter?.removeConversation(...)
    }

    actual fun addConversationMember(accountId: String, conversationId: String, uri: String) {
        // TODO: conversationsAdapter?.addConversationMember(...)
    }

    actual fun removeConversationMember(accountId: String, conversationId: String, uri: String) {
        // TODO: conversationsAdapter?.removeConversationMember(...)
    }

    actual fun updateConversationInfo(accountId: String, conversationId: String, info: Map<String, String>) {
        // TODO: conversationsAdapter?.updateConversationInfo(...)
    }

    actual fun getConversationPreferences(accountId: String, conversationId: String): Map<String, String> {
        // TODO: return conversationsAdapter?.getConversationPreferences(...)?.toKotlinMap() ?: emptyMap()
        return emptyMap()
    }

    actual fun setConversationPreferences(accountId: String, conversationId: String, prefs: Map<String, String>) {
        // TODO: conversationsAdapter?.setConversationPreferences(...)
    }

    actual fun setMessageDisplayed(accountId: String, conversationUri: String, messageId: String, status: Int) {
        // TODO: conversationsAdapter?.setMessageDisplayed(...)
    }

    actual fun getActiveCalls(accountId: String, conversationId: String): List<Map<String, String>> {
        // TODO: return conversationsAdapter?.getActiveCalls(...)?.toKotlinListOfMaps() ?: emptyList()
        return emptyList()
    }

    // ==================== Conversation Requests ====================

    actual fun getConversationRequests(accountId: String): List<Map<String, String>> {
        // TODO: return requestsAdapter?.getConversationRequests(accountId)?.toKotlinListOfMaps() ?: emptyList()
        return emptyList()
    }

    actual fun acceptConversationRequest(accountId: String, conversationId: String) {
        // TODO: requestsAdapter?.acceptConversationRequest(...)
    }

    actual fun declineConversationRequest(accountId: String, conversationId: String) {
        // TODO: requestsAdapter?.declineConversationRequest(...)
    }

    // ==================== Trust Requests ====================

    actual fun getTrustRequests(accountId: String): List<Map<String, String>> {
        // TODO: return contactsAdapter?.getTrustRequests(accountId)?.toKotlinListOfMaps() ?: emptyList()
        return emptyList()
    }

    actual fun acceptTrustRequest(accountId: String, uri: String) {
        // TODO: contactsAdapter?.acceptTrustRequest(...)
    }

    actual fun discardTrustRequest(accountId: String, uri: String) {
        // TODO: contactsAdapter?.discardTrustRequest(...)
    }

    actual fun sendTrustRequest(accountId: String, uri: String, payload: ByteArray) {
        // TODO: contactsAdapter?.sendTrustRequest(...)
    }

    // ==================== Contact Operations ====================

    actual fun addContact(accountId: String, uri: String) {
        // TODO: contactsAdapter?.addContact(...)
    }

    actual fun removeContact(accountId: String, uri: String, ban: Boolean) {
        // TODO: contactsAdapter?.removeContact(...)
    }

    actual fun getContacts(accountId: String): List<Map<String, String>> {
        // TODO: return contactsAdapter?.getContacts(accountId)?.toKotlinListOfMaps() ?: emptyList()
        return emptyList()
    }

    actual fun getContactDetails(accountId: String, uri: String): Map<String, String> {
        // TODO: return contactsAdapter?.getContactDetails(...)?.toKotlinMap() ?: emptyMap()
        return emptyMap()
    }

    actual fun subscribeBuddy(accountId: String, uri: String, subscribe: Boolean) {
        // TODO: presenceAdapter?.subscribeBuddy(...)
    }

    // ==================== Name Lookup ====================

    actual fun lookupName(accountId: String, nameServiceUrl: String, name: String): Boolean {
        // TODO: return accountAdapter?.lookupName(...) ?: false
        return false
    }

    actual fun lookupAddress(accountId: String, nameServiceUrl: String, address: String): Boolean {
        // TODO: return accountAdapter?.lookupAddress(...) ?: false
        return false
    }

    actual fun registerName(accountId: String, name: String, scheme: String, password: String): Boolean {
        // TODO: return accountAdapter?.registerName(...) ?: false
        return false
    }

    actual fun searchUser(accountId: String, query: String): Boolean {
        // TODO: return accountAdapter?.searchUser(...) ?: false
        return false
    }

    // ==================== Messaging ====================

    actual fun sendTextMessage(accountId: String, callIdOrUri: String, message: String) {
        // TODO: conversationsAdapter?.sendTextMessage(...)
    }

    actual fun setIsComposing(accountId: String, uri: String, isComposing: Boolean) {
        // TODO: conversationsAdapter?.setIsComposing(...)
    }

    actual fun cancelMessage(accountId: String, messageId: Long): Boolean {
        // TODO: return conversationsAdapter?.cancelMessage(...) ?: false
        return false
    }

    // ==================== File Transfer ====================

    actual fun sendFile(accountId: String, conversationId: String, filePath: String, displayName: String, parent: String) {
        // TODO: dataTransferAdapter?.sendFile(...)
    }

    actual fun downloadFile(accountId: String, conversationId: String, interactionId: String, fileId: String, path: String) {
        // TODO: dataTransferAdapter?.downloadFile(...)
    }

    actual fun cancelDataTransfer(accountId: String, conversationId: String, fileId: String) {
        // TODO: dataTransferAdapter?.cancelDataTransfer(...)
    }

    actual fun fileTransferInfo(accountId: String, conversationId: String, fileId: String): FileTransferInfo? {
        // TODO: return dataTransferAdapter?.fileTransferInfo(...)?.toKotlin()
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
        // TODO: audioAdapter?.setActiveCodecList(...)
    }

    actual fun getCodecDetails(accountId: String, codecId: Long): Map<String, String> {
        // TODO: return audioAdapter?.getCodecDetails(...)?.toKotlinMap() ?: emptyMap()
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
        // TODO: accountAdapter?.pushNotificationReceived(...)
    }
}
