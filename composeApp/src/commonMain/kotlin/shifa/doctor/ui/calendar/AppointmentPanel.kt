package shifa.doctor.ui.calendar

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
import androidx.compose.ui.draw.alpha
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.StringResource
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.appointments.AppointmentsRepository
import shifa.doctor.data.appointments.Cta
import shifa.doctor.data.appointments.CtaHint
import shifa.doctor.data.appointments.CtaKind
import shifa.doctor.data.appointments.appointmentsRepository
import shifa.doctor.data.appointments.changeable
import shifa.doctor.data.appointments.ctaFor
import shifa.doctor.data.appointments.durationOptions
import shifa.doctor.data.appointments.fits
import shifa.doctor.data.calendar.CalendarCache
import shifa.doctor.data.calendar.CalendarRepository
import shifa.doctor.data.calendar.DayKey
import shifa.doctor.data.calendar.calendarRepository
import shifa.doctor.data.calendar.changed
import shifa.doctor.data.model.CalendarEntry
import shifa.doctor.data.model.DayOccupancy
import shifa.doctor.data.model.PatientDocument
import shifa.doctor.data.profile.profileRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.adjustDuration
import shifa.doctor.generated.resources.applyDurationChange
import shifa.doctor.generated.resources.appointmentCancelled
import shifa.doctor.generated.resources.appointmentDetails
import shifa.doctor.generated.resources.appointmentMoved
import shifa.doctor.generated.resources.appointmentOver
import shifa.doctor.generated.resources.cancelAppointment
import shifa.doctor.generated.resources.cancelAppointmentTitle
import shifa.doctor.generated.resources.continueAppointment
import shifa.doctor.generated.resources.couldNotLoadThisDay
import shifa.doctor.generated.resources.currentSlot
import shifa.doctor.generated.resources.dateAndTime
import shifa.doctor.generated.resources.durationChanged
import shifa.doctor.generated.resources.encouragePayment
import shifa.doctor.generated.resources.ic_sparkles_22
import shifa.doctor.generated.resources.ic_trash_22
import shifa.doctor.generated.resources.keepAppointment
import shifa.doctor.generated.resources.moveTo
import shifa.doctor.generated.resources.noSlotsAvailable
import shifa.doctor.generated.resources.openSummary
import shifa.doctor.generated.resources.pastAppointmentsCannotBeChanged
import shifa.doctor.generated.resources.patientWillBeNotified
import shifa.doctor.generated.resources.paymentReminderSent
import shifa.doctor.generated.resources.place
import shifa.doctor.generated.resources.placeReadOnlyHint
import shifa.doctor.generated.resources.reschedule
import shifa.doctor.generated.resources.saveFailed
import shifa.doctor.generated.resources.startAppointment
import shifa.doctor.generated.resources.unknownPatient
import shifa.doctor.generated.resources.videoCall
import shifa.doctor.generated.resources.videoEndedHint
import shifa.doctor.generated.resources.videoStartHint
import shifa.doctor.generated.resources.visitBriefingPending
import shifa.doctor.generated.resources.visitBriefingRowDocs
import shifa.doctor.generated.resources.visitBriefingRowFailed
import shifa.doctor.generated.resources.visitBriefingTitle
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
import shifa.doctor.ui.visit.BriefingSheet
import shifa.doctor.ui.widgets.DialogTone
import shifa.doctor.ui.widgets.ShifaAvatar
import shifa.doctor.ui.widgets.ShifaChip
import shifa.doctor.ui.widgets.ShifaChipType
import shifa.doctor.ui.widgets.ShifaDialogSheet
import shifa.doctor.ui.widgets.ShifaDropdown
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.ui.widgets.ShifaListSkeleton
import shifa.doctor.ui.widgets.ShifaLoadError
import shifa.doctor.ui.widgets.ShifaPrimaryButton
import shifa.doctor.ui.widgets.ShifaSecondaryButton
import shifa.doctor.ui.widgets.ShifaSection
import shifa.doctor.ui.widgets.ShifaSettingsItem
import shifa.doctor.ui.widgets.ShifaSubHeader
import shifa.doctor.ui.widgets.ShifaTray
import shifa.doctor.utils.PracticeClock
import shifa.doctor.utils.displayTime
import shifa.doctor.utils.tr
import shifa.doctor.utils.trPlural
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

