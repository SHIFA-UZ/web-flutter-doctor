package shifa.doctor.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.StringResource
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.calendar.CalendarRepository
import shifa.doctor.data.calendar.calendarRepository
import shifa.doctor.data.locations.LocationsRepository
import shifa.doctor.data.locations.locationsRepository
import shifa.doctor.data.model.DateSpecificRuleDto
import shifa.doctor.data.model.DoctorLocation
import shifa.doctor.data.model.ValidityPeriodDto
import shifa.doctor.data.profile.profileRepository
import shifa.doctor.data.schedule.RangeProblem
import shifa.doctor.data.schedule.SLOT_SIZES
import shifa.doctor.data.schedule.ScheduleRepository
import shifa.doctor.data.schedule.TimeRange
import shifa.doctor.data.schedule.WeeklySchedule
import shifa.doctor.data.schedule.periodFits
import shifa.doctor.data.schedule.rangeProblem
import shifa.doctor.data.schedule.scheduleRepository
import shifa.doctor.data.storage.ActiveLocationStorage
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.add
import shifa.doctor.generated.resources.addFirstLocationHint
import shifa.doctor.generated.resources.addLocation
import shifa.doctor.generated.resources.addPeriod
import shifa.doctor.generated.resources.addTimeRange
import shifa.doctor.generated.resources.applyToMonFri
import shifa.doctor.generated.resources.bookableUntil
import shifa.doctor.generated.resources.bookingEndTime
import shifa.doctor.generated.resources.copyFrom
import shifa.doctor.generated.resources.dateHoursFailed
import shifa.doctor.generated.resources.dateSpecificHours
import shifa.doctor.generated.resources.endTimeMustBeAfterStartTime
import shifa.doctor.generated.resources.friday
import shifa.doctor.generated.resources.fromDate
import shifa.doctor.generated.resources.ic_trash_22
import shifa.doctor.generated.resources.manage
import shifa.doctor.generated.resources.monday
import shifa.doctor.generated.resources.noBookablePeriod
import shifa.doctor.generated.resources.noDateSpecific
import shifa.doctor.generated.resources.noSlotFits
import shifa.doctor.generated.resources.off
import shifa.doctor.generated.resources.periodOverlaps
import shifa.doctor.generated.resources.remove
import shifa.doctor.generated.resources.retry
import shifa.doctor.generated.resources.saturday
import shifa.doctor.generated.resources.save
import shifa.doctor.generated.resources.scheduleLoadFailed
import shifa.doctor.generated.resources.scheduleSaveFailed
import shifa.doctor.generated.resources.scheduleSaved
import shifa.doctor.generated.resources.scheduleSavedPeriodFailed
import shifa.doctor.generated.resources.scheduleValidUntil
import shifa.doctor.generated.resources.slotTimeframe
import shifa.doctor.generated.resources.slotTooShortError
import shifa.doctor.generated.resources.startTime
import shifa.doctor.generated.resources.sunday
import shifa.doctor.generated.resources.thisTimeOverlapsExistingSlot
import shifa.doctor.generated.resources.thursday
import shifa.doctor.generated.resources.toDate
import shifa.doctor.generated.resources.tooManySlotsError
import shifa.doctor.generated.resources.tuesday
import shifa.doctor.generated.resources.wednesday
import shifa.doctor.generated.resources.workingHours
import shifa.doctor.ui.calendar.FieldLabel
import shifa.doctor.ui.calendar.MonthPicker
import shifa.doctor.ui.calendar.longDate
import shifa.doctor.ui.calendar.quarterHours
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.ui.emailcode.codeBanner
import shifa.doctor.ui.navigation.Notice
import shifa.doctor.ui.navigation.ShellNotice
import shifa.doctor.ui.profile.BackStep
import shifa.doctor.ui.profile.DiscardChangesSheet
import shifa.doctor.ui.profile.FormExitEffects
import shifa.doctor.ui.profile.ProfileSheet
import shifa.doctor.ui.profile.backStep
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.ShifaChip
import shifa.doctor.ui.widgets.ShifaChipType
import shifa.doctor.ui.widgets.ShifaDropdown
import shifa.doctor.ui.widgets.ShifaIconButton
import shifa.doctor.ui.widgets.ShifaIconButtonVariant
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.ui.widgets.ShifaListSkeleton
import shifa.doctor.ui.widgets.ShifaPrimaryButton
import shifa.doctor.ui.widgets.ShifaSecondaryButton
import shifa.doctor.ui.widgets.ShifaSection
import shifa.doctor.ui.widgets.ShifaSettingsItem
import shifa.doctor.ui.widgets.ShifaSubHeader
import shifa.doctor.ui.widgets.ShifaTray
import shifa.doctor.utils.PracticeClock
import shifa.doctor.utils.displayTime
import shifa.doctor.utils.tr

