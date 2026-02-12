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
 * JavaScript test driver.
 *
 * Note: SQLite is not directly supported in JavaScript/browser environments.
 * This creates a stub driver for test compilation only.
 * JS-specific database tests should be skipped at runtime.
 */
actual fun createTestDriver(): SqlDriver {
    throw UnsupportedOperationException(
        "SQLite is not supported in JavaScript test environment. " +
        "Use a mock or skip database tests for JS target."
    )
}
