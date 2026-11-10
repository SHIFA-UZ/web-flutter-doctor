package shifa.doctor.ui.patients

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.activityHint
import shifa.doctor.generated.resources.ic_calendar_16
import shifa.doctor.generated.resources.ic_check_circle_16
import shifa.doctor.generated.resources.ic_pin_16
import shifa.doctor.generated.resources.ic_video_16
import shifa.doctor.generated.resources.inClinic
import shifa.doctor.generated.resources.newAppointment
import shifa.doctor.generated.resources.noPatientAppointments
import shifa.doctor.generated.resources.noRecentActivity
import shifa.doctor.generated.resources.patientAppointmentHistory
import shifa.doctor.generated.resources.recentActivity
import shifa.doctor.generated.resources.today
import shifa.doctor.generated.resources.upcoming
import shifa.doctor.generated.resources.upcomingVisit
import shifa.doctor.generated.resources.videoCall
import shifa.doctor.generated.resources.viewFullHistory
import shifa.doctor.generated.resources.visitCancelled
import shifa.doctor.generated.resources.visitCompleted
import shifa.doctor.generated.resources.visitInProgress
import shifa.doctor.generated.resources.visitNoShow
import shifa.doctor.generated.resources.visitOther
import shifa.doctor.generated.resources.visitRequested
import shifa.doctor.generated.resources.yesterday
import shifa.doctor.ui.calendar.longDate
import shifa.doctor.ui.calendar.monthTitle
import shifa.doctor.ui.calendar.status
import shifa.doctor.ui.profile.ProfileChip
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.ShifaButtonSize
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.ui.widgets.ShifaListSkeleton
import shifa.doctor.ui.widgets.ShifaLoadError
import shifa.doctor.ui.widgets.ShifaPrimaryButton
import shifa.doctor.utils.displayTime
import shifa.doctor.utils.tr

/** "Recent activity" on the Overview: the latest six past events (upcoming visits aren't history), "View full history". */
@Composable
internal fun RecentActivity(
    state: PatientDetailUiState,
    actions: PatientDetailActions,
) = Column(
    Modifier.fillMaxWidth().background(ShifaColors.Bg.Surface, ShifaRadius.ControlLg).padding(ShifaSpacing.Inset.Md),
    verticalArrangement = Arrangement.spacedBy(ShifaSize.S10),
) {
    Text(tr(Res.string.recentActivity), style = ShifaTextStyles.panelTitle(), color = ShifaColors.Text.Primary)
    val error = state.visitsError
    val past = state.visits?.filterNot(Visit::upcoming)
    when {
        error != null -> Failed(error, actions, compact = true)
        past == null -> ShifaListSkeleton(count = 3)
        past.isEmpty() -> {
            Text(
                tr(Res.string.noRecentActivity),
                style = ShifaTextStyles.panelTitle(),
                color = ShifaColors.Text.Secondary,
            )
            Text(tr(Res.string.activityHint), style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Secondary)
        }
        else -> {
            past.take(RECENT).forEach { Event(it, state.today) }
            ShifaLink(tr(Res.string.viewFullHistory), { actions.select(RecordTab.Activity) })
        }
    }
}

/**
 * PAT-07 Activity (flow 06/11): upcoming visits apart on top, then the history newest first, grouped by month on the
 * practice clock. Visits only for now; documents and forms join with DOCS-04 and FORM-05.
 */
