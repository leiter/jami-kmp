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
package net.jami.model

/**
 * A call recording file the daemon has written.
 *
 * The daemon names these itself and keeps no index of them, so everything here comes from the
 * file on disk rather than from call history: there is no reliable way to say which call a
 * recording belongs to.
 */
data class CallRecording(
    val path: String,
    val name: String,
    val sizeBytes: Long,
    /** Last-modified time in milliseconds, used as the recording's date. */
    val timestamp: Long,
)
