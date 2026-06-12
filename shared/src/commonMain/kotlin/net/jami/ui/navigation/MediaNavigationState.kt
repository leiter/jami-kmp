/*
 *  Copyright (C) 2004-2025 Savoir-faire Linux Inc.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */
package net.jami.ui.navigation

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
}
