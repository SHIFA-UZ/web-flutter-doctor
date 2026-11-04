package shifa.doctor.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.calendar.BlocksRepository
import shifa.doctor.data.calendar.CalendarRepository
import shifa.doctor.data.calendar.blocksRepository
import shifa.doctor.data.calendar.calendarRepository
import shifa.doctor.data.calendar.changed
import shifa.doctor.data.calendar.knownDays
import shifa.doctor.data.model.CalendarEntry
import shifa.doctor.data.profile.profileRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.allDay
import shifa.doctor.generated.resources.blockCancelOverlapping
import shifa.doctor.generated.resources.blockCancelOverlappingHint
import shifa.doctor.generated.resources.blockCountFailed
import shifa.doctor.generated.resources.blockEndDateMustBeOnOrAfterStart
import shifa.doctor.generated.resources.blockFailed
import shifa.doctor.generated.resources.blockManagedElsewhere
import shifa.doctor.generated.resources.blockModeDay
import shifa.doctor.generated.resources.blockModeDays
import shifa.doctor.generated.resources.blockModeHours
import shifa.doctor.generated.resources.blockOverlapInfo
import shifa.doctor.generated.resources.blockOverlapWarning
import shifa.doctor.generated.resources.blockOverlapWillCancel
import shifa.doctor.generated.resources.blockReason
import shifa.doctor.generated.resources.blockReasonBreak
import shifa.doctor.generated.resources.blockReasonHint
import shifa.doctor.generated.resources.blockReasonSick
import shifa.doctor.generated.resources.blockReasonVacation
import shifa.doctor.generated.resources.blockTimeConfirm
import shifa.doctor.generated.resources.blockTimeSuccess
import shifa.doctor.generated.resources.blockTimeSuccessWithCancel
import shifa.doctor.generated.resources.blockTimeTitle
import shifa.doctor.generated.resources.blockedTime
import shifa.doctor.generated.resources.bookingEndTime
import shifa.doctor.generated.resources.cancel
import shifa.doctor.generated.resources.durationLabelShort
import shifa.doctor.generated.resources.emergencyBlock
import shifa.doctor.generated.resources.endTimeMustBeAfterStartTime
import shifa.doctor.generated.resources.fromDate
import shifa.doctor.generated.resources.ic_block_16
import shifa.doctor.generated.resources.ic_trash_22
import shifa.doctor.generated.resources.start
import shifa.doctor.generated.resources.toDate
import shifa.doctor.generated.resources.unblockConfirm
import shifa.doctor.generated.resources.unblockFailed
import shifa.doctor.generated.resources.unblockSuccess
import shifa.doctor.generated.resources.unblockTime
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.ui.emailcode.codeBanner
import shifa.doctor.ui.navigation.Notice
import shifa.doctor.ui.navigation.ShellNotice
import shifa.doctor.ui.profile.ProfileSheet
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.DialogTone
import shifa.doctor.ui.widgets.ShifaCheckboxRow
import shifa.doctor.ui.widgets.ShifaChip
import shifa.doctor.ui.widgets.ShifaDialogSheet
import shifa.doctor.ui.widgets.ShifaDropdown
import shifa.doctor.ui.widgets.ShifaInputField
import shifa.doctor.ui.widgets.ShifaPrimaryButton
import shifa.doctor.ui.widgets.ShifaSecondaryButton
import shifa.doctor.ui.widgets.ShifaSection
import shifa.doctor.ui.widgets.ShifaSettingsItem
import shifa.doctor.ui.widgets.ShifaSubHeader
import shifa.doctor.ui.widgets.ShifaTray
import shifa.doctor.utils.PracticeClock
import shifa.doctor.utils.displayTime
import shifa.doctor.utils.tr
import kotlin.time.Duration.Companion.milliseconds

enum class BlockMode { Day, Hours, Days }

/** Which date the inline month is picking in the "Several days" mode. */
enum class DateField { From, To }

