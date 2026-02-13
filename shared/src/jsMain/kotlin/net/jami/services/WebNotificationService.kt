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

import net.jami.model.*
import net.jami.utils.Log

// Extension functions for model properties
private fun Call.getDisplayName(): String =
    contact?.displayName ?: peerUri.uri

private fun Call.getDaemonIdString(): String =
    daemonId ?: ""

private fun Conference.getDisplayName(): String =
    firstCall?.getDisplayName() ?: id

private fun Conversation.getDisplayName(): String =
    contact?.displayName ?: profileFlow.value.displayName?.takeIf { it.isNotEmpty() } ?: uri.uri

private fun Conversation.getLastMessage(): String? =
    lastEventFlow.value?.body

/**
 * External declarations for Web Notifications API.
 */
@JsName("Notification")
external class JsNotification(title: String, options: dynamic = definedExternally) {
    companion object {
        val permission: String
        fun requestPermission(): kotlin.js.Promise<String>
    }

    var onclick: ((dynamic) -> Unit)?
    fun close()
}

private const val PERMISSION_GRANTED = "granted"
private const val PERMISSION_DENIED = "denied"
private const val PERMISSION_DEFAULT = "default"

/**
 * Web (Kotlin/JS) implementation of NotificationService.
 *
 * Uses the Web Notifications API for browser notifications.
 * Requires user permission which should be requested at app startup.
 *
 * ## Permission Request
 * ```kotlin
 * Notification.requestPermission().then { permission ->
 *     if (permission == NotificationPermission.GRANTED) {
 *         // Notifications enabled
 *     }
 * }
 * ```
 *
 * ## Limitations
 * - Requires HTTPS or localhost
 * - User must grant permission
 * - Some browsers block notifications from background tabs
 * - No actions support in all browsers
 */
class WebNotificationService : NotificationService {

    // Track active notifications
    private val activeNotifications = mutableMapOf<String, JsNotification>()
    private var currentCallNotificationId: String? = null
    private var pendingScreenshareCallback: (() -> Unit)? = null
    private var pendingScreenshareConfId: String? = null

    private val isSupported: Boolean
        get() = js("typeof Notification !== 'undefined'") as Boolean

    private val hasPermission: Boolean
        get() = isSupported && JsNotification.permission == PERMISSION_GRANTED

    private fun showNotification(
        id: String,
        title: String,
        body: String,
        tag: String? = null,
        requireInteraction: Boolean = false,
        onClick: (() -> Unit)? = null
    ): JsNotification? {
        if (!hasPermission) {
            Log.w(TAG, "Notification permission not granted, logging: $title - $body")
            return null
        }

        try {
            // Close existing notification with same ID
            activeNotifications[id]?.close()

            val options = js("{}")
            options.body = body
            options.tag = tag ?: id
            options.requireInteraction = requireInteraction
            options.icon = "/icons/jami-icon.png" // App should provide this

            val notification = JsNotification(title, options)

            notification.onclick = {
                onClick?.invoke()
                notification.close()
            }

            activeNotifications[id] = notification
            Log.d(TAG, "Showing notification: $id")
            return notification
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to show notification: ${e.message}")
            return null
        }
    }

    private fun closeNotification(id: String) {
        activeNotifications.remove(id)?.close()
        Log.d(TAG, "Closed notification: $id")
    }

    // ==================== Call Notifications ====================

    override fun showCallNotification(notifId: Int): Any? {
        val id = "call_$notifId"
        currentCallNotificationId = id

        return showNotification(
            id = id,
            title = "Jami Call",
            body = "Ongoing call",
            tag = "call",
            requireInteraction = true
        )
    }

    override fun cancelCallNotification() {
        currentCallNotificationId?.let { closeNotification(it) }
        currentCallNotificationId = null
    }

    override fun removeCallNotification() {
        cancelCallNotification()
    }

    override suspend fun handleCallNotification(
        conference: Conference,
        remove: Boolean,
        startScreenshare: Boolean
    ) {
        if (remove) {
            cancelCallNotification()
            return
        }

        if (startScreenshare) {
            startPendingScreenshare(conference.id)
            return
        }

        val id = "call_${conference.id}"
        currentCallNotificationId = id

        val state = conference.state
        val title = when (state) {
            Call.CallStatus.RINGING -> "Incoming Call"
            Call.CallStatus.CURRENT, Call.CallStatus.HOLD -> "Ongoing Call"
            else -> "Call"
        }

        showNotification(
            id = id,
            title = title,
            body = conference.getDisplayName(),
            tag = "call",
            requireInteraction = state == Call.CallStatus.RINGING
        )

        Log.d(TAG, "Showing call notification for conference: ${conference.id}")
    }

    override fun preparePendingScreenshare(conference: Conference, callback: () -> Unit) {
        pendingScreenshareConfId = conference.id
        pendingScreenshareCallback = callback
        Log.d(TAG, "Prepared pending screenshare for conference: ${conference.id}")
    }

    override fun startPendingScreenshare(confId: String) {
        if (pendingScreenshareConfId == confId) {
            pendingScreenshareCallback?.invoke()
            pendingScreenshareCallback = null
            pendingScreenshareConfId = null
            Log.d(TAG, "Started pending screenshare for conference: $confId")
        }
    }

    override fun showMissedCallNotification(call: Call) {
        val id = "missed_${call.getDaemonIdString()}"

        showNotification(
            id = id,
            title = "Missed Call",
            body = "From ${call.getDisplayName()}",
            tag = "missed_call"
        )

        Log.d(TAG, "Showing missed call notification: $id")
    }

