package shifa.doctor.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.calendar.CalendarCache
import shifa.doctor.data.calendar.CalendarRepository
import shifa.doctor.data.calendar.DayKey
import shifa.doctor.data.calendar.calendarRepository
import shifa.doctor.data.model.CalendarEntry
import shifa.doctor.data.model.DayOccupancy
import shifa.doctor.data.profile.ProfileRepository
import shifa.doctor.data.profile.profileRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.allDay
import shifa.doctor.generated.resources.apply
import shifa.doctor.generated.resources.appointments
import shifa.doctor.generated.resources.blocked
import shifa.doctor.generated.resources.calendar
import shifa.doctor.generated.resources.calendarTimezoneMismatchHint
import shifa.doctor.generated.resources.couldNotLoadThisDay
import shifa.doctor.generated.resources.durationHours
import shifa.doctor.generated.resources.durationHoursMinutes
import shifa.doctor.generated.resources.durationMinutes
import shifa.doctor.generated.resources.filter
import shifa.doctor.generated.resources.freeSlot
import shifa.doctor.generated.resources.freeSlots
import shifa.doctor.generated.resources.hiddenByFilters
import shifa.doctor.generated.resources.hideDetails
import shifa.doctor.generated.resources.ic_alert_circle_16
import shifa.doctor.generated.resources.ic_block_16
import shifa.doctor.generated.resources.ic_refresh_22
import shifa.doctor.generated.resources.ic_sliders_22
import shifa.doctor.generated.resources.noItemsForThisDay
import shifa.doctor.generated.resources.patient
import shifa.doctor.generated.resources.paymentFailed
import shifa.doctor.generated.resources.paymentNotRequired
import shifa.doctor.generated.resources.paymentPaid
import shifa.doctor.generated.resources.paymentPending
import shifa.doctor.generated.resources.refresh
import shifa.doctor.generated.resources.reset
import shifa.doctor.generated.resources.retry
import shifa.doctor.generated.resources.showDetails
import shifa.doctor.generated.resources.statusCancelled
import shifa.doctor.generated.resources.statusCompleted
import shifa.doctor.generated.resources.statusConfirmed
import shifa.doctor.generated.resources.statusInProgress
import shifa.doctor.generated.resources.statusNoShow
import shifa.doctor.generated.resources.statusRequested
import shifa.doctor.generated.resources.tzMismatchZones
import shifa.doctor.generated.resources.videoCall
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.ui.emailcode.codeBanner
import shifa.doctor.ui.profile.ProfileChip
import shifa.doctor.ui.profile.ProfileSheet
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaShadow
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.ShifaAvatar
import shifa.doctor.ui.widgets.ShifaBanner
import shifa.doctor.ui.widgets.ShifaBannerType
import shifa.doctor.ui.widgets.ShifaCheckboxRow
import shifa.doctor.ui.widgets.ShifaIconButton
import shifa.doctor.ui.widgets.ShifaIconButtonVariant
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.ui.widgets.ShifaListSkeleton
import shifa.doctor.ui.widgets.ShifaPrimaryButton
import shifa.doctor.ui.widgets.ShifaTray
import shifa.doctor.utils.PracticeClock
import shifa.doctor.utils.TimeZones
import shifa.doctor.utils.displayTime
import shifa.doctor.utils.tr

/** Which row kinds the agenda shows (the Filter sheet). */
data class AgendaFilter(
    val appointments: Boolean = true,
    val free: Boolean = true,
    val blocked: Boolean = true,
) {
    val on: Boolean get() = !(appointments && free && blocked)

    fun shows(entry: CalendarEntry) =
        when (entry) {
            is CalendarEntry.Appointment -> appointments
            is CalendarEntry.FreeSlot -> free
            is CalendarEntry.Blocked -> blocked
        }
}

