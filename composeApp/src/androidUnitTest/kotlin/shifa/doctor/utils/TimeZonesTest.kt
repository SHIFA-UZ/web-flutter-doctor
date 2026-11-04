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

    @Test
    fun `offsets read like the board's chip`() {
        val now = kotlinx.datetime.Instant.parse("2026-11-04T10:00:00Z")
        assertThat(TimeZones.utcLabel("Asia/Tashkent", now)).isEqualTo("UTC+5")
        assertThat(TimeZones.utcLabel("Asia/Kolkata", now)).isEqualTo("UTC+5:30")
        assertThat(TimeZones.utcLabel("America/New_York", now)).isEqualTo("UTC−5")
        assertThat(TimeZones.utcLabel("UTC", now)).isEqualTo("UTC")
        assertThat(TimeZones.utcLabel("Nowhere/City", now)).isNull()
    }

    @Test
    fun `search finds a city with spaces and an offset typed with or without UTC`() {
        val all = {
            setOf(
                "Asia/Tashkent",
                "Asia/Karachi",
                "America/Argentina/Buenos_Aires",
                "Europe/Berlin",
                "Asia/Kolkata",
            )
        }
        assertThat(TimeZones.options("buenos aires", null, all)).containsExactly("America/Argentina/Buenos_Aires")
        assertThat(TimeZones.options("+5", null, all)).containsExactly("Asia/Karachi", "Asia/Tashkent")
        assertThat(TimeZones.options("utc+5", null, all)).containsExactly("Asia/Karachi", "Asia/Tashkent")
        assertThat(TimeZones.options("+5:30", null, all)).containsExactly("Asia/Kolkata")
        assertThat(TimeZones.options("+05:00", null, all)).containsExactly("Asia/Karachi", "Asia/Tashkent")
        assertThat(TimeZones.options("UTC+0", null, all = { setOf("UTC", "Asia/Tashkent") })).containsExactly("UTC")
        assertThat(TimeZones.options("5", null, all)).isEmpty() // a bare number is not an offset
        // Etc/GMT+5 is UTC−5: an offset query never matches it by its id.
        assertThat(
            TimeZones.options("+5", null, all = { setOf("Etc/GMT+5", "Asia/Tashkent") }),
        ).containsExactly("Asia/Tashkent")
        assertThat(
            TimeZones.options("-5", null, all = { setOf("Etc/GMT+5", "Asia/Tashkent") }),
        ).containsExactly("Etc/GMT+5")
        assertThat(TimeZones.city("America/Argentina/Buenos_Aires")).isEqualTo("Buenos Aires")
    }

    @Test
    fun `the current zone is offered right after the detected one`() {
        assertThat(TimeZones.options("", "Asia/Tashkent", current = "Pacific/Auckland").take(2))
            .containsExactly("Asia/Tashkent", "Pacific/Auckland")
            .inOrder()
    }
}