/** A range being typed in a sheet: minutes of the day and the slot size. */
data class RangeDraft(
    val startMinute: Int = 9 * 60,
    val endMinute: Int = 17 * 60,
    val slotMinutes: Int = 30,
) {
    fun range() = TimeRange(time(startMinute), time(endMinute), slotMinutes)
}

/** The extra-hours form (flow 04/11): a date span, the hours and the slot size. */
data class DateHoursDraft(
    val from: LocalDate,
    val to: LocalDate,
    val hours: RangeDraft = RangeDraft(),
    val picking: Boolean? = null, // true = From, false = To
    val month: LocalDate = from,
)

/** Which date of the new validity period the inline month is picking. */
enum class PeriodField { From, Until }

data class ScheduleUiState(
    val clock: PracticeClock,
    val locations: List<DoctorLocation>? = null,
    val locationId: Long? = null,
    // Null until the rules of the chosen location are read; Save stays off until then (a failed read must never
    // overwrite the real template with an empty one, Flutter bug).
    val saved: WeeklySchedule? = null,
    val week: WeeklySchedule = WeeklySchedule(),
    val loading: Boolean = false,
    val loadError: CodeBanner? = null,
    val periods: List<ValidityPeriodDto> = emptyList(),
    // A validity period to add on Save: no start means from today.
    val newFrom: LocalDate? = null,
    val newUntil: LocalDate? = null,
    val periodPicking: PeriodField? = null,
    val periodMonth: LocalDate = clock.today(),
    val dates: List<DateSpecificRuleDto> = emptyList(),
    val dateDraft: DateHoursDraft? = null,
    val dateBusy: Boolean = false,
    // The day sheet: which weekday, the range being added and why it can't be.
    val day: Int? = null,
    val draft: RangeDraft = RangeDraft(),
    val problem: RangeProblem? = null,
    val saving: Boolean = false,
    val banner: CodeBanner? = null,
    val askDiscard: Boolean = false,
    // A location the doctor picked while there were unsaved edits (switching asks first).
    val pendingLocation: Long? = null,
    val closed: Boolean = false,
) {
    val newPeriod: Pair<LocalDate, LocalDate>? get() = newUntil?.let { (newFrom ?: clock.today()) to it }

    /** The new period can be added (before, after or inside the existing ones, ending within a year). */
    val periodOk: Boolean
        get() =
            newPeriod?.let { (from, until) ->
                until <= clock.today().plus(DatePeriod(days = MAX_DAYS)) &&
                    periodFits(
                        from,
                        until,
                        periods.mapNotNull {
                                p ->
                            date(p.validFrom)?.let { f -> date(p.validUntil)?.let { f to it } }
                        },
                    )
            } ?: true

    val dirty: Boolean get() = (saved != null && week != saved) || newUntil != null

    val canSave: Boolean get() = saved != null && dirty && periodOk && !saving

    /** The last day patients can book (the latest period's end), if any. */
    val bookableUntil: LocalDate? get() = periods.mapNotNull { date(it.validUntil) }.maxOrNull()
}

/** Bookable at most a year ahead (03 §2.2). */
private const val MAX_DAYS = 365

private fun date(s: String) = runCatching { LocalDate.parse(s) }.getOrNull()

private fun time(minute: Int) = LocalTime(minute / 60, minute % 60)

private fun minuteOf(t: LocalTime) = t.hour * 60 + t.minute

/**
 * SCHED-2 (flows 04/10, 04/11, 04/12; spec 03 §2.2–2.3): the weekly hours of one location as a week overview, each day
 * edited in a sheet (ranges, copy from another day, apply to Mon–Fri), the bookable period, and extra hours on dates
 * (written at once). Save replaces the location's template and adds a new period if one was set; until the rules are
 * read Save stays off.
 */
