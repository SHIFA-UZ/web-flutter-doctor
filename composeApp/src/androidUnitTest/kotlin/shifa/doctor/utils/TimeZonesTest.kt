package shifa.doctor.utils

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.TimeZone
import org.junit.Test

class TimeZonesTest {
    @Test
    fun `the detected zone leads the short list, typing searches every zone`() {
        assertThat(
            TimeZones.options("", "Europe/Paris").take(2),
        ).containsExactly("Europe/Paris", "Asia/Tashkent").inOrder()
        assertThat(TimeZones.options("", "Asia/Tashkent").count { it == "Asia/Tashkent" }).isEqualTo(1)
        assertThat(TimeZones.options("pari", null)).contains("Europe/Paris")
        assertThat(TimeZones.options("zzz", null)).isEmpty()
    }

    @Test
    fun `an unreadable or offset-only device zone counts as not detected`() {
        assertThat(TimeZones.device { TimeZone.of("Asia/Tashkent") }).isEqualTo("Asia/Tashkent")
        assertThat(TimeZones.device { TimeZone.of("UTC") }).isEqualTo("UTC")
        assertThat(TimeZones.device { TimeZone.of("+05:00") }).isNull()
        assertThat(TimeZones.device { error("no zone") }).isNull()
    }
}
