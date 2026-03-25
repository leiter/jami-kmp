package net.jami.ui.platform

import androidx.compose.runtime.Composable

/**
 * Composable that provides a platform-specific file picker.
 *
 * @param show Whether the file picker should be shown.
 * @param mimeTypes List of MIME types to filter (e.g., "application/gzip").
 * @param onFilePicked Called with the selected file's absolute path, or null if cancelled.
 */
@Composable
expect fun FilePickerEffect(
    show: Boolean,
    mimeTypes: List<String> = listOf("*/*"),
    onFilePicked: (path: String?) -> Unit,
)
