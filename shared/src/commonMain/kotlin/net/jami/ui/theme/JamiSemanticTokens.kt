package net.jami.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp

/**
 * Semantic token layer — maps raw JamiTheme values to named design roles.
 *
 * Follow the same three-tier pattern as AndrewCompose:
 *   Base (raw palette) → Semantic (role-named) → Component (per-widget)
 *
 * Obtain via [JamiTheme.semantic].
 */
data class JamiSemanticTokens(
    val color: Color,
    val typography: Typography,
    val spacing: Spacing,
    val radius: Radius,
    val sizing: Sizing,
) {
    // ── Colors ────────────────────────────────────────────────────────────

    data class Color(
        val text: Text,
        val background: Background,
        val border: Border,
        val icon: Icon,
    ) {
        data class Text(
            val primary: androidx.compose.ui.graphics.Color,
            val secondary: androidx.compose.ui.graphics.Color,
            val caption: androidx.compose.ui.graphics.Color,
            val link: androidx.compose.ui.graphics.Color,
            val positive: androidx.compose.ui.graphics.Color,
            val negative: androidx.compose.ui.graphics.Color,
            val warning: androidx.compose.ui.graphics.Color,
            val disabled: androidx.compose.ui.graphics.Color,
            val onPrimary: androidx.compose.ui.graphics.Color,
            val onMessageSent: androidx.compose.ui.graphics.Color,
            val onMessageReceived: androidx.compose.ui.graphics.Color,
        )

        data class Background(
            val screen: androidx.compose.ui.graphics.Color,
            val surface: androidx.compose.ui.graphics.Color,
            val surfaceVariant: androidx.compose.ui.graphics.Color,
            val primary: androidx.compose.ui.graphics.Color,
            val positive: androidx.compose.ui.graphics.Color,
            val negative: androidx.compose.ui.graphics.Color,
            val warning: androidx.compose.ui.graphics.Color,
            val disabled: androidx.compose.ui.graphics.Color,
            val messageSent: androidx.compose.ui.graphics.Color,
            val messageReceived: androidx.compose.ui.graphics.Color,
            val avatar: androidx.compose.ui.graphics.Color,
            val inputField: androidx.compose.ui.graphics.Color,
            val overlay: androidx.compose.ui.graphics.Color,
        )

        data class Border(
            val default: androidx.compose.ui.graphics.Color,
            val focused: androidx.compose.ui.graphics.Color,
            val error: androidx.compose.ui.graphics.Color,
            val disabled: androidx.compose.ui.graphics.Color,
            val divider: androidx.compose.ui.graphics.Color,
        )

        data class Icon(
            val primary: androidx.compose.ui.graphics.Color,
            val secondary: androidx.compose.ui.graphics.Color,
            val positive: androidx.compose.ui.graphics.Color,
            val negative: androidx.compose.ui.graphics.Color,
            val warning: androidx.compose.ui.graphics.Color,
            val disabled: androidx.compose.ui.graphics.Color,
            val onPrimary: androidx.compose.ui.graphics.Color,
        )
    }

    // ── Typography ────────────────────────────────────────────────────────

    data class Typography(
        val titleLarge: TextStyle,
        val title: TextStyle,
        val titleSmall: TextStyle,
        val body: TextStyle,
        val bodyBold: TextStyle,
        val caption: TextStyle,
        val captionBold: TextStyle,
        val label: TextStyle,
        val action: TextStyle,
        val link: TextStyle,
    )

    // ── Spacing ───────────────────────────────────────────────────────────

    data class Spacing(
        val xxs: Dp,
        val xs: Dp,
        val s: Dp,
        val m: Dp,
        val l: Dp,
        val xl: Dp,
        val xxl: Dp,
    )

    // ── Radius ────────────────────────────────────────────────────────────

    data class Radius(
        val none: Dp,
        val xs: Dp,
        val s: Dp,
        val m: Dp,
        val l: Dp,
        val full: Dp,
    )

    // ── Sizing ────────────────────────────────────────────────────────────

    data class Sizing(
        val minTouchTarget: Dp,
        val iconSmall: Dp,
        val iconMedium: Dp,
        val iconLarge: Dp,
        val avatarSmall: Dp,
        val avatarMedium: Dp,
        val avatarLarge: Dp,
    )
}
