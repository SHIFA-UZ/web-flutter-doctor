package shifa.doctor.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.StringResource
import shifa.doctor.data.model.DayOccupancy
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.dayAppointments
import shifa.doctor.generated.resources.dayBlocked
import shifa.doctor.generated.resources.friday
import shifa.doctor.generated.resources.ic_chev_l_16
import shifa.doctor.generated.resources.ic_chev_r_16
import shifa.doctor.generated.resources.monday
import shifa.doctor.generated.resources.monthApril
import shifa.doctor.generated.resources.monthAugust
import shifa.doctor.generated.resources.monthDecember
import shifa.doctor.generated.resources.monthFebruary
import shifa.doctor.generated.resources.monthJanuary
import shifa.doctor.generated.resources.monthJuly
import shifa.doctor.generated.resources.monthJune
import shifa.doctor.generated.resources.monthMarch
import shifa.doctor.generated.resources.monthMay
import shifa.doctor.generated.resources.monthNovember
import shifa.doctor.generated.resources.monthOctober
import shifa.doctor.generated.resources.monthSeptember
import shifa.doctor.generated.resources.nextMonth
import shifa.doctor.generated.resources.previousMonth
import shifa.doctor.generated.resources.saturday
import shifa.doctor.generated.resources.sunday
import shifa.doctor.generated.resources.thursday
import shifa.doctor.generated.resources.tuesday
import shifa.doctor.generated.resources.wednesday
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.ShifaIconButton
import shifa.doctor.ui.widgets.ShifaIconButtonVariant
import shifa.doctor.utils.displayDate
import shifa.doctor.utils.tr

/** How a day cell is drawn (DaCalendar month grid, CAL-3). */
enum class DayShade { None, Light, Busy, Blocked }

/**
 * The board's shading (the classes its cells bind): the booked share of the bookable time (appointments over
 * appointments plus free slots); none → no fill, under two thirds → light teal, more → stronger teal; a day blocked from
 * midnight → the error tint. A day not read yet has no shade.
 */
fun dayShade(occupancy: DayOccupancy?): DayShade {
    val o = occupancy ?: return DayShade.None
    if (o.fullDayBlock) return DayShade.Blocked
    val bookable = o.appointments + o.freeSlots
    if (bookable == 0 || o.appointments == 0) return DayShade.None
    return if (o.appointments.toFloat() / bookable < 2f / 3) DayShade.Light else DayShade.Busy
}

/** The months the picker reaches (flow 04/01 §3.4: 2020–2030). */
val MONTH_RANGE = LocalDate(2020, 1, 1)..LocalDate(2030, 12, 31)

/** The 6×7 (or 5×7, 4×7) grid of a month, Monday first, with nulls for the days of the neighbouring months. */
fun monthCells(month: LocalDate): List<LocalDate?> {
    val first = LocalDate(month.year, month.monthNumber, 1)
    val lead = first.dayOfWeek.ordinal // Monday = 0
    val days =
        generateSequence(first) {
            it.plus(DatePeriod(days = 1))
        }.takeWhile { it.monthNumber == first.monthNumber }.toList()
    val cells = List<LocalDate?>(lead) { null } + days
    return cells + List((7 - cells.size % 7) % 7) { null }
}

/**
 * The month card (flow 04/01 §3.4, DaCalendar `dr-month`): month title with previous / next (and a horizontal swipe),
 * weekday letters, and the days with their shading, a dot when appointments remain, today ringed, the selected day
 * filled. Months stay within [MONTH_RANGE].
 */
