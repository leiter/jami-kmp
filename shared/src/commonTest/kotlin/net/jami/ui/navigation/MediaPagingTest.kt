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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediaPagingTest {

    private fun transfer(id: String, path: String?, picture: Boolean = true, video: Boolean = false) =
        MessageItem(
            id = id, text = "$id.jpg", author = "", timestamp = id.hashCode().toLong(),
            isOutgoing = false, type = MessageType.Transfer,
            isPicture = picture, isVideo = video, destinationPath = path,
        )

    @Test
    fun theViewerPagesThroughTheConversationsImagesInMessageOrder() {
        val messages = listOf(
            transfer("a", "/f/a.jpg"),
            MessageItem(id = "t", text = "hello", author = "", timestamp = 2, isOutgoing = true),
            transfer("v", "/f/v.mp4", picture = false, video = true),
            transfer("pending", null),
            transfer("b", "/f/b.jpg"),
            transfer("doc", "/f/doc.pdf", picture = false),
        )
        assertEquals(listOf("/f/a.jpg", "/f/b.jpg"), pagedImagePaths(messages))
    }

    @Test
    fun theViewerOpensOnTheTappedImage() {
        val paths = listOf("/f/a.jpg", "/f/b.jpg", "/f/c.jpg")
        assertEquals(paths to 1, mediaPages("/f/b.jpg", paths))
    }

    @Test
    fun anImageMissingFromTheListIsShownAlone() {
        // A stale list from an earlier navigation, or one built before this file arrived.
        assertEquals(listOf("/f/new.jpg") to 0, mediaPages("/f/new.jpg", listOf("/f/a.jpg")))
        assertEquals(listOf("/f/new.jpg") to 0, mediaPages("/f/new.jpg", emptyList()))
    }

    @Test
    fun fullScreenOverlaysFadeAndOrdinaryScreensSlide() {
        assertTrue(isOverlayRoute(Screen.MediaViewer.route))
        assertTrue(isOverlayRoute(Screen.VideoPlayer.route))
        assertTrue(isOverlayRoute(Screen.Call.ROUTE))
        assertTrue(isOverlayRoute(Screen.ViewCall.ROUTE))
        assertFalse(isOverlayRoute(Screen.Chat.ROUTE))
        assertFalse(isOverlayRoute(Screen.Home.route))
        assertFalse(isOverlayRoute(null))
    }
}
