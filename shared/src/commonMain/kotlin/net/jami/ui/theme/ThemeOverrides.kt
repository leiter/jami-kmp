/*
 *  Copyright (C) 2004-2025 Savoir-faire Linux Inc.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */
package net.jami.ui.theme

import androidx.compose.ui.graphics.Color

val JamiLightColors = JamiThemeColors(
    primary = JamiColors.Blue500,
    onPrimary = Color.White,
    accent = JamiColors.Cyan500,
    onAccent = Color.White,
    background = JamiColors.Grey50,
    onBackground = JamiColors.Grey900,
    surface = Color.White,
    onSurface = JamiColors.Grey900,
    surfaceVariant = JamiColors.Grey100,
    onSurfaceVariant = JamiColors.Grey700,
    outline = JamiColors.Grey300,
    error = JamiColors.Red500,
    onError = Color.White,
    positive = JamiColors.Green500,
    onPositive = Color.White,
    warning = JamiColors.Orange500,
    onWarning = Color.White,
    messageSent = JamiColors.Blue500,
    onMessageSent = Color.White,
    messageReceived = JamiColors.Grey200,
    onMessageReceived = JamiColors.Grey900,
    disabled = JamiColors.Grey300,
    onDisabled = JamiColors.Grey500,
)

val JamiDarkColors = JamiThemeColors(
    primary = JamiColors.Blue300,
    onPrimary = JamiColors.Blue900,
    accent = JamiColors.Cyan300,
    onAccent = JamiColors.Grey900,
    background = JamiColors.DarkBackground,
    onBackground = JamiColors.Grey200,
    surface = JamiColors.DarkSurface,
    onSurface = JamiColors.Grey200,
    surfaceVariant = JamiColors.DarkSurfaceVariant,
    onSurfaceVariant = JamiColors.Grey400,
    outline = JamiColors.Grey700,
    error = Color(0xFFEF5350),
    onError = JamiColors.Grey900,
    positive = Color(0xFF81C784),
    onPositive = JamiColors.Grey900,
    warning = Color(0xFFFFB74D),
    onWarning = JamiColors.Grey900,
    messageSent = JamiColors.Blue700,
    onMessageSent = Color.White,
    messageReceived = JamiColors.DarkSurfaceVariant,
    onMessageReceived = JamiColors.Grey200,
    disabled = JamiColors.Grey800,
    onDisabled = JamiColors.Grey600,
)
