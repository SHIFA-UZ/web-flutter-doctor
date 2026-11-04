package shifa.doctor.utils

import kotlinx.datetime.TimeZone

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
