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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CallRecordingsServiceTest {

    /** In-memory stand-in for the record directory. */
    private class FakeRecordingFileSystem(
        var files: MutableMap<String, Pair<Long, Long>> = mutableMapOf(),
    ) : RecordingFileSystem {
        val deleted = mutableListOf<String>()
        var deleteSucceeds = true

        override fun listFiles(directory: String): List<String> =
            files.keys.filter { it.startsWith("$directory/") }

        override fun fileSize(path: String): Long = files[path]?.first ?: 0L
        override fun lastModified(path: String): Long = files[path]?.second ?: 0L
        override fun exists(path: String): Boolean = files.containsKey(path)
        override fun delete(path: String): Boolean {
            deleted += path
            if (!deleteSucceeds) return false
            files.remove(path)
            return true
        }
    }

    private fun service(
        recordPath: String = "/records",
        fs: FakeRecordingFileSystem = FakeRecordingFileSystem(),
    ): Pair<CallRecordingsService, FakeRecordingFileSystem> {
        val bridge = StubDaemonBridge().apply { recordPathValue = recordPath }
        return CallRecordingsService(bridge, fs) to fs
    }

    @Test
    fun recordingsAreListedNewestFirst() {
        val fs = FakeRecordingFileSystem(
            mutableMapOf(
                "/records/old.ogg" to (1_000L to 1_000L),
                "/records/new.ogg" to (2_000L to 9_000L),
                "/records/middle.ogg" to (3_000L to 5_000L),
            )
        )
        val (recordings, _) = service(fs = fs)

        val listed = recordings.listRecordings()

        assertEquals(listOf("new.ogg", "middle.ogg", "old.ogg"), listed.map { it.name })
        assertEquals(2_000L, listed[0].sizeBytes)
        assertEquals(9_000L, listed[0].timestamp)
    }

    /**
     * The daemon's record directory also holds things that are not finished recordings, and
     * anything else the user put there is not ours to list.
     */
    @Test
    fun nonMediaFilesAreNotListed() {
        val fs = FakeRecordingFileSystem(
            mutableMapOf(
                "/records/call.ogg" to (10L to 10L),
                "/records/notes.txt" to (10L to 20L),
                "/records/partial.tmp" to (10L to 30L),
                "/records/video.MKV" to (10L to 40L),
            )
        )
        val (recordings, _) = service(fs = fs)

        val listed = recordings.listRecordings().map { it.name }

        // The extension match is case-insensitive, so MKV counts.
        assertEquals(listOf("video.MKV", "call.ogg"), listed)
    }

    /** With no record path there is nothing to read, and no exception either. */
    @Test
    fun noRecordPathYieldsNoRecordings() {
        val (recordings, _) = service(recordPath = "")

        assertTrue(recordings.listRecordings().isEmpty())
    }

    @Test
    fun deletingARecordingRemovesIt() {
        val fs = FakeRecordingFileSystem(mutableMapOf("/records/call.ogg" to (10L to 10L)))
        val (recordings, _) = service(fs = fs)

        assertTrue(recordings.deleteRecording("/records/call.ogg"))
        assertEquals(listOf("/records/call.ogg"), fs.deleted)
        assertTrue(recordings.listRecordings().isEmpty())
    }

    /** A file that is already gone is not an error worth reporting as a failed delete attempt. */
    @Test
    fun deletingAMissingRecordingDoesNotTouchTheFilesystem() {
        val fs = FakeRecordingFileSystem()
        val (recordings, _) = service(fs = fs)

        assertFalse(recordings.deleteRecording("/records/gone.ogg"))
        assertTrue(fs.deleted.isEmpty())
    }

    @Test
    fun aFailedDeleteIsReported() {
        val fs = FakeRecordingFileSystem(mutableMapOf("/records/call.ogg" to (10L to 10L)))
        fs.deleteSucceeds = false
        val (recordings, _) = service(fs = fs)

        assertFalse(recordings.deleteRecording("/records/call.ogg"))
        assertEquals(1, recordings.listRecordings().size)
    }

    @Test
    fun theRecordDirectoryIsReadFromAndWrittenToTheDaemon() {
        val bridge = StubDaemonBridge().apply { recordPathValue = "/records" }
        val recordings = CallRecordingsService(bridge, FakeRecordingFileSystem())

        assertEquals("/records", recordings.recordingsDirectory())

        recordings.setRecordingsDirectory("/sdcard/jami")
        assertEquals("/sdcard/jami", bridge.recordPathValue)
    }
}
