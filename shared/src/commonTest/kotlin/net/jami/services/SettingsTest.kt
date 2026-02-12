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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsTest {

    @Test
    fun testSettingsKeyAccountKey() {
        val key = SettingsKey.accountKey("acc123", "setting")
        assertEquals("account_acc123_setting", key)
    }

    @Test
    fun testSettingsKeyConversationKey() {
        val key = SettingsKey.conversationKey("acc123", "conv456", "muted")
        assertEquals("conversation_acc123_conv456_muted", key)
    }

    @Test
    fun testSettingsKeyConstants() {
        assertEquals("dark_theme", SettingsKey.DARK_THEME)
        assertEquals("notifications_enabled", SettingsKey.NOTIFICATIONS_ENABLED)
        assertEquals("video_enabled", SettingsKey.VIDEO_ENABLED)
        assertEquals("max_file_auto_accept", SettingsKey.MAX_FILE_AUTO_ACCEPT)
    }

    // Note: UserSettings tests require a platform-specific Settings instance
    // These tests verify the key generation and constants work correctly
}
