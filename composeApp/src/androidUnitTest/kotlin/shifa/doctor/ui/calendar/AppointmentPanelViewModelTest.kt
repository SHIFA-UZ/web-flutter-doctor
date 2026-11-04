@file:Suppress("ktlint:standard:max-line-length") // JSON fixtures

package shifa.doctor.ui.calendar

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
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
import shifa.doctor.data.appointments.AppointmentsRepository
import shifa.doctor.data.calendar.CalendarRepository
import shifa.doctor.data.calendar.DayKey
import shifa.doctor.data.model.CalendarEntry
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.appointmentCancelled
import shifa.doctor.generated.resources.appointmentMoved
import shifa.doctor.generated.resources.durationChanged
import shifa.doctor.stopAll
import shifa.doctor.ui.navigation.ShellNotice
import shifa.doctor.utils.PracticeClock

// Practice zone Tashkent (UTC+5), now 08:00 there on 4 November. The appointment is 10:00–10:30, two free slots follow.
@OptIn(ExperimentalCoroutinesApi::class)
class AppointmentPanelViewModelTest {
    private val clock =
        PracticeClock("Asia/Tashkent", { Instant.parse("2026-11-04T03:00:00Z") }, { TimeZone.of("Asia/Tashkent") })
    private val today = LocalDate(2026, 11, 4)
    private val read = mutableListOf<String>()
    private val writes = mutableListOf<String>()
    private var failWrites = false
    private var failDay: String? = null
    private var empty = false
    private val engine =
        MockEngine { req ->
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            if (req.method == HttpMethod.Get) {
                val day = req.url.parameters["day"]!!
                read += day
                if (empty) return@MockEngine respond("[]", HttpStatusCode.OK, json)
                if (day == failDay) {
                    return@MockEngine respond(
                        """{"status":503,"code":"UNAVAILABLE","requestId":"req-day"}""",
                        HttpStatusCode.ServiceUnavailable,
                        json,
                    )
                }
                return@MockEngine respond(
                    """[{"type":"APPOINTMENT","startAt":"${day}T05:00:00Z","endAt":"${day}T05:30:00Z","appointmentId":7,"patientName":"A","status":"CONFIRMED","locationId":1},
                    {"type":"FREE_SLOT","startAt":"${day}T05:30:00Z","endAt":"${day}T06:00:00Z","locationId":1},
                    {"type":"FREE_SLOT","startAt":"${day}T06:00:00Z","endAt":"${day}T06:30:00Z","locationId":1}]""",
                    HttpStatusCode.OK,
                    json,
                )
            }
            writes += "${req.method.value} ${req.url.encodedPath} ${req.body.toByteArray().decodeToString()}"
            if (failWrites) {
                respond("""{"status":409,"code":"SLOT_TAKEN","requestId":"req-appt"}""", HttpStatusCode.Conflict, json)
            } else {
                respond("", HttpStatusCode.OK)
            }
        }
    private val calendar = CalendarRepository({ createHttpClient({ null }, engine) })
    private val appointments = AppointmentsRepository { createHttpClient({ null }, engine) }

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        runBlocking { calendar.day(DayKey(today)) }
        read.clear()
    }

    private val screen = CoroutineScope(UnconfinedTestDispatcher())

    private val created = mutableListOf<AppointmentPanelViewModel>()

    @After fun tearDown() {
        screen.cancel()
        created.stopAll()
        ShellNotice.pending.value = null
        Dispatchers.resetMain()
    }

    private fun settle(check: () -> Boolean) {
        repeat(300) {
            if (check()) return
            Thread.sleep(10)
        }
    }

    private fun opened(): AppointmentPanelViewModel {
        val entry =
            calendar.state.value.days.getValue(
                DayKey(today),
            ).filterIsInstance<CalendarEntry.Appointment>().single()
        // The screen runs follow() while it is shown.
        return AppointmentPanelViewModel(entry, clock, calendar, appointments).also {
                vm ->
            created += vm
            screen.launch { vm.follow() }
        }
    }

    @Test
    fun `the length can grow over the free slots after it, and applying re-reads the day`() {
        val classToTest = opened()
        settle { classToTest.state.value.day.isNotEmpty() }
        assertThat(classToTest.durations).containsExactly(30, 60, 90).inOrder()
        classToTest.ui { it.copy(minutes = 60) }
        classToTest.applyDuration()
        settle { !classToTest.state.value.busy && classToTest.state.value.banner != null }
        assertThat(
            writes.single(),
        ).isEqualTo("""PUT /api/appointments/7/change {"startAt":"2026-11-04T05:00:00Z","slotMinutes":60}""")
        assertThat(classToTest.state.value.banner?.text).isEqualTo(Res.string.durationChanged)
        assertThat(read).contains(today.toString())
    }

    @Test
    fun `a move keeps the length, refreshes both days and follows the appointment there`() {
        val classToTest = opened()
        val tomorrow = LocalDate(2026, 11, 5)
        classToTest.rescheduleOn(tomorrow)
        settle { classToTest.state.value.rescheduleSlots != null }
        val slot = classToTest.state.value.rescheduleSlots!!.first()
        classToTest.ui { it.copy(target = slot) }
        classToTest.move()
        settle { classToTest.state.value.closed }
        assertThat(
            writes.single(),
        ).isEqualTo("""PUT /api/appointments/7/change {"startAt":"2026-11-05T05:30:00Z","slotMinutes":30}""")
        assertThat(read).containsAtLeast(today.toString(), tomorrow.toString())
        assertThat(classToTest.state.value.rescheduleDate).isNull()
        assertThat(clock.local(classToTest.state.value.appointment.start).date).isEqualTo(tomorrow)
        assertThat(ShellNotice.pending.value?.text).isEqualTo(Res.string.appointmentMoved)
    }

    @Test
    fun `a failed cancel keeps the question open with the request id, a good one closes with a notice`() {
        val classToTest = opened()
        classToTest.ui { it.copy(askCancel = true) }
        failWrites = true
        classToTest.cancel()
        settle { !classToTest.state.value.busy && classToTest.state.value.cancelError != null }
        assertThat(classToTest.state.value.askCancel).isTrue()
        assertThat(classToTest.state.value.closed).isFalse()
        assertThat(classToTest.state.value.cancelError?.requestId).isEqualTo("req-appt")
        // It may have gone through: the day is read again.
        assertThat(read).contains(today.toString())
        read.clear()

        failWrites = false
        classToTest.cancel()
        settle { classToTest.state.value.closed }
        assertThat(writes.last()).isEqualTo("DELETE /api/appointments/7 ")
        assertThat(ShellNotice.pending.value?.text).isEqualTo(Res.string.appointmentCancelled)
        assertThat(read).contains(today.toString())
    }

    @Test
    fun `a failed slot read says so with the request id instead of no slots`() {
        val classToTest = opened()
        failDay = "2026-11-06"
        classToTest.rescheduleOn(LocalDate(2026, 11, 6))
        settle { classToTest.state.value.rescheduleError != null }
        assertThat(classToTest.state.value.rescheduleSlots).isNull()
        assertThat(classToTest.state.value.rescheduleError?.requestId).isEqualTo("req-day")
        failDay = null
        classToTest.rescheduleOn(LocalDate(2026, 11, 6))
        settle { classToTest.state.value.rescheduleSlots != null }
        assertThat(classToTest.state.value.rescheduleError).isNull()
        // Past days are closed.
        classToTest.rescheduleOn(LocalDate(2026, 11, 3))
        assertThat(classToTest.state.value.rescheduleDate).isEqualTo(LocalDate(2026, 11, 6))
    }

    @Test
    fun `an unapplied duration pick survives other days arriving in the cache`() {
        val classToTest = opened()
        settle { classToTest.state.value.day.isNotEmpty() }
        classToTest.ui { it.copy(minutes = 90) }
        runBlocking { calendar.day(DayKey(LocalDate(2026, 11, 9))) }
        assertThat(classToTest.state.value.minutes).isEqualTo(90)
    }

    @Test
    fun `back asks before dropping an unapplied length, and the panel closes when its visit is gone`() {
        val classToTest = opened()
        settle { classToTest.state.value.day.isNotEmpty() }
        classToTest.ui { it.copy(minutes = 60) }
        classToTest.back()
        assertThat(classToTest.state.value.askDiscard).isTrue()
        classToTest.back()
        assertThat(classToTest.state.value.askDiscard).isFalse()
        assertThat(classToTest.state.value.closed).isFalse()

        empty = true
        runBlocking { calendar.day(DayKey(today), force = true) }
        settle { classToTest.state.value.closed }
        assertThat(classToTest.state.value.closed).isTrue()
    }
}
