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
package net.jami.ui.components.video

import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Corner positions for snapping the preview.
 */
enum class PreviewCorner {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT
}

/**
 * Draggable camera preview overlay with snap-to-corner behavior.
 *
 * The preview can be dragged anywhere on screen and will snap to the nearest
 * corner when released. Tapping the preview toggles between small and large sizes.
 *
 * @param modifier Modifier for the container
 * @param previewWidth Width of the preview in small mode
 * @param previewHeight Height of the preview in small mode
 * @param padding Padding from screen edges
 * @param initialCorner Starting corner position
 * @param isVisible Whether the preview is visible
 * @param onVisibilityToggle Callback when preview visibility is toggled
 * @param content The preview content (typically CameraPreview)
 */
@Composable
fun DraggablePreview(
    modifier: Modifier = Modifier,
    previewWidth: Dp = 120.dp,
    previewHeight: Dp = 160.dp,
    padding: Dp = 16.dp,
    initialCorner: PreviewCorner = PreviewCorner.TOP_RIGHT,
    isVisible: Boolean = true,
    onVisibilityToggle: () -> Unit = {},
    content: @Composable () -> Unit
) {
    if (!isVisible) return

    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val containerWidthPx = with(density) { maxWidth.toPx() }
        val containerHeightPx = with(density) { maxHeight.toPx() }
        val previewWidthPx = with(density) { previewWidth.toPx() }
        val previewHeightPx = with(density) { previewHeight.toPx() }
        val paddingPx = with(density) { padding.toPx() }

        var currentCorner by remember { mutableStateOf(initialCorner) }
        var isDragging by remember { mutableStateOf(false) }
        var dragOffset by remember { mutableStateOf(Offset.Zero) }
        var isExpanded by remember { mutableStateOf(false) }

        val currentWidth = if (isExpanded) previewWidth * 1.5f else previewWidth
        val currentHeight = if (isExpanded) previewHeight * 1.5f else previewHeight
        val currentWidthPx = with(density) { currentWidth.toPx() }
        val currentHeightPx = with(density) { currentHeight.toPx() }

        fun getCornerOffset(corner: PreviewCorner): Offset {
            return when (corner) {
                PreviewCorner.TOP_LEFT -> Offset(paddingPx, paddingPx)
                PreviewCorner.TOP_RIGHT -> Offset(
                    containerWidthPx - currentWidthPx - paddingPx,
                    paddingPx
                )
                PreviewCorner.BOTTOM_LEFT -> Offset(
                    paddingPx,
                    containerHeightPx - currentHeightPx - paddingPx
                )
                PreviewCorner.BOTTOM_RIGHT -> Offset(
                    containerWidthPx - currentWidthPx - paddingPx,
                    containerHeightPx - currentHeightPx - paddingPx
                )
            }
        }

        fun findNearestCorner(offset: Offset): PreviewCorner {
            val corners = PreviewCorner.entries.map { corner ->
                corner to getCornerOffset(corner)
            }

            return corners.minByOrNull { (_, cornerOffset) ->
                val dx = offset.x - cornerOffset.x
                val dy = offset.y - cornerOffset.y
                dx * dx + dy * dy
            }?.first ?: PreviewCorner.TOP_RIGHT
        }

        val targetOffset = if (isDragging) {
            dragOffset
        } else {
            getCornerOffset(currentCorner)
        }

        val animatedOffset by animateOffsetAsState(
            targetValue = targetOffset,
            animationSpec = spring(
                dampingRatio = 0.7f,
                stiffness = 300f
            ),
            label = "previewOffset"
        )

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        animatedOffset.x.roundToInt(),
                        animatedOffset.y.roundToInt()
                    )
                }
                .size(currentWidth, currentHeight)
                .shadow(8.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            isExpanded = !isExpanded
                        },
                        onDoubleTap = {
                            onVisibilityToggle()
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            isDragging = true
                            dragOffset = getCornerOffset(currentCorner)
                        },
                        onDragEnd = {
                            isDragging = false
                            currentCorner = findNearestCorner(dragOffset)
                        },
                        onDragCancel = {
                            isDragging = false
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val newX = (dragOffset.x + dragAmount.x)
                                .coerceIn(0f, containerWidthPx - currentWidthPx)
                            val newY = (dragOffset.y + dragAmount.y)
                                .coerceIn(0f, containerHeightPx - currentHeightPx)
                            dragOffset = Offset(newX, newY)
                        }
                    )
                }
        ) {
            content()
        }
    }
}

/**
 * Simplified preview overlay without drag functionality.
 *
 * Used when the preview should be fixed in a corner.
 *
 * @param modifier Modifier for the container
 * @param corner Corner position
 * @param previewWidth Width of the preview
 * @param previewHeight Height of the preview
 * @param padding Padding from screen edge
 * @param content The preview content
 */
@Composable
fun FixedPreview(
    modifier: Modifier = Modifier,
    corner: PreviewCorner = PreviewCorner.TOP_RIGHT,
    previewWidth: Dp = 120.dp,
    previewHeight: Dp = 160.dp,
    padding: Dp = 16.dp,
    content: @Composable () -> Unit
) {
    val alignment = when (corner) {
        PreviewCorner.TOP_LEFT -> Alignment.TopStart
        PreviewCorner.TOP_RIGHT -> Alignment.TopEnd
        PreviewCorner.BOTTOM_LEFT -> Alignment.BottomStart
        PreviewCorner.BOTTOM_RIGHT -> Alignment.BottomEnd
    }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .align(alignment)
                .offset(
                    x = when (corner) {
                        PreviewCorner.TOP_LEFT, PreviewCorner.BOTTOM_LEFT -> padding
                        else -> -padding
                    },
                    y = when (corner) {
                        PreviewCorner.TOP_LEFT, PreviewCorner.TOP_RIGHT -> padding
                        else -> -padding
                    }
                )
                .size(previewWidth, previewHeight)
                .shadow(8.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black)
        ) {
            content()
        }
    }
}