data class BlockUiState(
    val clock: PracticeClock,
    // The calendar's selected day: Day and Hours block it, Several days start from it.
    val date: LocalDate,
    val mode: BlockMode = BlockMode.Day,
    val startMinute: Int = 9 * 60,
    val endMinute: Int = 17 * 60,
    val from: LocalDate = date,
    val to: LocalDate = date,
    val picking: DateField? = null,
    val month: LocalDate = date,
    val reason: String = "",
    val cancelOverlapping: Boolean = true,
    // The overlap preview: null while unknown, failed when it couldn't be read (never shown as 0).
    val count: Int? = null,
    val countFailed: Boolean = false,
    val saving: Boolean = false,
    val banner: CodeBanner? = null,
    val done: Boolean = false,
) {
    /**
     * End not after start, or a time the day doesn't have: in a DST gap (02:30 on a spring-forward day) the converted
     * instant lands elsewhere, so the block would not be what the doctor sees.
     */
    val timesInvalid: Boolean
        get() = mode == BlockMode.Hours && (endMinute <= startMinute || !exists(startMinute) || !exists(endMinute))

    private fun exists(minute: Int): Boolean {
        if (minute >= DAY) return true
        val back = wall(date, minute).toLocalDateTime(clock.zone)
        return back.date == date && back.hour * 60 + back.minute == minute
    }

    val datesInvalid: Boolean get() = mode == BlockMode.Days && to < from

    /** The UTC range in the practice zone; a whole day is 00:00 to the next 00:00 (Flutter stopped at 23:59). */
    val range: Pair<Instant, Instant>?
        get() =
            when {
                timesInvalid || datesInvalid -> null
                mode == BlockMode.Day -> start(date) to start(date.next())
                mode == BlockMode.Hours -> wall(date, startMinute) to wall(date, endMinute)
                else -> start(from) to start(to.next())
            }

    /** Every practice date the block covers. */
    val dates: List<LocalDate>
        get() =
            if (mode == BlockMode.Days) {
                generateSequence(from) { it.next() }.takeWhile { it <= to }.toList()
            } else {
                listOf(date)
            }

    private fun start(d: LocalDate) = d.atStartOfDayIn(clock.zone)

    // 24:00 is the next day's start; other wall times convert in the practice zone, so DST days keep their hours.
    private fun wall(
        d: LocalDate,
        minute: Int,
    ) = if (minute >= DAY) {
        start(
            d.next(),
        )
    } else {
        LocalDateTime(d, LocalTime(minute / 60, minute % 60)).toInstant(clock.zone)
    }
}

private fun LocalDate.next() = plus(DatePeriod(days = 1))

private const val DAY = 24 * 60
private const val STEP = 15

/** 00:00 … 24:00 in quarter hours, the same 24-hour labels as the rest of the app (no system 12-hour picker). */
internal val quarterHours = (0..DAY step STEP).toList()

/**
 * CAL-8 (flow 04/09, spec 03 §1.8): blocks the selected day, some hours of it, or several days, with an optional reason
 * and "Cancel overlapping appointments" (on by default). How many appointments the range hits is read 300 ms after the
 * last change; a failed read says so instead of showing nothing. Success re-reads the touched days (four at a time) and
 * the shell says how many were cancelled.
 */
