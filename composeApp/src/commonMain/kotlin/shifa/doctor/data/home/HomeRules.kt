package shifa.doctor.data.home

import kotlinx.datetime.Instant
import shifa.doctor.data.model.CalendarEntry
import kotlin.time.Duration.Companion.minutes

/** The hero's status pill (board `DaHome`): in its slot, requested, starting within the hour ("In 12 min"), or later. */
enum class HeroPill { Now, Waiting, Soon, Confirmed }

data class HeroLook(
    val pill: HeroPill,
    // Minutes until the start, for "In 12 min".
    val minutes: Long,
    val canStart: Boolean,
    // When Start opens, while it can't yet; null when it can, or for a requested visit.
    val opensAt: Instant?,
)

/** Start opens this long before the visit (flow 02/02 §7); a video call only 5 minutes before (the backend's join window). */
val START_EARLY = 15.minutes
val START_EARLY_VIDEO = 5.minutes

/**
 * The hero card at [now] (HOME-03, flow 02/02 §4, §7; board `DaHome`). Start: a visit today, not merely requested,
 * in progress or at most 15 minutes away (5 for video). Recomputed by the screen's ticker, so Start opens without a refresh (Flutter
 * never ticked).
 */
fun heroLook(
    schedule: HomeSchedule,
    now: Instant,
): HeroLook? {
    val hero = schedule.hero ?: return null
    val today = schedule.heroDay == null
    val status = hero.status?.uppercase()
    val inProgress = status == "IN_PROGRESS"
    val requested = status == "REQUESTED"
    val opens = hero.start - if (hero.isVideo) START_EARLY_VIDEO else START_EARLY
    val canStart = today && !requested && (inProgress || now >= opens)
    val minutes = (hero.start - now).inWholeMinutes
    val pill =
        when {
            inProgress || (today && hero.start <= now && now < hero.end) -> HeroPill.Now
            requested -> HeroPill.Waiting
            today && minutes in 0..60 -> HeroPill.Soon
            else -> HeroPill.Confirmed
        }
    return HeroLook(pill, minutes, canStart, opens.takeIf { !canStart && !requested })
}

/** Today's timeline (board `DaHome`): visits and blocked times by start, the finished ones folded away as "earlier". */
data class HomeTimeline(
    val earlier: List<CalendarEntry>,
    val upcoming: List<CalendarEntry>,
)

/**
 * Splits today at [now]: completed and no-show visits, and anything ended (except a visit still in progress), are
 * earlier. Cancelled visits stay in place until their slot ends, with their own chip (Flutter dropped completed ones).
 */
fun homeTimeline(
    schedule: HomeSchedule,
    now: Instant,
): HomeTimeline {
    val rows = (schedule.today + schedule.blocks).sortedBy { it.start }
    val (earlier, upcoming) =
        rows.partition { e ->
            val status = (e as? CalendarEntry.Appointment)?.status?.uppercase()
            status == "COMPLETED" || status == "NO_SHOW" || (e.end <= now && status != "IN_PROGRESS")
        }
    return HomeTimeline(earlier, upcoming)
}

/**
 * The schedule needs reading again at [now]: the practice day turned, or today's hero isn't the one [pickHero] would
 * choose now (it left its slot, or another visit's slot began while it ran over). The screen's ticker asks; the read
 * stays gated.
 */
fun HomeSchedule.stale(
    now: Instant,
    today: kotlinx.datetime.LocalDate,
): Boolean {
    if (day != null && day != today) return true
    val hero = hero ?: return false
    return heroDay == null && pickHero(this.today, now)?.first?.appointmentId != hero.appointmentId
}
