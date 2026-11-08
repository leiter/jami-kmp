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

import android.content.Context
import net.jami.model.ConfigKey
import net.jami.utils.Log
import java.io.File

/**
 * Puts the bundled ringtones on disk and points every account at one.
 *
 * Mirrors `JamiApplication.onCreate()` in jami-android-client, which copies `assets/ringtones`
 * into the app's files directory on first start. Without it the daemon falls back to a path
 * compiled into libjami for the *official* client (`/data/data/cx.ring/...`), so no ringtone
 * can ever be played and the ringtone picker in account settings has nothing to list.
 *
 * Copying alone is not enough: the daemon keeps using its own default unless the account's
 * `Account.ringtonePath` is set, so [repairAccounts] writes it.
 */
class AndroidRingtoneInstaller(
    private val context: Context,
    private val daemonBridge: DaemonBridgeApi,
) {

    /** `filesDir/ringtones`, the directory the daemon is pointed at. */
    private val ringtoneDir: File
        get() = File(context.filesDir, RingtonePolicy.RINGTONE_DIR)

    /** The bundled default, whether or not it has been installed yet. */
    val defaultRingtone: File
        get() = File(ringtoneDir, RingtonePolicy.DEFAULT_RINGTONE_FILE)

    /**
     * Copies every bundled ringtone that is not already on disk.
     *
     * Per-file rather than all-or-nothing on the directory: a half-finished first run (killed
     * mid-copy) would otherwise leave the rest missing forever, since the directory exists.
     * Cheap on later starts — it only stats.
     *
     * @return true when the default ringtone is present afterwards.
     */
    fun install(): Boolean {
        try {
            val assets = context.assets.list(RingtonePolicy.RINGTONE_DIR).orEmpty()
            if (assets.isEmpty()) {
                Log.w(TAG, "No ringtones bundled in assets/${RingtonePolicy.RINGTONE_DIR}")
                return false
            }
            if (!ringtoneDir.exists() && !ringtoneDir.mkdirs()) {
                Log.w(TAG, "Unable to create $ringtoneDir")
                return false
            }
            var copied = 0
            for (name in assets) {
                val target = File(ringtoneDir, name)
                // A zero-length file is a previous run that died mid-copy, so recopy it.
                if (target.exists() && target.length() > 0) continue
                context.assets.open("${RingtonePolicy.RINGTONE_DIR}/$name").use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                copied++
            }
            if (copied > 0) Log.i(TAG, "Installed $copied ringtone(s) into $ringtoneDir")
            return defaultRingtone.exists() && defaultRingtone.length() > 0
        } catch (e: Exception) {
            Log.w(TAG, "Unable to install bundled ringtones", e)
            return false
        }
    }

    /**
     * Points any account without a usable ringtone at the bundled default.
     *
     * Also covers accounts created before this existed, whose stored path is the unreachable
     * `cx.ring` one — [RingtonePolicy.needsDefaultRingtone] tests that the file resolves, not
     * merely that something is configured.
     *
     * An account whose ringtone the user has chosen is left alone, because that path exists.
     */
    fun repairAccounts() {
        val default = defaultRingtone
        if (!default.exists()) {
            Log.w(TAG, "Default ringtone missing; not repairing accounts")
            return
        }
        for (accountId in daemonBridge.getAccountList()) {
            try {
                val details = daemonBridge.getAccountDetails(accountId)
                val configured = details[ConfigKey.RINGTONE_PATH.key]
                if (!RingtonePolicy.needsDefaultRingtone(configured) { File(it).exists() }) continue
                daemonBridge.setAccountDetails(
                    accountId,
                    details + mapOf(
                        ConfigKey.RINGTONE_PATH.key to default.absolutePath,
                        ConfigKey.RINGTONE_ENABLED.key to "true",
                    ),
                )
                Log.i(TAG, "Account $accountId ringtone was '$configured', set to ${default.absolutePath}")
            } catch (e: Exception) {
                Log.w(TAG, "Unable to set the ringtone for account $accountId", e)
            }
        }
    }

    private companion object {
        const val TAG = "AndroidRingtoneInstaller"
    }
}
