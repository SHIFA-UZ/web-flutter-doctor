package shifa.doctor.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import shifa.doctor.data.calendar.DayKey
import shifa.doctor.data.model.CalendarEntry
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.blocked
import shifa.doctor.generated.resources.calendarDayView
import shifa.doctor.generated.resources.calendarWeekView
import shifa.doctor.generated.resources.ic_chev_l_16
import shifa.doctor.generated.resources.ic_chev_r_16
import shifa.doctor.generated.resources.nextWeek
import shifa.doctor.generated.resources.previousWeek
import shifa.doctor.generated.resources.unknownPatient
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaDimens
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.ShifaChip
import shifa.doctor.ui.widgets.ShifaIconButton
import shifa.doctor.ui.widgets.ShifaIconButtonVariant
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

/** "Day | Week" (board `views`, wide panes only). */
@Composable
internal fun ViewToggle(
    week: Boolean,
    onWeek: (Boolean) -> Unit,
) = Row(horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6)) {
    ShifaChip(tr(Res.string.calendarDayView), !week, { onWeek(false) })
    ShifaChip(tr(Res.string.calendarWeekView), week, { onWeek(true) })
}

/**
 * CAL-5 (board DaCalendar `CalendarWeek`, web and tablet widths): the selected day's week as a time grid: the hours on
 * the left, a column per day (today and the selected day marked; a tap on the head opens that day), appointments,
 * blocks and free slots placed by their practice time, cancelled ones struck through, and the now-line on today. A tap
 * opens the row as the agenda does. The agenda's filters apply. The doctor's own calendar only (colleagues stay in the
 * day view).
 */
@Composable
internal fun WeekGrid(
    state: CalendarUiState,
    actions: CalendarActions,
) = Column(verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md)) {
    val dates = weekOf(state.selected)
    val clock = state.clock
    val days = dates.map { state.cache.days[DayKey(it, state.scope?.doctorId)].orEmpty().filter(state.filter::shows) }
    val minuteOf = { t: kotlinx.datetime.Instant -> clock.local(t).time.let { it.hour * MINUTES_PER_HOUR + it.minute } }
    val hours = gridHours(days, minuteOf)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8)) {
        ShifaIconButton(
            Res.drawable.ic_chev_l_16,
            tr(Res.string.previousWeek),
            { actions.select(state.selected.plus(DatePeriod(days = -7))) },
            variant = ShifaIconButtonVariant.Secondary,
        )
        Text(
            "${displayDate(dates.first())} – ${displayDate(dates.last())}",
            Modifier.weight(1f),
            style = ShifaTextStyles.buttonCompact(),
            color = ShifaColors.Text.Primary,
            textAlign = TextAlign.Center,
        )
        ShifaIconButton(
            Res.drawable.ic_chev_r_16,
            tr(Res.string.nextWeek),
            { actions.select(state.selected.plus(DatePeriod(days = 7))) },
            variant = ShifaIconButtonVariant.Secondary,
        )
    }
    Column(
        Modifier.fillMaxWidth().background(ShifaColors.Bg.Surface, ShifaRadius.ControlLg).padding(ShifaSize.S8),
    ) {
        // The heads: weekday and date; a tap opens that day in the day view.
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.width(GUTTER))
            dates.forEachIndexed { i, d ->
                val today = d == state.today
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(role = Role.Button) {
                            actions.select(d)
                            actions.ui { it.copy(week = false) }
                        }.padding(vertical = ShifaSize.S6),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        tr(WEEKDAYS[i]).take(3),
                        style = ShifaTextStyles.caption(),
                        color = if (today) ShifaColors.Text.Brand else ShifaColors.Text.Secondary,
                    )
                    Text(
                        d.dayOfMonth.toString(),
                        style = ShifaTextStyles.buttonCompact(),
                        color = if (today) ShifaColors.Text.Brand else ShifaColors.Text.Primary,
                    )
                }
            }
        }
        val height = HOUR * ((hours.last + 1 - hours.first) / MINUTES_PER_HOUR)
        Row(Modifier.fillMaxWidth().height(height)) {
            // The hour gutter.
            Box(Modifier.width(GUTTER).height(height)) {
                (hours.first until hours.last + 1 step MINUTES_PER_HOUR).forEach { m ->
                    Text(
                        "${(m / MINUTES_PER_HOUR).toString().padStart(2, '0')}:00",
                        Modifier.offset(y = HOUR * ((m - hours.first) / MINUTES_PER_HOUR)),
                        style = ShifaTextStyles.caption(),
                        color = ShifaColors.Text.Tertiary,
                    )
                }
            }
            dates.forEachIndexed { i, d ->
                DayColumn(
                    days[i],
                    hours,
                    minuteOf,
                    selected = d == state.selected,
                    nowMinute = if (d == state.today) minuteOf(clock.nowInstant()) else null,
                    open = actions.open,
                    modifier = Modifier.weight(1f).height(height),
                )
            }
        }
    }
}

@Composable
private fun DayColumn(
    entries: List<CalendarEntry>,
    hours: IntRange,
    minuteOf: (kotlinx.datetime.Instant) -> Int,
    selected: Boolean,
    nowMinute: Int?,
    open: (CalendarEntry) -> Unit,
    modifier: Modifier,
) = BoxWithConstraints(
    modifier
        .background(if (selected) ShifaColors.Bg.Selected else ShifaColors.Bg.Surface)
        .border(ShifaSize.S1, ShifaColors.Border.Default),
) {
    val from = hours.first
    val to = hours.last + 1

    fun y(minute: Int): Dp = HOUR * ((minute.coerceIn(from, to) - from) / MINUTES_PER_HOUR.toFloat())
    entries.forEach { e ->
        val s = minuteOf(e.start)
        val end = minuteOf(e.end).let { if (it <= s) MINUTES_PER_DAY else it }
        if (end <= from || s >= to) return@forEach
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
                .offset(y = top)
                .padding(horizontal = ShifaSize.S2)
                .fillMaxWidth()
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
