package shifa.doctor.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.data.model.AppNotification
import shifa.doctor.data.notifications.AccessDecision
import shifa.doctor.data.notifications.NotificationFilter
import shifa.doctor.data.notifications.NotificationKind
import shifa.doctor.data.notifications.NotificationTime
import shifa.doctor.data.notifications.accessDecision
import shifa.doctor.data.notifications.awaitsAccessDecision
import shifa.doctor.data.notifications.notificationDays
import shifa.doctor.data.notifications.notificationTime
import shifa.doctor.data.profile.profileRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.approve
import shifa.doctor.generated.resources.approved
import shifa.doctor.generated.resources.careStatusDeclined
import shifa.doctor.generated.resources.couldNotLoadRetry
import shifa.doctor.generated.resources.declineInvite
import shifa.doctor.generated.resources.ic_bell_22
import shifa.doctor.generated.resources.ic_check_circle_22
import shifa.doctor.generated.resources.ic_refresh_22
import shifa.doctor.generated.resources.ic_sliders_22
import shifa.doctor.generated.resources.markAllAsRead
import shifa.doctor.generated.resources.noNotifications
import shifa.doctor.generated.resources.notificationEmptyBody
import shifa.doctor.generated.resources.notificationEmptyFilter
import shifa.doctor.generated.resources.notificationEmptyFilterHint
import shifa.doctor.generated.resources.notificationFilterAll
import shifa.doctor.generated.resources.notificationFilterAppointments
import shifa.doctor.generated.resources.notificationFilterMessages
import shifa.doctor.generated.resources.notificationFilterTasks
import shifa.doctor.generated.resources.notificationOpenCalendar
import shifa.doctor.generated.resources.notificationReschedule
import shifa.doctor.generated.resources.notificationSettings
import shifa.doctor.generated.resources.notificationUnread
import shifa.doctor.generated.resources.notificationViewAppointment
import shifa.doctor.generated.resources.notificationViewResult
import shifa.doctor.generated.resources.notifications
import shifa.doctor.generated.resources.patient
import shifa.doctor.generated.resources.refresh
import shifa.doctor.generated.resources.retry
import shifa.doctor.generated.resources.timeJustNow
import shifa.doctor.generated.resources.timeMinAgo
import shifa.doctor.generated.resources.timeYesterday
import shifa.doctor.generated.resources.today
import shifa.doctor.generated.resources.yesterday
import shifa.doctor.ui.analytics.shortMonth
import shifa.doctor.ui.emailcode.codeBanner
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaShadow
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.EmptyState
import shifa.doctor.ui.widgets.PressVariant
import shifa.doctor.ui.widgets.ShifaButtonSize
import shifa.doctor.ui.widgets.ShifaChip
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.ui.widgets.ShifaListSkeleton
import shifa.doctor.ui.widgets.ShifaLoadError
import shifa.doctor.ui.widgets.ShifaPageToolbar
import shifa.doctor.ui.widgets.ShifaSecondaryButton
import shifa.doctor.ui.widgets.ShifaSpinner
import shifa.doctor.ui.widgets.ShifaTertiaryButton
import shifa.doctor.ui.widgets.ShifaToolbarAction
import shifa.doctor.ui.widgets.pressable
import shifa.doctor.utils.PracticeClock
import shifa.doctor.utils.tr

class NotificationsActions(
    val refresh: () -> Unit = {},
    val loadMore: () -> Unit = {},
    val select: (NotificationFilter) -> Unit = {},
    val open: (AppNotification) -> Unit = {},
    val markAll: () -> Unit = {},
    val settings: () -> Unit = {},
    // "Open Calendar" on a cancellation: the row read, then the Calendar tab.
    val calendar: (AppNotification) -> Unit = {},
    // NOTIF-04: Approve (true) or Decline (false) on a document-access request.
    val decideAccess: (AppNotification, Boolean) -> Unit = { _, _ -> },
)

/**
 * The inbox (NOTIF-02, flow 07/08, board `DaInbox` "notifications"): a doctor's More / bell destination and clinic
 * staff's third tab. [unread] is the shell's count (the title's "(n)"); [onOpen] takes a row's target.
 */
