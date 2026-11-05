@file:Suppress("ktlint:standard:max-line-length")

package shifa.doctor.ui.patients

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import shifa.doctor.data.ai.AiRepository
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.profile.ProfileRepository
import shifa.doctor.data.storage.AppLanguage
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.aiRateLimit
import shifa.doctor.generated.resources.aiUnavailable
import shifa.doctor.generated.resources.intelTookTooLong

@OptIn(ExperimentalCoroutinesApi::class)
class PatientBriefingViewModelTest {
    // Each briefing request takes the next answer; a held one waits for [gate].
    private val answers = ArrayDeque<Pair<HttpStatusCode, String>>()
    private val asked = mutableListOf<String>()
    private var gate: CompletableDeferred<Unit>? = null
    private val client =
        createHttpClient(
            { null },
            MockEngine { req ->
                val path = req.url.encodedPath.substringAfter("/api/")
                val json = headersOf(HttpHeaders.ContentType, "application/json")
                if (path.startsWith("doctors/me")) return@MockEngine respond("{}", HttpStatusCode.OK, json)
                asked += "$path ${req.body.toByteArray().decodeToString()}"
                gate?.await()
                val (status, body) = answers.removeFirst()
                respond(body, status, json)
            },
        )

    private fun vm(language: AppLanguage = AppLanguage.English) =
        PatientBriefingViewModel(11, AiRepository { client }, { language }, ProfileRepository { client })

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private fun settle(check: () -> Boolean) {
        repeat(300) {
            if (check()) return
            Thread.sleep(10)
        }
    }

    private val ok = HttpStatusCode.OK to """{"briefing":"## Snapshot\n- Pain on **14**","documentCount":2,"appointmentCount":3}"""

    @Test
    fun `the first open generates in the app language, reopening shows it without asking again`() {
        answers += ok
        val classToTest = vm(AppLanguage.UzbekCyrillic)
        classToTest.open()
        settle { classToTest.state.value.briefing != null }
        assertThat(classToTest.state.value.briefing!!.documentCount).isEqualTo(2)
        assertThat(asked.single()).isEqualTo("ai/patient-briefing/11 {\"language\":\"uz\"}")
        classToTest.open()
        assertThat(asked).hasSize(1)
    }

    @Test
    fun `a second tap while generating doesn't ask twice`() {
        answers += ok
        gate = CompletableDeferred()
        val classToTest = vm()
        classToTest.open()
        settle { asked.isNotEmpty() }
        classToTest.generate()
        classToTest.open()
        assertThat(classToTest.state.value.loading).isTrue()
        gate!!.complete(Unit)
        settle { !classToTest.state.value.loading }
        assertThat(asked).hasSize(1)
    }

    @Test
    fun `nothing to brief on is the empty state, a blank text too`() {
        answers += HttpStatusCode.BadRequest to """{"status":400,"code":"BAD_REQUEST"}"""
        answers += HttpStatusCode.OK to """{"briefing":"  ","documentCount":0}"""
        val classToTest = vm()
        classToTest.open()
        settle { !classToTest.state.value.loading && asked.size == 1 }
        assertThat(classToTest.state.value.empty).isTrue()
        assertThat(classToTest.state.value.error).isNull()
        classToTest.generate()
        settle { !classToTest.state.value.loading && asked.size == 2 }
        assertThat(classToTest.state.value.empty).isTrue()
        assertThat(classToTest.state.value.briefing).isNull()
        // A briefing, then a Regenerate that finds nothing: the empty state replaces the old text.
        answers += ok
        answers += HttpStatusCode.BadRequest to """{"status":400}"""
        classToTest.generate()
        settle { classToTest.state.value.briefing != null }
        classToTest.generate()
        settle { !classToTest.state.value.loading && asked.size == 4 }
        assertThat(classToTest.state.value.briefing).isNull()
        assertThat(classToTest.state.value.empty).isTrue()
    }

    @Test
    fun `a timeout says Shifa took too long, not the connection`() {
        val slow =
            PatientBriefingViewModel(
                11,
                AiRepository {
                    createHttpClient(
                        { null },
                        MockEngine { throw io.ktor.client.plugins.HttpRequestTimeoutException("x", 60_000) },
                    )
                },
                { AppLanguage.English },
                ProfileRepository { client },
            )
        slow.open()
        settle { slow.state.value.error != null }
        assertThat(slow.state.value.error!!.text).isEqualTo(Res.string.intelTookTooLong)
    }

    @Test
    fun `the daily limit has its own message, a failed regenerate keeps the last briefing`() {
        answers += HttpStatusCode.TooManyRequests to """{"status":429,"code":"RATE_LIMITED","requestId":"r-1"}"""
        val classToTest = vm()
        classToTest.open()
        settle { classToTest.state.value.error != null }
        assertThat(classToTest.state.value.error!!.text).isEqualTo(Res.string.aiRateLimit)
        assertThat(classToTest.state.value.error!!.requestId).isEqualTo("r-1")

        answers += ok
        answers += HttpStatusCode.InternalServerError to """{"status":500,"code":"AI_UNAVAILABLE"}"""
        classToTest.generate()
        settle { classToTest.state.value.briefing != null }
        assertThat(classToTest.state.value.error).isNull()
        classToTest.copied()
        classToTest.generate()
        settle { classToTest.state.value.error != null }
        // The AI service down says so (AI_UNAVAILABLE); other failures say the briefing couldn't be made.
        assertThat(classToTest.state.value.error!!.text).isEqualTo(Res.string.aiUnavailable)
        assertThat(classToTest.state.value.briefing).isNotNull()
        assertThat(classToTest.state.value.copied).isFalse()
    }
}
