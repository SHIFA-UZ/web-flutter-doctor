@file:Suppress("ktlint:standard:max-line-length")

package shifa.doctor.ui.patients

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
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
import shifa.doctor.data.patients.PatientsRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.prophylaxisSaved
import java.util.Collections

@OptIn(ExperimentalCoroutinesApi::class)
class ProphylaxisViewModelTest {
    private var getBody = ""
    private var getStatus = HttpStatusCode.OK
    private var putStatus = HttpStatusCode.OK
    private val sent = Collections.synchronizedList(mutableListOf<String>())
    private val queries = Collections.synchronizedList(mutableListOf<String>())
    private val client =
        createHttpClient(
            { null },
            MockEngine { req ->
                val json = headersOf(HttpHeaders.ContentType, "application/json")
                if (req.method.value == "GET") {
                    queries += req.url.encodedQuery
                    // "No setting" is an empty 200 without a content type, as dev answers.
                    respond(getBody, getStatus, if (getBody.isEmpty()) headersOf() else json)
                } else {
                    sent += req.body.toByteArray().decodeToString()
                    // The backend's answer (its patientId may be null) without lastSentAt.
                    respond("""{"patientId":null,"clinicId":2,"intervalMonths":6,"enabled":false}""", putStatus, json)
                }
            },
        )
    private val repo = PatientsRepository({ client })

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private fun settle(check: () -> Boolean) {
        repeat(300) {
            if (check()) return
            Thread.sleep(10)
        }
    }

    @Test
    fun `no setting reads as 12 months on, Save sends both ids without lastSentAt and keeps Last sent`() {
        val classToTest = ProphylaxisViewModel(15, 2, repo)
        settle { classToTest.state.value.loaded }
        assertThat(queries.single()).isEqualTo("patientId=15&clinicId=2")
        assertThat(classToTest.state.value.interval).isEqualTo(12)
        assertThat(classToTest.state.value.enabled).isTrue()
        classToTest.interval(0)
        classToTest.interval(61)
        assertThat(classToTest.state.value.interval).isEqualTo(12)
        classToTest.interval(6)
        classToTest.enabled(false)
        classToTest.save()
        settle { classToTest.state.value.banner != null }
        assertThat(sent.single()).isEqualTo("""{"patientId":15,"clinicId":2,"intervalMonths":6,"enabled":false}""")
        assertThat(classToTest.state.value.banner!!.text).isEqualTo(Res.string.prophylaxisSaved)
        assertThat(classToTest.state.value.interval).isEqualTo(6)
        assertThat(classToTest.state.value.enabled).isFalse()
    }

    @Test
    fun `a stored setting shows with Last sent, a failed read blocks Save, a failed save says so`() {
        getBody = """{"patientId":15,"clinicId":2,"intervalMonths":3,"enabled":true,"lastSentAt":"2026-10-01T05:00:00Z"}"""
        val classToTest = ProphylaxisViewModel(15, 2, repo)
        settle { classToTest.state.value.loaded }
        assertThat(classToTest.state.value.interval).isEqualTo(3)
        assertThat(classToTest.state.value.lastSentAt).isEqualTo("2026-10-01T05:00:00Z")
        putStatus = HttpStatusCode.BadRequest
        classToTest.save()
        settle { classToTest.state.value.banner != null }
        assertThat(classToTest.state.value.banner!!.error).isTrue()
        assertThat(classToTest.state.value.saving).isFalse()
        assertThat(classToTest.state.value.lastSentAt).isEqualTo("2026-10-01T05:00:00Z")

        getStatus = HttpStatusCode.InternalServerError
        val failed = ProphylaxisViewModel(16, 2, repo)
        settle { failed.state.value.loadError != null }
        assertThat(failed.state.value.loaded).isFalse()
        val before = sent.size
        failed.save()
        Thread.sleep(50)
        assertThat(sent.size).isEqualTo(before)
    }
}
