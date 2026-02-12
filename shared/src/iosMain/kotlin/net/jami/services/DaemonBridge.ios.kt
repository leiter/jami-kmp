package net.jami.services

import net.jami.model.MediaAttribute
import net.jami.model.SwarmMessage

/**
 * iOS implementation of DaemonBridge using Kotlin/Native cinterop.
 *
 * This implementation uses cinterop to call libjami C functions directly.
 * The bindings are generated from the .def file at:
 * shared/src/nativeInterop/cinterop/libjami.def
 *
 * Headers referenced from:
 * - jami-daemon/src/jami/jami.h
 * - jami-daemon/src/jami/callmanager_interface.h
 * - jami-daemon/src/jami/configurationmanager_interface.h
 * - etc.
 *
 * TODO: Implement cinterop callbacks using staticCFunction and StableRef
 */
actual class DaemonBridge actual constructor() {
    private var isInitialized = false
    private var callbacks: DaemonCallbacks? = null

    actual fun init(callbacks: DaemonCallbacks): Boolean {
        this.callbacks = callbacks

        // TODO: Uncomment when cinterop is properly configured
        // import libjami.*
        // val result = jami_init(InitFlag.NONE.value)
        // Register callbacks using staticCFunction

        isInitialized = true
        return true
    }

    actual fun start(): Boolean {
        // TODO: libjami.jami_start()
        return isInitialized
    }

    actual fun stop() {
        // TODO: libjami.jami_fini()
        isInitialized = false
    }

    actual fun isRunning(): Boolean = isInitialized

    actual fun addAccount(details: Map<String, String>): String {
        // TODO: Convert to C map and call libjami.addAccount(...)
        return ""
    }

    actual fun removeAccount(accountId: String) {
        // TODO: libjami.removeAccount(accountId.cstr)
    }

    actual fun getAccountDetails(accountId: String): Map<String, String> {
        // TODO: libjami.getAccountDetails(...) and convert result
        return emptyMap()
    }

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

    actual fun sendTextMessage(accountId: String, callIdOrUri: String, message: String) {}
    actual fun setIsComposing(accountId: String, uri: String, isComposing: Boolean) {}
    actual fun cancelMessage(accountId: String, messageId: Long): Boolean = false
}
