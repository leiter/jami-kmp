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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import net.jami.model.ConfigKey
import net.jami.model.settings.NotificationSettings
import net.jami.repository.SettingsRepository
import net.jami.ui.platform.LocalPrefs
import net.jami.utils.Log

/**
 * Who rings an incoming call on iOS: CallKit, never the daemon.
 *
 * Follows jami-client-ios, which sets `CXProviderConfiguration.ringtoneSound = "default.wav"`
 * (`CallsHelper.swift`) and creates every account with `Account.ringtoneEnabled = false`
 * (`AccountsService.swift`). Only CallKit can make a sound for a locked or backgrounded phone
 * before the call is answered, and a daemon ringing as well would ring twice whenever the app
 * is in the foreground. Android is the other way round (the daemon rings, see
 * `AndroidRingtoneInstaller`), because Android has no CallKit.
 *
 * Two jobs, both started from [start]:
 * - **Keep the daemon's ringtone off** on every account. The reference only sets this at
 *   creation; this client's accounts were created with it on, pointing at a file that does not
 *   exist on iOS, so existing accounts are repaired as well, and new ones as they appear.
 * - **Mirror what CallKit needs into `NSUserDefaults`**: the quiet-hours window and the
 *   "show names in notifications" privacy setting. A call woken by a VoIP push has to be
 *   reported to CallKit synchronously, before the daemon has loaded an account and so before
 *   [SettingsRepository] holds anything but defaults. [ringtoneSoundNow] and [showNamesNow]
 *   therefore read the mirror, never the repository.
 */
class IOSCallRinging(
    private val accountService: AccountService,
    private val daemonBridge: DaemonBridgeApi,
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope,
) {

    fun start() {
        scope.launch {
            accountService.accounts
                .map { accounts -> accounts.map { it.accountId }.toSet() }
                .distinctUntilChanged()
                .collect { disableDaemonRingtone() }
        }
        scope.launch {
            // Only once an account's settings have really loaded: until then the repository
            // holds defaults, and mirroring those would switch quiet hours off for the next
            // push-woken call.
            combine(
                settingsRepository.loadedAccountId,
                settingsRepository.notificationSettings,
                settingsRepository.privacySettings,
            ) { loaded, notifications, privacy ->
                if (loaded == null) null else notifications to privacy.showNamesInNotifications
            }
                .filterNotNull()
                .distinctUntilChanged()
                .collect { (notifications, showNames) ->
                    mirrorQuietHours(notifications)
                    mirrorShowNames(showNames)
                }
        }
    }

    /** Sets `Account.ringtoneEnabled` to false on every account the daemon lists that has it on. */
    internal fun disableDaemonRingtone() {
        for (accountId in daemonBridge.getAccountList()) {
            try {
                val details = daemonBridge.getAccountDetails(accountId)
                if (details[ConfigKey.RINGTONE_ENABLED.key] == "false") continue
                daemonBridge.setAccountDetails(
                    accountId,
                    details + (ConfigKey.RINGTONE_ENABLED.key to "false"),
                )
                // Keep the in-memory copy in step, so a later write of the whole map from it
                // does not switch the daemon's ringtone back on.
                accountService.getAccount(accountId)?.details?.set(ConfigKey.RINGTONE_ENABLED.key, "false")
                Log.i(TAG, "Account $accountId: daemon ringtone switched off; CallKit rings")
            } catch (e: Exception) {
                Log.w(TAG, "Unable to switch off the daemon ringtone for account $accountId", e)
            }
        }
    }

    internal fun mirrorQuietHours(settings: NotificationSettings) {
        LocalPrefs.setBoolean(KEY_QUIET_ENABLED, settings.quietHoursEnabled)
        LocalPrefs.setInt(KEY_QUIET_START, settings.quietHoursStart)
        LocalPrefs.setInt(KEY_QUIET_END, settings.quietHoursEnd)
    }

    internal fun mirrorShowNames(showNames: Boolean) {
        LocalPrefs.setBoolean(KEY_SHOW_NAMES, showNames)
    }

    companion object {
        private const val TAG = "IOSCallRinging"

        /** Bundled in the app target; the same file jami-client-ios ships. */
        const val RINGTONE_SOUND = "default.wav"

        /** One second of silence, bundled in the app target, for quiet hours. */
        const val SILENT_SOUND = "silence.wav"

        internal const val KEY_QUIET_ENABLED = "ios_callkit_quiet_hours_enabled"
        internal const val KEY_QUIET_START = "ios_callkit_quiet_hours_start"
        internal const val KEY_QUIET_END = "ios_callkit_quiet_hours_end"
        internal const val KEY_SHOW_NAMES = "ios_callkit_show_names"

        /**
         * The mirrored "show names in notifications" setting; on until an account's settings
         * have loaded once, matching `PrivacySettings`' default. CallKit's incoming-call screen
         * is a lock-screen notification too, so it obeys the same setting. Synchronous and
         * repository-free, so the PushKit handler can call it.
         */
        fun showNamesNow(): Boolean = LocalPrefs.getBoolean(KEY_SHOW_NAMES, true)

        /**
         * The sound CallKit should ring the next incoming call with: [SILENT_SOUND] inside the
         * mirrored quiet-hours window, [RINGTONE_SOUND] otherwise.
         *
         * Quiet hours silence a call, they do not hide it — the same rule as
         * `NotificationGuard.shouldSilenceCallNotification()` on Android. Whether the phone
         * still vibrates is iOS's own haptics setting; an app cannot ask CallKit for it.
         * Synchronous and repository-free, so the PushKit handler can call it.
         */
        fun ringtoneSoundNow(): String {
            val quiet = QuietHours.isActiveNow(
                enabled = LocalPrefs.getBoolean(KEY_QUIET_ENABLED, false),
                start = LocalPrefs.getInt(KEY_QUIET_START, NotificationSettings().quietHoursStart),
                end = LocalPrefs.getInt(KEY_QUIET_END, NotificationSettings().quietHoursEnd),
            )
            return if (quiet) SILENT_SOUND else RINGTONE_SOUND
        }
    }
}
