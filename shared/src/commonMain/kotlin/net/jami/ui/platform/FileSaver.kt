package net.jami.ui.platform

import androidx.compose.runtime.Composable

/** Outcome of a [FileSaverEffect] "Save as" request. */
enum class FileSaveResult { SAVED, CANCELLED, FAILED }

/**
 * Platform-native "Save as" dialog for a file that already exists at [sourcePath] — the
 * counterpart of [FilePickerEffect]. Used for the account export: the daemon can only write to
 * a real filesystem path, so it exports into the app's private temp dir and this lets the user
 * choose where the archive finally goes (mirrors jami-android-client's ACTION_CREATE_DOCUMENT
 * + moveToUri flow).
 *
 * The dialog opens whenever [sourcePath] becomes non-null; the file name of [sourcePath] is the
 * suggested name. The source file is deleted afterwards whatever the outcome, so a sensitive
 * temp file (the account archive holds the private keys) never lingers in the temp dir.
 *
 * @param sourcePath Absolute path of the file to save, or null when there is nothing to save.
 * @param mimeType MIME type of the file (used by pickers that filter/label by type).
 * @param onResult Called once per request with the outcome; the caller should reset
 *   [sourcePath] to null here.
 */
@Composable
expect fun FileSaverEffect(
    sourcePath: String?,
    mimeType: String = "application/octet-stream",
    onResult: (FileSaveResult) -> Unit,
)