data class PanelUiState(
    val appointment: CalendarEntry.Appointment,
    val clock: PracticeClock,
    val day: List<CalendarEntry> = emptyList(),
    val minutes: Int = (appointment.end - appointment.start).inWholeMinutes.toInt(),
    val busy: Boolean = false,
    val banner: CodeBanner? = null,
    val askCancel: Boolean = false,
    val askDiscard: Boolean = false,
    val cancelError: CodeBanner? = null,
    // The reschedule sheet: the chosen date, its free slots, and the chosen one.
    val rescheduleDate: LocalDate? = null,
    val rescheduleMonth: LocalDate = clock.local(appointment.start).date,
    val rescheduleSlots: List<CalendarEntry.FreeSlot>? = null,
    val rescheduleError: CodeBanner? = null,
    val target: CalendarEntry.FreeSlot? = null,
    val closed: Boolean = false,
    // Ticked by the ViewModel, so the main button and the change actions follow the clock while the panel is open.
    val now: Instant = clock.nowInstant(),
    // The calendar's cached days, for the reschedule month's shading.
    val days: Map<DayKey, List<CalendarEntry>> = emptyMap(),
    // A colleague's appointment (CAL-10): their days; the visit is theirs to start, so no main button.
    val doctorId: Long? = null,
) {
    val current: Int get() = (appointment.end - appointment.start).inWholeMinutes.toInt()
    val changeable: Boolean get() = appointment.changeable(now)
    val cta: Cta? get() = if (doctorId != null) null else ctaFor(appointment, clock, now)

    fun key(date: LocalDate) = DayKey(date, doctorId)

    fun occupancy(date: LocalDate): DayOccupancy? =
        days[key(date)]?.let { entries -> DayOccupancy.of(entries) { clock.local(it).time == LocalTime(0, 0) } }
}

/**
 * CAL-6 (flows 04/05, 04/07, 04/08; spec 03 §1.7): one appointment from the calendar: status and payment, patient,
 * when (Reschedule), Adjust duration, place, the payment reminder for an unpaid video visit, the main button by the
 * time window, and Cancel. Every change refreshes today and the touched days once (`changed`), so the agenda behind
 * updates too; the panel follows the live entry from the cache.
 */
