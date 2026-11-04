@file:Suppress("ktlint:standard:max-line-length") // JSON fixtures

package shifa.doctor.ui.navigation

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
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
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.clinics.ClinicsRepository
import shifa.doctor.data.profile.ProfileRepository

@OptIn(ExperimentalCoroutinesApi::class)
class ShellViewModelTest {
    private var startTab = "calendar"
    private var tier = "PREMIUM"
    private var clinics = """[{"clinicId":1,"name":"Shifa Demo Clinic"}]"""
    private var hold = CompletableDeferred(Unit)
    private val client by lazy {
        createHttpClient(
            { null },
            MockEngine { req ->
                val body =
                    if (req.url.encodedPath.endsWith("me/clinics")) {
                        clinics
                    } else {
                        hold.await()
                        """{"profile":{},"settings":{"defaultStartTab":"$startTab"},"subscription":{"tier":"$tier","features":[]}}"""
                    }
                respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        )
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = ShellViewModel(ProfileRepository { client }, ClinicsRepository { client })

    private fun ShellViewModel.settle() {
        repeat(200) {
            if (state.value.plan != Plan.Unknown) return
            Thread.sleep(10)
        }
    }

    @Test
    fun `the saved start tab opens once the profile is in`() {
        val classToTest = vm()
        classToTest.settle()
        assertThat(classToTest.state.value.tab).isEqualTo(ShellTab.Calendar)
        assertThat(classToTest.state.value.hasClinic).isTrue()
    }

    @Test
    fun `a start tab the doctor can't use falls back to Home`() {
        startTab = "tasks"
        tier = "BASIC"
        val basic = vm()
        basic.settle()
        assertThat(basic.state.value.tab).isEqualTo(ShellTab.Home)
        startTab = "clinic"
        tier = "PREMIUM"
        clinics = "[]"
        val solo = vm()
        solo.settle()
        assertThat(solo.state.value.tab).isEqualTo(ShellTab.Home)
        assertThat(solo.state.value.hasClinic).isFalse()
    }

    @Test
    fun `a tab picked before the profile arrives wins`() {
        hold = CompletableDeferred()
        val classToTest = vm()
        classToTest.select(ShellTab.Patients)
        hold.complete(Unit)
        classToTest.settle()
        assertThat(classToTest.state.value.tab).isEqualTo(ShellTab.Patients)
    }

    @Test
    fun `each tab keeps its stack, re-tapping goes to its root, Back walks home then out`() {
        val classToTest = vm()
        classToTest.settle()
        classToTest.select(ShellTab.Profile)
        classToTest.push(ShellRoute.Debug)
        assertThat(classToTest.state.value.barVisible).isFalse()
        classToTest.select(ShellTab.Chat)
        classToTest.select(ShellTab.Profile)
        assertThat(classToTest.state.value.route).isEqualTo(ShellRoute.Debug) // kept while away
        classToTest.select(ShellTab.Profile)
        assertThat(classToTest.state.value.route).isEqualTo(ShellRoute.Root(ShellTab.Profile)) // re-tap: root
        classToTest.push(ShellRoute.Language)
        classToTest.openMore()
        assertThat(classToTest.back()).isTrue() // closes More
        assertThat(classToTest.state.value.moreOpen).isFalse()
        assertThat(classToTest.back()).isTrue() // pops Language
        assertThat(classToTest.back()).isTrue() // Profile root → Home
        assertThat(classToTest.state.value.tab).isEqualTo(ShellTab.Home)
        assertThat(classToTest.back()).isFalse() // Home root: the app may close
    }

    @Test
    fun `a push before the profile arrives keeps the doctor where they went`() {
        hold = CompletableDeferred()
        val classToTest = vm()
        classToTest.push(ShellRoute.Language)
        hold.complete(Unit)
        classToTest.settle()
        assertThat(classToTest.state.value.tab).isEqualTo(ShellTab.Home)
        assertThat(classToTest.state.value.route).isEqualTo(ShellRoute.Language)
    }

    @Test
    fun `a gated tab picked early opens once it is allowed`() {
        hold = CompletableDeferred()
        val classToTest = vm()
        classToTest.select(ShellTab.Tasks) // plan unknown yet
        assertThat(classToTest.state.value.tab).isEqualTo(ShellTab.Home)
        hold.complete(Unit)
        classToTest.settle()
        assertThat(classToTest.state.value.tab).isEqualTo(ShellTab.Tasks)
    }
}
