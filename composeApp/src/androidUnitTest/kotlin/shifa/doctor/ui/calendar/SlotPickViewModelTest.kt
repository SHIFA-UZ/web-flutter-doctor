@file:Suppress("ktlint:standard:max-line-length") // JSON fixtures

package shifa.doctor.ui.calendar

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Test
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.calendar.CalendarRepository
import shifa.doctor.data.calendar.DayKey
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.couldNotLoadThisDay
import shifa.doctor.stopAll
import shifa.doctor.utils.PracticeClock

// Tashkent, now 08:00 on 4 November (03:00 UTC). Each day: 07:30 (already gone today), 09:00 and 09:30 free.
@OptIn(ExperimentalCoroutinesApi::class)
class SlotPickViewModelTest {
    private var now = Instant.parse("2026-11-04T03:00:00Z")
    private val clock = PracticeClock("Asia/Tashkent", { now }, { TimeZone.of("Asia/Tashkent") })
    private var fail = false
    private var taken = false
    private val engine =
        MockEngine { req ->
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            val day = req.url.parameters["day"]
            when {
                fail ->
                    respond(
                        """{"status":500,"code":"X","requestId":"req-day"}""",
                        HttpStatusCode.InternalServerError,
                        json,
                    )
                day == null -> respond("[]", HttpStatusCode.OK, json)
                else ->
                    respond(
                        """[{"type":"FREE_SLOT","startAt":"${day}T02:30:00Z","endAt":"${day}T03:00:00Z","locationId":1},
                        ${if (taken) "" else """{"type":"FREE_SLOT","startAt":"${day}T04:00:00Z","endAt":"${day}T04:30:00Z","locationId":1},"""}
                        {"type":"FREE_SLOT","startAt":"${day}T04:30:00Z","endAt":"${day}T05:00:00Z","locationId":1}]""",
                        HttpStatusCode.OK,
                        json,
                    )
            }
        }
    private val calendar = CalendarRepository({ createHttpClient({ null }, engine) })
    private val screen = CoroutineScope(UnconfinedTestDispatcher())
    private val created = mutableListOf<SlotPickViewModel>()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() {
        screen.cancel()
        created.stopAll()
        Dispatchers.resetMain()
    }

    private fun settle(check: () -> Boolean) {
        repeat(300) {
            if (check()) return
            Thread.sleep(10)
        }
    }

    private fun opened() =
        SlotPickViewModel(clock, calendar).also {
            created += it
            screen.launch { it.follow() }
            it.open()
            settle { it.state.value.slots != null }
        }

    @Test
    fun `today shows only the slots still ahead, a past day stays closed`() {
        val classToTest = opened()
        assertThat(classToTest.state.value.date).isEqualTo(LocalDate(2026, 11, 4))
        assertThat(classToTest.state.value.slots!!.map { it.start.toString() })
            .containsExactly("2026-11-04T04:00:00Z", "2026-11-04T04:30:00Z").inOrder()
        classToTest.pick(LocalDate(2026, 11, 3))
        assertThat(classToTest.state.value.date).isEqualTo(LocalDate(2026, 11, 4))
    }

    @Test
    fun `a slot booked elsewhere leaves the picker and its selection`() {
        val classToTest = opened()
        classToTest.pick(LocalDate(2026, 11, 6))
        settle { classToTest.state.value.slots?.size == 3 }
        val nine = classToTest.state.value.slots!!.first { it.start.toString() == "2026-11-06T04:00:00Z" }
        classToTest.select(nine)
        taken = true
        runBlocking { calendar.day(DayKey(LocalDate(2026, 11, 6)), force = true) }
        settle { classToTest.state.value.target == null }
        assertThat(classToTest.state.value.target).isNull()
        assertThat(classToTest.state.value.slots).hasSize(2)
    }

    @Test
    fun `a failed day says so with its request id`() {
        fail = true
        val classToTest = SlotPickViewModel(clock, calendar).also { created += it }
        classToTest.pick(LocalDate(2026, 11, 6))
        settle { classToTest.state.value.error != null }
        assertThat(classToTest.state.value.error?.text).isEqualTo(Res.string.couldNotLoadThisDay)
        assertThat(classToTest.state.value.error?.requestId).isEqualTo("req-day")
    }

    @Test
    fun `a slot that starts while the sheet is open drops out with its selection`() {
        val classToTest = opened()
        val nine = classToTest.state.value.slots!!.first()
        classToTest.select(nine)
        now = Instant.parse("2026-11-04T04:05:00Z")
        assertThat(classToTest.confirm()).isNull()
        assertThat(classToTest.state.value.slots!!.map { it.start.toString() }).containsExactly("2026-11-04T04:30:00Z")
    }
}
