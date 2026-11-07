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

    @Test
    fun `a wall-clock time inside the spring-forward gap lands after it, never before`() {
        // Berlin skips 02:00–03:00 on 2026-03-29; 02:30 doesn't exist, it resolves to 03:30 CEST (01:30 UTC).
        val berlin = PracticeClock("Europe/Berlin", { at })
        assertThat(
            berlin.instant(LocalDate(2026, 3, 29), LocalTime(2, 30)),
        ).isEqualTo(Instant.parse("2026-03-29T01:30:00Z"))
    }

    @Test
    fun `the repeated hour in autumn reads both instants on the same wall clock`() {
        // Berlin goes 03:00 CEST → 02:00 CET on 2026-10-25: 00:30 and 01:30 UTC are both 02:30 there.
        val berlin = PracticeClock("Europe/Berlin", { at })
        assertThat(berlin.local(Instant.parse("2026-10-25T00:30:00Z")).time).isEqualTo(LocalTime(2, 30))
        assertThat(berlin.local(Instant.parse("2026-10-25T01:30:00Z")).time).isEqualTo(LocalTime(2, 30))
        // Start of that day is 00:00 CEST (22:00 UTC the day before); the day is 25 hours long.
        val start = berlin.startOfDay(LocalDate(2026, 10, 25))
        val next = berlin.startOfDay(LocalDate(2026, 10, 26))
        assertThat(start).isEqualTo(Instant.parse("2026-10-24T22:00:00Z"))
        assertThat((next - start).inWholeHours).isEqualTo(25)
    }

    @Test
    fun `the device mismatch follows the offsets now, so it can change with DST`() {
        // London and Lisbon share an offset all year; Tashkent and Berlin differ in summer and winter alike.
        val summer = Instant.parse("2026-07-01T12:00:00Z")
        assertThat(
            PracticeClock("Europe/London", { summer }, { TimeZone.of("Europe/Lisbon") }).deviceDiffers(),
        ).isFalse()
        assertThat(
            PracticeClock("Asia/Tashkent", { summer }, { TimeZone.of("Europe/Berlin") }).deviceDiffers(),
        ).isTrue()
        // Moscow (no DST) and Berlin: 1 h apart in summer, 2 h in winter, never equal.
        val winter = Instant.parse("2026-01-15T12:00:00Z")
        assertThat(
            PracticeClock("Europe/Moscow", { winter }, { TimeZone.of("Europe/Istanbul") }).deviceDiffers(),
        ).isFalse()
    }

    @Test
    fun `dates and times are numeric and two-digit`() {
        assertThat(displayDate(LocalDate(2026, 3, 5))).isEqualTo("05.03.2026")
        assertThat(displayTime(LocalTime(9, 5))).isEqualTo("09:05")
        assertThat(displayMinutes(25 * 60 + 30)).isEqualTo("01:30")
        assertThat(displayMinutes(-30)).isEqualTo("23:30")
    }
}
