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

import net.jami.ui.navigation.OpenConversationState
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A tapped home-screen quick action opens its conversation through OpenConversationState. */
class IOSConversationShortcutsTest {

    @BeforeTest
    @AfterTest
    fun reset() = OpenConversationState.consume()

    @Test
    fun ourQuickActionOpensTheConversation() {
        assertTrue(openConversationShortcut(IOSConversationShortcuts.SHORTCUT_TYPE, "abc123"))
        assertEquals("abc123", OpenConversationState.conversationId.value)
    }

    @Test
    fun aSwarmUriIsReducedToTheBareIdTheChatRouteTakes() {
        openConversationShortcut(IOSConversationShortcuts.SHORTCUT_TYPE, "swarm:abc123")
        assertEquals("abc123", OpenConversationState.conversationId.value)
    }

    @Test
    fun someoneElsesQuickActionIsNotOurs() {
        assertFalse(openConversationShortcut("com.example.other", "abc123"))
        assertNull(OpenConversationState.conversationId.value)
    }

    @Test
    fun aQuickActionWithoutAConversationOpensNothing() {
        assertFalse(openConversationShortcut(IOSConversationShortcuts.SHORTCUT_TYPE, null))
        assertFalse(openConversationShortcut(IOSConversationShortcuts.SHORTCUT_TYPE, " "))
        assertNull(OpenConversationState.conversationId.value)
    }
}
