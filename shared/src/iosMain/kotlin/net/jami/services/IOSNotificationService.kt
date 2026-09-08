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

import jami_kmp.shared.generated.resources.Res
import jami_kmp.shared.generated.resources.*
import kotlinx.coroutines.runBlocking
import net.jami.model.*
import org.jetbrains.compose.resources.getString
import net.jami.repository.SettingsRepository
import net.jami.utils.Log
import platform.Foundation.NSUUID
import platform.UserNotifications.*
import net.jami.services.IOSNotificationDelegate

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
 * iOS implementation of NotificationService.
 *
 * Uses UserNotifications framework (UNUserNotificationCenter) for local notifications.
 * Call notifications should ideally integrate with CallKit for the best user experience.
 * Enforces NotificationSettings via NotificationGuard.
 *
 * ## Note
 * This implementation requires notification permission to be requested at app launch:
 * ```swift
 * UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])
 * ```
 *
 * ## Categories
 * - `JAMI_CALL` - Call notifications with answer/decline actions
 * - `JAMI_MESSAGE` - Message notifications with reply action
 * - `JAMI_REQUEST` - Contact request notifications with accept/decline actions
 */
class IOSNotificationService(
    private val settingsRepository: SettingsRepository,
    private val notificationGuard: NotificationGuard,
    private val callKitManager: CallKitManagerApi,
) : NotificationService {

    private val notificationCenter = UNUserNotificationCenter.currentNotificationCenter()

    // Track active notifications
    private val activeNotifications = mutableMapOf<String, String>() // key -> notification id
    private var currentCallNotificationId: String? = null
    private var pendingScreenshareCallback: (() -> Unit)? = null
    private var pendingScreenshareConfId: String? = null

    init {
        setupNotificationCategories()
    }

    private fun setupNotificationCategories() {
        // Call category with answer/decline actions
        val answerAction = UNNotificationAction.actionWithIdentifier(
            identifier = ACTION_ANSWER_CALL,
            title = runBlocking { getString(Res.string.action_call_accept) },
            options = UNNotificationActionOptionForeground
        )
        val declineAction = UNNotificationAction.actionWithIdentifier(
            identifier = ACTION_DECLINE_CALL,
            title = runBlocking { getString(Res.string.action_call_decline) },
            options = UNNotificationActionOptionDestructive
        )
        val callCategory = UNNotificationCategory.categoryWithIdentifier(
            identifier = CATEGORY_CALL,
            actions = listOf(answerAction, declineAction),
            intentIdentifiers = emptyList<String>(),
            options = UNNotificationCategoryOptionCustomDismissAction
        )

        // Message category with reply and mark as read actions
        val replyAction = UNTextInputNotificationAction.actionWithIdentifier(
            identifier = ACTION_REPLY_MESSAGE,
            title = runBlocking { getString(Res.string.notif_reply) },
            options = UNNotificationActionOptionNone,
            textInputButtonTitle = runBlocking { getString(Res.string.notif_send_reply) },
            textInputPlaceholder = runBlocking { getString(Res.string.write_a_message) }
        )
        val markReadAction = UNNotificationAction.actionWithIdentifier(
            identifier = ACTION_MARK_READ,
            title = runBlocking { getString(Res.string.notif_mark_as_read) },
            options = 0u
        )
        val messageCategory = UNNotificationCategory.categoryWithIdentifier(
            identifier = CATEGORY_MESSAGE,
            actions = listOf(replyAction, markReadAction),
            intentIdentifiers = emptyList<String>(),
            options = UNNotificationCategoryOptionNone
        )

        // Request category with accept/decline actions
        val acceptAction = UNNotificationAction.actionWithIdentifier(
            identifier = ACTION_ACCEPT,
            title = runBlocking { getString(Res.string.accept) },
            options = UNNotificationActionOptionNone
        )
        val requestDeclineAction = UNNotificationAction.actionWithIdentifier(
            identifier = ACTION_REQUEST_DECLINE,
            title = runBlocking { getString(Res.string.decline) },
            options = UNNotificationActionOptionDestructive
        )
        val requestCategory = UNNotificationCategory.categoryWithIdentifier(
            identifier = CATEGORY_REQUEST,
            actions = listOf(acceptAction, requestDeclineAction),
            intentIdentifiers = emptyList<String>(),
            options = UNNotificationCategoryOptionNone
        )

        notificationCenter.setNotificationCategories(
            setOf(callCategory, messageCategory, requestCategory)
        )
    }

    // ==================== Call Notifications ====================

    override fun showCallNotification(notifId: Int): Any? {
        // Check settings before showing
        if (!notificationGuard.shouldShowCallNotification()) {
            Log.d(TAG, "Call notifications disabled in settings")
            return null
        }

        val identifier = "call_$notifId"
        currentCallNotificationId = identifier

        val content = UNMutableNotificationContent().apply {
            setTitle(runBlocking { getString(Res.string.notif_current_call) })
            setCategoryIdentifier(CATEGORY_CALL)
            // Apply sound based on settings
            if (notificationGuard.shouldVibrate()) {
                setSound(UNNotificationSound.defaultSound())
            }
        }

        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = identifier,
            content = content,
            trigger = null // Deliver immediately
        )

        notificationCenter.addNotificationRequest(request) { error ->
            if (error != null) {
                Log.e(TAG, "Failed to show call notification: ${error.localizedDescription}")
            } else {
                Log.d(TAG, "Showing call notification: $identifier")
            }
        }

        return identifier
    }

    override fun cancelCallNotification() {
        currentCallNotificationId?.let { id ->
            notificationCenter.removeDeliveredNotificationsWithIdentifiers(listOf(id))
            notificationCenter.removePendingNotificationRequestsWithIdentifiers(listOf(id))
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

        // Check settings before showing
        if (!notificationGuard.shouldShowCallNotification()) {
            Log.d(TAG, "Call notifications disabled in settings")
            return
        }

        if (startScreenshare) {
            startPendingScreenshare(conference.id)
            return
        }

        val identifier = "call_${conference.id}"
        currentCallNotificationId = identifier

        val state = conference.state
        // For RINGING calls, CallKit already shows the native incoming-call UI.
        // Skip the UNNotification to avoid a duplicate banner.
        if (state == Call.CallStatus.RINGING) return
        val title = runBlocking {
            when (state) {
                Call.CallStatus.CURRENT, Call.CallStatus.HOLD ->
                    getString(Res.string.notif_current_call_title, conference.getDisplayName())
                else -> getString(Res.string.notif_current_call)
            }
        }

        val content = UNMutableNotificationContent().apply {
            setTitle(title)
            setBody(conference.getDisplayName())
            setCategoryIdentifier(CATEGORY_CALL)
            // Apply sound based on settings
            if (notificationGuard.shouldVibrate()) {
                setSound(UNNotificationSound.defaultSound())
            }
            setUserInfo(buildMap<Any?, Any?> {
                put(KEY_ACCOUNT_ID, conference.accountId)
                put(KEY_CONFERENCE_ID, conference.id)
                conference.firstCall?.daemonId?.let { put(KEY_CALL_ID, it) }
            })
        }

        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = identifier,
            content = content,
            trigger = null
        )

        notificationCenter.addNotificationRequest(request) { error ->
            if (error != null) {
                Log.e(TAG, "Failed to show call notification: ${error.localizedDescription}")
            } else {
                Log.d(TAG, "Showing call notification for conference: ${conference.id}")
            }
        }
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
        val identifier = "missed_${call.getDaemonIdString()}"

        val content = UNMutableNotificationContent().apply {
            setTitle(runBlocking { getString(Res.string.notif_missed_incoming_call) })
            setBody(call.getDisplayName())
            setSound(UNNotificationSound.defaultSound())
        }

        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = identifier,
            content = content,
            trigger = null
        )

        notificationCenter.addNotificationRequest(request) { error ->
            if (error != null) {
                Log.e(TAG, "Failed to show missed call notification: ${error.localizedDescription}")
            } else {
                Log.d(TAG, "Showing missed call notification: $identifier")
            }
        }
    }

    override fun showGroupCallNotification(conversation: Conversation, remove: Boolean) {
        val identifier = "group_call_${conversation.uri.uri}"

        if (remove) {
            notificationCenter.removeDeliveredNotificationsWithIdentifiers(listOf(identifier))
            Log.d(TAG, "Removed group call notification: $identifier")
            return
        }

        val content = UNMutableNotificationContent().apply {
            setTitle(runBlocking { getString(Res.string.notif_inprogress_group_call) })
            setBody(conversation.getDisplayName())
            setCategoryIdentifier(CATEGORY_CALL)
            setSound(UNNotificationSound.defaultSound())
        }

        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = identifier,
            content = content,
            trigger = null
        )

        notificationCenter.addNotificationRequest(request) { error ->
            if (error != null) {
                Log.e(TAG, "Failed to show group call notification: ${error.localizedDescription}")
            } else {
                Log.d(TAG, "Showing group call notification: $identifier")
            }
        }
    }

    // ==================== Text Notifications ====================

    override fun showTextNotification(conversation: Conversation) {
        // Check settings before showing
        if (!notificationGuard.shouldShowMessageNotification()) {
            Log.d(TAG, "Message notifications disabled in settings")
            return
        }

        val conversationKey = "${conversation.accountId}_${conversation.uri.uri}"
        val identifier = "message_$conversationKey"
        activeNotifications[conversationKey] = identifier

        val lastMessage = conversation.getLastMessage() ?: return
        val senderName = conversation.getDisplayName()

        val content = UNMutableNotificationContent().apply {
            setTitle(senderName)
            setBody(lastMessage)
            setCategoryIdentifier(CATEGORY_MESSAGE)
            // Apply sound based on settings
            if (notificationGuard.shouldVibrate()) {
                setSound(UNNotificationSound.defaultSound())
            }
            setBadge(platform.Foundation.NSNumber(int = 1))
            setUserInfo(mapOf<Any?, Any?>(
                KEY_ACCOUNT_ID to conversation.accountId,
                KEY_CONVERSATION_ID to conversation.uri.uri
            ))
            setThreadIdentifier(conversation.uri.uri) // For grouping notifications
        }

        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = identifier,
            content = content,
            trigger = null
        )

        notificationCenter.addNotificationRequest(request) { error ->
            if (error != null) {
                Log.e(TAG, "Failed to show text notification: ${error.localizedDescription}")
            } else {
                Log.d(TAG, "Showing text notification for conversation: $conversationKey")
            }
        }
    }

    override fun cancelTextNotification(accountId: String, contact: Uri) {
        val conversationKey = "${accountId}_${contact.uri}"
        activeNotifications.remove(conversationKey)?.let { identifier ->
            notificationCenter.removeDeliveredNotificationsWithIdentifiers(listOf(identifier))
            Log.d(TAG, "Cancelled text notification: $conversationKey")
        }
    }

    override fun cancelAll() {
        notificationCenter.removeAllDeliveredNotifications()
        notificationCenter.removeAllPendingNotificationRequests()
        activeNotifications.clear()
        currentCallNotificationId = null
        Log.d(TAG, "Cancelled all notifications")
    }

    // ==================== Trust Request Notifications ====================

    override fun showIncomingTrustRequestNotification(account: Account) {
        // Check settings before showing
        if (!notificationGuard.shouldShowRequestNotification()) {
            Log.d(TAG, "Contact request notifications disabled in settings")
            return
        }

        val identifier = "request_${account.accountId}"

        val content = UNMutableNotificationContent().apply {
            setTitle(runBlocking { getString(Res.string.new_invitation_request_title) })
            setBody(account.displayUsername)
            setCategoryIdentifier(CATEGORY_REQUEST)
            // Apply sound based on settings
            if (notificationGuard.shouldVibrate()) {
                setSound(UNNotificationSound.defaultSound())
            }
            setUserInfo(buildMap<Any?, Any?> {
                put(KEY_ACCOUNT_ID, account.accountId)
                account.getPending().singleOrNull()?.let { put(KEY_CONVERSATION_ID, it.uri.uri) }
            })
        }

        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = identifier,
            content = content,
            trigger = null
        )

        notificationCenter.addNotificationRequest(request) { error ->
            if (error != null) {
                Log.e(TAG, "Failed to show trust request notification: ${error.localizedDescription}")
            } else {
                Log.d(TAG, "Showing trust request notification for account: ${account.accountId}")
            }
        }
    }

    override fun cancelTrustRequestNotification(accountId: String) {
        val identifier = "request_$accountId"
        notificationCenter.removeDeliveredNotificationsWithIdentifiers(listOf(identifier))
        Log.d(TAG, "Cancelled trust request notification for account: $accountId")
    }

    // ==================== File Transfer Notifications ====================

    override fun showFileTransferNotification(conversation: Conversation, info: DataTransfer) {
        val identifier = "transfer_${info.fileId ?: info.id}"

        val progress = if (info.totalSize > 0) {
            ((info.bytesProgress * 100) / info.totalSize).toInt()
        } else {
            0
        }

        val content = UNMutableNotificationContent().apply {
            setTitle(info.displayName)
            setBody("${info.bytesProgress} / ${info.totalSize} bytes ($progress%)")
            setSound(null) // No sound for progress updates
        }

        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = identifier,
            content = content,
            trigger = null
        )

        notificationCenter.addNotificationRequest(request) { error ->
            if (error != null) {
                Log.e(TAG, "Failed to show file transfer notification: ${error.localizedDescription}")
            } else {
                Log.d(TAG, "Showing file transfer notification: ${info.fileId}, progress: $progress%")
            }
        }
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
        val identifier = "transfer_$fileId"
        notificationCenter.removeDeliveredNotificationsWithIdentifiers(listOf(identifier))
        Log.d(TAG, "Removed file transfer notification: $fileId")
    }

    override fun getDataTransferNotification(notificationId: Int): Any? {
        return null
    }

    override fun cancelFileNotification(notificationId: Int) {
        val identifier = "transfer_$notificationId"
        notificationCenter.removeDeliveredNotificationsWithIdentifiers(listOf(identifier))
        Log.d(TAG, "Cancelled file notification: $notificationId")
    }

    // ==================== Location Notifications ====================

    override fun showLocationNotification(
        account: Account,
        contact: Contact,
        conversation: Conversation
    ) {
        val identifier = "location_${contact.uri.uri}"

        val content = UNMutableNotificationContent().apply {
            setTitle(runBlocking { getString(Res.string.notif_location_title, contact.displayName.orEmpty()) })
            setSound(null)
        }

        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = identifier,
            content = content,
            trigger = null
        )

        notificationCenter.addNotificationRequest(request) { error ->
            if (error != null) {
                Log.e(TAG, "Failed to show location notification: ${error.localizedDescription}")
            } else {
                Log.d(TAG, "Showing location notification for contact: ${contact.uri.uri}")
            }
        }
    }

    override fun cancelLocationNotification(account: Account, contact: Contact) {
        val identifier = "location_${contact.uri.uri}"
        notificationCenter.removeDeliveredNotificationsWithIdentifiers(listOf(identifier))
        Log.d(TAG, "Cancelled location notification for contact: ${contact.uri.uri}")
    }

    // ==================== Service Notification ====================

    override val serviceNotification: Any
        get() = Unit // iOS doesn't have foreground service notifications

    override fun onConnectionUpdate(connected: Boolean) {
        // iOS doesn't need a persistent service notification
        Log.d(TAG, "Connection status updated: connected=$connected")
    }

    // ==================== Push Notifications ====================

    override fun processPush() {
        Log.d(TAG, "Processing push notification")
        // Push notifications are handled by AppDelegate/NotificationService extension
    }

    override fun testPushNotification(accountId: String) {
        val identifier = "test_push"

        val content = UNMutableNotificationContent().apply {
            setTitle("Push Test")
            setBody("Push notification working for account: $accountId")
            setSound(UNNotificationSound.defaultSound())
        }

        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = identifier,
            content = content,
            trigger = null
        )

        notificationCenter.addNotificationRequest(request) { error ->
            if (error != null) {
                Log.e(TAG, "Failed to show test push notification: ${error.localizedDescription}")
            } else {
                Log.d(TAG, "Test push notification shown for account: $accountId")
            }
        }
    }

    companion object {
        private const val TAG = "IOSNotificationService"

        // Categories
        const val CATEGORY_CALL = "JAMI_CALL"
        const val CATEGORY_MESSAGE = "JAMI_MESSAGE"
        const val CATEGORY_REQUEST = "JAMI_REQUEST"

    }
}