@Composable
fun MonthPicker(
    month: LocalDate,
    selected: LocalDate,
    today: LocalDate,
    occupancy: (LocalDate) -> DayOccupancy?,
    onSelect: (LocalDate) -> Unit,
    onMonth: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val canBack = month.year > MONTH_RANGE.start.year || month.monthNumber > 1
    val canForward = month.year < MONTH_RANGE.endInclusive.year || month.monthNumber < 12
    val back by rememberUpdatedState { if (canBack) onMonth(-1) }
    val forward by rememberUpdatedState { if (canForward) onMonth(1) }
    Column(
        modifier.fillMaxWidth().background(
            ShifaColors.Bg.Surface,
            ShifaRadius.ControlLg,
        ).padding(ShifaSpacing.Inset.Md),
        verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ShifaIconButton(
                Res.drawable.ic_chev_l_16,
                tr(Res.string.previousMonth),
                back,
                variant = ShifaIconButtonVariant.Ghost,
                enabled = canBack,
            )
            Text(
                monthTitle(month),
                Modifier.weight(1f),
                style = ShifaTextStyles.panelTitle(),
                color = ShifaColors.Text.Primary,
                textAlign = TextAlign.Center,
            )
            ShifaIconButton(
                Res.drawable.ic_chev_r_16,
                tr(Res.string.nextMonth),
                forward,
                variant = ShifaIconButtonVariant.Ghost,
                enabled = canForward,
            )
        }
        Row(Modifier.fillMaxWidth()) {
            // One letter of each weekday's own name, Monday first in every language (flow 04/01 §4).
            WEEKDAYS.forEach { day ->
                Text(
                    tr(day).take(1).uppercase(),
                    Modifier.weight(1f),
                    style = ShifaTextStyles.caption(),
                    color = ShifaColors.Text.Tertiary,
                    textAlign = TextAlign.Center,
                )
            }
        }
        // A horizontal swipe over the days pages the month (flow 04/01 §3.4).
        Column(
            Modifier.pointerInput(Unit) {
                var dragged = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragged = 0f },
                    onHorizontalDrag = { _, delta -> dragged += delta },
                    onDragEnd = {
                        if (dragged > SWIPE_PX) {
                            back()
                        } else if (dragged < -SWIPE_PX) {
                            forward()
                        }
                    },
                )
            },
            verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md),
        ) {
            monthCells(month).chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ShifaSize.S4)) {
                    week.forEach { date ->
                        Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                            if (date != null) {
                                DayCell(
                                    date,
                                    date == selected,
                                    date == today,
                                    date < today,
                                    occupancy(date),
                                    onSelect,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val SWIPE_PX = 80f

@Composable
private fun DayCell(
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    isPast: Boolean,
    occupancy: DayOccupancy?,
    onSelect: (LocalDate) -> Unit,
) {
    val shade = dayShade(occupancy)
    // The date, then what the shading and the dot show, for screen readers (the board's labels).
    val label =
        listOfNotNull(
            displayDate(date),
            occupancy?.appointments?.takeIf { it > 0 }?.let { tr(Res.string.dayAppointments, it) },
            tr(Res.string.dayBlocked).takeIf { shade == DayShade.Blocked },
        ).joinToString(", ")
    val fill =
        when {
            isSelected -> ShifaColors.Interactive.Primary
            shade == DayShade.Blocked -> ShifaColors.Bg.ErrorSubtle
            shade == DayShade.Light -> ShifaColors.Alpha.Teal15
            shade == DayShade.Busy -> ShifaColors.Alpha.Teal25
            else -> Color.Transparent
        }
    val ink =
        when {
            isSelected -> ShifaColors.Text.Inverse
            isToday -> ShifaColors.Text.BrandStrong
            isPast -> ShifaColors.Text.Tertiary
            else -> ShifaColors.Text.Primary
        }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(fill, ShifaRadius.Full)
            .let {
                if (isToday && !isSelected) {
                    it.border(
                        ShifaSize.S2,
                        ShifaColors.Border.Focus,
                        ShifaRadius.Full,
                    )
                } else {
                    it
                }
            }
            .clickable(role = Role.Button) { onSelect(date) }
            .semantics {
                selected = isSelected
                contentDescription = label
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            date.dayOfMonth.toString(),
            style =
                ShifaTextStyles.chipSlot().copy(
                    fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.SemiBold,
                ),
            color = ink,
        )
        // Appointments still ahead on this day.
        if (!isSelected && !isPast && (occupancy?.appointments ?: 0) > 0) {
            Box(
                Modifier.align(Alignment.BottomCenter).padding(bottom = ShifaSize.S4).size(ShifaSize.S4).background(
                    ShifaColors.Text.Primary,
                    ShifaRadius.Full,
                ),
            )
        }
    }
}

private val MONTHS: List<StringResource> =
    listOf(
        Res.string.monthJanuary,
        Res.string.monthFebruary,
        Res.string.monthMarch,
        Res.string.monthApril,
        Res.string.monthMay,
        Res.string.monthJune,
        Res.string.monthJuly,
        Res.string.monthAugust,
        Res.string.monthSeptember,
        Res.string.monthOctober,
        Res.string.monthNovember,
        Res.string.monthDecember,
    )

internal val WEEKDAYS: List<StringResource> =
    DayOfWeek.entries.map {
        when (it) {
            DayOfWeek.MONDAY -> Res.string.monday
            DayOfWeek.TUESDAY -> Res.string.tuesday
            DayOfWeek.WEDNESDAY -> Res.string.wednesday
            DayOfWeek.THURSDAY -> Res.string.thursday
            DayOfWeek.FRIDAY -> Res.string.friday
            DayOfWeek.SATURDAY -> Res.string.saturday
            else -> Res.string.sunday
        }
    }

/** The month's title, shared with the header ("November 2026"). */
@Composable
internal fun monthTitle(month: LocalDate) = "${tr(MONTHS[month.monthNumber - 1])} ${month.year}"
