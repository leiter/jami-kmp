package net.jami.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp

/**
 * Component token layer — maps semantic tokens to per-widget design decisions.
 *
 * Obtain via [JamiTheme.component].
 */
data class JamiComponentTokens(
    val button: Button,
    val avatar: Avatar,
    val inputField: InputField,
    val message: Message,
    val conversationItem: ConversationItem,
    val badge: Badge,
    val topBar: TopBar,
    val navigationBar: NavigationBar,
    val toggle: Toggle,
    val callAction: CallAction,
    val presenceIndicator: PresenceIndicator,
) {

    // ── Button ────────────────────────────────────────────────────────────

    data class Button(
        val primary: Variant,
        val secondary: Variant,
        val destructive: Variant,
        val ghost: Variant,
        val paddingHorizontal: Dp,
        val paddingVertical: Dp,
        val radius: Dp,
        val textStyle: TextStyle,
    ) {
        data class Variant(
            val background: Color,
            val text: Color,
            val border: Color,
            val backgroundPressed: Color,
            val backgroundDisabled: Color,
            val textDisabled: Color,
            val strokeWidth: Dp,
        )
    }

    // ── Avatar ────────────────────────────────────────────────────────────

    data class Avatar(
        val backgroundDefault: Color,
        val initialsText: Color,
        val iconDefault: Color,
        val borderColor: Color,
        val borderStroke: Dp,
        val radiusFull: Dp,
        val padding: Dp,
    )

    // ── Input field ───────────────────────────────────────────────────────

    data class InputField(
        val text: Text,
        val paddingHorizontal: Dp,
        val paddingVertical: Dp,
        val radius: Dp,
        val strokeDefault: Dp,
        val strokeFocused: Dp,
    ) {
        data class Text(
            val backgroundDefault: Color,
            val backgroundDisabled: Color,
            val textDefault: Color,
            val textDisabled: Color,
            val placeholder: Color,
            val borderDefault: Color,
            val borderFocused: Color,
            val borderError: Color,
            val borderDisabled: Color,
            val cursor: Color,
            val textStyle: TextStyle,
        )
    }

    // ── Message bubble ────────────────────────────────────────────────────

    data class Message(
        val sent: Bubble,
        val received: Bubble,
        val paddingHorizontal: Dp,
        val paddingVertical: Dp,
        val radiusLarge: Dp,
        val radiusSmall: Dp,
        val textStyle: TextStyle,
        val captionStyle: TextStyle,
    ) {
        data class Bubble(
            val background: Color,
            val text: Color,
            val linkText: Color,
            val timestamp: Color,
        )
    }

    // ── Conversation list item ─────────────────────────────────────────────

    data class ConversationItem(
        val background: Color,
        val backgroundPressed: Color,
        val titleText: Color,
        val subtitleText: Color,
        val timestampText: Color,
        val divider: Color,
        val titleStyle: TextStyle,
        val subtitleStyle: TextStyle,
        val timestampStyle: TextStyle,
        val paddingHorizontal: Dp,
        val paddingVertical: Dp,
        val avatarSpacing: Dp,
    )

    // ── Badge (unread count) ───────────────────────────────────────────────

    data class Badge(
        val background: Color,
        val text: Color,
        val textStyle: TextStyle,
        val paddingHorizontal: Dp,
        val paddingVertical: Dp,
        val radiusFull: Dp,
        val minSize: Dp,
    )

    // ── Top app bar ────────────────────────────────────────────────────────

    data class TopBar(
        val background: Color,
        val title: Color,
        val icon: Color,
        val titleStyle: TextStyle,
        val height: Dp,
    )

    // ── Bottom navigation bar ──────────────────────────────────────────────

    data class NavigationBar(
        val background: Color,
        val iconSelected: Color,
        val iconUnselected: Color,
        val labelSelected: Color,
        val labelUnselected: Color,
        val labelStyle: TextStyle,
        val indicator: Color,
    )

    // ── Settings toggle / switch ───────────────────────────────────────────

    data class Toggle(
        val trackOn: Color,
        val trackOff: Color,
        val trackDisabledOn: Color,
        val trackDisabledOff: Color,
        val thumb: Color,
        val thumbDisabled: Color,
    )

    // ── Call action button (circular) ──────────────────────────────────────

    data class CallAction(
        val accept: Variant,
        val decline: Variant,
        val neutral: Variant,
        val active: Variant,
        val size: Dp,
        val iconSize: Dp,
        val radius: Dp,
    ) {
        data class Variant(
            val background: Color,
            val backgroundPressed: Color,
            val icon: Color,
        )
    }

    // ── Presence indicator dot ─────────────────────────────────────────────

    data class PresenceIndicator(
        val online: Color,
        val away: Color,
        val offline: Color,
        val sizeSmall: Dp,
        val sizeMedium: Dp,
        val sizeLarge: Dp,
        val borderColor: Color,
        val borderStroke: Dp,
    )
}