class ScheduleViewModel(
    private val schedule: ScheduleRepository = scheduleRepository,
    private val places: LocationsRepository = locationsRepository,
    private val calendar: CalendarRepository = calendarRepository,
    clock: PracticeClock = PracticeClock(profileRepository.me.value?.profile?.timeZone),
    private val active: ActiveLocationStorage = ActiveLocationStorage(),
    private val doctorId: () -> Long? = { profileRepository.me.value?.profile?.id },
) : ViewModel() {
    private val _state = MutableStateFlow(ScheduleUiState(clock))
    val state: StateFlow<ScheduleUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { places.locations.collect { list -> _state.update { it.copy(locations = list) } } }
    }

    /** Every edit goes through here or the actions below; none while saving (Save sends what was there). */
    fun ui(change: (ScheduleUiState) -> ScheduleUiState) = edit(change)

    private fun edit(change: (ScheduleUiState) -> ScheduleUiState) = _state.update { if (it.saving) it else change(it) }

    /** Locations first (the chosen one, else the primary, else the first), then that location's schedule. */
    fun load() {
        _state.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            val listed = places.locations.value?.let { Result.success(it) } ?: places.refresh()
            val list =
                listed.getOrElse { e ->
                    // The locations couldn't be read: say so with Retry (not an empty screen).
                    return@launch _state.update {
                        it.copy(
                            loading = false,
                            loadError =
                                CodeBanner(
                                    e.toUserMessage(Res.string.scheduleLoadFailed),
                                    requestId = e.requestId,
                                ),
                        )
                    }
                }

            // The location picked this session, else the one last used on this phone, else the primary, else the first.
            fun known(id: Long?) = id?.takeIf { wanted -> list.any { it.id == wanted } }
            val id =
                known(_state.value.locationId) ?: known(doctorId()?.let(active::get))
                    ?: list.firstOrNull { it.isPrimary }?.id ?: list.firstOrNull()?.id
            if (id == null) return@launch _state.update { it.copy(loading = false, locationId = null, saved = null) }
            read(id)
        }
    }

    // Each read takes a number; only the newest one may fill the form (A → B → A must not end with the first A).
    private var reads = 0

    /** The location's rules, the periods and its extra hours; all three are needed before anything can be saved. */
    private suspend fun read(locationId: Long) {
        val mine = ++reads
        _state.update { it.copy(locationId = locationId, loading = true, loadError = null, saved = null) }
        val (rules, periods, dates) =
            coroutineScope {
                val a = async { schedule.rules(locationId) }
                val b = async { schedule.periods() }
                val c = async { schedule.dateSpecific(locationId) }
                Triple(a.await(), b.await(), c.await())
            }
        if (mine != reads) return
        val failure = rules.exceptionOrNull() ?: periods.exceptionOrNull() ?: dates.exceptionOrNull()
        if (failure != null) {
            return _state.update {
                it.copy(
                    loading = false,
                    loadError =
                        CodeBanner(
                            failure.toUserMessage(Res.string.scheduleLoadFailed),
                            requestId = failure.requestId,
                        ),
                )
            }
        }
        doctorId()?.let { active.set(it, locationId) }
        val week = rules.getOrThrow()
        _state.update {
            it.copy(
                loading = false,
                saved = week,
                week = week,
                periods = periods.getOrThrow(),
                dates = dates.getOrThrow(),
                newFrom = null,
                newUntil = null,
            )
        }
    }

    /** Another location: unsaved edits ask first. */
    fun selectLocation(id: Long) {
        val s = _state.value
        if (id == s.locationId || s.saving) return
        if (s.dirty) return _state.update { it.copy(askDiscard = true, pendingLocation = id) }
        viewModelScope.launch { read(id) }
    }

    /** Discard confirmed: either switch to the pending location or leave the screen. */
    fun discard() {
        val pending = _state.value.pendingLocation
        _state.update {
            it.copy(
                askDiscard = false,
                pendingLocation = null,
                week = it.saved ?: it.week,
                newFrom = null,
                newUntil = null,
            )
        }
        if (pending != null) viewModelScope.launch { read(pending) } else _state.update { it.copy(closed = true) }
    }

    fun back() =
        _state.update {
            when (backStep(it.saving, it.askDiscard, it.dirty)) {
                BackStep.Stay -> it
                BackStep.CloseQuestion -> it.copy(askDiscard = false, pendingLocation = null)
                BackStep.Ask -> it.copy(askDiscard = true)
                BackStep.Close -> it.copy(closed = true)
            }
        }

    fun openDay(weekday: Int) = edit { it.copy(day = weekday, draft = RangeDraft(), problem = null) }

    /** Adds the typed range to the open day, checked like the backend does (inline message instead). */
    fun addRange() {
        val s = _state.value
        val day = s.day ?: return
        val range = s.draft.range()
        val problem = rangeProblem(range, s.week.ranges(day))
        if (problem != null) return _state.update { it.copy(problem = problem) }
        edit { it.copy(week = it.week.with(day, it.week.ranges(day) + range), problem = null) }
    }

    fun removeRange(
        weekday: Int,
        range: TimeRange,
    ) = edit { it.copy(week = it.week.with(weekday, it.week.ranges(weekday) - range)) }

    fun copyFrom(source: Int) = edit { s -> s.day?.let { s.copy(week = s.week.with(it, s.week.ranges(source))) } ?: s }

    /** The open day's hours on Monday to Friday (flow 04/10 target). */
    fun applyToWeekdays() =
        edit { s ->
            val day = s.day ?: return@edit s
            val ranges = s.week.ranges(day)
            s.copy(week = (1..5).fold(s.week) { w, d -> w.with(d, ranges) })
        }

    /** The new period's date from the inline month (no start date earlier than today, no end after a year). */
    fun pickPeriodDate(day: LocalDate) =
        edit { s ->
            val today = s.clock.today()
            if (day < today || day > today.plus(DatePeriod(days = MAX_DAYS))) return@edit s
            when (s.periodPicking) {
                PeriodField.From ->
                    s.copy(
                        newFrom = day,
                        newUntil = s.newUntil?.let { maxOf(it, day) } ?: day,
                        periodPicking = null,
                    )
                PeriodField.Until ->
                    s.copy(
                        newUntil = day,
                        newFrom = s.newFrom?.let { minOf(it, day) },
                        periodPicking = null,
                    )
                null -> s
            }
        }

    /** Starts a new period right after the last one (or today), so extending is two taps. */
    fun startPeriod() =
        edit { s ->
            val today = s.clock.today()
            val last = today.plus(DatePeriod(days = MAX_DAYS))
            val after = s.bookableUntil?.plus(DatePeriod(days = 1))?.takeIf { it > today }
            // Already bookable a year ahead: nothing to add.
            if (after != null && after > last) return@edit s
            s.copy(
                newFrom = after,
                newUntil = minOf((after ?: today).plus(DatePeriod(days = DEFAULT_DAYS)), last),
                periodPicking = null,
            )
        }

    fun save() {
        val s = _state.value
        val locationId = s.locationId ?: return
        if (!s.canSave) return
        _state.update { it.copy(saving = true, banner = null) }
        viewModelScope.launch {
            fun failed(
                e: Throwable,
                text: StringResource,
            ) = _state.update {
                it.copy(
                    saving = false,
                    banner = CodeBanner(e.toUserMessage(text), requestId = e.requestId),
                )
            }
            if (s.week != s.saved) {
                schedule.saveRules(
                    locationId,
                    s.week,
                ).onFailure { return@launch failed(it, Res.string.scheduleSaveFailed) }
                _state.update { it.copy(saved = s.week) }
                // Every day's free slots may have changed: the calendar reads its days again when shown (also when the
                // period below fails), and answers already on their way are dropped.
                calendar.invalidateAll()
            }
            // First setup: without any period nothing is bookable, so the default 60 days from today go with it.
            val today = s.clock.today()
            val period =
                s.newPeriod ?: (
                    today to
                        today.plus(
                            DatePeriod(days = DEFAULT_DAYS),
                        )
                ).takeIf { s.periods.isEmpty() }
            if (period != null) {
                // Two calls, not one (the backend has no combined save): a failed period after saved hours says so.
                schedule.addPeriod(period.first, period.second, today).onFailure {
                    // The period stays pending (also the first setup's default), so Save can try it again.
                    _state.update { s2 -> s2.copy(newFrom = period.first, newUntil = period.second) }
                    return@launch failed(it, Res.string.scheduleSavedPeriodFailed)
                }
                calendar.invalidateAll()
            }
            ShellNotice.pending.value = Notice(Res.string.scheduleSaved, success = true)
            _state.update { it.copy(saving = false, newFrom = null, newUntil = null, closed = true) }
        }
    }

    fun openDateHours() =
        _state.update { s ->
            val today = s.clock.today()
            s.copy(dateDraft = DateHoursDraft(today, today))
        }

    /** No edits while the hours are being sent (the request carries what was there). */
    fun editDateHours(change: (DateHoursDraft) -> DateHoursDraft) =
        _state.update { s ->
            if (s.dateBusy) return@update s
            s.copy(
                dateDraft = s.dateDraft?.let(change),
            )
        }

    /** Extra hours are written at once (flow 04/11), not with Save. */
    fun addDateHours() {
        val s = _state.value
        val d = s.dateDraft ?: return
        val locationId = s.locationId ?: return
        val range = d.hours.range()
        if (rangeProblem(range, emptyList()) != null || d.to < d.from || s.dateBusy) return
        _state.update { it.copy(dateBusy = true, banner = null) }
        viewModelScope.launch {
            val rule =
                DateSpecificRuleDto(
                    d.from.toString(),
                    d.to.toString(),
                    shifa.doctor.data.schedule.hhmm(range.start),
                    shifa.doctor.data.schedule.hhmm(range.end),
                    range.slotMinutes,
                    locationId,
                )
            schedule.addDateSpecific(rule).fold(
                onSuccess = { saved ->
                    calendar.invalidateAll()
                    _state.update { it.copy(dateBusy = false, dateDraft = null, dates = it.dates + saved) }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(
                            dateBusy = false,
                            banner = CodeBanner(e.toUserMessage(Res.string.dateHoursFailed), requestId = e.requestId),
                        )
                    }
                },
            )
        }
    }

    fun deleteDateHours(id: Long) {
        if (_state.value.dateBusy) return
        _state.update { it.copy(dateBusy = true, banner = null) }
        viewModelScope.launch {
            schedule.deleteDateSpecific(id).fold(
                onSuccess = {
                    calendar.invalidateAll()
                    _state.update { s -> s.copy(dateBusy = false, dates = s.dates.filterNot { it.id == id }) }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(
                            dateBusy = false,
                            banner = CodeBanner(e.toUserMessage(Res.string.dateHoursFailed), requestId = e.requestId),
                        )
                    }
                },
            )
        }
    }

    private companion object {
        const val DEFAULT_DAYS = 60
    }
}

