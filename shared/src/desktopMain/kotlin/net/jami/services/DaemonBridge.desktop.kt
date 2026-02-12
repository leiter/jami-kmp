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
import net.jami.utils.Log

/**
 * Desktop (JVM) implementation of DaemonBridge.
 *
 * This is a stub implementation. When the native library is built for desktop,
 * this can be updated to use the same SWIG JNI bindings as Android.
 *
 * ## To Enable Full JNI Integration
 *
 * 1. Build libjami for desktop platform (Linux, Windows, or macOS)
 * 2. The SWIG Java classes in androidMain/java can be shared
 * 3. Create a separate JVM module or use Gradle's Java compilation support
 * 4. The implementation pattern is identical to Android (see DaemonBridge.android.kt)
 *
 * ## Note
 *
 * Gradle KMP doesn't support withJava() when Android plugin is present,
 * so a separate module would be needed to share Java SWIG classes between
 * Android and Desktop targets.
 */
actual class DaemonBridge actual constructor() {
    private var isInitialized = false
    private var callbacks: DaemonCallbacks? = null

    companion object {
        private const val TAG = "DaemonBridge"
        private var isNativeLoaded = false

        init {
            try {
                System.loadLibrary("jami")
                isNativeLoaded = true
                Log.i(TAG, "Native library 'jami' loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "Native library 'jami' not available - running in stub mode")
            }
        }
    }

    actual fun init(callbacks: DaemonCallbacks): Boolean {
        this.callbacks = callbacks

        if (!isNativeLoaded) {
            Log.w(TAG, "Running in stub mode - no native library")
            isInitialized = true
            return true
        }

        // When native library is available, initialize here
        // See DaemonBridge.android.kt for full implementation pattern
        isInitialized = true
        Log.i(TAG, "DaemonBridge initialized (desktop stub mode)")
        return true
    }

    actual fun start(): Boolean {
        Log.i(TAG, "Daemon started (desktop stub mode)")
        return isInitialized
    }

    actual fun stop() {
        if (isInitialized) {
            isInitialized = false
            Log.i(TAG, "Daemon stopped (desktop)")
        }
    }

    actual fun isRunning(): Boolean = isInitialized

    // ==================== Account Operations (Stubs) ====================

    actual fun addAccount(details: Map<String, String>): String {
        Log.d(TAG, "addAccount called (stub)")
        return ""
    }

    actual fun removeAccount(accountId: String) {
        Log.d(TAG, "removeAccount called (stub): $accountId")
    }

    actual fun getAccountDetails(accountId: String): Map<String, String> {
        Log.d(TAG, "getAccountDetails called (stub): $accountId")
        return emptyMap()
    }

    actual fun setAccountDetails(accountId: String, details: Map<String, String>) {
        Log.d(TAG, "setAccountDetails called (stub): $accountId")
    }

    actual fun getAccountList(): List<String> {
        Log.d(TAG, "getAccountList called (stub)")
        return emptyList()
    }

    actual fun setAccountActive(accountId: String, active: Boolean) {
        Log.d(TAG, "setAccountActive called (stub): $accountId, active=$active")
    }

    actual fun getAccountTemplate(accountType: String): Map<String, String> {
        Log.d(TAG, "getAccountTemplate called (stub): $accountType")
        return emptyMap()
    }

    actual fun getVolatileAccountDetails(accountId: String): Map<String, String> {
        Log.d(TAG, "getVolatileAccountDetails called (stub): $accountId")
        return emptyMap()
    }

    actual fun sendRegister(accountId: String, enable: Boolean) {
        Log.d(TAG, "sendRegister called (stub): $accountId, enable=$enable")
    }

    actual fun setAccountsOrder(order: String) {
        Log.d(TAG, "setAccountsOrder called (stub)")
    }

    actual fun changeAccountPassword(accountId: String, oldPassword: String, newPassword: String): Boolean {
        Log.d(TAG, "changeAccountPassword called (stub): $accountId")
        return false
    }

    actual fun exportToFile(accountId: String, path: String, scheme: String, password: String): Boolean {
        Log.d(TAG, "exportToFile called (stub): $accountId -> $path")
        return false
    }

    // ==================== Credentials (Stubs) ====================

    actual fun getCredentials(accountId: String): List<Map<String, String>> {
        Log.d(TAG, "getCredentials called (stub): $accountId")
        return emptyList()
    }

    actual fun setCredentials(accountId: String, credentials: List<Map<String, String>>) {
        Log.d(TAG, "setCredentials called (stub): $accountId")
    }

    // ==================== Device Management (Stubs) ====================

    actual fun getKnownRingDevices(accountId: String): Map<String, String> {
        Log.d(TAG, "getKnownRingDevices called (stub): $accountId")
        return emptyMap()
    }

    actual fun revokeDevice(accountId: String, deviceId: String, scheme: String, password: String) {
        Log.d(TAG, "revokeDevice called (stub): $accountId, device=$deviceId")
    }

    actual fun setDeviceName(accountId: String, deviceName: String) {
        Log.d(TAG, "setDeviceName called (stub): $accountId, name=$deviceName")
    }

    // ==================== Profile (Stubs) ====================

    actual fun updateProfile(accountId: String, displayName: String, avatar: String, fileType: String, flag: Int) {
        Log.d(TAG, "updateProfile called (stub): $accountId, name=$displayName")
    }

    // ==================== Call Operations (Stubs) ====================

    actual fun placeCall(accountId: String, uri: String, mediaList: List<MediaAttribute>): String {
        Log.d(TAG, "placeCall called (stub): $accountId -> $uri")
        return ""
    }

    actual fun accept(accountId: String, callId: String, mediaList: List<MediaAttribute>) {
        Log.d(TAG, "accept called (stub): $callId")
    }

    actual fun hangUp(accountId: String, callId: String) {
        Log.d(TAG, "hangUp called (stub): $callId")
    }

    actual fun hold(accountId: String, callId: String) {
        Log.d(TAG, "hold called (stub): $callId")
    }

    actual fun unhold(accountId: String, callId: String) {
        Log.d(TAG, "unhold called (stub): $callId")
    }

    actual fun muteLocalMedia(accountId: String, callId: String, mediaType: String, mute: Boolean) {
        Log.d(TAG, "muteLocalMedia called (stub): $callId, $mediaType, mute=$mute")
    }

    // ==================== Conversation Operations (Stubs) ====================

    actual fun getConversations(accountId: String): List<String> {
        Log.d(TAG, "getConversations called (stub): $accountId")
        return emptyList()
    }

    actual fun startConversation(accountId: String): String {
        Log.d(TAG, "startConversation called (stub): $accountId")
        return ""
    }

    actual fun sendMessage(accountId: String, conversationId: String, message: String, replyTo: String, flag: Int) {
        Log.d(TAG, "sendMessage called (stub): $conversationId")
    }

    actual fun loadConversation(accountId: String, conversationId: String, fromMessage: String, size: Int) {
        Log.d(TAG, "loadConversation called (stub): $conversationId")
    }

    actual fun getConversationMembers(accountId: String, conversationId: String): List<Map<String, String>> {
        Log.d(TAG, "getConversationMembers called (stub): $conversationId")
        return emptyList()
    }

    actual fun getConversationInfo(accountId: String, conversationId: String): Map<String, String> {
        Log.d(TAG, "getConversationInfo called (stub): $conversationId")
        return emptyMap()
    }

    actual fun removeConversation(accountId: String, conversationId: String) {
        Log.d(TAG, "removeConversation called (stub): $conversationId")
    }

    actual fun addConversationMember(accountId: String, conversationId: String, uri: String) {
        Log.d(TAG, "addConversationMember called (stub): $conversationId, $uri")
    }

    actual fun removeConversationMember(accountId: String, conversationId: String, uri: String) {
        Log.d(TAG, "removeConversationMember called (stub): $conversationId, $uri")
    }

    actual fun updateConversationInfo(accountId: String, conversationId: String, info: Map<String, String>) {
        Log.d(TAG, "updateConversationInfo called (stub): $conversationId")
    }

    actual fun getConversationPreferences(accountId: String, conversationId: String): Map<String, String> {
        Log.d(TAG, "getConversationPreferences called (stub): $conversationId")
        return emptyMap()
    }

    actual fun setConversationPreferences(accountId: String, conversationId: String, prefs: Map<String, String>) {
        Log.d(TAG, "setConversationPreferences called (stub): $conversationId")
    }

    actual fun setMessageDisplayed(accountId: String, conversationUri: String, messageId: String, status: Int) {
        Log.d(TAG, "setMessageDisplayed called (stub): $conversationUri, $messageId")
    }

    actual fun getActiveCalls(accountId: String, conversationId: String): List<Map<String, String>> {
        Log.d(TAG, "getActiveCalls called (stub): $conversationId")
        return emptyList()
    }

    // ==================== Conversation Requests (Stubs) ====================

    actual fun getConversationRequests(accountId: String): List<Map<String, String>> {
        Log.d(TAG, "getConversationRequests called (stub): $accountId")
        return emptyList()
    }

    actual fun acceptConversationRequest(accountId: String, conversationId: String) {
        Log.d(TAG, "acceptConversationRequest called (stub): $conversationId")
    }

    actual fun declineConversationRequest(accountId: String, conversationId: String) {
        Log.d(TAG, "declineConversationRequest called (stub): $conversationId")
    }

    // ==================== Trust Requests (Stubs) ====================

    actual fun getTrustRequests(accountId: String): List<Map<String, String>> {
        Log.d(TAG, "getTrustRequests called (stub): $accountId")
        return emptyList()
    }

    actual fun acceptTrustRequest(accountId: String, uri: String) {
        Log.d(TAG, "acceptTrustRequest called (stub): $uri")
    }

    actual fun discardTrustRequest(accountId: String, uri: String) {
        Log.d(TAG, "discardTrustRequest called (stub): $uri")
    }

    actual fun sendTrustRequest(accountId: String, uri: String, payload: ByteArray) {
        Log.d(TAG, "sendTrustRequest called (stub): $uri")
    }

    // ==================== Contact Operations (Stubs) ====================

    actual fun addContact(accountId: String, uri: String) {
        Log.d(TAG, "addContact called (stub): $uri")
    }

    actual fun removeContact(accountId: String, uri: String, ban: Boolean) {
        Log.d(TAG, "removeContact called (stub): $uri")
    }

    actual fun getContacts(accountId: String): List<Map<String, String>> {
        Log.d(TAG, "getContacts called (stub): $accountId")
        return emptyList()
    }

    actual fun getContactDetails(accountId: String, uri: String): Map<String, String> {
        Log.d(TAG, "getContactDetails called (stub): $uri")
        return emptyMap()
    }

    actual fun subscribeBuddy(accountId: String, uri: String, subscribe: Boolean) {
        Log.d(TAG, "subscribeBuddy called (stub): $uri, subscribe=$subscribe")
    }

    // ==================== Name Lookup (Stubs) ====================

    actual fun lookupName(accountId: String, nameServiceUrl: String, name: String): Boolean {
        Log.d(TAG, "lookupName called (stub): $name")
        return false
    }

    actual fun lookupAddress(accountId: String, nameServiceUrl: String, address: String): Boolean {
        Log.d(TAG, "lookupAddress called (stub): $address")
        return false
    }

    actual fun registerName(accountId: String, name: String, scheme: String, password: String): Boolean {
        Log.d(TAG, "registerName called (stub): $name")
        return false
    }

    actual fun searchUser(accountId: String, query: String): Boolean {
        Log.d(TAG, "searchUser called (stub): $query")
        return false
    }

    // ==================== Messaging (Stubs) ====================

    actual fun sendTextMessage(accountId: String, callIdOrUri: String, message: String) {
        Log.d(TAG, "sendTextMessage called (stub): $callIdOrUri")
    }

    actual fun setIsComposing(accountId: String, uri: String, isComposing: Boolean) {
        Log.d(TAG, "setIsComposing called (stub): $uri, composing=$isComposing")
    }

    actual fun cancelMessage(accountId: String, messageId: Long): Boolean {
        Log.d(TAG, "cancelMessage called (stub): $messageId")
        return false
    }

    // ==================== File Transfer (Stubs) ====================

    actual fun sendFile(accountId: String, conversationId: String, filePath: String, displayName: String, parent: String) {
        Log.d(TAG, "sendFile called (stub): $conversationId, $filePath")
    }

    actual fun downloadFile(accountId: String, conversationId: String, interactionId: String, fileId: String, path: String) {
        Log.d(TAG, "downloadFile called (stub): $conversationId, $fileId")
    }

    actual fun cancelDataTransfer(accountId: String, conversationId: String, fileId: String) {
        Log.d(TAG, "cancelDataTransfer called (stub): $fileId")
    }

    actual fun fileTransferInfo(accountId: String, conversationId: String, fileId: String): FileTransferInfo? {
        Log.d(TAG, "fileTransferInfo called (stub): $fileId")
        return null
    }

    // ==================== Codec Operations (Stubs) ====================

    actual fun getCodecList(): List<Long> {
        Log.d(TAG, "getCodecList called (stub)")
        return emptyList()
    }

    actual fun getActiveCodecList(accountId: String): List<Long> {
        Log.d(TAG, "getActiveCodecList called (stub): $accountId")
        return emptyList()
    }

    actual fun setActiveCodecList(accountId: String, codecList: List<Long>) {
        Log.d(TAG, "setActiveCodecList called (stub): $accountId")
    }

    actual fun getCodecDetails(accountId: String, codecId: Long): Map<String, String> {
        Log.d(TAG, "getCodecDetails called (stub): $codecId")
        return emptyMap()
    }

    // ==================== Push Notifications (Stubs) ====================

    actual fun setPushNotificationToken(token: String) {
        Log.d(TAG, "setPushNotificationToken called (stub)")
    }

    actual fun setPushNotificationConfig(config: Map<String, String>) {
        Log.d(TAG, "setPushNotificationConfig called (stub)")
    }

    actual fun pushNotificationReceived(from: String, data: Map<String, String>) {
        Log.d(TAG, "pushNotificationReceived called (stub): $from")
    }
}
