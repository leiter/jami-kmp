/*
 *  Copyright (C) 2004-2025 Savoir-faire Linux Inc.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */
package net.jami.services

import kotlinx.cinterop.ExperimentalForeignApi
import net.jami.ui.navigation.ShareState
import net.jami.utils.Log
import platform.Foundation.NSDictionary
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.dictionaryWithContentsOfURL

/**
 * Takes what the Share Extension (`ios-app/jamiShareExtension`, I8) left in the App Group's
 * share inbox and opens the share picker with it, as Android's ACTION_SEND does.
 *
 * The extension writes `ShareInbox/<id>/` with the shared files and, last, a `manifest.plist`
 * (`text`, `files`). Only entries with a manifest are taken, so a share still being written is
 * left for the next look. Files are moved into the app's tmp directory, which the picker reads,
 * and the entry is removed.
 */
object IOSShareInbox {
    const val APP_GROUP = "group.net.jami.kmp"
    private const val TAG = "IOSShareInbox"

    /** Called when the app comes to the foreground and when the extension opens it. */
    @OptIn(ExperimentalForeignApi::class)
    fun collect(): Boolean {
        val manager = NSFileManager.defaultManager
        val container = manager.containerURLForSecurityApplicationGroupIdentifier(APP_GROUP) ?: return false
        val inbox = container.URLByAppendingPathComponent("ShareInbox") ?: return false
        val entries = manager.contentsOfDirectoryAtURL(inbox, includingPropertiesForKeys = null, options = 0u, error = null)
            ?.filterIsInstance<NSURL>()
            ?.sortedBy { it.lastPathComponent }
            .orEmpty()
        var text: String? = null
        val files = mutableListOf<String>()
        for (entry in entries) {
            val manifestUrl = entry.URLByAppendingPathComponent("manifest.plist") ?: continue
            val manifest = NSDictionary.dictionaryWithContentsOfURL(manifestUrl) ?: continue
            val parsed = ShareManifest.parse(manifest[ShareManifest.KEY_TEXT] as? String, manifest[ShareManifest.KEY_FILES] as? List<*>)
            parsed.text?.let { text = listOfNotNull(text, it).joinToString("\n") }
            for (name in parsed.files) {
                val source = entry.URLByAppendingPathComponent(name)?.path ?: continue
                val target = "${NSTemporaryDirectory().removeSuffix("/")}/${entry.lastPathComponent}-$name"
                manager.removeItemAtPath(target, error = null)
                if (manager.moveItemAtPath(source, toPath = target, error = null)) files += target
            }
            manager.removeItemAtURL(entry, error = null)
        }
        if (text == null && files.isEmpty()) return false
        Log.d(TAG, "Shared from another app: ${files.size} file(s), text=${text != null}")
        ShareState.pendingText = text
        ShareState.pendingFilePaths = files
        ShareState.mimeType = null
        ShareState.signalSharePicker()
        return true
    }
}

/** The extension's manifest, read defensively: it comes from another process. */
internal data class ShareManifest(val text: String?, val files: List<String>) {
    companion object {
        const val KEY_TEXT = "text"
        const val KEY_FILES = "files"

        fun parse(text: String?, files: List<*>?): ShareManifest = ShareManifest(
            text = text?.takeIf { it.isNotBlank() },
            // Plain names only: a path separator could reach outside the entry's folder.
            files = files.orEmpty().filterIsInstance<String>()
                .filter { it.isNotEmpty() && '/' !in it && it != "." && it != ".." && it != "manifest.plist" },
        )
    }
}

/** For Swift: `IOSShareInboxKt.collectSharedItems()`. */
fun collectSharedItems(): Boolean = IOSShareInbox.collect()
