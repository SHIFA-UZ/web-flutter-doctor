@file:Suppress("ktlint:standard:max-line-length") // JSON fixtures

package shifa.doctor.ui.profile

import com.google.common.truth.Truth.assertThat
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
import shifa.doctor.data.legal.LegalRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.contractLoadFailed
import shifa.doctor.stopAll

@OptIn(ExperimentalCoroutinesApi::class)
class AboutViewModelTest {
    private val created = mutableListOf<androidx.lifecycle.ViewModel>()
    private val asked = java.util.concurrent.CopyOnWriteArrayList<String>()
    private var contract: Pair<HttpStatusCode, String> = HttpStatusCode.OK to """{"contractNumber":"SHIFA-0461","effectiveDate":"2026-06-01","status":"ACTIVE"}"""
    private var legal: Pair<HttpStatusCode, String> = HttpStatusCode.OK to """{"terms":"https://shifa.uz/#terms","privacy":"https://shifa.uz/#privacy","version":"1"}"""
    private var pdf = HttpStatusCode.OK
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val engine =
        MockEngine { req ->
            val path = req.url.encodedPath.substringAfter("/api/")
            asked += path + (req.url.parameters["lang"]?.let { "?lang=$it" } ?: "")
            when (path) {
                "public/legal" -> respond(legal.second, legal.first, json)
                "doctors/me/contract" -> respond(contract.second, contract.first, json)
                "doctors/me/contract/pdf" ->
                    if (pdf == HttpStatusCode.OK) {
                        respond(
                            "%PDF-1.7".encodeToByteArray(),
                            pdf,
                            headersOf(HttpHeaders.ContentType, "application/pdf"),
                        )
                    } else {
                        respond("""{"status":500,"requestId":"r-pdf"}""", pdf, json)
                    }
                else -> respond("{}", HttpStatusCode.OK, json)
            }
        }

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

    private fun opened(doctor: Boolean = true) =
        AboutViewModel(doctor, { "uz" }, LegalRepository({ createHttpClient({ null }, engine) })).also { created += it }

    @Test
    fun `the legal pages in the app's language and the doctor's contract, its PDF on opening`() {
        val classToTest = opened()
        settle { classToTest.state.value.links != null && classToTest.state.value.contract != null }
        assertThat(asked).contains("public/legal?lang=uz")
        assertThat(classToTest.state.value.links?.terms).isEqualTo("https://shifa.uz/#terms")
        assertThat(classToTest.state.value.contract?.contractNumber).isEqualTo("SHIFA-0461")
        assertThat(classToTest.state.value.contract?.from.toString()).isEqualTo("2026-06-01")
        classToTest.openContract()
        settle { classToTest.state.value.pdf != null }
        assertThat(classToTest.state.value.viewing).isTrue()
        assertThat(classToTest.state.value.pdf!!.decodeToString()).isEqualTo("%PDF-1.7")
    }

    @Test
    fun `no contract or an older backend shows none, staff never ask, a failed PDF says so`() {
        contract = HttpStatusCode.NotFound to """{"status":404,"code":"NO_CONTRACT"}"""
        legal = HttpStatusCode.NotFound to """{"status":404}"""
        val doctor = opened()
        settle { asked.count { it.startsWith("public/legal") || it == "doctors/me/contract" } == 2 }
        Thread.sleep(50)
        assertThat(doctor.state.value.contract).isNull()
        assertThat(doctor.state.value.links).isNull()

        asked.clear()
        opened(doctor = false)
        settle { asked.isNotEmpty() }
        Thread.sleep(50)
        assertThat(asked.none { it.startsWith("doctors/me/contract") }).isTrue()

        pdf = HttpStatusCode.InternalServerError
        doctor.openContract()
        settle { doctor.state.value.pdfError != null }
        assertThat(doctor.state.value.pdfError?.text).isEqualTo(Res.string.contractLoadFailed)
        assertThat(doctor.state.value.pdfError?.requestId).isEqualTo("r-pdf")
    }
}
