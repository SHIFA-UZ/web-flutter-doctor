@file:Suppress("ktlint:standard:max-line-length") // JSON fixtures

package shifa.doctor.ui.calendar

import androidx.lifecycle.viewModelScope
import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
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
import shifa.doctor.data.patients.PatientsRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.bookingRangeUnavailable
import shifa.doctor.generated.resources.patientAssignedName
import shifa.doctor.ui.navigation.ShellNotice
import shifa.doctor.utils.PracticeClock

// Practice zone Tashkent (UTC+5), now 08:00 there on 4 November. 6 November: 09:00, 09:30, 10:00 free at location 1
// (UTC 04:00–05:30) and 09:00 at location 2.
@OptIn(ExperimentalCoroutinesApi::class)
class BookingViewModelTest {
    private val clock =
        PracticeClock("Asia/Tashkent", { Instant.parse("2026-11-04T03:00:00Z") }, { TimeZone.of("Asia/Tashkent") })
    private val read = mutableListOf<String>()
    private val posted = mutableListOf<String>()
    private var bookStatus = HttpStatusCode.OK
    private var taken = false
    private var patientReads = 0
    private var patientsHold = CompletableDeferred(Unit)
    private val engine =
        MockEngine { req ->
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            val path = req.url.encodedPath
            when {
                req.method == HttpMethod.Post -> {
                    posted += req.body.toByteArray().decodeToString()
                    if (bookStatus == HttpStatusCode.OK) {
                        respond("", HttpStatusCode.OK)
                    } else {
                        respond(
                            """{"status":${bookStatus.value},"code":"X","requestId":"req-book"}""",
                            bookStatus,
                            json,
                        )
                    }
                }
                path.endsWith("for-assignment") -> {
                    patientReads++
                    patientsHold.await()
                    respond(
                        """{"content":[{"id":7,"name":"Zarina Usmonova"},{"id":3,"name":"Anna Karimova"},{"id":null,"name":"No id"}],"last":true}""",
                        HttpStatusCode.OK,
                        json,
                    )
                }
                else -> {
                    val day = req.url.parameters["day"]!!
                    read += day
                    respond(
                        """[{"type":"FREE_SLOT","startAt":"${day}T04:00:00Z","endAt":"${day}T04:30:00Z","locationId":1,"locationLabel":"Main"},
                        ${if (taken) "" else """{"type":"FREE_SLOT","startAt":"${day}T04:30:00Z","endAt":"${day}T05:00:00Z","locationId":1,"locationLabel":"Main"},"""}
                        {"type":"FREE_SLOT","startAt":"${day}T05:00:00Z","endAt":"${day}T05:30:00Z","locationId":1,"locationLabel":"Main"},
                        {"type":"FREE_SLOT","startAt":"${day}T04:00:00Z","endAt":"${day}T04:30:00Z","locationId":2,"locationLabel":"Branch"}]""",
                        HttpStatusCode.OK,
                        json,
                    )
                }
            }
        }
    private val calendar = CalendarRepository({ createHttpClient({ null }, engine) })
    private val screen = CoroutineScope(UnconfinedTestDispatcher())

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    private val created = mutableListOf<BookingViewModel>()

    @After fun tearDown() {
        screen.cancel()
        created.forEach { it.viewModelScope.cancel() }
        ShellNotice.pending.value = null
        Dispatchers.resetMain()
    }

    private fun settle(check: () -> Boolean) {
        repeat(300) {
            if (check()) return
            Thread.sleep(10)
        }
    }

    private fun opened(
        date: LocalDate = LocalDate(2026, 11, 6),
        wait: Boolean = true,
    ): BookingViewModel {
        val slot =
            runBlocking { calendar.day(DayKey(date)).getOrThrow() }
                .filterIsInstance<CalendarEntry.FreeSlot>().first { it.locationId == 1L }
        read.clear()
        val vm =
            BookingViewModel(
                slot,
                clock,
                "Check-up",
                calendar,
                AppointmentsRepository { createHttpClient({ null }, engine) },
                PatientsRepository { createHttpClient({ null }, engine) },
            )
        screen.launch { vm.follow() }
        created += vm
        settle { vm.state.value.day != null && (!wait || vm.state.value.patients != null) }
        return vm
    }

    @Test
    fun `two consecutive slots book as 60 minutes at the slot's location, then close with the name`() {
        val classToTest = opened()
        val s = classToTest.state.value
        assertThat(s.starts.map { it.locationId }.distinct()).containsExactly(1L)
        assertThat(s.ends).hasSize(3)
        classToTest.ui { it.copy(end = it.ends[1]) }
        classToTest.pickPatient(s.patients!!.first())
        classToTest.book()
        settle { classToTest.state.value.closed }
        assertThat(posted.single()).isEqualTo(
            """{"startAt":"2026-11-06T04:00:00Z","slotMinutes":60,"patientId":3,"isVideo":false,"reason":"Check-up","location":"Main","locationId":1}""",
        )
        assertThat(ShellNotice.pending.value?.text).isEqualTo(Res.string.patientAssignedName)
        assertThat(ShellNotice.pending.value?.args).containsExactly("Anna Karimova")
        assertThat(read).contains("2026-11-06")
    }