class ScheduleActions(
    val back: () -> Unit = {},
    val ui: ((ScheduleUiState) -> ScheduleUiState) -> Unit = {},
    val retry: () -> Unit = {},
    val manage: () -> Unit = {},
    val location: (Long) -> Unit = {},
    val openDay: (Int) -> Unit = {},
    val addRange: () -> Unit = {},
    val removeRange: (Int, TimeRange) -> Unit = { _, _ -> },
    val copyFrom: (Int) -> Unit = {},
    val applyToWeekdays: () -> Unit = {},
    val startPeriod: () -> Unit = {},
    val pickPeriodDate: (LocalDate) -> Unit = {},
    val openDateHours: () -> Unit = {},
    val editDateHours: ((DateHoursDraft) -> DateHoursDraft) -> Unit = {},
    val addDateHours: () -> Unit = {},
    val deleteDateHours: (Long) -> Unit = {},
    val discard: () -> Unit = {},
    val save: () -> Unit = {},
)

@Composable
fun ScheduleScreen(
    onClose: () -> Unit,
    onManageLocations: () -> Unit,
    viewModel: ScheduleViewModel = viewModel { ScheduleViewModel() },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Read on open and on every return (from Manage locations too); edits in progress are kept by the guard in load.
    LaunchedEffect(Unit) { if (!viewModel.state.value.dirty) viewModel.load() }
    FormExitEffects(state.closed, { viewModel.ui { it.copy(closed = false) } }, onClose, viewModel::back)
    ScheduleContent(
        state,
        ScheduleActions(
            back = viewModel::back,
            ui = viewModel::ui,
            retry = viewModel::load,
            manage = onManageLocations,
            location = viewModel::selectLocation,
            openDay = viewModel::openDay,
            addRange = viewModel::addRange,
            removeRange = viewModel::removeRange,
            copyFrom = viewModel::copyFrom,
            applyToWeekdays = viewModel::applyToWeekdays,
            startPeriod = viewModel::startPeriod,
            pickPeriodDate = viewModel::pickPeriodDate,
            openDateHours = viewModel::openDateHours,
            editDateHours = viewModel::editDateHours,
            addDateHours = viewModel::addDateHours,
            deleteDateHours = viewModel::deleteDateHours,
            discard = viewModel::discard,
            save = viewModel::save,
        ),
    )
}

