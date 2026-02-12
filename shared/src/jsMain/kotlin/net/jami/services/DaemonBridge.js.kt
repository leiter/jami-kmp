package net.jami.services

import net.jami.model.MediaAttribute
import net.jami.model.SwarmMessage

/**
 * Web (Kotlin/JS) implementation of DaemonBridge.
 *
 * Uses REST API and WebSocket connection to communicate with a daemon server.
 * The daemon runs as a separate process and exposes its API over HTTP/WS.
 *
 * This approach is necessary because:
 * 1. JavaScript cannot directly load native libraries
 * 2. WebRTC handles the actual media, while the daemon manages signaling
 *
 * Alternative: WebAssembly compilation of libjami (future)
 */
actual class DaemonBridge actual constructor() {
    private var isInitialized = false
    private var callbacks: DaemonCallbacks? = null
    // TODO: private val httpClient = HttpClient(Js)
    // TODO: private var wsSession: WebSocketSession? = null

    private val baseUrl = "http://localhost:8080/api"
    private val wsUrl = "ws://localhost:8080/events"

    actual fun init(callbacks: DaemonCallbacks): Boolean {
        this.callbacks = callbacks

        // TODO: Connect WebSocket for real-time events
        // scope.launch {
        //     wsSession = httpClient.webSocketSession(wsUrl)
        //     wsSession?.incoming?.collect { frame ->
        //         handleEvent(frame)
        //     }
        // }

        isInitialized = true
        return true
    }

    actual fun start(): Boolean = isInitialized
    actual fun stop() {
        // TODO: wsSession?.close()
        isInitialized = false
    }
    actual fun isRunning(): Boolean = isInitialized

    // REST API calls (TODO: implement with Ktor client)
    actual fun addAccount(details: Map<String, String>): String {
        // POST /api/accounts { details }
        return ""
    }

    actual fun removeAccount(accountId: String) {
        // DELETE /api/accounts/{accountId}
    }

    actual fun getAccountDetails(accountId: String): Map<String, String> {
        // GET /api/accounts/{accountId}
        return emptyMap()
    }

    actual fun setAccountDetails(accountId: String, details: Map<String, String>) {
        // PUT /api/accounts/{accountId}
    }

    actual fun getAccountList(): List<String> {
        // GET /api/accounts
        return emptyList()
    }

    actual fun setAccountActive(accountId: String, active: Boolean) {
        // PUT /api/accounts/{accountId}/active
    }

    actual fun placeCall(accountId: String, uri: String, mediaList: List<MediaAttribute>): String {
        // POST /api/calls
        return ""
    }

    actual fun accept(accountId: String, callId: String, mediaList: List<MediaAttribute>) {
        // POST /api/calls/{callId}/accept
    }

    actual fun hangUp(accountId: String, callId: String) {
        // POST /api/calls/{callId}/hangup
    }

    actual fun hold(accountId: String, callId: String) {}
    actual fun unhold(accountId: String, callId: String) {}
    actual fun muteLocalMedia(accountId: String, callId: String, mediaType: String, mute: Boolean) {}

    actual fun getConversations(accountId: String): List<String> = emptyList()
    actual fun startConversation(accountId: String): String = ""
    actual fun sendMessage(accountId: String, conversationId: String, message: String, replyTo: String, flag: Int) {
        // POST /api/conversations/{conversationId}/messages
    }
    actual fun loadConversation(accountId: String, conversationId: String, fromMessage: String, size: Int) {}
    actual fun getConversationMembers(accountId: String, conversationId: String): List<Map<String, String>> = emptyList()

    actual fun addContact(accountId: String, uri: String) {}
    actual fun removeContact(accountId: String, uri: String, ban: Boolean) {}
    actual fun getContacts(accountId: String): List<Map<String, String>> = emptyList()

    actual fun lookupName(accountId: String, nameServiceUrl: String, name: String): Boolean = false
    actual fun lookupAddress(accountId: String, nameServiceUrl: String, address: String): Boolean = false
    actual fun registerName(accountId: String, name: String, scheme: String, password: String): Boolean = false
}
