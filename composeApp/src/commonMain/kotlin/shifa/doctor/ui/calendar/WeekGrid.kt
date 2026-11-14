package shifa.doctor.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import shifa.doctor.data.calendar.DayKey
import shifa.doctor.data.model.CalendarEntry
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.blocked
import shifa.doctor.generated.resources.calendarDayView
import shifa.doctor.generated.resources.calendarMonthView
import shifa.doctor.generated.resources.calendarWeekView
import shifa.doctor.generated.resources.dayAppointments
import shifa.doctor.generated.resources.dayBlocked
import shifa.doctor.generated.resources.ic_chev_l_16
import shifa.doctor.generated.resources.ic_chev_r_16
import shifa.doctor.generated.resources.moreCount
import shifa.doctor.generated.resources.nextDay
import shifa.doctor.generated.resources.nextMonth
import shifa.doctor.generated.resources.nextWeek
import shifa.doctor.generated.resources.previousDay
import shifa.doctor.generated.resources.previousMonth
import shifa.doctor.generated.resources.previousWeek
import shifa.doctor.generated.resources.today
import shifa.doctor.generated.resources.unknownPatient
import shifa.doctor.ui.schedule.dayName
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaDimens
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.SegmentedTabs
import shifa.doctor.ui.widgets.ShifaButtonSize
import shifa.doctor.ui.widgets.ShifaIconButton
import shifa.doctor.ui.widgets.ShifaIconButtonVariant
import shifa.doctor.ui.widgets.ShifaSecondaryButton
import shifa.doctor.ui.widgets.ShifaSkeleton
import shifa.doctor.ui.widgets.TabItem
import shifa.doctor.utils.displayDate
import shifa.doctor.utils.displayTime
import shifa.doctor.utils.tr

/** Monday of [date]'s week (the board's week starts on Monday in every language). */
internal fun weekStart(date: LocalDate): LocalDate = date.plus(DatePeriod(days = -(date.dayOfWeek.ordinal)))

internal fun weekOf(date: LocalDate): List<LocalDate> = (0..6).map { weekStart(date).plus(DatePeriod(days = it)) }

/**
 * The hours the grid spans: 08:00–18:00 at least, widened to whole hours around every row of the week (an all-day block
 * doesn't widen it), and never less than four hours (board DaCalendar `CalendarWeek`).
 */
internal fun gridHours(
    week: List<List<CalendarEntry>>,
    minuteOf: (kotlinx.datetime.Instant) -> Int,
): IntRange {
    var lo = DAY_FROM
    var hi = DAY_TO
    week.flatten().forEach { e ->
        val s = minuteOf(e.start)
        val end = minuteOf(e.end).let { if (it <= s) MINUTES_PER_DAY else it }
        if (e is CalendarEntry.Blocked && s == 0 && end >= MINUTES_PER_DAY) return@forEach
        lo = minOf(lo, s / MINUTES_PER_HOUR * MINUTES_PER_HOUR)
        hi = maxOf(hi, (end + MINUTES_PER_HOUR - 1) / MINUTES_PER_HOUR * MINUTES_PER_HOUR)
    }
    lo = lo.coerceAtLeast(0)
    hi = hi.coerceAtMost(MINUTES_PER_DAY).coerceAtLeast(lo + MIN_SPAN)
    return lo until hi
}

/** The calendar's views on wide panes (design review D9: Day, Week and Month, as calendars doctors already know). */
enum class CalendarView { Day, Week, Month }

/**
 * The calendar's bar on wide panes (design review D9, the familiar calendar pattern): Today, previous and next, what is
 * shown ("16 – 22 November 2026"), and the Day / Week / Month switch at the right. Previous and next move by the view's
 * own step.
 */
