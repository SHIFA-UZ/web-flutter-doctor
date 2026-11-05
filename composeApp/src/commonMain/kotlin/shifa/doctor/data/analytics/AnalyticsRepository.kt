package shifa.doctor.data.analytics

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import shifa.doctor.data.api.ApiConfig
import shifa.doctor.data.api.apiCall
import shifa.doctor.data.api.httpClient
import shifa.doctor.data.api.rows
import shifa.doctor.data.model.AnalyticsOverview
import shifa.doctor.data.model.AnalyticsRange
import shifa.doctor.data.model.ConsultationTypes
import shifa.doctor.data.model.Engagement
import shifa.doctor.data.model.SmsUsage
import shifa.doctor.data.model.TrendPoint

/**
 * The doctor's analytics (ANALYTICS-01, 02 §2.1, 15 §2.3): five independent reads, so one failing never hides the
 * others (flow 02/05 §6). Nothing is cached: Home and Reports read on open and on refresh (Flutter never refreshed the
 * trend, types or engagement).
 */
class AnalyticsRepository(
    private val client: () -> HttpClient = { httpClient },
) {
    suspend fun overview(): Result<AnalyticsOverview> = apiCall { client().get(ApiConfig.Endpoints.ANALYTICS_OVERVIEW) }

    /** `days` = the range's length, 1–90; a day that can't be read is skipped, not the whole trend. */
    suspend fun trend(range: AnalyticsRange): Result<List<TrendPoint>> =
        apiCall<List<JsonElement>> {
            client().get(ApiConfig.Endpoints.ANALYTICS_TREND) { parameter("days", range.trendDays) }
        }.map { it.rows(TrendPoint.serializer(), "analytics trend") { p -> p.takeIf { p.day != null } } }

    suspend fun consultationTypes(): Result<ConsultationTypes> =
        apiCall { client().get(ApiConfig.Endpoints.ANALYTICS_TYPES) }

    suspend fun engagement(): Result<Engagement> = apiCall { client().get(ApiConfig.Endpoints.ANALYTICS_ENGAGEMENT) }

    /** `from`/`to` as `yyyy-MM-dd`, honoured exactly (Reports' SMS block). */
    suspend fun smsUsage(range: AnalyticsRange): Result<SmsUsage> =
        apiCall {
            client().get(ApiConfig.Endpoints.ANALYTICS_SMS) {
                parameter("from", range.start.toString())
                parameter("to", range.end.toString())
            }
        }
}

/**
 * Every analytics block at once (ANALYTICS-01's single refresh entry point): each keeps its own result, so a failure
 * shows only in its block (flow 02/05 §6). [sms] is read only for Reports.
 */
data class AnalyticsSnapshot(
    val overview: Result<AnalyticsOverview>,
    val trend: Result<List<TrendPoint>>,
    val types: Result<ConsultationTypes>,
    val engagement: Result<Engagement>,
    val sms: Result<SmsUsage>? = null,
)

/** Reads all of [AnalyticsSnapshot] in parallel: Home's open, pull-to-refresh and export, and Reports. */
suspend fun AnalyticsRepository.all(
    range: AnalyticsRange,
    withSms: Boolean = false,
): AnalyticsSnapshot =
    coroutineScope {
        val overview = async { overview() }
        val trend = async { trend(range) }
        val types = async { consultationTypes() }
        val engagement = async { engagement() }
        val sms = if (withSms) async { smsUsage(range) } else null
        AnalyticsSnapshot(overview.await(), trend.await(), types.await(), engagement.await(), sms?.await())
    }

val analyticsRepository = AnalyticsRepository()
