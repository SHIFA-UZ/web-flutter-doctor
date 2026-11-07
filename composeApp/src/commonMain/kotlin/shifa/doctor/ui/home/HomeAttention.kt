package shifa.doctor.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.data.model.AppNotification
import shifa.doctor.data.model.RemoteCareTask
import shifa.doctor.data.notifications.NotificationTime
import shifa.doctor.data.notifications.notificationTime
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.allCaughtUp
import shifa.doctor.generated.resources.allNotifications
import shifa.doctor.generated.resources.attentionRequired
import shifa.doctor.generated.resources.attentionRequiredSubtitle
import shifa.doctor.generated.resources.checkInsOf
import shifa.doctor.generated.resources.followUpTask
import shifa.doctor.generated.resources.ic_check_16
import shifa.doctor.generated.resources.ic_check_circle_20
import shifa.doctor.generated.resources.ic_chev_r_16
import shifa.doctor.generated.resources.ic_tasks_18
import shifa.doctor.generated.resources.markAsRead
import shifa.doctor.generated.resources.moreUnreadInInbox
import shifa.doctor.generated.resources.noRecentActivity
import shifa.doctor.generated.resources.notificationsLoadFailed
import shifa.doctor.generated.resources.patient
import shifa.doctor.generated.resources.patientActivity
import shifa.doctor.generated.resources.patientActivitySubtitle
import shifa.doctor.generated.resources.retry
import shifa.doctor.generated.resources.seeAll
import shifa.doctor.generated.resources.yesterday
import shifa.doctor.ui.analytics.shortMonth
import shifa.doctor.ui.notifications.NotificationTone
import shifa.doctor.ui.notifications.bodyText
import shifa.doctor.ui.notifications.colors
import shifa.doctor.ui.notifications.look
import shifa.doctor.ui.notifications.timeLabel
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaShadow
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.PressVariant
import shifa.doctor.ui.widgets.ShifaIconButton
import shifa.doctor.ui.widgets.ShifaIconButtonVariant
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.ui.widgets.ShifaLoadError
import shifa.doctor.ui.widgets.ShifaSkeleton
import shifa.doctor.ui.widgets.ShifaSpinner
import shifa.doctor.ui.widgets.pressable
import shifa.doctor.utils.PracticeClock
import shifa.doctor.utils.tr

class HomeAttentionActions(
    val open: (AppNotification) -> Unit = {},
    val openActivity: (AppNotification) -> Unit = {},
    val markRead: (AppNotification) -> Unit = {},
    val openTask: (RemoteCareTask) -> Unit = {},
    val all: () -> Unit = {},
    val retry: () -> Unit = {},
)

/** The board's card frame: white, rounded, the card shadow, a heading and its line. */
@Composable
private fun HomeCard(
    title: String,
    subtitle: String,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) = Column(
    Modifier
        .fillMaxWidth()
        .dropShadow(ShifaRadius.ControlLg, ShifaShadow.Card)
        .background(ShifaColors.Bg.Surface, ShifaRadius.ControlLg)
        .padding(ShifaSize.S16),
    verticalArrangement = Arrangement.spacedBy(ShifaSize.S12),
) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ShifaSize.S2)) {
            Text(
                title,
                Modifier.semantics {
                    heading()
                },
                style = ShifaTextStyles.sectionHeading(),
                color = ShifaColors.Text.Primary,
            )
            Text(subtitle, style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Secondary)
        }
        trailing?.invoke()
    }
    content()
}

@Composable
private fun Loading(rows: Int) =
    Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S10)) {
        repeat(rows) { ShifaSkeleton(Modifier.fillMaxWidth().height(ShifaSize.Touch), ShifaRadius.ControlMd) }
    }

@Composable
private fun Failed(retry: () -> Unit) = ShifaLoadError(tr(Res.string.notificationsLoadFailed), retry, compact = true)

/**
 * "Attention required" (HOME-04, 02 §1.5, flow 02/03, board `DaHome`): up to 4 unread notifications (each with Mark as
 * read) then the active tasks, 6 in all; "All caught up!" when nothing waits; "All notifications" opens the inbox.
 */