internal fun LazyListScope.activityRows(
    state: PatientDetailUiState,
    actions: PatientDetailActions,
    gap: Modifier,
) = visitRows(state, actions, gap) { visits ->
    if (visits.isEmpty()) return@visitRows item { Box(gap) { Empty(tr(Res.string.activityHint)) } }
    val (upcoming, past) = visits.partition(Visit::upcoming)
    // Soonest first among the upcoming ones.
    val groups =
        listOfNotNull(upcoming.takeIf { it.isNotEmpty() }?.let { null to it.reversed() }) +
            past.groupBy { it.date.year to it.date.monthNumber }.values.map { it.first().date to it }
    groups.forEach { (month, events) ->
        item {
            Text(
                month?.let { monthTitle(it) } ?: tr(Res.string.upcoming),
                gap,
                style = ShifaTextStyles.panelTitle(),
                color = ShifaColors.Text.Primary,
            )
        }
        items(events, key = { "event-${it.id}" }) { v ->
            Box(
                gap.background(ShifaColors.Bg.Surface, ShifaRadius.ControlMd).padding(ShifaSize.S12),
            ) { Event(v, state.today) }
        }
    }
}

/** PAT-07 Appointments (06 §2.5): "New appointment", then each visit with its date, times, place and status. */
internal fun LazyListScope.appointmentRows(
    state: PatientDetailUiState,
    actions: PatientDetailActions,
    gap: Modifier,
) {
    item {
        Row(gap, verticalAlignment = Alignment.CenterVertically) {
            Text(
                tr(Res.string.patientAppointmentHistory),
                Modifier.weight(1f),
                style = ShifaTextStyles.panelTitle(),
                color = ShifaColors.Text.Primary,
            )
            actions.book?.let { ShifaPrimaryButton(tr(Res.string.newAppointment), it, size = ShifaButtonSize.Compact) }
        }
    }
    visitRows(state, actions, gap) { visits ->
        if (visits.isEmpty()) {
            item { Box(gap) { Empty(tr(Res.string.noPatientAppointments)) } }
        } else {
            items(visits, key = { "visit-${it.id}" }) { Box(gap) { VisitCard(it) } }
        }
    }
}

/** The visits once read: a skeleton before, the error with Retry after a failed read (Flutter showed raw text). */
private fun LazyListScope.visitRows(
    state: PatientDetailUiState,
    actions: PatientDetailActions,
    gap: Modifier,
    rows: LazyListScope.(List<Visit>) -> Unit,
) {
    val error = state.visitsError
    val visits = state.visits
    when {
        error != null ->
            item {
                Box(gap) { Failed(error, actions, compact = false) }
            }
        visits == null -> item { Box(gap) { ShifaListSkeleton(count = 3) } }
        else -> rows(visits)
    }
}

@Composable
private fun Failed(
    error: shifa.doctor.ui.emailcode.CodeBanner,
    actions: PatientDetailActions,
    compact: Boolean,
) = ShifaLoadError(tr(error.text), actions.retryVisits, requestId = error.requestId, compact = compact)

@Composable
private fun Empty(text: String) =
    Text(
        text,
        Modifier.fillMaxWidth().background(ShifaColors.Bg.Surface, ShifaRadius.ControlLg).padding(ShifaSize.S24),
        style = ShifaTextStyles.bodyText(),
        color = ShifaColors.Text.Secondary,
        textAlign = TextAlign.Center,
    )

