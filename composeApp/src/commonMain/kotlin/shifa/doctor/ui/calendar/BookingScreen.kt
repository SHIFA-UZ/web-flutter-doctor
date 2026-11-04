package shifa.doctor.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.appointments.AppointmentsRepository
import shifa.doctor.data.appointments.BookRequest
import shifa.doctor.data.appointments.appointmentsRepository
import shifa.doctor.data.calendar.CalendarCache
import shifa.doctor.data.calendar.CalendarRepository
import shifa.doctor.data.calendar.DayKey
import shifa.doctor.data.calendar.calendarRepository
import shifa.doctor.data.calendar.chainEnds
import shifa.doctor.data.calendar.changed
import shifa.doctor.data.model.ApiException
import shifa.doctor.data.model.CalendarEntry
import shifa.doctor.data.model.PatientOption
import shifa.doctor.data.patients.PatientsRepository
import shifa.doctor.data.patients.patientsRepository
import shifa.doctor.data.profile.profileRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.assignPatient
import shifa.doctor.generated.resources.book
import shifa.doctor.generated.resources.bookingEndTime
import shifa.doctor.generated.resources.bookingFailed
import shifa.doctor.generated.resources.bookingRangeUnavailable
import shifa.doctor.generated.resources.bookingSameDayPast
import shifa.doctor.generated.resources.clinicAddress
import shifa.doctor.generated.resources.defaultVisitReason
import shifa.doctor.generated.resources.durationLabelShort
import shifa.doctor.generated.resources.failedToLoadPatients
import shifa.doctor.generated.resources.ic_alert_circle_16
import shifa.doctor.generated.resources.noPatientsFound
import shifa.doctor.generated.resources.pastSlotCannotAssign
import shifa.doctor.generated.resources.patientAssignedName
import shifa.doctor.generated.resources.patientIdLabel
import shifa.doctor.generated.resources.patientNumber
import shifa.doctor.generated.resources.place
import shifa.doctor.generated.resources.pleaseSelectPatient
import shifa.doctor.generated.resources.reason
import shifa.doctor.generated.resources.retry
import shifa.doctor.generated.resources.searchByNameOrId
import shifa.doctor.generated.resources.selected
import shifa.doctor.generated.resources.slotDetails
import shifa.doctor.generated.resources.startTime
import shifa.doctor.generated.resources.time
import shifa.doctor.generated.resources.videoCall
import shifa.doctor.generated.resources.willBeBookedAsVideoCall
import shifa.doctor.generated.resources.willBeBookedAtClinic
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.ui.emailcode.codeBanner
import shifa.doctor.ui.navigation.Notice
import shifa.doctor.ui.navigation.ShellNotice
import shifa.doctor.ui.profile.BackStep
import shifa.doctor.ui.profile.DiscardChangesSheet
import shifa.doctor.ui.profile.FormExitEffects
import shifa.doctor.ui.profile.ProfileChip
import shifa.doctor.ui.profile.ProfileSheet
import shifa.doctor.ui.profile.backStep
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.ShifaAvatar
import shifa.doctor.ui.widgets.ShifaBanner
import shifa.doctor.ui.widgets.ShifaBannerType
import shifa.doctor.ui.widgets.ShifaChip
import shifa.doctor.ui.widgets.ShifaDropdown
import shifa.doctor.ui.widgets.ShifaInputField
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.ui.widgets.ShifaListSkeleton
import shifa.doctor.ui.widgets.ShifaPrimaryButton
import shifa.doctor.ui.widgets.ShifaSearchField
import shifa.doctor.ui.widgets.ShifaSection
import shifa.doctor.ui.widgets.ShifaSettingsItem
import shifa.doctor.ui.widgets.ShifaSubHeader
import shifa.doctor.ui.widgets.ShifaTray
import shifa.doctor.utils.PracticeClock
import shifa.doctor.utils.displayTime
import shifa.doctor.utils.tr
import kotlin.time.Duration.Companion.seconds

