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
package net.jami.ui.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.jami.model.Call.CallStatus
import net.jami.model.Uri
import net.jami.services.AccountService
import net.jami.services.CallService

/**
 * State for the active call screen.
 */
data class CallState(
    val callStatus: String = "",
    val peerName: String = "",
    val peerUri: String = "",
    val duration: Long = 0L,
    val isAudioMuted: Boolean = false,
    val isVideoMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isIncoming: Boolean = false
)

/**
 * ViewModel for the active call screen.
 *
 * Manages call lifecycle operations (place, accept, end) and media
 * controls (mute audio/video, speaker). Observes call state changes
 * from the daemon via CallService.
 */
class CallViewModel(
    private val callService: CallService,
    private val accountService: AccountService
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(CallState())
    val state: StateFlow<CallState> = _state.asStateFlow()

    private var currentCallId: String? = null
    private var currentAccountId: String? = null

    init {
        // Observe call state updates
        scope.launch {
            callService.callUpdates.collect { call ->
                if (call.daemonId == currentCallId || currentCallId == null) {
                    currentCallId = call.daemonId
                    currentAccountId = call.account

                    _state.value = _state.value.copy(
                        callStatus = call.callStatus.name,
                        peerUri = call.peerUri.uri,
                        isAudioMuted = call.isAudioMuted,
                        isVideoMuted = call.isVideoMuted,
                        isIncoming = call.isIncoming
                    )

                    // Update duration for ongoing calls
                    if (call.callStatus == CallStatus.CURRENT && call.timestamp > 0) {
                        val now = net.jami.utils.currentTimeMillis()
                        _state.value = _state.value.copy(
                            duration = (now - call.timestamp) / 1000
                        )
                    }

                    // Clean up if call ended
                    if (call.callStatus == CallStatus.OVER || call.callStatus == CallStatus.FAILURE) {
                        // Call ended; UI should navigate away
                    }
                }
            }
        }
    }

    /**
     * Initiate a new outgoing call.
     *
     * @param contactUri URI of the contact to call.
     * @param isVideo True for a video call, false for audio only.
     */
    fun initCall(contactUri: String, isVideo: Boolean) {
        scope.launch {
            val account = accountService.currentAccount.value ?: return@launch
            currentAccountId = account.accountId

            val peerUri = Uri.fromString(contactUri)
            _state.value = _state.value.copy(
                peerUri = contactUri,
                peerName = contactUri,
                callStatus = CallStatus.SEARCHING.name,
                isIncoming = false
            )

            try {
                val call = callService.placeCall(
                    accountId = account.accountId,
                    contactUri = peerUri,
                    hasVideo = isVideo
                )
                currentCallId = call.daemonId
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    callStatus = CallStatus.FAILURE.name
                )
            }
        }
    }

    /**
     * Accept an incoming call.
     */
    fun acceptCall() {
        val callId = currentCallId ?: return
        val accountId = currentAccountId ?: return
        callService.accept(accountId, callId, hasVideo = !_state.value.isVideoMuted)
    }

    /**
     * End (hang up) the current call.
     */
    fun endCall() {
        val callId = currentCallId ?: return
        val accountId = currentAccountId ?: return
        callService.hangUp(accountId, callId)
    }

    /**
     * Toggle the audio mute state.
     */
    fun toggleMute() {
        val callId = currentCallId ?: return
        val accountId = currentAccountId ?: return
        val newMuteState = !_state.value.isAudioMuted
        callService.muteLocalMedia(accountId, callId, CallService.MEDIA_TYPE_AUDIO, newMuteState)
        _state.value = _state.value.copy(isAudioMuted = newMuteState)
    }

    /**
     * Toggle the video mute state.
     */
    fun toggleVideo() {
        val callId = currentCallId ?: return
        val accountId = currentAccountId ?: return
        val newMuteState = !_state.value.isVideoMuted
        callService.muteLocalMedia(accountId, callId, CallService.MEDIA_TYPE_VIDEO, newMuteState)
        _state.value = _state.value.copy(isVideoMuted = newMuteState)
    }

    /**
     * Toggle the speaker output.
     * Note: Actual speaker routing is platform-specific and handled by HardwareService.
     */
    fun toggleSpeaker() {
        val newState = !_state.value.isSpeakerOn
        _state.value = _state.value.copy(isSpeakerOn = newState)
    }

    /**
     * Cancel the coroutine scope when this ViewModel is no longer needed.
     */
    fun onCleared() {
        scope.cancel()
    }
}
