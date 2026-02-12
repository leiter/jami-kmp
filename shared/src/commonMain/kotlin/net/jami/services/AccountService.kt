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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.jami.model.Account
import net.jami.model.AccountConfig
import net.jami.model.AccountCredentials
import net.jami.model.ConfigKey
import net.jami.model.Conversation
import net.jami.model.TrustRequest
import net.jami.model.Uri

/**
 * Service for managing Jami accounts.
 *
 * Ported from: jami-client-android/jami-android/libjamiclient/src/main/kotlin/net/jami/services/AccountService.kt
 *
 * Key changes from original:
 * - RxJava Observable/Subject → Kotlin Flow/StateFlow
 * - Dagger injection → Koin or constructor injection
 * - Synchronous daemon calls remain, but callbacks use Flow
 */
class AccountService(
    private val daemonBridge: DaemonBridge,
    private val scope: CoroutineScope
) {
    // ==================== State Flows ====================

    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()

    private val _currentAccount = MutableStateFlow<Account?>(null)
    val currentAccount: StateFlow<Account?> = _currentAccount.asStateFlow()

    private val _accountEvents = MutableSharedFlow<AccountEvent>()
    val accountEvents: SharedFlow<AccountEvent> = _accountEvents.asSharedFlow()

    private val _incomingMessages = MutableSharedFlow<IncomingMessage>()
    val incomingMessages: SharedFlow<IncomingMessage> = _incomingMessages.asSharedFlow()

    private val accountsMap = mutableMapOf<String, Account>()

    // ==================== Account Queries ====================

    /**
     * Check if there's at least one SIP account.
     */
    fun hasSipAccount(): Boolean = accountsMap.values.any { it.isSip }

    /**
     * Check if there's at least one Jami account.
     */
    fun hasJamiAccount(): Boolean = accountsMap.values.any { it.isJami }

    /**
     * Get an account by ID.
     */
    fun getAccount(accountId: String): Account? = accountsMap[accountId]

    /**
     * Get a new unique account name with the given prefix.
     * The prefix can contain %s or %d which will be replaced with a number.
     */
    fun getNewAccountName(prefix: String): String {
        // Replace format specifiers with empty or number
        var name = prefix.replace(Regex("%[sd]"), "").trim()
        if (accountsMap.values.none { it.alias == name }) {
            return name
        }
        var num = 1
        do {
            num++
            name = prefix.replace(Regex("%[sd]"), num.toString()).trim()
        } while (accountsMap.values.any { it.alias == name })
        return name
    }

    // ==================== Account Loading ====================

    /**
     * Load all accounts from the daemon.
     */
    fun loadAccounts() {
        val accountIds = daemonBridge.getAccountList()
        val loadedAccounts = accountIds.map { accountId ->
            val details = daemonBridge.getAccountDetails(accountId)
            val volatileDetails = daemonBridge.getVolatileAccountDetails(accountId)
            val credentials = daemonBridge.getCredentials(accountId)

            accountsMap[accountId]?.apply {
                this.details.putAll(details)
                this.volatileDetails.putAll(volatileDetails)
            } ?: Account(
                accountId = accountId,
                details = details.toMutableMap(),
                volatileDetails = volatileDetails.toMutableMap(),
                credentials = credentials.map { AccountCredentials.fromMap(it) }.toMutableList()
            )
        }

        accountsMap.clear()
        loadedAccounts.forEach { accountsMap[it.accountId] = it }
        _accounts.value = loadedAccounts

        if (_currentAccount.value == null && loadedAccounts.isNotEmpty()) {
            _currentAccount.value = loadedAccounts.first()
        }
    }

    /**
     * Load accounts from daemon with initial connection state.
     */
    fun loadAccountsFromDaemon(isConnected: Boolean) {
        loadAccounts()
        setAccountsActive(isConnected)
    }

    // ==================== Account Creation ====================

    /**
     * Get the default template for an account type.
     */
    fun getAccountTemplate(accountType: String): Map<String, String> {
        return daemonBridge.getAccountTemplate(accountType)
    }

    /**
     * Create a new account with the given details.
     */
    fun addAccount(details: Map<String, String>): String {
        return daemonBridge.addAccount(details)
    }

    /**
     * Create a new Jami account.
     */
    fun createJamiAccount(
        displayName: String,
        password: String? = null,
        pin: String? = null,
        archivePath: String? = null
    ): String {
        val details = mutableMapOf(
            ConfigKey.ACCOUNT_TYPE.key to AccountConfig.ACCOUNT_TYPE_JAMI,
            ConfigKey.ACCOUNT_DISPLAYNAME.key to displayName,
            ConfigKey.VIDEO_ENABLED.key to "true",
            ConfigKey.ACCOUNT_AUTOANSWER.key to "false"
        )

        password?.let { details[ConfigKey.ACCOUNT_ARCHIVE_PASSWORD.key] = it }
        pin?.let { details[ConfigKey.ACCOUNT_ARCHIVE_PIN.key] = it }
        archivePath?.let { details[ConfigKey.ACCOUNT_ARCHIVE_PATH.key] = it }

        return daemonBridge.addAccount(details)
    }

    /**
     * Create a new SIP account.
     */
    fun createSipAccount(
        alias: String,
        hostname: String,
        username: String,
        password: String
    ): String {
        val details = mutableMapOf(
            ConfigKey.ACCOUNT_TYPE.key to AccountConfig.ACCOUNT_TYPE_SIP,
            ConfigKey.ACCOUNT_ALIAS.key to alias,
            ConfigKey.ACCOUNT_HOSTNAME.key to hostname,
            ConfigKey.ACCOUNT_USERNAME.key to username,
            ConfigKey.ACCOUNT_PASSWORD.key to password
        )

        return daemonBridge.addAccount(details)
    }

    /**
     * Remove an account.
     */
    fun removeAccount(accountId: String) {
        daemonBridge.removeAccount(accountId)
        accountsMap.remove(accountId)
        _accounts.value = accountsMap.values.toList()

        if (_currentAccount.value?.accountId == accountId) {
            _currentAccount.value = _accounts.value.firstOrNull()
        }
    }

    // ==================== Account Settings ====================

    /**
     * Set the current active account.
     */
    fun setCurrentAccount(account: Account) {
        if (_accounts.value.isEmpty() || _accounts.value[0] === account) return

        // Reorder accounts list with selected account first
        val orderedAccountIds = mutableListOf(account.accountId)
        for (a in _accounts.value) {
            if (a.accountId != account.accountId) {
                orderedAccountIds.add(a.accountId)
            }
        }
        setAccountOrder(orderedAccountIds)
        _currentAccount.value = account
    }

    /**
     * Set the order of accounts.
     */
    fun setAccountOrder(accountIds: List<String>) {
        val order = accountIds.joinToString("/")
        daemonBridge.setAccountsOrder(order)
    }

    /**
     * Enable or disable an account (registration).
     */
    fun setAccountEnabled(accountId: String, enabled: Boolean) {
        daemonBridge.sendRegister(accountId, enabled)
    }

    /**
     * Activate or deactivate an account.
     */
    fun setAccountActive(accountId: String, active: Boolean) {
        daemonBridge.setAccountActive(accountId, active)
    }

    /**
     * Activate or deactivate all accounts.
     */
    fun setAccountsActive(active: Boolean) {
        for (account in accountsMap.values) {
            // If proxy is enabled, account is considered always active
            if (account.isDhtProxyEnabled) {
                daemonBridge.setAccountActive(account.accountId, true)
            } else {
                daemonBridge.setAccountActive(account.accountId, active)
            }
        }
    }

    /**
     * Update account details.
     */
    fun setAccountDetails(accountId: String, details: Map<String, String>) {
        daemonBridge.setAccountDetails(accountId, details)
        accountsMap[accountId]?.details?.putAll(details)
    }

    /**
     * Enable or disable video for all accounts.
     */
    fun setAccountsVideoEnabled(isEnabled: Boolean) {
        for (account in accountsMap.values) {
            account.details[ConfigKey.VIDEO_ENABLED.key] = isEnabled.toString()
        }
    }

    // ==================== Credentials ====================

    /**
     * Get credentials for an account.
     */
    fun getCredentials(accountId: String): List<Map<String, String>> {
        return daemonBridge.getCredentials(accountId)
    }

    /**
     * Set credentials for an account.
     */
    fun setCredentials(accountId: String, credentials: List<Map<String, String>>) {
        daemonBridge.setCredentials(accountId, credentials)
    }

    // ==================== Device Management ====================

    /**
     * Get known devices for an account.
     */
    fun getKnownRingDevices(accountId: String): Map<String, String> {
        return daemonBridge.getKnownRingDevices(accountId)
    }

    /**
     * Revoke a device.
     */
    fun revokeDevice(accountId: String, deviceId: String, scheme: String, password: String) {
        daemonBridge.revokeDevice(accountId, deviceId, scheme, password)
    }

    /**
     * Rename a device.
     */
    fun renameDevice(accountId: String, newName: String) {
        val details = daemonBridge.getAccountDetails(accountId).toMutableMap()
        details[ConfigKey.ACCOUNT_DEVICE_NAME.key] = newName
        daemonBridge.setAccountDetails(accountId, details)
        accountsMap[accountId]?.details?.set(ConfigKey.ACCOUNT_DEVICE_NAME.key, newName)
    }

    // ==================== Profile ====================

    /**
     * Update account profile with display name and avatar.
     * @param flag 0 = path, 1 = base64, 2 = clear avatar
     */
    fun updateProfile(accountId: String, displayName: String, avatar: String = "", fileType: String = "", flag: Int = 0) {
        daemonBridge.updateProfile(accountId, displayName, avatar, fileType, flag)
    }

    // ==================== Export/Import ====================

    /**
     * Export account to file.
     */
    fun exportToFile(accountId: String, path: String, scheme: String, password: String): Boolean {
        return daemonBridge.exportToFile(accountId, path, scheme, password)
    }

    /**
     * Change account password.
     */
    fun changeAccountPassword(accountId: String, oldPassword: String, newPassword: String): Boolean {
        return daemonBridge.changeAccountPassword(accountId, oldPassword, newPassword)
    }

    // ==================== Name Service ====================

    /**
     * Register a username on the name server.
     */
    fun registerName(accountId: String, name: String, scheme: String = "", password: String = ""): Boolean {
        accountsMap[accountId]?.let { account ->
            if (account.registeringUsername) return false
            account.registeringUsername = true
        }
        return daemonBridge.registerName(accountId, name, scheme, password)
    }

    /**
     * Look up a registered name.
     */
    fun lookupName(accountId: String, name: String): Boolean {
        return daemonBridge.lookupName(accountId, "", name)
    }

    /**
     * Look up an address on the name server.
     */
    fun lookupAddress(accountId: String, address: String): Boolean {
        return daemonBridge.lookupAddress(accountId, "", address)
    }

    /**
     * Search for users.
     */
    fun searchUser(accountId: String, query: String): Boolean {
        return daemonBridge.searchUser(accountId, query)
    }

    // ==================== Contact Operations ====================

    /**
     * Add a contact.
     */
    fun addContact(accountId: String, uri: String) {
        daemonBridge.addContact(accountId, uri)
    }

    /**
     * Remove a contact.
     */
    fun removeContact(accountId: String, uri: String, block: Boolean = false) {
        daemonBridge.removeContact(accountId, uri, block)
    }

    /**
     * Get contacts for an account.
     */
    fun getContacts(accountId: String): List<Map<String, String>> {
        return daemonBridge.getContacts(accountId)
    }

    /**
     * Subscribe to buddy presence.
     */
    fun subscribeBuddy(accountId: String, uri: String, subscribe: Boolean) {
        daemonBridge.subscribeBuddy(accountId, uri, subscribe)
    }

    // ==================== Trust Requests ====================

    /**
     * Get trust requests for an account.
     */
    fun getTrustRequests(accountId: String): List<Map<String, String>> {
        return daemonBridge.getTrustRequests(accountId)
    }

    /**
     * Accept a trust request.
     */
    fun acceptTrustRequest(accountId: String, from: Uri) {
        if (from.isSwarm) {
            daemonBridge.acceptConversationRequest(accountId, from.rawRingId)
        } else {
            daemonBridge.acceptTrustRequest(accountId, from.rawRingId)
        }
    }

    /**
     * Discard (refuse) a trust request.
     */
    fun discardTrustRequest(accountId: String, contactUri: Uri): Boolean {
        return if (contactUri.isSwarm) {
            daemonBridge.declineConversationRequest(accountId, contactUri.rawRingId)
            true
        } else {
            daemonBridge.discardTrustRequest(accountId, contactUri.rawRingId)
            true
        }
    }

    /**
     * Send a trust request.
     */
    fun sendTrustRequest(accountId: String, to: Uri, payload: ByteArray = ByteArray(0)) {
        daemonBridge.sendTrustRequest(accountId, to.rawRingId, payload)
    }

    // ==================== Conversation Operations ====================

    /**
     * Get conversation requests for an account.
     */
    fun getConversationRequests(accountId: String): List<Map<String, String>> {
        return daemonBridge.getConversationRequests(accountId)
    }

    /**
     * Start a new conversation.
     */
    fun startConversation(accountId: String): String {
        return daemonBridge.startConversation(accountId)
    }

    /**
     * Start a conversation with initial members.
     */
    fun startConversation(accountId: String, initialMembers: Collection<String>): String {
        val conversationId = daemonBridge.startConversation(accountId)
        for (member in initialMembers) {
            daemonBridge.addConversationMember(accountId, conversationId, member)
        }
        return conversationId
    }

    /**
     * Remove a conversation.
     */
    fun removeConversation(accountId: String, conversationUri: Uri) {
        daemonBridge.removeConversation(accountId, conversationUri.rawRingId)
    }

    /**
     * Send a message to a conversation.
     */
    fun sendConversationMessage(accountId: String, conversationUri: Uri, message: String, replyTo: String? = null, flag: Int = 0) {
        daemonBridge.sendMessage(accountId, conversationUri.rawRingId, message, replyTo ?: "", flag)
    }

    /**
     * Delete a conversation message.
     */
    fun deleteConversationMessage(accountId: String, conversationUri: Uri, messageId: String) {
        sendConversationMessage(accountId, conversationUri, "", messageId, 1)
    }

    /**
     * Edit a conversation message.
     */
    fun editConversationMessage(accountId: String, conversationUri: Uri, message: String, messageId: String) {
        sendConversationMessage(accountId, conversationUri, message, messageId, 1)
    }

    /**
     * Send a reaction to a message.
     */
    fun sendConversationReaction(accountId: String, conversationUri: Uri, emoji: String, replyTo: String) {
        sendConversationMessage(accountId, conversationUri, emoji, replyTo, 2)
    }

    /**
     * Set message as displayed.
     */
    fun setMessageDisplayed(accountId: String, conversationUri: Uri, messageId: String) {
        daemonBridge.setMessageDisplayed(accountId, conversationUri.uri, messageId, 3)
    }

    /**
     * Update conversation info (title, description, avatar).
     */
    fun updateConversationInfo(accountId: String, conversationId: String, info: Map<String, String>) {
        daemonBridge.updateConversationInfo(accountId, conversationId, info)
    }

    /**
     * Set conversation preferences.
     */
    fun setConversationPreferences(accountId: String, conversationId: String, prefs: Map<String, String>) {
        daemonBridge.setConversationPreferences(accountId, conversationId, prefs)
    }

    /**
     * Get conversation preferences.
     */
    fun getConversationPreferences(accountId: String, conversationId: String): Map<String, String> {
        return daemonBridge.getConversationPreferences(accountId, conversationId)
    }

    /**
     * Add members to a conversation.
     */
    fun addConversationMembers(accountId: String, conversationId: String, uris: List<Uri>) {
        for (uri in uris) {
            daemonBridge.addConversationMember(accountId, conversationId, uri.rawRingId)
        }
    }

    /**
     * Remove a member from a conversation.
     */
    fun removeConversationMember(accountId: String, conversationId: String, uri: Uri) {
        daemonBridge.removeConversationMember(accountId, conversationId, uri.rawRingId)
    }

    // ==================== Codec Operations ====================

    /**
     * Get codec list.
     */
    fun getCodecList(): List<Long> {
        return daemonBridge.getCodecList()
    }

    /**
     * Get active codec list for an account.
     */
    fun getActiveCodecList(accountId: String): List<Long> {
        return daemonBridge.getActiveCodecList(accountId)
    }

    /**
     * Set active codec list for an account.
     */
    fun setActiveCodecList(accountId: String, codecs: List<Long>) {
        daemonBridge.setActiveCodecList(accountId, codecs)
    }

    /**
     * Get codec details.
     */
    fun getCodecDetails(accountId: String, codecId: Long): Map<String, String> {
        return daemonBridge.getCodecDetails(accountId, codecId)
    }

    // ==================== Push Notifications ====================

    /**
     * Set push notification token.
     */
    fun setPushNotificationToken(token: String) {
        daemonBridge.setPushNotificationToken(token)
    }

    /**
     * Set push notification config.
     */
    fun setPushNotificationConfig(token: String = "", topic: String = "", platform: String = "") {
        daemonBridge.setPushNotificationConfig(mapOf(
            "token" to token,
            "topic" to topic,
            "platform" to platform
        ))
    }

    /**
     * Handle received push notification.
     */
    fun pushNotificationReceived(from: String, data: Map<String, String>) {
        daemonBridge.pushNotificationReceived(from, data)
    }

    // ==================== Proxy Settings ====================

    /**
     * Enable or disable DHT proxy for all Jami accounts.
     */
    fun setProxyEnabled(enabled: Boolean) {
        for (account in accountsMap.values) {
            if (account.isJami && account.isDhtProxyEnabled != enabled) {
                account.isDhtProxyEnabled = enabled
                val details = daemonBridge.getAccountDetails(account.accountId).toMutableMap()
                details[ConfigKey.ACCOUNT_PROXY_ENABLED.key] = if (enabled) "true" else "false"
                daemonBridge.setAccountDetails(account.accountId, details)
            }
        }
    }

    // ==================== Callback Handlers ====================

    internal fun onAccountsChanged() {
        scope.launch {
            loadAccounts()
            _accountEvents.emit(AccountEvent.AccountsChanged)
        }
    }

    internal fun onRegistrationStateChanged(
        accountId: String,
        state: String,
        code: Int,
        detail: String
    ) {
        scope.launch {
            accountsMap[accountId]?.let { account ->
                account.volatileDetails[ConfigKey.ACCOUNT_REGISTRATION_STATUS.key] = state
                account.volatileDetails[ConfigKey.ACCOUNT_REGISTRATION_STATE_CODE.key] = code.toString()
                account.volatileDetails[ConfigKey.ACCOUNT_REGISTRATION_STATE_DESC.key] = detail

                _accounts.value = accountsMap.values.toList()
                _accountEvents.emit(
                    AccountEvent.RegistrationStateChanged(accountId, state, code, detail)
                )
            }
        }
    }

    internal fun onAccountDetailsChanged(accountId: String, details: Map<String, String>) {
        scope.launch {
            accountsMap[accountId]?.details?.putAll(details)
            _accounts.value = accountsMap.values.toList()
            _accountEvents.emit(AccountEvent.DetailsChanged(accountId, details))
        }
    }

    internal fun onVolatileAccountDetailsChanged(accountId: String, details: Map<String, String>) {
        scope.launch {
            accountsMap[accountId]?.volatileDetails?.putAll(details)
            _accounts.value = accountsMap.values.toList()
            _accountEvents.emit(AccountEvent.VolatileDetailsChanged(accountId, details))
        }
    }

    internal fun onKnownDevicesChanged(accountId: String, devices: Map<String, String>) {
        scope.launch {
            accountsMap[accountId]?.devices?.clear()
            accountsMap[accountId]?.devices?.putAll(devices)
            _accountEvents.emit(AccountEvent.KnownDevicesChanged(accountId, devices))
        }
    }

    internal fun onDeviceRevocationEnded(accountId: String, deviceId: String, state: Int) {
        scope.launch {
            if (state == 0) {
                accountsMap[accountId]?.devices?.remove(deviceId)
            }
            _accountEvents.emit(AccountEvent.DeviceRevocationEnded(accountId, deviceId, state))
        }
    }

    internal fun onMigrationEnded(accountId: String, state: String) {
        scope.launch {
            _accountEvents.emit(AccountEvent.MigrationEnded(accountId, state))
        }
    }

    internal fun onNameRegistrationEnded(accountId: String, state: Int, name: String) {
        scope.launch {
            accountsMap[accountId]?.registeringUsername = false
            if (state == 0) {
                accountsMap[accountId]?.let { account ->
                    account.volatileDetails[ConfigKey.ACCOUNT_REGISTERED_NAME.key] = name
                    _accounts.value = accountsMap.values.toList()
                }
            }
            _accountEvents.emit(AccountEvent.NameRegistrationEnded(accountId, state, name))
        }
    }

    internal fun onRegisteredNameFound(
        accountId: String,
        state: Int,
        address: String,
        name: String
    ) {
        scope.launch {
            _accountEvents.emit(AccountEvent.RegisteredNameFound(accountId, state, address, name))
        }
    }

    internal fun onUserSearchEnded(accountId: String, state: Int, query: String, results: List<Map<String, String>>) {
        scope.launch {
            _accountEvents.emit(AccountEvent.UserSearchEnded(accountId, state, query, results))
        }
    }

    internal fun onContactAdded(accountId: String, uri: String, confirmed: Boolean) {
        scope.launch {
            _accountEvents.emit(AccountEvent.ContactAdded(accountId, uri, confirmed))
        }
    }

    internal fun onContactRemoved(accountId: String, uri: String, banned: Boolean) {
        scope.launch {
            _accountEvents.emit(AccountEvent.ContactRemoved(accountId, uri, banned))
        }
    }

    internal fun onIncomingTrustRequest(accountId: String, conversationId: String, from: String, payload: ByteArray, receivedTime: Long) {
        scope.launch {
            val conversationUri = if (conversationId.isNotEmpty()) {
                Uri(Uri.SWARM_SCHEME, conversationId)
            } else {
                Uri.fromString(from)
            }
            val request = TrustRequest(
                accountId = accountId,
                from = Uri.fromString(from),
                timestamp = receivedTime,
                conversationUri = conversationUri,
                mode = if (conversationId.isNotEmpty()) Conversation.Mode.OneToOne else Conversation.Mode.Request
            )
            _accountEvents.emit(AccountEvent.IncomingTrustRequest(accountId, request))
        }
    }

    internal fun onIncomingAccountMessage(accountId: String, messageId: String?, callId: String?, from: String, messages: Map<String, String>) {
        scope.launch {
            _incomingMessages.emit(IncomingMessage(accountId, messageId, callId, from, messages))
        }
    }

    internal fun onAccountProfileReceived(accountId: String, name: String, photo: String) {
        scope.launch {
            _accountEvents.emit(AccountEvent.ProfileReceived(accountId, name, photo))
        }
    }

    companion object {
        const val ACCOUNT_SCHEME_NONE = ""
        const val ACCOUNT_SCHEME_PASSWORD = "password"
        const val ACCOUNT_SCHEME_KEY = "key"
    }
}

