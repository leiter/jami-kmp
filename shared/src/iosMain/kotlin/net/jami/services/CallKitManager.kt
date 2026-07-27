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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.jami.model.Call
import net.jami.utils.Log
import kotlinx.cinterop.ExperimentalForeignApi
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
/**
 * Pure-Kotlin interface for CallKit integration, used as the Koin binding type.
 *
 * IMPORTANT: [CallKitManager] extends [NSObject] (required by CXProviderDelegateProtocol),
 * and Kotlin/Native does NOT provide a usable `KClass` for Objective-C-derived classes —
 * `CallKitManager::class.hashCode()` throws (KClassUnsupportedImpl), which aborts
 * `startKoin` on release builds. Kotlin/Native also forbids an ObjC-derived class from
 * implementing a Kotlin interface ("Mixing Kotlin and Objective-C supertypes is not
 * supported"). So Koin binds this interface to [CallKitManagerWrapper], a pure-Kotlin
 * adapter (well-formed `KClass`) that delegates to the NSObject-based [CallKitManager].
 */
interface CallKitManagerApi {
    fun reportIncomingCall(callId: String, displayName: String, hasVideo: Boolean)
    fun reportIncomingCallFromPush(peerId: String, displayName: String, hasVideo: Boolean)
    fun reportOutgoingCallStarted(callId: String, accountId: String, displayName: String, hasVideo: Boolean)
    fun onCleared()
}

/** Pure-Kotlin Koin-facing adapter delegating to the NSObject-based [CallKitManager]. */
class CallKitManagerWrapper(private val delegate: CallKitManager) : CallKitManagerApi {
    override fun reportIncomingCall(callId: String, displayName: String, hasVideo: Boolean) =
        delegate.reportIncomingCall(callId, displayName, hasVideo)

    override fun reportIncomingCallFromPush(peerId: String, displayName: String, hasVideo: Boolean) =
        delegate.reportIncomingCallFromPush(peerId, displayName, hasVideo)

    override fun reportOutgoingCallStarted(callId: String, accountId: String, displayName: String, hasVideo: Boolean) =
        delegate.reportOutgoingCallStarted(callId, accountId, displayName, hasVideo)

    override fun onCleared() = delegate.onCleared()
}

