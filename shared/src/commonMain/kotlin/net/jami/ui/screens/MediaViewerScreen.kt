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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import net.jami.ui.navigation.mediaPages
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
import net.jami.ui.components.mediaSharedKey
import net.jami.ui.components.sharedMediaElement
import net.jami.ui.utils.toImageBitmap
import kotlin.math.abs

/**
 * How far the image has to be dragged for the swipe to dismiss it.
 *
 * Expressed in pixels rather than dp because it is compared against raw drag deltas inside a
 * pointer-input block, where no density is in scope. Half of it is the commit threshold.
 */
private const val DISMISS_DISTANCE_PX = 600f

/**
 * Full-screen image viewer.
 *
 * Opens on [filePath] and swipes sideways through [imagePaths] (the conversation's images, oldest
 * first). The reference viewer shows one image only; paging is the S5 refinement of
 * doc/open-tasks-2026-11-24.md. Each page zooms on its own, and the pager only moves while the
 * current page is unzoomed, so a horizontal drag on a zoomed image still pans it.
 */
@Composable
fun MediaViewerScreen(
    filePath: String,
    onBack: () -> Unit,
    imagePaths: List<String> = emptyList(),
) {
    val (pages, initialPage) = remember(filePath, imagePaths) { mediaPages(filePath, imagePaths) }
    val pagerState = rememberPagerState(initialPage = initialPage) { pages.size }
    val currentPath = pages[pagerState.currentPage.coerceIn(0, pages.lastIndex)]

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

    // Swipe the image away instead of reaching for the back button, the gesture every gallery
    // has. Hoisted out of the pages because the backdrop it thins belongs to the whole screen.
    var dismissOffset by remember { mutableFloatStateOf(0f) }
    val dismissProgress = (abs(dismissOffset) / DISMISS_DISTANCE_PX).coerceIn(0f, 1f)
    // The backdrop thins out as the image is dragged, so the chat behind it comes back into
    // view and the gesture reads as reversible before it commits.
    val backdropAlpha = 1f - dismissProgress * 0.85f
    // Whether the page on screen is zoomed: paging would steal its pan.
    var currentZoomed by remember { mutableStateOf(false) }
    LaunchedEffect(pagerState.currentPage) {
        dismissOffset = 0f
        currentZoomed = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = backdropAlpha)),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !currentZoomed,
            key = { pages[it] },
        ) { page ->
            val isCurrent = page == pagerState.currentPage
            ZoomableImagePage(
                path = pages[page],
                dismissOffset = if (isCurrent) dismissOffset else 0f,
                onZoomedChange = { zoomed -> if (isCurrent) currentZoomed = zoomed },
                onDismissDrag = { delta -> dismissOffset += delta },
                onDismissEnd = {
                    if (abs(dismissOffset) > DISMISS_DISTANCE_PX / 2f) onBack()
                    else dismissOffset = 0f
                },
                onDismissCancel = { dismissOffset = 0f },
            )
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                // Below the status bar: drawn under it, the buttons' taps went to the system (the
                // clock and battery sit exactly there on an iPhone).
                .safeDrawingPadding()
                .padding(8.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(Res.string.content_desc_back),
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .safeDrawingPadding()
                .padding(8.dp),
        ) {
            IconButton(onClick = { shareFile(currentPath) }) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = stringResource(Res.string.menu_file_share),
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
            IconButton(onClick = { pendingSave = currentPath }) {
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
            modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding(),
        )
    }
}

/**
 * One image of the viewer: loads [path], pinch-zooms and pans while zoomed, double-tap resets,
 * and reports vertical drags for swipe-to-dismiss while unzoomed.
 */
@Composable
private fun ZoomableImagePage(
    path: String,
    dismissOffset: Float,
    onZoomedChange: (Boolean) -> Unit,
    onDismissDrag: (Float) -> Unit,
    onDismissEnd: () -> Unit,
    onDismissCancel: () -> Unit,
) {
    var imageBitmap by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    var isLoading by remember(path) { mutableStateOf(true) }

    LaunchedEffect(path) {
        isLoading = true
        imageBitmap = withContext(Dispatchers.Default) {
            FileUtils.readBytes(path)?.toImageBitmap()
        }
        isLoading = false
    }

    var scale by remember(path) { mutableFloatStateOf(1f) }
    var offset by remember(path) { mutableStateOf(Offset.Zero) }
    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 8f)
        // Clamp pan so image can't drift infinitely when not zoomed in
        if (scale > 1f) offset += panChange else offset = Offset.Zero
        onZoomedChange(scale > 1f)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(path) {
                detectTapGestures(
                    onDoubleTap = {
                        scale = 1f
                        offset = Offset.Zero
                        onZoomedChange(false)
                    },
                )
            }
            // Only while unzoomed: once the image is scaled up a vertical drag is a pan, and
            // stealing it would make a zoomed image impossible to move around.
            .pointerInput(path, scale) {
                if (scale > 1f) return@pointerInput
                detectVerticalDragGestures(
                    onDragEnd = onDismissEnd,
                    onDragCancel = onDismissCancel,
                    onVerticalDrag = { _, delta -> onDismissDrag(delta) },
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
                    // The full-screen end of the shared-element transition: the same key as the
                    // chat bubble's thumbnail, so the image flies between the two — back to the
                    // bubble of whichever image is on screen when the viewer closes.
                    .sharedMediaElement(mediaSharedKey(path))
                    // Pan only while zoomed: unzoomed, a horizontal drag belongs to the pager.
                    .transformable(transformableState, canPan = { scale > 1f })
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        // The drag rides on top of any pan the user has already applied.
                        translationY = offset.y + dismissOffset,
                    ),
                contentScale = ContentScale.Fit,
            )
        }
    }
}
