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
actual class DaemonBridge() : DaemonBridgeApi {
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

    override fun init(callbacks: DaemonCallbacks): Boolean {
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

    override fun start(): Boolean {
        Log.i(TAG, "Daemon started (desktop stub mode)")
        return isInitialized
    }

    override fun stop() {
        if (isInitialized) {
            isInitialized = false
            Log.i(TAG, "Daemon stopped (desktop)")
        }
    }

    override fun isRunning(): Boolean = isInitialized

    // ==================== Account Operations (Stubs) ====================

    override fun addAccount(details: Map<String, String>): String {
        Log.d(TAG, "addAccount called (stub)")
        return ""
    }

    override fun removeAccount(accountId: String) {
        Log.d(TAG, "removeAccount called (stub): $accountId")
    }

    override fun getAccountDetails(accountId: String): Map<String, String> {
        Log.d(TAG, "getAccountDetails called (stub): $accountId")
        return emptyMap()
    }

    override fun setAccountDetails(accountId: String, details: Map<String, String>) {
        Log.d(TAG, "setAccountDetails called (stub): $accountId")
    }

    override fun getAccountList(): List<String> {
        Log.d(TAG, "getAccountList called (stub)")
        return emptyList()
    }

    override fun setAccountActive(accountId: String, active: Boolean) {
        Log.d(TAG, "setAccountActive called (stub): $accountId, active=$active")
    }

    override fun getAccountTemplate(accountType: String): Map<String, String> {
        Log.d(TAG, "getAccountTemplate called (stub): $accountType")
        return emptyMap()
    }

    override fun getVolatileAccountDetails(accountId: String): Map<String, String> {
        Log.d(TAG, "getVolatileAccountDetails called (stub): $accountId")
        return emptyMap()
    }

    override fun sendRegister(accountId: String, enable: Boolean) {
        Log.d(TAG, "sendRegister called (stub): $accountId, enable=$enable")
    }

    override fun setAccountsOrder(order: String) {
        Log.d(TAG, "setAccountsOrder called (stub)")
    }

    override fun changeAccountPassword(accountId: String, oldPassword: String, newPassword: String): Boolean {
        Log.d(TAG, "changeAccountPassword called (stub): $accountId")
        return false
    }

    override fun exportToFile(accountId: String, path: String, scheme: String, password: String): Boolean {
        Log.d(TAG, "exportToFile called (stub): $accountId -> $path")
        return false
    }

    // ==================== Credentials (Stubs) ====================

    override fun getCredentials(accountId: String): List<Map<String, String>> {
        Log.d(TAG, "getCredentials called (stub): $accountId")
        return emptyList()
    }

    override fun setCredentials(accountId: String, credentials: List<Map<String, String>>) {
        Log.d(TAG, "setCredentials called (stub): $accountId")
    }

    // ==================== Device Management (Stubs) ====================

    override fun getKnownRingDevices(accountId: String): Map<String, String> {
        Log.d(TAG, "getKnownRingDevices called (stub): $accountId")
        return emptyMap()
    }

    override fun revokeDevice(accountId: String, deviceId: String, scheme: String, password: String) {
        Log.d(TAG, "revokeDevice called (stub): $accountId, device=$deviceId")
    }

    override fun setDeviceName(accountId: String, deviceName: String) {
        Log.d(TAG, "setDeviceName called (stub): $accountId, name=$deviceName")
    }

    // ==================== Profile (Stubs) ====================

    override fun updateProfile(accountId: String, displayName: String, avatar: String, fileType: String, flag: Int) {
        Log.d(TAG, "updateProfile called (stub): $accountId, name=$displayName")
    }

    // ==================== Call Operations (Stubs) ====================

    override fun placeCall(accountId: String, uri: String, mediaList: List<MediaAttribute>): String {
        Log.d(TAG, "placeCall called (stub): $accountId -> $uri")
        return ""
    }

    override fun accept(accountId: String, callId: String, mediaList: List<MediaAttribute>) {
        Log.d(TAG, "accept called (stub): $callId")
    }

    override fun hangUp(accountId: String, callId: String) {
        Log.d(TAG, "hangUp called (stub): $callId")
    }

    override fun hold(accountId: String, callId: String) {
        Log.d(TAG, "hold called (stub): $callId")
    }

    override fun unhold(accountId: String, callId: String) {
        Log.d(TAG, "unhold called (stub): $callId")
    }

    override fun muteLocalMedia(accountId: String, callId: String, mediaType: String, mute: Boolean) {
        Log.d(TAG, "muteLocalMedia called (stub): $callId, $mediaType, mute=$mute")
    }

    // ==================== Conference Operations ====================
    override fun holdConference(accountId: String, confId: String): Boolean {
        Log.d(TAG, "holdConference called (stub): $confId")
        return true
    }

    override fun unholdConference(accountId: String, confId: String): Boolean {
        Log.d(TAG, "unholdConference called (stub): $confId")
        return true
    }

    override fun setActiveParticipant(accountId: String, confId: String, callId: String) {
        Log.d(TAG, "setActiveParticipant called (stub): $confId callId=$callId")
    }

    override fun setConferenceLayout(accountId: String, confId: String, layout: Int) {
        Log.d(TAG, "setConferenceLayout called (stub): $confId layout=$layout")
    }

    // ==================== Conversation Operations (Stubs) ====================

    override fun getConversations(accountId: String): List<String> {
        Log.d(TAG, "getConversations called (stub): $accountId")
        return emptyList()
    }

    override fun startConversation(accountId: String): String {
        Log.d(TAG, "startConversation called (stub): $accountId")
        return ""
    }

    override fun sendMessage(accountId: String, conversationId: String, message: String, replyTo: String, flag: Int) {
        Log.d(TAG, "sendMessage called (stub): $conversationId")
    }

    override fun loadConversation(accountId: String, conversationId: String, fromMessage: String, size: Int) {
        Log.d(TAG, "loadConversation called (stub): $conversationId")
    }

    override fun getConversationMembers(accountId: String, conversationId: String): List<Map<String, String>> {
        Log.d(TAG, "getConversationMembers called (stub): $conversationId")
        return emptyList()
    }

    override fun getConversationInfo(accountId: String, conversationId: String): Map<String, String> {
        Log.d(TAG, "getConversationInfo called (stub): $conversationId")
        return emptyMap()
    }

    override fun removeConversation(accountId: String, conversationId: String) {
        Log.d(TAG, "removeConversation called (stub): $conversationId")
    }

    override fun addConversationMember(accountId: String, conversationId: String, uri: String) {
        Log.d(TAG, "addConversationMember called (stub): $conversationId, $uri")
    }

    override fun removeConversationMember(accountId: String, conversationId: String, uri: String) {
        Log.d(TAG, "removeConversationMember called (stub): $conversationId, $uri")
    }

    override fun updateConversationInfo(accountId: String, conversationId: String, info: Map<String, String>) {
        Log.d(TAG, "updateConversationInfo called (stub): $conversationId")
    }

    override fun getConversationPreferences(accountId: String, conversationId: String): Map<String, String> {
        Log.d(TAG, "getConversationPreferences called (stub): $conversationId")
        return emptyMap()
    }

    override fun setConversationPreferences(accountId: String, conversationId: String, prefs: Map<String, String>) {
        Log.d(TAG, "setConversationPreferences called (stub): $conversationId")
    }

    override fun setMessageDisplayed(accountId: String, conversationUri: String, messageId: String, status: Int) {
        Log.d(TAG, "setMessageDisplayed called (stub): $conversationUri, $messageId")
    }

    override fun getActiveCalls(accountId: String, conversationId: String): List<Map<String, String>> {
        Log.d(TAG, "getActiveCalls called (stub): $conversationId")
        return emptyList()
    }

    // ==================== Conversation Requests (Stubs) ====================

    override fun getConversationRequests(accountId: String): List<Map<String, String>> {
        Log.d(TAG, "getConversationRequests called (stub): $accountId")
        return emptyList()
    }

    override fun acceptConversationRequest(accountId: String, conversationId: String) {
        Log.d(TAG, "acceptConversationRequest called (stub): $conversationId")
    }

    override fun declineConversationRequest(accountId: String, conversationId: String) {
        Log.d(TAG, "declineConversationRequest called (stub): $conversationId")
    }

    // ==================== Trust Requests (Stubs) ====================

    override fun getTrustRequests(accountId: String): List<Map<String, String>> {
        Log.d(TAG, "getTrustRequests called (stub): $accountId")
        return emptyList()
    }

    override fun acceptTrustRequest(accountId: String, uri: String) {
        Log.d(TAG, "acceptTrustRequest called (stub): $uri")
    }

    override fun discardTrustRequest(accountId: String, uri: String) {
        Log.d(TAG, "discardTrustRequest called (stub): $uri")
    }

    override fun sendTrustRequest(accountId: String, uri: String, payload: ByteArray) {
        Log.d(TAG, "sendTrustRequest called (stub): $uri")
    }

    // ==================== Contact Operations (Stubs) ====================

    override fun addContact(accountId: String, uri: String) {
        Log.d(TAG, "addContact called (stub): $uri")
    }

    override fun removeContact(accountId: String, uri: String, ban: Boolean) {
        Log.d(TAG, "removeContact called (stub): $uri")
    }

    override fun getContacts(accountId: String): List<Map<String, String>> {
        Log.d(TAG, "getContacts called (stub): $accountId")
        return emptyList()
    }

    override fun getContactDetails(accountId: String, uri: String): Map<String, String> {
        Log.d(TAG, "getContactDetails called (stub): $uri")
        return emptyMap()
    }

    override fun subscribeBuddy(accountId: String, uri: String, subscribe: Boolean) {
        Log.d(TAG, "subscribeBuddy called (stub): $uri, subscribe=$subscribe")
    }

    // ==================== Name Lookup (Stubs) ====================

    override fun lookupName(accountId: String, nameServiceUrl: String, name: String): Boolean {
        Log.d(TAG, "lookupName called (stub): $name")
        return false
    }

    override fun lookupAddress(accountId: String, nameServiceUrl: String, address: String): Boolean {
        Log.d(TAG, "lookupAddress called (stub): $address")
        return false
    }

    override fun registerName(accountId: String, name: String, scheme: String, password: String): Boolean {
        Log.d(TAG, "registerName called (stub): $name")
        return false
    }

    override fun searchUser(accountId: String, query: String): Boolean {
        Log.d(TAG, "searchUser called (stub): $query")
        return false
    }

    // ==================== Messaging (Stubs) ====================

    override fun sendTextMessage(accountId: String, callIdOrUri: String, message: String) {
        Log.d(TAG, "sendTextMessage called (stub): $callIdOrUri")
    }

    override fun setIsComposing(accountId: String, uri: String, isComposing: Boolean) {
        Log.d(TAG, "setIsComposing called (stub): $uri, composing=$isComposing")
    }

    override fun cancelMessage(accountId: String, messageId: Long): Boolean {
        Log.d(TAG, "cancelMessage called (stub): $messageId")
        return false
    }

    // ==================== File Transfer (Stubs) ====================

    override fun sendFile(accountId: String, conversationId: String, filePath: String, displayName: String, parent: String) {
        Log.d(TAG, "sendFile called (stub): $conversationId, $filePath")
    }

    override fun downloadFile(accountId: String, conversationId: String, interactionId: String, fileId: String, path: String) {
        Log.d(TAG, "downloadFile called (stub): $conversationId, $fileId")
    }

    override fun cancelDataTransfer(accountId: String, conversationId: String, fileId: String) {
        Log.d(TAG, "cancelDataTransfer called (stub): $fileId")
    }

    override fun fileTransferInfo(accountId: String, conversationId: String, fileId: String): FileTransferInfo? {
        Log.d(TAG, "fileTransferInfo called (stub): $fileId")
        return null
    }

    // ==================== Search & History (Stubs) ====================

    override fun searchConversation(accountId: String, conversationId: String, author: String, lastId: String, query: String, type: String, after: Long, before: Long, maxResult: Long, flag: Int): Long {
        Log.d(TAG, "searchConversation called (stub): $conversationId query=$query")
        return -1L
    }

    override fun loadSwarmUntil(accountId: String, conversationId: String, fromMessage: String, toMessage: String): Long {
        Log.d(TAG, "loadSwarmUntil called (stub): $conversationId")
        return -1L
    }

    // ==================== Codec Operations (Stubs) ====================

    override fun getCodecList(): List<Long> {
        Log.d(TAG, "getCodecList called (stub)")
        return emptyList()
    }

    override fun getActiveCodecList(accountId: String): List<Long> {
        Log.d(TAG, "getActiveCodecList called (stub): $accountId")
        return emptyList()
    }

    override fun setActiveCodecList(accountId: String, codecList: List<Long>) {
        Log.d(TAG, "setActiveCodecList called (stub): $accountId")
    }

    override fun getCodecDetails(accountId: String, codecId: Long): Map<String, String> {
        Log.d(TAG, "getCodecDetails called (stub): $codecId")
        return emptyMap()
    }

    // ==================== Push Notifications (Stubs) ====================

    override fun setPushNotificationToken(token: String) {
        Log.d(TAG, "setPushNotificationToken called (stub)")
    }

    override fun setPushNotificationConfig(config: Map<String, String>) {
        Log.d(TAG, "setPushNotificationConfig called (stub)")
    }

    override fun pushNotificationReceived(from: String, data: Map<String, String>) {
        Log.d(TAG, "pushNotificationReceived called (stub): $from")
    }
}