@Composable
internal fun AttentionCard(
    state: HomeAttentionUiState,
    clock: PracticeClock,
    actions: HomeAttentionActions,
) = HomeCard(tr(Res.string.attentionRequired), tr(Res.string.attentionRequiredSubtitle)) {
    val items = state.attention
    when {
        // The notifications failed: say so, and still show the tasks that came (flow §6: show what loaded).
        state.failed -> {
            Failed(actions.retry)
            items.filterIsInstance<AttentionItem.Task>().forEach { TaskTile(it.task) { actions.openTask(it.task) } }
        }
        state.notifications == null -> Loading(3)
        items.isEmpty() && state.incomplete ->
            Text(
                tr(Res.string.moreUnreadInInbox),
                style = ShifaTextStyles.dialogBody(),
                color = ShifaColors.Text.Secondary,
            )
        items.isEmpty() ->
            Column(
                Modifier.fillMaxWidth().padding(vertical = ShifaSize.S24),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ShifaSize.S8),
            ) {
                Box(
                    Modifier.size(ShifaSize.S40).background(ShifaColors.Bg.SuccessSubtle, ShifaRadius.Full),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(Res.drawable.ic_check_circle_20), null, tint = ShifaColors.Text.BrandStrong)
                }
                Text(
                    tr(Res.string.allCaughtUp),
                    style = ShifaTextStyles.contextName(),
                    color = ShifaColors.Text.Primary,
                )
            }
        else ->
            Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S8)) {
                items.forEach { item ->
                    when (item) {
                        is AttentionItem.Unread ->
                            UnreadTile(
                                item.notification,
                                clock,
                                state.opening == item.notification.id,
                                actions,
                            )
                        is AttentionItem.Task -> TaskTile(item.task) { actions.openTask(item.task) }
                    }
                }
            }
    }
    ShifaLink(tr(Res.string.allNotifications), actions.all)
}

@Composable
private fun Tile(
    icon: org.jetbrains.compose.resources.DrawableResource,
    tint: androidx.compose.ui.graphics.Color,
    ink: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String,
    meta: String,
    busy: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) = Row(
    modifier
        .pressable(onClick, PressVariant.Tile, enabled = !busy)
        .clearAndSetSemantics {
            contentDescription = listOf(title, subtitle, meta).filter(String::isNotEmpty).joinToString(", ")
        }
        .padding(ShifaSize.S12),
    horizontalArrangement = Arrangement.spacedBy(ShifaSize.S12),
    verticalAlignment = Alignment.CenterVertically,
) {
    Box(Modifier.size(ShifaSize.S36).background(tint, ShifaRadius.ControlMd), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), null, tint = ink)
    }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ShifaSize.S2)) {
        Text(
            title,
            style = ShifaTextStyles.buttonCompact(),
            color = ShifaColors.Text.Primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            subtitle,
            style = ShifaTextStyles.metaSmall(),
            color = ShifaColors.Text.Secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(meta, style = ShifaTextStyles.legend(), color = ShifaColors.Text.Tertiary)
    }
    if (busy) {
        ShifaSpinner(ShifaColors.Icon.Brand, Modifier.size(ShifaSize.S16))
    } else {
        Icon(painterResource(Res.drawable.ic_chev_r_16), null, tint = ShifaColors.Text.Tertiary)
    }
}

/** A tone's tile background (board `tInfo`): its colour's subtle shade. */
private fun NotificationTone.tint() =
    when (this) {
        NotificationTone.Brand -> ShifaColors.Bg.SuccessSubtle
        NotificationTone.Info -> ShifaColors.Bg.InfoSubtle
        NotificationTone.Error -> ShifaColors.Bg.ErrorSubtle
        NotificationTone.Warning -> ShifaColors.Bg.WarningSubtle
        NotificationTone.Neutral -> ShifaColors.Bg.Canvas
    }

