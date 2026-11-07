package shifa.doctor.ui.profile

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
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
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.practiceTimezoneSaved
import shifa.doctor.stopAll
import shifa.doctor.ui.navigation.ShellNotice
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class TimezoneViewModelTest {
    private var me = File("src/commonMain/composeResources/files/demo/doctors-me.json").readText()
    private val sent = java.util.concurrent.CopyOnWriteArrayList<String>()
    private var failing = false
    private val engine =
        MockEngine { req ->
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            if (req.method == HttpMethod.Get) return@MockEngine respond(me, HttpStatusCode.OK, json)
            val body = req.body.toByteArray().decodeToString()
            sent += body
            if (failing) {
                respond(
                    """{"status":500,"code":"INTERNAL","requestId":"req-6"}""",
                    HttpStatusCode.InternalServerError,
                    json,
                )
            } else {
                respond(body, HttpStatusCode.OK, json)
            }
        }
    private val repo = ProfileRepository { createHttpClient({ null }, engine) }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    private val created = mutableListOf<androidx.lifecycle.ViewModel>()

    // Saves are answered on the engine's threads: stop the ViewModels before the next test sets Main.
    @After fun tearDown() {
        created.stopAll()
        ShellNotice.pending.value = null
        Dispatchers.resetMain()
    }

    private fun opened(zone: String?): TimezoneViewModel {
        me =
            me.replace(
                Regex(""""timeZone"\s*:\s*"[^"]*""""),
                if (zone == null) "\"timeZone\":null" else "\"timeZone\":\"$zone\"",
            )
        runBlocking { repo.refresh() }
        return TimezoneViewModel(repo) { "Asia/Tashkent" }.also { created += it }.apply { open() }
    }

    private fun settle(check: () -> Boolean) {
        // Up to 10 s: a loaded CI runner can take more than a second or two (it returns as soon as the check holds).
        repeat(1000) {
            if (check()) return
            Thread.sleep(10)
        }
    }

    @Test
    fun `a first zone saves at once and closes with the saved notice`() {
        val classToTest = opened(null)
        assertThat(classToTest.state.value.zones.first()).isEqualTo("Asia/Tashkent") // the device's
        classToTest.pick("Asia/Tashkent")
        settle { classToTest.state.value.done }
        assertThat(sent).containsExactly("""{"timeZone":"Asia/Tashkent"}""")
        assertThat(ShellNotice.pending.value?.text).isEqualTo(Res.string.practiceTimezoneSaved)
        assertThat(repo.me.value?.profile?.timeZone).isEqualTo("Asia/Tashkent")
    }

    @Test
    fun `the same zone closes without a request, another one asks first`() {
        val classToTest = opened("Asia/Tashkent")
        classToTest.pick("Asia/Tashkent")
        assertThat(classToTest.state.value.done).isTrue()
        classToTest.open()
        classToTest.pick("Europe/Berlin")
        assertThat(classToTest.state.value.confirm).isEqualTo("Europe/Berlin")
        classToTest.cancelConfirm()
        assertThat(sent).isEmpty()
        classToTest.pick("Europe/Berlin")
        classToTest.confirm()
        settle { classToTest.state.value.done && sent.isNotEmpty() }
        assertThat(sent).containsExactly("""{"timeZone":"Europe/Berlin"}""")
    }

    @Test
    fun `a failed save keeps the sheet and the old zone, with the request id`() {
        val classToTest = opened(null)
        failing = true
        classToTest.pick("Europe/Berlin")
        settle { !classToTest.state.value.saving }
        assertThat(classToTest.state.value.done).isFalse()
        assertThat(classToTest.state.value.current).isNull()
        assertThat(classToTest.state.value.banner?.requestId).isEqualTo("req-6")
        assertThat(ShellNotice.pending.value).isNull()
    }

    @Test
    fun `09 00 shifted by today's offsets, with the day it lands on`() {
        val summer = kotlinx.datetime.Instant.parse("2026-07-01T12:00:00Z")
        assertThat(nineOClockIn("Asia/Tashkent", "Asia/Karachi", summer)).isEqualTo("09:00" to 0)
        assertThat(nineOClockIn("Asia/Tashkent", "Asia/Kolkata", summer)).isEqualTo("09:30" to 0)
        assertThat(nineOClockIn("Asia/Tashkent", "Europe/Berlin", summer)).isEqualTo("06:00" to 0) // UTC+2 in summer
        assertThat(nineOClockIn("Pacific/Auckland", "America/Los_Angeles", summer)).isEqualTo("14:00" to -1)
        assertThat(nineOClockIn("America/Los_Angeles", "Pacific/Auckland", summer)).isEqualTo("04:00" to 1)
        assertThat(nineOClockIn("Asia/Tashkent", "Not/AZone", summer)).isNull()
    }

    @Test
    fun `a save in flight survives closing and reopening, a rotation keeps the search`() {
        val classToTest = opened("Asia/Tashkent")
        classToTest.query("berl")
        classToTest.open() // composition again (rotation)
        assertThat(classToTest.state.value.query).isEqualTo("berl")
        classToTest.pick("Europe/Berlin")
        failing = true
        classToTest.confirm()
        settle { !classToTest.state.value.saving }
        // The warning stays with the error in it; Confirm retries.
        assertThat(classToTest.state.value.confirm).isEqualTo("Europe/Berlin")
        assertThat(classToTest.state.value.banner?.requestId).isEqualTo("req-6")
        failing = false
        classToTest.confirm()
        settle { classToTest.state.value.done }
        assertThat(sent).hasSize(2)
        classToTest.closed()
        classToTest.open()
        assertThat(classToTest.state.value.query).isEmpty()
        assertThat(classToTest.state.value.current).isEqualTo("Europe/Berlin")
    }
}