data class BookingUiState(
    val slot: CalendarEntry.FreeSlot,
    val clock: PracticeClock,
    // Prefilled in the app's language (04/06 §11), sent when the field is left empty.
    val defaultReason: String,
    // Null until the day is read (an empty read day means nothing is free).
    val day: List<CalendarEntry>? = null,
    val start: CalendarEntry.FreeSlot = slot,
    val end: Instant = slot.end,
    val video: Boolean = false,
    val reason: String = defaultReason,
    val patient: PatientOption? = null,
    val patientMissing: Boolean = false,
    // The picker: everyone bookable (read once), the search, its error.
    val picking: Boolean = false,
    val patients: List<PatientOption>? = null,
    val patientsError: CodeBanner? = null,
    val query: String = "",
    val saving: Boolean = false,
    val banner: CodeBanner? = null,
    val askDiscard: Boolean = false,
    val closed: Boolean = false,
    val now: Instant = clock.nowInstant(),
) {
    val date: LocalDate get() = clock.local(slot.start).date

    /** Before today in the practice zone: nothing can be booked (today stays bookable, walk-ins included). */
    val pastDay: Boolean get() = date < clock.local(now).date

    /** Start choices: the day's free slots at the tapped slot's location (a booking never changes venue). */
    val starts: List<CalendarEntry.FreeSlot>
        get() = (freeStarts + slot).distinctBy { it.start }.sortedBy { it.start }

    /** The day's free slots at the tapped slot's location, as read. */
    private val freeStarts: List<CalendarEntry.FreeSlot>
        get() = day.orEmpty().filterIsInstance<CalendarEntry.FreeSlot>().filter { it.locationId == slot.locationId }

    /** End choices: the chain of free slots from the start (same location, no gap). */
    val ends: List<Instant> get() =
        chainEnds(
            day.orEmpty(),
            start.start,
            start.locationId,
        ).ifEmpty { listOf(start.end) }

    val minutes: Int get() = (end - start.start).inWholeMinutes.toInt()

    /** The start is in the day's free slots again (it may have been taken meanwhile). */
    val startFree: Boolean
        get() =
            day == null ||
                day.any {
                    it is CalendarEntry.FreeSlot && it.start == start.start && it.locationId == start.locationId
                }

    /** The picked start is free and the chain still reaches the picked end. */
    val rangeFree: Boolean get() = startFree && end in ends

    /**
     * After the day changed: a start taken meanwhile moves to one still free (the tapped slot first) with a banner, so
     * the doctor sees the new time and the selector never strands them; an end the chain no longer reaches goes back to
     * the start's own.
     */
    fun reconciled(): BookingUiState {
        if (!startFree) {
            val free = freeStarts.firstOrNull { it.start == slot.start } ?: freeStarts.firstOrNull() ?: return this
            return copy(start = free, end = free.end, banner = banner ?: CodeBanner(Res.string.bookingRangeUnavailable))
        }
        return if (end in ends) this else copy(end = start.end)
    }

    val dirty: Boolean
        get() = patient != null || video || reason != defaultReason || start.start != slot.start || end != slot.end

    val shown: List<PatientOption>
        get() {
            val q = query.trim().lowercase()
            val all = patients.orEmpty()
            return if (q.isEmpty()) {
                all
            } else {
                all.filter {
                    it.name.orEmpty().lowercase().contains(q) || it.id.toString().contains(q)
                }
            }
        }
}

/**
 * CAL-7 (flow 04/06, spec 03 §1.6, §1.10): books a free slot for a patient: the slot from the calendar, the patient from
 * the picker, clinic or video, a start among the day's free slots at that location and an end along the chain, and a
 * reason. `POST /schedule/book` sends the UTC start and the chain's minutes, `locationId` only in person. A 409 / 400
 * means the range was taken: the day is read again and the form stays. Success reads the day, closes and the shell says
 * who was booked.
 */