@Composable
private fun UnreadTile(
    n: AppNotification,
    clock: PracticeClock,
    busy: Boolean,
    actions: HomeAttentionActions,
) = Row(horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8), verticalAlignment = Alignment.CenterVertically) {
    val look = n.kind.look()
    Tile(
        look.icon,
        look.tone.tint(),
        look.tone.colors().first,
        tr(look.label),
        bodyText(n, clock),
        timeLabel(notificationTime(n.createdAt, clock)),
        busy,
        Modifier.weight(1f),
    ) { actions.open(n) }
    ShifaIconButton(Res.drawable.ic_check_16, tr(Res.string.markAsRead), {
        actions.markRead(n)
    }, variant = ShifaIconButtonVariant.Secondary)
}

/** A follow-up: its name, the patient, "3 of 6 check-ins" (board). */
@Composable
private fun TaskTile(
    t: RemoteCareTask,
    onClick: () -> Unit,
) {
    val p = t.progress
    Tile(
        Res.drawable.ic_tasks_18,
        ShifaColors.Bg.WarningSubtle,
        ShifaColors.Text.Warning,
        t.name,
        "${t.patientName.ifBlank { tr(Res.string.patient) }} · ${tr(Res.string.followUpTask)}",
        p?.let { tr(Res.string.checkInsOf, it.completed.toString(), it.total.toString()) }.orEmpty(),
        busy = false,
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    )
}

/**
 * "Patient activity" (02 §1.6, board `DaHome`): the 8 newest notifications, read or not, with the time, the type's
 * dot, the label and 2 lines of body; a row opens its target (the board; Flutter's rows didn't open) without reading
 * it. "See all" opens the inbox.
 */
@Composable
internal fun ActivityCard(
    state: HomeAttentionUiState,
    clock: PracticeClock,
    actions: HomeAttentionActions,
) = HomeCard(
    tr(Res.string.patientActivity),
    tr(Res.string.patientActivitySubtitle),
    trailing = { ShifaLink(tr(Res.string.seeAll), actions.all) },
) {
    val rows = state.activity
    when {
        state.failed -> Failed(actions.retry)
        state.notifications == null -> Loading(4)
        rows.isEmpty() ->
            Text(
                tr(Res.string.noRecentActivity),
                Modifier.padding(vertical = ShifaSize.S24),
                style = ShifaTextStyles.dialogBody(),
                color = ShifaColors.Text.Secondary,
            )
        else ->
            Column {
                rows.forEachIndexed { i, n ->
                    if (i > 0) HorizontalDivider(color = ShifaColors.Border.Default)
                    ActivityRow(n, clock, state.opening == n.id) { actions.openActivity(n) }
                }
            }
    }
}

@Composable
private fun ActivityRow(
    n: AppNotification,
    clock: PracticeClock,
    busy: Boolean,
    onClick: () -> Unit,
) {
    val look = n.kind.look()
    // The 64 column (board): older rows show the day only, yesterday's the word (the board's "Yest.").
    val time =
        when (val at = notificationTime(n.createdAt, clock)) {
            is NotificationTime.Day -> "${at.date.dayOfMonth} ${tr(shortMonth(at.date.monthNumber))}"
            is NotificationTime.Yesterday -> tr(Res.string.yesterday)
            else -> timeLabel(at)
        }
    val title = tr(look.label)
    val body = bodyText(n, clock)
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(onClick, PressVariant.Row, enabled = !busy)
            .clearAndSetSemantics { contentDescription = "$time, $title, $body" }
            .padding(horizontal = ShifaSize.S4, vertical = ShifaSize.S12),
        horizontalArrangement = Arrangement.spacedBy(ShifaSize.S10),
    ) {
        Text(
            time,
            Modifier.width(ShifaSize.S64),
            style = ShifaTextStyles.legend(),
            color = ShifaColors.Text.Tertiary,
            textAlign = TextAlign.End,
        )
        Box(
            Modifier.padding(
                top = ShifaSize.S4,
            ).size(ShifaSize.S8).background(look.tone.colors().first, ShifaRadius.Full),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ShifaSize.S2)) {
            Text(title, style = ShifaTextStyles.buttonCompact(), color = ShifaColors.Text.Primary)
            Text(
                body,
                style = ShifaTextStyles.metaSmall(),
                color = ShifaColors.Text.Secondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (busy) ShifaSpinner(ShifaColors.Icon.Brand, Modifier.size(ShifaSize.S16))
    }
}
