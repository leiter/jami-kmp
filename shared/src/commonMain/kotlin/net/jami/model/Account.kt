package net.jami.model

import kotlinx.serialization.Serializable

/**
 * Represents a Jami account.
 *
 * Ported from: jami-client-android/jami-android/libjamiclient/src/main/kotlin/net/jami/model/Account.kt
 * Original uses RxJava Subjects, this uses Kotlin StateFlow.
 */
@Serializable
data class Account(
    val accountId: String,
    val details: MutableMap<String, String> = mutableMapOf(),
    val volatileDetails: MutableMap<String, String> = mutableMapOf(),
    val credentials: MutableList<AccountCredentials> = mutableListOf(),
    val devices: MutableMap<String, String> = mutableMapOf()
) {
    // Registration state
    val registrationState: RegistrationState
        get() = RegistrationState.fromString(volatileDetails[ConfigKey.ACCOUNT_REGISTRATION_STATUS.key] ?: "")

    val isRegistered: Boolean
        get() = registrationState == RegistrationState.REGISTERED

    val isEnabled: Boolean
        get() = details[ConfigKey.ACCOUNT_ENABLE.key]?.toBoolean() ?: true

    // Account type
    val isJami: Boolean
        get() = details[ConfigKey.ACCOUNT_TYPE.key] == AccountConfig.ACCOUNT_TYPE_JAMI

    val isSip: Boolean
        get() = details[ConfigKey.ACCOUNT_TYPE.key] == AccountConfig.ACCOUNT_TYPE_SIP

    // Display info
    val displayName: String
        get() = details[ConfigKey.ACCOUNT_DISPLAYNAME.key] ?: ""

    val username: String
        get() = if (isJami) {
            details[ConfigKey.ACCOUNT_USERNAME.key] ?: ""
        } else {
            details[ConfigKey.ACCOUNT_USERNAME.key] ?: ""
        }

    val alias: String
        get() = details[ConfigKey.ACCOUNT_ALIAS.key] ?: ""

    val displayUsername: String
        get() = alias.ifEmpty { username.ifEmpty { accountId } }

    // Host/Server
    val host: String
        get() = details[ConfigKey.ACCOUNT_HOSTNAME.key] ?: ""

    // Registered name (for Jami accounts)
    val registeredName: String
        get() = volatileDetails[ConfigKey.ACCOUNT_REGISTERED_NAME.key] ?: ""

    val hasRegisteredName: Boolean
        get() = registeredName.isNotEmpty()

    // DHT Proxy
    var isDhtProxyEnabled: Boolean
        get() = details[ConfigKey.ACCOUNT_PROXY_ENABLED.key]?.toBoolean() ?: false
        set(value) { details[ConfigKey.ACCOUNT_PROXY_ENABLED.key] = value.toString() }

    // State for username registration in progress
    var registeringUsername: Boolean = false

    enum class RegistrationState {
        UNREGISTERED,
        TRYING,
        REGISTERED,
        ERROR_GENERIC,
        ERROR_AUTH,
        ERROR_NETWORK,
        ERROR_HOST,
        ERROR_SERVICE_UNAVAILABLE,
        ERROR_NEED_MIGRATION,
        INITIALIZING;

        companion object {
            fun fromString(state: String): RegistrationState = when (state) {
                "UNREGISTERED" -> UNREGISTERED
                "TRYING" -> TRYING
                "REGISTERED" -> REGISTERED
                "ERROR_GENERIC" -> ERROR_GENERIC
                "ERROR_AUTH" -> ERROR_AUTH
                "ERROR_NETWORK" -> ERROR_NETWORK
                "ERROR_HOST" -> ERROR_HOST
                "ERROR_SERVICE_UNAVAILABLE" -> ERROR_SERVICE_UNAVAILABLE
                "ERROR_NEED_MIGRATION" -> ERROR_NEED_MIGRATION
                "INITIALIZING" -> INITIALIZING
                else -> UNREGISTERED
            }
        }
    }
}

@Serializable
data class AccountCredentials(
    val username: String,
    val password: String,
    val realm: String = "*"
) {
    fun toMap(): Map<String, String> = mapOf(
        ConfigKey.ACCOUNT_USERNAME.key to username,
        ConfigKey.ACCOUNT_PASSWORD.key to password,
        ConfigKey.ACCOUNT_REALM.key to realm
    )

    companion object {
        fun fromMap(map: Map<String, String>): AccountCredentials = AccountCredentials(
            username = map[ConfigKey.ACCOUNT_USERNAME.key] ?: "",
            password = map[ConfigKey.ACCOUNT_PASSWORD.key] ?: "",
            realm = map[ConfigKey.ACCOUNT_REALM.key] ?: "*"
        )
    }
}

object AccountConfig {
    const val ACCOUNT_TYPE_JAMI = "RING"
    const val ACCOUNT_TYPE_SIP = "SIP"
}
