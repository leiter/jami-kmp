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
 * Web (Kotlin/JS) implementation of DaemonBridge using REST Bridge API.
 *
 * ## Architecture
 *
 * Since JavaScript cannot directly load native libraries, this implementation
 * communicates with a REST Bridge server that wraps libjami. The server handles
 * all daemon operations and broadcasts events via WebSocket.
 *
 * ```
 * ┌─────────────────────────────────────────────────────────────────┐
 * │                       Web Browser                               │
 * │  ┌───────────────────────────────────────────────────────────┐ │
 * │  │           DaemonBridge (Kotlin/JS)                        │ │
 * │  │  ┌─────────────┐  ┌─────────────┐  ┌──────────────────┐  │ │
 * │  │  │ HTTP Client │  │  WebSocket  │  │ Event → Callbacks│  │ │
 * │  │  │ (fetch API) │  │  (events)   │  │                  │  │ │
 * │  │  └─────────────┘  └─────────────┘  └──────────────────┘  │ │
 * │  └───────────────────────────────────────────────────────────┘ │
 * └─────────────────────────────────────────────────────────────────┘
 *                              │
 *                              ▼ HTTP/WebSocket
 * ┌─────────────────────────────────────────────────────────────────┐
 * │                    REST Bridge Server                           │
 * │                         libjami                                 │
 * └─────────────────────────────────────────────────────────────────┘
 * ```
 *
 * ## API Specification
 *
 * See `docs/REST_BRIDGE_API.md` for the full REST/WebSocket API specification.
 *
 * ## Implementation Notes
 *
 * ### HTTP Client
 * Uses browser's native `fetch()` API for HTTP requests. Example:
 * ```kotlin
 * suspend fun addAccount(details: Map<String, String>): String {
 *     val response = window.fetch("$baseUrl/accounts", RequestInit(
 *         method = "POST",
 *         headers = json("Content-Type" to "application/json", "Authorization" to "Bearer $apiKey"),
 *         body = JSON.stringify(json("details" to details.toJsObject()))
 *     )).await()
 *     val result = response.json().await().unsafeCast<AccountResponse>()
 *     return result.accountId
 * }
 * ```
 *
 * ### WebSocket Events
 * Connect to WebSocket for real-time daemon events:
 * ```kotlin
 * val ws = WebSocket(wsUrl)
 * ws.onmessage = { event ->
 *     val data = JSON.parse<DaemonEvent>(event.data.toString())
 *     when (data.type) {
 *         "CALL_STATE_CHANGED" -> callbacks?.onCallStateChanged(...)
 *         "MESSAGE_RECEIVED" -> callbacks?.onMessageReceived(...)
 *         // ... handle all event types
 *     }
 * }
 * ```
 *
 * ### Media Handling
 * Audio/video is handled separately via WebRTC APIs:
 * - libjami (through REST bridge) handles signaling
 * - Browser WebRTC handles actual media capture/playback
 * - ICE candidates exchanged through conversation messages
 *
 * ## Current Status: Stub Implementation
 *
 * This is currently a stub that returns empty/default values.
 * Full implementation requires:
 * 1. REST Bridge server implementation (see docs/REST_BRIDGE_API.md)
 * 2. Ktor client or fetch API integration
 * 3. WebSocket event handling
 */