class AppointmentPanelViewModel(
    appointment: CalendarEntry.Appointment,
    clock: PracticeClock,
    private val calendar: CalendarRepository = calendarRepository,
    private val appointments: AppointmentsRepository = appointmentsRepository,
    // A colleague's appointment from the clinic doctor schedule (CAL-10).
    private val doctorId: Long? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(PanelUiState(appointment, clock, doctorId = doctorId))
    val state: StateFlow<PanelUiState> = _state.asStateFlow()

    // The appointment's practice date now (a move changes it).
    private val date: LocalDate get() = _state.value.let { it.clock.local(it.appointment.start).date }

    /**
     * Follows the live entry in the calendar cache and ticks the clock every 30 s. The screen runs it while it is shown,
     * so a closed panel's ViewModel (left in the shell's store) holds no collector or timer.
     */
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
            val day =
                cache.days[
                    s.key(
                        s.clock.local(s.appointment.start).date,
                    ),
                ] ?: return@update s.copy(days = cache.days)
            val live =
                day.filterIsInstance<CalendarEntry.Appointment>().firstOrNull {
                    it.appointmentId == s.appointment.appointmentId
                }
            // Gone from its day (deleted, or moved elsewhere) while nothing of ours is running: the panel closes.
            if (live == null && !s.busy) return@update s.copy(day = day, days = cache.days, closed = true)
            val next = live ?: s.appointment
            val length = (next.end - next.start).inWholeMinutes.toInt()
            // An unapplied pick stays unless the length itself changed (elsewhere, or by this panel); a question about a
            // visit that can no longer change (cancelled meanwhile) closes.
            s.copy(
                appointment = next,
                day = day,
                days = cache.days,
                minutes = if (s.busy || length == s.current) s.minutes else length,
                askCancel = s.askCancel && next.changeable(s.now),
            )
        }

    /** Back (header or system): a running change keeps the panel; an open question closes; an unapplied length asks. */
    fun back() =
        _state.update {
            when (backStep(it.busy, it.askDiscard, it.minutes != it.current && it.changeable)) {
                BackStep.Stay -> it
                BackStep.CloseQuestion -> it.copy(askDiscard = false)
                BackStep.Ask -> it.copy(askDiscard = true)
                BackStep.Close -> it.copy(closed = true)
            }
        }

    fun ui(change: (PanelUiState) -> PanelUiState) = _state.update(change)

    val durations: List<Int> get() = durationOptions(_state.value.appointment, _state.value.day)

    fun applyDuration() {
        val s = _state.value
        if (s.busy || s.minutes == s.current) return
        val end = s.appointment.start + s.minutes.minutes
        run(Res.string.durationChanged, after = { it.copy(appointment = it.appointment.copy(end = end)) }) {
            appointments.change(s.appointment.appointmentId, s.appointment.start, s.minutes)
        }
    }

    fun remindPayment() =
        run(Res.string.paymentReminderSent, refresh = false) {
            appointments.remindPayment(_state.value.appointment.appointmentId)
        }

    /**
     * Opens the reschedule sheet on [day] (the appointment's own day first) and reads its free slots: future ones where
     * the visit's whole length fits; a failed read says so with Retry instead of "No slots" (04/07 §8).
     */
    fun rescheduleOn(day: LocalDate) {
        if (day < _state.value.clock.today()) return
        _state.update {
            it.copy(rescheduleDate = day, rescheduleSlots = null, rescheduleError = null, target = null)
        }
        viewModelScope.launch {
            val result = calendar.day(DayKey(day, doctorId))
            _state.update { s ->
                if (s.rescheduleDate != day) return@update s
                result.fold(
                    onSuccess = { entries ->
                        val slots =
                            entries.filterIsInstance<CalendarEntry.FreeSlot>().filter {
                                it.start > s.now && fits(it, entries, s.current)
                            }
                        s.copy(rescheduleSlots = slots)
                    },
                    onFailure = { e ->
                        s.copy(
                            rescheduleError =
                                CodeBanner(e.toUserMessage(Res.string.couldNotLoadThisDay), requestId = e.requestId),
                        )
                    },
                )
            }
        }
    }

    /** Pages the sheet's month and reads its days for the shading. */
    fun rescheduleMonth(delta: Int) {
        val month = _state.value.rescheduleMonth.plus(DatePeriod(months = delta))
        _state.update { it.copy(rescheduleMonth = month) }
        viewModelScope.launch { calendar.month(monthCells(month).filterNotNull(), doctorId) }
    }

    /** Moves to the chosen free slot keeping the length (the backend checks the slots after it). */
    fun move() {
        val s = _state.value
        val target = s.target ?: return
        val from = date
        val to = s.clock.local(target.start).date
        _state.update { it.copy(rescheduleDate = null) }
        val moved = s.appointment.copy(start = target.start, end = target.start + s.current.minutes)
        run(
            Res.string.appointmentMoved,
            days = listOf(from, to),
            close = true,
            after = { it.copy(appointment = moved) },
        ) {
            appointments.change(s.appointment.appointmentId, target.start, s.current)
        }
    }

    /** The question stays open on failure with its error, so the doctor can retry (flow 04/08 §3). */
    fun cancel() {
        val s = _state.value
        if (s.busy) return
        _state.update { it.copy(busy = true, cancelError = null) }
        viewModelScope.launch {
            appointments.cancel(s.appointment.appointmentId).fold(
                onSuccess = {
                    calendar.changed(s.clock, date, doctorId = doctorId)
                    ShellNotice.pending.value = Notice(Res.string.appointmentCancelled, success = true)
                    _state.update { it.copy(busy = false, askCancel = false, closed = true) }
                },
                onFailure = { e ->
                    // It may have gone through with the answer lost: the day read again shows it (04/08 §3).
                    calendar.changed(s.clock, date, doctorId = doctorId)
                    _state.update {
                        it.copy(
                            busy = false,
                            cancelError = CodeBanner(e.toUserMessage(Res.string.saveFailed), requestId = e.requestId),
                        )
                    }
                },
            )
        }
    }

    private companion object {
        val TICK = 30.seconds
    }

    private fun run(
        done: StringResource,
        refresh: Boolean = true,
        days: List<LocalDate> = listOf(date),
        close: Boolean = false,
        after: (PanelUiState) -> PanelUiState = { it },
        call: suspend () -> Result<Unit>,
    ) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, banner = null) }
        viewModelScope.launch {
            val result = call()
            // The new times first, so the refreshed day below finds the appointment where it is now.
            if (result.isSuccess) _state.update(after)
            // A conflict (409 / 400) means the calendar moved under us: reading the days shows why.
            if (refresh || result.isFailure) {
                calendar.changed(
                    _state.value.clock,
                    *days.toTypedArray(),
                    doctorId = doctorId,
                )
            }
            // A move is done with the panel: back to the calendar with the notice (04/07 §5).
            if (close && result.isSuccess) {
                ShellNotice.pending.value = Notice(done, success = true)
                return@launch _state.update { it.copy(busy = false, closed = true) }
            }
            _state.update {
                result.fold(
                    onSuccess = { _ -> it.copy(busy = false, banner = CodeBanner(done, error = false)) },
                    onFailure = {
                            e ->
                        it.copy(
                            busy = false,
                            banner = CodeBanner(e.toUserMessage(Res.string.saveFailed), requestId = e.requestId),
                        )
                    },
                )
            }
        }
    }
}