class BlockViewModel(
    date: LocalDate,
    clock: PracticeClock,
    private val calendar: CalendarRepository = calendarRepository,
    private val blocks: BlocksRepository = blocksRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(BlockUiState(clock, date))
    val state: StateFlow<BlockUiState> = _state.asStateFlow()
    private var counting: Job? = null

    init {
        recount()
    }

    /** A field changed: the preview is read again once the doctor stops changing things. */
    fun edit(change: (BlockUiState) -> BlockUiState) {
        if (_state.value.saving) return
        val before = _state.value.range
        _state.update(change)
        if (_state.value.range != before) recount()
    }

    /** The inline month picked a date; From after To drags To along (and To before From drags From). */
    fun pickDate(day: LocalDate) =
        edit {
            when (it.picking) {
                DateField.From -> it.copy(from = day, to = maxOf(day, it.to), picking = null)
                DateField.To -> it.copy(to = day, from = minOf(day, it.from), picking = null)
                null -> it
            }
        }

    private fun recount() {
        counting?.cancel()
        val range = _state.value.range
        _state.update { it.copy(count = null, countFailed = false) }
        if (range == null) return
        counting =
            viewModelScope.launch {
                delay(DEBOUNCE)
                val result = blocks.overlapping(range.first, range.second)
                _state.update {
                    if (it.range != range) it else it.copy(count = result.getOrNull(), countFailed = result.isFailure)
                }
            }
    }

    fun block() {
        val s = _state.value
        val range = s.range ?: return
        if (s.saving) return
        _state.update { it.copy(saving = true, banner = null) }
        viewModelScope.launch {
            val result = blocks.create(range.first, range.second, s.reason.trim().ifEmpty { null }, s.cancelOverlapping)
            // The covered days already in the calendar are read again (others when shown); on failure too, the block
            // may exist with its answer lost.
            calendar.changed(s.clock, s.dates.first(), *calendar.knownDays(s.dates).toTypedArray())
            result.fold(
                onSuccess = { cancelled ->
                    ShellNotice.pending.value =
                        if (cancelled > 0) {
                            Notice(Res.string.blockTimeSuccessWithCancel, true, listOf(cancelled.toString()))
                        } else {
                            Notice(Res.string.blockTimeSuccess, true)
                        }
                    _state.update { it.copy(saving = false, done = true) }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(
                            saving = false,
                            banner = CodeBanner(e.toUserMessage(Res.string.blockFailed), requestId = e.requestId),
                        )
                    }
                },
            )
        }
    }

    private companion object {
        val DEBOUNCE = 300.milliseconds
    }
}

/** Hosted by the calendar over its selected day; [opened] gives each open a fresh form. */
@Composable
fun BlockTimeSheet(
    date: LocalDate,
    opened: Long,
    onClose: () -> Unit,
) {
    val viewModel =
        viewModel(key = "block-$date-$opened") {
            BlockViewModel(date, PracticeClock(profileRepository.me.value?.profile?.timeZone))
        }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onClose() }
    BlockTimeContent(
        state,
        BlockActions(viewModel::edit, viewModel::pickDate, viewModel::block, {
            if (!state.saving) onClose()
        }),
    )
}

class BlockActions(
    val edit: ((BlockUiState) -> BlockUiState) -> Unit = {},
    val pickDate: (LocalDate) -> Unit = {},
    val block: () -> Unit = {},
    val close: () -> Unit = {},
)

@Composable
fun BlockTimeContent(
    state: BlockUiState,
    actions: BlockActions,
) = ProfileSheet(actions.close) { BlockTimeTray(state, actions) }

