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

    /** "America/Argentina/Buenos_Aires" → "Buenos Aires" (flow 08/05 §3). */
    fun city(zoneId: String) = zoneId.substringAfterLast('/').replace('_', ' ')

    /**
     * The detected and the [current] zone first, then the short list, or every matching zone when [query] isn't
     * blank. A query matches the id, the city with spaces ("buenos aires") or the offset now ("+5", "utc+5").
     */
    fun options(
        query: String,
        detected: String?,
        all: () -> Set<String> = { TimeZone.availableZoneIds },
        current: String? = null,
    ): List<String> {
        val q = query.trim()
        val base = listOfNotNull(detected, current) + (if (q.isEmpty()) common else all().sorted())
        return base.distinct().filter { q.isEmpty() || matches(it, q) }
    }

    private fun matches(
        zoneId: String,
        query: String,
    ): Boolean {
        // An offset ("+5", "UTC-3:30") matches the offset now only: "Etc/GMT+5" is UTC−5 and must not come up by its id.
        val typed = query.replace('−', '-').replace(Regex("^utc", RegexOption.IGNORE_CASE), "")
        OFFSET.matchEntire(typed)?.let { m ->
            val (sign, hours, minutes) = m.destructured
            val seconds = (hours.toInt() * 3600 + (minutes.toIntOrNull() ?: 0) * 60) * if (sign == "-") -1 else 1
            return runCatching { TimeZone.of(zoneId).offsetAt(currentInstant()).totalSeconds }.getOrNull() == seconds
        }
        return zoneId.contains(query, ignoreCase = true) || zoneId.replace('_', ' ').contains(query, ignoreCase = true)
    }

    // "+5", "-3:30", "+05:00", "+0": compared as seconds, so every spelling of an offset finds the same zones.
    private val OFFSET = Regex("""([+-])(\d{1,2})(?::(\d{2}))?""")
}
