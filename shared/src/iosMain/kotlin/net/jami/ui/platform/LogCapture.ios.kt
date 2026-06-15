package net.jami.ui.platform

import net.jami.bridge.JamiBridgeWrapper

actual fun captureRecentLogs(maxLines: Int): String =
    JamiBridgeWrapper.shared().captureRecentLogs(maxLines) ?: ""
