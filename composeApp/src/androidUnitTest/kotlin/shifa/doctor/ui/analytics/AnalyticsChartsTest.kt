package shifa.doctor.ui.analytics

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import shifa.doctor.data.model.ConsultationTypes

class AnalyticsChartsTest {
    @Test
    fun `the axis tops 20 percent over the highest day with about five steps`() {
        assertThat(trendAxis(0)).isEqualTo(10 to 2)
        assertThat(trendAxis(1)).isEqualTo(2 to 1)
        assertThat(trendAxis(4)).isEqualTo(5 to 1)
        assertThat(trendAxis(7)).isEqualTo(9 to 2)
        assertThat(trendAxis(1000)).isEqualTo(1200 to 240)
    }

    @Test
    fun `about seven date labels whatever the range`() {
        assertThat(labelEvery(1)).isEqualTo(1)
        assertThat(labelEvery(7)).isEqualTo(1)
        assertThat(labelEvery(30)).isEqualTo(5)
        assertThat(labelEvery(90)).isEqualTo(13)
    }

    @Test
    fun `the shares always add up to 100`() {
        assertThat(shares(ConsultationTypes(video = 8, inPerson = 17))).isEqualTo(68 to 32)
        // 33.5 / 66.5 would round to 101 % each on its own.
        assertThat(shares(ConsultationTypes(video = 133, inPerson = 67))).isEqualTo(34 to 66)
        assertThat(shares(ConsultationTypes(video = 1, inPerson = 1))).isEqualTo(50 to 50)
        assertThat(shares(ConsultationTypes(0, 0))).isEqualTo(0 to 0)
    }
}
