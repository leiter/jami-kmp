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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.jami.model.Call
import net.jami.utils.Log
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionModeVoiceChat
import platform.CallKit.CXAnswerCallAction
import platform.CallKit.CXCallController
import platform.CallKit.CXCallEndedReasonFailed
import platform.CallKit.CXCallEndedReasonRemoteEnded
import platform.CallKit.CXCallEndedReasonUnanswered
import platform.CallKit.CXCallUpdate
import platform.CallKit.CXEndCallAction
import platform.CallKit.CXHandle
import platform.CallKit.CXHandleTypeGeneric
import platform.CallKit.CXProvider
import platform.CallKit.CXProviderConfiguration
import platform.CallKit.CXProviderDelegateProtocol
import platform.CallKit.CXSetHeldCallAction
import platform.CallKit.CXSetMutedCallAction
import platform.CallKit.CXStartCallAction
import platform.CallKit.CXTransaction
import platform.Foundation.NSUUID
import platform.darwin.NSObject

/**
 * CallKit integration for iOS.
 *
 * Implements [CXProviderDelegateProtocol] to bridge between the Jami daemon and the
 * iOS native call UI (lock screen, incoming call sheet, Control Centre call banner).
 *
 * ## Call lifecycle
 * - Incoming: daemon fires onIncomingCall → CallService emits callUpdates(RINGING+incoming)
 *   → [reportIncomingCall] → CXProvider shows the native incoming-call screen.
 * - User answers via system UI → [provider:performAnswerCallAction:] → callService.accept().
 * - User declines via system UI → [provider:performEndCallAction:] → callService.refuse().
 * - Outgoing: callService.call() → [reportOutgoingCallStarted] → system registers the call.
 * - Call connects → callUpdates(CURRENT) → [reportOutgoingCallConnected].
 * - Call ends → callUpdates(OVER) → CXProvider.reportCallWithUUID(ended).
 *
 * ## Audio session
 * When the user answers, [provider:didActivateAudioSession:] is called and we configure
 * AVAudioSession for VoIP (.playAndRecord / .voiceChat).
 */
