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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScheduler
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
import shifa.doctor.data.calendar.BlocksRepository
import shifa.doctor.data.calendar.CalendarRepository
import shifa.doctor.data.calendar.DayKey
import shifa.doctor.data.model.CalendarEntry
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.blockTimeSuccessWithCancel
import shifa.doctor.generated.resources.unblockSuccess
import shifa.doctor.stopAll
import shifa.doctor.ui.navigation.ShellNotice
import shifa.doctor.utils.PracticeClock

// Practice zone Tashkent (UTC+5), now 08:00 there on 4 November.
@OptIn(ExperimentalCoroutinesApi::class)
class BlockViewModelTest {
    private val clock =
        PracticeClock("Asia/Tashkent", { Instant.parse("2026-11-04T03:00:00Z") }, { TimeZone.of("Asia/Tashkent") })
    private val scheduler = TestCoroutineScheduler()
    private val read = mutableListOf<String>()
    private val counts = mutableListOf<String>()
    private val sent = mutableListOf<String>()
    private var countFails = false
    private var hold7 = CompletableDeferred(Unit)
    private val engine =
        MockEngine { req ->
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            val path = req.url.encodedPath
            when {
                path.endsWith("overlapping-count") -> {
                    counts += "${req.url.parameters["startAt"]}..${req.url.parameters["endAt"]}"
                    if (countFails) {
                        respond(
                            "",
                            HttpStatusCode.InternalServerError,
                        )
                    } else {
                        respond("""{"count":2}""", HttpStatusCode.OK, json)
                    }
                }
                req.method == HttpMethod.Post -> {
                    sent += req.body.toByteArray().decodeToString()
                    respond(
                        """{"startAt":"x","endAt":"y","cancelOverlappingAppointments":true,"id":5,"cancelledAppointmentCount":2}""",
                        HttpStatusCode.OK,
                        json,
                    )
                }
                req.method == HttpMethod.Delete -> {
                    sent += "DELETE $path" + req.url.encodedQuery.let { if (it.isEmpty()) "" else "?$it" }
                    respond("", HttpStatusCode.OK)
                }
                else -> {
                    val day = req.url.parameters["day"]!!
                    read += day
                    if (day == "2026-11-07") hold7.await()
                    respond(
                        """[{"type":"BLOCKED","startAt":"${day}T08:00:00Z","endAt":"${day}T09:00:00Z","blockId":5,"blockReason":"Lunch"}]""",
                        HttpStatusCode.OK,
                        json,
                    )
                }
            }
        }
    private val calendar = CalendarRepository({ createHttpClient({ null }, engine) })
    private val blocks = BlocksRepository { createHttpClient({ null }, engine) }
    private val created = mutableListOf<androidx.lifecycle.ViewModel>()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))

    @After fun tearDown() {
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

    /** Lets the 300 ms debounce pass and the count arrive. */
    private fun debounce(check: () -> Boolean) {
        scheduler.advanceTimeBy(301)
        scheduler.runCurrent()
        settle(check)
    }

    private fun opened(
        date: LocalDate = LocalDate(2026, 11, 6),
        clock: PracticeClock = this.clock,
    ) = BlockViewModel(date, clock, calendar, blocks).also { created += it }

    @Test
    fun `a whole day is midnight to midnight in the practice zone and its count is read once after the changes settle`() {
        val classToTest = opened()
        classToTest.edit { it.copy(reason = "Va") }
        classToTest.edit { it.copy(reason = "Vacation") }
        debounce { classToTest.state.value.count != null }
        assertThat(
            counts,
        ).containsExactly("2026-11-05T19:00:00Z..2026-11-06T19:00:00Z") // the reason doesn't change the range
        assertThat(classToTest.state.value.count).isEqualTo(2)
    }

    @Test
    fun `blocking sends the range, the reason and the flag, re-reads the covered days and reports the cancelled ones`() {
        runBlocking { calendar.day(DayKey(LocalDate(2026, 11, 7))) }
        read.clear()
        val classToTest = opened()
        classToTest.edit { it.copy(mode = BlockMode.Days, to = LocalDate(2026, 11, 8), reason = "  Vacation ") }
        classToTest.block()
        settle { classToTest.state.value.done }
        assertThat(sent.single()).isEqualTo(
            """{"startAt":"2026-11-05T19:00:00Z","endAt":"2026-11-08T19:00:00Z","cancelOverlappingAppointments":true,"reason":"Vacation"}""",
        )
        assertThat(read).containsAtLeast("2026-11-06", "2026-11-07") // the first day and the cached one in range
        assertThat(read).doesNotContain("2026-11-08") // not in the calendar yet: read when shown
        assertThat(ShellNotice.pending.value?.text).isEqualTo(Res.string.blockTimeSuccessWithCancel)
        assertThat(ShellNotice.pending.value?.args).containsExactly("2")
    }

    @Test
    fun `hours must end after they start - nothing is counted or sent until then`() {
        val classToTest = opened()
        debounce { counts.isNotEmpty() }
        counts.clear()
        classToTest.edit { it.copy(mode = BlockMode.Hours, startMinute = 14 * 60, endMinute = 13 * 60) }
        debounce { true }
        assertThat(classToTest.state.value.timesInvalid).isTrue()
        assertThat(counts).isEmpty()
        classToTest.block()
        assertThat(sent).isEmpty()
        classToTest.edit { it.copy(endMinute = 24 * 60) }
        debounce { counts.isNotEmpty() }
        assertThat(counts.single()).isEqualTo("2026-11-06T09:00:00Z..2026-11-06T19:00:00Z")
    }

    @Test
    fun `a failed count is said, never shown as zero`() {
        countFails = true
        val classToTest = opened()
        debounce { classToTest.state.value.countFailed }
        assertThat(classToTest.state.value.countFailed).isTrue()
        assertThat(classToTest.state.value.count).isNull()
    }

    @Test
    fun `picking From after To drags To along, and the other way round`() {
        val classToTest = opened()
        classToTest.edit { it.copy(mode = BlockMode.Days, picking = DateField.From) }
        classToTest.pickDate(LocalDate(2026, 11, 10))
        assertThat(classToTest.state.value.to).isEqualTo(LocalDate(2026, 11, 10))
        classToTest.edit { it.copy(picking = DateField.To) }
        classToTest.pickDate(LocalDate(2026, 11, 8))
        assertThat(classToTest.state.value.from).isEqualTo(LocalDate(2026, 11, 8))
    }

    @Test
    fun `a whole day on the autumn DST switch is 25 hours`() {
        val berlin =
            PracticeClock("Europe/Berlin", { Instant.parse("2026-10-20T08:00:00Z") }, { TimeZone.of("Europe/Berlin") })
        val range = opened(LocalDate(2026, 10, 25), berlin).state.value.range!!
        assertThat(range.first).isEqualTo(Instant.parse("2026-10-24T22:00:00Z"))
        assertThat((range.second - range.first).inWholeHours).isEqualTo(25)
    }

    @Test
    fun `removing a block deletes it, re-reads every day showing it and closes with the notice`() {
        runBlocking {
            calendar.day(DayKey(LocalDate(2026, 11, 6)))
            calendar.day(DayKey(LocalDate(2026, 11, 7)))
        }
        read.clear()
        val entry = calendar.state.value.days.getValue(DayKey(LocalDate(2026, 11, 6))).single() as CalendarEntry.Blocked
        val classToTest = BlockPanelViewModel(entry, clock, calendar, blocks).also { created += it }
        classToTest.remove()
        settle { classToTest.state.value.done }
        assertThat(sent.single()).isEqualTo("DELETE /api/schedule/blocks/5")
        assertThat(read).containsAtLeast("2026-11-06", "2026-11-07")
        assertThat(ShellNotice.pending.value?.text).isEqualTo(Res.string.unblockSuccess)

        val noId = BlockPanelViewModel(entry.copy(blockId = null), clock, calendar, blocks).also { created += it }
        noId.remove()
        assertThat(sent).hasSize(1)
    }

    @Test
    fun `a colleague's block is removed in their calendar and their days are read again`() {
        runBlocking { calendar.day(DayKey(LocalDate(2026, 11, 6), 22)) }
        read.clear()
        val entry =
            calendar.state.value.days.getValue(
                DayKey(LocalDate(2026, 11, 6), 22),
            ).single() as CalendarEntry.Blocked
        val classToTest = BlockPanelViewModel(entry, clock, calendar, blocks, doctorId = 22).also { created += it }
        classToTest.remove()
        settle { classToTest.state.value.done }
        assertThat(sent.single()).isEqualTo("DELETE /api/schedule/blocks/5?doctorId=22")
        assertThat(read).contains("2026-11-06")
        assertThat(calendar.state.value.days.keys.map { it.doctorId }.toSet()).containsExactly(22L)
    }

    @Test
    fun `hours inside a DST gap that turn backwards in UTC are refused`() {
        val berlin =
            PracticeClock("Europe/Berlin", { Instant.parse("2027-03-20T08:00:00Z") }, { TimeZone.of("Europe/Berlin") })
        val classToTest = opened(LocalDate(2027, 3, 28), berlin)
        classToTest.edit { it.copy(mode = BlockMode.Hours, startMinute = 2 * 60 + 30, endMinute = 3 * 60 + 15) }
        assertThat(classToTest.state.value.timesInvalid).isTrue()
        assertThat(classToTest.state.value.range).isNull()
        // 01:30–02:30 doesn't turn backwards, but 02:30 doesn't exist that day.
        classToTest.edit { it.copy(startMinute = 90, endMinute = 150) }
        assertThat(classToTest.state.value.timesInvalid).isTrue()
        classToTest.edit { it.copy(endMinute = 180) }
        assertThat(classToTest.state.value.timesInvalid).isFalse()
    }

    @Test
    fun `a covered day still being read when the block is made is read again`() {
        hold7 = CompletableDeferred()
        val prefetch = Thread { runBlocking { calendar.day(DayKey(LocalDate(2026, 11, 7))) } }.apply { start() }
        settle { read.contains("2026-11-07") }
        val classToTest = opened()
        classToTest.edit { it.copy(mode = BlockMode.Days, to = LocalDate(2026, 11, 8)) }
        classToTest.block()
        settle { sent.isNotEmpty() }
        Thread.sleep(100) // the refresh is chosen while 7 November is still being read
        hold7.complete(Unit)
        settle { classToTest.state.value.done }
        prefetch.join()
        assertThat(read.count { it == "2026-11-07" }).isEqualTo(2)
    }
}
