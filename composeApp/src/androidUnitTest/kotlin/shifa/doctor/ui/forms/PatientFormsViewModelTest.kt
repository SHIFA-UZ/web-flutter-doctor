@file:Suppress("ktlint:standard:max-line-length")

package shifa.doctor.ui.forms

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.forms.FormsRepository
import shifa.doctor.data.model.PatientForm
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.formsLoadFailed
import shifa.doctor.stopAll

class PatientFormsViewModelTest {
    private var fail = false
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val engine =
        MockEngine { req ->
            when {
                fail ->
                    respond(
                        """{"status":500,"code":"X","requestId":"req-f"}""",
                        HttpStatusCode.InternalServerError,
                        json,
                    )
                req.url.encodedPath == "/api/patients/14/forms" && req.method.value == "POST" ->
                    respond(
                        """{"id":40,"patientId":14,"templateId":"025-2","date":"2026-11-04","documentId":120}""",
                        HttpStatusCode.OK,
                        json,
                    )
                else ->
                    respond(
                        """[{"id":3,"patientId":14,"templateId":"025-2","date":"2026-10-01"},
                           {"id":5,"patientId":14,"templateId":"025-2","date":"2026-10-20","documentId":77,"patientSignedAt":"2026-10-20T10:00:00Z"},
                           null]""",
                        HttpStatusCode.OK,
                        json,
                    )
            }
        }
    private val repo = FormsRepository({ createHttpClient({ null }, engine) })
    private val created = mutableListOf<PatientFormsViewModel>()

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

    private fun opened() = PatientFormsViewModel(14, repo).also { created += it }

    @Test
    fun `the forms come newest first, a saved form shows without another read, and PDFs map to their forms`() {
        val classToTest = opened()
        classToTest.load()
        settle { classToTest.state.value.forms != null }
        assertThat(classToTest.state.value.forms?.map { it.id }).containsExactly(5L, 3L).inOrder()
        assertThat(classToTest.state.value.forms?.first()?.signed).isTrue()
        assertThat(classToTest.state.value.byDocument).containsExactly(77L, 5L)
        // The editor saves through the same repository: the list has it on return.
        runBlocking { repo.save(PatientForm(patientId = 14, date = "2026-11-04")) }
        settle { classToTest.state.value.forms?.size == 3 }
        assertThat(classToTest.state.value.forms?.first()?.id).isEqualTo(40L)
    }

    @Test
    fun `a failed first read is an error with Retry, a failed refresh a banner over the list`() {
        fail = true
        val classToTest = opened()
        classToTest.load()
        settle { classToTest.state.value.error != null }
        assertThat(classToTest.state.value.error?.text).isEqualTo(Res.string.formsLoadFailed)
        assertThat(classToTest.state.value.error?.requestId).isEqualTo("req-f")
        assertThat(classToTest.state.value.forms).isNull()
        fail = false
        classToTest.load()
        settle { classToTest.state.value.forms != null }
        assertThat(classToTest.state.value.error).isNull()
        fail = true
        classToTest.load()
        settle { classToTest.state.value.banner != null }
        assertThat(classToTest.state.value.forms).hasSize(2)
        assertThat(classToTest.state.value.error).isNull()
        // A good refresh (the same list) clears it.
        fail = false
        classToTest.load()
        settle { classToTest.state.value.banner == null }
        assertThat(classToTest.state.value.banner).isNull()
    }
}
