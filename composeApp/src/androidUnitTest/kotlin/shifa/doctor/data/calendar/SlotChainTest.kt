package shifa.doctor.data.calendar

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.Test
import shifa.doctor.data.model.CalendarEntry

// Ported from Flutter's test/consecutive_slot_range_test.dart (the five cases), plus DST and a venue boundary.
class SlotChainTest {
    private fun free(
        start: String,
        end: String,
        location: Long? = null,
    ) = CalendarEntry.FreeSlot(Instant.parse(start), Instant.parse(end), location)

    private fun minutes(
        from: Instant,
        to: Instant,
    ) = (to - from).inWholeMinutes.toInt()

    @Test
    fun `two consecutive hourly slots at one venue chain`() {
        val s0 = free("2026-06-01T07:00:00Z", "2026-06-01T08:00:00Z", 1)
        val s1 = free("2026-06-01T08:00:00Z", "2026-06-01T09:00:00Z", 1)
        assertThat(chainEnds(listOf(s0, s1), s0.start, 1)).contains(s1.end)
    }

    @Test
    fun `a gap between slots breaks the chain`() {
        val s0 = free("2026-06-01T07:00:00Z", "2026-06-01T08:00:00Z")
        val skip = free("2026-06-01T09:00:00Z", "2026-06-01T10:00:00Z")
        assertThat(chainEnds(listOf(s0, skip), s0.start, null)).containsExactly(s0.end)
    }

    @Test
    fun `the ends include the full chain and two hours book as 120 minutes`() {
        val slots =
            listOf(
                free("2026-06-01T07:00:00Z", "2026-06-01T08:00:00Z", 5),
                free("2026-06-01T08:00:00Z", "2026-06-01T09:00:00Z", 5),
            )
        val ends = chainEnds(slots, slots.first().start, 5)
        assertThat(ends).hasSize(2)
        assertThat(minutes(slots.first().start, ends.last())).isEqualTo(120)
    }

    @Test
    fun `an end at 11 00 Tashkent from 08 00 there books 180 minutes, for a booking and a change alike`() {
        // 08:00–11:00 Asia/Tashkent = 03:00–06:00 UTC in 30-minute slots.
        val slots =
            (0 until 6).map {
                val m = 180 + it * 30
                free(
                    "2026-06-01T${(m / 60).toString().padStart(2, '0')}:${(m % 60).toString().padStart(2, '0')}:00Z",
                    "2026-06-01T${((m + 30) / 60).toString().padStart(
                        2,
                        '0',
                    )}:${((m + 30) % 60).toString().padStart(2, '0')}:00Z",
                    1,
                )
            }
        val end = chainEnds(slots, slots.first().start, 1).last()
        assertThat(end.toLocalDateTime(TimeZone.of("Asia/Tashkent")).hour).isEqualTo(11)
        assertThat(minutes(slots.first().start, end)).isEqualTo(180)
    }

    @Test
    fun `across the autumn DST switch the minutes are real, not wall clock`() {
        // Berlin, 25 Oct 2026: 02:00 CEST → 02:00 CET. Wall clock 02:00 → 03:00 is two real hours.
        val s0 = free("2026-10-25T00:00:00Z", "2026-10-25T01:00:00Z", 1)
        val s1 = free("2026-10-25T01:00:00Z", "2026-10-25T02:00:00Z", 1)
        val end = chainEnds(listOf(s0, s1), s0.start, 1).last()
        val berlin = TimeZone.of("Europe/Berlin")
        assertThat(s0.start.toLocalDateTime(berlin).hour).isEqualTo(2)
        assertThat(end.toLocalDateTime(berlin).hour).isEqualTo(3)
        assertThat(minutes(s0.start, end)).isEqualTo(120)
    }

    @Test
    fun `a chain never crosses into another venue`() {
        val a = free("2026-06-01T07:00:00Z", "2026-06-01T07:30:00Z", 1)
        val b = free("2026-06-01T07:30:00Z", "2026-06-01T08:00:00Z", 2)
        assertThat(chainEnds(listOf(a, b), a.start, 1)).containsExactly(a.end)
    }
}