data class CalendarUiState(
    val clock: PracticeClock = PracticeClock(null),
    val selected: LocalDate = clock.today(),
    val month: LocalDate = selected,
    val cache: CalendarCache = CalendarCache(),
    val loading: Boolean = false,
    val error: CodeBanner? = null,
    val filter: AgendaFilter = AgendaFilter(),
    // The Filter sheet edits a draft; Apply commits it, dismissing drops it (flow 04/01 §7). Null when closed.
    val filterDraft: AgendaFilter? = null,
    val details: Boolean = false,
    val mismatchDismissed: Boolean = false,
) {
    val today: LocalDate get() = clock.today()

    /** The selected day's rows, unfiltered (null until read). */
    val day: List<CalendarEntry>? get() = cache.days[DayKey(selected)]

    val rows: List<CalendarEntry> get() = day.orEmpty().filter(filter::shows)

    /** Flow 04/03: phone and practice offsets differ now, and the doctor hasn't dismissed it this session. */
    val showMismatch: Boolean get() = !mismatchDismissed && !clock.zoneMissing && clock.deviceDiffers()

    fun occupancy(date: LocalDate): DayOccupancy? =
        cache.days[DayKey(date)]?.let { entries ->
            DayOccupancy.of(entries) { clock.local(it).time == LocalTime(0, 0) }
        }
}

/**
 * CAL-4 (flow 04/01, DaCalendar day view, phone): the month picker, the selected day's agenda (free slots,
 * appointments, blocks) with filters and the compact / details rows, and the timezone-mismatch banner (04/03). Days
 * come from the shared [CalendarRepository]; the screen observes its cache, so a change made anywhere shows here.
 * The selected day is read on open, on every return to the app (60 s throttle) and on pull-to-refresh (forced).
 */
class CalendarViewModel(
    private val calendar: CalendarRepository = calendarRepository,
    private val profile: ProfileRepository = profileRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(CalendarUiState(clock = clockOf(profile)))
    val state: StateFlow<CalendarUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // The cache and the practice zone (the profile may change it while the calendar is open).
            combine(calendar.state, profile.me) { cache, me -> cache to me?.profile?.timeZone }.collect {
                    (cache, zone) ->
                val old = _state.value.clock
                if (zone == old.zoneId || (zone == null && old.zoneMissing)) {
                    return@collect _state.update {
                        it.copy(
                            cache = cache,
                        )
                    }
                }
                // The practice zone arrived or changed: "today" moves with it (unless the doctor picked another day),
                // and days grouped under the old zone are read again.
                val clock = PracticeClock(zone)
                _state.update { s ->
                    val today = if (s.selected == old.today()) clock.today() else s.selected
                    s.copy(
                        cache = cache,
                        clock = clock,
                        selected = today,
                        month = if (s.month == s.selected) today else s.month,
                    )
                }
                calendar.clear()
                load()
            }
        }
        load()
    }

    fun select(date: LocalDate) {
        _state.update { it.copy(selected = date, month = date, error = null) }
        load()
    }

    /** Pages the month; the selected day stays (flow 04/01 §7). */
    fun month(delta: Int) {
        _state.update { it.copy(month = it.month.plus(DatePeriod(months = delta))) }
        prefetch()
    }

    fun ui(change: (CalendarUiState) -> CalendarUiState) = _state.update(change)

    /** [force] for pull-to-refresh; a resume reads only when the day is older than the throttle. */
    fun load(force: Boolean = false) {
        val date = _state.value.selected
        _state.update { it.copy(loading = it.day == null, error = null) }
        viewModelScope.launch {
            val result = calendar.day(DayKey(date), force)
            _state.update { s ->
                if (s.selected != date) {
                    s
                } else {
                    s.copy(
                        loading = false,
                        error =
                            result.exceptionOrNull()?.let {
                                CodeBanner(it.toUserMessage(Res.string.couldNotLoadThisDay), requestId = it.requestId)
                            },
                    )
                }
            }
            prefetch()
        }
    }

    private fun prefetch() {
        val month = _state.value.month
        viewModelScope.launch { calendar.month(monthCells(month).filterNotNull()) }
    }

    private companion object {
        fun clockOf(profile: ProfileRepository) = PracticeClock(profile.me.value?.profile?.timeZone)
    }
}

