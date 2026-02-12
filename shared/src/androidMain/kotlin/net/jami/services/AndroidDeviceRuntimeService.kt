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

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Android implementation of DeviceRuntimeService.
 *
 * Uses Android Context for file paths and ContextCompat for permission checking.
 *
 * Ported from: jami-client-android DeviceRuntimeServiceImpl.kt
 */
class AndroidDeviceRuntimeService(
    private val context: Context
) : DeviceRuntimeService {

    private val conversationsDir: File by lazy {
        File(context.filesDir, "conversations").also { it.mkdirs() }
    }

    override fun getConversationPath(accountId: String, conversationId: String, name: String): String {
        val dir = File(conversationsDir, "$accountId/$conversationId")
        dir.mkdirs()
        return File(dir, name).absolutePath
    }

    override fun getNewConversationPath(accountId: String, conversationId: String, name: String): String {
        var prefix = 0
        var destPath: String
        do {
            val fileName = if (prefix == 0) name else "${prefix}_$name"
            destPath = getConversationPath(accountId, conversationId, fileName)
            prefix++
        } while (File(destPath).exists())
        return destPath
    }

    override fun getTempPath(): String =
        context.cacheDir.absolutePath

    override fun getCachePath(): String =
        context.cacheDir.absolutePath

    override fun getDataPath(): String =
        context.filesDir.absolutePath

    override fun hasStoragePermission(): Boolean =
        checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE)

    override fun hasCameraPermission(): Boolean =
        checkPermission(Manifest.permission.CAMERA)

    override fun hasMicrophonePermission(): Boolean =
        checkPermission(Manifest.permission.RECORD_AUDIO)

    override fun fileExists(path: String): Boolean =
        File(path).exists()

    override fun deleteFile(path: String): Boolean =
        File(path).delete()

    private fun checkPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
