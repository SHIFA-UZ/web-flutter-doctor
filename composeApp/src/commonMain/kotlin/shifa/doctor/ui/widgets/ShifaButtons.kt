package shifa.doctor.ui.widgets

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaDimens
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaShadow
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.theme.lineBox

// `.sh-btn` (shifa-ds.css "Buttons" + the audit's "two heights only"): Regular is 46 (size-control-lg) for screen
// actions, Compact 40 on phones (36 on web) for toolbars and card rows. Icons are drawn at their drawable size
// (22 for Regular, 16 for Compact and the action pill).
enum class ShifaButtonSize { Regular, Compact }

@Composable
fun ShifaPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: DrawableResource? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
    loading: Boolean = false,
    size: ShifaButtonSize = ShifaButtonSize.Regular,
) {
    val bg =
        when {
            !enabled -> ShifaColors.Interactive.DisabledBg
            destructive -> ShifaColors.Interactive.Destructive
            else -> ShifaColors.Interactive.Primary
        }
    val content = if (enabled) ShifaColors.Interactive.OnPrimary else ShifaColors.Text.Disabled
    ButtonContent(
        text = text,
        icon = icon,
        color = content,
        loading = loading,
        size = size,
        modifier =
            modifier
                .then(if (enabled) Modifier.dropShadow(ShifaRadius.ControlMd, ShifaShadow.Default) else Modifier)
                .clip(ShifaRadius.ControlMd)
                .background(bg)
                .clickable(enabled = enabled && !loading, role = Role.Button, onClick = onClick),
    )
}

@Composable
fun ShifaSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: DrawableResource? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
    loading: Boolean = false,
    size: ShifaButtonSize = ShifaButtonSize.Regular,
) {
    val (border, content) =
        when {
            !enabled -> ShifaColors.Border.Disabled to ShifaColors.Text.Disabled
            destructive -> ShifaColors.Border.Error to ShifaColors.Text.Error
            else -> ShifaColors.Border.Brand to ShifaColors.Text.BrandStrong
        }
    ButtonContent(
        text = text,
        icon = icon,
        color = content,
        loading = loading,
        size = size,
        modifier =
            modifier
                .dropShadow(ShifaRadius.ControlMd, ShifaShadow.Default)
                .clip(ShifaRadius.ControlMd)
                .background(ShifaColors.Bg.Surface)
                .border(ShifaSize.S2, border, ShifaRadius.ControlMd)
                .clickable(enabled = enabled && !loading, role = Role.Button, onClick = onClick),
    )
}

@Composable
fun ShifaTertiaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: DrawableResource? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
    size: ShifaButtonSize = ShifaButtonSize.Regular,
) {
    val content =
        when {
            !enabled -> ShifaColors.Text.Disabled
            destructive -> ShifaColors.Text.Error
            else -> ShifaColors.Text.BrandStrong
        }
    ButtonContent(
        text = text,
        icon = icon,
        color = content,
        loading = false,
        size = size,
        tertiary = true,
        modifier =
            modifier
                .clip(ShifaRadius.ControlMd)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    )
}

/** `.sh-btn--ai`: charcoal, so AI actions ("Start AI notes", "Prepare briefing") read apart from the teal primary. */
@Composable
fun ShifaAiButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: DrawableResource? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    size: ShifaButtonSize = ShifaButtonSize.Regular,
) = ButtonContent(
    text = text,
    icon = icon,
    color = if (enabled) ShifaColors.Text.Inverse else ShifaColors.Text.Disabled,
    loading = loading,
    size = size,
    modifier =
        modifier
            .then(if (enabled) Modifier.dropShadow(ShifaRadius.ControlMd, ShifaShadow.Dropdown) else Modifier)
            .clip(ShifaRadius.ControlMd)
            .background(if (enabled) ShifaColors.Text.Primary else ShifaColors.Interactive.DisabledBg)
            .clickable(enabled = enabled && !loading, role = Role.Button, onClick = onClick),
)

// `.sh-action` variants: Primary (teal), Secondary (canvas), Surface (white lifted pill on the canvas), Tint (teal-subtle).
enum class ShifaActionVariant { Primary, Secondary, Surface, Tint }