class BookingViewModel(
    slot: CalendarEntry.FreeSlot,
    clock: PracticeClock,
    defaultReason: String,
    private val calendar: CalendarRepository = calendarRepository,
    private val appointments: AppointmentsRepository = appointmentsRepository,
    private val patients: PatientsRepository = patientsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(BookingUiState(slot, clock, defaultReason))
    val state: StateFlow<BookingUiState> = _state.asStateFlow()

    init {
        // The picker opens instantly when the list is already there (04/06 §6 target).
        if (!_state.value.pastDay) loadPatients()
    }

    /** Follows the day in the cache and the clock while the screen is shown (see AppointmentPanelViewModel). */
    suspend fun follow() =
        coroutineScope {
            launch { calendar.state.collect(::onCache) }
            while (true) {
                delay(TICK)
                _state.update { it.copy(now = it.clock.nowInstant()) }
            }
        }

    private fun onCache(cache: CalendarCache) =
        _state.update { s ->
            val day = cache.days[DayKey(s.date)] ?: return@update s
            // The day changed under the form: keep the end only while the chain still reaches it (after a save).
            s.copy(day = day).let { if (it.saving) it else it.reconciled() }
        }

    fun ui(change: (BookingUiState) -> BookingUiState) = _state.update(change)

    fun pickStart(slot: CalendarEntry.FreeSlot) = _state.update { it.copy(start = slot, end = slot.end) }

    fun openPicker() {
        if (_state.value.saving) return
        _state.update { it.copy(picking = true) }
        if (_state.value.patients == null) loadPatients()
    }

    fun pickPatient(patient: PatientOption) =
        _state.update { it.copy(patient = patient, picking = false, patientMissing = false, query = "") }

    private var patientsLoad: Job? = null

    /** One read at a time: opening the picker during the prefetch waits for it instead of racing it. */
    fun loadPatients() {
        if (patientsLoad?.isActive == true) return
        _state.update { it.copy(patientsError = null) }
        patientsLoad =
            viewModelScope.launch {
                patients.forAssignment().fold(
                    onSuccess = { list -> _state.update { it.copy(patients = list, patientsError = null) } },
                    onFailure = { e ->
                        _state.update {
                            it.copy(
                                patientsError =
                                    CodeBanner(
                                        e.toUserMessage(Res.string.failedToLoadPatients),
                                        requestId = e.requestId,
                                    ),
                            )
                        }
                    },
                )
            }
    }

    fun back() =
        _state.update {
            when (backStep(it.saving, it.askDiscard, it.dirty)) {
                BackStep.Stay -> it
                BackStep.CloseQuestion -> it.copy(askDiscard = false)
                BackStep.Ask -> it.copy(askDiscard = true)
                BackStep.Close -> it.copy(closed = true)
            }
        }

    fun book() {
        // The day may have turned since the last tick: "past" is decided on the clock now.
        _state.update { it.copy(now = it.clock.nowInstant()) }
        val s = _state.value
        if (s.saving || s.pastDay) return
        val patient = s.patient
        val id = patient?.id
        if (id == null) return _state.update { it.copy(patientMissing = true) }
        _state.update { it.copy(saving = true, banner = null) }
        val request =
            BookRequest(
                startAt = s.start.start.toString(),
                slotMinutes = s.minutes,
                patientId = id,
                isVideo = s.video,
                reason = s.reason.trim().ifEmpty { s.defaultReason },
                // Only for older backends; this one labels video and known locations itself (BookingDtos).
                location = if (s.video) VIDEO_LOCATION else s.start.locationLabel ?: CLINIC_LOCATION,
                locationId = s.start.locationId.takeUnless { s.video },
            )
        viewModelScope.launch {
            val result = appointments.book(request)
            // Taken or not, the day is read again: a conflict shows why, a lost answer may hide a booking.
            calendar.changed(s.clock, s.date)
            result.fold(
                onSuccess = {
                    ShellNotice.pending.value = Notice(Res.string.patientAssignedName, true, listOf(name(patient)))
                    _state.update { it.copy(saving = false, closed = true) }
                },
                onFailure = { e ->
                    val taken = e is ApiException && e.status in setOf(400, 409)
                    val text =
                        if (taken) {
                            Res.string.bookingRangeUnavailable
                        } else {
                            e.toUserMessage(
                                Res.string.bookingFailed,
                            )
                        }
                    // The day was read again above: a range that no longer fits is narrowed before a retry.
                    _state.update {
                        it.copy(
                            saving = false,
                            banner = CodeBanner(text, requestId = e.requestId),
                        ).reconciled()
                    }
                },
            )
        }
    }

    private companion object {
        val TICK = 30.seconds
        const val VIDEO_LOCATION = "Video Consultation"
        const val CLINIC_LOCATION = "Clinic Address"

        // The notice is shown later in the shell: the name goes as it is (a nameless patient by number).
        fun name(p: PatientOption) = p.name?.takeIf { it.isNotBlank() } ?: "#${p.id}"
    }
}

@Composable
fun BookingScreen(
    slot: CalendarEntry.FreeSlot,
    opened: Long,
    onClose: () -> Unit,
) {
    val defaultReason = tr(Res.string.defaultVisitReason)
    val viewModel =
        viewModel(key = "booking-${slot.start}-${slot.locationId}-$opened") {
            BookingViewModel(slot, PracticeClock(profileRepository.me.value?.profile?.timeZone), defaultReason)
        }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.follow() }
    FormExitEffects(state.closed, {}, onClose, viewModel::back)
    BookingContent(
        state,
        BookingActions(
            back = viewModel::back,
            ui = viewModel::ui,
            pickStart = viewModel::pickStart,
            openPicker = viewModel::openPicker,
            pickPatient = viewModel::pickPatient,
            retryPatients = viewModel::loadPatients,
            book = viewModel::book,
        ),
    )
}

