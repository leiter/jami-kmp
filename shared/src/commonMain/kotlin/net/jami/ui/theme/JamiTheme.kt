/*
 *  Copyright (C) 2004-2025 Savoir-faire Linux Inc.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */
package net.jami.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalJamiColors = staticCompositionLocalOf { JamiLightColors }
private val LocalJamiTypography = staticCompositionLocalOf { JamiTypography() }
private val LocalJamiSpacing = staticCompositionLocalOf { JamiSpacing() }
private val LocalJamiRadius = staticCompositionLocalOf { JamiRadius() }
private val LocalJamiSizes = staticCompositionLocalOf { JamiSizes() }

@Immutable
object JamiTheme {
    val colors: JamiThemeColors
        @Composable
        @ReadOnlyComposable
        get() = LocalJamiColors.current

    val typography: JamiTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalJamiTypography.current

    val spacing: JamiSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalJamiSpacing.current

    val radius: JamiRadius
        @Composable
        @ReadOnlyComposable
        get() = LocalJamiRadius.current

    val sizes: JamiSizes
        @Composable
        @ReadOnlyComposable
        get() = LocalJamiSizes.current
}

private val LightMaterialColorScheme = lightColorScheme(
    primary = JamiColors.Blue500,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = JamiColors.Cyan500,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    background = JamiColors.Grey50,
    onBackground = JamiColors.Grey900,
    surface = androidx.compose.ui.graphics.Color.White,
    onSurface = JamiColors.Grey900,
    error = JamiColors.Red500,
    onError = androidx.compose.ui.graphics.Color.White,
)

private val DarkMaterialColorScheme = darkColorScheme(
    primary = JamiColors.Blue300,
    onPrimary = JamiColors.Blue900,
    secondary = JamiColors.Cyan300,
    onSecondary = JamiColors.Grey900,
    background = JamiColors.DarkBackground,
    onBackground = JamiColors.Grey200,
    surface = JamiColors.DarkSurface,
    onSurface = JamiColors.Grey200,
    error = androidx.compose.ui.graphics.Color(0xFFEF5350),
    onError = JamiColors.Grey900,
)

@Composable
fun JamiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val jamiColors = if (darkTheme) JamiDarkColors else JamiLightColors
    val materialColorScheme = if (darkTheme) DarkMaterialColorScheme else LightMaterialColorScheme

    CompositionLocalProvider(
        LocalJamiColors provides jamiColors,
        LocalJamiTypography provides JamiTypography(),
        LocalJamiSpacing provides JamiSpacing(),
        LocalJamiRadius provides JamiRadius(),
        LocalJamiSizes provides JamiSizes(),
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            content = content
        )
    }
}
