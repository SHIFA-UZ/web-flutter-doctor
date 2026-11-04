package shifa.doctor.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.ic_block_16
import shifa.doctor.generated.resources.ic_calendar_22
import shifa.doctor.generated.resources.ic_chev_d_16
import shifa.doctor.generated.resources.ic_clipboard_16
import shifa.doctor.generated.resources.ic_clock_14
import shifa.doctor.generated.resources.ic_lock_12
import shifa.doctor.generated.resources.ic_plus_16
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaShadow
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles

/** A meta line of the hero ("10:30–11:00", "Video call", "3 documents"), each with its icon. */
data class HeroMeta(
    val icon: DrawableResource,
    val text: String,
)

/**
 * NextPatientHero (`.dr-hero`, Hero board) at phone width: the pills on their own top row ("Next patient" +
 * the countdown, teal-subtle once started), the identity row, the medical-record disclosure holding [flags]
 * (AlertChips, including the AI briefing chip), and the two actions side by side at full width, without icons.
 */
@Composable
fun NextPatientHero(
    eyebrow: String,
    countdown: String,
    started: Boolean,
    name: String,
    initials: String,
    meta: List<HeroMeta>,
    recordLabel: String,
    flagCount: String,
    recordOpen: Boolean,
    recordToggleLabel: String,
    onToggleRecord: () -> Unit,
    primary: @Composable (Modifier) -> Unit,
    secondary: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    flags: @Composable () -> Unit = {},
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(ShifaRadius.ControlMd)
            .background(ShifaColors.Bg.Surface)
            .border(ShifaSize.S2, ShifaColors.Border.Brand, ShifaRadius.ControlMd)
            .padding(ShifaSpacing.Inset.Md),
        verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Stack.Md),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Pill(eyebrow, ShifaColors.Interactive.Primary, ShifaColors.Interactive.OnPrimary)
            Pill(
                countdown,
                if (started) ShifaColors.Bg.SuccessSubtle else ShifaColors.Bg.Canvas,
                ShifaColors.Text.Primary,
                Res.drawable.ic_clock_14,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(ShifaSize.S48).background(ShifaColors.Alpha.Teal15, ShifaRadius.Full),
                contentAlignment = Alignment.Center,
            ) {
                Text(initials, style = ShifaTextStyles.dayNumber(), color = ShifaColors.Text.Primary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ShifaSize.S6)) {
                Text(
                    name,
                    style = ShifaTextStyles.heroNamePhone(),
                    color = ShifaColors.Text.Primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(ShifaSize.S14),
                    verticalArrangement = Arrangement.spacedBy(ShifaSize.S6),
                ) {
                    meta.forEach { m ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(ShifaSize.S4),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(painterResource(m.icon), null, tint = ShifaColors.Icon.Secondary)
                            Text(
                                m.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = ShifaColors.Text.Secondary,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S10)) {
            HorizontalDivider(thickness = ShifaSize.S1, color = ShifaColors.Border.Default)
            RecordToggle(recordLabel, flagCount, recordOpen, recordToggleLabel, onToggleRecord)
            if (recordOpen) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm),
                    verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm),
                ) {
                    flags()
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md)) {
            primary(Modifier.weight(1f))
            secondary(Modifier.weight(1f))
        }
    }
}

/** HeroStates "empty day": no appointment today, with the way to the calendar. */
@Composable
fun NextPatientHeroEmpty(
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(ShifaRadius.ControlMd)
            .background(ShifaColors.Bg.Surface)
            .border(ShifaSize.S2, ShifaColors.Border.Brand, ShifaRadius.ControlMd)
            .padding(ShifaSpacing.Inset.Md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md),
    ) {
        Box(
            Modifier.size(ShifaSize.S56).background(ShifaColors.Bg.Canvas, ShifaRadius.Full),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(Res.drawable.ic_calendar_22), null, tint = ShifaColors.Icon.Secondary)
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = ShifaColors.Text.Primary,
            textAlign = TextAlign.Center,
        )
        Text(body, style = ShifaTextStyles.bodyText(), color = ShifaColors.Text.Secondary, textAlign = TextAlign.Center)
        ShifaTertiaryButton(action, onAction)
    }
}

