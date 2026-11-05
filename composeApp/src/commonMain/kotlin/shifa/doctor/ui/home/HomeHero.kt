package shifa.doctor.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.datetime.Instant
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.data.home.HeroLook
import shifa.doctor.data.home.HeroPill
import shifa.doctor.data.home.HomeSchedule
import shifa.doctor.data.home.homeTimeline
import shifa.doctor.data.model.CalendarEntry
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.addPracticeTimezone
import shifa.doctor.generated.resources.age
import shifa.doctor.generated.resources.blocked
import shifa.doctor.generated.resources.continueAppointment
import shifa.doctor.generated.resources.doneForToday
import shifa.doctor.generated.resources.doneForTodayBody
import shifa.doctor.generated.resources.heroInMinutes
import shifa.doctor.generated.resources.heroOpensAt
import shifa.doctor.generated.resources.heroOpensAtVideo
import shifa.doctor.generated.resources.hideEarlier
import shifa.doctor.generated.resources.homeSetTimezone
import shifa.doctor.generated.resources.ic_block_14
import shifa.doctor.generated.resources.ic_cal_plus_18
import shifa.doctor.generated.resources.ic_calendar_24
import shifa.doctor.generated.resources.ic_clinic_14
import shifa.doctor.generated.resources.ic_clock_14
import shifa.doctor.generated.resources.ic_globe_24
import shifa.doctor.generated.resources.ic_pin_14
import shifa.doctor.generated.resources.ic_play_14
import shifa.doctor.generated.resources.ic_play_18
import shifa.doctor.generated.resources.ic_video_14
import shifa.doctor.generated.resources.ic_video_18
import shifa.doctor.generated.resources.inClinic
import shifa.doctor.generated.resources.newAppointment
import shifa.doctor.generated.resources.nextAppointment
import shifa.doctor.generated.resources.nextPatient
import shifa.doctor.generated.resources.noNextPatient
import shifa.doctor.generated.resources.noUpcomingBody
import shifa.doctor.generated.resources.now
import shifa.doctor.generated.resources.onlineVisit
import shifa.doctor.generated.resources.openPatientCard
import shifa.doctor.generated.resources.quickActions
import shifa.doctor.generated.resources.seeAllInCalendar
import shifa.doctor.generated.resources.showEarlierCount
import shifa.doctor.generated.resources.startAppointment
import shifa.doctor.generated.resources.startVideoCall
import shifa.doctor.generated.resources.statusConfirmed
import shifa.doctor.generated.resources.todayTimelineSubtitle
import shifa.doctor.generated.resources.todaysSchedule
import shifa.doctor.generated.resources.videoCall
import shifa.doctor.generated.resources.waiting
import shifa.doctor.ui.calendar.status
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaShadow
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.ShifaActionButton
import shifa.doctor.ui.widgets.ShifaActionVariant
import shifa.doctor.ui.widgets.ShifaAvatar
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.ui.widgets.ShifaPrimaryButton
import shifa.doctor.ui.widgets.ShifaSecondaryButton
import shifa.doctor.ui.widgets.pressable
import shifa.doctor.utils.PracticeClock
import shifa.doctor.utils.displayDate
import shifa.doctor.utils.displayTime
import shifa.doctor.utils.tr
import shifa.doctor.utils.trPlural

// The timeline lists at most this many visits still to come (flow 02/02 §8: clinic days can hold 100+).
private const val TIMELINE_CAP = 8

private fun PracticeClock.hm(at: Instant) = displayTime(local(at).time)