@Composable
fun AppointmentPanelScreen(
    appointment: CalendarEntry.Appointment,
    opened: Long,
    onClose: () -> Unit,
    // Start / Continue: the visit's screen (APPT-01).
    onStart: (CalendarEntry.Appointment) -> Unit = {},
    // An attached document from the briefing: the patient, the document.
    onOpenDocument: (Long, PatientDocument) -> Unit = { _, _ -> },
    // A colleague's appointment from the clinic doctor schedule (CAL-10): their calendar, the clinic's zone.
    scope: CalendarScope? = null,
    // The patient's record (Back returns here).
    onOpenPatient: ((Long) -> Unit)? = null,
    viewModel: AppointmentPanelViewModel =
        viewModel(key = "appointment-${appointment.appointmentId}-$opened") {
            AppointmentPanelViewModel(
                appointment,
                scope?.let { PracticeClock(it.zone) } ?: PracticeClock(profileRepository.me.value?.profile?.timeZone),
                doctorId = scope?.doctorId,
            )
        },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.follow() }
    var briefingOpen by remember { mutableStateOf(false) }
    FormExitEffects(state.closed, {}, onClose, viewModel::back)
    AppointmentPanel(
        state,
        PanelActions(
            back = viewModel::back,
            ui = viewModel::ui,
            briefing = { briefingOpen = true },
            primary = { kind ->
                // ponytail: Open summary waits for the summary document (PDF-01); Ended does nothing.
                if (kind == CtaKind.Start || kind == CtaKind.Continue) onStart(appointment)
            },
            applyDuration = viewModel::applyDuration,
            remind = viewModel::remindPayment,
            reschedule = viewModel::rescheduleOn,
            month = viewModel::rescheduleMonth,
            move = viewModel::move,
            cancel = viewModel::cancel,
            durations = { viewModel.durations },
            patient = onOpenPatient,
        ),
    )
    if (briefingOpen) {
        BriefingSheet(
            appointment.appointmentId,
            appointment.patientId,
            appointment.status.equals("COMPLETED", ignoreCase = true),
            state.clock,
            onOpenDocument = { d ->
                briefingOpen = false
                appointment.patientId?.let { onOpenDocument(it, d) }
            },
            onClose = { briefingOpen = false },
        )
    }
}

