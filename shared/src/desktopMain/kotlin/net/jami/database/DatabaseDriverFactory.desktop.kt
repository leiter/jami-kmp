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
package net.jami.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File

/**
 * Desktop/JVM implementation of DatabaseDriverFactory.
 * Uses JdbcSqliteDriver with JDBC SQLite.
 */
actual class DatabaseDriverFactory(
    private val databaseDir: String? = null
) {
    actual fun createDriver(dbName: String): SqlDriver {
        val dbPath = if (databaseDir != null) {
            File(databaseDir).mkdirs()
            "jdbc:sqlite:$databaseDir/$dbName.db"
        } else {
            // Use user home directory
            val userHome = System.getProperty("user.home")
            val jamiDir = File(userHome, ".jami")
            jamiDir.mkdirs()
            "jdbc:sqlite:${jamiDir.absolutePath}/$dbName.db"
        }

        return JdbcSqliteDriver(dbPath).also { openSchema(it) }
    }
}

/**
 * Creates or migrates the schema, tracking the version in `PRAGMA user_version` like the
 * Android/native drivers do. Databases written before versioning was tracked here have
 * user_version 0 but already hold the tables; they are adopted at the version their tables show.
 */
internal fun openSchema(driver: SqlDriver) {
    val schema = JamiDatabase.Schema
    val stored = driver.userVersion()
    val current = stored.takeIf { it > 0 } ?: legacyVersion(driver)
    when {
        current == 0L -> schema.create(driver)
        current < schema.version -> schema.migrate(driver, current, schema.version)
    }
    if (stored != schema.version) driver.execute(null, "PRAGMA user_version = ${schema.version}", 0)
}

private fun SqlDriver.userVersion(): Long =
    executeQuery(null, "PRAGMA user_version", { c -> QueryResult.Value(if (c.next().value) c.getLong(0) else null) }, 0)
        .value ?: 0L

/** 0 for an empty database, otherwise the schema version its (unversioned) tables match. */
private fun legacyVersion(driver: SqlDriver): Long {
    fun hasTable(name: String) = driver.executeQuery(
        null,
        "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?",
        { c -> QueryResult.Value(c.next().value) },
        1,
    ) { bindString(0, name) }.value
    return when {
        !hasTable("conversation") -> 0L
        hasTable("outbox_message") -> 2L
        else -> 1L
    }
}
