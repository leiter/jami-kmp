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
 * Uses expect/actual for platform-specific implementations.
 * This is a simplified version - full implementation would use
 * platform logging (Logcat on Android, NSLog on iOS, etc.)
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

    fun v(tag: String, message: String) {
        if (level.value <= Level.VERBOSE.value) {
            println("V/$tag: $message")
        }
    }

    fun d(tag: String, message: String) {
        if (level.value <= Level.DEBUG.value) {
            println("D/$tag: $message")
        }
    }

    fun i(tag: String, message: String) {
        if (level.value <= Level.INFO.value) {
            println("I/$tag: $message")
        }
    }

    fun w(tag: String, message: String) {
        if (level.value <= Level.WARN.value) {
            println("W/$tag: $message")
        }
    }

    fun w(tag: String, message: String, throwable: Throwable?) {
        if (level.value <= Level.WARN.value) {
            println("W/$tag: $message")
            throwable?.printStackTrace()
        }
    }

    fun e(tag: String, message: String) {
        if (level.value <= Level.ERROR.value) {
            println("E/$tag: $message")
        }
    }

    fun e(tag: String, message: String, throwable: Throwable?) {
        if (level.value <= Level.ERROR.value) {
            println("E/$tag: $message")
            throwable?.printStackTrace()
        }
    }
}
