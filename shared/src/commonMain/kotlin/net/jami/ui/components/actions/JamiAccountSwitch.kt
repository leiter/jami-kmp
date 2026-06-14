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
package net.jami.ui.components.actions

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.jami.ui.theme.JamiColors

/**
 * Account enable/disable switch that renders the status label INSIDE the track,
 * matching the reference cx.ring.views.SwitchButton visual:
 *
 * - Track: 96×24 dp pill shape; green_400 (#66BB6A) when on, grey_400 (#BDBDBD) when off.
 *   (96 = 20dp thumb + 4dp margins + 72dp text area, matching reference DEFAULT_SWITCH_WIDTH.)
 * - Thumb: 20 dp white circle with 2 dp margin.
 * - Text: current [label] drawn in white (13 sp) on the half of the track opposite the thumb,
 *   fading in when the thumb reaches its resting position and fading out during travel.
 * - Animation: 250 ms FastOutSlowIn (≈ Android AccelerateDecelerate interpolator).
 */
@Composable
fun JamiAccountSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    // Reference uses green_400 (#66BB6A) for ON and grey_400 (#BDBDBD) for OFF.
    val onColor = JamiColors.Green400
    val offColor = JamiColors.Grey400

    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "accountSwitchProgress",
    )

    val textMeasurer = rememberTextMeasurer()
    val textStyle = remember {
        TextStyle(
            color = Color.White,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
    }
    val measuredText = remember(label) { textMeasurer.measure(label, textStyle) }

    Box(
        modifier = modifier
            .size(width = 96.dp, height = 24.dp)
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                role = Role.Switch,
                indication = null,
                interactionSource = null,
            )
            .drawBehind {
                val thumbSizePx = 20.dp.toPx()
                val thumbMarginPx = 2.dp.toPx()
                val cornerRadius = size.height / 2f

                // Track — color interpolates between off and on
                drawRoundRect(
                    color = lerp(offColor, onColor, progress),
                    cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                )

                // Thumb — white circle travelling left ↔ right
                val thumbX = thumbMarginPx + thumbSizePx / 2f +
                    progress * (size.width - thumbSizePx - 2f * thumbMarginPx)
                drawCircle(
                    color = Color.White,
                    radius = thumbSizePx / 2f,
                    center = Offset(thumbX, size.height / 2f),
                )

                // Text alpha: fade in when thumb reaches rest (≥0.75 or <0.25),
                // invisible during travel (0.25–0.75).
                val alpha = when {
                    progress >= 0.75f -> (progress * 4f - 3f).coerceIn(0f, 1f)
                    progress < 0.25f -> (1f - progress * 4f).coerceIn(0f, 1f)
                    else -> 0f
                }

                if (alpha > 0.01f) {
                    val tw = measuredText.size.width.toFloat()
                    val th = measuredText.size.height.toFloat()
                    // On side (thumb right): center text in left portion of track.
                    // Off side (thumb left): center text in right portion of track.
                    val halfSpan = size.width - thumbSizePx - thumbMarginPx
                    val textCenterX = if (progress > 0.5f) halfSpan / 2f
                                      else size.width - halfSpan / 2f
                    drawText(
                        textMeasurer = textMeasurer,
                        text = label,
                        style = textStyle,
                        topLeft = Offset(
                            x = textCenterX - tw / 2f,
                            y = (size.height - th) / 2f,
                        ),
                    )
                }
            }
    )
}
