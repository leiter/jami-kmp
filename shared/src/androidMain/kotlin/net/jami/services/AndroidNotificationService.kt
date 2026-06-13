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
import android.media.AudioAttributes
import android.net.Uri as AndroidUri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import jami_kmp.shared.generated.resources.Res
import jami_kmp.shared.generated.resources.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.jami.model.*
import net.jami.repository.SettingsRepository
import net.jami.ui.platform.LocalPrefKeys
import net.jami.ui.platform.LocalPrefs
import net.jami.utils.Log
import org.jetbrains.compose.resources.getString

// Returns the best available human-readable name for a call peer.
// Priority: contact displayName → registered username → first 12 chars of Jami hash.
private fun Call.getBestName(): String {
    val c = contact
    if (c != null) {
        return c.displayName?.takeIf { it.isNotBlank() }
            ?: c.username?.takeIf { it.isNotBlank() }
            ?: peerUri.rawRingId.take(12).ifEmpty { peerUri.uri }
    }
    return peerUri.rawRingId.take(12).ifEmpty { peerUri.uri }
}

private fun Conference.getBestName(): String = firstCall?.getBestName() ?: id

private fun Call.getDaemonIdString(): String =
    daemonId ?: ""

private fun Conversation.getBestName(): String =
    contact?.let { c ->
        c.displayName?.takeIf { it.isNotBlank() }
            ?: c.username?.takeIf { it.isNotBlank() }
    } ?: profileFlow.value.displayName?.takeIf { it.isNotEmpty() }
    ?: uri.rawRingId.take(12).ifEmpty { uri.uri }

private fun Conversation.getLastMessage(): String? =
    lastEventFlow.value?.body

