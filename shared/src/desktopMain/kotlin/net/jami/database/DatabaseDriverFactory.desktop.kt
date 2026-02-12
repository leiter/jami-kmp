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

        val driver = JdbcSqliteDriver(dbPath)
        JamiDatabase.Schema.create(driver)
        return driver
    }
}
