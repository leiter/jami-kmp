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
import net.jami.model.ConfigKey

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
    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()

    private val _currentAccount = MutableStateFlow<Account?>(null)
    val currentAccount: StateFlow<Account?> = _currentAccount.asStateFlow()

    private val _accountEvents = MutableSharedFlow<AccountEvent>()
    val accountEvents: SharedFlow<AccountEvent> = _accountEvents.asSharedFlow()

    private val accountsMap = mutableMapOf<String, Account>()

    /**
     * Load all accounts from the daemon.
     */
    fun loadAccounts() {
        val accountIds = daemonBridge.getAccountList()
        val loadedAccounts = accountIds.map { accountId ->
            val details = daemonBridge.getAccountDetails(accountId)
            Account(accountId = accountId, details = details.toMutableMap())
        }

        accountsMap.clear()
        loadedAccounts.forEach { accountsMap[it.accountId] = it }
        _accounts.value = loadedAccounts

        if (_currentAccount.value == null && loadedAccounts.isNotEmpty()) {
            _currentAccount.value = loadedAccounts.first()
        }
    }

    /**
     * Get an account by ID.
     */
    fun getAccount(accountId: String): Account? = accountsMap[accountId]

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

    /**
     * Set the current active account.
     */
    fun setCurrentAccount(account: Account) {
        _currentAccount.value = account
    }

    /**
     * Enable or disable an account.
     */
    fun setAccountEnabled(accountId: String, enabled: Boolean) {
        daemonBridge.setAccountActive(accountId, enabled)
        accountsMap[accountId]?.let { account ->
            account.details[ConfigKey.ACCOUNT_ENABLE.key] = enabled.toString()
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
     * Register a username on the name server.
     */
    fun registerName(accountId: String, name: String, password: String = ""): Boolean {
        return daemonBridge.registerName(accountId, name, "", password)
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

    // Callback handlers (called by DaemonCallbacks implementation)

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

    internal fun onNameRegistrationEnded(accountId: String, state: Int, name: String) {
        scope.launch {
            if (state == 0) { // Success
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
}

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
}
