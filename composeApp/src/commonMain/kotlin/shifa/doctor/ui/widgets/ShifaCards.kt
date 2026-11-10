package shifa.doctor.ui.widgets

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.data.api.fileCacheKey
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.ic_activity_16
import shifa.doctor.generated.resources.ic_alert_16
import shifa.doctor.generated.resources.ic_check_16
import shifa.doctor.generated.resources.ic_chev_r_16
import shifa.doctor.generated.resources.ic_file_text_22
import shifa.doctor.generated.resources.ic_pill_16
import shifa.doctor.generated.resources.ic_x_16
import shifa.doctor.generated.resources.img_avatar_fallback
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaShadow
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.theme.lineBox

/** Cards inside a white surface (a dialog, a panel): canvas fill, no shadow, no chevron (the DS card rule). */
val LocalCardOnSurface = staticCompositionLocalOf { false }

/** `.sh-card`: the white card with the card shadow. On a white surface it turns canvas and flat. */
@Composable
fun ShifaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) = CardChrome(modifier, onClick, content = content)

/** A person's photo, round. No photo, or one that fails to load, shows the brand fallback picture. */
@Composable
fun ShifaAvatar(
    url: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    placeholder: Color = if (LocalCardOnSurface.current) ShifaColors.Bg.Surface else ShifaColors.Bg.Canvas,
) {
    Box(modifier.size(size).clip(ShifaRadius.Full).background(placeholder)) {
        if (url == null) {
            DefaultAvatar()
        } else {
            KamelImage(
                resource = asyncPainterResource(url, key = fileCacheKey(url)),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onFailure = { DefaultAvatar() },
            )
        }
    }
}

@Composable
private fun DefaultAvatar() =
    Image(
        painterResource(Res.drawable.img_avatar_fallback),
        null,
        Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
    )

enum class ShifaStatusTone { Ok, Warn, Err }

/** `.sh-status`: a 28 disc in the tone with its icon (check, alert, cross); the word is [contentDescription]. */
@Composable
fun ShifaStatusDisc(
    tone: ShifaStatusTone,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val (bg, tint, icon) =
        when (tone) {
            ShifaStatusTone.Ok -> Triple(ShifaColors.Bg.SuccessSubtle, ShifaColors.Icon.Brand, Res.drawable.ic_check_16)
            ShifaStatusTone.Warn ->
                Triple(
                    ShifaColors.Bg.WarningSubtle,
                    ShifaColors.Icon.Warning,
                    Res.drawable.ic_alert_16,
                )
            ShifaStatusTone.Err -> Triple(ShifaColors.Bg.ErrorSubtle, ShifaColors.Icon.Error, Res.drawable.ic_x_16)
        }
    Box(modifier.size(ShifaSize.S28).background(bg, ShifaRadius.Full), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), contentDescription, tint = tint)
    }
}

/**
 * `.sh-press--listrow`: a settings or list row inside a grouped section, at least 44 tall, a hairline below.
 * [trailing] replaces the chevron (a switch, a value).
 */
@Composable
fun ShifaSettingsItem(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: DrawableResource? = null,
    subtitle: String? = null,
    destructive: Boolean = false,
    selected: Boolean = false,
    divider: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = ShifaSize.Touch)
                .background(if (selected) ShifaColors.Bg.Selected else Color.Transparent)
                .pointerHoverIcon(PointerIcon.Hand).clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = ShifaSpacing.Inset.Xs),
            horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tint = if (destructive) ShifaColors.Icon.Error else ShifaColors.Icon.Primary
            icon?.let { Icon(painterResource(it), null, tint = tint) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ShifaSize.S2)) {
                CardTitle(title, color = if (destructive) ShifaColors.Text.Error else ShifaColors.Text.Primary)
                subtitle?.let { CardBody(it, ShifaColors.Text.Secondary) }
            }
            if (trailing != null) trailing() else Chevron()
        }
        if (divider) HorizontalDivider(thickness = ShifaSize.S1, color = ShifaColors.Bg.Canvas)
    }
}

/**
 * `.dr-dtile` / `.dr-doc`: a document with its icon tile (red for a PDF), name and meta ("PDF · 2 pages · 12 Oct").
 * [trailing] replaces the chevron (a download icon button, an action pill).
 */
