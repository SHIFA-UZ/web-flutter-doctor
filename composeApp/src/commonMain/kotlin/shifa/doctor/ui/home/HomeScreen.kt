package shifa.doctor.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.yearsUntil
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.data.home.heroLook
import shifa.doctor.data.home.stale
import shifa.doctor.data.model.CalendarEntry
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.followUpTasksCount
import shifa.doctor.generated.resources.home
import shifa.doctor.generated.resources.homeLoadFailed
import shifa.doctor.generated.resources.homeTimesInPracticeTime
import shifa.doctor.generated.resources.ic_bell_16
import shifa.doctor.generated.resources.ic_bell_22
import shifa.doctor.generated.resources.ic_calendar_16
import shifa.doctor.generated.resources.ic_clipboard_16
import shifa.doctor.generated.resources.ic_info_16
import shifa.doctor.generated.resources.ic_refresh_22
import shifa.doctor.generated.resources.ic_search_22
import shifa.doctor.generated.resources.metricFollowUp
import shifa.doctor.generated.resources.metricToday
import shifa.doctor.generated.resources.notificationUnread
import shifa.doctor.generated.resources.notifications
import shifa.doctor.generated.resources.profile
import shifa.doctor.generated.resources.refresh
import shifa.doctor.generated.resources.retry
import shifa.doctor.generated.resources.searchPatients
import shifa.doctor.generated.resources.unreadCountLabel
import shifa.doctor.generated.resources.visitsCount
import shifa.doctor.ui.analytics.AnalyticsActions
import shifa.doctor.ui.analytics.AnalyticsSection
import shifa.doctor.ui.analytics.AnalyticsViewModel
import shifa.doctor.ui.calendar.longDate
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.ui.emailcode.codeBanner
import shifa.doctor.ui.intelligence.greetingFor
import shifa.doctor.ui.notifications.NotificationGo
import shifa.doctor.ui.schedule.dayName
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaDimens
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaShadow
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.LocalWideLayout
import shifa.doctor.ui.widgets.ShifaAvatar
import shifa.doctor.ui.widgets.ShifaIconButton
import shifa.doctor.ui.widgets.ShifaIconButtonVariant
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.ui.widgets.ShifaSkeleton
import shifa.doctor.utils.PracticeClock
import shifa.doctor.utils.displayTime
import shifa.doctor.utils.tr
import shifa.doctor.utils.trPlural
import kotlin.time.Duration.Companion.seconds

class HomeActions(
    val openProfile: () -> Unit = {},
    val refresh: () -> Unit = {},
    // The shell's counts read again with the schedule (pull-to-refresh, coming back).
    val refreshCounts: () -> Unit = {},
    val search: () -> Unit = {},
    val notifications: () -> Unit = {},
    val calendar: () -> Unit = {},
    val tasks: () -> Unit = {},
    // HOME-03: the hero's Start and Open chart, a timeline row's panel, the Video Call chip and its booking, the
    // practice-timezone sheet.
    val start: (CalendarEntry.Appointment) -> Unit = {},
    val openChart: (Long) -> Unit = {},
    val openEntry: (CalendarEntry) -> Unit = {},
    val videoCall: () -> Unit = {},
    val bookVideo: (CalendarEntry.FreeSlot) -> Unit = {},
    val setTimezone: () -> Unit = {},
    // ANALYTICS-02: the card's taps (Calendar, Patients, the plans) and its refresh with Home's.
    val patients: () -> Unit = {},
    val seePlans: () -> Unit = {},
    // HOME-04: a notification's or a follow-up's target.
    val openNotification: (NotificationGo) -> Unit = {},
)

/** What Home shows from the shell: the doctor, and the counts the shell keeps (02 §1.3–1.4). */
data class HomeShell(
    val name: String = "",
    val photoUrl: String? = null,
    // Null until read (a skeleton, not a 0); "—" when its read failed with nothing known.
    val unread: Int? = null,
    val unreadFailed: Boolean = false,
    // Active and draft remote-care tasks, null until read; the card shows only with PREMIUM's remote-care tasks.
    val followUps: Int? = null,
    val followUpsFailed: Boolean = false,
    val showFollowUps: Boolean = false,
    // The profile couldn't be read: no practice zone, so no schedule (an error with Retry instead of a skeleton).
    val profileFailed: Boolean = false,
    // The profile is in, with its practice zone (null zone: the doctor hasn't set one).
    val profileLoaded: Boolean = false,
    val zone: String? = null,
    // ANALYTICS-02: PREMIUM's advanced analytics; null until the plan is known (neither charts nor teaser then).
    val advancedAnalytics: Boolean? = null,
)

