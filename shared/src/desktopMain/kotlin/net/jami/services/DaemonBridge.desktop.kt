package net.jami.services

import net.jami.model.MediaAttribute
import net.jami.model.SwarmMessage

/**
 * Desktop (JVM) implementation of DaemonBridge.
 *
 * Uses the same SWIG-generated JNI bindings as Android.
 * The JamiService class is generated from jami-daemon/bin/jni/jni_interface.i
 *
 * Note: Requires building libjami for the desktop platform and loading
 * the native library via System.loadLibrary("jami").
 */
actual class DaemonBridge actual constructor() {
    private var isInitialized = false
    private var callbacks: DaemonCallbacks? = null

    init {
        // TODO: Load native library
        // System.loadLibrary("jami")
    }

    actual fun init(callbacks: DaemonCallbacks): Boolean {
        this.callbacks = callbacks
        // TODO: Initialize SWIG bindings (same as Android)
        isInitialized = true
        return true
    }

    actual fun start(): Boolean {
        return isInitialized
    }

    actual fun stop() {
        isInitialized = false
    }

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
