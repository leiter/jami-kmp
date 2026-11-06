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

import android.util.Log as AndroidLog

internal actual fun platformLog(
    level: Log.Level,
    tag: String,
    message: String,
    throwable: Throwable?,
) {
    when (level) {
        Log.Level.VERBOSE -> if (throwable == null) AndroidLog.v(tag, message) else AndroidLog.v(tag, message, throwable)
        Log.Level.DEBUG -> if (throwable == null) AndroidLog.d(tag, message) else AndroidLog.d(tag, message, throwable)
        Log.Level.INFO -> if (throwable == null) AndroidLog.i(tag, message) else AndroidLog.i(tag, message, throwable)
        Log.Level.WARN -> if (throwable == null) AndroidLog.w(tag, message) else AndroidLog.w(tag, message, throwable)
        Log.Level.ERROR -> if (throwable == null) AndroidLog.e(tag, message) else AndroidLog.e(tag, message, throwable)
        Log.Level.NONE -> Unit
    }
}
