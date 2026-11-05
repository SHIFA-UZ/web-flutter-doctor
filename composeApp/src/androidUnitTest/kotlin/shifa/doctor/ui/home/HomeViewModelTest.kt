@file:Suppress("ktlint:standard:max-line-length")

package shifa.doctor.ui.home

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Before
import org.junit.Test
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.calendar.CalendarRepository
import shifa.doctor.data.home.HomeRepository
import shifa.doctor.data.home.RefreshGate
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.homeLoadFailed
import shifa.doctor.utils.PracticeClock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class HomeViewModelTest {
    private var status = HttpStatusCode.OK
    private var reads = 0
    private val engine =
        MockEngine {
            reads++
            val body =
                """[{"type":"APPOINTMENT","startAt":"2026-11-05T05:00:00Z","endAt":"2026-11-05T05:30:00Z","appointmentId":3,"patientName":"Jasur","status":"CONFIRMED"}]"""
            respond(
                if (status == HttpStatusCode.OK) body else """{"status":500}""",
                status,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
    private val time = TestTimeSource()
    private val zones = mutableListOf<String?>()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private fun settle(check: () -> Boolean) {
        repeat(300) {
            if (check()) return
            Thread.sleep(10)
        }
    }

    private fun opened() =
        HomeViewModel(
            HomeRepository(CalendarRepository({ createHttpClient({ null }, engine) }, time, 60.seconds)) { true },
            { zone ->
                zones += zone
                PracticeClock(zone, { Instant.parse("2026-11-05T04:12:00Z") })
            },
            RefreshGate(60.seconds, time),
        )

    @Test
    fun `the tab's focus reads once a minute, pull-to-refresh always, keeping the content meanwhile`() {
        val classToTest = opened()
        classToTest.practiceZone("Asia/Tashkent")
        settle { !classToTest.state.value.loading }
        assertThat(classToTest.state.value.schedule?.hero?.appointmentId).isEqualTo(3L)
        val first = reads
        classToTest.focus()
        Thread.sleep(50)
        assertThat(reads).isEqualTo(first)
        val readsBefore = classToTest.state.value.reads
        classToTest.refresh()
        settle { !classToTest.state.value.refreshing }
        assertThat(reads).isGreaterThan(first)
        // An unchanged answer still counts as a read (the analytics card's KPIs follow each one).
        assertThat(classToTest.state.value.reads).isEqualTo(readsBefore + 1)
        assertThat(classToTest.state.value.schedule?.today).hasSize(1)
    }

    @Test
    fun `a failed read is an error with the content kept`() {
        val classToTest = opened()
        classToTest.practiceZone("Asia/Tashkent")
        settle { !classToTest.state.value.loading }
        status = HttpStatusCode.InternalServerError
        classToTest.refresh()
        settle { classToTest.state.value.error != null }
        assertThat(classToTest.state.value.error?.text).isEqualTo(Res.string.homeLoadFailed)
        assertThat(classToTest.state.value.schedule?.today).hasSize(1)
    }

    @Test
    fun `nothing is read before the practice zone, a changed zone reads at once`() {
        val classToTest = opened()
        classToTest.focus()
        classToTest.refresh()
        Thread.sleep(50)
        assertThat(reads).isEqualTo(0)
        classToTest.practiceZone("Asia/Tashkent")
        settle { !classToTest.state.value.loading }
        classToTest.practiceZone("Asia/Tashkent")
        classToTest.practiceZone("Europe/Moscow")
        settle { zones.size == 2 }
        assertThat(zones).containsExactly("Asia/Tashkent", "Europe/Moscow").inOrder()
    }

    @Test
    fun `the shell's counts follow the same gate, and come along before the zone is known`() {
        var counts = 0
        val classToTest = opened()
        classToTest.focus { counts++ }
        settle { counts == 1 }
        assertThat(reads).isEqualTo(0)
        classToTest.practiceZone("Asia/Tashkent")
        settle { !classToTest.state.value.loading }
        classToTest.focus { counts++ }
        Thread.sleep(50)
        assertThat(counts).isEqualTo(1)
        classToTest.refresh { counts++ }
        settle { counts == 2 && !classToTest.state.value.refreshing }
        time += 61.seconds
        classToTest.focus { counts++ }
        settle { counts == 3 }
        assertThat(counts).isEqualTo(3)
    }
}
