@file:Suppress("ktlint:standard:max-line-length")

package shifa.doctor.data.analytics

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Test
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.model.AnalyticsRange

class AnalyticsRepositoryTest {
    private val asked = java.util.concurrent.CopyOnWriteArrayList<String>()
    private val engine =
        MockEngine { req ->
            asked += req.url.encodedPath.substringAfter("/api/") + "?" + req.url.encodedQuery
            val body =
                when {
                    req.url.encodedPath.endsWith(
                        "overview",
                    ) -> """{"appointmentsToday":6,"completedToday":null,"cancelledToday":1}"""
                    req.url.encodedPath.endsWith("appointments-trend") ->
                        """[{"date":"2026-11-04","count":3},{"date":"bad","count":1},{"date":"2026-11-05","count":"x"},{"date":"2026-11-05"}]"""
                    req.url.encodedPath.endsWith("sms-usage") -> """{"sentCount":2,"totalCostMinor":1000}"""
                    else -> """{"video":4,"inPerson":11}"""
                }
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
    private val classToTest = AnalyticsRepository { createHttpClient({ null }, engine) }

    @Test
    fun `missing and null values read as 0, a bad trend day is skipped, not the trend`() =
        runTest {
            val o = classToTest.overview().getOrThrow()
            assertThat(
                listOf(o.appointmentsToday, o.completedToday, o.cancelledToday, o.newPatientsToday),
            ).containsExactly(6, 0, 1, 0).inOrder()
            val t = classToTest.trend(AnalyticsRange.lastDays(LocalDate(2026, 11, 5))).getOrThrow()
            assertThat(t.map { it.date to it.count }).containsExactly("2026-11-04" to 3, "2026-11-05" to 0).inOrder()
            assertThat(classToTest.consultationTypes().getOrThrow().total).isEqualTo(15)
            assertThat(
                classToTest.smsUsage(AnalyticsRange.lastDays(LocalDate(2026, 11, 5))).getOrThrow().currency,
            ).isEqualTo("UZS")
        }

    @Test
    fun `the range is inclusive, the trend asks 1 to 90 days, SMS gets the dates as they are`() =
        runTest {
            val week = AnalyticsRange.lastDays(LocalDate(2026, 11, 5))
            assertThat(week.start).isEqualTo(LocalDate(2026, 10, 30))
            assertThat(week.days).isEqualTo(7)
            val year = AnalyticsRange(LocalDate(2025, 11, 5), LocalDate(2026, 11, 5))
            assertThat(year.trendDays).isEqualTo(90)
            classToTest.trend(year)
            classToTest.smsUsage(week)
            assertThat(asked).containsExactly(
                "doctor/analytics/appointments-trend?days=90",
                "doctor/analytics/sms-usage?from=2026-10-30&to=2026-11-05",
            ).inOrder()
        }

    @Test
    fun `one refresh reads every block, each keeping its own result`() =
        runTest {
            val week = AnalyticsRange.lastDays(LocalDate(2026, 11, 5))
            val home = classToTest.all(week)
            assertThat(home.overview.getOrThrow().appointmentsToday).isEqualTo(6)
            assertThat(home.trend.getOrThrow()).hasSize(2)
            assertThat(home.types.isSuccess).isTrue()
            assertThat(home.engagement.isSuccess).isTrue()
            assertThat(home.sms).isNull()
            assertThat(classToTest.all(week, withSms = true).sms?.getOrThrow()?.sentCount).isEqualTo(2)
        }
}