/** `.sh-action`, the pill. Text only, icon only (pass [contentDescription]) or both. [selected] is the toggle state. */
@Composable
fun ShifaActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    icon: DrawableResource? = null,
    contentDescription: String? = null,
    variant: ShifaActionVariant = ShifaActionVariant.Primary,
    enabled: Boolean = true,
    destructive: Boolean = false,
    selected: Boolean = false,
    iconAfter: Boolean = false,
) {
    val (bg, content) =
        when {
            !enabled -> ShifaColors.Interactive.DisabledBg to ShifaColors.Text.Disabled
            destructive -> ShifaColors.Interactive.Destructive to ShifaColors.Interactive.OnPrimary
            selected -> ShifaColors.Bg.SuccessSubtle to ShifaColors.Text.Primary
            variant == ShifaActionVariant.Secondary -> ShifaColors.Bg.Canvas to ShifaColors.Icon.Primary
            variant == ShifaActionVariant.Surface -> ShifaColors.Bg.Surface to ShifaColors.Text.Primary
            variant == ShifaActionVariant.Tint -> ShifaColors.Bg.SuccessSubtle to ShifaColors.Text.Primary
            else -> ShifaColors.Interactive.Primary to ShifaColors.Interactive.OnPrimary
        }
    val padding =
        when {
            text == null -> PaddingValues(ShifaSpacing.Inset.Xs)
            icon == null -> PaddingValues(horizontal = ShifaSpacing.Inset.Md, vertical = ShifaSpacing.Inset.Xs)
            else -> PaddingValues(horizontal = ShifaSpacing.Inset.Sm, vertical = ShifaSpacing.Inset.Xs)
        }
    Row(
        modifier =
            modifier
                .then(
                    if (variant == ShifaActionVariant.Surface && enabled && !selected) {
                        Modifier.dropShadow(ShifaRadius.Full, ShifaShadow.Dropdown)
                    } else {
                        Modifier
                    },
                ).clip(ShifaRadius.Full)
                .background(bg)
                .then(
                    if (selected) {
                        Modifier.border(
                            ShifaSize.S2,
                            ShifaColors.Border.Focus,
                            ShifaRadius.Full,
                        )
                    } else {
                        Modifier
                    },
                )
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .then(if (selected) Modifier.semantics { this.selected = true } else Modifier)
                .padding(padding),
        horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Xs, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!iconAfter) icon?.let { Icon(painterResource(it), contentDescription, tint = content) }
        text?.let {
            val style = MaterialTheme.typography.labelMedium
            Text(
                it,
                Modifier.lineBox(style),
                style = style,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (iconAfter) icon?.let { Icon(painterResource(it), contentDescription, tint = content) }
    }
}

// `.sh-icon-btn` variants. Primary is teal with a shadow; the rest are flat.
enum class ShifaIconButtonVariant { Primary, Secondary, Ghost, Surface, Danger, OnDark }

/** `.sh-icon-btn`: always a 44 square (the touch size) with an 18 icon; never stretches. [selected] is the toggle state. */
@Composable
fun ShifaIconButton(
    icon: DrawableResource,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ShifaIconButtonVariant = ShifaIconButtonVariant.Primary,
    enabled: Boolean = true,
    destructive: Boolean = false,
    selected: Boolean = false,
) {
    val (bg, tint) =
        when {
            !enabled -> ShifaColors.Interactive.DisabledBg to ShifaColors.Icon.Disabled
            selected -> ShifaColors.Bg.SuccessSubtle to ShifaColors.Icon.Primary
            destructive && variant == ShifaIconButtonVariant.Primary ->
                ShifaColors.Interactive.Destructive to ShifaColors.Icon.Inverse
            variant == ShifaIconButtonVariant.Primary -> ShifaColors.Interactive.Primary to ShifaColors.Icon.Inverse
            variant == ShifaIconButtonVariant.Secondary ->
                ShifaColors.Bg.Canvas to if (destructive) ShifaColors.Icon.Error else ShifaColors.Icon.Primary
            variant == ShifaIconButtonVariant.Ghost -> Color.Transparent to ShifaColors.Icon.Primary
            variant == ShifaIconButtonVariant.Surface -> ShifaColors.Bg.Surface to ShifaColors.Icon.Primary
            variant == ShifaIconButtonVariant.Danger -> ShifaColors.Bg.ErrorSubtle to ShifaColors.Icon.Error
            else -> ShifaColors.Alpha.White15 to ShifaColors.Icon.Inverse
        }
    Box(
        modifier =
            modifier
                .size(ShifaSize.Touch)
                .then(
                    if (variant == ShifaIconButtonVariant.Primary && enabled) {
                        Modifier.dropShadow(ShifaRadius.Full, ShifaShadow.Default)
                    } else {
                        Modifier
                    },
                ).clip(ShifaRadius.Full)
                .background(bg)
                .then(
                    if (selected) {
                        Modifier.border(
                            ShifaSize.S2,
                            ShifaColors.Border.Focus,
                            ShifaRadius.Full,
                        )
                    } else {
                        Modifier
                    },
                )
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .then(if (selected) Modifier.semantics { this.selected = true } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription, Modifier.size(ShifaSize.S18), tint = tint)
    }
}

// `.sh-link` variants: underlined teal (Default), no underline (Plain), charcoal (Quiet), red (Danger).
enum class ShifaLinkVariant { Default, Plain, Quiet, Danger }

/** `.sh-link`: an inline text action ("See all", a patient's name). 44 tall on phones for the touch target. */
@Composable
fun ShifaLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ShifaLinkVariant = ShifaLinkVariant.Default,
    style: TextStyle = MaterialTheme.typography.titleSmall,
) {
    val color =
        when (variant) {
            ShifaLinkVariant.Quiet -> ShifaColors.Text.Primary
            ShifaLinkVariant.Danger -> ShifaColors.Text.Error
            else -> ShifaColors.Text.Link
        }
    Box(
        modifier
            .defaultMinSize(minHeight = ShifaSize.Touch)
            .clip(ShifaRadius.Xs)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text,
            style =
                style.copy(
                    fontWeight = FontWeight.Bold,
                    textDecoration = if (variant == ShifaLinkVariant.Default) TextDecoration.Underline else null,
                ),
            color = color,
        )
    }
}