/**
 * The next-patient card (HOME-03, flow 02/02 §3–4, board `DaHome`): who, when, how, and Start (opens 15 minutes
 * before; Continue while in progress) with Open chart. Read out as "Next patient: <name>". Not built: the board's
 * "Medical record" flags (allergy, chronic disease, documents, briefing); the visit screen warns about a chronic
 * disease itself when it opens.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HeroCard(
    schedule: HomeSchedule,
    look: HeroLook,
    age: Int?,
    clock: PracticeClock,
    actions: HomeActions,
) {
    val hero = schedule.hero ?: return
    val name = hero.patientName.orEmpty()
    val kicker = tr(if (schedule.heroDay == null) Res.string.nextPatient else Res.string.nextAppointment)
    Column(
        Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "$kicker: $name" }
            .dropShadow(ShifaRadius.ControlLg, ShifaShadow.Card)
            .clip(ShifaRadius.ControlLg)
            .background(ShifaColors.Bg.Surface)
            .border(ShifaSize.S2, ShifaColors.Border.Brand, ShifaRadius.ControlLg)
            .padding(ShifaSize.S16),
        verticalArrangement = Arrangement.spacedBy(ShifaSize.S14),
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Pill(kicker, ShifaColors.Interactive.Primary, ShifaColors.Text.Inverse)
            val now = look.pill == HeroPill.Now
            Pill(
                when (look.pill) {
                    HeroPill.Now -> tr(Res.string.now)
                    HeroPill.Waiting -> tr(Res.string.waiting)
                    HeroPill.Soon -> tr(Res.string.heroInMinutes, look.minutes.toString())
                    HeroPill.Confirmed -> tr(Res.string.statusConfirmed)
                },
                if (now) ShifaColors.Bg.SuccessSubtle else ShifaColors.Bg.WarningSubtle,
                ShifaColors.Text.Primary,
                if (now) Res.drawable.ic_play_14 else Res.drawable.ic_clock_14,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(ShifaSize.S14),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShifaAvatar(hero.photoUrl, ShifaSize.S56)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ShifaSize.S6)) {
                Text(
                    name,
                    style = ShifaTextStyles.contextName(),
                    color = ShifaColors.Text.Primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(ShifaSize.S14),
                    verticalArrangement = Arrangement.spacedBy(ShifaSize.S6),
                ) {
                    val day = schedule.heroDay?.let { "${displayDate(it)} · " }.orEmpty()
                    Meta(Res.drawable.ic_clock_14, "$day${clock.hm(hero.start)}–${clock.hm(hero.end)}")
                    Meta(
                        if (hero.isVideo) Res.drawable.ic_video_14 else Res.drawable.ic_clinic_14,
                        tr(if (hero.isVideo) Res.string.onlineVisit else Res.string.inClinic),
                    )
                    // The place, unless it would only repeat "In Clinic" (flow 02/02 §8).
                    place(hero)?.let { Meta(Res.drawable.ic_pin_14, it) }
                    age?.let {
                        Text(
                            "${tr(Res.string.age)} $it",
                            style = ShifaTextStyles.metaSmall(),
                            color = ShifaColors.Text.Secondary,
                        )
                    }
                }
                hero.reason.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = ShifaTextStyles.metaSmall(),
                        color = ShifaColors.Text.Primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        val inProgress = hero.status.equals("IN_PROGRESS", ignoreCase = true)
        ShifaPrimaryButton(
            tr(
                when {
                    inProgress -> Res.string.continueAppointment
                    hero.isVideo -> Res.string.startVideoCall
                    else -> Res.string.startAppointment
                },
            ),
            { actions.start(hero) },
            Modifier.fillMaxWidth(),
            icon = if (hero.isVideo) Res.drawable.ic_video_18 else Res.drawable.ic_play_18,
            enabled = look.canStart,
        )
        hero.patientId?.let {
                id ->
            ShifaSecondaryButton(tr(Res.string.openPatientCard), { actions.openChart(id) }, Modifier.fillMaxWidth())
        }
        look.opensAt?.let {
            val at =
                if (schedule.heroDay == null) {
                    clock.hm(
                        it,
                    )
                } else {
                    "${displayDate(clock.local(it).date)}, ${clock.hm(it)}"
                }
            Text(
                tr(if (hero.isVideo) Res.string.heroOpensAtVideo else Res.string.heroOpensAt, at),
                Modifier.fillMaxWidth(),
                style = ShifaTextStyles.metaSmall(),
                color = ShifaColors.Text.Secondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** The booked place for an in-clinic visit; null for video, or when it would only say "In Clinic" again. */