@Composable
internal fun CalendarNav(
    state: CalendarUiState,
    actions: CalendarActions,
    // Colleagues shown (CAL-10 A): their sections live in the day view, so there is nothing to switch.
    switchable: Boolean,
) = Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8)) {
    val view = if (switchable) state.view else CalendarView.Day
    val step =
        when (view) {
            CalendarView.Day -> DatePeriod(days = 1)
            CalendarView.Week -> DatePeriod(days = 7)
            CalendarView.Month -> DatePeriod(months = 1)
        }
    val (previous, next) =
        when (view) {
            CalendarView.Day -> Res.string.previousDay to Res.string.nextDay
            CalendarView.Week -> Res.string.previousWeek to Res.string.nextWeek
            CalendarView.Month -> Res.string.previousMonth to Res.string.nextMonth
        }
    ShifaSecondaryButton(
        tr(Res.string.today),
        { actions.select(state.today) },
        enabled = state.selected != state.today,
        size = ShifaButtonSize.Compact,
    )
    // Within the calendar's years, as the month card's arrows.
    val before = state.selected.minus(step)
    val after = state.selected.plus(step)
    ShifaIconButton(
        Res.drawable.ic_chev_l_16,
        tr(previous),
        { actions.select(before) },
        variant = ShifaIconButtonVariant.Surface,
        enabled = before in MONTH_RANGE,
    )
    ShifaIconButton(
        Res.drawable.ic_chev_r_16,
        tr(next),
        { actions.select(after) },
        variant = ShifaIconButtonVariant.Surface,
        enabled = after in MONTH_RANGE,
    )
    Text(
        when (view) {
            CalendarView.Day -> "${dayName(state.selected.dayOfWeek.isoDayNumber)}, ${longDate(state.selected)}"
            CalendarView.Week ->
                weekOf(state.selected).let { "${displayDate(it.first())} – ${displayDate(it.last())}" }
            CalendarView.Month -> monthTitle(state.selected)
        },
        Modifier.weight(1f).padding(horizontal = ShifaSpacing.Inset.Xs).semantics { heading() },
        style = ShifaTextStyles.sectionHeading(),
        color = ShifaColors.Text.Primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    if (switchable) {
        SegmentedTabs(
            listOf(
                TabItem(tr(Res.string.calendarDayView)),
                TabItem(tr(Res.string.calendarWeekView)),
                TabItem(tr(Res.string.calendarMonthView)),
            ),
            view.ordinal,
            { i -> actions.ui { it.copy(view = CalendarView.entries[i]) } },
        )
    }
}

/**
 * CAL-5 (board DaCalendar `CalendarWeek`, web and tablet widths): [dates] (the selected day, or its week) as a time
 * grid: the hours on the left with a line across at each, a column per day (today's head is the teal disc; a tap on a
 * head opens that day), appointments, blocks and free slots placed by their practice time, cancelled ones struck
 * through, and the now-line on today. A tap opens the row as the agenda does. The agenda's filters apply. The doctor's
 * own calendar only (colleagues stay in the agenda).
 */
@Composable
internal fun TimeGrid(
    state: CalendarUiState,
    actions: CalendarActions,
    dates: List<LocalDate>,
) {
    val clock = state.clock
    // Null: the day hasn't been read (or its read failed). It is drawn as loading, never as a day with nothing in it.
    val read = dates.map { state.cache.days[DayKey(it, state.scope?.doctorId)] }
    val days = read.map { it.orEmpty().filter(state.filter::shows) }
    val minuteOf = { t: kotlinx.datetime.Instant -> clock.local(t).time.let { it.hour * MINUTES_PER_HOUR + it.minute } }
    val hours = gridHours(days, minuteOf)
    val line = ShifaColors.Border.Default
    Column(
        Modifier
            .fillMaxWidth()
            .clip(ShifaRadius.ControlLg)
            .background(ShifaColors.Bg.Surface)
            .border(ShifaSize.S1, line, ShifaRadius.ControlLg),
    ) {
        // The heads: weekday and date; a tap opens that day in the day view.
        Row(Modifier.fillMaxWidth().padding(vertical = ShifaSize.S8)) {
            Box(Modifier.width(GUTTER))
            dates.forEach { d ->
                val today = d == state.today
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(role = Role.Button) {
                            actions.select(d)
                            actions.ui { it.copy(view = CalendarView.Day) }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(ShifaSize.S2),
                ) {
                    Text(
                        tr(WEEKDAYS[d.dayOfWeek.ordinal]).take(3),
                        style = ShifaTextStyles.caption(),
                        color = if (today) ShifaColors.Text.Brand else ShifaColors.Text.Secondary,
                    )
                    Box(
                        Modifier
                            .size(ShifaSize.S32)
                            .background(
                                if (today) ShifaColors.Interactive.Primary else Color.Transparent,
                                ShifaRadius.Full,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            d.dayOfMonth.toString(),
                            style = ShifaTextStyles.buttonCompact(),
                            color = if (today) ShifaColors.Interactive.OnPrimary else ShifaColors.Text.Primary,
                        )
                    }
                }
            }
        }
        val height = HOUR * ((hours.last + 1 - hours.first) / MINUTES_PER_HOUR)
        val rows = (hours.last + 1 - hours.first) / MINUTES_PER_HOUR
        Row(
            Modifier.fillMaxWidth().height(height).drawBehind {
                // One line across at each hour, from the gutter's edge.
                repeat(rows) { i ->
                    val y = i * HOUR.toPx()
                    drawLine(line, Offset(GUTTER.toPx(), y), Offset(size.width, y), ShifaSize.S1.toPx())
                }
            },
        ) {
            // The hour gutter: each label sits on its line.
            Box(Modifier.width(GUTTER).height(height)) {
                (hours.first until hours.last + 1 step MINUTES_PER_HOUR).forEach { m ->
                    if (m == hours.first) return@forEach
                    Text(
                        "${(m / MINUTES_PER_HOUR).toString().padStart(2, '0')}:00",
                        Modifier
                            .fillMaxWidth()
                            .padding(end = ShifaSize.S8)
                            .offset(y = HOUR * ((m - hours.first) / MINUTES_PER_HOUR) - ShifaSize.S8),
                        style = ShifaTextStyles.caption(),
                        color = ShifaColors.Text.Tertiary,
                        textAlign = TextAlign.End,
                    )
                }
            }
            dates.forEachIndexed { i, d ->
                DayColumn(
                    days[i],
                    read[i] != null,
                    hours,
                    minuteOf,
                    // In a week, the chosen day is tinted; a single day needs no mark.
                    selected = dates.size > 1 && d == state.selected,
                    nowMinute = if (d == state.today) minuteOf(clock.nowInstant()) else null,
                    open = actions.open,
                    modifier = Modifier.weight(1f).height(height),
                )
            }
        }
    }
}

/**
 * The month on wide panes (design review D9): a week per row, each day a cell with its date (today's is the teal disc),
 * its first appointments and "+N more", a blocked day tinted. A tap opens the day in the day view. Reads the same cache
 * as the month card's shading; a day not read yet shows nothing.
 */
@Composable
internal fun MonthGrid(
    state: CalendarUiState,
    actions: CalendarActions,
) {
    val line = ShifaColors.Border.Default
    Column(
        Modifier
            .fillMaxWidth()
            .clip(ShifaRadius.ControlLg)
            .background(ShifaColors.Bg.Surface)
            .border(ShifaSize.S1, line, ShifaRadius.ControlLg),
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = ShifaSize.S8)) {
            WEEKDAYS.forEach {
                Text(
                    tr(it).take(3),
                    Modifier.weight(1f),
                    style = ShifaTextStyles.caption(),
                    color = ShifaColors.Text.Secondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
        monthCells(state.selected).chunked(7).forEach { week ->
            HorizontalDivider(thickness = ShifaSize.S1, color = line)
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                week.forEachIndexed { i, date ->
                    if (i > 0) VerticalDivider(thickness = ShifaSize.S1, color = line)
                    Box(Modifier.weight(1f).defaultMinSize(minHeight = ShifaSize.S112)) {
                        if (date != null) MonthCell(date, state, actions)
                    }
                }
                repeat(7 - week.size) {
                    VerticalDivider(thickness = ShifaSize.S1, color = line)
                    Box(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MonthCell(
    date: LocalDate,
    state: CalendarUiState,
    actions: CalendarActions,
) {
    val entries = state.cache.days[DayKey(date, state.scope?.doctorId)].orEmpty().filter(state.filter::shows)
    val visits = entries.filterIsInstance<CalendarEntry.Appointment>().filter { !it.cancelled }.sortedBy { it.start }
    val occupancy = state.occupancy(date)
    val today = date == state.today
    val label =
        listOfNotNull(
            displayDate(date),
            visits.size.takeIf { it > 0 }?.let { tr(Res.string.dayAppointments, it) },
            tr(Res.string.dayBlocked).takeIf { occupancy?.fullDayBlock == true },
        ).joinToString(", ")
    Column(
        Modifier
            .fillMaxSize()
            .background(if (occupancy?.fullDayBlock == true) ShifaColors.Bg.ErrorSubtle else Color.Transparent)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(role = Role.Button) {
                actions.select(date)
                actions.ui { it.copy(view = CalendarView.Day) }
            }.semantics { contentDescription = label }
            .padding(ShifaSize.S6),
        verticalArrangement = Arrangement.spacedBy(ShifaSize.S4),
    ) {
        Box(
            Modifier
                .size(ShifaSize.S28)
                .background(if (today) ShifaColors.Interactive.Primary else Color.Transparent, ShifaRadius.Full),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                date.dayOfMonth.toString(),
                style = ShifaTextStyles.buttonCompact(),
                color =
                    when {
                        today -> ShifaColors.Interactive.OnPrimary
                        date < state.today -> ShifaColors.Text.Tertiary
                        else -> ShifaColors.Text.Primary
                    },
            )
        }
        visits.take(MONTH_CELL_ROWS).forEach { v ->
            Text(
                "${displayTime(
                    state.clock.local(v.start).time,
                )} ${shortName(v.patientName) ?: tr(Res.string.unknownPatient)}",
                Modifier
                    .fillMaxWidth()
                    .background(ShifaColors.Bg.InfoSubtle, ShifaRadius.Xs)
                    .padding(horizontal = ShifaSize.S4, vertical = ShifaSize.S2),
                style = ShifaTextStyles.caption(),
                color = ShifaColors.Text.Info,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (visits.size > MONTH_CELL_ROWS) {
            Text(
                tr(Res.string.moreCount, visits.size - MONTH_CELL_ROWS),
                style = ShifaTextStyles.caption(),
                color = ShifaColors.Text.Secondary,
            )
        }
    }
}

private const val MONTH_CELL_ROWS = 2

@Composable
private fun DayColumn(
    entries: List<CalendarEntry>,
    loaded: Boolean,
    hours: IntRange,
    minuteOf: (kotlinx.datetime.Instant) -> Int,
    selected: Boolean,
    nowMinute: Int?,
    open: (CalendarEntry) -> Unit,
    modifier: Modifier,
    separator: Color = ShifaColors.Border.Default,
) = BoxWithConstraints(
    modifier
        .background(if (selected) ShifaColors.Bg.Selected else Color.Transparent)
        .drawBehind {
            drawLine(separator, Offset.Zero, Offset(0f, size.height), ShifaSize.S1.toPx())
        },
) {
    val from = hours.first
    val to = hours.last + 1

    fun y(minute: Int): Dp = HOUR * ((minute.coerceIn(from, to) - from) / MINUTES_PER_HOUR.toFloat())
    if (!loaded) ShifaSkeleton(Modifier.fillMaxSize().padding(ShifaSize.S2), ShifaRadius.Xs)
    val lanes =
        lanes(
            entries.map {
                    e ->
                minuteOf(e.start).let { s -> s to minuteOf(e.end).let { if (it <= s) MINUTES_PER_DAY else it } }
            },
        )
    entries.forEachIndexed { index, e ->
        val s = minuteOf(e.start)
        val end = minuteOf(e.end).let { if (it <= s) MINUTES_PER_DAY else it }
        if (end <= from || s >= to) return@forEachIndexed
        val (lane, of) = lanes[index]
        val top = y(s)
        val tall = (y(end) - top - ShifaSize.S2).coerceAtLeast(MIN_EVENT)
        val (bg, ink) =
            when (e) {
                is CalendarEntry.Appointment ->
                    if (e.cancelled) {
                        ShifaColors.Bg.Canvas to ShifaColors.Text.Tertiary
                    } else {
                        ShifaColors.Bg.InfoSubtle to ShifaColors.Text.Info
                    }
                is CalendarEntry.FreeSlot -> ShifaColors.Bg.SuccessSubtle to ShifaColors.Text.Brand
                is CalendarEntry.Blocked -> ShifaColors.Bg.ErrorSubtle to ShifaColors.Text.Error
            }
        val title =
            when (e) {
                is CalendarEntry.Appointment -> shortName(e.patientName) ?: tr(Res.string.unknownPatient)
                is CalendarEntry.FreeSlot -> "+ ${displayTime(clockTime(s))}"
                is CalendarEntry.Blocked -> e.reason?.takeIf { it.isNotBlank() } ?: tr(Res.string.blocked)
            }
        val time = "${displayTime(clockTime(s))}–${displayTime(clockTime(end))}"
        Column(
            Modifier
                // Rows at the same time (two locations' slots) sit side by side, each one reachable.
                .offset(x = maxWidth * (lane.toFloat() / of), y = top)
                .width(maxWidth / of)
                .padding(horizontal = ShifaSize.S2)
                .height(tall)
                .background(bg, ShifaRadius.Xs)
                .clickable(role = Role.Button) { open(e) }
                .semantics { contentDescription = "$title, $time" }
                .padding(horizontal = ShifaSize.S4, vertical = ShifaSize.S2),
        ) {
            Text(
                title,
                style = ShifaTextStyles.caption(),
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = TextDecoration.LineThrough.takeIf { e is CalendarEntry.Appointment && e.cancelled },
            )
            if (tall >= TWO_LINES && e !is CalendarEntry.FreeSlot) {
                Text(time, style = ShifaTextStyles.caption(), color = ink, maxLines = 1)
            }
        }
    }
    nowMinute?.takeIf { it in from until to }?.let {
        Box(Modifier.offset(y = y(it)).fillMaxWidth().height(ShifaSize.S2).background(ShifaColors.Icon.Error))
    }
}

/**
 * Side-by-side lanes for rows that overlap in time: for each [start, end) in [spans], its lane and how many lanes its
 * group of overlapping rows needs. Rows that don't overlap anything get (0, 1), the full width.
 */
internal fun lanes(spans: List<Pair<Int, Int>>): List<Pair<Int, Int>> {
    val result = MutableList(spans.size) { 0 to 1 }
    val order = spans.indices.sortedWith(compareBy({ spans[it].first }, { spans[it].second }))
    var group = mutableListOf<Int>()
    var groupEnd = Int.MIN_VALUE
    val ends = mutableListOf<Int>() // the end of the last row in each lane of the group

    fun close() {
        group.forEach { result[it] = result[it].first to ends.size.coerceAtLeast(1) }
        group = mutableListOf()
        ends.clear()
    }
    order.forEach { i ->
        val (start, end) = spans[i]
        if (start >= groupEnd) close()
        val lane = ends.indexOfFirst { it <= start }.takeIf { it >= 0 } ?: ends.size.also { ends.add(end) }
        ends[lane] = end
        result[i] = lane to 1
        group.add(i)
        groupEnd = maxOf(groupEnd, end)
    }
    close()
    return result
}

/** "M. Tursunova" (the board's short name in a narrow column). */
internal fun shortName(name: String?): String? {
    val parts = name?.trim()?.split(Regex("\\s+"))?.filter { it.isNotEmpty() }.orEmpty()
    return when {
        parts.isEmpty() -> null
        parts.size == 1 -> parts[0]
        else -> "${parts[0].first()}. ${parts.drop(1).joinToString(" ")}"
    }
}

// Midnight at the end of a day (minute 1440) reads 00:00.
private fun clockTime(minute: Int) =
    (minute % MINUTES_PER_DAY).let { kotlinx.datetime.LocalTime(it / MINUTES_PER_HOUR, it % MINUTES_PER_HOUR) }

private const val MINUTES_PER_HOUR = 60
private const val MINUTES_PER_DAY = 24 * 60
private const val DAY_FROM = 8 * 60
private const val DAY_TO = 18 * 60
private const val MIN_SPAN = 4 * 60

private val HOUR = ShifaDimens.CalendarHour
private val GUTTER = ShifaDimens.CalendarGutter
private val MIN_EVENT = ShifaDimens.CalendarEventMin
private val TWO_LINES = ShifaDimens.CalendarEventTwoLines
