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
package net.jami.android.service

import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccountHandle
import net.jami.services.JamiConnection
import net.jami.services.JamiTelecomManager
import net.jami.utils.Log
import org.koin.android.ext.android.inject

/**
 * Android [ConnectionService] that bridges the Telecom framework with Jami calls.
 *
 * The system binds to this service when [JamiTelecomManager] calls
 * [android.telecom.TelecomManager.addNewIncomingCall]. Telecom then calls
 * [onCreateIncomingConnection] to obtain a [Connection] object representing the call.
 *
 * All business logic lives in [JamiTelecomManager] and [JamiConnection]; this class is a
 * thin binding layer between the Android [ConnectionService] lifecycle and those objects.
 *
 * Must be declared in `AndroidManifest.xml` with
 * `android:permission="android.permission.BIND_TELECOM_CONNECTION_SERVICE"`.
 */
class JamiConnectionService : ConnectionService() {

    private val telecomManager: JamiTelecomManager by inject()

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest,
    ): Connection {
        val extras = request.extras
        val callId = extras.getString(JamiTelecomManager.EXTRA_CALL_ID)
        val accountId = extras.getString(JamiTelecomManager.EXTRA_ACCOUNT_ID)
            ?: telecomManager.getAccountId(callId ?: "")

        if (callId == null || accountId == null) {
            Log.e(TAG, "onCreateIncomingConnection: missing callId or accountId in extras")
            return Connection.createFailedConnection(
                DisconnectCause(DisconnectCause.ERROR, "Missing call metadata")
            )
        }

        val connection = JamiConnection(
            callId = callId,
            accountId = accountId,
            callService = telecomManager.callService,
        )
        telecomManager.registerConnection(callId, connection)
        Log.d(TAG, "Incoming connection created: callId=$callId")
        return connection
    }

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest,
    ): Connection {
        val extras = request.extras
        val callId = extras.getString(JamiTelecomManager.EXTRA_CALL_ID)
        val accountId = extras.getString(JamiTelecomManager.EXTRA_ACCOUNT_ID)

        if (callId == null || accountId == null) {
            Log.e(TAG, "onCreateOutgoingConnection: missing callId or accountId")
            return Connection.createFailedConnection(
                DisconnectCause(DisconnectCause.ERROR, "Missing call metadata")
            )
        }

        val connection = JamiConnection(
            callId = callId,
            accountId = accountId,
            callService = telecomManager.callService,
        ).also { it.setDialing() }
        telecomManager.registerConnection(callId, connection)
        Log.d(TAG, "Outgoing connection created: callId=$callId")
        return connection
    }

    override fun onCreateIncomingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest,
    ) {
        val callId = request.extras.getString(JamiTelecomManager.EXTRA_CALL_ID)
        Log.w(TAG, "onCreateIncomingConnectionFailed: callId=$callId")
    }

    override fun onCreateOutgoingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest,
    ) {
        val callId = request.extras.getString(JamiTelecomManager.EXTRA_CALL_ID)
        Log.w(TAG, "onCreateOutgoingConnectionFailed: callId=$callId")
    }

    companion object {
        private const val TAG = "JamiConnectionService"
    }
}
