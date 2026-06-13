package net.jami.ui.platform

/**
 * Captures recent log output on platforms that support it.
 * Returns an empty string on platforms without log access.
 *
 * @param maxLines Maximum number of lines to capture.
 */
expect fun captureRecentLogs(maxLines: Int = 500): String