private val WEEKDAYS =
    listOf(
        Res.string.monday,
        Res.string.tuesday,
        Res.string.wednesday,
        Res.string.thursday,
        Res.string.friday,
        Res.string.saturday,
        Res.string.sunday,
    )

@Composable
private fun dayName(weekday: Int) = tr(WEEKDAYS[weekday - 1])

private fun label(minute: Int) = displayTime(LocalTime(minute / 60, minute % 60))

/** "09:00 – 13:00, 14:00 – 18:00 · 30 min" (one slot size when all ranges share it). */
@Composable
private fun summary(ranges: List<TimeRange>): String {
    if (ranges.isEmpty()) return tr(Res.string.off)
    val times = ranges.joinToString(", ") { "${displayTime(it.start)} – ${displayTime(it.end)}" }
    val slots = ranges.map { it.slotMinutes }.distinct()
    return if (slots.size == 1) "$times · ${shifa.doctor.ui.calendar.duration(slots[0])}" else times
}

@Composable
fun ScheduleContent(
    state: ScheduleUiState,
    actions: ScheduleActions,
) {
    Column(Modifier.fillMaxSize().background(ShifaColors.Bg.Canvas)) {
        ShifaSubHeader(tr(Res.string.workingHours), Modifier.statusBarsPadding(), onBack = actions.back)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(ShifaSpacing.Inset.Md),
            verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Stack.Md),
        ) {
            codeBanner(state.banner) { actions.ui { it.copy(banner = null) } }?.invoke()
            Locations(state, actions)
            val error = state.loadError
            when {
                state.saved == null && error != null -> {
                    codeBanner(error)?.invoke()
                    ShifaLink(tr(Res.string.retry), actions.retry)
                }
                state.locations?.isEmpty() == true -> Unit // the hint and Add location are above
                state.saved == null -> ShifaListSkeleton(count = 4)
                else -> {
                    Week(state, actions)
                    Period(state, actions)
                    DateHours(state, actions)
                }
            }
        }
        if (state.saved != null) {
            Column(
                Modifier.fillMaxWidth().background(
                    ShifaColors.Bg.Surface,
                ).navigationBarsPadding().padding(ShifaSpacing.Inset.Md),
            ) {
                ShifaPrimaryButton(
                    tr(Res.string.save),
                    actions.save,
                    Modifier.fillMaxWidth(),
                    enabled = state.canSave,
                    loading = state.saving,
                )
            }
        }
    }
    state.day?.let { DaySheet(state, it, actions) }
    state.dateDraft?.let { DateHoursSheet(state, it, actions) }
    if (state.askDiscard) {
        DiscardChangesSheet(actions.discard, {
            actions.ui { it.copy(askDiscard = false, pendingLocation = null) }
        })
    }
}

