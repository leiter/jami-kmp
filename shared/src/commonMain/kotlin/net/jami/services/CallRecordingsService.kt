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
package net.jami.services

import net.jami.model.CallRecording
import net.jami.utils.FileUtils
import net.jami.utils.Log

/**
 * Filesystem access needed to browse recordings, kept behind an interface so the service can be
 * tested without touching a real directory.
 */
interface RecordingFileSystem {
    fun listFiles(directory: String): List<String>
    fun fileSize(path: String): Long
    fun lastModified(path: String): Long
    fun delete(path: String): Boolean
    fun exists(path: String): Boolean
}

/** Default implementation, delegating to [FileUtils]. */
class DefaultRecordingFileSystem : RecordingFileSystem {
    override fun listFiles(directory: String): List<String> = FileUtils.listFiles(directory)
    override fun fileSize(path: String): Long = FileUtils.getFileSize(path)
    override fun lastModified(path: String): Long = FileUtils.getLastModified(path)
    override fun delete(path: String): Boolean = FileUtils.deleteFile(path)
    override fun exists(path: String): Boolean = FileUtils.exists(path)
}

/**
 * Browses the call recordings the daemon has written.
 *
 * The daemon records into one directory (`getRecordPath`) and names the files itself; it offers no
 * listing API, so the app reads the directory. Playback is deliberately *not* done through
 * libjami's `startRecordedFilePlayback`, which feeds a file into the daemon's audio layer for
 * sending into a call — a recording the user wants to hear is played by the platform player,
 * as the reference client does.
 */
class CallRecordingsService(
    private val daemonBridge: DaemonBridgeApi,
    private val fileSystem: RecordingFileSystem = DefaultRecordingFileSystem(),
) {
    /** Directory the daemon writes recordings into; empty if it reports none. */
    fun recordingsDirectory(): String = daemonBridge.getRecordPath()

    /** Point the daemon at a different recordings directory. */
    fun setRecordingsDirectory(path: String) {
        daemonBridge.setRecordPath(path)
    }

    /**
     * Recordings on disk, newest first.
     *
     * Only media extensions are listed: the daemon's record directory can also hold temporary
     * and partial files, and anything else in there is not ours to show.
     */
    fun listRecordings(): List<CallRecording> {
        val directory = recordingsDirectory()
        if (directory.isEmpty()) {
            Log.w(TAG, "listRecordings: the daemon reports no record path")
            return emptyList()
        }
        return fileSystem.listFiles(directory)
            .filter { path -> FileUtils.getExtension(path).lowercase() in MEDIA_EXTENSIONS }
            .map { path ->
                CallRecording(
                    path = path,
                    name = FileUtils.getName(path),
                    sizeBytes = fileSystem.fileSize(path),
                    timestamp = fileSystem.lastModified(path),
                )
            }
            .sortedByDescending { it.timestamp }
    }

    /** Delete a recording. Returns false when the file is already gone or cannot be removed. */
    fun deleteRecording(path: String): Boolean {
        if (!fileSystem.exists(path)) return false
        val deleted = fileSystem.delete(path)
        if (!deleted) Log.w(TAG, "deleteRecording: could not delete $path")
        return deleted
    }

    companion object {
        private const val TAG = "CallRecordingsService"

        /**
         * Containers libjami writes call recordings into, plus the ones its media player can
         * produce; the extension is matched case-insensitively.
         */
        private val MEDIA_EXTENSIONS = setOf("ogg", "opus", "wav", "webm", "mkv", "mp4", "mp3", "m4a")
    }
}
