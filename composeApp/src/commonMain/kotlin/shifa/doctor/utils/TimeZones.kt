package shifa.doctor.utils

import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt

/** The practice-timezone picker's data (flows 01/07 §3b, 01/08, 01/10; DaAuth picker). */
object TimeZones {
    /** The board's short list, shown before the doctor types; typing searches every zone the platform knows. */
    val common =
        listOf(
            "Asia/Tashkent", "Asia/Samarkand", "Asia/Almaty", "Asia/Bishkek", "Asia/Dushanbe", "Asia/Ashgabat",
            "Asia/Baku", "Asia/Tbilisi", "Asia/Yerevan", "Asia/Dubai", "Asia/Karachi", "Asia/Kolkata",
            "Asia/Istanbul", "Europe/Moscow", "Europe/Kyiv", "Europe/Berlin", "Europe/London", "America/New_York",
            "UTC",
        )

    /** The device's IANA zone, or null when it can't be read (never a silent "UTC": flow 01/07 §8). */
    fun device(zone: () -> TimeZone = { TimeZone.currentSystemDefault() }): String? =
        runCatching { zone().id }.getOrNull()?.takeIf { it.contains('/') || it == "UTC" }

    /** "UTC+5", "UTC−3:30" for [zoneId] now (08/01 §4); null when the id isn't a zone. */
    fun utcLabel(
        zoneId: String,
        now: kotlinx.datetime.Instant = currentInstant(),
    ): String? {
        val seconds = runCatching { TimeZone.of(zoneId).offsetAt(now).totalSeconds }.getOrNull() ?: return null
        val sign = if (seconds < 0) "−" else "+"
        val h = kotlin.math.abs(seconds) / 3600
        val m = kotlin.math.abs(seconds) % 3600 / 60
        return if (seconds == 0) "UTC" else "UTC$sign$h" + if (m > 0) ":" + m.toString().padStart(2, '0') else ""
    }

    /** The detected zone first, then the short list, or every matching zone when [query] isn't blank. */
    fun options(
        query: String,
        detected: String?,
        all: () -> Set<String> = { TimeZone.availableZoneIds },
    ): List<String> {
        val q = query.trim()
        val base = listOfNotNull(detected) + (if (q.isEmpty()) common else all().sorted())
        return base.distinct().filter { q.isEmpty() || it.contains(q, ignoreCase = true) }
    }
}
