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
 * macOS test driver.
 *
 * Note: NativeSqliteDriver has different behavior in test environments
 * (e.g., lastInsertRowId not working as expected). Database tests should
 * be run on desktopTest for now.
 *
 * TODO: Debug native SQLite driver behavior in macOS tests
 */
actual fun createTestDriver(): SqlDriver {
    throw UnsupportedOperationException(
        "SQLite tests are not yet supported on macOS native. " +
        "Use desktopTest for database testing."
    )
}