/** The drawn tray (previews and screenshots render it without the sheet). */
@Composable
fun BlockTimeTray(
    state: BlockUiState,
    actions: BlockActions,
) {
    ShifaTray(tr(Res.string.blockTimeTitle), actions.close) {
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md),
        ) { BlockForm(state, actions) }
        ShifaPrimaryButton(
            tr(Res.string.blockTimeConfirm),
            actions.block,
            Modifier.fillMaxWidth(),
            enabled = state.range != null && !state.saving,
            loading = state.saving,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BlockForm(
    state: BlockUiState,
    actions: BlockActions,
) {
    codeBanner(state.banner) { actions.edit { it.copy(banner = null) } }?.invoke()
    Row(horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6)) {
        listOf(
            BlockMode.Day to Res.string.blockModeDay,
            BlockMode.Hours to Res.string.blockModeHours,
            BlockMode.Days to Res.string.blockModeDays,
        ).forEach {
                (mode, label) ->
            ShifaChip(tr(label), state.mode == mode, { actions.edit { it.copy(mode = mode, picking = null) } })
        }
    }
    when (state.mode) {
        BlockMode.Day ->
            Text(
                longDate(state.date),
                style = ShifaTextStyles.dialogBody(),
                color = ShifaColors.Text.Primary,
            )
        BlockMode.Hours -> Hours(state, actions)
        BlockMode.Days -> Days(state, actions)
    }
    // Reason: a few common ones to tap, or free text (Flutter prefilled "Emergency", wrong for a lunch break).
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6),
        verticalArrangement = Arrangement.spacedBy(ShifaSize.S6),
    ) {
        listOf(
            Res.string.blockReasonBreak,
            Res.string.blockReasonVacation,
            Res.string.blockReasonSick,
            Res.string.emergencyBlock,
        )
            .forEach { res -> ReasonChip(res, state, actions) }
    }
    ShifaInputField(
        state.reason,
        { v -> actions.edit { it.copy(reason = v) } },
        tr(Res.string.blockReason),
        Modifier.fillMaxWidth(),
        supportingText = tr(Res.string.blockReasonHint),
        enabled = !state.saving,
    )
    ShifaCheckboxRow(
        tr(Res.string.blockCancelOverlapping),
        ToggleableState(state.cancelOverlapping),
        { actions.edit { it.copy(cancelOverlapping = !it.cancelOverlapping) } },
    )
    Text(
        tr(Res.string.blockCancelOverlappingHint),
        style = ShifaTextStyles.metaSmall(),
        color = ShifaColors.Text.Secondary,
    )
    OverlapLine(state)
}

@Composable
private fun ReasonChip(
    res: StringResource,
    state: BlockUiState,
    actions: BlockActions,
) {
    val label = tr(res)
    ShifaChip(label, state.reason.trim() == label, { actions.edit { it.copy(reason = label) } })
}

/** The live preview: red when they will be cancelled, amber when they stay, a plain note when it couldn't be read. */
@Composable
private fun OverlapLine(state: BlockUiState) {
    val count = state.count
    val (text, color) =
        when {
            state.range == null -> return
            state.countFailed -> tr(Res.string.blockCountFailed) to ShifaColors.Text.Secondary
            count == null || count == 0 -> return
            state.cancelOverlapping -> tr(Res.string.blockOverlapWillCancel, count.toString()) to ShifaColors.Text.Error
            else -> tr(Res.string.blockOverlapWarning) to ShifaColors.Text.Warning
        }
    Text(text, style = ShifaTextStyles.dialogBody(), color = color)
}

@Composable
private fun Hours(
    state: BlockUiState,
    actions: BlockActions,
) = Column(verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md)) {
    var menu by remember { mutableStateOf<Boolean?>(null) } // true = start, false = end

    fun label(m: Int) = if (m >= DAY) "24:00" else displayTime(LocalTime(m / 60, m % 60))
    val starts = quarterHours.dropLast(1)
    val ends = quarterHours.drop(1)
    FieldLabel(tr(Res.string.start))
    ShifaDropdown(
        tr(Res.string.start),
        starts.map(::label),
        label(state.startMinute),
        onSelect = { i ->
            actions.edit { it.copy(startMinute = starts[i]) }
            menu = null
        },
        expanded = menu == true,
        onExpandedChange = { menu = if (it && !state.saving) true else null },
        modifier = Modifier.fillMaxWidth(),
    )
    FieldLabel(tr(Res.string.bookingEndTime))
    ShifaDropdown(
        tr(Res.string.bookingEndTime),
        ends.map(::label),
        label(state.endMinute),
        onSelect = { i ->
            actions.edit { it.copy(endMinute = ends[i]) }
            menu = null
        },
        expanded = menu == false,
        onExpandedChange = { menu = if (it && !state.saving) false else null },
        modifier = Modifier.fillMaxWidth(),
        isError = state.timesInvalid,
    )
    if (state.timesInvalid) {
        Text(
            tr(Res.string.endTimeMustBeAfterStartTime),
            style = ShifaTextStyles.metaSmall(),
            color = ShifaColors.Text.Error,
        )
    }
}

