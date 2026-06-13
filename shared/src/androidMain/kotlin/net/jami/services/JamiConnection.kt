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

import android.os.Build
import android.telecom.CallAudioState
import android.telecom.Connection
import android.telecom.DisconnectCause
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.jami.utils.Log

/**
 * A single Telecom [Connection] representing one Jami call.
 *
 * Created by [net.jami.android.service.JamiConnectionService] when Telecom invokes
 * [net.jami.android.service.JamiConnectionService.onCreateIncomingConnection] or
 * [net.jami.android.service.JamiConnectionService.onCreateOutgoingConnection].
 *
 * User actions from the system (mute, hold, disconnect) are forwarded to [CallService].
 * State changes driven by the daemon (connected, held, over) are applied to this object
 * by [JamiTelecomManager.handleCallUpdate].
 */
class JamiConnection(
    val callId: String,
    val accountId: String,
    private val callService: CallService,
) : Connection() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        connectionCapabilities = CAPABILITY_HOLD or
                CAPABILITY_SUPPORT_HOLD or
                CAPABILITY_MUTE or
                CAPABILITY_SUPPORTS_VT_LOCAL_BIDIRECTIONAL or
                CAPABILITY_SUPPORTS_VT_REMOTE_BIDIRECTIONAL
        audioModeIsVoip = true
        // Start in RINGING state so Telecom knows this is a live incoming call
        setRinging()
        Log.d(TAG, "JamiConnection created: callId=$callId accountId=$accountId")
    }

    // ==================== System → Jami ====================

    override fun onAnswer() {
        Log.d(TAG, "onAnswer: $callId")
        val hasVideo = (connectionProperties and PROPERTY_IS_EXTERNAL_CALL) == 0 &&
                videoState != android.telecom.VideoProfile.STATE_AUDIO_ONLY
        scope.launch { callService.accept(accountId, callId, hasVideo = hasVideo) }
    }

    override fun onReject() {
        Log.d(TAG, "onReject: $callId")
        scope.launch { callService.refuse(accountId, callId) }
    }

    override fun onDisconnect() {
        Log.d(TAG, "onDisconnect: $callId")
        scope.launch { callService.hangUp(accountId, callId) }
        setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
        destroy()
    }

    override fun onHold() {
        Log.d(TAG, "onHold: $callId")
        scope.launch { callService.hold(accountId, callId) }
        setOnHold()
    }

    override fun onUnhold() {
        Log.d(TAG, "onUnhold: $callId")
        scope.launch { callService.unhold(accountId, callId) }
        setActive()
    }

    override fun onCallAudioStateChanged(state: CallAudioState) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val muted = state.isMuted
        scope.launch {
            callService.muteLocalMedia(accountId, callId, "MEDIA_TYPE_AUDIO", muted)
        }
    }

    override fun onStateChanged(state: Int) {
        Log.d(TAG, "onStateChanged: $callId state=${stateToString(state)}")
    }

    companion object {
        private const val TAG = "JamiConnection"
    }
}