/**
 * The Home tab (HOME-02, 02 §1.2–1.4, flow 02/01, board `DaHome`; phone, one column): the header (the doctor, Refresh,
 * Search, the bell with an unread dot), the practice-time note when the phone's zone differs, "Today at a glance"
 * (today's visits, unread notifications, follow-up tasks), then the next patient (HOME-03). Pull to refresh.
 */
@Composable
fun HomeScreen(
    shell: HomeShell,
    actions: HomeActions,
    viewModel: HomeViewModel = viewModel { HomeViewModel() },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The schedule waits for the practice zone and reads again when it changes.
    LaunchedEffect(shell.profileLoaded, shell.zone) { if (shell.profileLoaded) viewModel.practiceZone(shell.zone) }
    // Each time Home is shown or the app comes back: the schedule (once a minute) and the shell's counts.
    LifecycleResumeEffect(viewModel) {
        viewModel.focus(actions.refreshCounts)
        onPauseOrDispose {}
    }
    // A ticker: Start opens, "In 12 min" counts down and the timeline moves on without a refresh (Flutter never ticked);
    // a hero that left its slot, or a new practice day, reads the schedule again (gated).
    val clock = remember(shell.zone) { PracticeClock(shell.zone) }
    // ANALYTICS-02: once the practice zone is known (its "today" sets the default range), one per zone.
    val analytics: AnalyticsViewModel? =
        if (shell.profileLoaded) {
            viewModel(
                key = "analytics-${shell.zone}",
            ) { AnalyticsViewModel(clock.today()) }
        } else {
            null
        }
    val now by produceState(clock.nowInstant(), clock) {
        while (true) {
            delay(TICK)
            value = clock.nowInstant()
        }
    }
    LaunchedEffect(now) {
        if (viewModel.state.value.schedule?.stale(
                now,
                clock.local(now).date,
            ) == true
        ) {
            viewModel.focus(actions.refreshCounts)
        }
    }
    // ANALYTICS-02: today's KPIs follow each new read of today's schedule (coming back, a hero leaving its slot, a new
    // day, a refresh), at most once a minute like that read; the first is the card's own open.
    var readsSeen by remember(analytics) { mutableStateOf(state.reads) }
    LaunchedEffect(analytics, state.reads) {
        if (state.reads > readsSeen) analytics?.refreshToday(clock.local(clock.nowInstant()).date)
        readsSeen = state.reads
    }
    // HOME-04: the attention centre and activity feed read with each read of the schedule (gated there), and on pull.
    val attention: HomeAttentionViewModel = viewModel { HomeAttentionViewModel() }
    val inbox by attention.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.reads, shell.showFollowUps) { attention.refresh(withTasks = shell.showFollowUps) }
    LaunchedEffect(inbox.go) {
        inbox.go?.let {
            attention.opened()
            actions.openNotification(it)
        }
    }
    // The Video Call chip found its slot: open it as a video booking, once.
    LaunchedEffect(state.videoSlot) {
        state.videoSlot?.let {
            actions.bookVideo(it)
            viewModel.videoSlotOpened()
        }
    }
    HomeContent(
        state,
        shell,
        HomeActions(
            actions.openProfile,
            refresh = {
                viewModel.refresh(actions.refreshCounts)
                analytics?.refresh()
                attention.refresh(withTasks = shell.showFollowUps, force = true)
            },
            search = actions.search,
            notifications = actions.notifications,
            calendar = actions.calendar,
            tasks = actions.tasks,
            start = actions.start,
            openChart = actions.openChart,
            openEntry = actions.openEntry,
            videoCall = viewModel::findVideoSlot,
            setTimezone = actions.setTimezone,
        ),
        clock,
        now,
        attention = {
            val inboxActions =
                HomeAttentionActions(
                    open = { attention.open(it, staff = false, clock) },
                    openActivity = { attention.open(it, staff = false, clock, read = false) },
                    markRead = attention::markRead,
                    openTask = attention::openTask,
                    all = actions.notifications,
                    retry = { attention.refresh(withTasks = shell.showFollowUps, force = true) },
                )
            AttentionCard(inbox, clock, inboxActions)
            ActivityCard(inbox, clock, inboxActions)
        },
    ) {
        analytics?.let {
            AnalyticsSection(
                clock.today(),
                shell.advancedAnalytics,
                AnalyticsActions(actions.calendar, actions.patients, actions.seePlans),
                it,
            )
        }
    }
}

