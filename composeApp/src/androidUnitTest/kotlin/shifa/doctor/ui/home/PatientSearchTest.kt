package shifa.doctor.ui.home

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
import org.junit.Test
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.model.Patient
import shifa.doctor.data.patients.PatientsRepository
import shifa.doctor.data.tracking.RecordingTracker
import shifa.doctor.data.tracking.events.HomeEvents
import shifa.doctor.stopAll

class PatientSearchTest {
    private val people =
        (1L..10L).map {
            Patient(
                it,
                "Patient ${'K' + it.toInt()}",
                phones = listOf("+998 90 000 00 ${it.toString().padStart(2, '0')}"),
            )
        }

    private fun state(q: String) = PatientSearchUiState(people.reversed(), query = q, today = LocalDate(2026, 11, 5))

    @Test
    fun `blank shows the first 8 by name, a query every match by name, display id or phone digits`() {
        assertThat(state(" ").hits.map { it.id }).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L).inOrder()
        assertThat(state("patient l").hits.map { it.id }).containsExactly(1L)
        assertThat(state("P-00003").hits.map { it.id }).containsExactly(3L)
        assertThat(state("00 07").hits.map { it.id }).containsExactly(7L)
        // Letters with digits never match phones.
        assertThat(state("x 007").hits).isEmpty()
    }

    @Test
    fun `an opened result sends how many matched and whether anything was typed, never the text`() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val rows = (1..3).joinToString(",", "[", "]") { """{"id":$it,"name":"Patient $it"}""" }
        val engine =
            MockEngine { respond(rows, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }
        val tracker = RecordingTracker()
        val classToTest =
            PatientSearchViewModel(
                PatientsRepository(client = { createHttpClient({ null }, engine) }),
                LocalDate(2026, 11, 5),
                tracker,
            )
        try {
            repeat(300) { if (classToTest.state.value.patients == null) Thread.sleep(10) }
            classToTest.opened()
            classToTest.type("patient 2")
            classToTest.opened()
            assertThat(tracker.events)
                .containsExactly(
                    HomeEvents.HomeSearchResultOpened("2-5", searched = false),
                    HomeEvents.HomeSearchResultOpened("1", searched = true),
                ).inOrder()
        } finally {
            listOf(classToTest).stopAll()
            Dispatchers.resetMain()
        }
    }
}