class PanelActions(
    val back: () -> Unit = {},
    val ui: ((PanelUiState) -> PanelUiState) -> Unit = {},
    val applyDuration: () -> Unit = {},
    val remind: () -> Unit = {},
    val reschedule: (LocalDate) -> Unit = {},
    val month: (Int) -> Unit = {},
    val move: () -> Unit = {},
    val cancel: () -> Unit = {},
    // The visit itself (start, continue, summary) comes with the visit flows (M5).
    val primary: (CtaKind) -> Unit = {},
    // The visit briefing sheet (APPT-06).
    val briefing: () -> Unit = {},
    val durations: () -> List<Int> = { emptyList() },
    // The patient's record (the name is a link when set and the patient is known).
    val patient: ((Long) -> Unit)? = null,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppointmentPanel(
    state: PanelUiState,
    actions: PanelActions,
) {
    val a = state.appointment
    val clock = state.clock
    val start = clock.local(a.start)
    val end = clock.local(a.end)
    Column(Modifier.fillMaxSize().background(ShifaColors.Bg.Canvas)) {
        ShifaSubHeader(tr(Res.string.appointmentDetails), Modifier.statusBarsPadding(), onBack = actions.back)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(ShifaSpacing.Inset.Md),
            verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Stack.Md),
        ) {
            codeBanner(state.banner) { actions.ui { it.copy(banner = null) } }?.invoke()
            // Patient, status, when.
            ShifaSection(null) {
                Column(verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ShifaSize.S12),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ShifaAvatar(a.photoUrl, ShifaSize.S48)
                        Column(Modifier.weight(1f)) {
                            val name = a.patientName?.takeIf { it.isNotBlank() } ?: tr(Res.string.unknownPatient)
                            val open = actions.patient
                            val id = a.patientId
                            if (open != null && id != null) {
                                ShifaLink(name, { open(id) })
                            } else {
                                Text(name, style = ShifaTextStyles.panelTitle(), color = ShifaColors.Text.Primary)
                            }
                            Text(
                                "${longDate(start.date)} · ${displayTime(start.time)} – ${displayTime(end.time)}",
                                style = ShifaTextStyles.metaSmall(),
                                color = ShifaColors.Text.Secondary,
                            )
                        }
                    }
                    StatusChips(a)
                }
            }
            // The visit briefing when the patient attached documents or one exists (flow 05/02 §2; board region 6).
            val briefing = a.briefingStatus?.uppercase()?.takeIf { it != "NONE" && it != "SKIPPED" }
            if (briefing != null || a.attachmentCount > 0) {
                ShifaSection(null) {
                    ShifaSettingsItem(
                        tr(Res.string.visitBriefingTitle),
                        actions.briefing,
                        icon = Res.drawable.ic_sparkles_22,
                        subtitle =
                            when (briefing) {
                                "PENDING", "GENERATING" -> tr(Res.string.visitBriefingPending)
                                "FAILED" -> tr(Res.string.visitBriefingRowFailed)
                                else -> trPlural(Res.plurals.visitBriefingRowDocs, a.attachmentCount, a.attachmentCount)
                            },
                        divider = false,
                    )
                }
            }
            // Only a visit that can still change opens the reschedule sheet; the header already says when.
            if (state.changeable) {
                ShifaSection(null) {
                    ShifaSettingsItem(
                        tr(Res.string.dateAndTime),
                        { actions.reschedule(start.date) },
                        subtitle = tr(Res.string.reschedule),
                        divider = false,
                    )
                }
            }
            if (state.changeable) DurationCard(state, actions)
            ShifaSection(tr(Res.string.place)) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ShifaSize.S4)) {
                    Text(
                        if (a.isVideo) tr(Res.string.videoCall) else a.locationLabel ?: a.location.orEmpty(),
                        style = ShifaTextStyles.dialogBody(),
                        color = ShifaColors.Text.Primary,
                    )
                    Text(
                        tr(Res.string.placeReadOnlyHint),
                        style = ShifaTextStyles.metaSmall(),
                        color = ShifaColors.Text.Tertiary,
                    )
                }
            }
            if (a.isVideo && a.paymentStatus.equals("PENDING", true) && state.changeable) {
                ShifaSecondaryButton(
                    tr(Res.string.encouragePayment),
                    actions.remind,
                    Modifier.fillMaxWidth(),
                    enabled = !state.busy,
                )
            }
        }
        ActionBox(state, actions)
    }
    if (state.askCancel) CancelSheet(state, actions)
    if (state.askDiscard) {
        DiscardChangesSheet(
            onDiscard = { actions.ui { it.copy(askDiscard = false, closed = true) } },
            onKeep = { actions.ui { it.copy(askDiscard = false) } },
        )
    }
    state.rescheduleDate?.let { RescheduleSheet(state, it, actions) }
}

