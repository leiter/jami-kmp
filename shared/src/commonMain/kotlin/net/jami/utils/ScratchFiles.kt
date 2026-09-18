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

import net.jami.services.DeviceRuntimeService

/**
 * Files the app itself created as temporary copies in its cache or temp directory — e.g. what the
 * file picker copies a picked document to on Android/iOS. Only those may be moved or deleted by
 * the app; anything else (a path the user typed, a desktop picker's original file) is left alone.
 */
object ScratchFiles {

    /** True when [path] lies inside the app's cache or temp directory. */
    fun isScratch(path: String, runtime: DeviceRuntimeService): Boolean {
        if (path.isEmpty() || "/../" in path) return false
        return listOf(runtime.getCachePath(), runtime.getTempPath())
            .filter { it.isNotEmpty() }
            .any { dir -> path.startsWith(dir.trimEnd('/') + "/") }
    }

    /** Delete [path] if it is an app scratch copy; returns true when a file was deleted. */
    fun deleteIfScratch(path: String, runtime: DeviceRuntimeService? = koinRuntime()): Boolean {
        if (runtime == null || !isScratch(path, runtime) || !FileUtils.exists(path)) return false
        val deleted = FileUtils.deleteFile(path)
        Log.d("ScratchFiles", "Deleted scratch copy $path: $deleted")
        return deleted
    }

    private fun koinRuntime(): DeviceRuntimeService? =
        org.koin.mp.KoinPlatform.getKoinOrNull()?.getOrNull<DeviceRuntimeService>()
}
