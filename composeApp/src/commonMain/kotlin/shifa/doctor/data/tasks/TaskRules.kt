package shifa.doctor.data.tasks

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import shifa.doctor.data.model.CheckInStatus
import shifa.doctor.data.model.TaskCheckIn

/**
 * A check-in's status as the doctor should see it (07 §3.2): one still pending after its scheduled time has passed is
 * missed. The time is the patient's ([patientZone], else [fallbackZone]); Flutter used the phone's clock and zone.
 * Without a time the whole day counts (missed once the day is over). With neither zone, the server's status as sent
 * (never the phone's zone).
 */
fun TaskCheckIn.effectiveStatus(
    now: Instant,
    patientZone: String?,
    fallbackZone: TimeZone?,
): CheckInStatus {
    if (status != CheckInStatus.Pending) return status
    val zone = patientZone?.let { runCatching { TimeZone.of(it) }.getOrNull() } ?: fallbackZone ?: return status
    val due =
        time?.let { LocalDateTime(date, it).toInstant(zone) }
            ?: LocalDateTime(date.year, date.month, date.dayOfMonth, 23, 59, 59).toInstant(zone)
    return if (now > due) CheckInStatus.Missed else CheckInStatus.Pending
}