private fun place(a: CalendarEntry.Appointment): String? =
    if (a.isVideo) null else (a.locationLabel ?: a.location)?.takeIf { it.isNotBlank() }

/**
 * No hero (board `DaHome`): "Done for today" when today had visits and nothing is booked for 14 days, else "No
 * upcoming appointments"; both offer New appointment. Without a practice timezone, the card asks for one instead
 * (flow 02/02 §6 target): today's day can't be known without it.
 */
@Composable
internal fun EmptyHero(
    schedule: HomeSchedule?,
    zoneMissing: Boolean,
    actions: HomeActions,
) {
    val done = !zoneMissing && schedule?.today?.isNotEmpty() == true
    Column(
        Modifier
            .fillMaxWidth()
            .dropShadow(ShifaRadius.ControlLg, ShifaShadow.Card)
            .clip(ShifaRadius.ControlLg)
            .background(ShifaColors.Bg.Surface)
            .padding(horizontal = ShifaSize.S16, vertical = ShifaSize.S24),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ShifaSize.S12),
    ) {
        Box(Modifier.size(ShifaSize.S56).background(ShifaColors.Bg.Canvas, ShifaRadius.Full), Alignment.Center) {
            Icon(
                painterResource(if (zoneMissing) Res.drawable.ic_globe_24 else Res.drawable.ic_calendar_24),
                null,
                tint = ShifaColors.Text.Secondary,
            )
        }
        if (zoneMissing) {
            Text(
                tr(Res.string.homeSetTimezone),
                style = ShifaTextStyles.bodyText(),
                color = ShifaColors.Text.Primary,
                textAlign = TextAlign.Center,
            )
            ShifaPrimaryButton(tr(Res.string.addPracticeTimezone), actions.setTimezone)
            return@Column
        }
        Text(
            tr(if (done) Res.string.doneForToday else Res.string.noNextPatient),
            Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
            color = ShifaColors.Text.Primary,
            textAlign = TextAlign.Center,
        )
        Text(
            tr(if (done) Res.string.doneForTodayBody else Res.string.noUpcomingBody),
            Modifier.widthIn(max = ShifaSize.S320),
            style = ShifaTextStyles.bodyText(),
            color = ShifaColors.Text.Secondary,
            textAlign = TextAlign.Center,
        )
        ShifaPrimaryButton(tr(Res.string.newAppointment), actions.calendar)
    }
}

/**
 * "Quick Actions" (flow 02/02 §3, board `DaHome`): New appointment (Calendar) and Video Call (the first free slot,
 * booked as a video call). Not yet: "Prescription", which opens the remote-care task form (TASK tickets); its label
 * and destination disagree (flow §13), so it waits for that decision.
 */
@Composable
internal fun QuickActions(
    // Video Call looks for a slot only on a known practice day, one search at a time.
    videoEnabled: Boolean,
    actions: HomeActions,
) = Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S10)) {
    Text(
        tr(Res.string.quickActions),
        Modifier.semantics { heading() },
        style = ShifaTextStyles.sectionHeading(),
        color = ShifaColors.Text.Primary,
    )
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8)) {
        ShifaActionButton(
            actions.calendar,
            text = tr(Res.string.newAppointment),
            icon = Res.drawable.ic_cal_plus_18,
            variant = ShifaActionVariant.Surface,
        )
        ShifaActionButton(
            actions.videoCall,
            text = tr(Res.string.videoCall),
            icon = Res.drawable.ic_video_18,
            variant = ShifaActionVariant.Surface,
            enabled = videoEnabled,
        )
    }
}