/**
 * Android implementation of NotificationService.
 *
 * Uses Android NotificationManager and NotificationCompat for system notifications.
 * Supports notification channels (Android 8.0+), actions, and progress updates.
 * Enforces NotificationSettings via NotificationGuard.
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
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val notificationGuard: NotificationGuard,
    private val telecomManager: JamiTelecomManager,
) : NotificationService {

    private val notificationManager: NotificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    // Resolve the app's own notification icon by name so shared module doesn't need
    // a compile-time dependency on android-app's R class.
    private val smallIcon: Int by lazy {
        context.resources.getIdentifier("ic_jami_24", "drawable", context.packageName)
            .takeIf { it != 0 } ?: android.R.drawable.ic_dialog_info
    }

    // Track active notifications
    private val activeCallNotifications = mutableSetOf<Int>()
    private val selfUser: Person = Person.Builder().setName("You").build()
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
                    runBlocking { getString(Res.string.notif_channel_incoming_calls) },
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    setShowBadge(true)
                    enableVibration(true)
                    setBypassDnd(true)
                },
                // v2: recreated at IMPORTANCE_HIGH (was IMPORTANCE_DEFAULT — no heads-up banners)
                NotificationChannel(
                    CHANNEL_MESSAGES,
                    runBlocking { getString(Res.string.notif_channel_messages) },
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    setShowBadge(true)
                    enableVibration(true)
                },
                NotificationChannel(
                    CHANNEL_FILE_TRANSFER,
                    runBlocking { getString(Res.string.notif_channel_file_transfer) },
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    setShowBadge(false)
                    enableVibration(false)
                },
                // v2: recreated at IMPORTANCE_LOW (was IMPORTANCE_MIN — no status bar icon)
                NotificationChannel(
                    CHANNEL_SERVICE,
                    runBlocking { getString(Res.string.notif_channel_background_service) },
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = runBlocking { getString(Res.string.notif_channel_background_service_descr) }
                    setShowBadge(false)
                    enableVibration(false)
                },
                NotificationChannel(
                    CHANNEL_REQUESTS,
                    runBlocking { getString(Res.string.notif_channel_requests) },
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    setShowBadge(true)
                    enableVibration(true)
                }
            )
            notificationManager.createNotificationChannels(channels)
        }
    }

    // ==================== Call Notifications ====================

    /**
     * Delete and recreate [CHANNEL_CALLS] with a new ringtone URI when the setting changes.
     * Channel sound can only be set at creation time on Android O+, so we delete + recreate.
     * The last applied ringtone is persisted in [LocalPrefs] to avoid redundant channel churn.
     */
    private fun refreshCallsChannel(ringtoneUri: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val lastApplied = LocalPrefs.getString(LocalPrefKeys.LAST_APPLIED_RINGTONE, "")
        if (ringtoneUri == lastApplied) return
        notificationManager.deleteNotificationChannel(CHANNEL_CALLS)
        val channel = NotificationChannel(
            CHANNEL_CALLS,
            runBlocking { getString(Res.string.notif_channel_incoming_calls) },
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            setShowBadge(true)
            enableVibration(true)
            setBypassDnd(true)
            if (ringtoneUri.isNotBlank()) {
                val sound = AndroidUri.parse(ringtoneUri)
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                setSound(sound, attrs)
            }
        }
        notificationManager.createNotificationChannel(channel)
        LocalPrefs.setString(LocalPrefKeys.LAST_APPLIED_RINGTONE, ringtoneUri)
    }

    override fun showCallNotification(notifId: Int): Any? {
        // Check settings before showing
        if (!notificationGuard.shouldShowCallNotification()) {
            Log.d(TAG, "Call notifications disabled in settings")
            return null
        }

        // Recreate the calls channel if the ringtone setting has changed
        val currentRingtone = runBlocking { settingsRepository.callSettings.first().ringtone }
        refreshCallsChannel(currentRingtone)

        currentCallNotificationId = notifId
        activeCallNotifications.add(notifId)

        // Create a simple ongoing call notification
        // In a full implementation, this would include call actions and caller info
        val builder = NotificationCompat.Builder(context, CHANNEL_CALLS)
            .setSmallIcon(smallIcon)
            .setContentTitle(runBlocking { getString(Res.string.notif_current_call) })
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(false)

        // Apply sound/vibration settings
        applySoundAndVibration(builder)

        val notification = builder.build()
        notificationManager.notify(notifId, notification)
        Log.d(TAG, "Showing call notification: $notifId")
        return notification
    }

    override fun cancelCallNotification() {
        // Cancel every notification ID that was ever shown for a call.
        // Two paths write to activeCallNotifications:
        //   1. handleCallNotification → conference.id.hashCode()
        //   2. showCallNotification (CallNotificationService) → NOTIF_CALL_BASE (1000)
        // currentCallNotificationId only holds the *last* one written, so cancelling
        // only that ID leaves the other notification visible.
        activeCallNotifications.forEach { notifId ->
            notificationManager.cancel(notifId)
            Log.d(TAG, "Cancelled call notification: $notifId")
        }
        activeCallNotifications.clear()
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

        val notifId = conference.id.hashCode()
        currentCallNotificationId = notifId
        activeCallNotifications.add(notifId)

        val state = conference.state
        val callId = conference.firstCall?.daemonId ?: conference.id
        val accountId = conference.accountId
        val isIncoming = conference.firstCall?.isIncoming ?: false
        val peerName = conference.getBestName()

        // Title carries the peer name; content text is the call type label.
        // Mirrors NotificationServiceImpl: notif_*_call_title (%s) + notif_*_call.
        val title = when {
            state == Call.CallStatus.RINGING && isIncoming ->
                getString(Res.string.notif_incoming_call_title, peerName)
            state == Call.CallStatus.RINGING ->
                getString(Res.string.notif_outgoing_call_title, peerName)
            state == Call.CallStatus.CURRENT || state == Call.CallStatus.HOLD ->
                getString(Res.string.notif_current_call_title, peerName)
            else -> peerName
        }
        val text = when {
            state == Call.CallStatus.RINGING && isIncoming ->
                getString(Res.string.notif_incoming_call)
            state == Call.CallStatus.RINGING ->
                getString(Res.string.notif_outgoing_call)
            state == Call.CallStatus.CURRENT || state == Call.CallStatus.HOLD ->
                getString(Res.string.notif_current_call)
            else -> ""
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_CALLS)
            .setSmallIcon(smallIcon)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(false)

        val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

        // Keep the foreground service alive for every active call state.
        try {
            val serviceIntent = Intent(context, Class.forName("net.jami.android.service.CallNotificationService"))
            androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: Exception) {
            Log.w(TAG, "Could not start CallNotificationService: ${e.message}")
        }

        val answerLabel  = getString(Res.string.action_call_accept)
        val declineLabel = getString(Res.string.action_call_decline)
        val hangUpLabel  = getString(Res.string.action_call_hangup)

        when {
            state == Call.CallStatus.RINGING && isIncoming -> {
                val fullScreenIntent = Intent(context, Class.forName("net.jami.android.MainActivity")).apply {
                    action = ACTION_VIEW_CALL
                    putExtra(KEY_CALL_ID, callId)
                    putExtra(KEY_ACCOUNT_ID, accountId)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                builder.setFullScreenIntent(
                    PendingIntent.getActivity(context, 100, fullScreenIntent, piFlags), true
                )
                builder.addAction(
                    smallIcon, answerLabel,
                    createCallActionPendingIntent(callId, accountId, ACTION_ANSWER, 101)
                )
                builder.addAction(
                    smallIcon, declineLabel,
                    createCallActionPendingIntent(callId, accountId, ACTION_DECLINE, 102)
                )
            }

            state == Call.CallStatus.RINGING -> {
                val tapIntent = Intent(context, Class.forName("net.jami.android.MainActivity")).apply {
                    action = ACTION_VIEW_CALL
                    putExtra(KEY_CALL_ID, callId)
                    putExtra(KEY_ACCOUNT_ID, accountId)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                builder.setContentIntent(PendingIntent.getActivity(context, 104, tapIntent, piFlags))
                builder.addAction(
                    smallIcon, hangUpLabel,
                    createCallActionPendingIntent(callId, accountId, ACTION_HANGUP, 103)
                )
            }

            state == Call.CallStatus.CURRENT || state == Call.CallStatus.HOLD -> {
                val tapIntent = Intent(context, Class.forName("net.jami.android.MainActivity")).apply {
                    action = ACTION_VIEW_CALL
                    putExtra(KEY_CALL_ID, callId)
                    putExtra(KEY_ACCOUNT_ID, accountId)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                builder.setContentIntent(PendingIntent.getActivity(context, 104, tapIntent, piFlags))
                builder.addAction(
                    smallIcon, hangUpLabel,
                    createCallActionPendingIntent(callId, accountId, ACTION_HANGUP, 103)
                )
            }
        }

        // Apply sound/vibration settings
        applySoundAndVibration(builder)

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
        val title = runBlocking { getString(Res.string.notif_missed_incoming_call) }
        val peerName = call.getBestName()

        val notification = NotificationCompat.Builder(context, CHANNEL_CALLS)
            .setSmallIcon(smallIcon)
            .setContentTitle(title)
            .setContentText(peerName)
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

        val conversationName = conversation.getBestName()
        val contentText = runBlocking { getString(Res.string.notif_inprogress_group_call) }

        val notification = NotificationCompat.Builder(context, CHANNEL_CALLS)
            .setSmallIcon(smallIcon)
            .setContentTitle(conversationName)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notifId, notification)
        Log.d(TAG, "Showing group call notification: $notifId")
    }

    // ==================== Text Notifications ====================

    override fun showTextNotification(conversation: Conversation) {
        // Check settings before showing
        if (!notificationGuard.shouldShowMessageNotification()) {
            Log.d(TAG, "Message notifications disabled in settings")
            return
        }

        val conversationKey = "${conversation.accountId}:${conversation.uri.uri}"
        val notifId = activeTextNotifications.getOrPut(conversationKey) {
            NOTIF_MESSAGE_BASE + conversationKey.hashCode()
        }

        val lastMessage = conversation.getLastMessage() ?: return
        val senderName = conversation.getBestName()
        val groupKey = "jami_messages_$conversationKey"

        val notificationActionReceiverClass = try {
            Class.forName("net.jami.android.service.NotificationActionReceiver")
        } catch (e: ClassNotFoundException) { null }

        // Create pending intents for actions
        val replyIntent = if (notificationActionReceiverClass != null) {
            Intent(context, notificationActionReceiverClass)
        } else {
            Intent(ACTION_REPLY_MESSAGE).also { it.setPackage(context.packageName) }
        }.apply {
            action = ACTION_REPLY_MESSAGE
            putExtra(KEY_ACCOUNT_ID, conversation.accountId)
            putExtra(KEY_CONVERSATION_ID, conversation.uri.rawRingId)
            putExtra(KEY_NOTIFICATION_ID, notifId)
            setPackage(context.packageName)
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            context, notifId, replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val markReadIntent = if (notificationActionReceiverClass != null) {
            Intent(context, notificationActionReceiverClass)
        } else {
            Intent(ACTION_MARK_READ).also { it.setPackage(context.packageName) }
        }.apply {
            action = ACTION_MARK_READ
            putExtra(KEY_ACCOUNT_ID, conversation.accountId)
            putExtra(KEY_CONVERSATION_ID, conversation.uri.rawRingId)
            putExtra(KEY_NOTIFICATION_ID, notifId)
            setPackage(context.packageName)
        }
        val markReadPendingIntent = PendingIntent.getBroadcast(
            context, notifId + 1, markReadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Add RemoteInput for quick reply
        val replyLabel    = runBlocking { getString(Res.string.notif_reply) }
        val markReadLabel = runBlocking { getString(Res.string.notif_mark_as_read) }

        val remoteInput = androidx.core.app.RemoteInput.Builder(KEY_REPLY_TEXT)
            .setLabel(replyLabel)
            .build()

        val replyAction = NotificationCompat.Action.Builder(
            smallIcon, replyLabel, replyPendingIntent
        ).addRemoteInput(remoteInput).build()

        val markReadAction = NotificationCompat.Action.Builder(
            smallIcon, markReadLabel, markReadPendingIntent
        ).build()

        // Build the individual message notification
        val messagingStyle = NotificationCompat.MessagingStyle(selfUser)
            .setConversationTitle(senderName) // Or conversation.displayName for groups
            .addMessage(lastMessage, System.currentTimeMillis(), Person.Builder().setName(senderName).build())

        val builder = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(smallIcon)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setGroup(groupKey) // Assign to conversation group
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN) // Children notifications can alert
            .setStyle(messagingStyle)
            .addAction(replyAction)
            .addAction(markReadAction)

        // Apply sound/vibration settings
        applySoundAndVibration(builder)

        notificationManager.notify(notifId, builder.build())
        Log.d(TAG, "Showing individual text notification for conversation: $conversationKey")

        // Create and update the group summary notification
        val summaryNotificationId = NOTIF_MESSAGE_GROUP_SUMMARY_BASE + conversationKey.hashCode()
        val summaryBuilder = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(smallIcon)
            .setContentText(senderName)
            .setGroup(groupKey)
            .setGroupSummary(true) // This is the group summary
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setOnlyAlertOnce(true) // Only alert for the first message in the group

        notificationManager.notify(summaryNotificationId, summaryBuilder.build())
        Log.d(TAG, "Showing group summary notification for conversation: $conversationKey")
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
        // Check settings before showing
        if (!notificationGuard.shouldShowRequestNotification()) {
            Log.d(TAG, "Contact request notifications disabled in settings")
            return
        }

        val notifId = NOTIF_TRUST_REQUEST_BASE + account.accountId.hashCode()

        val builder = NotificationCompat.Builder(context, CHANNEL_REQUESTS)
            .setSmallIcon(smallIcon)
            .setContentTitle(runBlocking { getString(Res.string.new_invitation_request_title) })
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setAutoCancel(true)

        // Apply sound/vibration settings
        applySoundAndVibration(builder)

        notificationManager.notify(notifId, builder.build())
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
            .setSmallIcon(smallIcon)
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
            .setSmallIcon(smallIcon)
            .setContentTitle(runBlocking { getString(Res.string.notif_location_title, contact.displayName ?: contact.uri.uri) })
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
            .setSmallIcon(smallIcon)
            .setContentText(runBlocking { getString(Res.string.notif_background_service) })
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
    }

    override fun onConnectionUpdate(connected: Boolean) {
        val notification = NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(smallIcon)
            .setContentText(runBlocking { getString(Res.string.notif_background_service) })
            .setPriority(NotificationCompat.PRIORITY_LOW)
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
            .setSmallIcon(smallIcon)
            .setContentTitle("Push Test")
            .setContentText("Push notification working for account: $accountId")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notifId, notification)
        Log.d(TAG, "Test push notification shown for account: $accountId")
    }

    // ==================== Private Helpers ====================

    /**
     * Apply sound and vibration settings to notification builder.
     * Respects user preferences from NotificationSettings.
     */
    private fun applySoundAndVibration(builder: NotificationCompat.Builder) {
        // Apply custom sound URI if set
        val soundUri = notificationGuard.getSoundUri()
        if (soundUri != null) {
            builder.setSound(android.net.Uri.parse(soundUri))
        }

        // Apply vibration if enabled
        if (notificationGuard.shouldVibrate()) {
            builder.setVibrate(longArrayOf(0, 250, 250, 250))
        }

        // LED color is handled differently - it's set on notification directly, not builder
        // Android handles LED through notification channels for API 26+
    }

    private fun createCallActionPendingIntent(
        callId: String,
        accountId: String,
        action: String,
        requestCode: Int
    ): PendingIntent? {
        val receiverClass = try {
            Class.forName("net.jami.android.service.CallActionReceiver")
        } catch (e: Exception) {
            null
        } ?: return null

        val intent = Intent(context, receiverClass).apply {
            this.action = action
            putExtra(NotificationService.KEY_CALL_ID, callId)
            putExtra(NotificationService.KEY_ACCOUNT_ID, accountId)
            setPackage(context.packageName)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }

    companion object {
        private const val TAG = "AndroidNotificationService"

        // Notification channels
        // _v2 suffix forces channel recreation when importance level changed
        const val CHANNEL_CALLS = "jami_calls"
        const val CHANNEL_MESSAGES = "jami_messages_v2"   // was DEFAULT, now HIGH
        const val CHANNEL_FILE_TRANSFER = "jami_file_transfer"
        const val CHANNEL_SERVICE = "jami_service_v2"     // was MIN, now LOW
        const val CHANNEL_REQUESTS = "jami_requests"

        // Notification ID bases
        const val NOTIF_SERVICE = 1
        const val NOTIF_CALL_BASE = 1000
        const val NOTIF_MISSED_CALL_BASE = 2000
        const val NOTIF_GROUP_CALL_BASE = 3000
        const val NOTIF_MESSAGE_BASE = 4000
        const val NOTIF_MESSAGE_GROUP_SUMMARY_BASE = 4001
        const val NOTIF_TRUST_REQUEST_BASE = 5000
        const val NOTIF_FILE_TRANSFER_BASE = 6000
        const val NOTIF_LOCATION_BASE = 7000
        const val NOTIF_TEST_PUSH = 9999

        // Intent actions
        const val ACTION_ANSWER = "net.jami.action.ANSWER_CALL"
        const val ACTION_DECLINE = "net.jami.action.DECLINE_CALL"
        const val ACTION_HANGUP = "net.jami.action.HANGUP_CALL"
        const val ACTION_VIEW_CALL = "net.jami.action.VIEW_CALL"
        const val ACTION_REPLY_MESSAGE = "net.jami.action.REPLY_MESSAGE"
        const val ACTION_MARK_READ = "net.jami.action.MARK_READ"

        // Intent extras
        const val KEY_CALL_ID = NotificationService.KEY_CALL_ID
        const val KEY_ACCOUNT_ID = "accountId"
        const val KEY_CONVERSATION_ID = "conversationId"
        const val KEY_REPLY_TEXT = "reply_text"
        const val KEY_NOTIFICATION_ID = "notifId"
    }
}
