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

import android.content.Context
import android.content.ActivityNotFoundException
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import org.koin.mp.KoinPlatform
import java.io.File

actual fun shareText(subject: String, body: String) {
    val context: Context = KoinPlatform.getKoin().get()
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }
    context.startActivity(
        Intent.createChooser(intent, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    )
}

actual fun shareFile(path: String) {
    val context: Context = KoinPlatform.getKoin().get()
    val file = shareableFile(context, File(path)) ?: return
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeTypeOf(file)
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(intent, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    )
}

actual fun openFile(path: String): Boolean {
    val context: Context = KoinPlatform.getKoin().get()
    val file = shareableFile(context, File(path)) ?: return false
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeTypeOf(file))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}

private fun mimeTypeOf(file: File): String =
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())
        ?: "application/octet-stream"

/**
 * A file the FileProvider can serve (res/xml/file_paths.xml: cache/share/ and the conversation
 * files under files/conversations/). Conversation files are served in place — never moved, they
 * belong to the conversation. Anything else in the app cache (e.g. the account export written to
 * cacheDir) is moved into cache/share/ so no stray duplicate of a sensitive file is left behind;
 * other files are copied there.
 */
private fun shareableFile(context: Context, source: File): File? {
    if (!source.exists()) return null
    val canonical = source.canonicalFile
    val shareDir = File(context.cacheDir, "share").apply { mkdirs() }
    val servedRoots = listOf(shareDir, File(context.filesDir, "conversations"))
    if (servedRoots.any { canonical.path.startsWith(it.canonicalPath + File.separator) }) return canonical
    return File(shareDir, source.name).also { target ->
        val inCache = canonical.path.startsWith(context.cacheDir.canonicalPath + File.separator)
        if (!(inCache && source.renameTo(target))) {
            source.copyTo(target, overwrite = true)
            if (inCache) source.delete()
        }
    }
}