@Composable
fun CalendarScreen(
    onOpen: (CalendarEntry) -> Unit,
    viewModel: CalendarViewModel = viewModel { CalendarViewModel() },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Back in the app (or the tab): the day again, within the throttle; the mismatch is re-evaluated by recomposition.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load() }
    CalendarContent(
        state,
        CalendarActions(
            select = viewModel::select,
            month = viewModel::month,
            retry = { viewModel.load(force = true) },
            open = onOpen,
            ui = viewModel::ui,
        ),
    )
}

class CalendarActions(
    val select: (LocalDate) -> Unit = {},
    val month: (Int) -> Unit = {},
    val retry: () -> Unit = {},
    val open: (CalendarEntry) -> Unit = {},
    val ui: ((CalendarUiState) -> CalendarUiState) -> Unit = {},
)

@Composable
fun CalendarContent(
    state: CalendarUiState,
    actions: CalendarActions,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(ShifaColors.Bg.Canvas)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            // Clear of the floating tab bar, like the Profile root.
            .padding(
                start = ShifaSpacing.Inset.Md,
                end = ShifaSpacing.Inset.Md,
                top = ShifaSpacing.Inset.Md,
                bottom = ShifaSize.S120,
            ),
        verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Stack.Md),
    ) {
        if (state.showMismatch) MismatchBanner(state.clock) { actions.ui { it.copy(mismatchDismissed = true) } }
        Header(state, actions)
        MonthPicker(state.month, state.selected, state.today, state::occupancy, actions.select, actions.month)
        codeBanner(state.error) { actions.ui { it.copy(error = null) } }?.invoke()
        state.error?.let { ShifaLink(tr(Res.string.retry), actions.retry) }
        Agenda(state, actions)
    }
    state.filterDraft?.let { FilterSheet(it, actions) }
}

@Composable
private fun Header(
    state: CalendarUiState,
    actions: CalendarActions,
) = Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Column(Modifier.weight(1f)) {
        Text(tr(Res.string.calendar), style = ShifaTextStyles.trayTitle(), color = ShifaColors.Text.Primary)
        Text(longDate(state.selected), style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Secondary)
    }
    ShifaIconButton(
        Res.drawable.ic_refresh_22,
        tr(Res.string.refresh),
        actions.retry,
        variant = ShifaIconButtonVariant.Ghost,
    )
    ShifaIconButton(
        Res.drawable.ic_sliders_22,
        tr(Res.string.filter),
        { actions.ui { it.copy(filterDraft = it.filter) } },
        variant = if (state.filter.on) ShifaIconButtonVariant.Primary else ShifaIconButtonVariant.Secondary,
    )
}

/** "4 November 2026", the month in the app's language. */
@Composable
private fun longDate(date: LocalDate) = "${date.dayOfMonth} ${monthTitle(date)}"

@Composable
private fun MismatchBanner(
    clock: PracticeClock,
    onDismiss: () -> Unit,
) {
    fun label(zone: String) = TimeZones.utcLabel(zone)?.let { "$zone ($it)" } ?: zone
    // Both zones on their own line (flow 04/03 §4 target); the banner's detail line is for request ids only.
    ShifaBanner(
        tr(Res.string.calendarTimezoneMismatchHint, clock.zoneId.orEmpty()) + "\n" +
            tr(Res.string.tzMismatchZones, label(clock.zoneId.orEmpty()), label(clock.deviceZoneId())),
        Res.drawable.ic_alert_circle_16,
        ShifaBannerType.Warning,
        onClose = onDismiss,
    )
}

