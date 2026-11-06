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
 * The platform's native log sink.
 *
 * Android routes an app's stdout to /dev/null, so [println] makes the KMP layer's
 * logs invisible in logcat (and in the in-app "Debug logs" screen, which reads logcat
 * for this process). Each platform therefore writes to the facility its tooling reads:
 * logcat on Android, NSLog on iOS/macOS, stdout elsewhere.
 *
 * Callers go through [Log], which applies the level gate before calling this.
 */
internal expect fun platformLog(
    level: Log.Level,
    tag: String,
    message: String,
    throwable: Throwable? = null,
)
