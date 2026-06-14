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
package net.jami.ui.components.navigation

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.jami.ui.components.actions.JamiIconButton
import net.jami.ui.theme.JamiTheme

/**
 * Top bar style presets.
 */
enum class JamiTopBarStyle {
    /** Flat bar without navigation icon, typically hosts a search area. */
    Main,
    /** Bar with a back arrow and title, used for detail screens. */
    Detail,
    /** Bar with a back arrow and centered title, used for settings screens. */
    Settings,
}

/**
 * Top app bar component supporting multiple screen types.
 *
 * @param style The bar style to render.
 * @param modifier Modifier applied to the bar.
 * @param title The title text (used in [JamiTopBarStyle.Detail] and [JamiTopBarStyle.Settings]).
 * @param onNavigateBack Callback invoked when the back arrow is tapped.
 * @param actions Optional trailing action composables.
 * @param searchContent Optional composable for the search area (used in [JamiTopBarStyle.Main]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JamiTopBar(
    style: JamiTopBarStyle,
    modifier: Modifier = Modifier,
    title: String = "",
    onNavigateBack: (() -> Unit)? = null,
    actions: @Composable (RowScope.() -> Unit) = {},
    searchContent: @Composable (() -> Unit)? = null,
) {
    when (style) {
        JamiTopBarStyle.Main -> {
            TopAppBar(
                title = {
                    if (searchContent != null) {
                        searchContent()
                    }
                },
                modifier = modifier,
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = JamiTheme.colors.background,
                    titleContentColor = JamiTheme.colors.onSurface,
                    actionIconContentColor = JamiTheme.colors.onSurfaceVariant,
                ),
            )
        }

        JamiTopBarStyle.Detail -> {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        style = JamiTheme.typography.titleMedium,
                    )
                },
                modifier = modifier,
                navigationIcon = {
                    if (onNavigateBack != null) {
                        JamiIconButton(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            onClick = onNavigateBack,
                            contentDescription = "Navigate back",
                        )
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = JamiTheme.colors.background,
                    titleContentColor = JamiTheme.colors.onSurface,
                    navigationIconContentColor = JamiTheme.colors.onSurface,
                    actionIconContentColor = JamiTheme.colors.onSurfaceVariant,
                ),
            )
        }

        JamiTopBarStyle.Settings -> {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = title,
                        style = JamiTheme.typography.titleMedium,
                    )
                },
                modifier = modifier,
                navigationIcon = {
                    if (onNavigateBack != null) {
                        JamiIconButton(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            onClick = onNavigateBack,
                            contentDescription = "Navigate back",
                        )
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = JamiTheme.colors.background,
                    titleContentColor = JamiTheme.colors.onSurface,
                    navigationIconContentColor = JamiTheme.colors.onSurface,
                    actionIconContentColor = JamiTheme.colors.onSurfaceVariant,
                ),
            )
        }
    }
}
