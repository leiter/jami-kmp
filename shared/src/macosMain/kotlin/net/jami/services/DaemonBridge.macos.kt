package net.jami.services

import net.jami.model.MediaAttribute
import net.jami.model.SwarmMessage

/**
 * macOS implementation of DaemonBridge using Kotlin/Native cinterop.
 *
 * Same approach as iOS - uses cinterop to call libjami C functions.
 * Shares the same .def file and patterns with iosMain.
 */
actual class DaemonBridge actual constructor() {
    private var isInitialized = false
    private var callbacks: DaemonCallbacks? = null

    actual fun init(callbacks: DaemonCallbacks): Boolean {
        this.callbacks = callbacks
        // TODO: Same cinterop implementation as iOS
        isInitialized = true
        return true
    }

    actual fun start(): Boolean = isInitialized
    actual fun stop() { isInitialized = false }
    actual fun isRunning(): Boolean = isInitialized

    actual fun addAccount(details: Map<String, String>): String = ""
    actual fun removeAccount(accountId: String) {}
    actual fun getAccountDetails(accountId: String): Map<String, String> = emptyMap()
    actual fun setAccountDetails(accountId: String, details: Map<String, String>) {}
    actual fun getAccountList(): List<String> = emptyList()
    actual fun setAccountActive(accountId: String, active: Boolean) {}

    actual fun placeCall(accountId: String, uri: String, mediaList: List<MediaAttribute>): String = ""
    actual fun accept(accountId: String, callId: String, mediaList: List<MediaAttribute>) {}
    actual fun hangUp(accountId: String, callId: String) {}
    actual fun hold(accountId: String, callId: String) {}
    actual fun unhold(accountId: String, callId: String) {}
    actual fun muteLocalMedia(accountId: String, callId: String, mediaType: String, mute: Boolean) {}

    actual fun getConversations(accountId: String): List<String> = emptyList()
    actual fun startConversation(accountId: String): String = ""
    actual fun sendMessage(accountId: String, conversationId: String, message: String, replyTo: String, flag: Int) {}
    actual fun loadConversation(accountId: String, conversationId: String, fromMessage: String, size: Int) {}
    actual fun getConversationMembers(accountId: String, conversationId: String): List<Map<String, String>> = emptyList()

    actual fun addContact(accountId: String, uri: String) {}
    actual fun removeContact(accountId: String, uri: String, ban: Boolean) {}
    actual fun getContacts(accountId: String): List<Map<String, String>> = emptyList()

    actual fun lookupName(accountId: String, nameServiceUrl: String, name: String): Boolean = false
    actual fun lookupAddress(accountId: String, nameServiceUrl: String, address: String): Boolean = false
    actual fun registerName(accountId: String, name: String, scheme: String, password: String): Boolean = false
}
