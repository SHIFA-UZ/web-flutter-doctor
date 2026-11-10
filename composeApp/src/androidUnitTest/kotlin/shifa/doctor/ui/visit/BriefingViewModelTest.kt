@file:Suppress("ktlint:standard:max-line-length")

package shifa.doctor.ui.visit

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
import org.junit.After
import org.junit.Before
import org.junit.Test
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.appointments.AppointmentsRepository
import shifa.doctor.data.documents.DocumentsRepository
import shifa.doctor.data.model.BriefingStatus
import shifa.doctor.data.tracking.RecordingTracker
import shifa.doctor.data.tracking.events.VisitEvents
import shifa.doctor.stopAll

class BriefingViewModelTest {
    private val calls = java.util.concurrent.CopyOnWriteArrayList<String>()
    private var briefing = """{"appointmentId":7,"status":"FAILED"}"""
    private var briefingStatus = HttpStatusCode.OK
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val engine =
        MockEngine { req ->
            val path = req.url.encodedPath.removePrefix("/api/")
            calls += "${req.method.value} $path"
            when {
                path == "appointments/7/briefing/retry" -> {
                    briefing = """{"appointmentId":7,"status":"READY","briefingText":"Text","documentCount":1}"""
                    respond(briefing, HttpStatusCode.OK, json)
                }
                path == "appointments/7/briefing" && briefingStatus != HttpStatusCode.OK ->
                    respond("""{"status":500}""", briefingStatus, json)
                path == "appointments/7/briefing" -> respond(briefing, HttpStatusCode.OK, json)
                path == "patients/14/documents" ->
                    respond(
                        """[{"id":1,"title":"CBC","date":"2026-10-22","appointmentId":7},{"id":2,"title":"Old","date":"2026-01-01","appointmentId":3}]""",
                        HttpStatusCode.OK,
                        json,
                    )
                else -> respond("[]", HttpStatusCode.OK, json)
            }
        }
    private val created = mutableListOf<BriefingViewModel>()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() {
        created.stopAll()
        Dispatchers.resetMain()
    }

    private fun settle(check: () -> Boolean) {
        repeat(300) {
            if (check()) return
            Thread.sleep(10)
        }
    }

    private fun opened(): BriefingViewModel {
        val client = { createHttpClient({ null }, engine) }
        return BriefingViewModel(7, 14, AppointmentsRepository(client), DocumentsRepository(client)).also {
            created += it
            it.start()
            settle { it.state.value.status != null && it.state.value.documents.isNotEmpty() }
        }
    }

    @Test
    fun `a failed briefing is asked again once, then shown with this visit's documents only`() {
        val classToTest = opened()
        assertThat(classToTest.state.value.status).isEqualTo(BriefingStatus.Failed)
        assertThat(classToTest.state.value.documents.map { it.id }).containsExactly(1L)
        classToTest.retry()
        classToTest.retry()
        settle { classToTest.state.value.status == BriefingStatus.Ready && !classToTest.state.value.retrying }
        assertThat(calls.count { it.endsWith("briefing/retry") }).isEqualTo(1)
        assertThat(classToTest.state.value.briefing?.briefingText).isEqualTo("Text")
    }

    @Test
    fun `a failed read shows the failure`() {
        briefingStatus = HttpStatusCode.InternalServerError
        val classToTest = opened()
        assertThat(classToTest.state.value.failed).isTrue()
        assertThat(classToTest.state.value.status).isEqualTo(BriefingStatus.Failed)
    }

    @Test
    fun `the briefing is tracked once failed, once ready, and copied`() {
        val tracker = RecordingTracker()
        val client = { createHttpClient({ null }, engine) }
        val classToTest =
            BriefingViewModel(7, 14, AppointmentsRepository(client), DocumentsRepository(client), tracker = tracker)
                .also { created += it }
        classToTest.start()
        settle { classToTest.state.value.status == BriefingStatus.Failed }
        classToTest.retry()
        settle { classToTest.state.value.status == BriefingStatus.Ready && !classToTest.state.value.retrying }
        classToTest.copied()
        assertThat(tracker.events)
            .containsExactly(
                VisitEvents.VisitBriefingOpened,
                VisitEvents.VisitBriefingFailed("generation_failed"),
                VisitEvents.VisitBriefingReady,
                VisitEvents.VisitBriefingCopied,
            ).inOrder()
    }
}
