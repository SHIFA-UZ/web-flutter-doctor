@file:Suppress("ktlint:standard:max-line-length")

package shifa.doctor.ui.analytics

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Test
import shifa.doctor.data.analytics.AnalyticsRepository
import shifa.doctor.data.analytics.dashboardCsv
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.model.AnalyticsOverview
import shifa.doctor.data.model.AnalyticsRange
import shifa.doctor.data.model.ConsultationTypes
import shifa.doctor.data.model.Engagement
import shifa.doctor.data.model.TrendPoint
import shifa.doctor.ui.navigation.ShellNotice

class AnalyticsViewModelTest {
    private val asked = mutableListOf<String>()
    private var failEngagement = false
    private val engine =
        MockEngine { req ->
            val path = req.url.encodedPath.substringAfterLast('/')
            asked += path + (req.url.parameters["days"]?.let { "?days=$it" } ?: "")
            val body =
                when (path) {
                    "overview" -> """{"appointmentsToday":6,"completedToday":4,"cancelledToday":1,"newPatientsToday":2}"""
                    "appointments-trend" -> """[{"date":"2026-11-04","count":3},{"date":"2026-11-05","count":5}]"""
                    "consultation-types" -> """{"video":8,"inPerson":17}"""
                    else -> """{"activePatients":41,"documentsReceived":12}"""
                }
            if (path == "engagement" && failEngagement) {
                respond(
                    """{"status":500}""",
                    HttpStatusCode.InternalServerError,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            } else {
                respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
    private val today = LocalDate(2026, 11, 5)

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() {
        Dispatchers.resetMain()
        ShellNotice.pending.value = null
    }

    private fun settle(check: () -> Boolean) {
        repeat(300) {
            if (check()) return
            Thread.sleep(10)
        }
    }

    private fun opened() = AnalyticsViewModel(today, AnalyticsRepository { createHttpClient({ null }, engine) })

    @Test
    fun `opening reads every block, a new range reads only the trend`() {
        val classToTest = opened()
        settle { classToTest.state.value.loaded != null }
        assertThat(classToTest.state.value.range).isEqualTo(AnalyticsRange(LocalDate(2026, 10, 30), today))
        assertThat(asked).containsExactly("overview", "appointments-trend?days=7", "consultation-types", "engagement")
        asked.clear()
        classToTest.refreshToday()
        settle { asked.isNotEmpty() }
        assertThat(asked).containsExactly("overview")
        asked.clear()
        classToTest.setRange(AnalyticsRange.lastDays(today, 30))
        settle { !classToTest.state.value.trendLoading }
        assertThat(asked).containsExactly("appointments-trend?days=30")
    }

    @Test
    fun `export builds the CSV from what is loaded, a failed block fails it with a notice`() {
        val classToTest = opened()
        settle { classToTest.state.value.loaded != null }
        asked.clear()
        classToTest.export()
        settle { classToTest.state.value.export != null }
        assertThat(asked).isEmpty()
        val export = classToTest.state.value.export!!
        assertThat(export.fileName).isEqualTo("shifa_dashboard_2026-10-30_2026-11-05.csv")
        assertThat(export.csv).contains("Appointments today,6\n")
        classToTest.exported(shown = true)
        assertThat(classToTest.state.value.export).isNull()
        assertThat(ShellNotice.pending.value).isNull()

        failEngagement = true
        classToTest.refresh()
        settle { classToTest.state.value.engagement?.isFailure == true }
        classToTest.export()
        settle { !classToTest.state.value.exporting }
        assertThat(classToTest.state.value.export).isNull()
        assertThat(ShellNotice.pending.value).isNotNull()
    }

    @Test
    fun `the CSV has the documented layout`() {
        val csv =
            dashboardCsv(
                AnalyticsRange(LocalDate(2026, 10, 1), LocalDate(2026, 10, 2)),
                AnalyticsOverview(6, 4, 1, 2),
                Engagement(41, 12),
                ConsultationTypes(8, 17),
                listOf(TrendPoint("2026-10-01", 3), TrendPoint("2026-10-02", 0)),
            )
        assertThat(csv).isEqualTo(
            "SHIFA Dashboard Export\nPeriod,2026-10-01,2026-10-02\n\nMetric,Value\nAppointments today,6\nCompleted today,4\nCancelled today,1\nNew patients today,2\nActive patients (30d),41\nDocuments received (30d),12\nVideo consultations,8\nIn-person consultations,17\n\nDate,Appointments\n2026-10-01,3\n2026-10-02,0\n",
        )
    }

    @Test
    fun `a slow block never holds the others back`() {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val slow =
            MockEngine { req ->
                val path = req.url.encodedPath.substringAfterLast('/')
                if (path == "consultation-types") gate.await()
                respond(
                    if (path == "overview") """{"appointmentsToday":6}""" else "{}",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        val classToTest = AnalyticsViewModel(today, AnalyticsRepository { createHttpClient({ null }, slow) })
        settle { classToTest.state.value.overview != null }
        assertThat(classToTest.state.value.overview?.getOrNull()?.appointmentsToday).isEqualTo(6)
        assertThat(classToTest.state.value.types).isNull()
        gate.complete(Unit)
        settle { classToTest.state.value.types != null }
        assertThat(classToTest.state.value.types?.isSuccess).isTrue()
    }

    @Test
    fun `an export read for an old range never replaces the new range's trend`() {
        failEngagement = true
        val classToTest = opened()
        settle { classToTest.state.value.engagement?.isFailure == true }
        classToTest.export()
        classToTest.setRange(AnalyticsRange.lastDays(today, 30))
        settle { !classToTest.state.value.exporting && !classToTest.state.value.trendLoading }
        assertThat(classToTest.state.value.range.days).isEqualTo(30)
        assertThat(asked.last { it.startsWith("appointments-trend") }).isEqualTo("appointments-trend?days=30")
        assertThat(classToTest.state.value.engagement?.isFailure).isTrue()
    }

    @Test
    fun `a new practice day moves the presets' end and a range left as it was`() {
        val classToTest = opened()
        settle { classToTest.state.value.loaded != null }
        asked.clear()
        classToTest.refreshToday(LocalDate(2026, 11, 6))
        settle { !classToTest.state.value.trendLoading && asked.size == 2 }
        assertThat(classToTest.state.value.today).isEqualTo(LocalDate(2026, 11, 6))
        assertThat(
            classToTest.state.value.range,
        ).isEqualTo(AnalyticsRange(LocalDate(2026, 10, 31), LocalDate(2026, 11, 6)))
        // A range the doctor picked stays.
        classToTest.setRange(AnalyticsRange.lastDays(LocalDate(2026, 11, 6), 30))
        settle { !classToTest.state.value.trendLoading }
        classToTest.refreshToday(LocalDate(2026, 11, 7))
        assertThat(classToTest.state.value.range.days).isEqualTo(30)
        assertThat(classToTest.state.value.range.end).isEqualTo(LocalDate(2026, 11, 6))
    }
}
