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

import kotlinx.coroutines.test.runTest
import net.jami.model.Account
import net.jami.model.Call
import net.jami.model.Conference
import net.jami.model.Contact
import net.jami.model.Conversation
import net.jami.model.DataTransfer
import net.jami.model.Uri
import net.jami.utils.currentTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NotificationServiceTest {

    // ══════════════════════════════════════════════════════════════════════════
    // Companion Object Constants Tests
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun testCompanionObjectConstants() {
        assertEquals("NOTIF_TRUST_REQUEST_ACCOUNT_ID", NotificationService.NOTIF_TRUST_REQUEST_ACCOUNT_ID)
        assertEquals("NOTIFICATION_TRUST_REQUEST_MULTIPLE", NotificationService.NOTIF_TRUST_REQUEST_MULTIPLE)
        assertEquals("callId", NotificationService.KEY_CALL_ID)
        assertEquals("holdId", NotificationService.KEY_HOLD_ID)
        assertEquals("endId", NotificationService.KEY_END_ID)
        assertEquals("notificationId", NotificationService.KEY_NOTIFICATION_ID)
        assertEquals("screenshare", NotificationService.KEY_SCREENSHARE)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // StubNotificationService Tests
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun testStubCallNotifications() = runTest {
        val stub = StubNotificationService()

        // Call notification methods should not throw
        assertNull(stub.showCallNotification(1))
        stub.cancelCallNotification()
        stub.removeCallNotification()

        val conf = Conference("account1", "conf1")
        stub.handleCallNotification(conf, false)
        stub.handleCallNotification(conf, true)
        stub.handleCallNotification(conf, false, startScreenshare = true)

        stub.startPendingScreenshare("conf1")
    }

    @Test
    fun testStubPreparePendingScreenshare() {
        val stub = StubNotificationService()
        var callbackCalled = false

        val conf = Conference("account1", "conf1")
        stub.preparePendingScreenshare(conf) {
            callbackCalled = true
        }

        // Stub should call the callback immediately
        assertEquals(true, callbackCalled)
    }

    @Test
    fun testStubMissedCallNotification() {
        val stub = StubNotificationService()

        val call = Call(
            account = "account1",
            daemonId = "call1",
            peerUri = Uri.fromString("jami:abc123"),
            isIncoming = true
        )

        // Should not throw
        stub.showMissedCallNotification(call)
    }

    @Test
    fun testStubGroupCallNotification() {
        val stub = StubNotificationService()
        val conversation = Conversation("account1", Uri.fromString("swarm:conversation1"))

        // Should not throw
        stub.showGroupCallNotification(conversation)
        stub.showGroupCallNotification(conversation, remove = false)
        stub.showGroupCallNotification(conversation, remove = true)
    }

    @Test
    fun testStubTextNotifications() {
        val stub = StubNotificationService()
        val conversation = Conversation("account1", Uri.fromString("swarm:conversation1"))

        // Should not throw
        stub.showTextNotification(conversation)
        stub.cancelTextNotification("account1", Uri.fromString("jami:contact1"))
        stub.cancelAll()
    }

    @Test
    fun testStubTrustRequestNotifications() {
        val stub = StubNotificationService()
        val account = Account("account1")

        // Should not throw
        stub.showIncomingTrustRequestNotification(account)
        stub.cancelTrustRequestNotification("account1")
    }

    @Test
    fun testStubFileTransferNotifications() {
        val stub = StubNotificationService()
        val conversation = Conversation("account1", Uri.fromString("swarm:conversation1"))

        val transfer = DataTransfer(
            fileId = "transfer1",
            accountId = "account1",
            peerUri = "jami:peer1",
            displayName = "file.txt",
            isOutgoing = false,
            timestamp = currentTimeMillis(),
            totalSize = 1024L,
            bytesProgress = 0L
        )

        // Should not throw
        stub.showFileTransferNotification(conversation, transfer)
        stub.handleDataTransferNotification(transfer, conversation, false)
        stub.handleDataTransferNotification(transfer, conversation, true)
        stub.removeTransferNotification("account1", Uri.fromString("swarm:conversation1"), "transfer1")
        assertNull(stub.getDataTransferNotification(1))
        stub.cancelFileNotification(1)
    }

    @Test
    fun testStubLocationNotifications() {
        val stub = StubNotificationService()
        val account = Account("account1")
        val contact = Contact(Uri.fromString("jami:contact1"))
        val conversation = Conversation("account1", Uri.fromString("swarm:conversation1"))

        // Should not throw
        stub.showLocationNotification(account, contact, conversation)
        stub.cancelLocationNotification(account, contact)
    }

    @Test
    fun testStubServiceNotification() {
        val stub = StubNotificationService()

        // Should return a non-null object (Unit in stub)
        assertNotNull(stub.serviceNotification)
    }

    @Test
    fun testStubConnectionUpdate() {
        val stub = StubNotificationService()

        // Should not throw
        stub.onConnectionUpdate(true)
        stub.onConnectionUpdate(false)
    }

    @Test
    fun testStubPushNotifications() {
        val stub = StubNotificationService()

        // Should not throw
        stub.processPush()
        stub.testPushNotification("account1")
    }
}