private val TICK = 30.seconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeContent(
    state: HomeUiState,
    shell: HomeShell,
    actions: HomeActions,
    clock: PracticeClock = PracticeClock(shell.zone),
    now: Instant = clock.nowInstant(),
    // HOME-04: the attention centre and activity feed, after today's schedule (a slot, as analytics).
    attention: @Composable () -> Unit = {},
    // ANALYTICS-02: "Clinic performance", last on Home (a slot, so previews draw Home without its reads).
    analytics: @Composable () -> Unit = {},
) = Column(Modifier.fillMaxSize().background(ShifaColors.Bg.Canvas).statusBarsPadding()) {
    val wide = LocalWideLayout.current
    // Wide: inside the same capped page as the columns (DaHome web).
    Header(
        shell,
        actions,
        if (wide) clock.local(now) else null,
        if (wide) Modifier.align(Alignment.CenterHorizontally).widthIn(max = ShifaDimens.Layout.Page) else Modifier,
    )
    // HOME-03: the next patient, the quick actions, today's timeline (flow 02/02 §3), as sections.
    val zoneMissing = shell.profileLoaded && clock.zoneMissing
    val schedule = state.schedule
    val main =
        buildList<@Composable () -> Unit> {
            if (!clock.zoneMissing && clock.deviceDiffers()) add { TimeZoneNote(clock) }
            add { Metrics(state, shell, actions, unknownDay = zoneMissing) }
            // No profile, no practice zone, no schedule: the same error as a failed schedule read.
            (state.error ?: CodeBanner(Res.string.homeLoadFailed).takeIf { shell.profileFailed })?.let { e ->
                add {
                    Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S6)) {
                        codeBanner(e)?.invoke()
                        ShifaLink(tr(Res.string.retry), actions.refresh)
                    }
                }
            }
            when {
                zoneMissing -> add { EmptyHero(schedule, zoneMissing = true, actions) }
                state.loading && !shell.profileFailed ->
                    add { ShifaSkeleton(Modifier.fillMaxWidth().height(ShifaSize.S180), ShifaRadius.ControlLg) }
                schedule != null -> {
                    val look = heroLook(schedule, now)
                    add {
                        if (look != null) {
                            HeroCard(
                                schedule,
                                look,
                                state.heroBirth?.let { b ->
                                    clock.local(now).date.takeIf { b <= it }?.let { b.yearsUntil(it) }
                                },
                                clock,
                                actions,
                            )
                        } else {
                            EmptyHero(schedule, zoneMissing = false, actions)
                        }
                    }
                }
            }
            add { QuickActions(!state.findingSlot && shell.profileLoaded && !clock.zoneMissing, actions) }
            if (schedule != null && !zoneMissing) add { Timeline(schedule, now, clock, actions) }
        }
    PullToRefreshBox(state.refreshing, actions.refresh, Modifier.weight(1f)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // DaHome web: the main column (basis 520) and the side column (360) side by side when both fit.
            if (wide && maxWidth >= ShifaDimens.Basis.B520 + ShifaDimens.Layout.DialogSm + ShifaSpacing.Inset.Lg * 3) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Row(
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .widthIn(max = ShifaDimens.Layout.Page)
                            .padding(ShifaSpacing.Inset.Lg),
                        horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Inset.Lg),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Inset.Lg)) {
                            main.forEach { it() }
                            analytics()
                        }
                        Column(
                            Modifier.width(ShifaDimens.Layout.DialogSm),
                            verticalArrangement = Arrangement.spacedBy(ShifaSize.S16),
                        ) { attention() }
                    }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    // The floating tab bar covers the last 120 (as on the other tabs); none on wide screens.
                    contentPadding =
                        PaddingValues(
                            start = ShifaSpacing.Inset.Md,
                            top = ShifaSpacing.Inset.Md,
                            end = ShifaSpacing.Inset.Md,
                            bottom = if (wide) ShifaSpacing.Inset.Lg else ShifaSize.S120,
                        ),
                    verticalArrangement = Arrangement.spacedBy(ShifaSize.S20),
                ) {
                    main.forEach { section -> item { section() } }
                    item { Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S16)) { attention() } }
                    item { analytics() }
                }
            }
        }
    }
}