/** The medical-record row of the hero: a flag count and Show/Hide with the chevron. */
@Composable
fun RecordToggle(
    label: String,
    count: String,
    open: Boolean,
    toggleLabel: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = ShifaSize.Touch)
            .clip(ShifaRadius.ControlMd)
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics { stateDescription = toggleLabel },
        horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(Res.drawable.ic_clipboard_16), null, tint = ShifaColors.Icon.Secondary)
        Text(label, style = MaterialTheme.typography.titleSmall, color = ShifaColors.Text.Primary)
        Row(
            Modifier.height(
                ShifaSize.S20,
            ).background(ShifaColors.Bg.ErrorSubtle, ShifaRadius.Full).padding(horizontal = ShifaSpacing.Inset.Xs),
            horizontalArrangement = Arrangement.spacedBy(ShifaSize.S4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(ShifaSize.S6).background(ShifaColors.Icon.Error, ShifaRadius.Full))
            Text(count, style = MaterialTheme.typography.labelSmall, color = ShifaColors.Text.Primary)
        }
        Spacer(Modifier.weight(1f))
        Text(toggleLabel, style = ShifaTextStyles.tableHead(), color = ShifaColors.Text.Secondary)
        Icon(
            painterResource(Res.drawable.ic_chev_d_16),
            null,
            Modifier.graphicsLayer { rotationZ = if (open) 180f else 0f },
            tint = ShifaColors.Icon.Secondary,
        )
    }
}

@Composable
private fun Pill(
    text: String,
    bg: Color,
    ink: Color,
    icon: DrawableResource? = null,
) = Row(
    Modifier.height(ShifaSize.S28).background(bg, ShifaRadius.Full).padding(horizontal = ShifaSpacing.Inset.Sm),
    horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6),
    verticalAlignment = Alignment.CenterVertically,
) {
    icon?.let { Icon(painterResource(it), null, tint = ink) }
    Text(text, style = ShifaTextStyles.tableHead(), color = ink, maxLines = 1)
}

// AppointmentTimeline rows.
sealed interface TimelineEntry {
    val start: String
    val end: String

    /** A visit. [badge] is the icon-only status disc on phones; [now] gives the dot its halo and shows [startAction]. */
    data class Visit(
        override val start: String,
        override val end: String,
        val patient: String,
        val initials: String,
        val visit: String,
        val mode: String,
        val modeIcon: DrawableResource,
        val badge: (@Composable () -> Unit)? = null,
        val past: Boolean = false,
        val now: Boolean = false,
        val done: Boolean = false,
        val lockedNote: String? = null,
        val startAction: (@Composable (Modifier) -> Unit)? = null,
    ) : TimelineEntry

    /** Free time: tap to book. */
    data class Free(
        override val start: String,
        override val end: String,
        val label: String,
        val hint: String,
        val onClick: () -> Unit,
    ) : TimelineEntry

    /** A blocked period (hatched). */
    data class Blocked(
        override val start: String,
        override val end: String,
        val label: String,
    ) : TimelineEntry
}

/**
 * AppointmentTimeline (`.dr-tl`, Timeline board) at phone width: 40 time gutter, 16 rail with the dot (filled when
 * done, haloed when now), and the card. Past rows dim to 72 %.
 */
@Composable
fun AppointmentTimeline(
    entries: List<TimelineEntry>,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        entries.forEachIndexed { i, e ->
            val visit = e as? TimelineEntry.Visit
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .alpha(if (visit?.past == true) PAST_ALPHA else 1f),
                horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm),
            ) {
                Column(
                    Modifier.width(ShifaSize.S40).padding(top = ShifaSpacing.Inset.Md),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(ShifaSize.S4),
                ) {
                    Text(e.start, style = ShifaTextStyles.timeLabel(), color = ShifaColors.Text.Primary)
                    Text(e.end, style = MaterialTheme.typography.bodyMedium, color = ShifaColors.Text.Tertiary)
                }
                Rail(
                    first = i == 0,
                    last = i == entries.lastIndex,
                    done = visit?.done == true,
                    now = visit?.now == true,
                )
                Box(Modifier.weight(1f).padding(top = ShifaSize.S4, bottom = ShifaSpacing.Inset.Sm)) {
                    when (e) {
                        is TimelineEntry.Visit -> VisitCard(e)
                        is TimelineEntry.Free -> FreeSlot(e)
                        is TimelineEntry.Blocked -> BlockedSlot(e)
                    }
                }
            }
        }
    }
}

