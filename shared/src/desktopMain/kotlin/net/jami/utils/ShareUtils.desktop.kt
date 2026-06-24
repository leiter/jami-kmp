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

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

actual fun shareText(subject: String, body: String) {
    val selection = StringSelection(body)
    Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
}

// On Desktop the exported file is written to a path the user can reach directly;
// no share-sheet presentation is needed.
actual fun shareFile(path: String) = Unit
