@file:Suppress("ktlint:standard:max-line-length")

package shifa.doctor.ui.clinic

import com.google.common.truth.Truth.assertThat
import com.russhwolf.settings.MapSettings
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.clinics.ClinicsRepository
import java.util.Collections

@OptIn(ExperimentalCoroutinesApi::class)
class ClinicPatientsViewModelTest {
    // Each request's page and q ("-" without q).
    private val asked = Collections.synchronizedList(mutableListOf<String>())
    private var fail = false
    private val client =
        createHttpClient(
            { null },
            MockEngine { req ->
                val page = req.url.parameters["page"]!!.toInt()
                val q = req.url.parameters["q"]
                asked += "$page ${q ?: "-"}"
                val json = headersOf(HttpHeaders.ContentType, "application/json")
                if (fail) return@MockEngine respond("""{"status":500}""", HttpStatusCode.InternalServerError, json)
                val rows =
                    if (q != null) {
                        """[{"patientId":15,"fullName":"Gulnora","phone":"+998901234567"}]"""
                    } else if (page == 0) {
                        // A row without an id is skipped, not the page.
                        """[{"patientId":11,"fullName":"Aziz"},{"fullName":"No id"},{"patientId":12,"fullName":"Bek"}]"""
                    } else {
                        """[{"patientId":12,"fullName":"Bek"},{"patientId":13,"fullName":"Dilya","email":"d@x.uz"}]"""
                    }
                val last = q != null || page == 1
                respond(
                    """{"content":$rows,"totalElements":${if (q != null) 1 else 3},"last":$last}""",
                    HttpStatusCode.OK,
                    json,
                )
            },
        )
    private val repo = ClinicsRepository({ client }, MapSettings(), { "u1" })
    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun settle(check: () -> Boolean) {
        repeat(300) {
            if (check()) return
            Thread.sleep(10)
        }
    }

    @Test
    fun `the first page loads, Load more adds the next without repeats, and stops at the last`() {
        val classToTest = ClinicPatientsViewModel(2, repo)
        settle { classToTest.state.value.rows != null }
        assertThat(classToTest.state.value.rows!!.map { it.patientId }).containsExactly(11L, 12L).inOrder()
        assertThat(classToTest.state.value.total).isEqualTo(3)
        assertThat(classToTest.state.value.last).isFalse()
        classToTest.loadMore()
        settle { classToTest.state.value.page == 1 }
        assertThat(classToTest.state.value.rows!!.map { it.patientId }).containsExactly(11L, 12L, 13L).inOrder()
        assertThat(classToTest.state.value.last).isTrue()
        classToTest.loadMore()
        Thread.sleep(50)
        assertThat(asked).containsExactly("0 -", "1 -").inOrder()
    }

    @Test
    fun `a search asks the server once typing pauses, trimmed, and only when it changed`() {
        val classToTest = ClinicPatientsViewModel(2, repo)
        settle { classToTest.state.value.rows != null }
        classToTest.type("Gu")
        classToTest.type(" Gul ")
        assertThat(classToTest.state.value.typed).isEqualTo(" Gul ")
        dispatcher.scheduler.advanceTimeBy(ClinicPatientsViewModel.DEBOUNCE.inWholeMilliseconds + 1)
        settle { classToTest.state.value.rows?.singleOrNull()?.patientId == 15L }
        assertThat(classToTest.state.value.total).isEqualTo(1)
        // The same search again (spaces aside) isn't asked twice.
        classToTest.type("Gul")
        dispatcher.scheduler.advanceTimeBy(ClinicPatientsViewModel.DEBOUNCE.inWholeMilliseconds + 1)
        Thread.sleep(50)
        assertThat(asked).containsExactly("0 -", "0 Gul").inOrder()
        // Cleared: everyone again, from the first page.
        classToTest.type("")
        dispatcher.scheduler.advanceTimeBy(ClinicPatientsViewModel.DEBOUNCE.inWholeMilliseconds + 1)
        settle { classToTest.state.value.rows?.size == 2 }
        assertThat(asked.last()).isEqualTo("0 -")
    }

    @Test
    fun `a failed first page offers Retry, a failed next page keeps the rows`() {
        fail = true
        val classToTest = ClinicPatientsViewModel(2, repo)
        settle { classToTest.state.value.error != null }
        assertThat(classToTest.state.value.rows).isNull()
        fail = false
        classToTest.load()
        settle { classToTest.state.value.rows != null }
        assertThat(classToTest.state.value.error).isNull()
        fail = true
        classToTest.loadMore()
        settle { classToTest.state.value.error != null }
        assertThat(classToTest.state.value.rows!!.size).isEqualTo(2)
        assertThat(classToTest.state.value.loadingMore).isFalse()
        assertThat(classToTest.state.value.page).isEqualTo(0)
    }
}
