package shifa.doctor.utils

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * The doctor's clock (CORE-09, TZ-1; flow 04 README §4.2): every time and date on screen is in the practice time zone
 * (or the clinic's, on clinic screens), never the phone's. [zoneId] is the profile's IANA id; empty or unknown falls
 * back to UTC and sets [zoneMissing] so the screen can ask the doctor to set it. The phone's zone is read only for
 * [deviceDiffers] (the mismatch banner, flow 04/03).
 */
class PracticeClock(
    zoneId: String?,
    private val clock: () -> Instant = ::currentInstant,
    private val deviceZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {
    private val parsed: TimeZone? =
        zoneId?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { TimeZone.of(it) }.getOrNull() }

    val zone: TimeZone = parsed ?: TimeZone.UTC

    /** True when the profile had no usable time zone and [zone] is the UTC fallback. */
    val zoneMissing: Boolean = parsed == null

    fun now(): LocalDateTime = clock().toLocalDateTime(zone)

    /** Now as an instant, for comparing with backend times. */
    fun nowInstant(): Instant = clock()

    /** "Today" is the date in the practice zone, not the phone's. */
    fun today(): LocalDate = now().date

    /** A backend instant (UTC, ISO-8601 with `Z` or an offset) on the practice wall clock. */
    fun local(instant: Instant): LocalDateTime = instant.toLocalDateTime(zone)

    /** A practice wall-clock time to the UTC instant the backend expects (DST-correct per day). */
    fun instant(
        date: LocalDate,
        time: LocalTime,
    ): Instant = LocalDateTime(date, time).toInstant(zone)

    /** 00:00 of [date] in the practice zone, for month and day ranges. */
    fun startOfDay(date: LocalDate): Instant = date.atStartOfDayIn(zone)

    /** Flow 04/03: the phone's zone gives a different offset right now. */
    fun deviceDiffers(): Boolean {
        val at = clock()
        return zone.offsetAt(at) != deviceZone().offsetAt(at)
    }

    companion object {
        /**
         * Parses a backend instant. A timestamp without a zone is read as UTC, on purpose (Flutter read it as device
         * time and shifted it, 03 §4.3 item 6). Null when it isn't a timestamp.
         */
        fun parseInstant(iso: String?): Instant? {
            val text = iso?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return runCatching { Instant.parse(text) }.getOrNull()
                ?: runCatching { LocalDateTime.parse(text).toInstant(TimeZone.UTC) }.getOrNull()
        }
    }
}

/**
 * Now, as the `kotlinx.datetime.Instant` the date code uses. Read through the stdlib clock: kotlinx-datetime 0.5's
 * `Clock` doesn't resolve on iOS with Kotlin 2.3.
 */
@OptIn(ExperimentalTime::class)
fun currentInstant(): Instant = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds())
