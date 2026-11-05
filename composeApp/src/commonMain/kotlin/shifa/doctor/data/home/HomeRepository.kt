package shifa.doctor.data.home

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import shifa.doctor.data.calendar.CalendarRepository
import shifa.doctor.data.calendar.DayKey
import shifa.doctor.data.calendar.calendarRepository
import shifa.doctor.data.model.CalendarEntry
import shifa.doctor.data.session.sessionRepository
import shifa.doctor.utils.PracticeClock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Home's schedule (HOME-01, 02 §1.4, flow 02/02): today's visits with a patient, by start, and the hero: the visit in
 * its slot now, else the next one today, else the first one in the next [LOOK_AHEAD_DAYS] days ([heroDay] set then).
 */
data class HomeSchedule(
    val today: List<CalendarEntry.Appointment> = emptyList(),
    val hero: CalendarEntry.Appointment? = null,
    // The hero is in its slot (or already in progress).
    val heroNow: Boolean = false,
    // The hero's day when it isn't today (the card shows its date then).
    val heroDay: LocalDate? = null,
)

/** A visit that can still be started: not completed, cancelled or a no-show (Flutter picked cancelled ones, 02/02 §8). */
private fun CalendarEntry.Appointment.open(): Boolean =
    status?.uppercase() !in setOf("COMPLETED", "CANCELLED", "NO_SHOW")

/**
 * The hero among one day's visits at [now]: the one in its slot; else one still in progress past its slot (not ended
 * yet); else the first still to come. The second of each pair says it is "now".
 */
fun pickHero(
    visits: List<CalendarEntry.Appointment>,
    now: Instant,
): Pair<CalendarEntry.Appointment, Boolean>? {
    val open = visits.filter { it.open() }.sortedBy { it.start }
    open.firstOrNull { it.start <= now && now < it.end }?.let { return it to true }
    open.firstOrNull {
        it.status.equals(
            "IN_PROGRESS",
            ignoreCase = true,
        ) && it.start <= now
    }?.let { return it to true }
    return open.firstOrNull { it.start >= now }?.let { it to false }
}

/**
 * Reads Home's schedule from the calendar's day cache (shared with the Calendar tab, so switching tabs doesn't read
 * again). The backend has no range query: when today has no hero, the next days are read four at a time, in order,
 * and the first visit found wins (Flutter read them one by one).
 */
class HomeRepository(
    private val calendar: CalendarRepository = calendarRepository,
    private val signedIn: () -> Boolean = { sessionRepository.session.value != null },
) {
    suspend fun schedule(
        clock: PracticeClock,
        force: Boolean = false,
    ): Result<HomeSchedule> {
        // Signed out (Home mid sign-out): an empty schedule, not an error (02 §1.10, Flutter threw).
        if (!signedIn()) return Result.success(HomeSchedule())
        // One instant for "today" and "now", so a read crossing midnight can't mix two dates.
        val now = clock.nowInstant()
        val today = clock.local(now).date
        val entries = calendar.day(DayKey(today), force).getOrElse { return Result.failure(it) }
        val visits = entries.visits()
        pickHero(visits, now)?.let { (hero, inSlot) -> return Result.success(HomeSchedule(visits, hero, inSlot)) }
        (1..LOOK_AHEAD_DAYS).map { today.plus(DatePeriod(days = it)) }.chunked(BATCH).forEach { days ->
            // A day that can't be read is skipped: the hero then comes from a later day, never an error for Home.
            val read =
                coroutineScope {
                    days.map {
                            d ->
                        async { d to calendar.day(DayKey(d), force).getOrNull() }
                    }.awaitAll()
                }
            read.forEach { (day, dayEntries) ->
                pickHero(dayEntries.orEmpty().visits(), now)?.let { (hero, _) ->
                    return Result.success(HomeSchedule(visits, hero, heroNow = false, heroDay = day))
                }
            }
        }
        return Result.success(HomeSchedule(visits))
    }

    private fun List<CalendarEntry>.visits() =
        filterIsInstance<CalendarEntry.Appointment>().filter { !it.patientName.isNullOrBlank() }.sortedBy { it.start }

    companion object {
        const val LOOK_AHEAD_DAYS = 14
        private const val BATCH = 4
    }
}

val homeRepository = HomeRepository()

/**
 * Home's refresh when its tab comes back (02 §1.10): skipped within a [cooldown] of the last unless [force]d
 * (pull-to-refresh), and never while one runs, forced or not, so an older answer can't overwrite a newer one. Not
 * module globals as in Flutter: one gate per Home.
 */
class RefreshGate(
    private val cooldown: Duration = 60.seconds,
    private val time: TimeSource = TimeSource.Monotonic,
) {
    private var last: TimeMark? = null
    private var running = false

    /** Runs [refresh] when allowed; false when skipped. */
    suspend fun run(
        force: Boolean = false,
        refresh: suspend () -> Unit,
    ): Boolean {
        val l = last
        if (running || (!force && l != null && l.elapsedNow() < cooldown)) return false
        last = time.markNow()
        running = true
        try {
            refresh()
        } finally {
            running = false
        }
        return true
    }
}