/** The header; [now] set (wide screens, DaHome web) shows the date and a greeting instead of the photo and "Home". */
@Composable
private fun Header(
    shell: HomeShell,
    actions: HomeActions,
    now: LocalDateTime?,
    modifier: Modifier = Modifier,
) = Row(
    // Wide: in line with the columns below (DaHome web's 24 page padding).
    modifier.fillMaxWidth().padding(
        horizontal = if (now != null) ShifaSpacing.Inset.Lg else ShifaSpacing.Inset.Md,
        vertical = ShifaSize.S8,
    ),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(ShifaSize.S12),
) {
    if (now != null) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ShifaSize.S2)) {
            Text(
                "${dayName(now.dayOfWeek.isoDayNumber)}, ${longDate(now.date)} · ${displayTime(now.time)}",
                style = ShifaTextStyles.metaSmall(),
                color = ShifaColors.Text.Secondary,
            )
            Text(
                listOf(tr(greetingFor(now.hour)), shell.name).filter { it.isNotEmpty() }.joinToString(", "),
                style = MaterialTheme.typography.headlineLarge,
                color = ShifaColors.Text.Primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    } else {
        val profile = tr(Res.string.profile)
        ShifaAvatar(
            shell.photoUrl,
            ShifaSize.Touch,
            Modifier
                .clip(ShifaRadius.Full)
                .clickable(role = Role.Button, onClick = actions.openProfile)
                .semantics { contentDescription = profile },
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ShifaSize.S2)) {
            Text(tr(Res.string.home), style = ShifaTextStyles.heroNamePhone(), color = ShifaColors.Text.Primary)
            Text(
                shell.name,
                style = ShifaTextStyles.metaSmall(),
                color = ShifaColors.Text.Secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    ShifaIconButton(
        Res.drawable.ic_refresh_22,
        tr(Res.string.refresh),
        actions.refresh,
        variant = ShifaIconButtonVariant.Surface,
    )
    ShifaIconButton(
        Res.drawable.ic_search_22,
        tr(Res.string.searchPatients),
        actions.search,
        variant = ShifaIconButtonVariant.Surface,
    )
    Box {
        ShifaIconButton(
            Res.drawable.ic_bell_22,
            if ((shell.unread ?: 0) > 0) {
                "${tr(
                    Res.string.notifications,
                )}, ${shell.unread} ${tr(Res.string.unreadCountLabel)}"
            } else {
                tr(Res.string.notifications)
            },
            actions.notifications,
            variant = ShifaIconButtonVariant.Surface,
        )
        if ((shell.unread ?: 0) > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = ShifaSize.S10, end = ShifaSize.S12)
                    .size(ShifaSize.S8)
                    .background(ShifaColors.Interactive.Destructive, ShifaRadius.Full),
            )
        }
    }
}

/** The board's practice-time note: times on Home follow the practice's zone, not the phone's. */
@Composable
private fun TimeZoneNote(clock: PracticeClock) =
    Row(
        Modifier.fillMaxWidth().background(ShifaColors.Bg.InfoSubtle, ShifaRadius.ControlMd).padding(ShifaSize.S12),
        horizontalArrangement = Arrangement.spacedBy(ShifaSize.S10),
    ) {
        Icon(painterResource(Res.drawable.ic_info_16), null, tint = ShifaColors.Text.Info)
        Text(
            tr(Res.string.homeTimesInPracticeTime, clock.zoneId.orEmpty(), clock.deviceZoneId()),
            style = ShifaTextStyles.metaSmall(),
            color = ShifaColors.Text.Primary,
        )
    }

