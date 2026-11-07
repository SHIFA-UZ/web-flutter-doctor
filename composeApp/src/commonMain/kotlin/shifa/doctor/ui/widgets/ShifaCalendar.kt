package shifa.doctor.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.ic_chev_l_16
import shifa.doctor.generated.resources.ic_chev_r_16
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles

/** A day of [DayStrip]: the short weekday and the date number. */
data class DayStripItem(
    val weekday: String,
    val day: String,
)

/** `.dr-daystrip`: the phone calendar's week of days above the agenda; scrolls sideways. */
@Composable
fun DayStrip(
    days: List<DayStripItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(ShifaSize.S4),
    ) {
        days.forEachIndexed { i, d ->
            val sel = i == selected
            Column(
                Modifier
                    .defaultMinSize(minWidth = ShifaSize.Touch)
                    .clip(ShifaRadius.ControlMd)
                    .background(if (sel) ShifaColors.Interactive.Primary else ShifaColors.Bg.Surface)
                    .pointerHoverIcon(PointerIcon.Hand).selectable(sel, role = Role.Tab, onClick = { onSelect(i) })
                    .padding(vertical = ShifaSpacing.Inset.Xs, horizontal = ShifaSize.S4),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val ink = if (sel) ShifaColors.Interactive.OnPrimary else ShifaColors.Text.Secondary
                Text(d.weekday, style = ShifaTextStyles.caption().copy(fontWeight = FontWeight.Medium), color = ink)
                Text(
                    d.day,
                    style = ShifaTextStyles.dayNumber(),
                    color = if (sel) ShifaColors.Interactive.OnPrimary else ShifaColors.Text.Primary,
                )
            }
        }
    }
}

// `.dr-ev` kinds: a booked visit (teal bar), a video visit (blue), free time (dashed teal frame; tap books),
// a block (hatched), and the visit happening now (solid teal).
enum class AgendaEventKind { Booked, Video, Free, Blocked, Now }

/**
 * `.dr-agenda__slot`: one row of the phone day agenda — the start time in the gutter and the event. [cancelled]
 * strikes the title through (the only struck-through state, per the audit). [chips] holds the row's badges
 * (payment, signature, briefing; flow 04/02).
 */
@Composable
fun AgendaSlot(
    time: String,
    title: String,
    kind: AgendaEventKind,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    cancelled: Boolean = false,
    chips: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm)) {
        Text(
            time,
            Modifier.width(ShifaSize.S48).padding(top = ShifaSpacing.Inset.Sm),
            style = ShifaTextStyles.tableHead(),
            color = ShifaColors.Text.Secondary,
            textAlign = TextAlign.End,
        )
        val (bg, bar, ink) =
            when (kind) {
                AgendaEventKind.Booked ->
                    Triple(
                        ShifaColors.Bg.SuccessSubtle,
                        ShifaColors.Interactive.Primary,
                        ShifaColors.Text.Primary,
                    )
                AgendaEventKind.Video ->
                    Triple(
                        ShifaColors.Bg.InfoSubtle,
                        ShifaColors.Icon.Info,
                        ShifaColors.Text.Primary,
                    )
                AgendaEventKind.Free -> Triple(ShifaColors.Bg.Surface, Color.Transparent, ShifaColors.Text.Secondary)
                AgendaEventKind.Blocked -> Triple(ShifaColors.Bg.Canvas, Color.Transparent, ShifaColors.Text.Secondary)
                AgendaEventKind.Now ->
                    Triple(
                        ShifaColors.Interactive.Primary,
                        Color.Transparent,
                        ShifaColors.Interactive.OnPrimary,
                    )
            }
        val hatch = ShifaColors.Bg.Disabled
        Column(
            Modifier
                .weight(1f)
                .clip(ShifaRadius.Sm)
                .background(bg)
                .then(
                    when (kind) {
                        AgendaEventKind.Free -> Modifier.border(ShifaSize.S1, ShifaColors.Border.Brand, ShifaRadius.Sm)
                        AgendaEventKind.Blocked ->
                            Modifier.background(
                                Brush.linearGradient(
                                    0f to ShifaColors.Bg.Canvas,
                                    0.75f to ShifaColors.Bg.Canvas,
                                    0.75f to hatch,
                                    1f to hatch,
                                    start = Offset.Zero,
                                    end = Offset(HATCH_PX, HATCH_PX),
                                    tileMode = TileMode.Repeated,
                                ),
                            )
                        else -> Modifier
                    },
                ).drawBehind {
                    if (bar != Color.Transparent) {
                        drawRect(
                            bar,
                            size = size.copy(width = ShifaSize.S3.toPx()),
                        )
                    }
                }
                .then(
                    if (onClick != null) {
                        Modifier.pointerHoverIcon(
                            PointerIcon.Hand,
                        ).clickable(role = Role.Button, onClick = onClick)
                    } else {
                        Modifier
                    },
                )
                .padding(ShifaSpacing.Inset.Sm),
            verticalArrangement = Arrangement.spacedBy(ShifaSize.S2),
        ) {
            Text(
                title,
                style =
                    MaterialTheme.typography.titleSmall.copy(
                        fontWeight = if (kind == AgendaEventKind.Free) FontWeight.Medium else FontWeight.Bold,
                        textDecoration = if (cancelled) TextDecoration.LineThrough else null,
                    ),
                color = ink,
                maxLines = 1, // the audit's open item: long names truncate to one line in the agenda
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                val now = kind == AgendaEventKind.Now
                Text(
                    it,
                    style = ShifaTextStyles.caption().copy(fontWeight = FontWeight.Medium),
                    color = if (now) ShifaColors.Interactive.OnPrimary else ShifaColors.Text.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            chips?.let {
                Row(
                    Modifier.padding(top = ShifaSize.S4),
                    horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Xs),
                ) { it() }
            }
        }
    }
}