/**
 * "Today's schedule" (flow 02/02 §3, board `DaHome`): today's visits and blocked times by start. Finished ones fold
 * into "Show N earlier"; at most [TIMELINE_CAP] still to come, then "See all in Calendar". A row opens its panel (the
 * Calendar's, with Start), not just a highlight as in Flutter. Hidden when today has nothing.
 */
@Composable
internal fun Timeline(
    schedule: HomeSchedule,
    now: Instant,
    clock: PracticeClock,
    actions: HomeActions,
) {
    val split = homeTimeline(schedule, now)
    if (split.earlier.isEmpty() && split.upcoming.isEmpty()) return
    var showEarlier by rememberSaveable { mutableStateOf(false) }
    val rows = (if (showEarlier) split.earlier else emptyList()) + split.upcoming.take(TIMELINE_CAP)
    Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S12)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ShifaSize.S2)) {
                Text(
                    tr(Res.string.todaysSchedule),
                    Modifier.semantics { heading() },
                    style = ShifaTextStyles.sectionHeading(),
                    color = ShifaColors.Text.Primary,
                )
                Text(
                    tr(Res.string.todayTimelineSubtitle),
                    style = ShifaTextStyles.metaSmall(),
                    color = ShifaColors.Text.Secondary,
                )
            }
            ShifaLink(tr(Res.string.seeAllInCalendar), actions.calendar)
        }
        if (split.earlier.isNotEmpty()) {
            ShifaActionButton(
                { showEarlier = !showEarlier },
                text =
                    if (showEarlier) {
                        tr(Res.string.hideEarlier)
                    } else {
                        trPlural(Res.plurals.showEarlierCount, split.earlier.size, split.earlier.size)
                    },
                variant = ShifaActionVariant.Surface,
            )
        }
        Column {
            rows.forEachIndexed { i, e ->
                TimelineRow(
                    e,
                    past = e in split.earlier,
                    current = e == schedule.hero && schedule.heroDay == null,
                    first = i == 0,
                    last = i == rows.lastIndex,
                    clock = clock,
                    now = now,
                ) { actions.openEntry(e) }
            }
        }
    }
}

