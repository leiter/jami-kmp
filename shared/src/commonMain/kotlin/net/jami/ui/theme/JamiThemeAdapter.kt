package net.jami.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Returns a [JamiSemanticTokens] instance derived from the current [JamiTheme] values.
 *
 * Usage:
 * ```
 * val semantic = JamiTheme.semantic()
 * Text(color = semantic.color.text.secondary, ...)
 * ```
 */
@Composable
@ReadOnlyComposable
fun JamiTheme.semantic(): JamiSemanticTokens {
    val c = colors
    val t = typography
    val sp = spacing
    val r = radius
    val sz = sizes

    return JamiSemanticTokens(
        color = JamiSemanticTokens.Color(
            text = JamiSemanticTokens.Color.Text(
                primary = c.onBackground,
                secondary = c.onSurfaceVariant,
                caption = c.onSurfaceVariant.copy(alpha = 0.7f),
                link = c.primary,
                positive = c.positive,
                negative = c.error,
                warning = c.warning,
                disabled = c.onDisabled,
                onPrimary = c.onPrimary,
                onMessageSent = c.onMessageSent,
                onMessageReceived = c.onMessageReceived,
            ),
            background = JamiSemanticTokens.Color.Background(
                screen = c.background,
                surface = c.surface,
                surfaceVariant = c.surfaceVariant,
                primary = c.primary,
                positive = c.positive.copy(alpha = 0.12f),
                negative = c.error.copy(alpha = 0.12f),
                warning = c.warning.copy(alpha = 0.12f),
                disabled = c.disabled,
                messageSent = c.messageSent,
                messageReceived = c.messageReceived,
                avatar = c.surfaceVariant,
                inputField = c.surface,
                overlay = Color.Black.copy(alpha = 0.4f),
            ),
            border = JamiSemanticTokens.Color.Border(
                default = c.outline,
                focused = c.primary,
                error = c.error,
                disabled = c.disabled,
                divider = c.outline.copy(alpha = 0.6f),
            ),
            icon = JamiSemanticTokens.Color.Icon(
                primary = c.onBackground,
                secondary = c.onSurfaceVariant,
                positive = c.positive,
                negative = c.error,
                warning = c.warning,
                disabled = c.onDisabled,
                onPrimary = c.onPrimary,
            ),
        ),
        typography = JamiSemanticTokens.Typography(
            titleLarge = t.headlineMedium,
            title = t.titleLarge,
            titleSmall = t.titleMedium,
            body = t.bodyLarge,
            bodyBold = t.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
            caption = t.bodySmall,
            captionBold = t.bodySmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
            label = t.labelMedium,
            action = t.labelLarge,
            link = t.bodyMedium,
        ),
        spacing = JamiSemanticTokens.Spacing(
            xxs = sp.xxs,
            xs = sp.xs,
            s = sp.s,
            m = sp.m,
            l = sp.l,
            xl = sp.xl,
            xxl = sp.xxl,
        ),
        radius = JamiSemanticTokens.Radius(
            none = r.none,
            xs = r.xs,
            s = r.s,
            m = r.m,
            l = r.l,
            full = r.full,
        ),
        sizing = JamiSemanticTokens.Sizing(
            minTouchTarget = sz.minTouchTarget,
            iconSmall = sz.iconSmall,
            iconMedium = sz.iconMedium,
            iconLarge = sz.iconLarge,
            avatarSmall = sz.avatarSmall,
            avatarMedium = sz.avatarMedium,
            avatarLarge = sz.avatarLarge,
        ),
    )
}

/**
 * Returns a [JamiComponentTokens] instance derived from [JamiTheme.semantic].
 *
 * Usage:
 * ```
 * val comp = JamiTheme.component()
 * Box(modifier = Modifier.background(comp.message.sent.background)) { ... }
 * ```
 */
