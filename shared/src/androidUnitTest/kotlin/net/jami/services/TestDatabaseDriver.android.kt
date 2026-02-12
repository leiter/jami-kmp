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

import app.cash.sqldelight.db.SqlDriver

/**
 * Android unit test driver.
 *
 * Note: The JDBC SQLite driver is not available in Android unit tests.
 * For full database testing on Android, use instrumented tests with
 * AndroidSqliteDriver. This stub throws to indicate tests should be skipped.
 */
actual fun createTestDriver(): SqlDriver {
    throw UnsupportedOperationException(
        "SQLite is not supported in Android unit test environment. " +
        "Use instrumented tests or run database tests on desktopTest."
    )
}