@Composable
private fun TimelineRow(
    entry: CalendarEntry,
    past: Boolean,
    current: Boolean,
    first: Boolean,
    last: Boolean,
    clock: PracticeClock,
    now: Instant,
    onClick: () -> Unit,
) {
    val visit = entry as? CalendarEntry.Appointment
    // The current visit reads "Now" in brand colours once its slot has begun; otherwise the calendar's status chip.
    val chip =
        visit?.let {
            if (current && it.start <= now && !it.status.equals("COMPLETED", true)) {
                Triple(tr(Res.string.now), ShifaColors.Bg.SuccessSubtle, ShifaColors.Text.BrandStrong)
            } else {
                status(it.status)
            }
        }
    val dot =
        when {
            current -> ShifaColors.Icon.Brand
            visit == null -> ShifaColors.Icon.Disabled
            else -> chip?.third ?: ShifaColors.Icon.Disabled
        }
    // Intrinsic height: the dot's line runs the row's full height.
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(ShifaSize.S10),
    ) {
        Text(
            clock.hm(entry.start),
            Modifier.width(ShifaSize.S48).padding(top = ShifaSize.S18),
            style = ShifaTextStyles.timeLabel(),
            color = if (past) ShifaColors.Text.Tertiary else ShifaColors.Text.Primary,
        )
        // The dot on its line: the line runs on from the row above and to the row below.
        Column(Modifier.width(ShifaSize.S10).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.width(ShifaSize.S2).height(ShifaSize.S18).background(line(!first)))
            Box(
                Modifier
                    .size(ShifaSize.S10)
                    .background(dot, ShifaRadius.Full),
            )
            Box(Modifier.width(ShifaSize.S2).weight(1f).background(line(!last)))
        }
        val title =
            visit?.patientName ?: (entry as? CalendarEntry.Blocked)?.reason?.takeIf {
                it.isNotBlank()
            } ?: tr(Res.string.blocked)
        val description =
            listOfNotNull(
                clock.hm(entry.start),
                title,
                sub(entry, clock),
                chip?.first,
            )
                .joinToString(", ")
        Row(
            Modifier
                .weight(1f)
                .padding(vertical = ShifaSize.S4)
                .heightIn(min = ShifaSize.S56)
                .then(if (visit != null) Modifier.dropShadow(ShifaRadius.ControlMd, ShifaShadow.Card) else Modifier)
                .clip(ShifaRadius.ControlMd)
                .background(if (visit != null) ShifaColors.Bg.Surface else ShifaColors.Bg.Disabled)
                .then(
                    if (current) {
                        Modifier.border(
                            ShifaSize.S2,
                            ShifaColors.Border.Brand,
                            ShifaRadius.ControlMd,
                        )
                    } else {
                        Modifier
                    },
                ).pressable(onClick)
                .semantics(mergeDescendants = true) { contentDescription = description }
                .padding(horizontal = ShifaSize.S12, vertical = ShifaSize.S10),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ShifaSize.S10),
        ) {
            if (visit != null) {
                ShifaAvatar(visit.photoUrl, ShifaSize.S36)
            } else {
                Box(
                    Modifier.size(ShifaSize.S36).background(ShifaColors.Bg.Canvas, ShifaRadius.Full),
                    Alignment.Center,
                ) {
                    Icon(painterResource(Res.drawable.ic_block_14), null, tint = ShifaColors.Text.Secondary)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ShifaSize.S4)) {
                Text(
                    title,
                    style = ShifaTextStyles.contextName(),
                    color = ShifaColors.Text.Primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    sub(entry, clock),
                    style = ShifaTextStyles.metaSmall(),
                    color = ShifaColors.Text.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            chip?.let { (label, bg, ink) -> Chip(label, bg, ink) }
        }
    }
}

@Composable
private fun sub(
    entry: CalendarEntry,
    clock: PracticeClock,
): String =
    when (entry) {
        is CalendarEntry.Appointment -> {
            val kind = tr(if (entry.isVideo) Res.string.videoCall else Res.string.inClinic)
            listOf(kind, entry.reason).filter { it.isNotBlank() }.joinToString(" · ")
        }
        else -> "${clock.hm(entry.start)}–${clock.hm(entry.end)} · ${tr(Res.string.blocked)}"
    }

private fun line(visible: Boolean) = if (visible) ShifaColors.Border.Default else Color.Transparent

@Composable
private fun Pill(
    text: String,
    bg: Color,
    ink: Color,
    icon: DrawableResource? = null,
) = Row(
    Modifier
        .height(ShifaSize.S28)
        .background(bg, ShifaRadius.Full)
        .padding(horizontal = ShifaSize.S12),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6),
) {
    icon?.let { Icon(painterResource(it), null, tint = ink) }
    Text(text, style = ShifaTextStyles.tag(), color = ink, maxLines = 1)
}

@Composable
private fun Chip(
    text: String,
    bg: Color,
    ink: Color,
) = Row(
    Modifier
        .height(ShifaSize.S24)
        .background(bg, ShifaRadius.Full)
        .padding(horizontal = ShifaSize.S10),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6),
) {
    Box(Modifier.size(ShifaSize.S6).background(ink, ShifaRadius.Full))
    Text(text, style = ShifaTextStyles.tag(), color = ink, maxLines = 1)
}

@Composable
private fun Meta(
    icon: DrawableResource,
    text: String,
) = Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ShifaSize.S4)) {
    Icon(painterResource(icon), null, tint = ShifaColors.Text.Secondary)
    Text(
        text,
        style = ShifaTextStyles.metaSmall(),
        color = ShifaColors.Text.Secondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
