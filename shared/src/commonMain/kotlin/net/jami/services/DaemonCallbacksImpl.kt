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

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import net.jami.model.SwarmMessage
import net.jami.utils.Log

/**
 * Implementation of DaemonCallbacks that routes daemon events to the appropriate services.
 *
 * This implementation uses Coroutine Channels to bridge native callbacks to Kotlin services.
 * Channels are used instead of Flows to ensure that high-volume events (like message sync)
 * are never dropped and are processed sequentially.
 */
class DaemonCallbacksImpl(
    private val accountService: AccountService,
    private val callService: CallService,
    private val contactService: ContactService,
    private val conversationFacade: ConversationFacade,
    private val scope: CoroutineScope
) : DaemonCallbacks {

    companion object {
        private const val TAG = "DaemonCallbacks"
    }

    // ==================== Event Channels ====================
    // We use Channels to ensure every event is delivered exactly once and in order.
    //
    // Conversation events are keyed by (accountId, conversationId): each conversation gets its own
    // FIFO channel + consumer coroutine, so a slow SwarmLoaded for conversation A no longer
    // head-of-line-blocks a MessageReceived for conversation B, while ordering *within* a
    // conversation is still guaranteed. A Removed event tears its key down after processing; the
    // next event for that key starts a fresh generation. Mirrors libjamiclient's
    // ConversationCallbackDispatcher (per-(account,conversation) concatMap + generation routes).
    private val accountTasks = Channel<AccountTask>(Channel.UNLIMITED)

    private data class ConversationKey(val accountId: String, val conversationId: String)

    private inner class ConversationRoute(val generation: Long) {
        val channel = Channel<ConversationTask>(Channel.UNLIMITED)
        var closing = false
    }

    private val routingLock = SynchronizedObject()
    private val routes = HashMap<ConversationKey, ConversationRoute>()
    private var nextGeneration = 0L

    private fun ConversationTask.conversationKey(): ConversationKey = when (this) {
        is ConversationTask.SwarmLoaded -> ConversationKey(accountId, conversationId)
        is ConversationTask.MessageReceived -> ConversationKey(accountId, conversationId)
        is ConversationTask.MessageUpdated -> ConversationKey(accountId, conversationId)
        is ConversationTask.DataTransfer -> ConversationKey(accountId, conversationId)
        is ConversationTask.Ready -> ConversationKey(accountId, conversationId)
        is ConversationTask.Removed -> ConversationKey(accountId, conversationId)
        is ConversationTask.RequestReceived -> ConversationKey(accountId, conversationId)
        is ConversationTask.MemberEvent -> ConversationKey(accountId, conversationId)
    }

    /** Route a conversation event onto its per-conversation FIFO channel, spawning the consumer
     *  (a fresh generation) on first use for that key. */
    private fun enqueueConversationTask(task: ConversationTask) {
        val key = task.conversationKey()
        val route = synchronized(routingLock) {
            routes.getOrPut(key) {
                ConversationRoute(nextGeneration++).also { startConversationConsumer(key, it) }
            }
        }
        if (route.channel.trySend(task).isFailure) {
            Log.w(TAG, "Dropped ${task::class.simpleName} for $key (route closed)")
        }
    }

    private fun startConversationConsumer(key: ConversationKey, route: ConversationRoute) {
        scope.launch {
            for (task in route.channel) {
                try {
                    processConversationTask(task)
                } catch (e: Exception) {
                    Log.e(TAG, "conversation task ${task::class.simpleName} failed for $key", e)
                }
                if (task is ConversationTask.Removed && !route.closing) {
                    // Drain-then-close: everything queued before Removed has run; drop this key so a
                    // later event for the same conversation starts a new generation.
                    route.closing = true
                    synchronized(routingLock) { if (routes[key] === route) routes.remove(key) }
                    route.channel.close()
                }
            }
        }
    }

    private suspend fun processConversationTask(task: ConversationTask) {
        when (task) {
            is ConversationTask.SwarmLoaded -> {
                // 1. Populate the UI model (Wait for completion)
                conversationFacade.onSwarmLoaded(task.id, task.accountId, task.conversationId, task.messages)
                // 2. Resolve loading tasks and update cursor
                accountService.resolveSwarmLoaded(task.id, task.accountId, task.conversationId, task.messages)
            }
            is ConversationTask.MessageReceived ->
                conversationFacade.onMessageReceived(task.accountId, task.conversationId, task.message)
            is ConversationTask.MessageUpdated ->
                conversationFacade.onMessageUpdated(task.accountId, task.conversationId, task.message)
            is ConversationTask.DataTransfer ->
                conversationFacade.onDataTransferEvent(task.accountId, task.conversationId, task.interactionId, task.fileId, task.eventCode)
            is ConversationTask.Ready ->
                conversationFacade.onConversationReady(task.accountId, task.conversationId)
            is ConversationTask.Removed ->
                conversationFacade.onConversationRemoved(task.accountId, task.conversationId)
            is ConversationTask.RequestReceived ->
                conversationFacade.onConversationRequestReceived(task.accountId, task.conversationId, task.metadata)
            is ConversationTask.MemberEvent ->
                conversationFacade.onConversationMemberEvent(task.accountId, task.conversationId, task.memberId, task.event)
        }
    }

    sealed class ConversationTask {
        data class SwarmLoaded(val id: Long, val accountId: String, val conversationId: String, val messages: List<SwarmMessage>) : ConversationTask()
        data class MessageReceived(val accountId: String, val conversationId: String, val message: SwarmMessage) : ConversationTask()
        data class MessageUpdated(val accountId: String, val conversationId: String, val message: SwarmMessage) : ConversationTask()
        data class DataTransfer(val accountId: String, val conversationId: String, val interactionId: String, val fileId: String, val eventCode: Int) : ConversationTask()
        data class Ready(val accountId: String, val conversationId: String) : ConversationTask()
        data class Removed(val accountId: String, val conversationId: String) : ConversationTask()
        data class RequestReceived(val accountId: String, val conversationId: String, val metadata: Map<String, String>) : ConversationTask()
        data class MemberEvent(val accountId: String, val conversationId: String, val memberId: String, val event: Int) : ConversationTask()
    }

    sealed class AccountTask {
        data class ProfileReceived(val accountId: String, val name: String, val photo: String) : AccountTask()
        data object AccountsChanged : AccountTask()
        data class RegistrationChanged(val accountId: String, val state: String, val code: Int, val detail: String) : AccountTask()
    }

    init {
        // Conversation events are processed per-conversation by startConversationConsumer(),
        // spawned lazily in enqueueConversationTask().

        // Account Event Processor
        scope.launch {
            for (task in accountTasks) {
                when (task) {
                    is AccountTask.ProfileReceived -> accountService.onAccountProfileReceived(task.accountId, task.name, task.photo)
                    is AccountTask.AccountsChanged -> {
                        Log.d(TAG, "processing AccountsChanged task")
                        accountService.onAccountsChanged()
                    }
                    is AccountTask.RegistrationChanged -> accountService.onRegistrationStateChanged(task.accountId, task.state, task.code, task.detail)
                }
            }
        }
    }

    // ==================== Account Callbacks ====================

    override fun onAccountsChanged() {
        Log.d(TAG, "onAccountsChanged: queuing task")
        accountTasks.trySend(AccountTask.AccountsChanged)
    }

    override fun onAccountDetailsChanged(accountId: String, details: Map<String, String>) {
        scope.launch { accountService.onAccountDetailsChanged(accountId, details) }
    }

    override fun onRegistrationStateChanged(accountId: String, state: String, code: Int, detail: String) {
        accountTasks.trySend(AccountTask.RegistrationChanged(accountId, state, code, detail))
    }

    override fun onVolatileAccountDetailsChanged(accountId: String, details: Map<String, String>) {
        scope.launch { accountService.onVolatileAccountDetailsChanged(accountId, details) }
    }

    override fun onKnownDevicesChanged(accountId: String, devices: Map<String, String>) {
        scope.launch { accountService.onKnownDevicesChanged(accountId, devices) }
    }

    override fun onDeviceRevocationEnded(accountId: String, deviceId: String, state: Int) {
        scope.launch { accountService.onDeviceRevocationEnded(accountId, deviceId, state) }
    }

    override fun onAddDeviceStateChanged(accountId: String, opId: Long, state: Int, details: Map<String, String>) {
        scope.launch { accountService.onAddDeviceStateChanged(accountId, opId, state, details) }
    }

    override fun onMigrationEnded(accountId: String, state: String) {
        scope.launch { accountService.onMigrationEnded(accountId, state) }
    }

    override fun onAccountProfileReceived(accountId: String, name: String, photo: String) {
        accountTasks.trySend(AccountTask.ProfileReceived(accountId, name, photo))
    }

    // ==================== Call Callbacks ====================

    override fun onCallStateChanged(accountId: String, callId: String, state: String, code: Int) {
        scope.launch { callService.onCallStateChanged(accountId, callId, state, code) }
    }

    override fun onIncomingCall(accountId: String, callId: String, from: String) {
        scope.launch { callService.onIncomingCall(accountId, callId, from, emptyList()) }
    }

    override fun onIncomingCallWithMedia(accountId: String, callId: String, from: String, mediaList: List<Map<String, String>>) {
        scope.launch { callService.onIncomingCall(accountId, callId, from, mediaList) }
    }

    override fun onMediaChangeRequested(accountId: String, callId: String, mediaList: List<Map<String, String>>) {
        scope.launch { callService.mediaChangeRequested(accountId, callId, mediaList) }
    }

    override fun onAudioMuted(callId: String, muted: Boolean) {
        scope.launch { callService.onAudioMuted(callId, muted) }
    }

    override fun onVideoMuted(callId: String, muted: Boolean) {
        scope.launch { callService.onVideoMuted(callId, muted) }
    }

    override fun onRecordingStateChanged(callId: String, recording: Boolean) {
        scope.launch { callService.onRecordingStateChanged(callId, recording) }
    }

    override fun onMediaNegotiationStatus(callId: String, event: String, mediaList: List<Map<String, String>>) {
        scope.launch { callService.onMediaNegotiationStatus(callId, event, mediaList) }
    }

    override fun onConferenceCreated(accountId: String, conversationId: String, confId: String) {
        scope.launch { callService.onConferenceCreated(accountId, conversationId, confId) }
    }

    override fun onConferenceChanged(accountId: String, confId: String, state: String) {
        scope.launch { callService.onConferenceChanged(accountId, confId, state) }
    }

    override fun onConferenceRemoved(accountId: String, confId: String) {
        scope.launch { callService.onConferenceRemoved(accountId, confId) }
    }

    override fun onConferenceInfoUpdated(confId: String, info: List<Map<String, String>>) {
        scope.launch { callService.onConferenceInfoUpdated(confId, info) }
    }

    // ==================== Conversation Callbacks ====================

    override fun onConversationReady(accountId: String, conversationId: String) {
        enqueueConversationTask(ConversationTask.Ready(accountId, conversationId))
    }

    override fun onConversationRemoved(accountId: String, conversationId: String) {
        enqueueConversationTask(ConversationTask.Removed(accountId, conversationId))
    }

    override fun onConversationRequestReceived(accountId: String, conversationId: String, metadata: Map<String, String>) {
        enqueueConversationTask(ConversationTask.RequestReceived(accountId, conversationId, metadata))
    }

    override fun onConversationRequestDeclined(accountId: String, conversationId: String) {
        scope.launch { conversationFacade.onConversationRequestDeclined(accountId, conversationId) }
    }

    override fun onConversationMemberEvent(accountId: String, conversationId: String, memberId: String, event: Int) {
        enqueueConversationTask(ConversationTask.MemberEvent(accountId, conversationId, memberId, event))
    }

    override fun onMessageReceived(accountId: String, conversationId: String, message: SwarmMessage) {
        enqueueConversationTask(ConversationTask.MessageReceived(accountId, conversationId, message))
    }

    override fun onMessageUpdated(accountId: String, conversationId: String, message: SwarmMessage) {
        enqueueConversationTask(ConversationTask.MessageUpdated(accountId, conversationId, message))
    }

    override fun onMessagesFound(messageId: Int, accountId: String, conversationId: String, messages: List<Map<String, String>>) {
        scope.launch { conversationFacade.onMessagesFound(messageId, accountId, conversationId, messages) }
    }

    override fun onSwarmLoaded(id: Long, accountId: String, conversationId: String, messages: List<SwarmMessage>) {
        enqueueConversationTask(ConversationTask.SwarmLoaded(id, accountId, conversationId, messages))
    }

    override fun onConversationProfileUpdated(accountId: String, conversationId: String, profile: Map<String, String>) {
        scope.launch { conversationFacade.onConversationProfileUpdated(accountId, conversationId, profile) }
    }

    override fun onConversationPreferencesUpdated(accountId: String, conversationId: String, preferences: Map<String, String>) {
        scope.launch { conversationFacade.onConversationPreferencesUpdated(accountId, conversationId, preferences) }
    }

    override fun onReactionAdded(accountId: String, conversationId: String, messageId: String, reaction: Map<String, String>) {
        scope.launch { conversationFacade.onReactionAdded(accountId, conversationId, messageId, reaction) }
    }

    override fun onReactionRemoved(accountId: String, conversationId: String, messageId: String, reactionId: String) {
        scope.launch { conversationFacade.onReactionRemoved(accountId, conversationId, messageId, reactionId) }
    }

    override fun onActiveCallsChanged(accountId: String, conversationId: String, activeCalls: List<Map<String, String>>) {
        scope.launch { conversationFacade.onActiveCallsChanged(accountId, conversationId, activeCalls) }
    }

    // ==================== Presence Callbacks ====================

    override fun onNewBuddyNotification(accountId: String, buddyUri: String, status: Int, lineStatus: String) {
        scope.launch { contactService.onPresenceUpdate(accountId, buddyUri, status) }
    }

    // ==================== Contact Callbacks ====================

    override fun onContactAdded(accountId: String, uri: String, confirmed: Boolean) {
        scope.launch { accountService.onContactAdded(accountId, uri, confirmed) }
    }

    override fun onContactRemoved(accountId: String, uri: String, banned: Boolean) {
        scope.launch { accountService.onContactRemoved(accountId, uri, banned) }
    }

    override fun onIncomingTrustRequest(accountId: String, conversationId: String, from: String, payload: ByteArray, receiveTime: Long) {
        scope.launch { accountService.onIncomingTrustRequest(accountId, conversationId, from, payload, receiveTime) }
    }

    override fun onProfileReceived(accountId: String, peerId: String, vcardPath: String) {
        scope.launch { contactService.onProfileReceived(accountId, peerId, vcardPath) }
    }

    // ==================== Message Callbacks ====================

    override fun onIncomingAccountMessage(accountId: String, messageId: String?, callId: String?, from: String, messages: Map<String, String>) {
        scope.launch { accountService.onIncomingAccountMessage(accountId, messageId, callId, from, messages) }
    }

    override fun onAccountMessageStatusChanged(accountId: String, conversationId: String, messageId: String, contactId: String, status: Int) {
        scope.launch { conversationFacade.onAccountMessageStatusChanged(accountId, conversationId, messageId, contactId, status) }
    }

    override fun onComposingStatusChanged(accountId: String, conversationId: String, contactUri: String, status: Int) {
        scope.launch { conversationFacade.onComposingStatusChanged(accountId, conversationId, contactUri, status) }
    }

    // ==================== Name Service Callbacks ====================

    override fun onNameRegistrationEnded(accountId: String, state: Int, name: String) {
        scope.launch { accountService.onNameRegistrationEnded(accountId, state, name) }
    }

    override fun onRegisteredNameFound(accountId: String, state: Int, address: String, name: String, query: String) {
        scope.launch { accountService.onRegisteredNameFound(accountId, state, address, name, query) }
    }

    override fun onUserSearchEnded(accountId: String, state: Int, query: String, results: List<Map<String, String>>) {
        scope.launch { accountService.onUserSearchEnded(accountId, state, query, results) }
    }

    // ==================== Data Transfer Callbacks ====================

    override fun onDataTransferEvent(accountId: String, conversationId: String, interactionId: String, fileId: String, eventCode: Int) {
        enqueueConversationTask(ConversationTask.DataTransfer(accountId, conversationId, interactionId, fileId, eventCode))
    }
}
