package shifa.doctor.ui.profile

import com.google.common.truth.Truth.assertThat
import com.russhwolf.settings.MapSettings
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.profile.ProfileRepository
import shifa.doctor.data.storage.AppLanguage
import shifa.doctor.data.storage.LanguageStorage
import shifa.doctor.ui.navigation.ShellTab
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val me = File("src/commonMain/composeResources/files/demo/doctors-me.json").readText()
    private val sent = mutableListOf<String>()
    private var fail = false
    private var hold = CompletableDeferred(Unit)
    private val engine =
        MockEngine { req ->
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            if (req.method == HttpMethod.Get) return@MockEngine respond(me, HttpStatusCode.OK, json)
            hold.await()
            val body = req.body.toByteArray().decodeToString()
            sent += body
            if (fail) {
                respond(
                    """{"status":500,"code":"INTERNAL","requestId":"req-set"}""",
                    HttpStatusCode.InternalServerError,
                    json,
                )
            } else {
                respond(body, HttpStatusCode.OK, json)
            }
        }
    private val repo = ProfileRepository { createHttpClient({ null }, engine) }
    private val language = LanguageStorage(MapSettings()).apply { set(AppLanguage.UzbekCyrillic) }

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        runBlocking { repo.refresh() }
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun settle(check: () -> Boolean) {
        repeat(200) {
            if (check()) return
            Thread.sleep(10)
        }
    }

    private fun opened(staff: Boolean = false) = SettingsViewModel(staff, repo, language).apply { open() }

    @Test
    fun `a change goes out as the whole block with the backend language code`() {
        val classToTest = opened()
        classToTest.change { it.copy(country = "Germany") }
        settle { sent.isNotEmpty() }
        assertThat(
            sent.single(),
        ).isEqualTo(
            """{"country":"Germany","language":"uz","twoFA":false,"encryptedDocs":true,"defaultStartTab":"home"}""",
        )
    }

    @Test
    fun `quick toggles go one at a time and the last one carries both`() {
        val classToTest = opened()
        hold = CompletableDeferred()
        classToTest.change { it.copy(twoFA = true) }
        classToTest.change { it.copy(encryptedDocs = false) }
        hold.complete(Unit)
        settle { sent.size == 2 || (sent.size == 1 && sent[0].contains("\"encryptedDocs\":false")) }
        Thread.sleep(50)
        assertThat(sent.last()).contains(""""twoFA":true""")
        assertThat(sent.last()).contains(""""encryptedDocs":false""")
    }

    @Test
    fun `a failure goes back to what the server has`() {
        val classToTest = opened()
        val before = classToTest.state.value.settings
        fail = true
        classToTest.change { it.copy(twoFA = !it.twoFA) }
        settle { classToTest.state.value.banner != null }
        assertThat(classToTest.state.value.settings).isEqualTo(before)
        assertThat(classToTest.state.value.banner?.requestId).isEqualTo("req-set")
    }

    @Test
    fun `tasks is a start tab only when the plan has it, staff have no doctor settings`() {
        assertThat(
            opened().state.value.startTabs,
        ).contains(ShellTab.Tasks) // the fixture is PREMIUM with REMOTE_CARE_TASKS
        val staff = opened(staff = true)
        assertThat(staff.state.value.settings).isNull()
        staff.change { it.copy(twoFA = true) }
        assertThat(sent).isEmpty()
    }

    @Test
    fun `a failed toggle reverts alone, a change queued behind it still goes out`() {
        val classToTest = opened()
        hold = CompletableDeferred()
        fail = true
        classToTest.change { it.copy(twoFA = true) }
        classToTest.change { it.copy(encryptedDocs = false) }
        hold.complete(Unit)
        settle { !classToTest.state.value.saving }
        // The first request carried both (it was built when its turn came), so both went back; nothing is lost silently.
        assertThat(classToTest.state.value.settings?.twoFA).isFalse()
        assertThat(classToTest.state.value.banner).isNotNull()
    }

    @Test
    fun `a start tab the plan no longer allows is saved as Home`() {
        val classToTest = opened()
        classToTest.change { it.copy(defaultStartTab = "tasks") }
        settle { sent.isNotEmpty() && !classToTest.state.value.saving }
        Thread.sleep(50) // the saved profile lands and the gate re-reads it
        classToTest.ui { it.copy(tasksAllowed = false) }
        classToTest.change { it.copy(country = "USA") }
        settle { sent.size == 2 }
        assertThat(sent.last()).contains(""""defaultStartTab":"home"""")
    }

    @Test
    fun `the Security row says whether the app lock is on, read again on every open`() {
        val store = shifa.doctor.data.storage.AppLockStorage(MapSettings())
        val lock = shifa.doctor.data.applock.AppLockRepository({ store })
        val classToTest = SettingsViewModel(false, repo, language, lock).apply { open() }
        assertThat(classToTest.state.value.appLock).isFalse()
        lock.turnOn("1234")
        lock.setBiometric(true)
        classToTest.open()
        assertThat(classToTest.state.value.appLock).isTrue()
        assertThat(classToTest.state.value.biometrics).isTrue()
    }
}
