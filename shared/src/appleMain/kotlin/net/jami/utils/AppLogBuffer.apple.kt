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

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLock

/**
 * The app's own (Kotlin) log lines of this launch, for the in-app debug log.
 *
 * On iOS [Log] went to NSLog only, which a TestFlight tester cannot read: the debug log showed
 * the daemon's side of a problem and none of the app's, so an app-side error (a failed accept,
 * a swallowed exception) left no trace there. Kept in memory, newest [CAPACITY] lines.
 */
internal object AppLogBuffer {
    private const val CAPACITY = 3000
    private val lock = NSLock()
    private val lines = ArrayDeque<String>(CAPACITY)
    private val formatter = NSDateFormatter().apply { dateFormat = "HH:mm:ss.SSS" }

    fun add(line: String) {
        lock.lock()
        try {
            val stamped = "[${formatter.stringFromDate(NSDate())}] $line"
            if (lines.size == CAPACITY) lines.removeFirst()
            lines.addLast(stamped)
        } finally {
            lock.unlock()
        }
    }

    fun clear() {
        lock.lock()
        try {
            lines.clear()
        } finally {
            lock.unlock()
        }
    }

    fun recent(maxLines: Int): String {
        lock.lock()
        try {
            return lines.takeLast(maxLines).joinToString("\n")
        } finally {
            lock.unlock()
        }
    }
}