@Composable
private fun Rail(
    first: Boolean,
    last: Boolean,
    done: Boolean,
    now: Boolean,
) = Column(Modifier.width(ShifaSize.S16).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
    Box(
        Modifier.width(
            ShifaSize.S2,
        ).height(ShifaSize.S18).background(if (first) Color.Transparent else ShifaColors.Border.Default),
    )
    Box(
        Modifier
            .then(
                if (now) {
                    Modifier.background(
                        ShifaColors.Alpha.Teal25,
                        ShifaRadius.Full,
                    ).padding(ShifaSize.S4)
                } else {
                    Modifier
                },
            )
            .size(ShifaSize.S12)
            .clip(ShifaRadius.Full)
            .background(if (done || now) ShifaColors.Interactive.Primary else ShifaColors.Bg.Surface)
            .border(ShifaSize.S2, ShifaColors.Border.Brand, ShifaRadius.Full),
    )
    Box(Modifier.width(ShifaSize.S2).weight(1f).background(if (last) Color.Transparent else ShifaColors.Border.Default))
}

@Composable
private fun VisitCard(e: TimelineEntry.Visit) {
    Column(
        Modifier
            .fillMaxWidth()
            .dropShadow(ShifaRadius.ControlMd, ShifaShadow.Card)
            .background(ShifaColors.Bg.Surface, ShifaRadius.ControlMd)
            .padding(ShifaSpacing.Inset.Sm),
        verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(ShifaSize.S40).background(ShifaColors.Alpha.Teal15, ShifaRadius.Full),
                contentAlignment = Alignment.Center,
            ) {
                Text(e.initials, style = MaterialTheme.typography.titleSmall, color = ShifaColors.Text.Primary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ShifaSize.S6)) {
                Text(
                    e.patient,
                    style = MaterialTheme.typography.titleSmall,
                    color = ShifaColors.Text.Primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(ShifaSize.S10),
                    verticalArrangement = Arrangement.spacedBy(ShifaSize.S4),
                ) {
                    Text(e.visit, style = MaterialTheme.typography.bodyMedium, color = ShifaColors.Text.Secondary)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ShifaSize.S4),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(painterResource(e.modeIcon), null, tint = ShifaColors.Text.Tertiary)
                        Text(e.mode, style = MaterialTheme.typography.bodyMedium, color = ShifaColors.Text.Tertiary)
                    }
                }
            }
            e.badge?.invoke()
        }
        e.lockedNote?.let {
            Row(
                horizontalArrangement = Arrangement.spacedBy(ShifaSize.S4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(Res.drawable.ic_lock_12), null, tint = ShifaColors.Text.Tertiary)
                Text(it, style = ShifaTextStyles.caption(), color = ShifaColors.Text.Tertiary)
            }
        }
        e.startAction?.invoke(Modifier.fillMaxWidth())
    }
}

@Composable
private fun FreeSlot(e: TimelineEntry.Free) {
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = ShifaSize.S52)
            .clip(ShifaRadius.ControlMd)
            .border(ShifaSize.S1, ShifaColors.Border.Brand, ShifaRadius.ControlMd)
            .clickable(role = Role.Button, onClick = e.onClick)
            .padding(horizontal = ShifaSpacing.Inset.Sm),
        horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(Res.drawable.ic_plus_16), null, tint = ShifaColors.Icon.Brand)
        Text(
            e.label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            color = ShifaColors.Text.Primary,
        )
        Text(e.hint, style = MaterialTheme.typography.bodyMedium, color = ShifaColors.Text.Secondary)
    }
}

@Composable
private fun BlockedSlot(e: TimelineEntry.Blocked) {
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = ShifaSize.S52)
            .clip(ShifaRadius.ControlMd)
            .background(
                Brush.linearGradient(
                    0f to ShifaColors.Bg.Canvas,
                    0.89f to ShifaColors.Bg.Canvas,
                    0.89f to ShifaColors.Bg.Disabled,
                    1f to ShifaColors.Bg.Disabled,
                    start = Offset.Zero,
                    end = Offset(BLOCK_STRIPE_PX, BLOCK_STRIPE_PX),
                    tileMode = TileMode.Repeated,
                ),
            ).semantics { contentDescription = e.label }
            .padding(horizontal = ShifaSpacing.Inset.Sm),
        horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(Res.drawable.ic_block_16), null, tint = ShifaColors.Icon.Secondary)
        Text(e.label, style = MaterialTheme.typography.titleSmall, color = ShifaColors.Text.Secondary)
    }
}

private const val PAST_ALPHA = 0.72f // `.dr-tl--past .dr-tl__card { opacity: .72 }`
private const val BLOCK_STRIPE_PX = 13f // the board's 8 + 1 px stripes at 135°