@Composable
private fun StatusChips(a: CalendarEntry.Appointment) =
    Row(horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6)) {
        status(a.status)?.let { (label, bg, ink) -> ProfileChip(label, bg, ink) }
        if (a.isVideo) payment(a.paymentStatus)?.let { (label, bg, ink) -> ProfileChip(label, bg, ink) }
    }

@Composable
private fun DurationCard(
    state: PanelUiState,
    actions: PanelActions,
) = ShifaSection(tr(Res.string.adjustDuration)) {
    Column(verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md)) {
        val options = actions.durations()
        var open by remember { mutableStateOf(false) }
        // "Until 11:30 · 1 h 30 min": where the visit would end, and how long it would be.
        val labels =
            options.map {
                "${displayTime(state.clock.local(state.appointment.start + it.minutes).time)} · ${duration(it)}"
            }
        ShifaDropdown(
            tr(Res.string.adjustDuration),
            labels,
            labels.getOrNull(options.indexOf(state.minutes)),
            onSelect = { i ->
                actions.ui { it.copy(minutes = options[i]) }
                open = false
            },
            expanded = open,
            onExpandedChange = { open = it && !state.busy },
            modifier = Modifier.fillMaxWidth(),
        )
        ShifaPrimaryButton(
            tr(Res.string.applyDurationChange),
            actions.applyDuration,
            Modifier.fillMaxWidth(),
            enabled = state.minutes != state.current && !state.busy,
            loading = state.busy,
        )
    }
}

@Composable
private fun ActionBox(
    state: PanelUiState,
    actions: PanelActions,
) = Column(
    Modifier.fillMaxWidth().background(ShifaColors.Bg.Surface).navigationBarsPadding().padding(ShifaSpacing.Inset.Md),
    verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md),
) {
    state.cta?.let { cta ->
        ShifaPrimaryButton(
            tr(
                when (cta.kind) {
                    CtaKind.Start -> Res.string.startAppointment
                    CtaKind.Continue -> Res.string.continueAppointment
                    CtaKind.OpenSummary -> Res.string.openSummary
                    CtaKind.Ended -> Res.string.appointmentOver
                },
            ),
            { actions.primary(cta.kind) },
            Modifier.fillMaxWidth(),
            enabled = cta.enabled,
        )
        cta.hint?.let { hint ->
            Text(
                tr(
                    when (hint) {
                        CtaHint.Past -> Res.string.pastAppointmentsCannotBeChanged
                        CtaHint.VideoTooEarly -> Res.string.videoStartHint
                        CtaHint.VideoEnded -> Res.string.videoEndedHint
                    },
                ),
                style = ShifaTextStyles.metaSmall(),
                color = ShifaColors.Text.Secondary,
            )
        }
    }
    if (state.changeable) {
        ShifaSecondaryButton(
            tr(Res.string.cancelAppointment),
            { actions.ui { it.copy(askCancel = true, cancelError = null) } },
            Modifier.fillMaxWidth(),
            enabled = !state.busy,
        )
    }
}

