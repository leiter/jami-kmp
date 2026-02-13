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
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.Image
import java.awt.Toolkit
import java.awt.TrayIcon.MessageType
import javax.swing.SwingUtilities

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
 * Desktop (JVM) implementation of NotificationService.
 *
 * Uses Java AWT SystemTray for desktop notifications.
 * Falls back to console logging if SystemTray is not supported.
 *
 * ## Platform Support
 * - Linux: Uses native notification daemon (libnotify)
 * - Windows: Uses system tray balloon notifications
 * - macOS (JVM): Uses Notification Center via SystemTray
 *
 * ## Note
 * For better native integration, consider using:
 * - Linux: DBus notifications via jna
 * - Windows: Windows.UI.Notifications via jna
 * - macOS: UNUserNotificationCenter via JNI
 */
class DesktopNotificationService : NotificationService {

    private var trayIcon: TrayIcon? = null
    private val isSupported: Boolean = SystemTray.isSupported()

    // Track active notifications (by key for cancellation)
    private val activeNotifications = mutableMapOf<String, String>()
    private var currentCallNotificationId: String? = null
    private var pendingScreenshareCallback: (() -> Unit)? = null
    private var pendingScreenshareConfId: String? = null

    init {
        if (isSupported) {
            initializeTrayIcon()
        } else {
            Log.w(TAG, "SystemTray is not supported on this platform")
        }
    }

    private fun initializeTrayIcon() {
        try {
            // Create a simple default icon
            val image = createDefaultIcon()
            trayIcon = TrayIcon(image, "Jami").apply {
                isImageAutoSize = true
            }

            val tray = SystemTray.getSystemTray()
            tray.add(trayIcon)

            Log.d(TAG, "Desktop notification service initialized with SystemTray")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize SystemTray: ${e.message}")
        }
    }

    private fun createDefaultIcon(): Image {
        // Create a simple 16x16 icon (in a real app, load from resources)
        val toolkit = Toolkit.getDefaultToolkit()
        // Try to load a default icon, or create a minimal one
        return toolkit.createImage(ByteArray(16 * 16 * 4)) // Placeholder
    }

    private fun showTrayNotification(
        title: String,
        message: String,
        type: MessageType = MessageType.INFO
    ) {
        if (!isSupported) {
            Log.i(TAG, "Notification [$type]: $title - $message")
            return
        }

        SwingUtilities.invokeLater {
            try {
                trayIcon?.displayMessage(title, message, type)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to display notification: ${e.message}")
            }
        }
    }

    // ==================== Call Notifications ====================

    override fun showCallNotification(notifId: Int): Any? {
        val id = "call_$notifId"
        currentCallNotificationId = id

        showTrayNotification(
            title = "Jami Call",
            message = "Ongoing call",
            type = MessageType.INFO
        )

        Log.d(TAG, "Showing call notification: $id")
        return id
    }

