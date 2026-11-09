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

import net.jami.model.Profile
import net.jami.model.TextMessage
import net.jami.viewmodel.groupConversation
import net.jami.viewmodel.oneToOneConversation
import platform.UserNotifications.UNMutableNotificationContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Who a message notification names, and that a group conversation is safe to notify about —
 * the iOS notification service used to read Conversation.contact, which throws for a group.
 */
class CommunicationNotificationsTest {

    @Test
    fun aGroupsTitleDoesNotThrow() {
        val group = groupConversation()
        assertEquals(group.uri.uri, conversationTitle(group))
    }

    @Test
    fun aGroupIsTitledByItsProfileName() {
        val group = groupConversation()
        group.setProfile(Profile("Climbing", null))
        assertEquals("Climbing", conversationTitle(group))
    }

    @Test
    fun aOneToOneIsTitledByThePeer() {
        val chat = oneToOneConversation(peerId = "bbbb")
        chat.contacts.first { !it.isUser }.username = "peace4us"
        assertEquals("peace4us", conversationTitle(chat))
    }

    @Test
    fun aGroupMessageIsFromItsAuthorNotTheFirstMember() {
        val group = groupConversation(memberCount = 4)
        group.lastEvent = TextMessage("peer2", group.accountId, "m1", null, "hi")

        assertEquals("peer2", messageSender(group)?.uri?.rawRingId)
    }

    @Test
    fun aOneToOneMessageWithoutAnAuthorIsFromThePeer() {
        val chat = oneToOneConversation(peerId = "bbbb")
        chat.lastEvent = TextMessage(null, chat.accountId, "m1", null, "hi")

        assertEquals("bbbb", messageSender(chat)?.uri?.rawRingId)
    }

    @Test
    fun buildingAGroupMessageNotificationFallsBackInsteadOfThrowing() {
        // A test binary has no entitlement, so iOS refuses the update: the plain content
        // must come back, never an exception.
        val group = groupConversation()
        group.lastEvent = TextMessage("peer1", group.accountId, "m1", null, "hi")
        val content = UNMutableNotificationContent().apply { setBody("hi") }

        val shown = CommunicationNotifications(VCardService(StubDeviceRuntimeService()))
            .forMessage(content, group, "hi")

        assertNotNull(shown)
        assertEquals("hi", shown.body)
    }
}
