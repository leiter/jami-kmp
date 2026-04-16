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
package net.jami.ui.platform

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import net.jami.utils.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
actual fun ImageCaptureEffect(
    show: Boolean,
    onImageCaptured: (path: String?) -> Unit,
) {
    val context = LocalContext.current

    // Remember the file path across recompositions
    val currentPhotoPath = remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && currentPhotoPath.value != null) {
            // Image was captured successfully
            onImageCaptured(currentPhotoPath.value)
        } else {
            // Cancelled or failed
            onImageCaptured(null)
        }
        currentPhotoPath.value = null
    }

    LaunchedEffect(show) {
        if (show) {
            try {
                // Create temp file for the photo
                val photoFile = createImageFile(context)
                currentPhotoPath.value = photoFile.absolutePath

                // Create content URI using FileProvider
                val photoUri = getUriForFile(context, photoFile)

                // Launch camera
                launcher.launch(photoUri)
            } catch (e: Exception) {
                Log.e("ImageCapture", "Failed to create image file: ${e.message}")
                onImageCaptured(null)
                currentPhotoPath.value = null
            }
        }
    }
}

/**
 * Create a temporary image file in the app's cache directory.
 * Mirrors AndroidFileUtils.createImageFile() from jami-android-client.
 */
private fun createImageFile(context: Context): File {
    val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    val timestamp = dateFormat.format(Date())
    val fileName = "img_${timestamp}_"

    // Use cache directory for temp files
    val cacheDir = File(context.cacheDir, "share")
    if (!cacheDir.exists()) {
        cacheDir.mkdirs()
    }

    return File.createTempFile(fileName, ".jpg", cacheDir)
}

/**
 * Get a content URI for a file using FileProvider.
 * Mirrors ContentUri.getUriForFile() from jami-android-client.
 */
private fun getUriForFile(context: Context, file: File): Uri {
    val authority = "${context.packageName}.fileprovider"
    return FileProvider.getUriForFile(context, authority, file)
}
