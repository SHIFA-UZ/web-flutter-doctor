package shifa.doctor.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.calendar.CalendarCache
import shifa.doctor.data.calendar.CalendarRepository
import shifa.doctor.data.calendar.DayKey
import shifa.doctor.data.calendar.calendarRepository
import shifa.doctor.data.model.CalendarEntry
import shifa.doctor.data.model.DayOccupancy
import shifa.doctor.data.profile.profileRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.couldNotLoadThisDay
import shifa.doctor.generated.resources.newAppointment
import shifa.doctor.generated.resources.next
import shifa.doctor.generated.resources.noSlotsAvailable
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.ui.profile.ProfileSheet
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.ShifaChip
import shifa.doctor.ui.widgets.ShifaChipType
import shifa.doctor.ui.widgets.ShifaListSkeleton
import shifa.doctor.ui.widgets.ShifaLoadError
import shifa.doctor.ui.widgets.ShifaPrimaryButton
import shifa.doctor.ui.widgets.ShifaTray
import shifa.doctor.utils.PracticeClock
import shifa.doctor.utils.displayTime
import shifa.doctor.utils.tr
import kotlin.time.Duration.Companion.seconds

data class SlotPickUiState(
    val clock: PracticeClock,
    val date: LocalDate = clock.today(),
    val month: LocalDate = date,
    // Null while the day is read.
    val slots: List<CalendarEntry.FreeSlot>? = null,
    val error: CodeBanner? = null,
    val target: CalendarEntry.FreeSlot? = null,
    val days: Map<DayKey, List<CalendarEntry>> = emptyMap(),
    val now: Instant = clock.nowInstant(),
) {
    fun occupancy(date: LocalDate): DayOccupancy? =
        days[DayKey(date)]?.let { entries -> DayOccupancy.of(entries) { clock.local(it).time == LocalTime(0, 0) } }
}

/**
 * A free slot to book from outside the calendar (PAT-07, flow 06/04 "Make appointment"): the month shaded like the
 * calendar, the picked day's future free slots as chips, Next. The booking itself is CAL-7's screen.
 */
class SlotPickViewModel(
    clock: PracticeClock,
    private val calendar: CalendarRepository = calendarRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SlotPickUiState(clock))
    val state: StateFlow<SlotPickUiState> = _state.asStateFlow()

    /** Run while the sheet shows: the cache's days (shading, and a slot booked elsewhere goes), and the clock (30 s). */
    suspend fun follow() =
        coroutineScope {
            launch { calendar.state.collect(::onCache) }
            while (true) {
                delay(TICK)
                tick()
            }
        }

    /** Drops slots that have started (and their selection) as time passes. */
    fun tick() =
        _state.update { s ->
            val now = s.clock.nowInstant()
            s.copy(now = now, slots = s.slots?.filter { it.start > now }, target = s.target?.takeIf { it.start > now })
        }

    /** The picked slot if it is still ahead (checked again at Next). */
    fun confirm(): CalendarEntry.FreeSlot? {
        tick()
        return _state.value.target
    }

    private fun onCache(cache: CalendarCache) =
        _state.update { s ->
            val day = cache.days[DayKey(s.date)]
            if (day == null || s.slots == null) return@update s.copy(days = cache.days)
            val now = s.clock.nowInstant()
            val slots = free(day, now)
            s.copy(days = cache.days, slots = slots, now = now, target = s.target?.takeIf { it in slots })
        }

    fun open() {
        pick(_state.value.date)
        month(0)
    }

    /** Reads [day]'s free slots; past days are closed. */
    fun pick(day: LocalDate) {
        val s = _state.value
        if (day < s.clock.today()) return
        val now = s.clock.nowInstant()
        _state.update { it.copy(date = day, slots = null, error = null, target = null, now = now) }
        viewModelScope.launch {
            val result = calendar.day(DayKey(day))
            _state.update { st ->
                if (st.date != day) return@update st
                result.fold(
                    { st.copy(slots = free(it, now)) },
                    {
                            e ->
                        st.copy(
                            error =
                                CodeBanner(
                                    e.toUserMessage(Res.string.couldNotLoadThisDay),
                                    requestId = e.requestId,
                                ),
                        )
                    },
                )
            }
        }
    }

    fun month(delta: Int) {
        val month = _state.value.month.plus(DatePeriod(months = delta))
        _state.update { it.copy(month = month) }
        viewModelScope.launch { calendar.month(monthCells(month).filterNotNull()) }
    }

    fun select(slot: CalendarEntry.FreeSlot) = _state.update { it.copy(target = slot) }

    private fun free(
        entries: List<CalendarEntry>,
        now: Instant,
    ) = entries.filterIsInstance<CalendarEntry.FreeSlot>().filter { it.start > now }.sortedBy { it.start }
}

/** The sheet; [onPicked] gets the slot (the caller opens the booking), [onClose] on dismiss. */
@Composable
fun SlotPickSheet(
    opened: Long,
    onPicked: (CalendarEntry.FreeSlot) -> Unit,
    onClose: () -> Unit,
) {
    val viewModel =
        viewModel(key = "slot-pick-$opened") {
            SlotPickViewModel(PracticeClock(profileRepository.me.value?.profile?.timeZone))
        }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.open() }
    LaunchedEffect(viewModel) { viewModel.follow() }
    ProfileSheet(onClose) {
        SlotPickTray(
            state,
            viewModel::pick,
            viewModel::month,
            viewModel::select,
            { viewModel.confirm()?.let(onPicked) },
            onClose,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SlotPickTray(
    state: SlotPickUiState,
    pick: (LocalDate) -> Unit,
    month: (Int) -> Unit,
    select: (CalendarEntry.FreeSlot) -> Unit,
    next: () -> Unit,
    close: () -> Unit,
) = ShifaTray(tr(Res.string.newAppointment), close) {
    Column(
        Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md),
    ) {
        MonthPicker(state.month, state.date, state.clock.today(), state::occupancy, pick, month)
        val slots = state.slots
        val error = state.error
        when {
            error != null ->
                ShifaLoadError(tr(error.text), { pick(state.date) }, requestId = error.requestId, compact = true)
            slots == null -> ShifaListSkeleton(count = 2)
            slots.isEmpty() ->
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
                    // Two locations at one time stay two chips, each naming its place.
                    val places = slots.mapNotNull { it.locationId }.distinct().size > 1
                    slots.forEach { slot ->
                        val time = displayTime(state.clock.local(slot.start).time)
                        ShifaChip(
                            if (places) "$time · ${slot.locationLabel.orEmpty()}" else time,
                            state.target == slot,
                            { select(slot) },
                            type = ShifaChipType.Slot,
                        )
                    }
                }
        }
    }
    ShifaPrimaryButton(tr(Res.string.next), next, Modifier.fillMaxWidth(), enabled = state.target != null)
}

private val TICK = 30.seconds
