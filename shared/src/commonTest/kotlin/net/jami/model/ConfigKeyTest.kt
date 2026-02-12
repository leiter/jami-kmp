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
package net.jami.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ConfigKeyTest {

    @Test
    fun testAccountTypeKey() {
        assertEquals("Account.type", ConfigKey.ACCOUNT_TYPE.key)
    }

    @Test
    fun testAccountAliasKey() {
        assertEquals("Account.alias", ConfigKey.ACCOUNT_ALIAS.key)
    }

    @Test
    fun testAccountEnableKey() {
        assertEquals("Account.enable", ConfigKey.ACCOUNT_ENABLE.key)
    }

    @Test
    fun testDhtKeys() {
        assertEquals("DHT.port", ConfigKey.DHT_PORT.key)
        assertEquals("DHT.PublicInCalls", ConfigKey.DHT_PUBLIC_IN.key)
    }

    @Test
    fun testTlsKeys() {
        assertEquals("TLS.enable", ConfigKey.TLS_ENABLE.key)
        assertEquals("TLS.certificateFile", ConfigKey.TLS_CERTIFICATE_FILE.key)
        assertEquals("TLS.privateKeyFile", ConfigKey.TLS_PRIVATE_KEY_FILE.key)
    }

    @Test
    fun testSrtpKeys() {
        assertEquals("SRTP.enable", ConfigKey.SRTP_ENABLE.key)
        assertEquals("SRTP.keyExchange", ConfigKey.SRTP_KEY_EXCHANGE.key)
    }

    @Test
    fun testStunKeys() {
        assertEquals("STUN.enable", ConfigKey.STUN_ENABLE.key)
        assertEquals("STUN.server", ConfigKey.STUN_SERVER.key)
    }

    @Test
    fun testTurnKeys() {
        assertEquals("TURN.enable", ConfigKey.TURN_ENABLE.key)
        assertEquals("TURN.server", ConfigKey.TURN_SERVER.key)
        assertEquals("TURN.username", ConfigKey.TURN_USERNAME.key)
        assertEquals("TURN.password", ConfigKey.TURN_PASSWORD.key)
    }

    @Test
    fun testVideoKeys() {
        assertEquals("Account.videoEnabled", ConfigKey.VIDEO_ENABLED.key)
        assertEquals("Account.videoPortMin", ConfigKey.VIDEO_PORT_MIN.key)
        assertEquals("Account.videoPortMax", ConfigKey.VIDEO_PORT_MAX.key)
    }

    @Test
    fun testAudioKeys() {
        assertEquals("Account.audioPortMin", ConfigKey.AUDIO_PORT_MIN.key)
        assertEquals("Account.audioPortMax", ConfigKey.AUDIO_PORT_MAX.key)
    }

    @Test
    fun testPresenceKeys() {
        assertEquals("Account.presencePublishSupported", ConfigKey.PRESENCE_PUBLISH_SUPPORTED.key)
        assertEquals("Account.presenceSubscribeSupported", ConfigKey.PRESENCE_SUBSCRIBE_SUPPORTED.key)
        assertEquals("Account.presenceStatus", ConfigKey.PRESENCE_STATUS.key)
    }

    @Test
    fun testJamiSpecificKeys() {
        assertEquals("Account.archivePassword", ConfigKey.ACCOUNT_ARCHIVE_PASSWORD.key)
        assertEquals("Account.archiveHasPassword", ConfigKey.ACCOUNT_ARCHIVE_HAS_PASSWORD.key)
        assertEquals("Account.deviceID", ConfigKey.ACCOUNT_DEVICE_ID.key)
        assertEquals("Account.deviceName", ConfigKey.ACCOUNT_DEVICE_NAME.key)
        assertEquals("Account.registeredName", ConfigKey.ACCOUNT_REGISTERED_NAME.key)
    }

    @Test
    fun testFromStringValidKey() {
        val key = ConfigKey.fromString("Account.type")
        assertNotNull(key)
        assertEquals(ConfigKey.ACCOUNT_TYPE, key)
    }

    @Test
    fun testFromStringTlsKey() {
        val key = ConfigKey.fromString("TLS.enable")
        assertNotNull(key)
        assertEquals(ConfigKey.TLS_ENABLE, key)
    }

    @Test
    fun testFromStringTurnKey() {
        val key = ConfigKey.fromString("TURN.server")
        assertNotNull(key)
        assertEquals(ConfigKey.TURN_SERVER, key)
    }

    @Test
    fun testFromStringInvalidKey() {
        val key = ConfigKey.fromString("Invalid.key")
        assertNull(key)
    }

    @Test
    fun testFromStringEmptyKey() {
        val key = ConfigKey.fromString("")
        assertNull(key)
    }

    @Test
    fun testFromStringCaseSensitive() {
        // Keys are case-sensitive
        val key = ConfigKey.fromString("account.type") // lowercase
        assertNull(key)
    }

    @Test
    fun testAllKeysHaveUniqueKeys() {
        val keys = ConfigKey.entries.map { it.key }
        val uniqueKeys = keys.toSet()
        assertEquals(keys.size, uniqueKeys.size, "All ConfigKey entries should have unique key strings")
    }

    @Test
    fun testEnumEntries() {
        // Ensure we have a reasonable number of config keys
        val entries = ConfigKey.entries
        assertTrue(entries.size > 40, "Should have at least 40 config keys")
    }

    @Test
    fun testUiKeys() {
        assertEquals("UI.notificationEnabled", ConfigKey.UI_NOTIFICATION_ENABLED.key)
        assertEquals("UI.customRingtone", ConfigKey.UI_CUSTOM_RINGTONE.key)
    }

    @Test
    fun testProxyKeys() {
        assertEquals("Account.proxyEnabled", ConfigKey.ACCOUNT_PROXY_ENABLED.key)
        assertEquals("Account.proxyServer", ConfigKey.ACCOUNT_PROXY_SERVER.key)
        assertEquals("Account.proxyPushToken", ConfigKey.ACCOUNT_PROXY_PUSH_TOKEN.key)
    }

    @Test
    fun testRingtoneKeys() {
        assertEquals("Account.ringtoneEnabled", ConfigKey.RINGTONE_ENABLED.key)
        assertEquals("Account.ringtonePath", ConfigKey.RINGTONE_PATH.key)
    }

    private fun assertTrue(condition: Boolean, message: String) {
        kotlin.test.assertTrue(condition, message)
    }
}