/** `.sh-fab`: the one floating create action on phone lists. [text] null gives the round icon-only FAB. */
@Composable
fun ShifaFab(
    icon: DrawableResource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    contentDescription: String? = text,
) {
    Row(
        modifier
            .height(ShifaSize.S56)
            .defaultMinSize(minWidth = ShifaSize.S56)
            .dropShadow(ShifaRadius.Full, ShifaShadow.Fab)
            .clip(ShifaRadius.Full)
            .background(ShifaColors.Interactive.Primary)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription?.let { this.contentDescription = it } }
            .then(
                if (text == null) {
                    Modifier.size(ShifaSize.S56)
                } else {
                    Modifier.padding(start = ShifaSpacing.Inset.Md, end = ShifaSpacing.Inset.Lg)
                },
            ),
        horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), null, tint = ShifaColors.Interactive.OnPrimary)
        text?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = ShifaColors.Interactive.OnPrimary) }
    }
}

/** `.sh-spinner`: only inside a busy control; pages load with skeletons. Draws in [color] (the control's text). */
@Composable
fun ShifaSpinner(
    color: Color,
    modifier: Modifier = Modifier,
    large: Boolean = false,
) {
    // Static in previews and screenshot tests, where an infinite animation never lets the UI idle.
    val angle =
        if (LocalInspectionMode.current) {
            0f
        } else {
            rememberInfiniteTransition().animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing)),
            ).value
        }
    val size: Dp = if (large) ShifaSize.S28 else ShifaSize.S16
    val stroke: Dp = if (large) ShifaSize.S3 else ShifaSize.S2
    Canvas(modifier.size(size).graphicsLayer { rotationZ = angle }) {
        val w = stroke.toPx()
        drawArc(
            color = color,
            startAngle = 0f,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = Offset(w / 2, w / 2),
            size = Size(this.size.width - w, this.size.height - w),
            style = Stroke(width = w, cap = StrokeCap.Butt),
        )
    }
}

@Composable
private fun ButtonContent(
    text: String,
    icon: DrawableResource?,
    color: Color,
    loading: Boolean,
    size: ShifaButtonSize,
    modifier: Modifier,
    tertiary: Boolean = false,
) {
    val compact = size == ShifaButtonSize.Compact
    val padding =
        when {
            tertiary && compact -> PaddingValues(horizontal = ShifaSpacing.Inset.Xs)
            tertiary ->
                PaddingValues(
                    start = ShifaDimens.TertiaryButtonPaddingStart,
                    end = ShifaDimens.TertiaryButtonPaddingEnd,
                    top = ShifaDimens.TertiaryButtonPaddingVertical,
                    bottom = ShifaDimens.TertiaryButtonPaddingVertical,
                )
            compact -> PaddingValues(horizontal = ShifaSpacing.Inset.Md)
            else -> PaddingValues(ShifaSpacing.Inset.Sm)
        }
    Row(
        modifier =
            modifier
                .defaultMinSize(minHeight = if (compact) ShifaSize.S40 else ShifaSize.ControlLg)
                .padding(padding),
        horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            ShifaSpinner(color, Modifier.semantics { contentDescription = text })
        } else {
            icon?.let { Icon(painterResource(it), contentDescription = null, tint = color) }
        }
        val style = if (compact) ShifaTextStyles.buttonCompact() else MaterialTheme.typography.labelLarge
        Text(text, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
