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

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.DisconnectCause
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.jami.model.Call
import net.jami.utils.Log

/**
 * Android Telecom API integration.
 *
 * Registers Jami as a self-managed [PhoneAccount] so that incoming calls appear in the
 * system call log and audio routing (Bluetooth, speakerphone, wired headset) is handled
 * by the platform rather than the app.
 *
 * ## Call lifecycle (incoming)
 * 1. Daemon fires onIncomingCall → [CallService] emits callUpdates(RINGING + incoming).
 * 2. [JamiTelecomManager.handleCallUpdate] calls [TelecomManager.addNewIncomingCall].
 * 3. Telecom invokes [net.jami.android.service.JamiConnectionService.onCreateIncomingConnection].
 * 4. The service creates a [JamiConnection] and registers it back via [registerConnection].
 * 5. User answers via Jami UI → [JamiConnection.onAnswer] → [CallService.accept].
 * 6. Call connects → callUpdates(CURRENT) → [android.telecom.Connection.setActive].
 * 7. Call ends → callUpdates(OVER) → [android.telecom.Connection.setDisconnected] + destroy.
 *
 * ## Call lifecycle (outgoing)
 * 1. User places call → daemon starts dialing → callUpdates(RINGING + outgoing).
 * 2. [handleCallUpdate] calls [TelecomManager.placeCall] with the self-managed handle.
 * 3. Telecom invokes [net.jami.android.service.JamiConnectionService.onCreateOutgoingConnection].
 * 4. Connection registered via [registerConnection]; pending state (CURRENT/OVER) applied.
 * 5. Call connects → callUpdates(CURRENT) → [android.telecom.Connection.setActive].
 * 6. Call ends → callUpdates(OVER) → [android.telecom.Connection.setDisconnected] + destroy.
 * The call appears in the system call log automatically once the connection is destroyed.
 *
 * ## Self-managed mode
 * [PhoneAccount.CAPABILITY_SELF_MANAGED] means Jami keeps its own call UI; the system does
 * not overlay a native incoming-call screen on top. Calls still appear in the call log on
 * Android 10+ and audio routing is managed by Telecom.
 *
 * ## placeCall permission
 * Self-managed [PhoneAccount]s only require [android.Manifest.permission.MANAGE_OWN_CALLS]
 * to call [TelecomManager.placeCall] — no [android.Manifest.permission.CALL_PHONE] needed.
 * Failures on older devices are caught and logged; the call proceeds normally without Telecom.
 */