/** Location chips (only with two or more) and Manage; none yet → the hint and Add location. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Locations(
    state: ScheduleUiState,
    actions: ScheduleActions,
) {
    val list = state.locations
    if (list != null && list.isEmpty()) {
        Text(
            tr(Res.string.addFirstLocationHint),
            style = ShifaTextStyles.dialogBody(),
            color = ShifaColors.Text.Warning,
        )
        ShifaSecondaryButton(tr(Res.string.addLocation), actions.manage, Modifier.fillMaxWidth())
        return
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        FlowRow(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6),
            verticalArrangement = Arrangement.spacedBy(ShifaSize.S6),
        ) {
            if (list.orEmpty().size > 1) {
                list.orEmpty().forEach { l ->
                    ShifaChip(
                        l.label?.takeIf {
                            it.isNotBlank()
                        } ?: l.clinic.orEmpty(),
                        l.id == state.locationId,
                        { actions.location(l.id) },
                    )
                }
            } else {
                list?.firstOrNull()?.let {
                    Text(
                        it.label ?: it.clinic.orEmpty(),
                        style = ShifaTextStyles.panelTitle(),
                        color = ShifaColors.Text.Primary,
                    )
                }
            }
        }
        ShifaLink(tr(Res.string.manage), actions.manage)
    }
}

/** Seven rows, Monday first: the day's hours or "Off" (flow 04/10 target: overview + a sheet per day). */
@Composable
private fun Week(
    state: ScheduleUiState,
    actions: ScheduleActions,
) = ShifaSection(null) {
    Column(Modifier.fillMaxWidth()) {
        (1..7).forEach { d ->
            ShifaSettingsItem(
                dayName(d),
                { actions.openDay(d) },
                subtitle = summary(state.week.ranges(d)),
                divider = d < 7,
            )
        }
    }
}

/** The bookable period: until when patients can book, and a new period to add on Save. */
@Composable
private fun Period(
    state: ScheduleUiState,
    actions: ScheduleActions,
) = ShifaSection(tr(Res.string.scheduleValidUntil)) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md)) {
        Text(
            state.bookableUntil?.let { tr(Res.string.bookableUntil, longDate(it)) } ?: tr(Res.string.noBookablePeriod),
            style = ShifaTextStyles.dialogBody(),
            color = ShifaColors.Text.Primary,
        )
        val period = state.newPeriod
        if (period == null) {
            ShifaSecondaryButton(tr(Res.string.addPeriod), actions.startPeriod, Modifier.fillMaxWidth())
        } else {
            ShifaSettingsItem(
                tr(Res.string.fromDate),
                { actions.ui { it.copy(periodPicking = PeriodField.From, periodMonth = period.first) } },
                subtitle = longDate(period.first),
            )
            ShifaSettingsItem(
                tr(Res.string.toDate),
                { actions.ui { it.copy(periodPicking = PeriodField.Until, periodMonth = period.second) } },
                subtitle = longDate(period.second),
                divider = false,
            )
            if (!state.periodOk) {
                Text(
                    tr(Res.string.periodOverlaps),
                    style = ShifaTextStyles.metaSmall(),
                    color = ShifaColors.Text.Error,
                )
            }
            state.periodPicking?.let { field ->
                MonthPicker(
                    state.periodMonth,
                    if (field == PeriodField.From) period.first else period.second,
                    state.clock.today(),
                    { null },
                    actions.pickPeriodDate,
                    { delta -> actions.ui { it.copy(periodMonth = it.periodMonth.plus(DatePeriod(months = delta))) } },
                )
            }
        }
    }
}

