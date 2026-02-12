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
 * Create a platform-specific test SQL driver with schema already created.
 *
 * Each platform implements this differently:
 * - JVM/Desktop: JdbcSqliteDriver with in-memory database + schema creation
 * - Native (iOS/macOS): NativeSqliteDriver with in-memory database (schema created automatically)
 * - Android Unit Tests / JS: Throws UnsupportedOperationException
 *
 * IMPORTANT: The returned driver already has the schema created.
 * Do NOT call JamiDatabase.Schema.create() on the returned driver.
 */
expect fun createTestDriver(): SqlDriver
