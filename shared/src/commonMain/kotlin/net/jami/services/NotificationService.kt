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

import net.jami.model.Account
import net.jami.model.Call
import net.jami.model.Conference
import net.jami.model.Contact
import net.jami.model.Conversation
import net.jami.model.DataTransfer
import net.jami.model.Uri

/**
 * Service for displaying platform notifications.
 *
 * Ported from: jami-client-android libjamiclient NotificationService.kt
 * Converted RxJava Completable to suspend functions.
 *
 * Platform-specific implementations:
 * - Android: NotificationManager, NotificationCompat
 * - iOS: UserNotifications framework (UNUserNotificationCenter)
 * - macOS: NSUserNotificationCenter / UserNotifications
 * - Desktop: Platform notification APIs (libnotify on Linux, etc.)
 * - Web: Browser Notification API
 */
interface NotificationService {

    // ══════════════════════════════════════════════════════════════════════════
    // Call Notifications
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Show the call notification (foreground service notification on Android).
     *
     * @param notifId Notification ID
     * @return Platform-specific notification object, or null
     */
    fun showCallNotification(notifId: Int): Any?

    /**
     * Cancel the active call notification.
     */
    fun cancelCallNotification()

    /**
     * Remove call notification from system.
     */
    fun removeCallNotification()

    /**
     * Handle call notification lifecycle (incoming, ongoing, ended).
     *
     * @param conference The conference/call
     * @param remove Whether to remove the notification
     * @param startScreenshare Whether to start screen share from notification
     */
    suspend fun handleCallNotification(
        conference: Conference,
        remove: Boolean,
        startScreenshare: Boolean = false
    )

    /**
     * Prepare for screen sharing from notification action.
     *
     * @param conference The conference
     * @param callback Callback when ready to share
     */
    fun preparePendingScreenshare(conference: Conference, callback: () -> Unit)

    /**
     * Start screen sharing that was pending from notification.
     *
     * @param confId Conference ID
     */
    fun startPendingScreenshare(confId: String)

    /**
     * Show missed call notification.
     *
     * @param call The missed call
     */
    fun showMissedCallNotification(call: Call)

    /**
     * Show/hide group call notification.
     *
     * @param conversation The conversation with group call
     * @param remove Whether to remove the notification
     */
    fun showGroupCallNotification(conversation: Conversation, remove: Boolean = false)

    // ══════════════════════════════════════════════════════════════════════════
    // Text/Message Notifications
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Show a text message notification.
     *
     * @param conversation The conversation with new message
     */
    fun showTextNotification(conversation: Conversation)

    /**
     * Cancel text notification for a conversation.
     *
     * @param accountId Account ID
     * @param contact Contact URI
     */
    fun cancelTextNotification(accountId: String, contact: Uri)

    /**
     * Cancel all notifications.
     */
    fun cancelAll()

    // ══════════════════════════════════════════════════════════════════════════
    // Trust Request Notifications
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Show incoming trust request notification.
     *
     * @param account Account receiving the request
     */
    fun showIncomingTrustRequestNotification(account: Account)

    /**
     * Cancel trust request notification for an account.
     *
     * @param accountId Account ID
     */
    fun cancelTrustRequestNotification(accountId: String)

    // ══════════════════════════════════════════════════════════════════════════
    // File Transfer Notifications
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Show file transfer notification.
     *
     * @param conversation The conversation
     * @param info Transfer information
     */
    fun showFileTransferNotification(conversation: Conversation, info: DataTransfer)

    /**
     * Handle data transfer notification lifecycle.
     *
     * @param transfer The transfer
     * @param conversation The conversation
     * @param remove Whether to remove the notification
     */
    fun handleDataTransferNotification(
        transfer: DataTransfer,
        conversation: Conversation,
        remove: Boolean
    )

    /**
     * Remove transfer notification.
     *
     * @param accountId Account ID
     * @param conversationUri Conversation URI
     * @param fileId File transfer ID
     */
    fun removeTransferNotification(accountId: String, conversationUri: Uri, fileId: String)

