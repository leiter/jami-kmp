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
import net.jami.model.SwarmMessage
import net.jami.utils.Log

/**
 * Desktop (JVM) implementation of DaemonBridge.
 *
 * Uses the same SWIG-generated JNI bindings as Android.
 * The JamiService class is generated from jami-daemon/bin/jni/jni_interface.i
 *
 * ## Integration Requirements
 *
 * 1. Build libjami for desktop platform (Linux, Windows, or macOS Intel)
 * 2. Generate SWIG bindings by running make-swig.sh
 * 3. Include the generated Java files from net.jami.daemon package
 * 4. Load the native library via System.loadLibrary("jami")
 *
 * Note: The JNI implementation is shared with Android, using the same
 * SWIG-generated classes and native library API.
 */
actual class DaemonBridge actual constructor() {
    private var isInitialized = false
    private var callbacks: DaemonCallbacks? = null

    // Callback implementations - must keep references to prevent GC
    private var configurationCallback: Any? = null
    private var callCallback: Any? = null
    private var presenceCallback: Any? = null
    private var videoCallback: Any? = null
    private var dataTransferCallback: Any? = null
    private var conversationCallback: Any? = null

    companion object {
        private const val TAG = "DaemonBridge"

        // Load native library for desktop
        init {
            try {
                // Try to load from java.library.path
                System.loadLibrary("jami")
                Log.i(TAG, "Native library 'jami' loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "Failed to load native library 'jami' - running in stub mode", e)
            }
        }
    }

    actual fun init(callbacks: DaemonCallbacks): Boolean {
        this.callbacks = callbacks

        try {
            // Desktop uses the same callback structure as Android
            // See DaemonBridge.android.kt for full callback implementation

            /* TODO: Uncomment when SWIG bindings are integrated
            configurationCallback = object : ConfigurationCallback() {
                override fun accountsChanged() {
                    callbacks.onAccountsChanged()
                }
                override fun registrationStateChanged(accountId: String, state: String, code: Int, detail: String) {
                    callbacks.onRegistrationStateChanged(accountId, state, code, detail)
                }
                // ... same callbacks as Android

                // Desktop-specific system info
                override fun getAppDataPath(name: String, ret: StringVect) {
                    val userHome = System.getProperty("user.home")
                    ret.add("$userHome/.local/share/jami")
                }

                override fun getDeviceName(ret: StringVect) {
                    try {
                        ret.add(java.net.InetAddress.getLocalHost().hostName)
                    } catch (e: Exception) {
                        ret.add("Desktop")
                    }
                }
            }

            // ... create other callbacks same as Android

            JamiService.init(
                configurationCallback as ConfigurationCallback,
                callCallback as Callback,
                presenceCallback as PresenceCallback,
                dataTransferCallback as DataTransferCallback,
                videoCallback as VideoCallback,
                conversationCallback as ConversationCallback
            )
            */

            isInitialized = true
            Log.i(TAG, "DaemonBridge initialized (desktop)")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize daemon", e)
            return false
        }
    }

    actual fun start(): Boolean {
        Log.i(TAG, "Daemon started (desktop)")
        return isInitialized
    }

    actual fun stop() {
        if (isInitialized) {
            // TODO: JamiService.fini()
            isInitialized = false
            Log.i(TAG, "Daemon stopped (desktop)")
        }
    }

    actual fun isRunning(): Boolean = isInitialized

    // ==================== Account Operations ====================

    actual fun addAccount(details: Map<String, String>): String {
        // TODO: return JamiService.addAccount(StringMap.toSwig(details))
        return ""
    }

    actual fun removeAccount(accountId: String) {
        // TODO: JamiService.removeAccount(accountId)
    }

    actual fun getAccountDetails(accountId: String): Map<String, String> {
        // TODO: return JamiService.getAccountDetails(accountId).toNative()
        return emptyMap()
    }

    actual fun setAccountDetails(accountId: String, details: Map<String, String>) {
        // TODO: JamiService.setAccountDetails(accountId, StringMap.toSwig(details))
    }

    actual fun getAccountList(): List<String> {
        // TODO: return JamiService.getAccountList().toList()
        return emptyList()
    }

    actual fun setAccountActive(accountId: String, active: Boolean) {
        // TODO: JamiService.setAccountActive(accountId, active)
    }

    // ==================== Call Operations ====================

    actual fun placeCall(accountId: String, uri: String, mediaList: List<MediaAttribute>): String {
        // TODO: return JamiService.placeCallWithMedia(accountId, uri, mediaList.toSwigVectMap())
        return ""
    }

    actual fun accept(accountId: String, callId: String, mediaList: List<MediaAttribute>) {
        // TODO: JamiService.acceptWithMedia(accountId, callId, mediaList.toSwigVectMap())
    }

    actual fun hangUp(accountId: String, callId: String) {
        // TODO: JamiService.hangUp(accountId, callId)
    }

    actual fun hold(accountId: String, callId: String) {
        // TODO: JamiService.hold(accountId, callId)
    }

    actual fun unhold(accountId: String, callId: String) {
        // TODO: JamiService.unhold(accountId, callId)
    }

    actual fun muteLocalMedia(accountId: String, callId: String, mediaType: String, mute: Boolean) {
        // TODO: JamiService.muteLocalMedia(accountId, callId, mediaType, mute)
    }

    // ==================== Conversation Operations ====================

    actual fun getConversations(accountId: String): List<String> {
        // TODO: return JamiService.getConversations(accountId).toList()
        return emptyList()
    }

    actual fun startConversation(accountId: String): String {
        // TODO: return JamiService.startConversation(accountId)
        return ""
    }

    actual fun sendMessage(accountId: String, conversationId: String, message: String, replyTo: String, flag: Int) {
        // TODO: JamiService.sendMessage(accountId, conversationId, message, replyTo, flag)
    }

    actual fun loadConversation(accountId: String, conversationId: String, fromMessage: String, size: Int) {
        // TODO: JamiService.loadConversation(accountId, conversationId, fromMessage, size)
    }

    actual fun getConversationMembers(accountId: String, conversationId: String): List<Map<String, String>> {
        // TODO: return JamiService.getConversationMembers(accountId, conversationId).toNative()
        return emptyList()
    }

    // ==================== Contact Operations ====================

    actual fun addContact(accountId: String, uri: String) {
        // TODO: JamiService.addContact(accountId, uri)
    }

    actual fun removeContact(accountId: String, uri: String, ban: Boolean) {
        // TODO: JamiService.removeContact(accountId, uri, ban)
    }

    actual fun getContacts(accountId: String): List<Map<String, String>> {
        // TODO: return JamiService.getContacts(accountId).toNative()
        return emptyList()
    }

    // ==================== Name Lookup ====================

    actual fun lookupName(accountId: String, nameServiceUrl: String, name: String): Boolean {
        // TODO: return JamiService.lookupName(accountId, nameServiceUrl, name)
        return false
    }

    actual fun lookupAddress(accountId: String, nameServiceUrl: String, address: String): Boolean {
        // TODO: return JamiService.lookupAddress(accountId, nameServiceUrl, address)
        return false
    }

    actual fun registerName(accountId: String, name: String, scheme: String, password: String): Boolean {
        // TODO: return JamiService.registerName(accountId, name, scheme, password)
        return false
    }

    // ==================== Messaging ====================

    actual fun sendTextMessage(accountId: String, callIdOrUri: String, message: String) {
        // TODO: JamiService.sendAccountTextMessage(accountId, callIdOrUri, StringMap.toSwig(mapOf("text/plain" to message)))
    }

    actual fun setIsComposing(accountId: String, uri: String, isComposing: Boolean) {
        // TODO: JamiService.setIsComposing(accountId, uri, isComposing)
    }

    actual fun cancelMessage(accountId: String, messageId: Long): Boolean {
        // TODO: return JamiService.cancelMessage(accountId, messageId)
        return false
    }
}