/** Extra hours on dates (flow 04/11): written at once, listed with a delete each. */
@Composable
private fun DateHours(
    state: ScheduleUiState,
    actions: ScheduleActions,
) = ShifaSection(tr(Res.string.dateSpecificHours)) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md)) {
        if (state.dates.isEmpty()) {
            Text(
                tr(Res.string.noDateSpecific),
                style = ShifaTextStyles.metaSmall(),
                color = ShifaColors.Text.Secondary,
            )
        }
        state.dates.forEach { r ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val from = date(r.startDate)
                val to = date(r.endDate)
                val days =
                    if (from == null || from == to) {
                        from?.let {
                            longDate(it)
                        }.orEmpty()
                    } else {
                        "${longDate(from)} – ${to?.let { longDate(it) }.orEmpty()}"
                    }
                Column(Modifier.weight(1f)) {
                    Text(days, style = ShifaTextStyles.dialogBody(), color = ShifaColors.Text.Primary)
                    Text(
                        "${r.startTime.take(
                            5,
                        )} – ${r.endTime.take(5)} · ${shifa.doctor.ui.calendar.duration(r.slotMinutes)}",
                        style = ShifaTextStyles.metaSmall(),
                        color = ShifaColors.Text.Secondary,
                    )
                }
                r.id?.let { id ->
                    ShifaIconButton(Res.drawable.ic_trash_22, tr(Res.string.remove), {
                        actions.deleteDateHours(id)
                    }, variant = ShifaIconButtonVariant.Ghost)
                }
            }
        }
        ShifaSecondaryButton(
            tr(Res.string.add),
            actions.openDateHours,
            Modifier.fillMaxWidth(),
            enabled = !state.dateBusy,
        )
    }
}

@Composable
private fun problemText(problem: RangeProblem): StringResource =
    when (problem) {
        RangeProblem.EndNotAfterStart -> Res.string.endTimeMustBeAfterStartTime
        RangeProblem.SlotTooShort -> Res.string.slotTooShortError
        RangeProblem.TooManySlots -> Res.string.tooManySlotsError
        RangeProblem.Overlaps -> Res.string.thisTimeOverlapsExistingSlot
    }

