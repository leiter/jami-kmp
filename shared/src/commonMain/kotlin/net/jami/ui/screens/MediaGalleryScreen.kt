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
package net.jami.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import jami_kmp.shared.generated.resources.Res
import jami_kmp.shared.generated.resources.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import net.jami.di.getViewModel
import net.jami.ui.theme.JamiTheme
import net.jami.ui.utils.toImageBitmap
import net.jami.ui.viewmodel.ChatViewModel
import net.jami.ui.viewmodel.MessageItem
import net.jami.ui.viewmodel.MessageType
import net.jami.utils.FileUtils

/**
 * Grid of all image/video attachments shared in a conversation.
 *
 * Reuses [ChatViewModel] for the message history query (filtered down to completed
 * picture/video transfers) rather than a dedicated ViewModel, mirroring how
 * [ChatScreen] already loads the conversation.
 *
 * @param conversationId The conversation whose shared media to display.
 * @param onBack Called when the user navigates back.
 * @param onImageClick Called with the local file path when an image tile is tapped.
 * @param onVideoClick Called with the local file path and display name when a video tile is tapped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaGalleryScreen(
    conversationId: String,
    onBack: () -> Unit,
    onImageClick: (filePath: String) -> Unit = {},
    onVideoClick: (filePath: String, fileName: String) -> Unit = { _, _ -> },
) {
    val viewModel = getViewModel<ChatViewModel>()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(conversationId) {
        viewModel.loadConversation(conversationId)
    }

    val mediaItems = state.messages.filter {
        it.type == MessageType.Transfer && it.destinationPath != null && (it.isPicture || it.isVideo)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.screen_title_media_gallery)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.content_desc_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = JamiTheme.colors.surface,
                    titleContentColor = JamiTheme.colors.onSurface,
                ),
            )
        },
    ) { padding ->
        if (mediaItems.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(Res.string.media_gallery_empty),
                    style = JamiTheme.typography.bodyMedium,
                    color = JamiTheme.colors.onSurfaceVariant,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(2.dp),
            ) {
                items(mediaItems, key = { it.id }) { item ->
                    MediaGalleryTile(
                        item = item,
                        onClick = {
                            val path = item.destinationPath ?: return@MediaGalleryTile
                            if (item.isVideo) onVideoClick(path, item.text) else onImageClick(path)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaGalleryTile(
    item: MessageItem,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .background(Color.Black)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (item.isPicture) {
            var imageBitmap by remember(item.destinationPath) { mutableStateOf<ImageBitmap?>(null) }
            LaunchedEffect(item.destinationPath) {
                val path = item.destinationPath ?: return@LaunchedEffect
                imageBitmap = withContext(Dispatchers.Default) {
                    FileUtils.readBytes(path)?.toImageBitmap()
                }
            }
            val bitmap = imageBitmap
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = stringResource(Res.string.content_desc_media_thumbnail),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.padding(24.dp))
            }
        } else {
            // Video thumbnail extraction is not implemented; show a play-icon placeholder tile.
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = stringResource(Res.string.content_desc_video_thumbnail),
                tint = Color.White,
            )
        }
    }
}