class CallKitManager(
    private val callService: CallService,
) : NSObject(), CXProviderDelegateProtocol {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val provider: CXProvider
    private val callController = CXCallController()

    /** daemon call ID → NSUUID reported to CallKit */
    private val callToUuid = mutableMapOf<String, NSUUID>()
    /** NSUUID.UUIDString → daemon call ID for delegate callback reverse lookup */
    private val uuidToCall = mutableMapOf<String, String>()
    /** daemon call ID → account ID needed for accept / refuse / hangUp */
    private val callToAccount = mutableMapOf<String, String>()

    init {
        val config = CXProviderConfiguration().apply {
            supportsVideo = true
            maximumCallsPerCallGroup = 1u
            supportedHandleTypes = setOf(CXHandleTypeGeneric)
        }
        provider = CXProvider(configuration = config)
        provider.setDelegate(this, queue = null)
        observeCallUpdates()
    }

    private fun observeCallUpdates() {
        scope.launch {
            callService.callUpdates.collect { call ->
                handleCallUpdate(call)
            }
        }
    }

    private fun handleCallUpdate(call: Call) {
        val callId = call.daemonId ?: return
        val accountId = call.account

        when (call.callStatus) {
            Call.CallStatus.RINGING -> {
                if (call.isIncoming && !callToUuid.containsKey(callId)) {
                    // New incoming call not yet tracked — report to system
                    callToAccount[callId] = accountId
                    val displayName = call.contact?.displayName?.takeIf { it.isNotBlank() }
                        ?: call.contact?.username?.takeIf { it.isNotBlank() }
                        ?: call.peerUri.rawRingId.take(12).ifEmpty { call.peerUri.uri }
                    reportIncomingCall(callId, displayName, call.hasVideo())
                }
            }
            Call.CallStatus.CURRENT -> {
                // Outgoing call connected — inform CallKit it is now live
                callToUuid[callId]?.let { uuid ->
                    provider.reportOutgoingCallWithUUID(uuid, connectedAtDate = null)
                }
            }
            Call.CallStatus.OVER -> {
                val reason = when (call.hangupReason) {
                    Call.HangupReason.BUSY -> CXCallEndedReasonFailed
                    Call.HangupReason.TIMEOUT -> CXCallEndedReasonUnanswered
                    else -> CXCallEndedReasonRemoteEnded
                }
                endTrackedCall(callId, reason)
            }
            else -> { /* CONNECTING / HOLD / SEARCHING — no CallKit action */ }
        }
    }

    // ==================== Public API ====================

    /**
     * Register a new incoming call with the system.
     * Called automatically from [observeCallUpdates] for RINGING+incoming calls.
     */
    fun reportIncomingCall(callId: String, displayName: String, hasVideo: Boolean) {
        val uuid = NSUUID()
        callToUuid[callId] = uuid
        uuidToCall[uuid.UUIDString] = callId

        val update = CXCallUpdate().apply {
            remoteHandle = CXHandle(type = CXHandleTypeGeneric, value = displayName)
            localizedCallerName = displayName
            this.hasVideo = hasVideo
        }

        provider.reportNewIncomingCallWithUUID(uuid, update = update) { error ->
            if (error != null) {
                Log.e(TAG, "reportNewIncomingCall failed: ${error.localizedDescription}")
                callToUuid.remove(callId)
                uuidToCall.remove(uuid.UUIDString)
                callToAccount.remove(callId)
            } else {
                Log.d(TAG, "Incoming call reported: $callId uuid=${uuid.UUIDString}")
            }
        }
    }

    /**
     * Register a new outgoing call with the system.
     * Should be called by [CallService] when it places an outgoing call.
     */
    fun reportOutgoingCallStarted(callId: String, accountId: String, displayName: String, hasVideo: Boolean) {
        val uuid = NSUUID()
        callToUuid[callId] = uuid
        uuidToCall[uuid.UUIDString] = callId
        callToAccount[callId] = accountId

        val handle = CXHandle(type = CXHandleTypeGeneric, value = displayName)
        val startAction = CXStartCallAction(callUUID = uuid, handle = handle).apply {
            video = hasVideo
        }
        callController.requestTransaction(CXTransaction(action = startAction)) { error ->
            if (error != null) {
                Log.e(TAG, "requestStartCallTransaction failed: ${error.localizedDescription}")
            } else {
                Log.d(TAG, "Outgoing call registered: $callId uuid=${uuid.UUIDString}")
            }
        }
    }

    private fun endTrackedCall(callId: String, reason: Long) {
        val uuid = callToUuid.remove(callId) ?: return
        uuidToCall.remove(uuid.UUIDString)
        callToAccount.remove(callId)
        provider.reportCallWithUUID(uuid, endedAtDate = null, reason = reason)
        Log.d(TAG, "Call ended: $callId reason=$reason")
    }

    // ==================== CXProviderDelegateProtocol ====================

    override fun providerDidReset(provider: CXProvider) {
        Log.d(TAG, "providerDidReset — clearing all tracked calls")
        callToUuid.clear()
        uuidToCall.clear()
        callToAccount.clear()
    }

    override fun provider(provider: CXProvider, performAnswerCallAction: CXAnswerCallAction) {
        val callId = uuidToCall[performAnswerCallAction.callUUID.UUIDString]
        val accountId = callId?.let { callToAccount[it] }
        if (callId == null || accountId == null) {
            Log.e(TAG, "performAnswerCallAction: unknown uuid=${performAnswerCallAction.callUUID.UUIDString}")
            performAnswerCallAction.fail()
            return
        }
        Log.d(TAG, "Answer via CallKit: $callId")
        scope.launch { callService.accept(accountId, callId, hasVideo = false) }
        performAnswerCallAction.fulfill()
    }

    override fun provider(provider: CXProvider, performEndCallAction: CXEndCallAction) {
        val callId = uuidToCall[performEndCallAction.callUUID.UUIDString]
        val accountId = callId?.let { callToAccount[it] }
        if (callId == null || accountId == null) {
            Log.e(TAG, "performEndCallAction: unknown uuid=${performEndCallAction.callUUID.UUIDString}")
            performEndCallAction.fail()
            return
        }
        Log.d(TAG, "End/Decline via CallKit: $callId")
        scope.launch {
            val call = callService.getCall(callId)
            if (call?.isRinging == true && call.isIncoming) {
                callService.refuse(accountId, callId)
            } else {
                callService.hangUp(accountId, callId)
            }
        }
        // Remove eagerly — the callUpdates OVER event will also fire but find nothing to remove
        uuidToCall.remove(performEndCallAction.callUUID.UUIDString)
        callToUuid.remove(callId)
        callToAccount.remove(callId)
        performEndCallAction.fulfill()
    }

    override fun provider(provider: CXProvider, performSetMutedCallAction: CXSetMutedCallAction) {
        val callId = uuidToCall[performSetMutedCallAction.callUUID.UUIDString]
        val accountId = callId?.let { callToAccount[it] }
        if (callId == null || accountId == null) {
            performSetMutedCallAction.fail()
            return
        }
        scope.launch {
            callService.muteLocalMedia(accountId, callId, "MEDIA_TYPE_AUDIO", performSetMutedCallAction.muted)
        }
        performSetMutedCallAction.fulfill()
    }

    override fun provider(provider: CXProvider, performSetHeldCallAction: CXSetHeldCallAction) {
        val callId = uuidToCall[performSetHeldCallAction.callUUID.UUIDString]
        val accountId = callId?.let { callToAccount[it] }
        if (callId == null || accountId == null) {
            performSetHeldCallAction.fail()
            return
        }
        scope.launch {
            if (performSetHeldCallAction.onHold) callService.hold(accountId, callId)
            else callService.unhold(accountId, callId)
        }
        performSetHeldCallAction.fulfill()
    }

    override fun provider(provider: CXProvider, didActivateAudioSession: AVAudioSession) {
        // CallKit hands us the activated session — configure it for VoIP
        try {
            didActivateAudioSession.setCategory(
                category = AVAudioSessionCategoryPlayAndRecord,
                mode = AVAudioSessionModeVoiceChat,
                options = 0u,
                error = null
            )
            didActivateAudioSession.setActive(true, error = null)
            Log.d(TAG, "AVAudioSession activated for call")
        } catch (e: Exception) {
            Log.e(TAG, "AVAudioSession activation failed: ${e.message}")
        }
    }

    override fun provider(provider: CXProvider, didDeactivateAudioSession: AVAudioSession) {
        Log.d(TAG, "AVAudioSession deactivated after call")
    }

    fun onCleared() {
        scope.cancel()
        provider.invalidate()
    }

    companion object {
        const val TAG = "CallKitManager"
    }
}