@OptIn(ExperimentalForeignApi::class)
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

    /**
     * A CallKit call reported straight off a VoIP push, before the daemon knows about it.
     *
     * iOS 13+ terminates the app if a PushKit VoIP push does not produce a
     * `reportNewIncomingCall` before its completion handler returns, but the daemon needs
     * seconds to reconnect to the DHT and surface the real call. So the push reports a
     * placeholder immediately and the real call [adoptPendingPush] es its UUID when it
     * arrives — the user sees one continuous incoming call, not two.
     */
    private class PendingPushCall(
        val peerId: String,
        val uuid: NSUUID,
        var answered: Boolean = false,
        var rejected: Boolean = false,
        var timeoutJob: Job? = null,
    )

    private var pendingPush: PendingPushCall? = null

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
                    callToAccount[callId] = accountId
                    val displayName = call.contact?.displayName?.takeIf { it.isNotBlank() }
                        ?: call.contact?.username?.takeIf { it.isNotBlank() }
                        ?: call.peerUri.rawRingId.take(12).ifEmpty { call.peerUri.uri }
                    // A VoIP push may already have put this call on screen; take that UUID over
                    // rather than reporting a second incoming call for the same ring.
                    if (!adoptPendingPush(call, callId, accountId, displayName)) {
                        reportIncomingCall(callId, displayName, call.hasVideo())
                    }
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
     * Put an incoming call on screen straight from a VoIP push payload, before the daemon has
     * reconnected. Must be called synchronously from the PushKit handler — see [PendingPushCall].
     *
     * [peerId] is the caller's Jami ID as carried in the push payload; it is what lets the real
     * call recognise this placeholder as its own. An empty [peerId] still works — the next
     * incoming call adopts it — which is the right behaviour for payloads without peer info.
     */
    fun reportIncomingCallFromPush(peerId: String, displayName: String, hasVideo: Boolean) {
        // Only one incoming call is supported at a time (maximumCallsPerCallGroup = 1). A second
        // push before the first resolved means the first is stale.
        pendingPush?.let { stale ->
            Log.d(TAG, "Replacing stale pending push call for ${stale.peerId}")
            stale.timeoutJob?.cancel()
            provider.reportCallWithUUID(stale.uuid, endedAtDate = null, reason = CXCallEndedReasonFailed)
        }

        val uuid = NSUUID()
        val name = displayName.takeIf { it.isNotBlank() }
            ?: peerId.take(12).takeIf { it.isNotBlank() }
            ?: "Jami"
        val pending = PendingPushCall(peerId = peerId, uuid = uuid)
        pendingPush = pending

        val update = CXCallUpdate().apply {
            remoteHandle = CXHandle(type = CXHandleTypeGeneric, value = name)
            localizedCallerName = name
            this.hasVideo = hasVideo
        }

        provider.reportNewIncomingCallWithUUID(uuid, update = update) { error ->
            if (error != null) {
                Log.e(TAG, "reportNewIncomingCall (push) failed: ${error.localizedDescription}")
                if (pendingPush === pending) pendingPush = null
            } else {
                Log.d(TAG, "Pending push call reported: peer=$peerId uuid=${uuid.UUIDString}")
            }
        }

        // If the daemon never produces the call — dead push, network failure, caller gave up —
        // the placeholder would otherwise ring forever.
        pending.timeoutJob = scope.launch {
            delay(PUSH_CALL_TIMEOUT_MS)
            if (pendingPush === pending) {
                Log.w(TAG, "Pending push call timed out without a daemon call: peer=$peerId")
                pendingPush = null
                provider.reportCallWithUUID(uuid, endedAtDate = null, reason = CXCallEndedReasonUnanswered)
            }
        }
    }

    /**
     * Hand a placeholder reported from a push over to the real daemon call.
     *
     * Returns true when [call] took over an existing pending UUID, meaning the caller must not
     * report it as a new incoming call.
     */
    private fun adoptPendingPush(
        call: Call,
        callId: String,
        accountId: String,
        displayName: String,
    ): Boolean {
        val pending = pendingPush ?: return false
        // An empty peerId means the payload carried no caller info, so any incoming call is a match.
        if (pending.peerId.isNotEmpty() && pending.peerId != call.peerUri.rawRingId) return false

        pending.timeoutJob?.cancel()
        pendingPush = null

        val uuid = pending.uuid
        callToUuid[callId] = uuid
        uuidToCall[uuid.UUIDString] = callId
        callToAccount[callId] = accountId
        Log.d(TAG, "Pending push call adopted by $callId uuid=${uuid.UUIDString}")

        // Replace the placeholder's guessed caller name with what the daemon actually resolved.
        provider.reportCallWithUUID(
            uuid,
            updated = CXCallUpdate().apply {
                remoteHandle = CXHandle(type = CXHandleTypeGeneric, value = displayName)
                localizedCallerName = displayName
                hasVideo = call.hasVideo()
            }
        )

        // The user may have already tapped answer or decline on the placeholder, before the
        // daemon had a call to act on. Apply that decision now.
        when {
            pending.answered -> {
                Log.d(TAG, "Applying deferred answer to $callId")
                scope.launch { callService.accept(accountId, callId, hasVideo = false) }
            }
            pending.rejected -> {
                Log.d(TAG, "Applying deferred decline to $callId")
                scope.launch { callService.refuse(accountId, callId) }
            }
        }
        return true
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
        pendingPush?.timeoutJob?.cancel()
        pendingPush = null
    }

    override fun provider(provider: CXProvider, performAnswerCallAction: CXAnswerCallAction) {
        // Answered off a push placeholder: there is no daemon call to accept yet, so record the
        // intent and let adoptPendingPush apply it. Fulfilling keeps the system call UI alive.
        pendingPush?.let { pending ->
            if (pending.uuid.UUIDString == performAnswerCallAction.callUUID.UUIDString) {
                Log.d(TAG, "Answer on pending push call — deferring until the daemon call arrives")
                pending.answered = true
                performAnswerCallAction.fulfill()
                return
            }
        }

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
        // Declined off a push placeholder — same deferral as answer. The pending entry is kept
        // (not cleared) so the call still gets refused on the daemon once it materialises,
        // otherwise the caller would keep ringing.
        pendingPush?.let { pending ->
            if (pending.uuid.UUIDString == performEndCallAction.callUUID.UUIDString) {
                Log.d(TAG, "Decline on pending push call — deferring until the daemon call arrives")
                pending.rejected = true
                performEndCallAction.fulfill()
                return
            }
        }

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

    fun onCleared() {
        pendingPush?.timeoutJob?.cancel()
        pendingPush = null
        scope.cancel()
        provider.invalidate()
    }

}

private const val TAG = "CallKitManager"

/**
 * How long a push-reported placeholder may ring before the daemon produces a matching call.
 * Generous, because the daemon has to reconnect to the DHT from a cold background process.
 */
private const val PUSH_CALL_TIMEOUT_MS = 25_000L