/**
 * Incoming message from daemon.
 */
data class IncomingMessage(
    val accountId: String,
    val messageId: String?,
    val callId: String?,
    val from: String,
    val messages: Map<String, String>
)

/**
 * Events emitted by AccountService.
 */
sealed class AccountEvent {
    data object AccountsChanged : AccountEvent()

    data class RegistrationStateChanged(
        val accountId: String,
        val state: String,
        val code: Int,
        val detail: String
    ) : AccountEvent()

    data class DetailsChanged(
        val accountId: String,
        val details: Map<String, String>
    ) : AccountEvent()

    data class VolatileDetailsChanged(
        val accountId: String,
        val details: Map<String, String>
    ) : AccountEvent()

    data class KnownDevicesChanged(
        val accountId: String,
        val devices: Map<String, String>
    ) : AccountEvent()

    data class DeviceRevocationEnded(
        val accountId: String,
        val deviceId: String,
        val state: Int
    ) : AccountEvent()

    data class MigrationEnded(
        val accountId: String,
        val state: String
    ) : AccountEvent()

    data class NameRegistrationEnded(
        val accountId: String,
        val state: Int,
        val name: String
    ) : AccountEvent()

    data class RegisteredNameFound(
        val accountId: String,
        val state: Int,
        val address: String,
        val name: String
    ) : AccountEvent()

    data class UserSearchEnded(
        val accountId: String,
        val state: Int,
        val query: String,
        val results: List<Map<String, String>>
    ) : AccountEvent()

    data class ContactAdded(
        val accountId: String,
        val uri: String,
        val confirmed: Boolean
    ) : AccountEvent()

    data class ContactRemoved(
        val accountId: String,
        val uri: String,
        val banned: Boolean
    ) : AccountEvent()

    data class IncomingTrustRequest(
        val accountId: String,
        val request: TrustRequest
    ) : AccountEvent()

    data class ProfileReceived(
        val accountId: String,
        val name: String,
        val photo: String
    ) : AccountEvent()
}

/**
 * Lookup state for name registration.
 */
enum class LookupState(val value: Int) {
    Success(0),
    Invalid(1),
    NotFound(2),
    NetworkError(3);

    companion object {
        fun fromInt(state: Int): LookupState = entries.getOrElse(state) { NetworkError }
    }
}