@Composable
private fun CancelSheet(
    state: PanelUiState,
    actions: PanelActions,
) {
    val a = state.appointment
    val start = state.clock.local(a.start)
    // Who and when, so the right one is cancelled; the error stays here for a retry (flow 04/08 §3).
    val body =
        listOfNotNull(
            "${a.patientName ?: tr(Res.string.unknownPatient)} · ${longDate(start.date)} · ${displayTime(start.time)}",
            tr(Res.string.patientWillBeNotified),
            state.cancelError?.let { e -> listOfNotNull(tr(e.text), e.requestId).joinToString("\n") },
        ).joinToString("\n\n")
    ProfileSheet({ if (!state.busy) actions.ui { it.copy(askCancel = false) } }) {
        ShifaDialogSheet(
            tr(Res.string.cancelAppointmentTitle),
            tr(Res.string.cancelAppointment),
            actions.cancel,
            body = body,
            tone = DialogTone.Destructive,
            icon = Res.drawable.ic_trash_22,
            confirmLoading = state.busy,
            cancelText = tr(Res.string.keepAppointment),
            onCancel = { if (!state.busy) actions.ui { it.copy(askCancel = false) } },
        )
    }
}

/**
 * Flow 04/07 target: the month (shaded like the calendar, past days closed) to pick any date, that day's free slots
 * where the visit fits as chips (the location named when it differs, the current slot greyed), and "Move to 10:30".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RescheduleSheet(
    state: PanelUiState,
    date: LocalDate,
    actions: PanelActions,
) = ProfileSheet({ actions.ui { it.copy(rescheduleDate = null) } }) {
    ShifaTray(tr(Res.string.reschedule), { actions.ui { it.copy(rescheduleDate = null) } }) {
        // The month and the slots scroll; Move stays in reach below them.
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md),
        ) { RescheduleBody(state, date, actions) }
        val target = state.target
        ShifaPrimaryButton(
            target?.let {
                tr(
                    Res.string.moveTo,
                    displayTime(state.clock.local(it.start).time),
                )
            } ?: tr(Res.string.reschedule),
            actions.move,
            Modifier.fillMaxWidth(),
            enabled = target != null && !state.busy,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RescheduleBody(
    state: PanelUiState,
    date: LocalDate,
    actions: PanelActions,
) {
    MonthPicker(
        state.rescheduleMonth,
        date,
        state.clock.today(),
        state::occupancy,
        actions.reschedule,
        actions.month,
    )
    val slots = state.rescheduleSlots
    val a = state.appointment
    val own = state.clock.local(a.start).date == date
    val error = state.rescheduleError
    when {
        error != null ->
            ShifaLoadError(tr(error.text), { actions.reschedule(date) }, requestId = error.requestId, compact = true)
        slots == null -> ShifaListSkeleton(count = 2)
        slots.isEmpty() && !own ->
            Text(
                tr(Res.string.noSlotsAvailable),
                style = ShifaTextStyles.dialogBody(),
                color = ShifaColors.Text.Secondary,
            )
        else ->
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6),
                verticalArrangement = Arrangement.spacedBy(ShifaSize.S6),
            ) {
                // Every free slot (two locations at one time stay two chips), and the visit's own time greyed.
                val items = slots.map { it.start to it } + listOfNotNull(if (own) a.start to null else null)
                items.sortedBy { it.first }.forEach { (start, slot) ->
                    val time = displayTime(state.clock.local(start).time)
                    if (slot == null) {
                        ShifaChip(
                            "$time · ${tr(Res.string.currentSlot)}",
                            false,
                            {},
                            Modifier.alpha(CANCELLED_ALPHA),
                            type = ShifaChipType.Slot,
                        )
                    } else {
                        // A move can change the venue silently (04/07 §4): say where the slot is when it differs.
                        val elsewhere = !a.isVideo && slot.locationId != a.locationId
                        ShifaChip(
                            if (elsewhere) "$time · ${slot.locationLabel.orEmpty()}" else time,
                            state.target == slot,
                            { actions.ui { it.copy(target = slot) } },
                            type = ShifaChipType.Slot,
                        )
                    }
                }
            }
    }
}
