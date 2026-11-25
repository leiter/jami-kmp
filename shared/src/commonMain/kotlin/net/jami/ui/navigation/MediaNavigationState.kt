/*
 *  Copyright (C) 2004-2025 Savoir-faire Linux Inc.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */
package net.jami.ui.navigation

import net.jami.ui.viewmodel.MessageItem
import net.jami.ui.viewmodel.MessageType

/**
 * Transient holder for media navigation arguments.
 * Set the path immediately before navigating to [Screen.MediaViewer] or [Screen.VideoPlayer];
 * read it on first composition of the destination screen.
 *
 * File paths contain '/' and other URL-unsafe characters so encoding them into
 * a route string is fragile. Using a singleton holder avoids that complexity while
 * remaining safe for the single-screen navigation flow (no deep-link support needed).
 */
object MediaNavigationState {
    var filePath: String = ""
    var fileName: String = ""

    /**
     * The images the viewer can swipe through, oldest first, [filePath] among them. Empty or
     * without [filePath] means just that one image.
     */
    var imagePaths: List<String> = emptyList()
}

/**
 * The conversation's viewable images, in message order: the picture transfers that are on disk.
 * The same filter as the media gallery, minus videos, which open in the video player instead.
 */
fun pagedImagePaths(messages: List<MessageItem>): List<String> =
    messages
        .filter { it.type == MessageType.Transfer && it.isPicture }
        .mapNotNull { it.destinationPath }
        .distinct()

/**
 * The pages [MediaViewerScreen][net.jami.ui.screens.MediaViewerScreen] shows and the one it opens
 * on. A path missing from [imagePaths] (the list was built before it arrived) is shown alone.
 */
fun mediaPages(filePath: String, imagePaths: List<String>): Pair<List<String>, Int> {
    val index = imagePaths.indexOf(filePath)
    return if (index < 0) listOf(filePath) to 0 else imagePaths to index
}
