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
import net.jami.ui.contracts.CallContract

/**
 * ViewModel for the active call screen.
 *
 * Exposes three separate state flows (Tier 1 split) so that the
 * 1-second timer tick does not recompose the control buttons.
 */
class CallViewModel(
    private val callService: CallService,
    private val accountService: AccountService
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _peerState = MutableStateFlow(CallContract.PeerState())
    val peerState: StateFlow<CallContract.PeerState> = _peerState.asStateFlow()

    private val _controlsState = MutableStateFlow(CallContract.ControlsState())
    val controlsState: StateFlow<CallContract.ControlsState> = _controlsState.asStateFlow()

    private val _timerState = MutableStateFlow(CallContract.TimerState())
    val timerState: StateFlow<CallContract.TimerState> = _timerState.asStateFlow()

    private var currentCallId: String? = null
    private var currentAccountId: String? = null

    init {
        scope.launch {
            callService.callUpdates.collect { call ->
                if (call.daemonId == currentCallId || currentCallId == null) {
                    currentCallId = call.daemonId
                    currentAccountId = call.account

                    _peerState.value = CallContract.PeerState(
                        peerName = _peerState.value.peerName,
                        peerUri = call.peerUri.uri,
                        callStatus = call.callStatus.name,
                        isIncoming = call.isIncoming
                    )

                    _controlsState.value = CallContract.ControlsState(
                        isAudioMuted = call.isAudioMuted,
                        isVideoMuted = call.isVideoMuted,
                        isSpeakerOn = _controlsState.value.isSpeakerOn
                    )

                    if (call.callStatus == CallStatus.CURRENT && call.timestamp > 0) {
                        val now = net.jami.utils.currentTimeMillis()
                        _timerState.value = CallContract.TimerState(
                            duration = (now - call.timestamp) / 1000
                        )
                    }
                }
            }
        }
    }

    fun onAction(action: CallContract.Action) {
        when (action) {
            CallContract.Action.ToggleMute -> toggleMute()
            CallContract.Action.ToggleVideo -> toggleVideo()
            CallContract.Action.ToggleSpeaker -> toggleSpeaker()
            CallContract.Action.AcceptCall -> acceptCall()
            CallContract.Action.EndCall -> endCall()
        }
    }

    fun initCall(contactUri: String, isVideo: Boolean) {
        scope.launch {
            val account = accountService.currentAccount.value ?: return@launch
            currentAccountId = account.accountId

            val peerUri = Uri.fromString(contactUri)
            _peerState.value = CallContract.PeerState(
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
                _peerState.value = _peerState.value.copy(
                    callStatus = CallStatus.FAILURE.name
                )
            }
        }
    }

    private fun acceptCall() {
        val callId = currentCallId ?: return
        val accountId = currentAccountId ?: return
        callService.accept(accountId, callId, hasVideo = !_controlsState.value.isVideoMuted)
    }

    private fun endCall() {
        val callId = currentCallId ?: return
        val accountId = currentAccountId ?: return
        callService.hangUp(accountId, callId)
    }

    private fun toggleMute() {
        val callId = currentCallId ?: return
        val accountId = currentAccountId ?: return
        val newMuteState = !_controlsState.value.isAudioMuted
        callService.muteLocalMedia(accountId, callId, CallService.MEDIA_TYPE_AUDIO, newMuteState)
        _controlsState.value = _controlsState.value.copy(isAudioMuted = newMuteState)
    }

    private fun toggleVideo() {
        val callId = currentCallId ?: return
        val accountId = currentAccountId ?: return
        val newMuteState = !_controlsState.value.isVideoMuted
        callService.muteLocalMedia(accountId, callId, CallService.MEDIA_TYPE_VIDEO, newMuteState)
        _controlsState.value = _controlsState.value.copy(isVideoMuted = newMuteState)
    }

    private fun toggleSpeaker() {
        val newState = !_controlsState.value.isSpeakerOn
        _controlsState.value = _controlsState.value.copy(isSpeakerOn = newState)
    }

    fun onCleared() {
        scope.cancel()
    }
}