@Composable
private fun Agenda(
    state: CalendarUiState,
    actions: CalendarActions,
) = Column(verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md)) {
    val day = state.day
    when {
        day == null && state.loading -> ShifaListSkeleton(count = 4)
        day == null -> Unit
        else -> {
            if (day.isNotEmpty()) {
                ShifaLink(
                    tr(if (state.details) Res.string.hideDetails else Res.string.showDetails),
                    { actions.ui { it.copy(details = !it.details) } },
                    Modifier.align(Alignment.End),
                )
            }
            state.rows.forEach { AgendaRow(it, state.clock, state.details) { actions.open(it) } }
            if (state.rows.isEmpty()) {
                // Empty because of the filters says so and offers a reset (flow 04/01 §6).
                val hidden = day.isNotEmpty()
                Row(
                    Modifier.fillMaxWidth().background(
                        ShifaColors.Bg.Surface,
                        ShifaRadius.ControlMd,
                    ).padding(ShifaSpacing.Inset.Lg),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        tr(if (hidden) Res.string.hiddenByFilters else Res.string.noItemsForThisDay),
                        style = ShifaTextStyles.dialogBody(),
                        color = ShifaColors.Text.Secondary,
                    )
                    if (hidden) {
                        Text(" · ", style = ShifaTextStyles.dialogBody(), color = ShifaColors.Text.Secondary)
                        ShifaLink(tr(Res.string.reset), { actions.ui { it.copy(filter = AgendaFilter()) } })
                    }
                }
            }
        }
    }
}

/**
 * One agenda card (flow 04/02, DaCalendar `cl-row`): free slot (brand ring with +), appointment (photo or the brand
 * fallback, name, time; cancelled dimmed and struck through), block (error tint, reason or "Blocked"). Compact by
 * default; [details] adds the location, status and payment chips and the note (the board's "Show details").
 */
