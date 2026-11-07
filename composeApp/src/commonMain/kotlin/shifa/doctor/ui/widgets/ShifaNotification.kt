package shifa.doctor.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.ic_calendar_16
import shifa.doctor.generated.resources.ic_check_circle_16
import shifa.doctor.generated.resources.ic_file_16
import shifa.doctor.generated.resources.ic_info_16
import shifa.doctor.generated.resources.ic_sparkles_16
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaDimens
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.theme.lineBox

enum class ShifaNotificationType(
    internal val icon: DrawableResource,
    internal val tint: Color,
) {
    Task(Res.drawable.ic_check_circle_16, ShifaColors.Icon.Info),
    Document(Res.drawable.ic_file_16, ShifaColors.Icon.Warning),
    Appointment(Res.drawable.ic_calendar_16, ShifaColors.Icon.Brand),
    Chat(Res.drawable.ic_sparkles_16, ShifaColors.Icon.Brand),
    Feedback(Res.drawable.ic_info_16, ShifaColors.Icon.Warning),
}

/**
 * Figma notification, every type but message. Unread ([unreadCount] > 0): a bold brand [time] and the count's
 * [ShifaNotificationBubble]; an unread chat sits on canvas. Feedback has no [time] or bubble in Figma.
 */
@Composable
fun ShifaNotification(
    title: String,
    body: String,
    type: ShifaNotificationType,
    modifier: Modifier = Modifier,
    time: String? = null,
    unreadCount: Int = 0,
    onClick: (() -> Unit)? = null,
) {
    val feedback = type == ShifaNotificationType.Feedback
    val unread = unreadCount > 0 && !feedback
    val bg =
        when {
            feedback -> ShifaColors.Bg.WarningSubtle
            unread && type == ShifaNotificationType.Chat -> ShifaColors.Bg.Canvas
            else -> ShifaColors.Bg.Surface
        }
    Column(
        modifier
            .notificationChrome(bg, onClick)
            .then(
                if (feedback) {
                    Modifier.border(
                        ShifaSize.S2,
                        ShifaColors.Border.Warning,
                        ShifaRadius.ControlMd,
                    )
                } else {
                    Modifier
                },
            )
            .padding(ShifaSpacing.Inset.Sm),
        verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm),
    ) {
        TitleBar(time, if (unread) unreadCount else 0) {
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(type.icon), null, tint = type.tint)
                TitleText(title)
            }
        }
        val style = ShifaTextStyles.notificationBody()
        Text(
            body,
            Modifier.lineBox(style),
            style = style,
            color = ShifaColors.Text.Primary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Figma notification, type = message. [unreadCount] > 0 is unread: a brand time and the count's bubble. */
@Composable
fun ShifaMessageNotification(
    sender: String,
    body: String,
    time: String?,
    modifier: Modifier = Modifier,
    avatarUrl: String? = null,
    unreadCount: Int = 0,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier.notificationChrome(ShifaColors.Bg.Surface, onClick).padding(ShifaSpacing.Inset.Sm),
        horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShifaAvatar(avatarUrl, ShifaDimens.NotificationAvatar, placeholder = ShifaColors.Bg.Disabled)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm)) {
            TitleBar(time, unreadCount) { TitleText(sender, Modifier.weight(1f)) }
            val style = ShifaTextStyles.notificationBody()
            Text(
                body,
                Modifier.fillMaxWidth().height(ShifaSize.S26),
                style = style,
                color = ShifaColors.Text.Primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Figma notification-bubble: an unread count, white on brand, in a 14 text box with 4 padding. Over 99 shows 99+. */
@Composable
fun ShifaNotificationBubble(
    count: Int,
    modifier: Modifier = Modifier,
) {
    val style = MaterialTheme.typography.labelSmall
    Box(
        modifier
            .background(ShifaColors.Icon.Brand, ShifaRadius.Full)
            .padding(ShifaSpacing.Stack.Xs)
            .defaultMinSize(ShifaSize.S14, ShifaSize.S14),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (count > 99) "99+" else "$count",
            Modifier.lineBox(style),
            style = style,
            color = ShifaColors.Text.Inverse,
            textAlign = TextAlign.Center,
        )
    }
}

private fun Modifier.notificationChrome(
    bg: Color,
    onClick: (() -> Unit)?,
) = fillMaxWidth()
    .clip(ShifaRadius.ControlMd)
    .background(bg)
    .then(if (onClick != null) Modifier.pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick) else Modifier)

/** Figma title-bar: 22 high (the bubble's height), the [title], then the time and, when [unreadCount] > 0, the bubble. */
@Composable
private fun TitleBar(
    time: String?,
    unreadCount: Int,
    title: @Composable RowScope.() -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(ShifaSize.S22),
        horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        title()
        val unread = unreadCount > 0
        time?.let {
            val style = MaterialTheme.typography.labelSmall
            Text(
                it,
                Modifier.lineBox(style),
                style = if (unread) style else style.copy(fontWeight = FontWeight.Medium),
                color = if (unread) ShifaColors.Text.Brand else ShifaColors.Text.Tertiary,
                maxLines = 1,
            )
        }
        if (unread) ShifaNotificationBubble(unreadCount)
    }
}

@Composable
private fun TitleText(
    text: String,
    modifier: Modifier = Modifier,
) {
    val style = ShifaTextStyles.profileName()
    Text(
        text,
        modifier.lineBox(style),
        style = style,
        color = ShifaColors.Text.Primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