/**
 * "Today at a glance" (board metric row): each card opens its tab. Three columns; one card per row at large text sizes
 * (flow 02/01 §8: the labels would be cut otherwise).
 */
@Composable
private fun Metrics(
    state: HomeUiState,
    shell: HomeShell,
    actions: HomeActions,
    // No practice timezone: "today" can't be known, so its count isn't either.
    unknownDay: Boolean = false,
) {
    val today = state.schedule?.today?.size?.takeUnless { unknownDay }
    val cards =
        listOfNotNull<@Composable (Modifier) -> Unit>(
            { m ->
                Metric(
                    Res.drawable.ic_calendar_16,
                    ShifaColors.Bg.SuccessSubtle,
                    ShifaColors.Icon.Brand,
                    tr(Res.string.metricToday),
                    countText(today, state.error != null || shell.profileFailed || unknownDay),
                    trPlural(Res.plurals.visitsCount, today ?: 0, today ?: 0).substringAfter(' '),
                    actions.calendar,
                    m,
                )
            },
            { m ->
                Metric(
                    Res.drawable.ic_bell_16,
                    ShifaColors.Bg.InfoSubtle,
                    ShifaColors.Icon.Info,
                    // The board's words: "Unread" fits a phone's third of the row, "Notifications" doesn't.
                    tr(Res.string.notificationUnread),
                    countText(shell.unread, shell.unreadFailed),
                    tr(Res.string.notifications).lowercase(),
                    actions.notifications,
                    m,
                )
            },
            if (shell.showFollowUps) {
                { m ->
                    Metric(
                        Res.drawable.ic_clipboard_16,
                        ShifaColors.Bg.WarningSubtle,
                        ShifaColors.Icon.Warning,
                        tr(Res.string.metricFollowUp),
                        countText(shell.followUps, shell.followUpsFailed),
                        trPlural(
                            Res.plurals.followUpTasksCount,
                            shell.followUps ?: 0,
                            shell.followUps ?: 0,
                        ).substringAfter(' '),
                        actions.tasks,
                        m,
                    )
                }
            } else {
                null
            },
        )
    if (LocalDensity.current.fontScale >= LARGE_TEXT) {
        Column(
            verticalArrangement = Arrangement.spacedBy(ShifaSize.S10),
        ) { cards.forEach { it(Modifier.fillMaxWidth()) } }
    } else {
        // Equal heights, whatever each card's lines.
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(ShifaSize.S10),
        ) {
            cards.forEach { it(Modifier.weight(1f).fillMaxHeight()) }
        }
    }
}

private const val LARGE_TEXT = 1.5f

// A count's value: the number; "—" when its read failed and none is known; null (a skeleton) while it loads.
private fun countText(
    n: Int?,
    failed: Boolean,
) = n?.toString() ?: if (failed) "—" else null

@Composable
private fun Metric(
    icon: DrawableResource,
    tint: Color,
    ink: Color,
    label: String,
    // Null while loading (a skeleton bar).
    value: String?,
    sub: String,
    onClick: () -> Unit,
    modifier: Modifier,
) = Column(
    modifier
        .dropShadow(ShifaRadius.ControlLg, ShifaShadow.Card)
        .clip(ShifaRadius.ControlLg)
        .background(ShifaColors.Bg.Surface)
        .clickable(role = Role.Button, onClick = onClick)
        .padding(ShifaSize.S14),
    verticalArrangement = Arrangement.spacedBy(ShifaSize.S8),
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8)) {
        Box(Modifier.size(ShifaSize.S28).background(tint, ShifaRadius.Sm), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), null, tint = ink)
        }
        Text(
            label,
            style = ShifaTextStyles.legend(),
            color = ShifaColors.Text.Secondary,
            // One line, cut with "…" like the board (two lines broke "Follow-up" mid-word on Android).
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (value == null) {
        // The value's line height, so the card doesn't change size when it arrives.
        ShifaSkeleton(Modifier.width(ShifaSize.S40).height(ShifaSize.S28))
    } else {
        Text(value, style = ShifaTextStyles.kpiValuePhone(), color = ShifaColors.Text.Primary)
    }
    Text(
        sub,
        style = ShifaTextStyles.metaSmall(),
        color = ShifaColors.Text.Secondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
