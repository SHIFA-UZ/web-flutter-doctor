package shifa.doctor.ui.calendar

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.Test
import shifa.doctor.data.model.CalendarEntry
import shifa.doctor.ui.visit.minutesSeconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class WeekGridTest {
    // Minutes of the day straight from UTC, so the fixtures read as wall times.
    private val minuteOf = { t: Instant -> ((t.epochSeconds / 60) % (24 * 60)).toInt() }

    private fun free(
        from: String,
        to: String,
    ) = CalendarEntry.FreeSlot(Instant.parse("2026-11-04T$from:00Z"), Instant.parse("2026-11-04T$to:00Z"))

    @Test
    fun `the week runs Monday to Sunday around any day`() {
        assertThat(weekStart(LocalDate(2026, 11, 4))).isEqualTo(LocalDate(2026, 11, 2)) // a Wednesday
        assertThat(weekStart(LocalDate(2026, 11, 2))).isEqualTo(LocalDate(2026, 11, 2))
        assertThat(weekOf(LocalDate(2026, 11, 8)).last()).isEqualTo(LocalDate(2026, 11, 8)) // a Sunday
    }

    @Test
    fun `hours span 08 to 18 at least, widen to whole hours, ignore all-day blocks`() {
        assertThat(gridHours(emptyList(), minuteOf)).isEqualTo(480 until 1080)
        assertThat(gridHours(listOf(listOf(free("07:30", "08:00"), free("18:10", "18:40"))), minuteOf))
            .isEqualTo(420 until 1140)
        val allDay =
            CalendarEntry.Blocked(Instant.parse("2026-11-04T00:00:00Z"), Instant.parse("2026-11-05T00:00:00Z"), 1, null)
        assertThat(gridHours(listOf(listOf(allDay)), minuteOf)).isEqualTo(480 until 1080)
    }

    @Test
    fun `names shorten to the initial, the waiting clock reads minutes and seconds`() {
        assertThat(shortName("Malika Tursunova")).isEqualTo("M. Tursunova")
        assertThat(shortName("Dilnoza")).isEqualTo("Dilnoza")
        assertThat(shortName("  ")).isNull()
        assertThat(minutesSeconds(3.minutes + 12.seconds)).isEqualTo("03:12")
        assertThat(minutesSeconds(75.minutes + 2.seconds)).isEqualTo("75:02")
    }
}
