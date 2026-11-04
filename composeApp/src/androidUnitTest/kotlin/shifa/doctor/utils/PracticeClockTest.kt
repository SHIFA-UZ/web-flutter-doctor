package shifa.doctor.utils

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.Test

class PracticeClockTest {
    // 2026-11-04 20:30 UTC = 2026-11-05 01:30 in Tashkent (UTC+5).
    private val at = Instant.parse("2026-11-04T20:30:00Z")

    @Test
    fun `today is the practice date, not the phone's`() {
        val clock = PracticeClock("Asia/Tashkent", { at }, { TimeZone.of("Europe/Berlin") })
        assertThat(clock.today()).isEqualTo(LocalDate(2026, 11, 5))
        assertThat(clock.deviceDiffers()).isTrue()
        assertThat(PracticeClock("Asia/Tashkent", { at }, { TimeZone.of("Asia/Samarkand") }).deviceDiffers()).isFalse()
    }

    @Test
    fun `wall clock to UTC keeps 09_00 across a DST switch`() {
        val berlin = PracticeClock("Europe/Berlin", { at })
        assertThat(
            berlin.instant(LocalDate(2026, 3, 28), LocalTime(9, 0)),
        ).isEqualTo(Instant.parse("2026-03-28T08:00:00Z"))
        assertThat(
            berlin.instant(LocalDate(2026, 3, 30), LocalTime(9, 0)),
        ).isEqualTo(Instant.parse("2026-03-30T07:00:00Z"))
        assertThat(berlin.local(Instant.parse("2026-03-30T07:00:00Z")).time).isEqualTo(LocalTime(9, 0))
    }

    @Test
    fun `an empty or unknown zone falls back to UTC and says so`() {
        listOf(null, "", "Mars/Olympus").forEach {
            val clock = PracticeClock(it, { at })
            assertThat(clock.zone).isEqualTo(TimeZone.UTC)
            assertThat(clock.zoneMissing).isTrue()
        }
        assertThat(PracticeClock("UTC", { at }).zoneMissing).isFalse()
        assertThat(PracticeClock(" Asia/Tashkent ", { at }).zone).isEqualTo(TimeZone.of("Asia/Tashkent"))
    }

    @Test
    fun `backend timestamps without a zone are UTC, not device time`() {
        assertThat(PracticeClock.parseInstant("2026-11-04T08:00:00")).isEqualTo(Instant.parse("2026-11-04T08:00:00Z"))
        assertThat(
            PracticeClock.parseInstant("2026-11-04T13:00:00+05:00"),
        ).isEqualTo(Instant.parse("2026-11-04T08:00:00Z"))
        assertThat(PracticeClock.parseInstant("not a time")).isNull()
        assertThat(PracticeClock.parseInstant(null)).isNull()
    }
}
