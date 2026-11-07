package shifa.doctor.data.calendar

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import shifa.doctor.data.model.CalendarEntry
import shifa.doctor.utils.PracticeClock

/**
 * One event per appointment change (CAL-2, 03 §5): booked, cancelled, moved, a block that cancelled some, a push about
 * one. [days] are the practice dates it touched. The calendar refreshes those days once, through
 * [CalendarRepository.changed]; other screens (home, analytics) listen here instead of chaining their own reloads.
 */
object AppointmentChanges {
    private val _events = MutableSharedFlow<Set<LocalDate>>(extraBufferCapacity = 16)
    val events: SharedFlow<Set<LocalDate>> = _events.asSharedFlow()

    // Suspends while a slow listener catches up rather than dropping a change.
    internal suspend fun emit(days: Set<LocalDate>) = _events.emit(days)
}

/**
 * After an appointment change: today and [days] are read again once (forced, deduplicated; Flutter read the same day
 * two or three times per booking), then [AppointmentChanges] tells everyone else.
 */
suspend fun CalendarRepository.changed(
    clock: PracticeClock,
    vararg days: LocalDate,
    doctorId: Long? = null,
) {
    val touched = (days.toSet() + clock.today())
    // Every cached view of those dates in this calendar (any location) is stale now and read again; another doctor's
    // calendar isn't touched by this change (CAL-10: a colleague's booking leaves the doctor's own days fresh).
    invalidate(*state.value.days.keys.filter { it.date in touched && it.doctorId == doctorId }.toTypedArray())
    // Four at a time, the given days first (a block over a vacation touches many; Flutter read them one by one).
    (days.toList() + clock.today()).distinct().chunked(BATCH).forEach { batch ->
        coroutineScope { batch.map { async { day(DayKey(it, doctorId), force = true) } }.awaitAll() }
    }
    AppointmentChanges.emit(touched)
}

/**
 * The days of [dates] the calendar holds or is reading now: after a change over a long range these are re-read (the rest
 * are read when shown). A read still running started before the change, so it counts too.
 */
fun CalendarRepository.knownDays(dates: List<LocalDate>): List<LocalDate> {
    return (state.value.days.keys + pending()).map { it.date }.distinct().filter { it in dates }
}

/** Today's appointments in the practice zone, cancelled ones left out, in time order (reads the day if needed). */
suspend fun CalendarRepository.todayAppointments(
    clock: PracticeClock,
    doctorId: Long? = null,
): Result<List<CalendarEntry.Appointment>> =
    day(DayKey(clock.today(), doctorId)).map { entries ->
        entries.filterIsInstance<CalendarEntry.Appointment>().filter { !it.cancelled }
    }

/**
 * The appointment to show next (03 §5): one in progress now, else the next one today, else the first in the next
 * [horizonDays] days, read day by day (from cache when fresh) and stopping at the first hit. Completed, cancelled and
 * no-show appointments don't count. Null when there is none; a failure when a day before the hit can't be read (an
 * earlier appointment may be on it).
 */
suspend fun CalendarRepository.nextAppointment(
    clock: PracticeClock,
    doctorId: Long? = null,
    horizonDays: Int = 14,
): Result<CalendarEntry.Appointment?> {
    val now = clock.nowInstant()
    val today = clock.today()
    for (offset in 0..horizonDays) {
        val date = today.plus(DatePeriod(days = offset))
        val entries = day(DayKey(date, doctorId)).getOrElse { return Result.failure(it) }
        val open = entries.filterIsInstance<CalendarEntry.Appointment>().filter { it.open }
        open.firstOrNull { it.start <= now && now < it.end }?.let { return Result.success(it) }
        open.firstOrNull { it.start > now }?.let { return Result.success(it) }
    }
    return Result.success(null)
}

private val CalendarEntry.Appointment.open: Boolean
    get() = status?.uppercase() !in setOf("CANCELLED", "COMPLETED", "NO_SHOW")

private const val BATCH = 4