    override fun cancelCallNotification() {
        currentCallNotificationId?.let { id ->
            // SystemTray doesn't support canceling specific notifications
            // Log the cancellation for tracking
            Log.d(TAG, "Cancelled call notification: $id")
        }
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

        showTrayNotification(
            title = title,
            message = conference.getDisplayName(),
            type = if (state == Call.CallStatus.RINGING) MessageType.WARNING else MessageType.INFO
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
        showTrayNotification(
            title = "Missed Call",
            message = "From ${call.getDisplayName()}",
            type = MessageType.INFO
        )

        Log.d(TAG, "Showing missed call notification: ${call.getDaemonIdString()}")
    }

    override fun showGroupCallNotification(conversation: Conversation, remove: Boolean) {
        if (remove) {
            Log.d(TAG, "Removed group call notification: ${conversation.uri.uri}")
            return
        }

        showTrayNotification(
            title = "Group Call",
            message = "Ongoing group call in ${conversation.getDisplayName()}",
            type = MessageType.INFO
        )

        Log.d(TAG, "Showing group call notification: ${conversation.uri.uri}")
    }

    // ==================== Text Notifications ====================

    override fun showTextNotification(conversation: Conversation) {
        val conversationKey = "${conversation.accountId}:${conversation.uri.uri}"
        activeNotifications[conversationKey] = "message_$conversationKey"

        val lastMessage = conversation.getLastMessage() ?: return
        val senderName = conversation.getDisplayName()

        showTrayNotification(
            title = senderName,
            message = lastMessage,
            type = MessageType.INFO
        )

        Log.d(TAG, "Showing text notification for conversation: $conversationKey")
    }

    override fun cancelTextNotification(accountId: String, contact: Uri) {
        val conversationKey = "$accountId:${contact.uri}"
        activeNotifications.remove(conversationKey)
        Log.d(TAG, "Cancelled text notification: $conversationKey")
    }

    override fun cancelAll() {
        activeNotifications.clear()
        currentCallNotificationId = null
        Log.d(TAG, "Cancelled all notifications")
    }

    // ==================== Trust Request Notifications ====================

    override fun showIncomingTrustRequestNotification(account: Account) {
        showTrayNotification(
            title = "New Contact Request",
            message = "You have a new contact request",
            type = MessageType.INFO
        )

        Log.d(TAG, "Showing trust request notification for account: ${account.accountId}")
    }

    override fun cancelTrustRequestNotification(accountId: String) {
        Log.d(TAG, "Cancelled trust request notification for account: $accountId")
    }

    // ==================== File Transfer Notifications ====================

    override fun showFileTransferNotification(conversation: Conversation, info: DataTransfer) {
        val progress = if (info.totalSize > 0) {
            ((info.bytesProgress * 100) / info.totalSize).toInt()
        } else {
            0
        }

        showTrayNotification(
            title = info.displayName,
            message = "Transfer progress: $progress%",
            type = MessageType.INFO
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
        Log.d(TAG, "Removed file transfer notification: $fileId")
    }

    override fun getDataTransferNotification(notificationId: Int): Any? {
        return null
    }

    override fun cancelFileNotification(notificationId: Int) {
        Log.d(TAG, "Cancelled file notification: $notificationId")
    }

    // ==================== Location Notifications ====================

    override fun showLocationNotification(
        account: Account,
        contact: Contact,
        conversation: Conversation
    ) {
        showTrayNotification(
            title = "Location Sharing",
            message = "Sharing location with ${contact.displayName}",
            type = MessageType.INFO
        )

        Log.d(TAG, "Showing location notification for contact: ${contact.uri.uri}")
    }

    override fun cancelLocationNotification(account: Account, contact: Contact) {
        Log.d(TAG, "Cancelled location notification for contact: ${contact.uri.uri}")
    }

    // ==================== Service Notification ====================

    override val serviceNotification: Any
        get() = Unit // Desktop doesn't need foreground service notifications

    override fun onConnectionUpdate(connected: Boolean) {
        // Could update tray icon tooltip
        SwingUtilities.invokeLater {
            trayIcon?.toolTip = if (connected) "Jami - Connected" else "Jami - Connecting..."
        }
        Log.d(TAG, "Connection status updated: connected=$connected")
    }

    // ==================== Push Notifications ====================

    override fun processPush() {
        Log.d(TAG, "Processing push notification")
    }

    override fun testPushNotification(accountId: String) {
        showTrayNotification(
            title = "Push Test",
            message = "Push notification working for account: $accountId",
            type = MessageType.INFO
        )

        Log.d(TAG, "Test push notification shown for account: $accountId")
    }

    /**
     * Cleanup resources when service is destroyed.
     */
    fun dispose() {
        trayIcon?.let { icon ->
            try {
                SystemTray.getSystemTray().remove(icon)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove tray icon: ${e.message}")
            }
        }
        trayIcon = null
    }

    companion object {
        private const val TAG = "DesktopNotificationService"
    }
}
