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
import shifa.doctor.data.badges.BadgesRepository
import shifa.doctor.data.clinics.ClinicsRepository
import shifa.doctor.data.profile.ProfileRepository

@OptIn(ExperimentalCoroutinesApi::class)
class ShellViewModelTest {
    private var startTab = "calendar"
    private var tier = "PREMIUM"
    private var clinics = """[{"clinicId":1,"name":"Shifa Demo Clinic"}]"""
    private var hold = CompletableDeferred(Unit)
    private val paths = mutableListOf<String>()
    private var chat = 1
    private val client by lazy {
        createHttpClient(
            { null },
            MockEngine { req ->
                paths += req.url.encodedPath
                val body =
                    if (req.url.encodedPath.endsWith("me/clinics")) {
                        clinics
                    } else if (req.url.encodedPath.endsWith("messages/unread-count")) {
                        """{"count":${chat++}}"""
                    } else if (req.url.encodedPath.endsWith("unread/count")) {
                        """{"count":140}"""
                    } else if (req.url.encodedPath.endsWith("/tasks")) {
                        """[{"id":1},{"id":2}]"""
                    } else if (req.url.encodedPath.endsWith("me/profile")) {
                        """{"id":5,"role":"CLINIC_STAFF","firstName":"Dilfuza","lastName":"Y","photoUrl":"https://x/p.jpg"}"""
                    } else {
                        hold.await()
                        """{"profile":{},"settings":{"defaultStartTab":"$startTab"},"subscription":{"tier":"$tier","features":[]}}"""
                    }
                respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        )
    }

    private val main = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(main)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(staff: Boolean = false) =
        ShellViewModel(staff, ProfileRepository { client }, ClinicsRepository { client }, BadgesRepository { client })

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

    @Test
    fun `clinic staff start on Clinic with four tabs, never ask for the doctor profile, Back goes to Clinic`() {
        val classToTest = vm(staff = true)
        repeat(200) { if (classToTest.state.value.avatarUrl == null) Thread.sleep(10) }
        val s = classToTest.state.value
        assertThat(s.tab).isEqualTo(ShellTab.Clinic)
        assertThat(
            s.barTabs,
        ).containsExactly(ShellTab.Chat, ShellTab.Clinic, ShellTab.Notifications, ShellTab.Profile).inOrder()
        assertThat(s.avatarUrl).isEqualTo("https://x/p.jpg")
        assertThat(paths.none { it.endsWith("doctors/me") }).isTrue()
        classToTest.select(ShellTab.Tasks) // not a staff tab
        assertThat(classToTest.state.value.tab).isEqualTo(ShellTab.Clinic)
        classToTest.select(ShellTab.Chat)
        assertThat(classToTest.back()).isTrue()
        assertThat(classToTest.state.value.tab).isEqualTo(ShellTab.Clinic)
        assertThat(classToTest.back()).isFalse()
    }

    private fun waitFor(check: () -> Boolean) {
        repeat(200) {
            if (check()) return
            Thread.sleep(10)
        }
    }

    @Test
    fun `chat is polled every 10 s only in front, notifications on each return, active tasks once`() {
        val classToTest = vm()
        classToTest.settle()
        waitFor { classToTest.state.value.activeTasks == 2 }
        assertThat(classToTest.state.value.activeTasks).isEqualTo(2)
        classToTest.onForeground()
        classToTest.refreshNotifications()
        waitFor { classToTest.state.value.chatUnread == 1 && classToTest.state.value.notificationsUnread == 140 }
        assertThat(classToTest.state.value.notificationsUnread).isEqualTo(140)
        main.scheduler.advanceTimeBy(10_001)
        waitFor { classToTest.state.value.chatUnread == 2 }
        assertThat(classToTest.state.value.chatUnread).isEqualTo(2)
        classToTest.onBackground()
        main.scheduler.advanceTimeBy(60_000)
        Thread.sleep(100)
        assertThat(classToTest.state.value.chatUnread).isEqualTo(2) // nothing polled in the background
    }
}
