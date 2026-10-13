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

import net.jami.model.SwarmMessage

/**
 * A [DaemonCallbacks] that drops everything, for tests that need to pass callbacks into the
 * daemon bridge without wiring up the whole service graph.
 *
 * Generated from the interface; when DaemonCallbacks gains a method the compiler will point here.
 */
object NoOpDaemonCallbacks : DaemonCallbacks {
    override fun onAccountsChanged() {}
    override fun onAccountDetailsChanged(accountId: String, details: Map<String, String>) {}
    override fun onRegistrationStateChanged(accountId: String, state: String, code: Int, detail: String) {}
    override fun onVolatileAccountDetailsChanged(accountId: String, details: Map<String, String>) {}
    override fun onKnownDevicesChanged(accountId: String, devices: Map<String, String>) {}
    override fun onDeviceRevocationEnded(accountId: String, deviceId: String, state: Int) {}
    override fun onAddDeviceStateChanged(accountId: String, opId: Long, state: Int, details: Map<String, String>) {}
    override fun onMigrationEnded(accountId: String, state: String) {}
    override fun onAccountProfileReceived(accountId: String, name: String, photo: String) {}
    override fun onCallStateChanged(accountId: String, callId: String, state: String, code: Int) {}
    override fun onIncomingCall(accountId: String, callId: String, from: String) {}
    override fun onIncomingCallWithMedia(accountId: String, callId: String, from: String, mediaList: List<Map<String, String>>) {}
    override fun onMediaChangeRequested(accountId: String, callId: String, mediaList: List<Map<String, String>>) {}
    override fun onAudioMuted(callId: String, muted: Boolean) {}
    override fun onVideoMuted(callId: String, muted: Boolean) {}
    override fun onRecordingStateChanged(callId: String, recording: Boolean) {}
    override fun onMediaNegotiationStatus(callId: String, event: String, mediaList: List<Map<String, String>>) {}
    override fun onPeerHold(callId: String, holding: Boolean) {}
    override fun onConnectionUpdate(id: String, state: Int) {}
    override fun onRemoteRecordingChanged(callId: String, peerUri: String, recording: Boolean) {}
    override fun onConferenceCreated(accountId: String, conversationId: String, confId: String) {}
    override fun onConferenceChanged(accountId: String, confId: String, state: String) {}
    override fun onConferenceRemoved(accountId: String, confId: String) {}
    override fun onConferenceInfoUpdated(confId: String, info: List<Map<String, String>>) {}
    override fun onConversationReady(accountId: String, conversationId: String) {}
    override fun onConversationRemoved(accountId: String, conversationId: String) {}
    override fun onConversationRequestReceived(accountId: String, conversationId: String, metadata: Map<String, String>) {}
    override fun onConversationRequestDeclined(accountId: String, conversationId: String) {}
    override fun onConversationMemberEvent(accountId: String, conversationId: String, memberId: String, event: Int) {}
    override fun onMessageReceived(accountId: String, conversationId: String, message: SwarmMessage) {}
    override fun onMessageUpdated(accountId: String, conversationId: String, message: SwarmMessage) {}
    override fun onMessagesFound(messageId: Int, accountId: String, conversationId: String, messages: List<Map<String, String>>) {}
    override fun onSwarmLoaded(id: Long, accountId: String, conversationId: String, messages: List<SwarmMessage>) {}
    override fun onConversationProfileUpdated(accountId: String, conversationId: String, profile: Map<String, String>) {}
    override fun onConversationPreferencesUpdated(accountId: String, conversationId: String, preferences: Map<String, String>) {}
    override fun onReactionAdded(accountId: String, conversationId: String, messageId: String, reaction: Map<String, String>) {}
    override fun onReactionRemoved(accountId: String, conversationId: String, messageId: String, reactionId: String) {}
    override fun onActiveCallsChanged(accountId: String, conversationId: String, activeCalls: List<Map<String, String>>) {}
    override fun onConversationError(accountId: String, conversationId: String, code: Long, message: String) {}
    override fun onNewBuddyNotification(accountId: String, buddyUri: String, status: Int, lineStatus: String) {}
    override fun onNewServerSubscriptionRequest(remoteUri: String) {}
    override fun onPresenceServerError(accountId: String, error: String, message: String) {}
    override fun onPresenceSubscriptionStateChanged(accountId: String, buddyUri: String, state: Int) {}
    override fun onNearbyPeerNotification(accountId: String, peerId: String, state: Int, displayName: String) {}
    override fun onContactAdded(accountId: String, uri: String, confirmed: Boolean) {}
    override fun onContactRemoved(accountId: String, uri: String, banned: Boolean) {}
    override fun onIncomingTrustRequest(accountId: String, conversationId: String, from: String, payload: ByteArray, receiveTime: Long) {}
    override fun onProfileReceived(accountId: String, peerId: String, vcardPath: String) {}
    override fun onIncomingAccountMessage(accountId: String, messageId: String?, callId: String?, from: String, messages: Map<String, String>) {}
    override fun onAccountMessageStatusChanged(accountId: String, conversationId: String, messageId: String, contactId: String, status: Int) {}
    override fun onComposingStatusChanged(accountId: String, conversationId: String, contactUri: String, status: Int) {}
    override fun onNameRegistrationEnded(accountId: String, state: Int, name: String) {}
    override fun onRegisteredNameFound(accountId: String, state: Int, address: String, name: String, query: String) {}
    override fun onUserSearchEnded(accountId: String, state: Int, query: String, results: List<Map<String, String>>) {}
    override fun onDataTransferEvent(accountId: String, conversationId: String, interactionId: String, fileId: String, eventCode: Int) {}
    override fun onInCallMessage(accountId: String, callId: String, from: String, messages: Map<String, String>) {}
    override fun onPeerServicesReceived(requestId: Long, accountId: String, peerId: String, status: Int, servicesJson: String) {}
    override fun onServiceTunnelOpened(accountId: String, tunnelId: String, localPort: Int) {}
    override fun onServiceTunnelClosed(accountId: String, tunnelId: String, reason: String) {}
    override fun onCertificatePinned(certId: String) {}
    override fun onCertificatePathPinned(path: String, certIds: List<String>) {}
    override fun onCertificateExpired(certId: String) {}
    override fun onCertificateStateChanged(accountId: String, certId: String, state: String) {}
    override fun onErrorAlert(alert: Int) {}
    override fun onVolumeChanged(device: String, value: Int) {}
    override fun onStunStatusFailure(accountId: String) {}
    override fun onHistoryChanged() {}
    override fun onHardwareDecodingChanged(enabled: Boolean) {}
    override fun onHardwareEncodingChanged(enabled: Boolean) {}
    override fun onDaemonLogMessage(message: String) {}
}
