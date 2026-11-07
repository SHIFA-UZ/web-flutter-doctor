package shifa.doctor.data.calendar

import kotlinx.datetime.Instant
import shifa.doctor.data.model.CalendarEntry

/**
 * The ends reachable from [start] through free slots that each begin exactly where the previous one ended, all at
 * [locationId] (03 §1.10, the backend's `SlotAvailabilityService` rule). On instants, so a chain across a DST switch
 * keeps its real length; it never crosses a venue, an appointment or a block, and stops after 400 hops like Flutter.
 */
fun chainEnds(
    day: List<CalendarEntry>,
    start: Instant,
    locationId: Long?,
): List<Instant> {
    val byStart =
        day.filterIsInstance<CalendarEntry.FreeSlot>().filter { it.locationId == locationId }.associateBy { it.start }
    val ends = mutableListOf<Instant>()
    var cursor = start
    repeat(MAX_HOPS) {
        val next = byStart[cursor]?.takeIf { it.end > cursor } ?: return ends
        ends += next.end
        cursor = next.end
    }
    return ends
}

private const val MAX_HOPS = 400
