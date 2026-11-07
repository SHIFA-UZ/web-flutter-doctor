package shifa.doctor.data.appointments

import kotlinx.datetime.Instant
import shifa.doctor.data.calendar.chainEnds
import shifa.doctor.data.model.CalendarEntry
import shifa.doctor.utils.PracticeClock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** What the panel's main button does (flow 04/05 §4). */
enum class CtaKind { Start, Continue, OpenSummary, Ended }

/** Why the main button is off, when it is. */
enum class CtaHint { Past, VideoTooEarly, VideoEnded }

data class Cta(
    val kind: CtaKind,
    val enabled: Boolean,
    val hint: CtaHint? = null,
)

/**
 * The main button by status and time, all on instants with "today" in the practice zone (flow 04/05 §4 table):
 * completed → Open Summary; in progress → Continue; a cancelled one has no button (null); a video visit may start from
 * 5 minutes before until 1 hour after its end; an in-person visit that ended earlier today can still start (same-day
 * grace); anything ended on an earlier day is Ended.
 */
fun ctaFor(
    appointment: CalendarEntry.Appointment,
    clock: PracticeClock,
    now: Instant = clock.nowInstant(),
): Cta? {
    val status = appointment.status?.uppercase()
    return when {
        // A cancelled or no-show visit is read-only: no main button (flow 05/01 §5).
        status == "CANCELLED" || status == "NO_SHOW" -> null
        status == "COMPLETED" -> Cta(CtaKind.OpenSummary, true)
        status == "IN_PROGRESS" -> Cta(CtaKind.Continue, true)
        appointment.isVideo && now < appointment.start - 5.minutes -> Cta(CtaKind.Start, false, CtaHint.VideoTooEarly)
        appointment.isVideo && now >= appointment.end + 1.hours -> Cta(CtaKind.Ended, false, CtaHint.VideoEnded)
        appointment.isVideo -> Cta(CtaKind.Start, true)
        now >= appointment.end && clock.local(
            appointment.end,
        ).date < clock.local(now).date -> Cta(CtaKind.Ended, false, CtaHint.Past)
        else -> Cta(CtaKind.Start, true)
    }
}

/**
 * The backend's no-show window (BE-10): a confirmed or started visit, from its start until a day after its end. The
 * calendar offers it for a confirmed visit whose patient hasn't come; the video call while the doctor waits alone.
 */
fun mayMarkNoShow(
    status: String?,
    start: Instant,
    end: Instant,
    now: Instant,
): Boolean = status?.uppercase() in setOf("CONFIRMED", "IN_PROGRESS") && now >= start && now <= end + 24.hours

/** The calendar panel's late banner: confirmed, not started, inside the window. */
fun CalendarEntry.Appointment.mayMarkNoShow(now: Instant) =
    status.equals("CONFIRMED", ignoreCase = true) && mayMarkNoShow(status, start, end, now)

/** Ended before now (its UTC end): no move, resize or cancel (flow 04/05 §3). */
fun CalendarEntry.Appointment.past(now: Instant) = end <= now

/** Not past, not cancelled or completed: the panel offers Reschedule, Adjust duration and Cancel. */
fun CalendarEntry.Appointment.changeable(now: Instant) =
    !past(now) && status?.uppercase() !in setOf("CANCELLED", "COMPLETED", "NO_SHOW")

/**
 * The end times a visit can be resized to (flow 04/07 §3B), in minutes from its start: the current length, shorter ones
 * stepping back by the day's slot length, and longer ones by chaining the free slots that follow it without a gap at
 * the same location. Ascending.
 */
fun durationOptions(
    appointment: CalendarEntry.Appointment,
    day: List<CalendarEntry>,
): List<Int> {
    val current = (appointment.end - appointment.start).inWholeMinutes.toInt()
    val free = day.filterIsInstance<CalendarEntry.FreeSlot>().filter { it.locationId == appointment.locationId }
    // The grain: the GCD of the free-slot lengths there, 5–240 minutes (04/07 §4); none free → the visit's own length.
    val grain =
        free.map { (it.end - it.start).inWholeMinutes.toInt() }.filter { it > 0 }.reduceOrNull(::gcd)?.coerceIn(
            MIN_MINUTES,
            MAX_GRAIN,
        ) ?: current
    val shorter = generateSequence(current - grain) { it - grain }.takeWhile { it >= MIN_MINUTES }.toList()
    val longer =
        chainEnds(day, appointment.end, appointment.locationId).map { (it - appointment.start).inWholeMinutes.toInt() }
    return (shorter + current + longer).sorted()
}

/**
 * Whether a visit of [minutes] fits from [slot]'s start in free slots chained without a gap at the same location, so a
 * move can keep its length (04/07 §4 target; Flutter sent the picked slot's length and silently shortened it).
 */
fun fits(
    slot: CalendarEntry.FreeSlot,
    day: List<CalendarEntry>,
    minutes: Int,
): Boolean {
    val until = slot.start + minutes.minutes
    return chainEnds(day, slot.start, slot.locationId).any { it >= until }
}

private tailrec fun gcd(
    a: Int,
    b: Int,
): Int = if (b == 0) a else gcd(b, a % b)

private const val MIN_MINUTES = 5
private const val MAX_GRAIN = 240
