/*
 *  Copyright (C) 2004-2025 Savoir-faire Linux Inc.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */
package net.jami.services

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** I8: the Share Extension's manifest comes from another process and is read defensively. */
class ShareManifestTest {
    @Test
    fun plainNamesAreKept() {
        val m = ShareManifest.parse("hello", listOf("a.jpg", "b.mov"))
        assertEquals("hello", m.text)
        assertEquals(listOf("a.jpg", "b.mov"), m.files)
    }

    @Test
    fun namesThatLeaveTheFolderAreDropped() {
        val m = ShareManifest.parse(null, listOf("../x", "a/b", "..", ".", "", "manifest.plist", 3, "ok.pdf"))
        assertEquals(listOf("ok.pdf"), m.files)
    }

    @Test
    fun blankTextIsNoText() {
        assertNull(ShareManifest.parse("  ", null).text)
        assertEquals(emptyList(), ShareManifest.parse("  ", null).files)
    }
}