@Composable
fun NotificationsScreen(
    unread: Int?,
    staff: Boolean,
    onOpen: (NotificationGo) -> Unit,
    onSettings: () -> Unit,
    // "Open Calendar": the appointment's practice day when known, else today (flow §12: Flutter always went to today).
    onCalendar: (LocalDate) -> Unit,
    viewModel: NotificationsViewModel = viewModel { NotificationsViewModel() },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Each time the inbox shows (first time, or back from a target): page 0 (flow §10: on return the list refreshes).
    LaunchedEffect(Unit) { if (state.items == null) viewModel.load() else viewModel.refresh() }
    LaunchedEffect(state.go) {
        state.go?.let {
            viewModel.opened()
            onOpen(it)
        }
    }
    val zone by profileRepository.zone.collectAsStateWithLifecycle(null)
    val clock = remember(zone) { PracticeClock(zone) }
    NotificationsContent(
        state,
        unread,
        staff,
        clock,
        NotificationsActions(
            refresh = viewModel::refresh,
            loadMore = viewModel::loadMore,
            select = viewModel::select,
            open = { viewModel.open(it, staff, clock) },
            markAll = viewModel::markAll,
            settings = onSettings,
            decideAccess = viewModel::decide,
            calendar = {
                viewModel.read(it)
                onCalendar(it.appointmentStartAt?.let { at -> clock.local(at).date } ?: clock.today())
            },
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NotificationsContent(
    state: NotificationsUiState,
    unread: Int?,
    staff: Boolean,
    clock: PracticeClock,
    actions: NotificationsActions,
) = Column(Modifier.fillMaxSize().background(ShifaColors.Bg.Canvas).statusBarsPadding()) {
    Column(
        Modifier.padding(horizontal = ShifaSpacing.Inset.Md).padding(top = ShifaSize.S16, bottom = ShifaSize.S12),
        verticalArrangement = Arrangement.spacedBy(ShifaSize.S8),
    ) {
        // "Notifications (3)" from the unread count (flow §3), "99+" past 99.
        val count = unread?.takeIf { it > 0 }?.let { if (it > MAX_COUNT) "$MAX_COUNT+" else "$it" }
        ShifaPageToolbar(listOfNotNull(tr(Res.string.notifications), count?.let { "($it)" }).joinToString(" ")) {
            ShifaToolbarAction(Res.drawable.ic_refresh_22, tr(Res.string.refresh), actions.refresh)
            // Design review D18: "Mark all as read" is an icon action in the header, not a button on its own row.
            ShifaToolbarAction(
                Res.drawable.ic_check_circle_22,
                tr(Res.string.markAllAsRead),
                actions.markAll,
                // Unread rows may sit on pages not read yet: the count (or not knowing it) also enables it.
                enabled = (state.anyUnread || unread == null || unread > 0) && !state.markingAll,
            )
            // Flutter's gear went to the tab it was on (flow §12); here it opens Settings.
            ShifaToolbarAction(Res.drawable.ic_sliders_22, tr(Res.string.notificationSettings), actions.settings)
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8),
        ) {
            // Clinic staff can't open tasks: no Tasks chip for them (flow §6, NEW).
            NotificationFilter.entries.filter { !staff || it != NotificationFilter.Tasks }.forEach { f ->
                ShifaChip(tr(f.label()), state.filter == f, { actions.select(f) })
            }
        }
    }
    val list = rememberLazyListState()
    // The next page as the end comes into view.
    val nearEnd by remember {
        derivedStateOf {
            val info = list.layoutInfo
            info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(nearEnd, state.items?.size) { if (nearEnd) actions.loadMore() }
    PullToRefreshBox(state.refreshing, actions.refresh, Modifier.weight(1f)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            list,
            // The floating tab bar covers the last 120.
            contentPadding =
                PaddingValues(
                    start = ShifaSpacing.Inset.Md,
                    end = ShifaSpacing.Inset.Md,
                    bottom = ShifaSize.S120,
                ),
            verticalArrangement = Arrangement.spacedBy(ShifaSize.S12),
        ) {
            state.error?.let { e ->
                item {
                    if (state.items.isNullOrEmpty()) {
                        // Nothing to show at all: the kit's load error, with its Retry.
                        ShifaLoadError(tr(e.text), actions.refresh, requestId = e.requestId)
                    } else {
                        // Over the rows read before: the banner, and Retry under it.
                        Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S6)) {
                            codeBanner(e)?.invoke()
                            ShifaLink(tr(Res.string.retry), actions.refresh)
                        }
                    }
                }
            }
            val shown = state.shown
            when {
                state.items == null -> item { ShifaListSkeleton(count = 6) }
                shown.isEmpty() && state.error == null ->
                    item {
                        if (state.filter == NotificationFilter.All) {
                            EmptyState(
                                tr(Res.string.noNotifications),
                                Res.drawable.ic_bell_22,
                                body = tr(Res.string.notificationEmptyBody),
                            )
                        } else {
                            EmptyState(
                                tr(Res.string.notificationEmptyFilter),
                                Res.drawable.ic_bell_22,
                                body = tr(Res.string.notificationEmptyFilterHint),
                            )
                        }
                    }
                else ->
                    notificationDays(shown, clock).forEach { (day, rows) ->
                        item(key = "d$day") { DayLabel(day, clock) }
                        items(rows, key = { "n${it.id}" }) { n ->
                            NotificationCard(n, staff, clock, state.opening == n.id, state.deciding == n.id, actions)
                        }
                    }
            }
            if (state.loadingMore) item { ShifaListSkeleton(count = 2) }
            if (state.moreFailed) item { ShifaLink(tr(Res.string.couldNotLoadRetry), actions.loadMore) }
        }
    }
}

private fun NotificationFilter.label() =
    when (this) {
        NotificationFilter.All -> Res.string.notificationFilterAll
        NotificationFilter.Appointments -> Res.string.notificationFilterAppointments
        NotificationFilter.Tasks -> Res.string.notificationFilterTasks
        NotificationFilter.Messages -> Res.string.notificationFilterMessages
    }

/** "Today", "Yesterday" or "5 Nov" (practice days). */
@Composable
private fun DayLabel(
    day: LocalDate,
    clock: PracticeClock,
) {
    val today = clock.today()
    Text(
        when (day) {
            today -> tr(Res.string.today)
            today.minus(1, kotlinx.datetime.DateTimeUnit.DAY) -> tr(Res.string.yesterday)
            else -> "${day.dayOfMonth} ${tr(shortMonth(day.monthNumber))}"
        },
        Modifier.padding(top = ShifaSize.S4).semantics { heading() },
        style = ShifaTextStyles.tableHead(),
        color = ShifaColors.Text.Secondary,
    )
}

@Composable
internal fun timeLabel(t: NotificationTime): String =
    when (t) {
        NotificationTime.JustNow -> tr(Res.string.timeJustNow)
        is NotificationTime.MinutesAgo -> tr(Res.string.timeMinAgo, t.minutes.toString())
        is NotificationTime.Today -> t.time
        is NotificationTime.Yesterday -> tr(Res.string.timeYesterday, t.time)
        is NotificationTime.Day -> "${t.date.dayOfMonth} ${tr(shortMonth(t.date.monthNumber))}, ${t.time}"
    }

/** The body in the app's language; a booking's date and time in the practice zone ("6 Nov 2026", "10:00"). */
@Composable
internal fun bodyText(
    n: AppNotification,
    clock: PracticeClock,
): String =
    when (val b = n.body(tr(Res.string.patient))) {
        is NotificationBody.Server -> b.text
        is NotificationBody.Template -> {
            val at =
                b.at?.let {
                    val local = clock.local(it)
                    listOf(
                        "${local.dayOfMonth} ${tr(shortMonth(local.monthNumber))} ${local.year}",
                        "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}",
                    )
                }.orEmpty()
            tr(b.text, *(b.args + at).toTypedArray())
        }
    }

/**
 * A row (board `DaInbox`): unread tinted with its colour at 6 % and a blue dot, the icon, label and time, the body (3
 * lines, bold while unread), then the board's actions for a booking or a cancellation (doctors only: staff have no
 * Calendar). While its appointment is looked up the time gives way to a spinner.
 */
@Composable
private fun NotificationCard(
    n: AppNotification,
    staff: Boolean,
    clock: PracticeClock,
    opening: Boolean,
    deciding: Boolean,
    actions: NotificationsActions,
) {
    val look = n.kind.look()
    val (icon, ink) = look.tone.colors()
    // A decided request says so after its type (board: "Document access request · Approved").
    val decided =
        when (n.accessDecision) {
            AccessDecision.Approved -> tr(Res.string.approved)
            AccessDecision.Declined -> tr(Res.string.careStatusDeclined)
            null -> null
        }
    val label = listOfNotNull(tr(look.label), decided).joinToString(" · ")
    val time = timeLabel(notificationTime(n.createdAt, clock))
    val body = bodyText(n, clock)
    val unreadText = tr(Res.string.notificationUnread)
    val bg =
        if (n.read) {
            ShifaColors.Bg.Surface
        } else {
            icon.copy(
                alpha = UNREAD_TINT,
            ).compositeOver(ShifaColors.Bg.Surface)
        }
    Box(
        Modifier
            .fillMaxWidth()
            .dropShadow(ShifaRadius.ControlMd, ShifaShadow.Dropdown)
            .background(bg, ShifaRadius.ControlMd)
            .border(ShifaSize.S1, ShifaColors.Border.Default, ShifaRadius.ControlMd),
    ) {
        Column {
            Column(
                Modifier
                    .fillMaxWidth()
                    .pressable({ actions.open(n) }, PressVariant.Row, enabled = !opening)
                    .clearAndSetSemantics {
                        contentDescription =
                            listOfNotNull(
                                unreadText.takeIf { !n.read },
                                label,
                                time,
                                body,
                            ).joinToString(", ")
                    }.padding(start = ShifaSize.S22, top = ShifaSize.S14, end = ShifaSize.S14, bottom = ShifaSize.S12),
                verticalArrangement = Arrangement.spacedBy(ShifaSize.S6),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(look.icon), null, tint = icon)
                    Text(
                        label,
                        Modifier.weight(1f),
                        style = ShifaTextStyles.buttonCompact(),
                        color = ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (opening) {
                        ShifaSpinner(ShifaColors.Icon.Brand, Modifier.size(ShifaSize.S16))
                    } else {
                        Text(
                            time,
                            style = ShifaTextStyles.metaSmall(),
                            color = ShifaColors.Text.Secondary,
                            maxLines = 1,
                        )
                    }
                }
                Text(
                    body,
                    style =
                        ShifaTextStyles.dialogBody().copy(
                            fontWeight = if (n.read) FontWeight.Medium else FontWeight.Bold,
                        ),
                    color = ShifaColors.Text.Primary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!staff) CardActions(n, deciding, actions)
        }
        if (!n.read) {
            Box(
                Modifier
                    .offset(ShifaSize.S8, ShifaSize.S18)
                    .size(ShifaSize.S8)
                    .background(ShifaColors.Icon.Info, ShifaRadius.Full),
            )
        }
    }
}

/**
 * The board's actions: a booking → View Appointment and Reschedule (its panel); a cancellation → Open Calendar; a
 * completed task → View Result; a document-access request still waiting → Approve and Decline (NOTIF-04).
 */
@Composable
private fun CardActions(
    n: AppNotification,
    deciding: Boolean,
    actions: NotificationsActions,
) {
    if (n.awaitsAccessDecision) {
        Row(
            Modifier.padding(start = ShifaSize.S22, end = ShifaSize.S14, bottom = ShifaSize.S12),
            horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8),
        ) {
            ShifaSecondaryButton(
                tr(Res.string.approve),
                { actions.decideAccess(n, true) },
                size = ShifaButtonSize.Compact,
                enabled = !deciding,
            )
            ShifaTertiaryButton(
                tr(Res.string.declineInvite),
                { actions.decideAccess(n, false) },
                size = ShifaButtonSize.Compact,
                enabled = !deciding,
            )
        }
        return
    }
    // A finished task opens its result (TASK-03).
    if (n.kind == NotificationKind.TaskCompleted && n.taskId != null) {
        Row(Modifier.padding(start = ShifaSize.S22, end = ShifaSize.S14, bottom = ShifaSize.S12)) {
            ShifaSecondaryButton(
                tr(Res.string.notificationViewResult),
                { actions.open(n) },
                size = ShifaButtonSize.Compact,
            )
        }
        return
    }
    if (n.appointmentId == null || (n.kind != NotificationKind.Booked && n.kind != NotificationKind.Cancelled)) return
    FlowRow(
        Modifier.padding(start = ShifaSize.S22, end = ShifaSize.S14, bottom = ShifaSize.S12),
        horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8),
        verticalArrangement = Arrangement.spacedBy(ShifaSize.S8),
    ) {
        if (n.kind == NotificationKind.Booked) {
            ShifaSecondaryButton(
                tr(Res.string.notificationViewAppointment),
                { actions.open(n) },
                size = ShifaButtonSize.Compact,
            )
            ShifaTertiaryButton(
                tr(Res.string.notificationReschedule),
                { actions.open(n) },
                size = ShifaButtonSize.Compact,
            )
        } else {
            ShifaTertiaryButton(
                tr(Res.string.notificationOpenCalendar),
                { actions.calendar(n) },
                size = ShifaButtonSize.Compact,
            )
        }
    }
}

/** The icon's colour and the label's (board `typeOf` and its `labelInk`). */
internal fun NotificationTone.colors(): Pair<Color, Color> =
    when (this) {
        NotificationTone.Brand -> ShifaColors.Icon.Brand to ShifaColors.Text.BrandStrong
        NotificationTone.Info -> ShifaColors.Icon.Info to ShifaColors.Text.Info
        NotificationTone.Error -> ShifaColors.Icon.Error to ShifaColors.Text.Error
        NotificationTone.Warning -> ShifaColors.Icon.Warning to ShifaColors.Text.WarningStrong
        NotificationTone.Neutral -> ShifaColors.Icon.Secondary to ShifaColors.Text.Secondary
    }

private const val MAX_COUNT = 99

// Board `lib.tint(colour, 6)`: an unread card is its colour at 6 % over white.
private const val UNREAD_TINT = 0.06f