class JamiTelecomManager(
    private val context: Context,
    val callService: CallService,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val telecomManager: TelecomManager? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            context.getSystemService(TelecomManager::class.java)
        else null

    /** daemon call ID → connection (base type to avoid android-app reverse dependency) */
    private val callToConnection = mutableMapOf<String, android.telecom.Connection>()
    /** daemon call ID → accountId (needed for accept/refuse before connection exists) */
    private val callToAccount = mutableMapOf<String, String>()
    /**
     * State updates that arrived before [registerConnection] was called for that call.
     * placeCall() is asynchronous — the connection may be created after CURRENT/OVER fires.
     */
    private val pendingConnectionState = mutableMapOf<String, Call.CallStatus>()

    val phoneAccountHandle: PhoneAccountHandle? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            PhoneAccountHandle(
                ComponentName(context.packageName, "$CONNECTION_SERVICE_CLASS"),
                JAMI_ACCOUNT_ID
            )
        else null

    init {
        registerPhoneAccount()
        observeCallUpdates()
    }

    private fun registerPhoneAccount() {
        val tm = telecomManager ?: return
        val handle = phoneAccountHandle ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val account = PhoneAccount.builder(handle, "Jami")
            .setCapabilities(
                PhoneAccount.CAPABILITY_SELF_MANAGED or
                PhoneAccount.CAPABILITY_SUPPORTS_VIDEO_CALLING
            )
            .build()
        try {
            tm.registerPhoneAccount(account)
            Log.d(TAG, "PhoneAccount registered: $JAMI_ACCOUNT_ID")
        } catch (e: Exception) {
            Log.e(TAG, "registerPhoneAccount failed: ${e.message}")
        }
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
                if (!callToAccount.containsKey(callId)) {
                    callToAccount[callId] = accountId
                    if (call.isIncoming) {
                        val displayName = call.contact?.displayName?.takeIf { it.isNotBlank() }
                            ?: call.contact?.username?.takeIf { it.isNotBlank() }
                            ?: call.peerUri.rawRingId.take(12).ifEmpty { call.peerUri.uri }
                        reportIncomingCall(callId, accountId, displayName, call.hasVideo())
                    } else {
                        reportOutgoingCall(callId, accountId, call.peerUri.uri, call.hasVideo())
                    }
                }
            }
            Call.CallStatus.CURRENT -> {
                val conn = callToConnection[callId]
                if (conn != null) conn.setActive()
                else pendingConnectionState[callId] = Call.CallStatus.CURRENT
            }
            Call.CallStatus.HOLD -> {
                val conn = callToConnection[callId]
                if (conn != null) conn.setOnHold()
                else pendingConnectionState[callId] = Call.CallStatus.HOLD
            }
            Call.CallStatus.OVER -> {
                val reason = when (call.hangupReason) {
                    Call.HangupReason.BUSY -> DisconnectCause(DisconnectCause.BUSY)
                    Call.HangupReason.TIMEOUT -> DisconnectCause(DisconnectCause.CANCELED)
                    else -> DisconnectCause(DisconnectCause.REMOTE)
                }
                val conn = callToConnection.remove(callId)
                if (conn != null) {
                    conn.setDisconnected(reason)
                    conn.destroy()
                } else {
                    pendingConnectionState[callId] = Call.CallStatus.OVER
                }
                callToAccount.remove(callId)
            }
            else -> { /* CONNECTING / SEARCHING / INACTIVE — no Telecom action needed */ }
        }
    }

    fun reportIncomingCall(callId: String, accountId: String, displayName: String, hasVideo: Boolean) {
        val tm = telecomManager ?: return
        val handle = phoneAccountHandle ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val extras = Bundle().apply {
            putString(EXTRA_CALL_ID, callId)
            putString(EXTRA_ACCOUNT_ID, accountId)
            putString(EXTRA_DISPLAY_NAME, displayName)
            putBoolean(EXTRA_HAS_VIDEO, hasVideo)
        }
        try {
            tm.addNewIncomingCall(handle, extras)
            Log.d(TAG, "addNewIncomingCall: callId=$callId displayName=$displayName")
        } catch (e: SecurityException) {
            Log.e(TAG, "addNewIncomingCall denied (missing MANAGE_OWN_CALLS?): ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "addNewIncomingCall failed: ${e.message}")
        }
    }

    fun reportOutgoingCall(callId: String, accountId: String, peerUri: String, hasVideo: Boolean) {
        val tm = telecomManager ?: return
        val handle = phoneAccountHandle ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val extras = Bundle().apply {
            putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
            putString(EXTRA_CALL_ID, callId)
            putString(EXTRA_ACCOUNT_ID, accountId)
            putBoolean(EXTRA_HAS_VIDEO, hasVideo)
        }
        try {
            tm.placeCall(Uri.fromParts("jami", peerUri, null), extras)
            Log.d(TAG, "placeCall: callId=$callId peerUri=$peerUri")
        } catch (e: SecurityException) {
            // Self-managed PhoneAccounts only need MANAGE_OWN_CALLS (no CALL_PHONE).
            // Log and degrade gracefully — call proceeds but won't appear in the call log.
            Log.e(TAG, "placeCall denied: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "placeCall failed: ${e.message}")
        }
    }

    /**
     * Called by [net.jami.android.service.JamiConnectionService] once a Connection is created.
     * Applies any state update (CURRENT/HOLD/OVER) that arrived before the connection was ready.
     */
    fun registerConnection(callId: String, connection: android.telecom.Connection) {
        callToConnection[callId] = connection
        Log.d(TAG, "Connection registered for callId=$callId")
        when (pendingConnectionState.remove(callId)) {
            Call.CallStatus.CURRENT -> connection.setActive()
            Call.CallStatus.HOLD -> connection.setOnHold()
            Call.CallStatus.OVER -> {
                connection.setDisconnected(DisconnectCause(DisconnectCause.REMOTE))
                connection.destroy()
                callToConnection.remove(callId)
            }
            else -> {}
        }
    }

    fun getAccountId(callId: String): String? = callToAccount[callId]

    fun onCleared() {
        scope.cancel()
    }

    companion object {
        private const val TAG = "JamiTelecomManager"
        private const val JAMI_ACCOUNT_ID = "jami"
        const val CONNECTION_SERVICE_CLASS = "net.jami.android.service.JamiConnectionService"
        const val EXTRA_CALL_ID = "net.jami.extra.CALL_ID"
        const val EXTRA_ACCOUNT_ID = "net.jami.extra.ACCOUNT_ID"
        const val EXTRA_DISPLAY_NAME = "net.jami.extra.DISPLAY_NAME"
        const val EXTRA_HAS_VIDEO = "net.jami.extra.HAS_VIDEO"
    }
}
