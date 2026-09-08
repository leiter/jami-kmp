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

import net.jami.model.Uri
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Round-trip tests for [SettingsPreferencesService].
 *
 * Lives in desktopTest rather than commonTest because [Settings] is an `expect class`
 * with no constructible instance in common code. The desktop actual is backed by
 * java.util.prefs, so these exercise real persistence against a scratch node.
 */
class SettingsPreferencesServiceTest {

    private val settings = Settings("net/jami/test/preferences")
    private val service = SettingsPreferencesService(settings)
    private val conversation = Uri.fromString("jami:abc123")

    @BeforeTest
    fun setUp() = settings.clear()

    @AfterTest
    fun tearDown() = settings.clear()

    @Test
    fun conversationPreferencesRoundTrip() {
        service.setConversationPreferences("acc1", conversation, mapOf("muted" to "true"))

        assertEquals(mapOf("muted" to "true"), service.getConversationPreferences("acc1", conversation))
    }

    @Test
    fun conversationPreferencesSurviveANewServiceInstance() {
        service.setConversationPreferences("acc1", conversation, mapOf("muted" to "true"))

        // The regression this phase fixes: the previous StubPreferencesService kept an
        // in-memory map, so anything written was lost when the process restarted.
        val reopened = SettingsPreferencesService(Settings("net/jami/test/preferences"))
        assertEquals(mapOf("muted" to "true"), reopened.getConversationPreferences("acc1", conversation))
    }

    @Test
    fun conversationPreferencesAreScopedPerAccount() {
        service.setConversationPreferences("acc1", conversation, mapOf("muted" to "true"))

        assertEquals(emptyMap(), service.getConversationPreferences("acc2", conversation))
    }

    @Test
    fun unknownConversationReturnsEmptyMap() {
        assertEquals(emptyMap(), service.getConversationPreferences("acc1", conversation))
    }

    @Test
    fun corruptStoredJsonDegradesToEmptyMap() {
        settings.setString("conv_acc1:${conversation.uri}", "{not json")

        assertEquals(emptyMap(), service.getConversationPreferences("acc1", conversation))
    }

    @Test
    fun removeRequestPreferencesClearsTheKey() {
        settings.setString("req_acc1:contact1", "pending")

        service.removeRequestPreferences("acc1", "contact1")

        assertEquals("", settings.getString("req_acc1:contact1", ""))
    }

    @Test
    fun maxFileAutoAcceptDefaultsTo20MbAndIsPerAccount() {
        assertEquals(20 * 1024 * 1024, service.getMaxFileAutoAccept("acc1"))

        settings.setInt("max_file_auto_accept_acc1", 1024)

        assertEquals(1024, service.getMaxFileAutoAccept("acc1"))
        assertEquals(20 * 1024 * 1024, service.getMaxFileAutoAccept("acc2"))
    }

    @Test
    fun notificationTogglesDefaultOnAndReflectStoredValues() {
        assertTrue(service.isNotificationsEnabled())
        assertTrue(service.isCallNotificationsEnabled())
        assertTrue(service.isMessageNotificationsEnabled())

        settings.setBoolean("notifications_enabled", false)
        settings.setBoolean("call_notifications_enabled", false)
        settings.setBoolean("message_notifications_enabled", false)

        // Previously hardcoded true, so turning notifications off had no effect on iOS.
        assertFalse(service.isNotificationsEnabled())
        assertFalse(service.isCallNotificationsEnabled())
        assertFalse(service.isMessageNotificationsEnabled())
    }

    @Test
    fun ringtonePathIsNullUntilSet() {
        assertNull(service.getRingtonePath())

        settings.setString("ringtone_path", "/tmp/ring.ogg")

        assertEquals("/tmp/ring.ogg", service.getRingtonePath())
    }

    @Test
    fun darkThemeDefaultsToFalseAndReflectsStoredValue() {
        assertFalse(service.isDarkTheme())

        settings.setBoolean("dark_theme", true)

        assertTrue(service.isDarkTheme())
    }
}
