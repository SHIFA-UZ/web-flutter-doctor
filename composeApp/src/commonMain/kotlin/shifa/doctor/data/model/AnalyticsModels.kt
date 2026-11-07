package shifa.doctor.data.model

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.serialization.Serializable

/** `GET /doctor/analytics/overview` (02 §2.3): today's counts, the server's day. Missing values read as 0. */
@Serializable
data class AnalyticsOverview(
    val appointmentsToday: Int = 0,
    val completedToday: Int = 0,
    val cancelledToday: Int = 0,
    val newPatientsToday: Int = 0,
)

/** One day of `GET /doctor/analytics/appointments-trend`: `yyyy-MM-dd` (the server's date) and its bookings. */
@Serializable
data class TrendPoint(
    val date: String = "",
    val count: Int = 0,
) {
    val day: LocalDate? get() = runCatching { LocalDate.parse(date) }.getOrNull()
}

/** `GET /doctor/analytics/consultation-types`: video and in-person visits (the backend's period). */
@Serializable
data class ConsultationTypes(
    val video: Int = 0,
    val inPerson: Int = 0,
) {
    val total: Int get() = video + inPerson
}

/** `GET /doctor/analytics/engagement`: active patients and documents received, 30-day windows. */
@Serializable
data class Engagement(
    val activePatients: Int = 0,
    val documentsReceived: Int = 0,
)

/** `GET /doctor/analytics/sms-usage?from&to` (Reports): SMS sent and their cost in minor units. */
@Serializable
data class SmsUsage(
    val sentCount: Int = 0,
    val totalCostMinor: Long = 0,
    val currency: String = "UZS",
    val pricePerSmsMinor: Long = 500,
    val smsRemindersAllowed: Boolean = false,
)

/**
 * The analytics period (flow 02/06 §4): inclusive days, start never after end. Shared by Home and Reports; the default
 * is the last 7 days ending on the practice date (Flutter used the phone's date).
 */
data class AnalyticsRange(
    val start: LocalDate,
    val end: LocalDate,
) {
    init {
        require(start <= end) { "range starts after it ends" }
    }

    val days: Int get() = start.daysUntil(end) + 1

    /**
     * The trend's `days` (1–90): the backend answers the last N days ending today, so a past range shows the days
     * before today, not the range (BE-19 asks for `from/to`).
     */
    val trendDays: Int get() = days.coerceIn(1, MAX_TREND_DAYS)

    companion object {
        const val MAX_TREND_DAYS = 90

        fun lastDays(
            today: LocalDate,
            count: Int = 7,
        ) = AnalyticsRange(today.minus(DatePeriod(days = count - 1)), today)
    }
}