    @Test
    fun `a video booking carries no location id, an empty reason sends the default`() {
        val classToTest = opened()
        classToTest.pickPatient(classToTest.state.value.patients!!.last())
        classToTest.ui { it.copy(video = true, reason = "  ") }
        classToTest.book()
        settle { posted.isNotEmpty() }
        assertThat(posted.single()).isEqualTo(
            """{"startAt":"2026-11-06T04:00:00Z","slotMinutes":30,"patientId":7,"isVideo":true,"reason":"Check-up","location":"Video Consultation"}""",
        )
    }

    @Test
    fun `no patient, no request - and a past day cannot be booked at all`() {
        val classToTest = opened()
        classToTest.book()
        assertThat(classToTest.state.value.patientMissing).isTrue()
        val past = opened(LocalDate(2026, 11, 3))
        assertThat(past.state.value.pastDay).isTrue()
        past.ui { it.copy(patient = shifa.doctor.data.model.PatientOption(3, "Anna")) }
        past.book()
        assertThat(posted).isEmpty()
    }

    @Test
    fun `a taken range re-reads the day and keeps the form with its message`() {
        val classToTest = opened()
        classToTest.pickPatient(classToTest.state.value.patients!!.first())
        bookStatus = HttpStatusCode.Conflict
        classToTest.book()
        settle { classToTest.state.value.banner != null }
        val s = classToTest.state.value
        assertThat(s.closed).isFalse()
        assertThat(s.banner?.text).isEqualTo(Res.string.bookingRangeUnavailable)
        assertThat(s.banner?.requestId).isEqualTo("req-book")
        assertThat(read).contains("2026-11-06")
    }

    @Test
    fun `the picker lists by name, searches name or id, and drops rows without an id`() {
        val classToTest = opened()
        assertThat(
            classToTest.state.value.patients!!.map {
                it.name
            },
        ).containsExactly("Anna Karimova", "Zarina Usmonova").inOrder()
        classToTest.ui { it.copy(query = "zar") }
        assertThat(classToTest.state.value.shown.map { it.id }).containsExactly(7L)
        classToTest.ui { it.copy(query = "3") }
        assertThat(classToTest.state.value.shown.map { it.id }).containsExactly(3L)
    }

    @Test
    fun `a new start resets the end, back asks once something was picked`() {
        val classToTest = opened()
        classToTest.ui { it.copy(end = it.ends.last()) }
        classToTest.pickStart(classToTest.state.value.starts[1])
        assertThat(classToTest.state.value.minutes).isEqualTo(30)
        classToTest.back()
        assertThat(classToTest.state.value.askDiscard).isTrue()
    }

    @Test
    fun `after a conflict the range is narrowed to what is still free before a retry`() {
        val classToTest = opened()
        classToTest.ui { it.copy(end = it.ends[1]) }
        classToTest.pickPatient(classToTest.state.value.patients!!.first())
        bookStatus = HttpStatusCode.Conflict
        taken = true // 09:30 is booked meanwhile
        classToTest.book()
        settle { classToTest.state.value.banner != null }
        assertThat(classToTest.state.value.minutes).isEqualTo(30)
        assertThat(classToTest.state.value.rangeFree).isTrue()
    }

    @Test
    fun `opening the picker during the prefetch reads the list once`() {
        patientsHold = CompletableDeferred()
        val classToTest = opened(wait = false)
        settle { patientReads == 1 }
        classToTest.openPicker()
        patientsHold.complete(Unit)
        settle { classToTest.state.value.patients != null }
        assertThat(patientReads).isEqualTo(1)
        assertThat(classToTest.state.value.picking).isTrue()
    }

    @Test
    fun `a start taken meanwhile moves back to a free one with a message, an emptied day blocks Book`() {
        val classToTest = opened()
        classToTest.pickStart(classToTest.state.value.starts[1]) // 09:30
        taken = true
        runBlocking { calendar.day(DayKey(LocalDate(2026, 11, 6)), force = true) }
        settle { classToTest.state.value.banner != null }
        val s = classToTest.state.value
        assertThat(s.start.start).isEqualTo(s.slot.start)
        assertThat(s.banner?.text).isEqualTo(Res.string.bookingRangeUnavailable)
        assertThat(s.rangeFree).isTrue()
        // A day read as empty is not "unknown": nothing is free.
        assertThat(s.copy(day = emptyList()).rangeFree).isFalse()
    }
}
