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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
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
 * Android implementation of NotificationService.
 *
 * Uses Android NotificationManager and NotificationCompat for system notifications.
 * Supports notification channels (Android 8.0+), actions, and progress updates.
 *
 * ## Notification Channels
 * - `jami_calls` - Call notifications (high importance)
 * - `jami_messages` - Message notifications (default importance)
 * - `jami_file_transfer` - File transfer notifications (low importance)
 * - `jami_service` - Foreground service notification (min importance)
 *
 * ## Usage
 * This service is typically injected via Koin and used by ConversationFacade and CallService.
 */
class AndroidNotificationService(
    private val context: Context
) : NotificationService {

    private val notificationManager: NotificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    // Track active notifications
    private val activeCallNotifications = mutableSetOf<Int>()
    private val activeTextNotifications = mutableMapOf<String, Int>() // conversationId -> notifId
    private val activeTransferNotifications = mutableMapOf<String, Int>() // fileId -> notifId
    private var currentCallNotificationId: Int? = null

    private var pendingScreenshareCallback: (() -> Unit)? = null
    private var pendingScreenshareConfId: String? = null

    init {
        createNotificationChannels()
    }

    // ==================== Notification Channels ====================

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channels = listOf(
                NotificationChannel(
                    CHANNEL_CALLS,
                    "Calls",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Incoming and ongoing call notifications"
                    setShowBadge(true)
                    enableVibration(true)
                    setBypassDnd(true)
                },
                NotificationChannel(
                    CHANNEL_MESSAGES,
                    "Messages",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "New message notifications"
                    setShowBadge(true)
                    enableVibration(true)
                },
                NotificationChannel(
                    CHANNEL_FILE_TRANSFER,
                    "File Transfers",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "File transfer progress notifications"
                    setShowBadge(false)
                    enableVibration(false)
                },
                NotificationChannel(
                    CHANNEL_SERVICE,
                    "Background Service",
                    NotificationManager.IMPORTANCE_MIN
                ).apply {
                    description = "Jami background service"
                    setShowBadge(false)
                    enableVibration(false)
                },
                NotificationChannel(
                    CHANNEL_REQUESTS,
                    "Contact Requests",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "New contact request notifications"
                    setShowBadge(true)
                    enableVibration(true)
                }
            )
            notificationManager.createNotificationChannels(channels)
        }
    }

    // ==================== Call Notifications ====================

    override fun showCallNotification(notifId: Int): Any? {
        currentCallNotificationId = notifId
        activeCallNotifications.add(notifId)

        // Create a simple ongoing call notification
        // In a full implementation, this would include call actions and caller info
        val notification = NotificationCompat.Builder(context, CHANNEL_CALLS)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("Jami Call")
            .setContentText("Ongoing call")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

        notificationManager.notify(notifId, notification)
        Log.d(TAG, "Showing call notification: $notifId")
        return notification
    }

    override fun cancelCallNotification() {
        currentCallNotificationId?.let { notifId ->
            notificationManager.cancel(notifId)
            activeCallNotifications.remove(notifId)
            Log.d(TAG, "Cancelled call notification: $notifId")
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

        val notifId = conference.id.hashCode()
        currentCallNotificationId = notifId
        activeCallNotifications.add(notifId)

        val state = conference.state
        val title = when (state) {
            Call.CallStatus.RINGING -> "Incoming Call"
            Call.CallStatus.CURRENT, Call.CallStatus.HOLD -> "Ongoing Call"
            else -> "Call"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_CALLS)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle(title)
            .setContentText(conference.getDisplayName())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(state == Call.CallStatus.CURRENT || state == Call.CallStatus.HOLD)
            .setAutoCancel(false)

        // Add actions based on state
        if (state == Call.CallStatus.RINGING) {
            // Incoming call - add answer and decline actions
            builder.addAction(
                android.R.drawable.ic_menu_call,
                "Answer",
                createCallActionPendingIntent(conference.id, ACTION_ANSWER)
            )
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Decline",
                createCallActionPendingIntent(conference.id, ACTION_DECLINE)
            )
        } else if (state == Call.CallStatus.CURRENT) {
            // Ongoing call - add hangup and hold actions
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Hang Up",
                createCallActionPendingIntent(conference.id, ACTION_HANGUP)
            )
        }

        notificationManager.notify(notifId, builder.build())
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
        val notifId = NOTIF_MISSED_CALL_BASE + call.getDaemonIdString().hashCode()

        val notification = NotificationCompat.Builder(context, CHANNEL_CALLS)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("Missed Call")
            .setContentText("From ${call.getDisplayName()}")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notifId, notification)
        Log.d(TAG, "Showing missed call notification: $notifId")
    }

    override fun showGroupCallNotification(conversation: Conversation, remove: Boolean) {
        val notifId = NOTIF_GROUP_CALL_BASE + conversation.uri.uri.hashCode()

        if (remove) {
            notificationManager.cancel(notifId)
            Log.d(TAG, "Removed group call notification: $notifId")
            return
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_CALLS)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("Group Call")
            .setContentText("Ongoing group call in ${conversation.getDisplayName()}")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notifId, notification)
        Log.d(TAG, "Showing group call notification: $notifId")
    }

    // ==================== Text Notifications ====================

    override fun showTextNotification(conversation: Conversation) {
        val conversationKey = "${conversation.accountId}:${conversation.uri.uri}"
        val notifId = activeTextNotifications.getOrPut(conversationKey) {
            NOTIF_MESSAGE_BASE + conversationKey.hashCode()
        }

        val lastMessage = conversation.getLastMessage() ?: return
        val senderName = conversation.getDisplayName()

        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(senderName)
            .setContentText(lastMessage)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            // Add message style for conversation
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(lastMessage)
                    .setBigContentTitle(senderName)
            )
            .build()

        notificationManager.notify(notifId, notification)
        Log.d(TAG, "Showing text notification for conversation: $conversationKey")
    }

    override fun cancelTextNotification(accountId: String, contact: Uri) {
        val conversationKey = "$accountId:${contact.uri}"
        activeTextNotifications.remove(conversationKey)?.let { notifId ->
            notificationManager.cancel(notifId)
            Log.d(TAG, "Cancelled text notification: $conversationKey")
        }
    }

    override fun cancelAll() {
        notificationManager.cancelAll()
        activeCallNotifications.clear()
        activeTextNotifications.clear()
        activeTransferNotifications.clear()
        currentCallNotificationId = null
        Log.d(TAG, "Cancelled all notifications")
    }

    // ==================== Trust Request Notifications ====================

    override fun showIncomingTrustRequestNotification(account: Account) {
        val notifId = NOTIF_TRUST_REQUEST_BASE + account.accountId.hashCode()

        val notification = NotificationCompat.Builder(context, CHANNEL_REQUESTS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("New Contact Request")
            .setContentText("You have a new contact request")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notifId, notification)
        Log.d(TAG, "Showing trust request notification for account: ${account.accountId}")
    }

    override fun cancelTrustRequestNotification(accountId: String) {
        val notifId = NOTIF_TRUST_REQUEST_BASE + accountId.hashCode()
        notificationManager.cancel(notifId)
        Log.d(TAG, "Cancelled trust request notification for account: $accountId")
    }

    // ==================== File Transfer Notifications ====================

    override fun showFileTransferNotification(conversation: Conversation, info: DataTransfer) {
        val notifId = activeTransferNotifications.getOrPut(info.fileId ?: info.id.toString()) {
            NOTIF_FILE_TRANSFER_BASE + (info.fileId?.hashCode() ?: info.id.toInt())
        }

        val progress = if (info.totalSize > 0) {
            ((info.bytesProgress * 100) / info.totalSize).toInt()
        } else {
            0
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_FILE_TRANSFER)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(info.displayName)
            .setContentText("${info.bytesProgress} / ${info.totalSize} bytes")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(100, progress, progress == 0)
            .setOngoing(info.transferStatus == Interaction.TransferStatus.TRANSFER_ONGOING)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notifId, notification)
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
        activeTransferNotifications.remove(fileId)?.let { notifId ->
            notificationManager.cancel(notifId)
            Log.d(TAG, "Removed file transfer notification: $fileId")
        }
    }

    override fun getDataTransferNotification(notificationId: Int): Any? {
        // Return the notification if it exists
        return null // Would need to track notifications by ID
    }

    override fun cancelFileNotification(notificationId: Int) {
        notificationManager.cancel(notificationId)
        // Remove from tracking map
        activeTransferNotifications.entries.removeIf { it.value == notificationId }
        Log.d(TAG, "Cancelled file notification: $notificationId")
    }

    // ==================== Location Notifications ====================

    override fun showLocationNotification(
        account: Account,
        contact: Contact,
        conversation: Conversation
    ) {
        val notifId = NOTIF_LOCATION_BASE + contact.uri.uri.hashCode()

        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Location Sharing")
            .setContentText("Sharing location with ${contact.displayName}")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

        notificationManager.notify(notifId, notification)
        Log.d(TAG, "Showing location notification for contact: ${contact.uri.uri}")
    }

    override fun cancelLocationNotification(account: Account, contact: Contact) {
        val notifId = NOTIF_LOCATION_BASE + contact.uri.uri.hashCode()
        notificationManager.cancel(notifId)
        Log.d(TAG, "Cancelled location notification for contact: ${contact.uri.uri}")
    }

    // ==================== Service Notification ====================

    override val serviceNotification: Any
        get() = createServiceNotification()

    private fun createServiceNotification(): Notification {
        return NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Jami")
            .setContentText("Running in background")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
    }

    override fun onConnectionUpdate(connected: Boolean) {
        // Update service notification with connection status
        val notification = NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Jami")
            .setContentText(if (connected) "Connected" else "Connecting...")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

        notificationManager.notify(NOTIF_SERVICE, notification)
        Log.d(TAG, "Connection status updated: connected=$connected")
    }

    // ==================== Push Notifications ====================

    override fun processPush() {
        Log.d(TAG, "Processing push notification")
        // Push notifications are typically processed by FirebaseMessagingService
        // This method can be used to trigger refresh or sync
    }

    override fun testPushNotification(accountId: String) {
        val notifId = NOTIF_TEST_PUSH

        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Push Test")
            .setContentText("Push notification working for account: $accountId")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notifId, notification)
        Log.d(TAG, "Test push notification shown for account: $accountId")
    }

    // ==================== Private Helpers ====================

    private fun createCallActionPendingIntent(callId: String, action: String): PendingIntent {
        val intent = Intent(action).apply {
            putExtra(NotificationService.KEY_CALL_ID, callId)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getBroadcast(
            context,
            callId.hashCode(),
            intent,
            flags
        )
    }

    companion object {
        private const val TAG = "AndroidNotificationService"

        // Notification channels
        const val CHANNEL_CALLS = "jami_calls"
        const val CHANNEL_MESSAGES = "jami_messages"
        const val CHANNEL_FILE_TRANSFER = "jami_file_transfer"
        const val CHANNEL_SERVICE = "jami_service"
        const val CHANNEL_REQUESTS = "jami_requests"

        // Notification ID bases
        const val NOTIF_SERVICE = 1
        const val NOTIF_CALL_BASE = 1000
        const val NOTIF_MISSED_CALL_BASE = 2000
        const val NOTIF_GROUP_CALL_BASE = 3000
        const val NOTIF_MESSAGE_BASE = 4000
        const val NOTIF_TRUST_REQUEST_BASE = 5000
        const val NOTIF_FILE_TRANSFER_BASE = 6000
        const val NOTIF_LOCATION_BASE = 7000
        const val NOTIF_TEST_PUSH = 9999

        // Intent actions
        const val ACTION_ANSWER = "net.jami.action.ANSWER_CALL"
        const val ACTION_DECLINE = "net.jami.action.DECLINE_CALL"
        const val ACTION_HANGUP = "net.jami.action.HANGUP_CALL"
    }
}
