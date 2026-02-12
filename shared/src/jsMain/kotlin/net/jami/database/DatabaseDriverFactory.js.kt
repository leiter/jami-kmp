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
 * JavaScript implementation of DatabaseDriverFactory.
 *
 * Note: Browser-based SQLite support is limited.
 * For web clients, consider using:
 * - IndexedDB directly
 * - Web Worker SQLite driver
 * - Server-side persistence via REST API
 *
 * This stub throws an exception as SQLite is not directly supported in browsers.
 */
actual class DatabaseDriverFactory {
    actual fun createDriver(dbName: String): SqlDriver {
        // Web Worker driver would require additional setup:
        // return WebWorkerDriver(...)
        throw UnsupportedOperationException(
            "SQLite is not directly supported in JavaScript. " +
            "Use IndexedDB or server-side persistence for web clients."
        )
    }
}
