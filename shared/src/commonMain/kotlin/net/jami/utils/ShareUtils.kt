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
 * Trigger the platform-native share sheet / share mechanism.
 *
 * @param subject Short title shown in some share targets (e.g. email subject).
 * @param body    Full text body to share.
 */
expect fun shareText(subject: String, body: String)

/**
 * Present a platform-native share sheet for a file at the given absolute path.
 *
 * On iOS this opens a [UIActivityViewController] so the user can save or send the file.
 * On Android this fires an ACTION_SEND intent via the FileProvider.
 * On Desktop / macOS / JS this is a no-op (files are written to accessible paths there).
 *
 * @param path Absolute path to the file to share.
 */
expect fun shareFile(path: String)
