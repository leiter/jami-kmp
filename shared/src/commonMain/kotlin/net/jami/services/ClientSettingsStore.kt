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

import net.jami.database.JamiDatabase

/**
 * Per-account client-side storage for settings and drafts, keyed by the `KMP.*` names in
 * `SettingsKeys`.
 *
 * This exists because the original store did not work. Both repositories wrote their JSON into
 * daemon account details, counting on the daemon to persist and sync it; `setAccountDetails`
 * parses the map into typed config and drops everything it does not recognise, so nothing was
 * ever kept (see `doc/status-note-open-ends.md`). Values live on the device now, as they do in
 * the reference client — cross-device sync is given up until there is a daemon-side store that
 * round-trips.
 */
interface ClientSettingsStore {
    /** Every stored value for an account, as key -> JSON. */
    fun all(accountId: String): Map<String, String>

    fun get(accountId: String, key: String): String?

    fun put(accountId: String, key: String, value: String)

    fun remove(accountId: String, key: String)

    /** Drop everything stored for an account; for account deletion. */
    fun clear(accountId: String)
}

/** The real store: survives restarts on Android, iOS, macOS and desktop. */
class SqlDelightClientSettingsStore(database: JamiDatabase) : ClientSettingsStore {
    private val queries = database.clientSettingsQueries

    override fun all(accountId: String): Map<String, String> =
        queries.selectForAccount(accountId).executeAsList()
            .associate { it.setting_key to it.setting_value }

    override fun get(accountId: String, key: String): String? =
        queries.selectOne(accountId, key).executeAsOneOrNull()

    override fun put(accountId: String, key: String, value: String) {
        queries.upsert(accountId, key, value)
    }

    override fun remove(accountId: String, key: String) {
        queries.deleteKey(accountId, key)
    }

    override fun clear(accountId: String) {
        queries.deleteForAccount(accountId)
    }
}

/** Non-persistent store for platforms without the database (web) and for tests. */
class InMemoryClientSettingsStore : ClientSettingsStore {
    private val values = mutableMapOf<String, MutableMap<String, String>>()

    override fun all(accountId: String): Map<String, String> = values[accountId]?.toMap() ?: emptyMap()

    override fun get(accountId: String, key: String): String? = values[accountId]?.get(key)

    override fun put(accountId: String, key: String, value: String) {
        values.getOrPut(accountId) { mutableMapOf() }[key] = value
    }

    override fun remove(accountId: String, key: String) {
        values[accountId]?.remove(key)
    }

    override fun clear(accountId: String) {
        values.remove(accountId)
    }
}
