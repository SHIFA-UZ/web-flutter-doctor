package shifa.doctor.ui.calendar

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.LocalDate
import org.junit.Test
import shifa.doctor.data.model.DayOccupancy

class MonthPickerTest {
    @Test
    fun `the shade follows the booked share, a blocked-only day is tinted, an unread day is plain`() {
        assertThat(dayShade(null)).isEqualTo(DayShade.None)
        assertThat(dayShade(DayOccupancy(16, 0, 0))).isEqualTo(DayShade.None)
        assertThat(dayShade(DayOccupancy(10, 2, 1))).isEqualTo(DayShade.Light) // 2 of 12 booked
        assertThat(dayShade(DayOccupancy(4, 4, 0))).isEqualTo(DayShade.Light) // half booked is still light on the board
        assertThat(dayShade(DayOccupancy(2, 6, 0))).isEqualTo(DayShade.Busy)
        assertThat(dayShade(DayOccupancy(0, 0, 2))).isEqualTo(DayShade.None) // a midday block alone isn't a day off
        assertThat(dayShade(DayOccupancy(0, 0, 1, fullDayBlock = true))).isEqualTo(DayShade.Blocked)
        assertThat(dayShade(DayOccupancy(0, 0, 0))).isEqualTo(DayShade.None)
    }

    @Test
    fun `the grid starts on Monday and pads both ends to full weeks`() {
        // November 2026 starts on a Sunday: six empty cells first; 30 days; ends on a Monday, six after.
        val cells = monthCells(LocalDate(2026, 11, 18))
        assertThat(cells.take(7)).containsExactly(null, null, null, null, null, null, LocalDate(2026, 11, 1)).inOrder()
        assertThat(cells.size % 7).isEqualTo(0)
        assertThat(cells.filterNotNull()).hasSize(30)
        // February 2027 starts on a Monday: no padding before, exactly four weeks.
        assertThat(monthCells(LocalDate(2027, 2, 1))).hasSize(28)
    }
}
