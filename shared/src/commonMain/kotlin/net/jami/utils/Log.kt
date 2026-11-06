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
package net.jami.utils

/**
 * Simple logging utility for Jami KMP.
 *
 * Writes through [platformLog] to the facility the platform's tooling reads:
 * logcat on Android, NSLog on iOS/macOS, stdout on Desktop/Web.
 */
object Log {
    var level: Level = Level.DEBUG

    enum class Level(val value: Int) {
        VERBOSE(0),
        DEBUG(1),
        INFO(2),
        WARN(3),
        ERROR(4),
        NONE(5)
    }

    fun v(tag: String, message: String) = log(Level.VERBOSE, tag, message)

    fun d(tag: String, message: String) = log(Level.DEBUG, tag, message)

    fun i(tag: String, message: String) = log(Level.INFO, tag, message)

    fun w(tag: String, message: String) = log(Level.WARN, tag, message)

    fun w(tag: String, message: String, throwable: Throwable?) = log(Level.WARN, tag, message, throwable)

    fun e(tag: String, message: String) = log(Level.ERROR, tag, message)

    fun e(tag: String, message: String, throwable: Throwable?) = log(Level.ERROR, tag, message, throwable)

    private fun log(at: Level, tag: String, message: String, throwable: Throwable? = null) {
        if (level.value <= at.value) platformLog(at, tag, message, throwable)
    }
}
