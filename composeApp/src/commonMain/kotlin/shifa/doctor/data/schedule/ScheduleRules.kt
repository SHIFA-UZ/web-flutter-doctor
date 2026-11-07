package shifa.doctor.data.schedule

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import shifa.doctor.data.model.RuleDto

/** One bookable range of a day, cut into [slotMinutes] slots by the backend. */
data class TimeRange(
    val start: LocalTime,
    val end: LocalTime,
    val slotMinutes: Int,
)

/** The slot sizes the editor offers (03 §2.2). */
val SLOT_SIZES = listOf(10, 15, 20, 30, 45, 60)

/**
 * The weekly template of one location (SCHED-1, 03 §2): weekday 1 = Monday … 7 = Sunday → its ranges in start order.
 * A day without ranges is off.
 */
data class WeeklySchedule(
    val days: Map<Int, List<TimeRange>> = emptyMap(),
) {
    fun ranges(weekday: Int): List<TimeRange> = days[weekday].orEmpty()

    fun with(
        weekday: Int,
        ranges: List<TimeRange>,
    ) = WeeklySchedule((days + (weekday to ranges.sortedBy { it.start })).filterValues { it.isNotEmpty() })

    /** The `PUT /schedule/rules?locationId=` body: every range, with the location in the body too (one scope). */
    fun toRules(locationId: Long?): List<RuleDto> =
        days.entries.sortedBy { it.key }.flatMap { (day, ranges) ->
            ranges.map { RuleDto(day, hhmm(it.start), hhmm(it.end), it.slotMinutes, locationId) }
        }

    companion object {
        /** From `GET /schedule/rules`; a row with an unreadable time or weekday is skipped (one bad row can't drop all). */
        fun of(rules: List<RuleDto>): WeeklySchedule =
            WeeklySchedule(
                rules.mapNotNull { r ->
                    val start = time(r.startTime) ?: return@mapNotNull null
                    val end = time(r.endTime) ?: return@mapNotNull null
                    if (r.weekday !in 1..7) return@mapNotNull null
                    r.weekday to TimeRange(start, end, r.slotMinutes)
                }.groupBy({ it.first }, { it.second }).mapValues { (_, v) -> v.sortedBy { it.start } },
            )
    }
}

/** Why a range can't be saved, checked before anything is sent (the backend refuses the same with a plain 400). */
enum class RangeProblem { EndNotAfterStart, SlotTooShort, TooManySlots, Overlaps }

/**
 * The backend's checks (`ScheduleController`): end after start (no range crosses midnight), slots of at least 5 minutes,
 * at most 200 slots in a range, no overlap with the day's [others] (touching is fine).
 */
fun rangeProblem(
    range: TimeRange,
    others: List<TimeRange>,
): RangeProblem? =
    when {
        range.end <= range.start -> RangeProblem.EndNotAfterStart
        range.slotMinutes < MIN_SLOT -> RangeProblem.SlotTooShort
        minutes(range) / range.slotMinutes > MAX_SLOTS -> RangeProblem.TooManySlots
        others.any { range.start < it.end && it.start < range.end } -> RangeProblem.Overlaps
        else -> null
    }

private fun minutes(r: TimeRange) = (r.end.toSecondOfDay() - r.start.toSecondOfDay()) / 60

private const val MIN_SLOT = 5
private const val MAX_SLOTS = 200

/**
 * Whether a new validity period [from]..[until] can be added next to [existing] (the backend's rule): inside an existing
 * one is fine (nothing changes), otherwise it must not overlap any. Dates inclusive.
 */
fun periodFits(
    from: LocalDate,
    until: LocalDate,
    existing: List<Pair<LocalDate, LocalDate>>,
): Boolean {
    if (from > until) return false
    if (existing.any { (f, u) -> from >= f && until <= u }) return true
    // The backend's overlap test (strict on both ends): a period may start on the day another ends.
    return existing.none { (f, u) -> from < u && until > f }
}

/** "09:00", or the time exactly as read when it had seconds or fractions (a load and save changes nothing). */
internal fun hhmm(t: LocalTime): String {
    if (t.second != 0 || t.nanosecond != 0) return t.toString()
    return "${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"
}

/** The whole value parsed ("09:00" or "09:00:30"); anything else is unreadable (the row is skipped). */
internal fun time(s: String): LocalTime? = runCatching { LocalTime.parse(s) }.getOrNull()