@Composable
private fun Days(
    state: BlockUiState,
    actions: BlockActions,
) = Column {
    ShifaSettingsItem(
        tr(Res.string.fromDate),
        {
            actions.edit {
                it.copy(
                    picking = if (it.picking == DateField.From) null else DateField.From,
                    month = it.from,
                )
            }
        },
        subtitle = longDate(state.from),
    )
    ShifaSettingsItem(
        tr(Res.string.toDate),
        { actions.edit { it.copy(picking = if (it.picking == DateField.To) null else DateField.To, month = it.to) } },
        subtitle = longDate(state.to),
        divider = false,
    )
    if (state.datesInvalid) {
        Text(
            tr(Res.string.blockEndDateMustBeOnOrAfterStart),
            style = ShifaTextStyles.metaSmall(),
            color = ShifaColors.Text.Error,
        )
    }
    state.picking?.let { field ->
        // The calendar's own month, inline; days before today are closed (04/09 §4 target).
        MonthPicker(
            state.month,
            if (field == DateField.From) state.from else state.to,
            state.clock.today(),
            { null },
            { d -> if (d >= state.clock.today()) actions.pickDate(d) },
            { delta -> actions.edit { it.copy(month = it.month.plus(DatePeriod(months = delta))) } },
        )
    }
}

/** A dropdown shows only its value once set: the caption above says which one it is. */
@Composable
internal fun FieldLabel(text: String) =
    Text(text, style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Secondary)

// ---------------------------------------------------------------------------------------------------------------------
// The blocked row's panel (flow 04/09 B).

data class BlockPanelUiState(
    val entry: CalendarEntry.Blocked,
    val clock: PracticeClock,
    val asking: Boolean = false,
    val busy: Boolean = false,
    val banner: CodeBanner? = null,
    val done: Boolean = false,
)

/** CAL-8: the block's time, its reason and "Remove block" (confirmed); a block without an id can't be removed here. */
class BlockPanelViewModel(
    entry: CalendarEntry.Blocked,
    clock: PracticeClock,
    private val calendar: CalendarRepository = calendarRepository,
    private val blocks: BlocksRepository = blocksRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(BlockPanelUiState(entry, clock))
    val state: StateFlow<BlockPanelUiState> = _state.asStateFlow()

    fun ui(change: (BlockPanelUiState) -> BlockPanelUiState) = _state.update(change)

    fun remove() {
        val s = _state.value
        val id = s.entry.blockId ?: return
        if (s.busy) return
        _state.update { it.copy(busy = true, banner = null) }
        viewModelScope.launch {
            val result = blocks.remove(id)
            // Every cached day showing a slice of this block, and the tapped one, read again.
            val cache = calendar.state.value
            val showing = cache.days.filterValues { day -> day.any { it is CalendarEntry.Blocked && it.blockId == id } }
            // Plus days still being read (their answer may predate the removal).
            val days =
                showing.keys.map { it.date } + calendar.pending().map { it.date } +
                    s.clock.local(
                        s.entry.start,
                    ).date
            calendar.changed(s.clock, *days.distinct().toTypedArray())
            result.fold(
                onSuccess = {
                    ShellNotice.pending.value = Notice(Res.string.unblockSuccess, true)
                    _state.update { it.copy(busy = false, asking = false, done = true) }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(
                            busy = false,
                            asking = false,
                            banner = CodeBanner(e.toUserMessage(Res.string.unblockFailed), requestId = e.requestId),
                        )
                    }
                },
            )
        }
    }
}

@Composable
fun BlockPanelScreen(
    entry: CalendarEntry.Blocked,
    opened: Long,
    onClose: () -> Unit,
) {
    val viewModel =
        viewModel(key = "blocked-${entry.blockId}-${entry.start}-$opened") {
            BlockPanelViewModel(entry, PracticeClock(profileRepository.me.value?.profile?.timeZone))
        }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onClose() }
    BlockPanelContent(state, onClose, viewModel::ui, viewModel::remove)
}

