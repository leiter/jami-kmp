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

import net.jami.model.Account
import net.jami.model.Call
import net.jami.model.Conference
import net.jami.model.Contact
import net.jami.model.Conversation
import net.jami.model.DataTransfer
import net.jami.model.Uri

/**
 * Service for displaying platform notifications.
 *
 * This is a stub interface for the ConversationFacade port.
 * Platform-specific implementations will be added via expect/actual.
 *
 * Ported from: jami-client-android libjamiclient
 */
interface NotificationService {

    /**
     * Show a text message notification.
     */
    fun showTextNotification(conversation: Conversation)

    /**
     * Cancel text notification for a conversation.
     */
    fun cancelTextNotification(accountId: String, conversationUri: Uri)

    /**
     * Handle call notification (incoming, ongoing, or ended).
     */
    suspend fun handleCallNotification(conference: Conference, remove: Boolean)

    /**
     * Remove all call notifications.
     */
    fun removeCallNotification()

    /**
     * Show missed call notification.
     */
    fun showMissedCallNotification(call: Call)

    /**
     * Show incoming trust request notification.
     */
    fun showIncomingTrustRequestNotification(account: Account)

    /**
     * Handle data transfer notification.
     */
    fun handleDataTransferNotification(
        transfer: DataTransfer,
        conversation: Conversation,
        isVisible: Boolean
    )

    /**
     * Remove transfer notification.
     */
    fun removeTransferNotification(accountId: String, conversationUri: Uri, fileId: String)

    /**
     * Show location sharing notification.
     */
    fun showLocationNotification(account: Account, contact: Contact, conversation: Conversation)

    /**
     * Cancel location sharing notification.
     */
    fun cancelLocationNotification(account: Account, contact: Contact)

    /**
     * Show group call notification.
     */
    fun showGroupCallNotification(conversation: Conversation, remove: Boolean)
}

/**
 * Stub implementation of NotificationService for testing.
 */
class StubNotificationService : NotificationService {
    override fun showTextNotification(conversation: Conversation) {}
    override fun cancelTextNotification(accountId: String, conversationUri: Uri) {}
    override suspend fun handleCallNotification(conference: Conference, remove: Boolean) {}
    override fun removeCallNotification() {}
    override fun showMissedCallNotification(call: Call) {}
    override fun showIncomingTrustRequestNotification(account: Account) {}
    override fun handleDataTransferNotification(transfer: DataTransfer, conversation: Conversation, isVisible: Boolean) {}
    override fun removeTransferNotification(accountId: String, conversationUri: Uri, fileId: String) {}
    override fun showLocationNotification(account: Account, contact: Contact, conversation: Conversation) {}
    override fun cancelLocationNotification(account: Account, contact: Contact) {}
    override fun showGroupCallNotification(conversation: Conversation, remove: Boolean) {}
}