/** One weekday: its ranges (remove each), add hours, copy from another day, apply to Mon–Fri. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DaySheet(
    state: ScheduleUiState,
    day: Int,
    actions: ScheduleActions,
) = ProfileSheet({ actions.ui { it.copy(day = null) } }) {
    ShifaTray(dayName(day), { actions.ui { it.copy(day = null) } }) {
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md),
        ) {
            val ranges = state.week.ranges(day)
            if (ranges.isEmpty()) {
                Text(
                    tr(Res.string.off),
                    style = ShifaTextStyles.dialogBody(),
                    color = ShifaColors.Text.Secondary,
                )
            }
            ranges.forEach { r ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${displayTime(r.start)} – ${displayTime(r.end)}",
                            style = ShifaTextStyles.dialogBody(),
                            color = ShifaColors.Text.Primary,
                        )
                        Text(
                            shifa.doctor.ui.calendar.duration(r.slotMinutes),
                            style = ShifaTextStyles.metaSmall(),
                            color = ShifaColors.Text.Secondary,
                        )
                        // The backend cuts a range into whole slots: a range shorter than one gives nothing.
                        if (minuteOf(r.end) - minuteOf(r.start) < r.slotMinutes) {
                            Text(
                                tr(Res.string.noSlotFits),
                                style = ShifaTextStyles.metaSmall(),
                                color = ShifaColors.Text.Warning,
                            )
                        }
                    }
                    ShifaIconButton(Res.drawable.ic_trash_22, tr(Res.string.remove), {
                        actions.removeRange(day, r)
                    }, variant = ShifaIconButtonVariant.Ghost)
                }
            }
            RangeFields(state.draft, { change -> actions.ui { it.copy(draft = change(it.draft), problem = null) } })
            state.problem?.let {
                Text(
                    tr(problemText(it)),
                    style = ShifaTextStyles.metaSmall(),
                    color = ShifaColors.Text.Error,
                )
            }
            ShifaSecondaryButton(tr(Res.string.addTimeRange), actions.addRange, Modifier.fillMaxWidth())
            val sources = (1..7).filter { it != day && state.week.ranges(it).isNotEmpty() }
            if (sources.isNotEmpty()) {
                FieldLabel(tr(Res.string.copyFrom))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6),
                    verticalArrangement = Arrangement.spacedBy(ShifaSize.S6),
                ) {
                    sources.forEach { d -> ShifaChip(dayName(d), false, { actions.copyFrom(d) }) }
                }
            }
        }
        if (state.week.ranges(
                day,
            ).isNotEmpty()
        ) {
            ShifaPrimaryButton(
                tr(Res.string.applyToMonFri),
                actions.applyToWeekdays,
                Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Start / end in quarter hours (24-hour labels) and the slot size chips (flow 04/12 target). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RangeFields(
    draft: RangeDraft,
    edit: ((RangeDraft) -> RangeDraft) -> Unit,
) {
    var menu by remember { mutableStateOf<Boolean?>(null) }
    // The backend reads "HH:mm" as a time of day, so a range ends by 23:45 (no 24:00 here, unlike a block).
    val starts = quarterHours.dropLast(2)
    val ends = quarterHours.drop(1).dropLast(1)
    FieldLabel(tr(Res.string.startTime))
    ShifaDropdown(
        tr(Res.string.startTime),
        starts.map(::label),
        label(draft.startMinute),
        onSelect = { i ->
            edit { it.copy(startMinute = starts[i]) }
            menu = null
        },
        expanded = menu == true,
        onExpandedChange = { menu = if (it) true else null },
        modifier = Modifier.fillMaxWidth(),
    )
    FieldLabel(tr(Res.string.bookingEndTime))
    ShifaDropdown(
        tr(Res.string.bookingEndTime),
        ends.map(::label),
        label(draft.endMinute),
        onSelect = { i ->
            edit { it.copy(endMinute = ends[i]) }
            menu = null
        },
        expanded = menu == false,
        onExpandedChange = { menu = if (it) false else null },
        modifier = Modifier.fillMaxWidth(),
    )
    FieldLabel(tr(Res.string.slotTimeframe))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6),
        verticalArrangement = Arrangement.spacedBy(ShifaSize.S6),
    ) {
        SLOT_SIZES.forEach { m ->
            ShifaChip(shifa.doctor.ui.calendar.duration(m), draft.slotMinutes == m, {
                edit { it.copy(slotMinutes = m) }
            }, type = ShifaChipType.Slot)
        }
    }
}

/** Extra hours on a date span (flow 04/11): From / To from the month, the hours and the slot size, Add. */
@Composable
private fun DateHoursSheet(
    state: ScheduleUiState,
    d: DateHoursDraft,
    actions: ScheduleActions,
) = ProfileSheet({ if (!state.dateBusy) actions.ui { it.copy(dateDraft = null) } }) {
    ShifaTray(tr(Res.string.dateSpecificHours), { if (!state.dateBusy) actions.ui { it.copy(dateDraft = null) } }) {
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md),
        ) {
            // A failed add shows here, over the form being retried (the screen behind is covered).
            codeBanner(state.banner) { actions.ui { it.copy(banner = null) } }?.invoke()
            ShifaSettingsItem(tr(Res.string.fromDate), {
                actions.editDateHours { it.copy(picking = true, month = it.from) }
            }, subtitle = longDate(d.from))
            ShifaSettingsItem(tr(Res.string.toDate), {
                actions.editDateHours { it.copy(picking = false, month = it.to) }
            }, subtitle = longDate(d.to), divider = false)
            d.picking?.let { from ->
                MonthPicker(
                    d.month,
                    if (from) d.from else d.to,
                    state.clock.today(),
                    { null },
                    { day ->
                        if (day >= state.clock.today()) {
                            actions.editDateHours {
                                if (from) {
                                    it.copy(
                                        from = day,
                                        to = maxOf(day, it.to),
                                        picking = null,
                                    )
                                } else {
                                    it.copy(to = day, from = minOf(day, it.from), picking = null)
                                }
                            }
                        }
                    },
                    { delta -> actions.editDateHours { it.copy(month = it.month.plus(DatePeriod(months = delta))) } },
                )
            }
            RangeFields(d.hours, { change -> actions.editDateHours { it.copy(hours = change(it.hours)) } })
            rangeProblem(d.hours.range(), emptyList())?.let {
                Text(tr(problemText(it)), style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Error)
            }
        }
        ShifaPrimaryButton(
            tr(Res.string.add),
            actions.addDateHours,
            Modifier.fillMaxWidth(),
            enabled = rangeProblem(d.hours.range(), emptyList()) == null && !state.dateBusy,
            loading = state.dateBusy,
        )
    }
}
