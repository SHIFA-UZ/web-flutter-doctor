@file:Suppress("ktlint:standard:max-line-length") // JSON fixtures

package shifa.doctor.ui.calendar

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Test
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.calendar.CalendarRepository
import shifa.doctor.data.calendar.DayKey
import shifa.doctor.data.model.CalendarEntry
import shifa.doctor.data.profile.ProfileRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.couldNotLoadThisDay
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {
    private val me = File("src/commonMain/composeResources/files/demo/doctors-me.json").readText()
    private val requested = mutableListOf<String>()
    private var failing = false
    private val engine =
        MockEngine { req ->
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            if (!req.url.encodedPath.endsWith("calendar")) return@MockEngine respond(me, HttpStatusCode.OK, json)
            val day = req.url.parameters["day"]!!
            requested += day
            if (failing) {
                return@MockEngine respond(
                    """{"status":500,"code":"INTERNAL","requestId":"req-cal"}""",
                    HttpStatusCode.InternalServerError,
                    json,
                )
            }
            // Each day: a free slot, a cancelled appointment and a block (UTC; Tashkent is +5).
            respond(
                """[{"type":"FREE_SLOT","startAt":"${day}T04:00:00Z","endAt":"${day}T04:30:00Z"},
                {"type":"APPOINTMENT","startAt":"${day}T05:00:00Z","endAt":"${day}T05:30:00Z","appointmentId":1,"patientName":"A","status":"CANCELLED"},
                {"type":"BLOCKED","startAt":"${day}T08:00:00Z","endAt":"${day}T09:00:00Z","blockReason":"Lunch"}]""",
                HttpStatusCode.OK,
                json,
            )
        }
    private val calendar = CalendarRepository({ createHttpClient({ null }, engine) })
    private val profile = ProfileRepository { createHttpClient({ null }, engine) }

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        runBlocking { profile.refresh() }
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun settle(check: () -> Boolean) {
        repeat(300) {
            if (check()) return
            Thread.sleep(10)
        }
    }

    @Test
    fun `opens on today in the practice zone, the day and the month read`() {
        val classToTest = CalendarViewModel(calendar, profile)
        settle { classToTest.state.value.day != null && requested.size > 20 }
        val s = classToTest.state.value
        assertThat(s.clock.zoneId).isEqualTo("Asia/Tashkent")
        assertThat(s.selected).isEqualTo(s.today)
        assertThat(s.rows.map { it::class.simpleName }).containsExactly("FreeSlot", "Appointment", "Blocked").inOrder()
        assertThat(requested).contains(s.today.toString())
        // The month is prefetched for the shading.
        assertThat(s.occupancy(LocalDate(s.today.year, s.today.monthNumber, 1))).isNotNull()
    }

    @Test
    fun `filters hide row kinds and say so when everything is hidden`() {
        val classToTest = CalendarViewModel(calendar, profile)
        settle { classToTest.state.value.day != null }
        classToTest.ui { it.copy(filter = AgendaFilter(appointments = false, free = false, blocked = true)) }
        assertThat(classToTest.state.value.rows.single()).isInstanceOf(CalendarEntry.Blocked::class.java)
        classToTest.ui { it.copy(filter = AgendaFilter(false, false, false)) }
        assertThat(classToTest.state.value.rows).isEmpty()
        assertThat(classToTest.state.value.day).isNotEmpty() // "Hidden by filters", not "No items"
    }

    @Test
    fun `selecting a day reads it, a failure keeps the request id, Retry forces it`() {
        val classToTest = CalendarViewModel(calendar, profile)
        settle { classToTest.state.value.day != null }
        failing = true
        classToTest.select(LocalDate(2030, 1, 15))
        settle { classToTest.state.value.error != null }
        assertThat(classToTest.state.value.error?.text).isEqualTo(Res.string.couldNotLoadThisDay)
        assertThat(classToTest.state.value.error?.requestId).isEqualTo("req-cal")
        failing = false
        classToTest.load(force = true)
        settle { classToTest.state.value.day != null }
        assertThat(classToTest.state.value.error).isNull()
        assertThat(calendar.state.value.days[DayKey(LocalDate(2030, 1, 15))]).hasSize(3)
    }

    @Test
    fun `paging the month keeps the selected day, a blocked day from midnight is a day off`() {
        val classToTest = CalendarViewModel(calendar, profile)
        settle { classToTest.state.value.day != null }
        val selected = classToTest.state.value.selected
        classToTest.month(1)
        assertThat(classToTest.state.value.selected).isEqualTo(selected)
        assertThat(classToTest.state.value.month.monthNumber).isNotEqualTo(selected.monthNumber)
    }

    @Test
    fun `a profile that arrives after opening moves today to the practice zone and reads the days again`() {
        val fresh = ProfileRepository { createHttpClient({ null }, engine) } // not read yet: no zone
        val classToTest = CalendarViewModel(calendar, fresh)
        settle { classToTest.state.value.day != null }
        assertThat(classToTest.state.value.clock.zoneMissing).isTrue()
        val before = requested.size
        runBlocking { fresh.refresh() }
        settle { classToTest.state.value.clock.zoneId == "Asia/Tashkent" && classToTest.state.value.day != null }
        val s = classToTest.state.value
        assertThat(s.selected).isEqualTo(s.clock.today())
        assertThat(requested.size).isGreaterThan(before) // the cache was dropped and the day read again
    }

    @Test
    fun `the filter sheet edits a draft, Apply commits, dismissing drops it`() {
        val classToTest = CalendarViewModel(calendar, profile)
        settle { classToTest.state.value.day != null }
        classToTest.ui { it.copy(filterDraft = it.filter) }
        classToTest.ui { it.copy(filterDraft = it.filterDraft?.copy(free = false)) }
        classToTest.ui { it.copy(filterDraft = null) } // dismissed
        assertThat(classToTest.state.value.filter.free).isTrue()
    }
}