actual class DaemonBridge actual constructor() {
    private var isInitialized = false
    private var callbacks: DaemonCallbacks? = null

    // REST Bridge configuration
    // TODO: Make configurable via constructor or init()
    private val baseUrl = "http://localhost:8080/api/v1"
    private val wsUrl = "ws://localhost:8080/ws"
    @Suppress("unused")
    private var apiKey: String? = null

    // WebSocket connection for real-time events
    // TODO: Implement WebSocket handling
    // private var webSocket: WebSocket? = null

    // ==================== Lifecycle ====================

    actual fun init(callbacks: DaemonCallbacks): Boolean {
        this.callbacks = callbacks

        // TODO: Implement WebSocket connection
        // webSocket = WebSocket(wsUrl)
        // webSocket?.onopen = { console.log("Connected to REST Bridge") }
        // webSocket?.onmessage = { event -> handleWebSocketEvent(event) }
        // webSocket?.onclose = { scheduleReconnect() }
        // webSocket?.onerror = { console.error("WebSocket error") }

        // TODO: Call POST /daemon/init
        // val response = httpClient.post("$baseUrl/daemon/init") { ... }

        isInitialized = true
        return true
    }

    actual fun start(): Boolean {
        // TODO: Call POST /daemon/start
        return isInitialized
    }

    actual fun stop() {
        // TODO: Call POST /daemon/stop
        // webSocket?.close()
        isInitialized = false
    }

    actual fun isRunning(): Boolean {
        // TODO: Call GET /daemon/status
        return isInitialized
    }

    // ==================== Account Operations ====================

    actual fun addAccount(details: Map<String, String>): String {
        // TODO: POST /accounts with body: { "details": details }
        return ""
    }

    actual fun removeAccount(accountId: String) {
        // TODO: DELETE /accounts/{accountId}
    }

    actual fun getAccountDetails(accountId: String): Map<String, String> {
        // TODO: GET /accounts/{accountId} -> response.details
        return emptyMap()
    }

    actual fun setAccountDetails(accountId: String, details: Map<String, String>) {
        // TODO: PUT /accounts/{accountId} with body: { "details": details }
    }

    actual fun getAccountList(): List<String> {
        // TODO: GET /accounts -> response.accounts
        return emptyList()
    }

    actual fun setAccountActive(accountId: String, active: Boolean) {
        // TODO: PUT /accounts/{accountId} with body: { "details": { "Account.enable": active } }
    }

    actual fun getAccountTemplate(accountType: String): Map<String, String> {
        // TODO: GET /accounts/template/{accountType} -> response.template
        return emptyMap()
    }

    actual fun getVolatileAccountDetails(accountId: String): Map<String, String> {
        // TODO: GET /accounts/{accountId}/volatile -> response.details
        return emptyMap()
    }

    actual fun sendRegister(accountId: String, enable: Boolean) {
        // TODO: POST /accounts/{accountId}/register with body: { "enable": enable }
    }

    actual fun setAccountsOrder(order: String) {
        // TODO: PUT /accounts/order with body: { "order": order }
    }

    actual fun changeAccountPassword(accountId: String, oldPassword: String, newPassword: String): Boolean {
        // TODO: POST /accounts/{accountId}/password with body: { "oldPassword": ..., "newPassword": ... }
        return false
    }

    actual fun exportToFile(accountId: String, path: String, scheme: String, password: String): Boolean {
        // TODO: POST /accounts/{accountId}/export
        return false
    }

    // ==================== Credentials ====================

    actual fun getCredentials(accountId: String): List<Map<String, String>> {
        // TODO: GET /accounts/{accountId}/credentials
        return emptyList()
    }

    actual fun setCredentials(accountId: String, credentials: List<Map<String, String>>) {
        // TODO: PUT /accounts/{accountId}/credentials
    }

    // ==================== Device Management ====================

    actual fun getKnownRingDevices(accountId: String): Map<String, String> {
        // TODO: GET /accounts/{accountId}/devices
        return emptyMap()
    }

    actual fun revokeDevice(accountId: String, deviceId: String, scheme: String, password: String) {
        // TODO: DELETE /accounts/{accountId}/devices/{deviceId}
    }

    actual fun setDeviceName(accountId: String, deviceName: String) {
        // TODO: PUT /accounts/{accountId}/devices/current
    }

    // ==================== Profile ====================

    actual fun updateProfile(accountId: String, displayName: String, avatar: String, fileType: String, flag: Int) {
        // TODO: PUT /accounts/{accountId}/profile
    }

    // ==================== Call Operations ====================

    actual fun placeCall(accountId: String, uri: String, mediaList: List<MediaAttribute>): String {
        // TODO: POST /calls with body: { "accountId": ..., "uri": ..., "mediaList": [...] }
        // Returns: response.callId
        return ""
    }

    actual fun accept(accountId: String, callId: String, mediaList: List<MediaAttribute>) {
        // TODO: POST /calls/{callId}/accept
    }

    actual fun hangUp(accountId: String, callId: String) {
        // TODO: POST /calls/{callId}/hangup
    }

    actual fun hold(accountId: String, callId: String) {
        // TODO: POST /calls/{callId}/hold
    }

    actual fun unhold(accountId: String, callId: String) {
        // TODO: POST /calls/{callId}/unhold
    }

    actual fun muteLocalMedia(accountId: String, callId: String, mediaType: String, mute: Boolean) {
        // TODO: POST /calls/{callId}/mute
    }

    // ==================== Conversation Operations ====================

    actual fun getConversations(accountId: String): List<String> {
        // TODO: GET /accounts/{accountId}/conversations
        return emptyList()
    }

    actual fun startConversation(accountId: String): String {
        // TODO: POST /accounts/{accountId}/conversations
        return ""
    }

    actual fun sendMessage(accountId: String, conversationId: String, message: String, replyTo: String, flag: Int) {
        // TODO: POST /accounts/{accountId}/conversations/{conversationId}/messages
    }

    actual fun loadConversation(accountId: String, conversationId: String, fromMessage: String, size: Int) {
        // TODO: GET /accounts/{accountId}/conversations/{conversationId}/messages?from=...&size=...
        // Event CONVERSATION_LOADED will be received via WebSocket
    }

    actual fun getConversationMembers(accountId: String, conversationId: String): List<Map<String, String>> {
        // TODO: GET /accounts/{accountId}/conversations/{conversationId}/members
        return emptyList()
    }

    actual fun getConversationInfo(accountId: String, conversationId: String): Map<String, String> {
        // TODO: GET /accounts/{accountId}/conversations/{conversationId}
        return emptyMap()
    }

    actual fun removeConversation(accountId: String, conversationId: String) {
        // TODO: DELETE /accounts/{accountId}/conversations/{conversationId}
    }

    actual fun addConversationMember(accountId: String, conversationId: String, uri: String) {
        // TODO: POST /accounts/{accountId}/conversations/{conversationId}/members
    }

    actual fun removeConversationMember(accountId: String, conversationId: String, uri: String) {
        // TODO: DELETE /accounts/{accountId}/conversations/{conversationId}/members/{uri}
    }

    actual fun updateConversationInfo(accountId: String, conversationId: String, info: Map<String, String>) {
        // TODO: PUT /accounts/{accountId}/conversations/{conversationId}
    }

    actual fun getConversationPreferences(accountId: String, conversationId: String): Map<String, String> {
        // TODO: GET /accounts/{accountId}/conversations/{conversationId}/preferences
        return emptyMap()
    }

    actual fun setConversationPreferences(accountId: String, conversationId: String, prefs: Map<String, String>) {
        // TODO: PUT /accounts/{accountId}/conversations/{conversationId}/preferences
    }

    actual fun setMessageDisplayed(accountId: String, conversationUri: String, messageId: String, status: Int) {
        // TODO: PUT /accounts/{accountId}/conversations/{conversationUri}/messages/{messageId}/displayed
    }

    actual fun getActiveCalls(accountId: String, conversationId: String): List<Map<String, String>> {
        // TODO: GET /accounts/{accountId}/conversations/{conversationId}/calls
        return emptyList()
    }

    // ==================== Conversation Requests ====================

    actual fun getConversationRequests(accountId: String): List<Map<String, String>> {
        // TODO: GET /accounts/{accountId}/conversations/requests
        return emptyList()
    }

    actual fun acceptConversationRequest(accountId: String, conversationId: String) {
        // TODO: POST /accounts/{accountId}/conversations/requests/{conversationId}/accept
    }

    actual fun declineConversationRequest(accountId: String, conversationId: String) {
        // TODO: POST /accounts/{accountId}/conversations/requests/{conversationId}/decline
    }

    // ==================== Trust Requests ====================

    actual fun getTrustRequests(accountId: String): List<Map<String, String>> {
        // TODO: GET /accounts/{accountId}/trust-requests
        return emptyList()
    }

    actual fun acceptTrustRequest(accountId: String, uri: String) {
        // TODO: POST /accounts/{accountId}/trust-requests/{uri}/accept
    }

    actual fun discardTrustRequest(accountId: String, uri: String) {
        // TODO: DELETE /accounts/{accountId}/trust-requests/{uri}
    }

    actual fun sendTrustRequest(accountId: String, uri: String, payload: ByteArray) {
        // TODO: POST /accounts/{accountId}/trust-requests
    }

    // ==================== Contact Operations ====================

    actual fun addContact(accountId: String, uri: String) {
        // TODO: POST /accounts/{accountId}/contacts
    }

    actual fun removeContact(accountId: String, uri: String, ban: Boolean) {
        // TODO: DELETE /accounts/{accountId}/contacts/{uri}?ban={ban}
    }

    actual fun getContacts(accountId: String): List<Map<String, String>> {
        // TODO: GET /accounts/{accountId}/contacts
        return emptyList()
    }

    actual fun getContactDetails(accountId: String, uri: String): Map<String, String> {
        // TODO: GET /accounts/{accountId}/contacts/{uri}
        return emptyMap()
    }

    actual fun subscribeBuddy(accountId: String, uri: String, subscribe: Boolean) {
        // TODO: PUT /accounts/{accountId}/contacts/{uri}/presence
    }

    // ==================== Name Lookup ====================

    actual fun lookupName(accountId: String, nameServiceUrl: String, name: String): Boolean {
        // TODO: GET /accounts/{accountId}/ns/name/{name}
        // Event REGISTERED_NAME_FOUND will be received via WebSocket
        return false
    }

    actual fun lookupAddress(accountId: String, nameServiceUrl: String, address: String): Boolean {
        // TODO: GET /accounts/{accountId}/ns/address/{address}
        // Event REGISTERED_NAME_FOUND will be received via WebSocket
        return false
    }

    actual fun registerName(accountId: String, name: String, scheme: String, password: String): Boolean {
        // TODO: POST /accounts/{accountId}/ns/register
        // Event NAME_REGISTRATION_ENDED will be received via WebSocket
        return false
    }

    actual fun searchUser(accountId: String, query: String): Boolean {
        // TODO: GET /accounts/{accountId}/ns/search?query={query}
        return false
    }

    // ==================== Messaging ====================

    actual fun sendTextMessage(accountId: String, callIdOrUri: String, message: String) {
        // TODO: POST /accounts/{accountId}/messages (for in-call messaging)
    }

    actual fun setIsComposing(accountId: String, uri: String, isComposing: Boolean) {
        // TODO: PUT /accounts/{accountId}/conversations/{uri}/composing
    }

    actual fun cancelMessage(accountId: String, messageId: Long): Boolean {
        // TODO: DELETE /accounts/{accountId}/messages/{messageId}
        return false
    }

    // ==================== File Transfer ====================

    actual fun sendFile(accountId: String, conversationId: String, filePath: String, displayName: String, parent: String) {
        // TODO: POST /accounts/{accountId}/conversations/{conversationId}/files (multipart)
        // Note: Web needs to use File API, not file paths
    }

    actual fun downloadFile(accountId: String, conversationId: String, interactionId: String, fileId: String, path: String) {
        // TODO: GET /accounts/{accountId}/conversations/{conversationId}/files/{fileId}
        // Returns file content for browser download
    }

    actual fun cancelDataTransfer(accountId: String, conversationId: String, fileId: String) {
        // TODO: DELETE /accounts/{accountId}/conversations/{conversationId}/files/{fileId}
    }

    actual fun fileTransferInfo(accountId: String, conversationId: String, fileId: String): FileTransferInfo? {
        // TODO: GET /accounts/{accountId}/conversations/{conversationId}/files/{fileId}/info
        return null
    }

    // ==================== Codec Operations ====================

    actual fun getCodecList(): List<Long> {
        // TODO: GET /codecs
        return emptyList()
    }

    actual fun getActiveCodecList(accountId: String): List<Long> {
        // TODO: GET /accounts/{accountId}/codecs
        return emptyList()
    }

    actual fun setActiveCodecList(accountId: String, codecList: List<Long>) {
        // TODO: PUT /accounts/{accountId}/codecs
    }

    actual fun getCodecDetails(accountId: String, codecId: Long): Map<String, String> {
        // TODO: GET /accounts/{accountId}/codecs/{codecId}
        return emptyMap()
    }

    // ==================== Push Notifications ====================

    actual fun setPushNotificationToken(token: String) {
        // Note: Push notifications work differently in browsers (Web Push API)
        // TODO: POST /push/register
    }

    actual fun setPushNotificationConfig(config: Map<String, String>) {
        // TODO: PUT /push/config
    }

    actual fun pushNotificationReceived(from: String, data: Map<String, String>) {
        // TODO: POST /push/received
    }

    // ==================== WebSocket Event Handling ====================

    /**
     * Handle incoming WebSocket events and dispatch to callbacks.
     *
     * Example implementation:
     * ```kotlin
     * private fun handleWebSocketEvent(event: MessageEvent) {
     *     val data = JSON.parse<JsAny>(event.data.toString())
     *     val type = data["type"].toString()
     *     val payload = data["payload"]
     *
     *     when (type) {
     *         "ACCOUNTS_CHANGED" -> callbacks?.onAccountsChanged()
     *         "REGISTRATION_STATE_CHANGED" -> {
     *             callbacks?.onRegistrationStateChanged(
     *                 payload["accountId"].toString(),
     *                 payload["state"].toString(),
     *                 payload["code"].toString().toInt(),
     *                 payload["details"].toString()
     *             )
     *         }
     *         "CALL_STATE_CHANGED" -> {
     *             callbacks?.onCallStateChanged(
     *                 payload["accountId"].toString(),
     *                 payload["callId"].toString(),
     *                 payload["state"].toString(),
     *                 payload["code"].toString().toInt()
     *             )
     *         }
     *         "INCOMING_CALL" -> {
     *             // ... parse media list and call onIncomingCall
     *         }
     *         "MESSAGE_RECEIVED" -> {
     *             // ... parse SwarmMessage and call onMessageReceived
     *         }
     *         // ... handle all event types from REST_BRIDGE_API.md
     *     }
     * }
     * ```
     */
    @Suppress("unused")
    private fun handleWebSocketEvent(@Suppress("UNUSED_PARAMETER") eventData: String) {
        // TODO: Implement event parsing and callback dispatch
    }
}