    override fun showGroupCallNotification(conversation: Conversation, remove: Boolean) {
        val id = "group_call_${conversation.uri.uri}"

        if (remove) {
            closeNotification(id)
            return
        }

        showNotification(
            id = id,
            title = "Group Call",
            body = "Ongoing group call in ${conversation.getDisplayName()}",
            tag = "group_call",
            requireInteraction = true
        )

        Log.d(TAG, "Showing group call notification: $id")
    }

    // ==================== Text Notifications ====================

    override fun showTextNotification(conversation: Conversation) {
        val conversationKey = "${conversation.accountId}_${conversation.uri.uri}"
        val id = "message_$conversationKey"

        val lastMessage = conversation.getLastMessage() ?: return
        val senderName = conversation.getDisplayName()

        showNotification(
            id = id,
            title = senderName,
            body = lastMessage,
            tag = "message"
        )

        Log.d(TAG, "Showing text notification for conversation: $conversationKey")
    }

    override fun cancelTextNotification(accountId: String, contact: Uri) {
        val conversationKey = "${accountId}_${contact.uri}"
        val id = "message_$conversationKey"
        closeNotification(id)
    }

    override fun cancelAll() {
        activeNotifications.values.forEach { it.close() }
        activeNotifications.clear()
        currentCallNotificationId = null
        Log.d(TAG, "Cancelled all notifications")
    }

    // ==================== Trust Request Notifications ====================

    override fun showIncomingTrustRequestNotification(account: Account) {
        val id = "request_${account.accountId}"

        showNotification(
            id = id,
            title = "New Contact Request",
            body = "You have a new contact request",
            tag = "contact_request"
        )

        Log.d(TAG, "Showing trust request notification for account: ${account.accountId}")
    }

    override fun cancelTrustRequestNotification(accountId: String) {
        val id = "request_$accountId"
        closeNotification(id)
    }

    // ==================== File Transfer Notifications ====================

    override fun showFileTransferNotification(conversation: Conversation, info: DataTransfer) {
        val id = "transfer_${info.fileId ?: info.id}"

        val progress = if (info.totalSize > 0) {
            ((info.bytesProgress * 100) / info.totalSize).toInt()
        } else {
            0
        }

        showNotification(
            id = id,
            title = info.displayName,
            body = "Transfer progress: $progress%",
            tag = "file_transfer"
        )

        Log.d(TAG, "Showing file transfer notification: ${info.fileId}, progress: $progress%")
    }

    override fun handleDataTransferNotification(
        transfer: DataTransfer,
        conversation: Conversation,
        remove: Boolean
    ) {
        val fileId = transfer.fileId ?: transfer.id.toString()

        if (remove) {
            removeTransferNotification(conversation.accountId, conversation.uri, fileId)
            return
        }

        showFileTransferNotification(conversation, transfer)
    }

    override fun removeTransferNotification(
        accountId: String,
        conversationUri: Uri,
        fileId: String
    ) {
        val id = "transfer_$fileId"
        closeNotification(id)
    }

    override fun getDataTransferNotification(notificationId: Int): Any? {
        return activeNotifications["transfer_$notificationId"] as Any?
    }

    override fun cancelFileNotification(notificationId: Int) {
        val id = "transfer_$notificationId"
        closeNotification(id)
    }

    // ==================== Location Notifications ====================

    override fun showLocationNotification(
        account: Account,
        contact: Contact,
        conversation: Conversation
    ) {
        val id = "location_${contact.uri.uri}"

        showNotification(
            id = id,
            title = "Location Sharing",
            body = "Sharing location with ${contact.displayName}",
            tag = "location"
        )

        Log.d(TAG, "Showing location notification for contact: ${contact.uri.uri}")
    }

    override fun cancelLocationNotification(account: Account, contact: Contact) {
        val id = "location_${contact.uri.uri}"
        closeNotification(id)
    }

    // ==================== Service Notification ====================

    override val serviceNotification: Any
        get() = Unit // Web doesn't have service notifications

    override fun onConnectionUpdate(connected: Boolean) {
        // Could update favicon or page title
        if (js("typeof document !== 'undefined'") as Boolean) {
            val title = if (connected) "Jami" else "Jami (Connecting...)"
            js("document.title = title")
        }
        Log.d(TAG, "Connection status updated: connected=$connected")
    }

    // ==================== Push Notifications ====================

    override fun processPush() {
        Log.d(TAG, "Processing push notification")
        // Push notifications are handled by Service Worker in web
    }

    override fun testPushNotification(accountId: String) {
        val id = "test_push"

        showNotification(
            id = id,
            title = "Push Test",
            body = "Push notification working for account: $accountId",
            tag = "test"
        )

        Log.d(TAG, "Test push notification shown for account: $accountId")
    }

    companion object {
        private const val TAG = "WebNotificationService"

        /**
         * Request notification permission from user.
         * Should be called on user interaction (e.g., button click).
         *
         * @param callback Called with true if permission granted
         */
        fun requestPermission(callback: (Boolean) -> Unit) {
            if (js("typeof Notification === 'undefined'") as Boolean) {
                callback(false)
                return
            }

            JsNotification.requestPermission().then { permission ->
                callback(permission == PERMISSION_GRANTED)
            }
        }

        /**
         * Check if notifications are supported and permitted.
         */
        fun checkPermission(): String {
            if (js("typeof Notification === 'undefined'") as Boolean) {
                return "unsupported"
            }
            return JsNotification.permission
        }
    }
}