/** "4 November 2026 · 09:00–09:30", "Check-up · In Clinic" (or the place, or Video Call), the status pill. */
@Composable
private fun VisitCard(v: Visit) =
    Row(
        Modifier.fillMaxWidth().background(ShifaColors.Bg.Surface, ShifaRadius.ControlLg).padding(ShifaSize.S12),
        horizontalArrangement = Arrangement.spacedBy(ShifaSize.S12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tile(
            if (v.video) Res.drawable.ic_video_16 else Res.drawable.ic_pin_16,
            ShifaColors.Bg.Canvas,
            ShifaColors.Text.Primary,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ShifaSize.S2)) {
            Text(
                longDate(v.date) + " · " + times(v),
                style = ShifaTextStyles.buttonCompact(),
                color = ShifaColors.Text.Primary,
            )
            val place = if (v.video) tr(Res.string.videoCall) else v.location ?: tr(Res.string.inClinic)
            Text(
                listOfNotNull(v.reason, place).joinToString(" · "),
                style = ShifaTextStyles.metaSmall(),
                color = ShifaColors.Text.Secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        status(v.status)?.let { (label, bg, ink) -> ProfileChip(label, bg, ink) }
    }

/** One feed row: a tinted icon, what happened, the reason and time, and when (Today / Yesterday / the date). */
@Composable
private fun Event(
    v: Visit,
    today: LocalDate,
) = Row(horizontalArrangement = Arrangement.spacedBy(ShifaSize.S10), verticalAlignment = Alignment.CenterVertically) {
    val (title, icon, tint) = event(v.status, v.upcoming)
    Tile(icon, tint.first, tint.second)
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ShifaSize.S2)) {
        Text(tr(title), style = ShifaTextStyles.buttonCompact(), color = ShifaColors.Text.Primary)
        val known = v.status?.uppercase() in KNOWN
        Text(
            listOfNotNull(status(v.status)?.first?.takeUnless { known }, v.reason, displayTime(v.start))
                .joinToString(" · "),
            style = ShifaTextStyles.metaSmall(),
            color = ShifaColors.Text.Secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    Text(
        when (v.date) {
            today -> tr(Res.string.today)
            today.minus(DatePeriod(days = 1)) -> tr(Res.string.yesterday)
            else -> longDate(v.date)
        },
        style = ShifaTextStyles.metaSmall(),
        color = ShifaColors.Text.Secondary,
    )
}

@Composable
private fun Tile(
    icon: DrawableResource,
    bg: Color,
    ink: Color,
) = Box(Modifier.size(ShifaSize.S36).background(bg, ShifaRadius.ControlMd), contentAlignment = Alignment.Center) {
    Icon(painterResource(icon), null, tint = ink)
}

private fun times(v: Visit) = displayTime(v.start) + (v.end?.let { "–" + displayTime(it) } ?: "")

/** The board's event kinds (DaPatients `activity`): completed in teal, cancelled and no-show in red, the rest neutral. */
@Composable
private fun event(
    status: String?,
    upcoming: Boolean,
): Triple<StringResource, DrawableResource, Pair<Color, Color>> {
    val teal = ShifaColors.Bg.SuccessSubtle to ShifaColors.Icon.Brand
    val red = ShifaColors.Bg.ErrorSubtle to ShifaColors.Text.Error
    val neutral = ShifaColors.Bg.Canvas to ShifaColors.Text.Primary
    return when (status?.uppercase()) {
        "COMPLETED" -> Triple(Res.string.visitCompleted, Res.drawable.ic_check_circle_16, teal)
        "CANCELLED" -> Triple(Res.string.visitCancelled, Res.drawable.ic_calendar_16, red)
        "NO_SHOW" -> Triple(Res.string.visitNoShow, Res.drawable.ic_calendar_16, red)
        "IN_PROGRESS" -> Triple(Res.string.visitInProgress, Res.drawable.ic_calendar_16, teal)
        "REQUESTED" -> Triple(Res.string.visitRequested, Res.drawable.ic_calendar_16, neutral)
        // A booked visit whose time has passed (never started, never closed) is no longer "upcoming".
        "CONFIRMED", "PENDING_PAYMENT" ->
            if (upcoming) {
                Triple(Res.string.upcomingVisit, Res.drawable.ic_calendar_16, teal)
            } else {
                Triple(Res.string.visitOther, Res.drawable.ic_calendar_16, neutral)
            }
        // Anything else (EXPIRED, PAYMENT_FAILED, a new status) is neutral, its status named under it: never "upcoming".
        else -> Triple(Res.string.visitOther, Res.drawable.ic_calendar_16, neutral)
    }
}

private const val RECENT = 6

private val KNOWN = setOf("COMPLETED", "CANCELLED", "NO_SHOW", "IN_PROGRESS", "REQUESTED", "CONFIRMED")