@Composable
internal fun AgendaRow(
    entry: CalendarEntry,
    clock: PracticeClock,
    details: Boolean,
    onClick: () -> Unit,
) {
    val start = clock.local(entry.start)
    val end = clock.local(entry.end)
    val allDay = entry is CalendarEntry.Blocked && start.time == LocalTime(0, 0) && end.date > start.date
    val time = if (allDay) tr(Res.string.allDay) else "${displayTime(start.time)} – ${displayTime(end.time)}"
    val blocked = entry is CalendarEntry.Blocked
    val cancelled = (entry as? CalendarEntry.Appointment)?.cancelled == true
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = ShifaSize.S64)
            .dropShadow(ShifaRadius.ControlMd, ShifaShadow.Dropdown)
            .background(if (blocked) ShifaColors.Bg.ErrorSubtle else ShifaColors.Bg.Surface, ShifaRadius.ControlMd)
            .let { if (blocked) it.border(ShifaSize.S1, ShifaColors.Border.Error, ShifaRadius.ControlMd) else it }
            .clickable(role = Role.Button, onClick = onClick)
            // A cancelled appointment stays visible but dimmed (DaCalendar: opacity 0.55).
            .alpha(if (cancelled) CANCELLED_ALPHA else 1f)
            .padding(horizontal = ShifaSize.S14, vertical = ShifaSize.S10),
        horizontalArrangement = Arrangement.spacedBy(ShifaSize.S12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Lead(entry)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ShifaSize.S4)) {
            Text(
                title(entry),
                style = ShifaTextStyles.panelTitle(),
                color = if (blocked) ShifaColors.Text.Error else ShifaColors.Text.Primary,
                textDecoration = if (cancelled) TextDecoration.LineThrough else null,
                maxLines = if (details) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
            val place = place(entry)
            // Compact: one line with the place, the status and (video) the payment, like the board's summary.
            val summary =
                listOfNotNull(
                    place,
                    (entry as? CalendarEntry.Appointment)?.let { status(it.status)?.first },
                    (entry as? CalendarEntry.Appointment)?.takeIf { it.isVideo }?.let {
                        payment(
                            it.paymentStatus,
                        )?.first
                    },
                ).joinToString(" · ").ifEmpty { null }
            if (!details && summary != null) {
                Text(
                    summary,
                    style = ShifaTextStyles.metaSmall(),
                    color = ShifaColors.Text.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (details) Chips(entry, place)
        }
        // Capped so a long reason never squeezes the name out (flow 04/02 §3).
        Column(
            Modifier.widthIn(max = ShifaSize.S160),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(ShifaSize.S4),
        ) {
            Text(
                time,
                style = ShifaTextStyles.chipSlot(),
                color = ShifaColors.Text.Primary,
                textDecoration = if (cancelled) TextDecoration.LineThrough else null,
            )
            val minutes = (entry.end - entry.start).inWholeMinutes.toInt()
            if (details && entry is CalendarEntry.Appointment && minutes > 5) {
                Text(
                    duration(minutes),
                    Modifier.background(ShifaColors.Alpha.Teal15, ShifaRadius.Full).padding(horizontal = ShifaSize.S6),
                    style = ShifaTextStyles.caption(),
                    color = ShifaColors.Text.Primary,
                )
            }
            if (blocked) {
                Text(tr(Res.string.blocked), style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Error)
            } else if (details && entry is CalendarEntry.Appointment && entry.reason.isNotBlank()) {
                Text(
                    entry.reason,
                    style = ShifaTextStyles.metaSmall(),
                    color = ShifaColors.Text.Tertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun Lead(entry: CalendarEntry) =
    when (entry) {
        is CalendarEntry.Appointment -> ShifaAvatar(entry.photoUrl, ShifaSize.S40)
        is CalendarEntry.FreeSlot ->
            Box(
                Modifier.size(ShifaSize.S40).border(ShifaSize.S2, ShifaColors.Border.Brand, ShifaRadius.Full),
                contentAlignment = Alignment.Center,
            ) { Text("+", style = ShifaTextStyles.panelTitle(), color = ShifaColors.Text.Brand) }
        is CalendarEntry.Blocked ->
            Box(
                Modifier.size(ShifaSize.S40).background(ShifaColors.Bg.Surface, ShifaRadius.Full),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(Res.drawable.ic_block_16), null, tint = ShifaColors.Icon.Error)
            }
    }

@Composable
private fun title(entry: CalendarEntry) =
    when (entry) {
        is CalendarEntry.Appointment -> entry.patientName?.takeIf { it.isNotBlank() } ?: tr(Res.string.patient)
        is CalendarEntry.FreeSlot -> tr(Res.string.freeSlot)
        is CalendarEntry.Blocked -> entry.reason?.takeIf { it.isNotBlank() } ?: tr(Res.string.blocked)
    }

/** "Video Call" for a video appointment, else the location label (or the booked address). */
@Composable
private fun place(entry: CalendarEntry): String? =
    when {
        entry is CalendarEntry.Appointment && entry.isVideo -> tr(Res.string.videoCall)
        entry is CalendarEntry.Appointment -> entry.locationLabel ?: entry.location
        entry is CalendarEntry.FreeSlot -> entry.locationLabel
        else -> null
    }

@Composable
private fun Chips(
    entry: CalendarEntry,
    place: String?,
) = Row(horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6)) {
    val video = entry is CalendarEntry.Appointment && entry.isVideo
    place?.let {
        ProfileChip(
            it,
            if (video) ShifaColors.Bg.InfoSubtle else ShifaColors.Bg.SuccessSubtle,
            ShifaColors.Text.Primary,
        )
    }
    if (entry is CalendarEntry.Appointment) {
        status(entry.status)?.let { (label, bg, ink) -> ProfileChip(label, bg, ink) }
        if (entry.isVideo) payment(entry.paymentStatus)?.let { (label, bg, ink) -> ProfileChip(label, bg, ink) }
    }
}

@Composable
private fun FilterSheet(
    draft: AgendaFilter,
    actions: CalendarActions,
) = ProfileSheet({ actions.ui { it.copy(filterDraft = null) } }) {
    ShifaTray(tr(Res.string.filter), { actions.ui { it.copy(filterDraft = null) } }) {
        fun edit(change: (AgendaFilter) -> AgendaFilter) =
            actions.ui { s -> s.copy(filterDraft = s.filterDraft?.let(change)) }
        ShifaCheckboxRow(tr(Res.string.appointments), ToggleableState(draft.appointments), {
            edit { it.copy(appointments = !it.appointments) }
        })
        ShifaCheckboxRow(tr(Res.string.freeSlots), ToggleableState(draft.free), { edit { it.copy(free = !it.free) } })
        ShifaCheckboxRow(
            tr(Res.string.blocked),
            ToggleableState(draft.blocked),
            { edit { it.copy(blocked = !it.blocked) } },
        )
        ShifaPrimaryButton(
            tr(Res.string.apply),
            { actions.ui { s -> s.copy(filter = s.filterDraft ?: s.filter, filterDraft = null) } },
            Modifier.fillMaxWidth(),
        )
    }
}

/** The status chip (flow 04/02 §3): label and colours; null when the backend sent none. */
@Composable
private fun status(
    raw: String?,
): Triple<String, androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color>? {
    val code = raw?.uppercase()?.takeIf { it.isNotBlank() } ?: return null
    return when (code) {
        "COMPLETED" -> Triple(tr(Res.string.statusCompleted), ShifaColors.Bg.SuccessSubtle, ShifaColors.Text.Primary)
        "CANCELLED" -> Triple(tr(Res.string.statusCancelled), ShifaColors.Bg.ErrorSubtle, ShifaColors.Text.Error)
        "IN_PROGRESS" -> Triple(tr(Res.string.statusInProgress), ShifaColors.Bg.InfoSubtle, ShifaColors.Text.Info)
        "CONFIRMED" -> Triple(tr(Res.string.statusConfirmed), ShifaColors.Bg.WarningSubtle, ShifaColors.Text.Warning)
        "REQUESTED" -> Triple(tr(Res.string.statusRequested), ShifaColors.Bg.Canvas, ShifaColors.Text.Secondary)
        "NO_SHOW" -> Triple(tr(Res.string.statusNoShow), ShifaColors.Bg.Canvas, ShifaColors.Text.Secondary)
        else -> Triple(code, ShifaColors.Bg.Canvas, ShifaColors.Text.Secondary)
    }
}

private const val CANCELLED_ALPHA = 0.55f

/** The payment chip of a video appointment (flow 04/02 §3): label and colours; null when unknown. */
@Composable
private fun payment(
    raw: String?,
): Triple<String, androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color>? {
    val code = raw?.uppercase()?.takeIf { it.isNotBlank() } ?: return null
    return when (code) {
        "PAID" -> Triple(tr(Res.string.paymentPaid), ShifaColors.Bg.SuccessSubtle, ShifaColors.Text.Primary)
        "PENDING" -> Triple(tr(Res.string.paymentPending), ShifaColors.Bg.WarningSubtle, ShifaColors.Text.Warning)
        "FAILED" -> Triple(tr(Res.string.paymentFailed), ShifaColors.Bg.ErrorSubtle, ShifaColors.Text.Error)
        "NOT_REQUIRED" -> Triple(tr(Res.string.paymentNotRequired), ShifaColors.Bg.Canvas, ShifaColors.Text.Secondary)
        else -> Triple(code, ShifaColors.Bg.Canvas, ShifaColors.Text.Secondary)
    }
}

/** "30 min", "1 h", "1 h 30 min". */
@Composable
private fun duration(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> tr(Res.string.durationMinutes, m)
        m == 0 -> tr(Res.string.durationHours, h)
        else -> tr(Res.string.durationHoursMinutes, h, m)
    }
}