class BookingActions(
    val back: () -> Unit = {},
    val ui: ((BookingUiState) -> BookingUiState) -> Unit = {},
    val pickStart: (CalendarEntry.FreeSlot) -> Unit = {},
    val openPicker: () -> Unit = {},
    val pickPatient: (PatientOption) -> Unit = {},
    val retryPatients: () -> Unit = {},
    val book: () -> Unit = {},
)

@Composable
private fun patientName(p: PatientOption) =
    p.name?.takeIf { it.isNotBlank() } ?: tr(Res.string.patientNumber, p.id.toString())

@Composable
fun BookingContent(
    state: BookingUiState,
    actions: BookingActions,
) {
    Column(Modifier.fillMaxSize().background(ShifaColors.Bg.Canvas).imePadding()) {
        ShifaSubHeader(tr(Res.string.slotDetails), Modifier.statusBarsPadding(), onBack = actions.back)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(ShifaSpacing.Inset.Md),
            verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Stack.Md),
        ) {
            codeBanner(state.banner) { actions.ui { it.copy(banner = null) } }?.invoke()
            Summary(state)
            if (state.pastDay) {
                ShifaBanner(
                    tr(Res.string.pastSlotCannotAssign),
                    Res.drawable.ic_alert_circle_16,
                    ShifaBannerType.Warning,
                )
            } else {
                Form(state, actions)
            }
        }
        if (!state.pastDay) {
            Column(
                Modifier.fillMaxWidth().background(
                    ShifaColors.Bg.Surface,
                ).navigationBarsPadding().padding(ShifaSpacing.Inset.Md),
            ) {
                ShifaPrimaryButton(
                    tr(Res.string.book),
                    actions.book,
                    Modifier.fillMaxWidth(),
                    enabled = !state.saving && state.rangeFree,
                    loading = state.saving,
                )
            }
        }
    }
    if (state.picking) PatientPicker(state, actions)
    if (state.askDiscard) {
        DiscardChangesSheet(
            onDiscard = { actions.ui { it.copy(askDiscard = false, closed = true) } },
            onKeep = { actions.ui { it.copy(askDiscard = false) } },
        )
    }
}

/** "4 November 2026 · 09:00 – 10:00", the length, and where (the location chip, 04/06 §8 target). */
@Composable
private fun Summary(state: BookingUiState) =
    ShifaSection(null) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ShifaSize.S4)) {
            val start = state.clock.local(state.start.start)
            val end = state.clock.local(state.end)
            Text(
                "${longDate(start.date)} · ${displayTime(start.time)} – ${displayTime(end.time)}",
                style = ShifaTextStyles.panelTitle(),
                color = ShifaColors.Text.Primary,
            )
            Text(
                "${tr(Res.string.durationLabelShort)}: ${duration(state.minutes)}",
                style = ShifaTextStyles.metaSmall(),
                color = ShifaColors.Text.Secondary,
            )
            val place = if (state.video) tr(Res.string.videoCall) else state.start.locationLabel
            place?.let {
                ProfileChip(
                    it,
                    if (state.video) ShifaColors.Bg.InfoSubtle else ShifaColors.Bg.SuccessSubtle,
                    ShifaColors.Text.Primary,
                )
            }
        }
    }

@Composable
private fun Form(
    state: BookingUiState,
    actions: BookingActions,
) {
    if (state.start.start < state.now) {
        // Today's slot that has begun: still bookable for a walk-in, but say so (04/06 §13).
        Text(tr(Res.string.bookingSameDayPast), style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Secondary)
    }
    ShifaSection(null) {
        Column(Modifier.fillMaxWidth()) {
            ShifaSettingsItem(
                tr(Res.string.assignPatient),
                actions.openPicker,
                subtitle = state.patient?.let { "${tr(Res.string.selected)}: ${patientName(it)}" },
                divider = false,
            )
            if (state.patientMissing) {
                Text(
                    tr(Res.string.pleaseSelectPatient),
                    style = ShifaTextStyles.metaSmall(),
                    color = ShifaColors.Text.Error,
                )
            }
        }
    }
    ShifaSection(tr(Res.string.place)) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md)) {
            Row(horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6)) {
                // Locked while the request runs: it already carries the values shown.
                ShifaChip(
                    tr(Res.string.clinicAddress),
                    !state.video,
                    { if (!state.saving) actions.ui { it.copy(video = false) } },
                )
                ShifaChip(
                    tr(Res.string.videoCall),
                    state.video,
                    { if (!state.saving) actions.ui { it.copy(video = true) } },
                )
            }
            Text(
                tr(if (state.video) Res.string.willBeBookedAsVideoCall else Res.string.willBeBookedAtClinic),
                style = ShifaTextStyles.metaSmall(),
                color = ShifaColors.Text.Secondary,
            )
        }
    }
    TimeCard(state, actions)
    ShifaInputField(
        state.reason,
        { v -> actions.ui { it.copy(reason = v) } },
        tr(Res.string.reason),
        Modifier.fillMaxWidth(),
        enabled = !state.saving,
    )
}

