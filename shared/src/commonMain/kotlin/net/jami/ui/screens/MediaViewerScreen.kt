/*
 *  Copyright (C) 2004-2025 Savoir-faire Linux Inc.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */
package net.jami.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import jami_kmp.shared.generated.resources.Res
import jami_kmp.shared.generated.resources.*
import net.jami.ui.platform.FileSaveResult
import net.jami.ui.platform.FileSaverEffect
import net.jami.utils.shareFile
import org.jetbrains.compose.resources.stringResource
import net.jami.utils.FileUtils
import net.jami.ui.utils.toImageBitmap

@Composable
fun MediaViewerScreen(
    filePath: String,
    onBack: () -> Unit,
) {
    var imageBitmap by remember(filePath) { mutableStateOf<ImageBitmap?>(null) }
    var isLoading by remember(filePath) { mutableStateOf(true) }

    LaunchedEffect(filePath) {
        isLoading = true
        imageBitmap = withContext(Dispatchers.Default) {
            FileUtils.readBytes(filePath)?.toImageBitmap()
        }
        isLoading = false
    }

    // Share / Save (jami-android-client MediaViewerFragment toolbar actions). Save writes a copy
    // where the user chooses; the conversation file stays in place.
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var pendingSave by remember { mutableStateOf<String?>(null) }
    val savedMsg = stringResource(Res.string.file_saved_successfully)
    val errorMsg = stringResource(Res.string.generic_error)
    FileSaverEffect(sourcePath = pendingSave, deleteSource = false) { result ->
        pendingSave = null
        scope.launch {
            when (result) {
                FileSaveResult.SAVED -> snackbarHostState.showSnackbar(savedMsg)
                FileSaveResult.FAILED -> snackbarHostState.showSnackbar(errorMsg)
                FileSaveResult.CANCELLED -> Unit
            }
        }
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 8f)
        // Clamp pan so image can't drift infinitely when not zoomed in
        if (scale > 1f) offset += panChange else offset = Offset.Zero
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        scale = 1f
                        offset = Offset.Zero
                    },
                )
            },
    ) {
        when {
            isLoading -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
            )
            imageBitmap != null -> Image(
                bitmap = imageBitmap!!,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .transformable(transformableState)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                    ),
                contentScale = ContentScale.Fit,
            )
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
        ) {
            IconButton(onClick = { shareFile(filePath) }) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = stringResource(Res.string.menu_file_share),
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
            IconButton(onClick = { pendingSave = filePath }) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = stringResource(Res.string.menu_file_save),
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
