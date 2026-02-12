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

import app.cash.sqldelight.db.SqlDriver

/**
 * Factory for creating platform-specific SQLDelight drivers.
 *
 * Each platform provides its own implementation:
 * - Android: AndroidSqliteDriver
 * - iOS/macOS: NativeSqliteDriver
 * - Desktop/JVM: JdbcSqliteDriver
 * - Web: Not supported (in-memory only for testing)
 */
expect class DatabaseDriverFactory {
    /**
     * Create a SQLite driver for the JamiDatabase.
     *
     * @param dbName The name of the database file (without extension)
     * @return A platform-specific SqlDriver
     */
    fun createDriver(dbName: String = "jami"): SqlDriver
}

/**
 * Database schema version and migration support.
 */
object DatabaseSchema {
    const val VERSION = 1
    const val DATABASE_NAME = "jami"
}