@Composable
private fun TimeCard(
    state: BookingUiState,
    actions: BookingActions,
) = ShifaSection(tr(Res.string.time)) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md)) {
        val starts = state.starts
        val ends = state.ends
        var menu by remember { mutableStateOf<String?>(null) }

        fun label(at: Instant) = displayTime(state.clock.local(at).time)
        // Only a real choice gets a dropdown (04/06 §6): one start or one end is just the slot.
        if (starts.size > 1) {
            FieldLabel(tr(Res.string.startTime))
            ShifaDropdown(
                tr(Res.string.startTime),
                starts.map { label(it.start) },
                label(state.start.start),
                onSelect = { i ->
                    actions.pickStart(starts[i])
                    menu = null
                },
                expanded = menu == START,
                onExpandedChange = { menu = if (it && !state.saving) START else null },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (ends.size > 1) {
            FieldLabel(tr(Res.string.bookingEndTime))
            ShifaDropdown(
                tr(Res.string.bookingEndTime),
                ends.map { "${label(it)} · ${duration((it - state.start.start).inWholeMinutes.toInt())}" },
                "${label(state.end)} · ${duration(state.minutes)}",
                onSelect = { i ->
                    actions.ui { it.copy(end = ends[i]) }
                    menu = null
                },
                expanded = menu == END,
                onExpandedChange = { menu = if (it && !state.saving) END else null },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (starts.size <= 1 && ends.size <= 1) {
            Text(
                "${label(state.start.start)} – ${label(state.end)}",
                style = ShifaTextStyles.dialogBody(),
                color = ShifaColors.Text.Primary,
            )
        }
    }
}

private const val START = "start"
private const val END = "end"

/** Flow 04/06 §3b: search by name or id over everyone bookable; a tap picks and closes. ("New patient" comes with PAT.) */
@Composable
private fun PatientPicker(
    state: BookingUiState,
    actions: BookingActions,
) = ProfileSheet({ actions.ui { it.copy(picking = false) } }) {
    ShifaTray(tr(Res.string.assignPatient), { actions.ui { it.copy(picking = false) } }) {
        ShifaSearchField(
            state.query,
            { q -> actions.ui { it.copy(query = q) } },
            tr(Res.string.searchByNameOrId),
            Modifier.fillMaxWidth(),
        )
        val error = state.patientsError
        when {
            error != null -> {
                codeBanner(error) {}?.invoke()
                ShifaLink(tr(Res.string.retry), actions.retryPatients)
            }
            state.patients == null -> ShifaListSkeleton(count = 4)
            state.shown.isEmpty() ->
                Text(
                    tr(Res.string.noPatientsFound),
                    style = ShifaTextStyles.dialogBody(),
                    color = ShifaColors.Text.Secondary,
                )
            else ->
                LazyColumn(
                    Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(ShifaSize.S4),
                ) {
                    items(state.shown, key = { it.id ?: 0L }) { p ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Button) { actions.pickPatient(p) }
                                .padding(vertical = ShifaSpacing.Inset.Xs),
                            horizontalArrangement = Arrangement.spacedBy(ShifaSize.S12),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ShifaAvatar(p.photoUrl, ShifaSize.S40)
                            Column(Modifier.weight(1f)) {
                                Text(
                                    patientName(p),
                                    style = ShifaTextStyles.dialogBody(),
                                    color = ShifaColors.Text.Primary,
                                )
                                Text(
                                    tr(Res.string.patientIdLabel, p.id.toString()),
                                    style = ShifaTextStyles.metaSmall(),
                                    color = ShifaColors.Text.Secondary,
                                )
                            }
                        }
                    }
                }
        }
    }
}