@Composable
fun ShifaDocumentCard(
    title: String,
    meta: String,
    modifier: Modifier = Modifier,
    pdf: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) = CardChrome(modifier, onClick) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(ShifaSize.S40)
                .background(
                    if (pdf) ShifaColors.Bg.ErrorSubtle else ShifaColors.Bg.SuccessSubtle,
                    ShifaRadius.ControlMd,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(Res.drawable.ic_file_text_22),
                null,
                tint = if (pdf) ShifaColors.Icon.Error else ShifaColors.Icon.Brand,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Stack.Xs)) {
            CardTitle(title, style = MaterialTheme.typography.titleSmall)
            CardBody(meta, ShifaColors.Text.Tertiary)
        }
        when {
            trailing != null -> trailing()
            onClick != null && !LocalCardOnSurface.current -> Chevron()
        }
    }
}

// `.dr-task__tile` tones: a check on teal (default), a pill on blue (medication), activity on amber (exercise).
enum class ShifaTaskType(
    internal val tile: Color,
    internal val fill: Color,
    internal val icon: DrawableResource,
) {
    Default(ShifaColors.Bg.SuccessSubtle, ShifaColors.Interactive.Primary, Res.drawable.ic_check_16),
    Medicine(ShifaColors.Bg.InfoSubtle, ShifaColors.Icon.Info, Res.drawable.ic_pill_16),
    Exercise(ShifaColors.Bg.WarningSubtle, ShifaColors.Icon.Warning, Res.drawable.ic_activity_16),
}

/** `.dr-task`: a remote-care task with its tile, name, meta, optional tag and the done/total progress bar. */
@Composable
fun ShifaTaskCard(
    title: String,
    meta: String,
    modifier: Modifier = Modifier,
    type: ShifaTaskType = ShifaTaskType.Default,
    tag: (@Composable () -> Unit)? = null,
    completed: Int = 0,
    total: Int = 0,
    onClick: (() -> Unit)? = null,
) = CardChrome(modifier, onClick) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(ShifaSize.S40).background(type.tile, ShifaRadius.ControlMd),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(ShifaSize.S26).background(type.fill, ShifaRadius.Full),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(type.icon), null, tint = ShifaColors.Icon.Inverse)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Stack.Xs)) {
            CardTitle(title, style = MaterialTheme.typography.titleSmall)
            CardBody(meta, ShifaColors.Text.Secondary)
        }
        tag?.invoke()
    }
    if (total > 0) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShifaProgressBar(completed.toFloat() / total, Modifier.weight(1f))
            Text("$completed/$total", style = ShifaTextStyles.tableHead(), color = ShifaColors.Text.Primary)
        }
    }
}

@Composable
internal fun CardChrome(
    modifier: Modifier,
    onClick: (() -> Unit)?,
    gap: Dp = ShifaSpacing.Gap.Sm,
    shadow: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val onSurface = LocalCardOnSurface.current
    Column(
        modifier
            .fillMaxWidth()
            .then(if (shadow && !onSurface) Modifier.dropShadow(ShifaRadius.ControlMd, ShifaShadow.Card) else Modifier)
            .clip(ShifaRadius.ControlMd)
            .background(if (onSurface) ShifaColors.Bg.Canvas else ShifaColors.Bg.Surface)
            .then(
                if (onClick != null) {
                    Modifier.pointerHoverIcon(
                        PointerIcon.Hand,
                    ).clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(ShifaSpacing.Inset.Sm),
        verticalArrangement = Arrangement.spacedBy(gap),
        content = content,
    )
}

@Composable
internal fun CardTitle(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = ShifaColors.Text.Primary,
    style: TextStyle = ShifaTextStyles.profileName(),
) = Text(text, modifier.lineBox(style), style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)

@Composable
internal fun CardBody(
    text: String,
    color: Color = ShifaColors.Text.Primary,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
) = Text(text, Modifier.lineBox(style), style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)

@Composable
internal fun MetaRow(
    icon: DrawableResource,
    text: String,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    iconTint: Color = ShifaColors.Icon.Primary,
    color: Color = ShifaColors.Text.Primary,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), null, tint = iconTint)
        Text(
            text,
            Modifier.lineBox(style),
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun Chevron() {
    Box(Modifier.background(ShifaColors.Bg.Canvas, ShifaRadius.Full).padding(ShifaSpacing.Inset.Xs)) {
        Icon(painterResource(Res.drawable.ic_chev_r_16), null, tint = ShifaColors.Icon.Primary)
    }
}