@Composable
@ReadOnlyComposable
fun JamiTheme.component(): JamiComponentTokens {
    val s = semantic()
    val c = s.color
    val t = s.typography
    val sp = s.spacing
    val r = s.radius
    val sz = s.sizing

    return JamiComponentTokens(

        button = JamiComponentTokens.Button(
            primary = JamiComponentTokens.Button.Variant(
                background = c.background.primary,
                text = c.text.onPrimary,
                border = Color.Transparent,
                backgroundPressed = colors.primary.copy(alpha = 0.85f),
                backgroundDisabled = c.background.disabled,
                textDisabled = c.text.disabled,
                strokeWidth = 0.dp,
            ),
            secondary = JamiComponentTokens.Button.Variant(
                background = Color.Transparent,
                text = c.text.primary,
                border = c.border.default,
                backgroundPressed = c.background.surfaceVariant,
                backgroundDisabled = Color.Transparent,
                textDisabled = c.text.disabled,
                strokeWidth = sizes.buttonBorderWidth,
            ),
            destructive = JamiComponentTokens.Button.Variant(
                background = colors.error,
                text = colors.onError,
                border = Color.Transparent,
                backgroundPressed = colors.error.copy(alpha = 0.85f),
                backgroundDisabled = c.background.disabled,
                textDisabled = c.text.disabled,
                strokeWidth = 0.dp,
            ),
            ghost = JamiComponentTokens.Button.Variant(
                background = Color.Transparent,
                text = c.text.link,
                border = Color.Transparent,
                backgroundPressed = c.background.primary.copy(alpha = 0.08f),
                backgroundDisabled = Color.Transparent,
                textDisabled = c.text.disabled,
                strokeWidth = 0.dp,
            ),
            paddingHorizontal = sp.l,
            paddingVertical = sp.m,
            radius = r.m,
            textStyle = t.action,
        ),

        avatar = JamiComponentTokens.Avatar(
            backgroundDefault = c.background.avatar,
            initialsText = c.text.primary,
            iconDefault = c.icon.secondary,
            borderColor = c.border.default,
            borderStroke = 0.dp,
            radiusFull = r.full,
            padding = sp.xs,
        ),

        inputField = JamiComponentTokens.InputField(
            text = JamiComponentTokens.InputField.Text(
                backgroundDefault = c.background.inputField,
                backgroundDisabled = c.background.disabled,
                textDefault = c.text.primary,
                textDisabled = c.text.disabled,
                placeholder = c.text.caption,
                borderDefault = c.border.default,
                borderFocused = c.border.focused,
                borderError = c.border.error,
                borderDisabled = c.border.disabled,
                cursor = c.background.primary,
                textStyle = t.body,
            ),
            paddingHorizontal = sp.l,
            paddingVertical = sp.m,
            radius = r.xs,
            strokeDefault = 1.dp,
            strokeFocused = 2.dp,
        ),

        message = JamiComponentTokens.Message(
            sent = JamiComponentTokens.Message.Bubble(
                background = c.background.messageSent,
                text = c.text.onMessageSent,
                linkText = colors.onPrimary,
                timestamp = c.text.onMessageSent.copy(alpha = 0.7f),
            ),
            received = JamiComponentTokens.Message.Bubble(
                background = c.background.messageReceived,
                text = c.text.onMessageReceived,
                linkText = c.text.link,
                timestamp = c.text.caption,
            ),
            paddingHorizontal = sp.m,
            paddingVertical = sp.s,
            radiusLarge = r.l,
            radiusSmall = r.xs,
            textStyle = t.body,
            captionStyle = t.caption,
        ),

        conversationItem = JamiComponentTokens.ConversationItem(
            background = c.background.surface,
            backgroundPressed = c.background.surfaceVariant,
            titleText = c.text.primary,
            subtitleText = c.text.secondary,
            timestampText = c.text.caption,
            divider = c.border.divider,
            titleStyle = t.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
            subtitleStyle = t.caption,
            timestampStyle = t.caption,
            paddingHorizontal = sp.l,
            paddingVertical = sp.m,
            avatarSpacing = sp.m,
        ),

        badge = JamiComponentTokens.Badge(
            background = c.background.primary,
            text = c.text.onPrimary,
            textStyle = t.captionBold,
            paddingHorizontal = sp.xs,
            paddingVertical = sp.xxs,
            radiusFull = r.full,
            minSize = sz.minTouchTarget / 2.4f,
        ),

        topBar = JamiComponentTokens.TopBar(
            background = c.background.surface,
            title = c.text.primary,
            icon = c.icon.primary,
            titleStyle = t.titleSmall,
            height = sizes.topBarHeight,
        ),

        navigationBar = JamiComponentTokens.NavigationBar(
            background = c.background.surface,
            iconSelected = c.background.primary,
            iconUnselected = c.icon.secondary,
            labelSelected = c.background.primary,
            labelUnselected = c.text.secondary,
            labelStyle = t.label,
            indicator = c.background.primary.copy(alpha = 0.12f),
        ),

        toggle = JamiComponentTokens.Toggle(
            trackOn = c.background.primary,
            trackOff = c.border.default,
            trackDisabledOn = c.background.primary.copy(alpha = 0.4f),
            trackDisabledOff = c.background.disabled,
            thumb = colors.surface,
            thumbDisabled = colors.surface.copy(alpha = 0.8f),
        ),

        callAction = JamiComponentTokens.CallAction(
            accept = JamiComponentTokens.CallAction.Variant(
                background = colors.positive,
                backgroundPressed = colors.positive.copy(alpha = 0.85f),
                icon = colors.onPositive,
            ),
            decline = JamiComponentTokens.CallAction.Variant(
                background = colors.error,
                backgroundPressed = colors.error.copy(alpha = 0.85f),
                icon = colors.onError,
            ),
            neutral = JamiComponentTokens.CallAction.Variant(
                background = c.background.surfaceVariant,
                backgroundPressed = c.border.default,
                icon = c.icon.primary,
            ),
            active = JamiComponentTokens.CallAction.Variant(
                background = c.background.primary,
                backgroundPressed = c.background.primary.copy(alpha = 0.85f),
                icon = c.text.onPrimary,
            ),
            size = sizes.fabSize,
            iconSize = sz.iconLarge,
            radius = r.full,
        ),

        presenceIndicator = JamiComponentTokens.PresenceIndicator(
            online = colors.positive,
            away = colors.warning,
            offline = c.icon.secondary,
            sizeSmall = sizes.presenceDotSmall,
            sizeMedium = sizes.presenceDotMedium,
            sizeLarge = sizes.presenceDotLarge,
            borderColor = c.background.surface,
            borderStroke = 2.dp,
        ),
    )
}