    /**
     * Get data transfer notification object.
     *
     * @param notificationId Notification ID
     * @return Platform-specific notification object, or null
     */
    fun getDataTransferNotification(notificationId: Int): Any?

    /**
     * Cancel file transfer notification.
     *
     * @param notificationId Notification ID
     */
    fun cancelFileNotification(notificationId: Int)

    // ══════════════════════════════════════════════════════════════════════════
    // Location Notifications
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Show location sharing notification.
     *
     * @param account Account sharing location
     * @param contact Contact receiving location
     * @param conversation The conversation
     */
    fun showLocationNotification(account: Account, contact: Contact, conversation: Conversation)

    /**
     * Cancel location sharing notification.
     *
     * @param account Account
     * @param contact Contact
     */
    fun cancelLocationNotification(account: Account, contact: Contact)

    // ══════════════════════════════════════════════════════════════════════════
    // Service / Connection Status
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Get the foreground service notification (Android).
     *
     * @return Platform-specific notification object
     */
    val serviceNotification: Any

    /**
     * Update notification based on connection status.
     *
     * @param connected Whether connected to daemon
     */
    fun onConnectionUpdate(connected: Boolean)

    // ══════════════════════════════════════════════════════════════════════════
    // Push Notifications
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Process incoming push notification.
     */
    fun processPush()

    /**
     * Test push notification delivery.
     *
     * @param accountId Account ID to test
     */
    fun testPushNotification(accountId: String)

    companion object {
        /** Intent extra key for trust request account ID */
        const val NOTIF_TRUST_REQUEST_ACCOUNT_ID = "NOTIF_TRUST_REQUEST_ACCOUNT_ID"

        /** Intent extra key for multiple trust requests */
        const val NOTIF_TRUST_REQUEST_MULTIPLE = "NOTIFICATION_TRUST_REQUEST_MULTIPLE"

        /** Intent extra key for call ID */
        const val KEY_CALL_ID = "callId"

        /** Intent extra key for hold action */
        const val KEY_HOLD_ID = "holdId"

        /** Intent extra key for end call action */
        const val KEY_END_ID = "endId"

        /** Intent extra key for notification ID */
        const val KEY_NOTIFICATION_ID = "notificationId"

        /** Intent extra key for screen share action */
        const val KEY_SCREENSHARE = "screenshare"
    }
}

/**
 * Stub implementation of NotificationService for testing and platforms without notifications.
 */
class StubNotificationService : NotificationService {
    private var _serviceNotification: Any = Unit

    override fun showCallNotification(notifId: Int): Any? = null
    override fun cancelCallNotification() {}
    override fun removeCallNotification() {}
    override suspend fun handleCallNotification(conference: Conference, remove: Boolean, startScreenshare: Boolean) {}
    override fun preparePendingScreenshare(conference: Conference, callback: () -> Unit) {
        callback()
    }
    override fun startPendingScreenshare(confId: String) {}
    override fun showMissedCallNotification(call: Call) {}
    override fun showGroupCallNotification(conversation: Conversation, remove: Boolean) {}

    override fun showTextNotification(conversation: Conversation) {}
    override fun cancelTextNotification(accountId: String, contact: Uri) {}
    override fun cancelAll() {}

    override fun showIncomingTrustRequestNotification(account: Account) {}
    override fun cancelTrustRequestNotification(accountId: String) {}

    override fun showFileTransferNotification(conversation: Conversation, info: DataTransfer) {}
    override fun handleDataTransferNotification(transfer: DataTransfer, conversation: Conversation, remove: Boolean) {}
    override fun removeTransferNotification(accountId: String, conversationUri: Uri, fileId: String) {}
    override fun getDataTransferNotification(notificationId: Int): Any? = null
    override fun cancelFileNotification(notificationId: Int) {}

    override fun showLocationNotification(account: Account, contact: Contact, conversation: Conversation) {}
    override fun cancelLocationNotification(account: Account, contact: Contact) {}

    override val serviceNotification: Any get() = _serviceNotification
    override fun onConnectionUpdate(connected: Boolean) {}

    override fun processPush() {}
    override fun testPushNotification(accountId: String) {}
}
