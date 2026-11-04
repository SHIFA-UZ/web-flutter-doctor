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
import shifa.doctor.data.profile.ProfileRepository
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    private val me = File("src/commonMain/composeResources/files/demo/doctors-me.json").readText()
    private var status = HttpStatusCode.OK
    private var failBody: String? = null
    private val paths = mutableListOf<String>()
    private val repo =
        ProfileRepository {
            createHttpClient(
                { null },
                MockEngine { req ->
                    paths += req.url.encodedPath
                    val body =
                        if (req.url.encodedPath.endsWith(
                                "me/profile",
                            )
                        ) {
                            """{"id":5,"role":"CLINIC_STAFF","firstName":"Dilfuza","lastName":"Y"}"""
                        } else {
                            me
                        }
                    val reply = if (status == HttpStatusCode.OK) body else failBody ?: body
                    respond(reply, status, headersOf(HttpHeaders.ContentType, "application/json"))
                },
            )
        }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private fun settle(check: () -> Boolean) {
        repeat(200) {
            if (check()) return
            Thread.sleep(10)
        }
    }

    @Test
    fun `the doctor's profile comes from the shared repository, reading it only when the shell hasn't`() {
        val classToTest = ProfileViewModel(false, repo)
        settle { classToTest.state.value.me != null }
        assertThat(classToTest.state.value.me?.profile?.firstName).isEqualTo("Aziza")
        assertThat(classToTest.state.value.loading).isFalse()
        val again = ProfileViewModel(false, repo)
        assertThat(again.state.value.me).isNotNull() // already there: no second read
        assertThat(paths.count { it.endsWith("doctors/me") }).isEqualTo(1)
    }

    @Test
    fun `a failed read keeps its request id, a later read from anywhere clears it, Retry reads again`() {
        status = HttpStatusCode.InternalServerError
        failBody = """{"status":500,"code":"INTERNAL","requestId":"req-77"}"""
        val classToTest = ProfileViewModel(false, repo)
        settle { classToTest.state.value.failed }
        assertThat(classToTest.state.value.requestId).isEqualTo("req-77")
        status = HttpStatusCode.OK
        kotlinx.coroutines.runBlocking { repo.refresh() } // the shell's resume refresh
        settle { !classToTest.state.value.failed }
        assertThat(classToTest.state.value.failed).isFalse()
        assertThat(classToTest.state.value.me).isNotNull()
    }

    @Test
    fun `staff read me_profile, never doctors_me`() {
        val classToTest = ProfileViewModel(true, repo)
        settle { classToTest.state.value.staffMe != null }
        assertThat(classToTest.state.value.staffMe?.firstName).isEqualTo("Dilfuza")
        assertThat(paths.none { it.endsWith("doctors/me") }).isTrue()
    }
}
