package shifa.doctor.ui.calendar

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LanesTest {
    @Test
    fun `rows that do not overlap keep the full width`() {
        assertThat(lanes(listOf(540 to 570, 570 to 600))).containsExactly(0 to 1, 0 to 1).inOrder()
    }

    @Test
    fun `two rows at the same time sit side by side`() {
        assertThat(lanes(listOf(540 to 570, 540 to 570, 600 to 630))).containsExactly(0 to 2, 1 to 2, 0 to 1).inOrder()
    }

    @Test
    fun `a lane is reused once its row has ended`() {
        // 09:00–10:00 beside 09:00–09:30 and then 09:30–10:00: two lanes, not three (the shorter row takes the first).
        assertThat(lanes(listOf(540 to 600, 540 to 570, 570 to 600))).containsExactly(1 to 2, 0 to 2, 0 to 2).inOrder()
    }
}