@Composable
fun BlockPanelContent(
    state: BlockPanelUiState,
    onClose: () -> Unit,
    ui: ((BlockPanelUiState) -> BlockPanelUiState) -> Unit = {},
    remove: () -> Unit = {},
) {
    val e = state.entry
    val start = state.clock.local(e.start)
    val end = state.clock.local(e.end)
    val allDay = start.time == LocalTime(0, 0) && end.date > start.date
    Column(Modifier.fillMaxSize().background(ShifaColors.Bg.Canvas)) {
        ShifaSubHeader(tr(Res.string.blockTimeTitle), Modifier.statusBarsPadding(), onBack = onClose)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(ShifaSpacing.Inset.Md),
            verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Stack.Md),
        ) {
            codeBanner(state.banner) { ui { it.copy(banner = null) } }?.invoke()
            ShifaSection(null) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ShifaSize.S4)) {
                    Text(
                        if (allDay) {
                            "${longDate(
                                start.date,
                            )} · ${tr(
                                Res.string.allDay,
                            )}"
                        } else {
                            "${longDate(start.date)} · ${displayTime(start.time)} – ${displayTime(end.time)}"
                        },
                        style = ShifaTextStyles.panelTitle(),
                        color = ShifaColors.Text.Primary,
                    )
                    Text(
                        "${tr(Res.string.durationLabelShort)}: ${duration((e.end - e.start).inWholeMinutes.toInt())}",
                        style = ShifaTextStyles.metaSmall(),
                        color = ShifaColors.Text.Secondary,
                    )
                }
            }
            // The board's error-tinted card: the reason and what a block means.
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(ShifaColors.Bg.ErrorSubtle, ShifaRadius.ControlMd)
                    .border(ShifaSize.S1, ShifaColors.Border.Error, ShifaRadius.ControlMd)
                    .padding(ShifaSpacing.Inset.Md),
                horizontalArrangement = Arrangement.spacedBy(ShifaSize.S12),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(painterResource(Res.drawable.ic_block_16), null, tint = ShifaColors.Icon.Error)
                Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S4)) {
                    Text(
                        e.reason?.takeIf { it.isNotBlank() } ?: tr(Res.string.blockedTime),
                        style = ShifaTextStyles.panelTitle(),
                        color = ShifaColors.Text.Primary,
                    )
                    Text(
                        tr(Res.string.blockOverlapInfo),
                        style = ShifaTextStyles.dialogBody(),
                        color = ShifaColors.Text.Secondary,
                    )
                }
            }
        }
        Column(
            Modifier.fillMaxWidth().background(
                ShifaColors.Bg.Surface,
            ).navigationBarsPadding().padding(ShifaSpacing.Inset.Md),
        ) {
            if (e.blockId != null) {
                ShifaSecondaryButton(
                    tr(Res.string.unblockTime),
                    { ui { it.copy(asking = true) } },
                    Modifier.fillMaxWidth(),
                    enabled = !state.busy,
                )
            } else {
                // A block without an id (made elsewhere) has nothing to delete: say so instead of a dead button.
                Text(
                    tr(Res.string.blockManagedElsewhere),
                    style = ShifaTextStyles.metaSmall(),
                    color = ShifaColors.Text.Secondary,
                )
            }
        }
    }
    if (state.asking) {
        ProfileSheet({ if (!state.busy) ui { it.copy(asking = false) } }) {
            ShifaDialogSheet(
                tr(Res.string.unblockTime),
                tr(Res.string.unblockTime),
                remove,
                body = tr(Res.string.unblockConfirm),
                tone = DialogTone.Destructive,
                icon = Res.drawable.ic_trash_22,
                confirmLoading = state.busy,
                cancelText = tr(Res.string.cancel),
                onCancel = { if (!state.busy) ui { it.copy(asking = false) } },
            )
        }
    }
}
