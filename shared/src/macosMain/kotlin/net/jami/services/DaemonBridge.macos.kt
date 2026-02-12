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
 * macOS implementation of DaemonBridge using Kotlin/Native cinterop.
 *
 * Same approach as iOS - uses cinterop to call libjami C functions.
 * Shares the same .def file and patterns with iosMain.
 */
actual class DaemonBridge actual constructor() {
    private var isInitialized = false
    private var callbacks: DaemonCallbacks? = null

    // ==================== Lifecycle ====================

    actual fun init(callbacks: DaemonCallbacks): Boolean {
        this.callbacks = callbacks
        // TODO: Implement with cinterop when libjami is built for macOS
        isInitialized = true
        return true
    }

    actual fun start(): Boolean = isInitialized
    actual fun stop() { isInitialized = false }
    actual fun isRunning(): Boolean = isInitialized

    // ==================== Account Operations ====================

    actual fun addAccount(details: Map<String, String>): String = ""
    actual fun removeAccount(accountId: String) {}
    actual fun getAccountDetails(accountId: String): Map<String, String> = emptyMap()
    actual fun setAccountDetails(accountId: String, details: Map<String, String>) {}
    actual fun getAccountList(): List<String> = emptyList()
    actual fun setAccountActive(accountId: String, active: Boolean) {}
    actual fun getAccountTemplate(accountType: String): Map<String, String> = emptyMap()
    actual fun getVolatileAccountDetails(accountId: String): Map<String, String> = emptyMap()
    actual fun sendRegister(accountId: String, enable: Boolean) {}
    actual fun setAccountsOrder(order: String) {}
    actual fun changeAccountPassword(accountId: String, oldPassword: String, newPassword: String): Boolean = false
    actual fun exportToFile(accountId: String, path: String, scheme: String, password: String): Boolean = false

    // ==================== Credentials ====================

    actual fun getCredentials(accountId: String): List<Map<String, String>> = emptyList()
    actual fun setCredentials(accountId: String, credentials: List<Map<String, String>>) {}

    // ==================== Device Management ====================

    actual fun getKnownRingDevices(accountId: String): Map<String, String> = emptyMap()
    actual fun revokeDevice(accountId: String, deviceId: String, scheme: String, password: String) {}
    actual fun setDeviceName(accountId: String, deviceName: String) {}

    // ==================== Profile ====================

    actual fun updateProfile(accountId: String, displayName: String, avatar: String, fileType: String, flag: Int) {}

    // ==================== Call Operations ====================

    actual fun placeCall(accountId: String, uri: String, mediaList: List<MediaAttribute>): String = ""
    actual fun accept(accountId: String, callId: String, mediaList: List<MediaAttribute>) {}
    actual fun hangUp(accountId: String, callId: String) {}
    actual fun hold(accountId: String, callId: String) {}
    actual fun unhold(accountId: String, callId: String) {}
    actual fun muteLocalMedia(accountId: String, callId: String, mediaType: String, mute: Boolean) {}

    // ==================== Conversation Operations ====================

    actual fun getConversations(accountId: String): List<String> = emptyList()
    actual fun startConversation(accountId: String): String = ""
    actual fun sendMessage(accountId: String, conversationId: String, message: String, replyTo: String, flag: Int) {}
    actual fun loadConversation(accountId: String, conversationId: String, fromMessage: String, size: Int) {}
    actual fun getConversationMembers(accountId: String, conversationId: String): List<Map<String, String>> = emptyList()
    actual fun getConversationInfo(accountId: String, conversationId: String): Map<String, String> = emptyMap()
    actual fun removeConversation(accountId: String, conversationId: String) {}
    actual fun addConversationMember(accountId: String, conversationId: String, uri: String) {}
    actual fun removeConversationMember(accountId: String, conversationId: String, uri: String) {}
    actual fun updateConversationInfo(accountId: String, conversationId: String, info: Map<String, String>) {}
    actual fun getConversationPreferences(accountId: String, conversationId: String): Map<String, String> = emptyMap()
    actual fun setConversationPreferences(accountId: String, conversationId: String, prefs: Map<String, String>) {}
    actual fun setMessageDisplayed(accountId: String, conversationUri: String, messageId: String, status: Int) {}
    actual fun getActiveCalls(accountId: String, conversationId: String): List<Map<String, String>> = emptyList()

    // ==================== Conversation Requests ====================

    actual fun getConversationRequests(accountId: String): List<Map<String, String>> = emptyList()
    actual fun acceptConversationRequest(accountId: String, conversationId: String) {}
    actual fun declineConversationRequest(accountId: String, conversationId: String) {}

    // ==================== Trust Requests ====================

    actual fun getTrustRequests(accountId: String): List<Map<String, String>> = emptyList()
    actual fun acceptTrustRequest(accountId: String, uri: String) {}
    actual fun discardTrustRequest(accountId: String, uri: String) {}
    actual fun sendTrustRequest(accountId: String, uri: String, payload: ByteArray) {}

    // ==================== Contact Operations ====================

    actual fun addContact(accountId: String, uri: String) {}
    actual fun removeContact(accountId: String, uri: String, ban: Boolean) {}
    actual fun getContacts(accountId: String): List<Map<String, String>> = emptyList()
    actual fun getContactDetails(accountId: String, uri: String): Map<String, String> = emptyMap()
    actual fun subscribeBuddy(accountId: String, uri: String, subscribe: Boolean) {}

    // ==================== Name Lookup ====================

    actual fun lookupName(accountId: String, nameServiceUrl: String, name: String): Boolean = false
    actual fun lookupAddress(accountId: String, nameServiceUrl: String, address: String): Boolean = false
    actual fun registerName(accountId: String, name: String, scheme: String, password: String): Boolean = false
    actual fun searchUser(accountId: String, query: String): Boolean = false

    // ==================== Messaging ====================

    actual fun sendTextMessage(accountId: String, callIdOrUri: String, message: String) {}
    actual fun setIsComposing(accountId: String, uri: String, isComposing: Boolean) {}
    actual fun cancelMessage(accountId: String, messageId: Long): Boolean = false

    // ==================== File Transfer ====================

    actual fun sendFile(accountId: String, conversationId: String, filePath: String, displayName: String, parent: String) {}
    actual fun downloadFile(accountId: String, conversationId: String, interactionId: String, fileId: String, path: String) {}
    actual fun cancelDataTransfer(accountId: String, conversationId: String, fileId: String) {}
    actual fun fileTransferInfo(accountId: String, conversationId: String, fileId: String): FileTransferInfo? = null

    // ==================== Codec Operations ====================

    actual fun getCodecList(): List<Long> = emptyList()
    actual fun getActiveCodecList(accountId: String): List<Long> = emptyList()
    actual fun setActiveCodecList(accountId: String, codecList: List<Long>) {}
    actual fun getCodecDetails(accountId: String, codecId: Long): Map<String, String> = emptyMap()

    // ==================== Push Notifications ====================

    actual fun setPushNotificationToken(token: String) {}
    actual fun setPushNotificationConfig(config: Map<String, String>) {}
    actual fun pushNotificationReceived(from: String, data: Map<String, String>) {}
}