// A month day's fill (`button.dr-day`): occupancy light/busy, blocked, or nothing.
enum class DayLoad { None, Light, Busy, Blocked }

private val DayLoad.tint
    get() =
        when (this) {
            DayLoad.Light -> ShifaColors.Alpha.Teal15
            DayLoad.Busy -> ShifaColors.Alpha.Teal25
            DayLoad.Blocked -> ShifaColors.Bg.ErrorSubtle
            DayLoad.None -> Color.Transparent
        }

/** A cell of [MonthCalendar]. [label] is the date number; [outside] days belong to the next/previous month. */
data class MonthDay(
    val label: String,
    val load: DayLoad = DayLoad.None,
    val today: Boolean = false,
    val selected: Boolean = false,
    val past: Boolean = false,
    val outside: Boolean = false,
    val off: Boolean = false,
    val description: String = label,
)

/**
 * `.dr-month`: the calendar's month picker with occupancy. Today has the focus ring, the selected day is teal,
 * light/busy days carry the teal tints, blocked days the error tint, days off are struck through. [legend] explains
 * the tints below the grid.
 */
@Composable
fun MonthCalendar(
    title: String,
    weekdays: List<String>,
    days: List<MonthDay>,
    onDay: (Int) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    previousDescription: String,
    nextDescription: String,
    modifier: Modifier = Modifier,
    legend: List<Pair<DayLoad, String>> = emptyList(),
) {
    Column(
        modifier.fillMaxWidth().background(
            ShifaColors.Bg.Surface,
            ShifaRadius.ControlLg,
        ).padding(ShifaSpacing.Inset.Md),
        verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShifaIconButton(
                Res.drawable.ic_chev_l_16,
                previousDescription,
                onPrevious,
                variant = ShifaIconButtonVariant.Ghost,
            )
            Text(
                title,
                Modifier.weight(1f),
                style = ShifaTextStyles.panelTitle(),
                color = ShifaColors.Text.Primary,
                textAlign = TextAlign.Center,
            )
            ShifaIconButton(Res.drawable.ic_chev_r_16, nextDescription, onNext, variant = ShifaIconButtonVariant.Ghost)
        }
        Row(Modifier.fillMaxWidth()) {
            weekdays.forEach {
                Text(
                    it,
                    Modifier.weight(1f),
                    style = ShifaTextStyles.caption().copy(fontWeight = FontWeight.Medium),
                    color = ShifaColors.Text.Tertiary,
                    textAlign = TextAlign.Center,
                )
            }
        }
        days.chunked(7).forEachIndexed { week, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ShifaSize.S4)) {
                row.forEachIndexed { i, d -> DayCell(d, Modifier.weight(1f)) { onDay(week * 7 + i) } }
                repeat(7 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
        if (legend.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md)) {
                legend.forEach { (load, label) ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(ShifaSize.S10).background(load.tint, ShifaRadius.Full))
                        Text(label, style = ShifaTextStyles.kpiLabel(), color = ShifaColors.Text.Secondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    d: MonthDay,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val bg =
        when {
            d.selected -> ShifaColors.Interactive.Primary
            else -> d.load.tint
        }
    val ink =
        when {
            d.selected -> ShifaColors.Interactive.OnPrimary
            d.outside || d.off -> ShifaColors.Text.Disabled
            d.past && d.load != DayLoad.None -> ShifaColors.Text.Secondary
            d.past -> ShifaColors.Text.Tertiary
            else -> ShifaColors.Text.Primary
        }
    Box(
        modifier
            .aspectRatio(1f)
            .clip(ShifaRadius.Full)
            .background(bg)
            .then(
                if (d.today && !d.selected) {
                    Modifier.border(
                        ShifaSize.S2,
                        ShifaColors.Border.Focus,
                        ShifaRadius.Full,
                    )
                } else {
                    Modifier
                },
            )
            .pointerHoverIcon(
                PointerIcon.Hand,
            ).selectable(d.selected, enabled = !d.outside, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = d.description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            d.label,
            Modifier.clearAndSetSemantics {},
            style =
                ShifaTextStyles.tabLabel().copy(
                    fontWeight = if (d.selected || d.today) FontWeight.Bold else FontWeight.Medium,
                    textDecoration = if (d.off) TextDecoration.LineThrough else null,
                ),
            color = ink,
        )
    }
}

private const val HATCH_PX = 11f // the 135° stripes of `.dr-ev--blocked` (6 + 2 px at 1.4 density)
