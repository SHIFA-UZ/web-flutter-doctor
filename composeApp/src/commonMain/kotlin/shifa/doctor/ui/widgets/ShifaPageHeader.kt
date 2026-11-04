package shifa.doctor.ui.widgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.back
import shifa.doctor.generated.resources.ic_arrow_l_16
import shifa.doctor.generated.resources.ic_bell_16
import shifa.doctor.generated.resources.ic_more_v_16
import shifa.doctor.generated.resources.ic_tasks_16
import shifa.doctor.generated.resources.more
import shifa.doctor.generated.resources.notifications
import shifa.doctor.generated.resources.profile
import shifa.doctor.generated.resources.select
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaDimens
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.utils.tr

/** Figma page-header, page = main: the 32 title on every tab. */
@Composable
fun ShifaMainHeader(
    title: String,
    modifier: Modifier = Modifier,
    avatarUrl: String? = null,
    onNotifications: (() -> Unit)? = null,
    onAvatar: (() -> Unit)? = null,
    notificationCount: Int = 0,
) {
    Row(
        modifier.fillMaxWidth().height(ShifaSize.S100).padding(horizontal = ShifaSpacing.Inset.Md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderTitle(title, MaterialTheme.typography.headlineLarge, Modifier.weight(1f, fill = false))
        Row(
            horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            onNotifications?.let {
                // Figma's header has no badge; the unread count (N2) is the notification-bubble.
                BadgedBox(badge = { if (notificationCount > 0) ShifaNotificationBubble(notificationCount) }) {
                    ShifaActionButton(
                        it,
                        icon = Res.drawable.ic_bell_16,
                        contentDescription = tr(Res.string.notifications),
                        variant = ShifaActionVariant.Secondary,
                    )
                }
            }
            ShifaAvatar(
                avatarUrl,
                ShifaDimens.PageHeaderAvatar,
                Modifier.clip(
                    ShifaRadius.Full,
                ).then(
                    if (onAvatar != null) {
                        val label = tr(Res.string.profile)
                        Modifier.clickable(
                            role = Role.Button,
                            onClick = onAvatar,
                        ).semantics { contentDescription = label }
                    } else {
                        Modifier
                    },
                ),
                placeholder = ShifaColors.Icon.Primary,
            )
        }
    }
}

/** Figma page-header, page = sub. Each action shows only when its callback is set; [avatar] puts a chat's photo before the title. */
@Composable
fun ShifaSubHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onCheck: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
    avatar: Boolean = false,
    avatarUrl: String? = null,
) {
    Row(
        modifier.fillMaxWidth().height(ShifaSize.S64).padding(horizontal = ShifaSpacing.Inset.Md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        onBack?.let {
            ShifaActionButton(
                it,
                icon = Res.drawable.ic_arrow_l_16,
                contentDescription = tr(Res.string.back),
                variant = ShifaActionVariant.Secondary,
            )
        }
        if (avatar) {
            Row(
                Modifier.weight(1f, fill = false),
                horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShifaAvatar(avatarUrl, ShifaDimens.SubHeaderAvatar, placeholder = ShifaColors.Icon.Primary)
                HeaderTitle(title, MaterialTheme.typography.headlineSmall, Modifier.weight(1f, fill = false))
            }
        } else {
            HeaderTitle(title, MaterialTheme.typography.headlineSmall, Modifier.weight(1f, fill = false))
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            onCheck?.let {
                ShifaActionButton(
                    it,
                    icon = Res.drawable.ic_tasks_16,
                    contentDescription = tr(Res.string.select),
                    variant = ShifaActionVariant.Secondary,
                )
            }
            onMore?.let {
                // Same as a secondary action-button, without the background.
                Box(
                    Modifier
                        .clip(ShifaRadius.Full)
                        .clickable(role = Role.Button, onClick = it)
                        .padding(ShifaSpacing.Inset.Xs),
                ) {
                    Icon(
                        painterResource(Res.drawable.ic_more_v_16),
                        tr(Res.string.more),
                        tint = ShifaColors.Icon.Primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderTitle(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
) = Text(
    text,
    modifier,
    style = style,
    color = ShifaColors.Text.Primary,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
)
