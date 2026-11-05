package shifa.doctor.data.analytics

import shifa.doctor.data.model.AnalyticsOverview
import shifa.doctor.data.model.AnalyticsRange
import shifa.doctor.data.model.ConsultationTypes
import shifa.doctor.data.model.Engagement
import shifa.doctor.data.model.TrendPoint

/**
 * The dashboard export (ANALYTICS-02, flow 02/06 §5): UTF-8, comma separated, English headers, LF line ends, every
 * metric the backend returns (the PREMIUM ones too, as in Flutter). The period is plain ISO dates (Flutter wrote
 * `2026-10-01T00:00:00.000`).
 */
fun dashboardCsv(
    range: AnalyticsRange,
    overview: AnalyticsOverview,
    engagement: Engagement,
    types: ConsultationTypes,
    trend: List<TrendPoint>,
): String =
    buildString {
        appendLine("SHIFA Dashboard Export")
        appendLine("Period,${range.start},${range.end}")
        appendLine()
        appendLine("Metric,Value")
        appendLine("Appointments today,${overview.appointmentsToday}")
        appendLine("Completed today,${overview.completedToday}")
        appendLine("Cancelled today,${overview.cancelledToday}")
        appendLine("New patients today,${overview.newPatientsToday}")
        appendLine("Active patients (30d),${engagement.activePatients}")
        appendLine("Documents received (30d),${engagement.documentsReceived}")
        appendLine("Video consultations,${types.video}")
        appendLine("In-person consultations,${types.inPerson}")
        appendLine()
        appendLine("Date,Appointments")
        trend.forEach { appendLine("${it.date},${it.count}") }
    }

/** `shifa_dashboard_<start>_<end>.csv`. */
fun dashboardCsvName(range: AnalyticsRange) = "shifa_dashboard_${range.start}_${range.end}.csv"
