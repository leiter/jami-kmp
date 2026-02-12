package net.jami.model

/**
 * Configuration keys for account details.
 *
 * Ported from: jami-client-android/jami-android/libjamiclient/src/main/kotlin/net/jami/model/ConfigKey.kt
 */
enum class ConfigKey(val key: String) {
    // Account
    ACCOUNT_TYPE("Account.type"),
    ACCOUNT_ALIAS("Account.alias"),
    ACCOUNT_ENABLE("Account.enable"),
    ACCOUNT_HOSTNAME("Account.hostname"),
    ACCOUNT_USERNAME("Account.username"),
    ACCOUNT_PASSWORD("Account.password"),
    ACCOUNT_REALM("Account.realm"),
    ACCOUNT_ROUTE_SET("Account.routeset"),
    ACCOUNT_REGISTRATION_EXPIRE("Account.registrationExpire"),
    ACCOUNT_REGISTRATION_STATUS("Account.registrationStatus"),
    ACCOUNT_REGISTRATION_STATE_CODE("Account.registrationCode"),
    ACCOUNT_REGISTRATION_STATE_DESC("Account.registrationDescription"),
    ACCOUNT_DISPLAYNAME("Account.displayName"),
    ACCOUNT_MAILBOX("Account.mailbox"),
    ACCOUNT_USER_AGENT("Account.useragent"),
    ACCOUNT_AUTOANSWER("Account.autoAnswer"),
    ACCOUNT_ACTIVE_CALL_LIMIT("Account.activeCallLimit"),

    // Jami-specific
    ACCOUNT_ARCHIVE_PASSWORD("Account.archivePassword"),
    ACCOUNT_ARCHIVE_HAS_PASSWORD("Account.archiveHasPassword"),
    ACCOUNT_ARCHIVE_PATH("Account.archivePath"),
    ACCOUNT_ARCHIVE_PIN("Account.archivePIN"),
    ACCOUNT_DEVICE_ID("Account.deviceID"),
    ACCOUNT_DEVICE_NAME("Account.deviceName"),
    ACCOUNT_REGISTERED_NAME("Account.registeredName"),
    ACCOUNT_MANAGER_URI("Account.managerUri"),
    ACCOUNT_MANAGER_USERNAME("Account.managerUsername"),

    // DHT
    DHT_PORT("DHT.port"),
    DHT_PUBLIC_IN("DHT.PublicInCalls"),

    // Proxy
    ACCOUNT_PROXY_ENABLED("Account.proxyEnabled"),
    ACCOUNT_PROXY_SERVER("Account.proxyServer"),
    ACCOUNT_PROXY_PUSH_TOKEN("Account.proxyPushToken"),

    // TLS
    TLS_LISTENER_PORT("TLS.listenerPort"),
    TLS_ENABLE("TLS.enable"),
    TLS_CA_LIST_FILE("TLS.certificateListFile"),
    TLS_CERTIFICATE_FILE("TLS.certificateFile"),
    TLS_PRIVATE_KEY_FILE("TLS.privateKeyFile"),
    TLS_PASSWORD("TLS.password"),
    TLS_METHOD("TLS.method"),
    TLS_CIPHERS("TLS.ciphers"),
    TLS_SERVER_NAME("TLS.serverName"),
    TLS_VERIFY_SERVER("TLS.verifyServer"),
    TLS_VERIFY_CLIENT("TLS.verifyClient"),
    TLS_REQUIRE_CLIENT_CERTIFICATE("TLS.requireClientCertificate"),
    TLS_NEGOTIATION_TIMEOUT_SEC("TLS.negotiationTimeoutSec"),

    // SRTP
    SRTP_ENABLE("SRTP.enable"),
    SRTP_KEY_EXCHANGE("SRTP.keyExchange"),
    SRTP_RTP_FALLBACK("SRTP.rtpFallback"),

    // STUN
    STUN_ENABLE("STUN.enable"),
    STUN_SERVER("STUN.server"),

    // TURN
    TURN_ENABLE("TURN.enable"),
    TURN_SERVER("TURN.server"),
    TURN_USERNAME("TURN.username"),
    TURN_PASSWORD("TURN.password"),
    TURN_REALM("TURN.realm"),

    // Audio
    AUDIO_PORT_MIN("Account.audioPortMin"),
    AUDIO_PORT_MAX("Account.audioPortMax"),

    // Video
    VIDEO_ENABLED("Account.videoEnabled"),
    VIDEO_PORT_MIN("Account.videoPortMin"),
    VIDEO_PORT_MAX("Account.videoPortMax"),

    // Ringtone
    RINGTONE_ENABLED("Account.ringtoneEnabled"),
    RINGTONE_PATH("Account.ringtonePath"),

    // Presence
    PRESENCE_PUBLISH_SUPPORTED("Account.presencePublishSupported"),
    PRESENCE_SUBSCRIBE_SUPPORTED("Account.presenceSubscribeSupported"),
    PRESENCE_STATUS("Account.presenceStatus"),
    PRESENCE_NOTE("Account.presenceNote"),

    // Conversation
    ACCOUNT_CONVERSATION_ENABLED("Account.peerDiscovery"),
    ACCOUNT_CONVERSATION_PATH("Account.conversationPath"),

    // UI preferences (not sent to daemon)
    UI_NOTIFICATION_ENABLED("UI.notificationEnabled"),
    UI_CUSTOM_RINGTONE("UI.customRingtone");

    companion object {
        private val keyMap = entries.associateBy { it.key }

        fun fromString(key: String): ConfigKey? = keyMap[key]
    }
}
