@file:Suppress("ktlint:standard:max-line-length")

package shifa.doctor.data.home

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.Test
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.calendar.CalendarRepository
import shifa.doctor.utils.PracticeClock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class HomeRepositoryTest {
    // Days by date (Tashkent, UTC+5); a missing day is empty, "fail" answers 500.
    private val days = mutableMapOf<String, String>()
    private val asked = mutableListOf<String>()

    private fun visit(
        id: Long,
        start: String,
        end: String,
        status: String = "CONFIRMED",
        name: String? = "P$id",
    ) =
        """{"type":"APPOINTMENT","startAt":"$start","endAt":"$end","appointmentId":$id,"patientId":$id,"patientName":${name?.let { "\"$it\"" } ?: "null"},"status":"$status","location":"Clinic"}"""

    private val engine =
        MockEngine { req ->
            val day = req.url.parameters["day"].orEmpty()
            asked += day
            val body = days[day]
            if (body == "fail") {
                respond(
                    """{"status":500}""",
                    HttpStatusCode.InternalServerError,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            } else {
                respond(body ?: "[]", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
    private val calendar = CalendarRepository({ createHttpClient({ null }, engine) }, TestTimeSource(), 60.seconds)
    private var signedIn = true
    private val classToTest = HomeRepository(calendar) { signedIn }

    // 09:12 in Tashkent on 5 November.
    private fun clock(at: String = "2026-11-05T04:12:00Z") = PracticeClock("Asia/Tashkent", { Instant.parse(at) })

    @Test
    fun `the visit in its slot is the hero, now, and a cancelled one in its slot is skipped`() =
        runTest {
            days["2026-11-05"] =
                "[" +
                listOf(
                    visit(1, "2026-11-05T03:00:00Z", "2026-11-05T03:30:00Z", "COMPLETED"),
                    visit(2, "2026-11-05T04:00:00Z", "2026-11-05T04:30:00Z", "CANCELLED"),
                    visit(3, "2026-11-05T04:10:00Z", "2026-11-05T04:40:00Z"),
                    visit(4, "2026-11-05T05:00:00Z", "2026-11-05T05:30:00Z"),
                    visit(5, "2026-11-05T06:00:00Z", "2026-11-05T06:30:00Z", name = null),
                ).joinToString(",") + "]"
            val s = classToTest.schedule(clock()).getOrThrow()
            assertThat(s.today.map { it.appointmentId }).containsExactly(1L, 2L, 3L, 4L).inOrder()
            assertThat(s.hero?.appointmentId).isEqualTo(3L)
            assertThat(s.heroNow).isTrue()
            assertThat(s.heroDay).isNull()
            // Later the same day, the next one is the hero, not yet "now".
            val later = classToTest.schedule(clock("2026-11-05T04:45:00Z")).getOrThrow()
            assertThat(later.hero?.appointmentId).isEqualTo(4L)
            assertThat(later.heroNow).isFalse()
        }

    @Test
    fun `nothing left today - the first open visit in the next 14 days, with its day`() =
        runTest {
            days["2026-11-05"] = "[" + visit(1, "2026-11-05T03:00:00Z", "2026-11-05T03:30:00Z", "COMPLETED") + "]"
            days["2026-11-07"] = "[" + visit(7, "2026-11-07T05:00:00Z", "2026-11-07T05:30:00Z", "NO_SHOW") + "]"
            days["2026-11-08"] = "fail"
            days["2026-11-11"] = "[" + visit(11, "2026-11-11T05:00:00Z", "2026-11-11T05:30:00Z") + "]"
            days["2026-11-12"] = "[" + visit(12, "2026-11-12T05:00:00Z", "2026-11-12T05:30:00Z") + "]"
            val s = classToTest.schedule(clock()).getOrThrow()
            assertThat(s.hero?.appointmentId).isEqualTo(11L)
            assertThat(s.heroDay).isEqualTo(LocalDate(2026, 11, 11))
            assertThat(s.today).hasSize(1)
            // Read four days at a time, stopping at the batch with the hero.
            assertThat(asked).doesNotContain("2026-11-14")
        }

    @Test
    fun `no visit in 14 days is an empty hero, and a failed today is a failure`() =
        runTest {
            assertThat(classToTest.schedule(clock()).getOrThrow().hero).isNull()
            assertThat(asked).contains("2026-11-19")
            assertThat(asked).doesNotContain("2026-11-20")
            days["2026-11-05"] = "fail"
            assertThat(classToTest.schedule(clock(), force = true).isFailure).isTrue()
        }

    @Test
    fun `the in-slot visit beats an earlier one still in progress, which beats the next, and signed out is empty`() =
        runTest {
            days["2026-11-05"] =
                "[" +
                listOf(
                    visit(1, "2026-11-05T03:00:00Z", "2026-11-05T03:30:00Z", "IN_PROGRESS"),
                    visit(2, "2026-11-05T04:00:00Z", "2026-11-05T04:30:00Z"),
                    visit(3, "2026-11-05T05:00:00Z", "2026-11-05T05:30:00Z"),
                ).joinToString(",") + "]"
            assertThat(classToTest.schedule(clock()).getOrThrow().hero?.appointmentId).isEqualTo(2L)
            val between = classToTest.schedule(clock("2026-11-05T04:40:00Z")).getOrThrow()
            assertThat(between.hero?.appointmentId).isEqualTo(1L)
            assertThat(between.heroNow).isTrue()
            signedIn = false
            asked.clear()
            assertThat(classToTest.schedule(clock(), force = true).getOrThrow()).isEqualTo(HomeSchedule())
            assertThat(asked).isEmpty()
        }

    @Test
    fun `the tab's refresh is skipped while one runs and within a minute, pull-to-refresh always runs`() =
        runTest {
            val time = TestTimeSource()
            val gate = RefreshGate(60.seconds, time)
            var inner: Boolean? = null
            assertThat(gate.run { inner = gate.run { } }).isTrue()
            assertThat(inner).isFalse()
            time += 30.seconds
            assertThat(gate.run { }).isFalse()
            assertThat(gate.run(force = true) { }).isTrue()
            time += 61.seconds
            assertThat(gate.run { }).isTrue()
        }

    private fun free(
        start: String,
        end: String,
    ) = """{"type":"FREE","startAt":"$start","endAt":"$end","locationId":7}"""

    @Test
    fun `the video chip's slot is the first not yet begun, today first, then the following days`() =
        runTest {
            // 09:12: the 09:00 slot has begun, so today's 10:00 one is first.
            days["2026-11-05"] = "[" +
                free(
                    "2026-11-05T04:00:00Z",
                    "2026-11-05T04:30:00Z",
                ) + "," + free("2026-11-05T05:00:00Z", "2026-11-05T05:30:00Z") + "]"
            assertThat(
                classToTest.firstFreeSlot(clock()).getOrThrow()?.start,
            ).isEqualTo(Instant.parse("2026-11-05T05:00:00Z"))
            // Nothing left today: the first in the days after, skipping a day that fails.
            days["2026-11-05"] = "[" + free("2026-11-05T04:00:00Z", "2026-11-05T04:30:00Z") + "]"
            days["2026-11-06"] = "fail"
            days["2026-11-08"] = "[" + free("2026-11-08T07:00:00Z", "2026-11-08T07:30:00Z") + "]"
            val repo =
                HomeRepository(
                    CalendarRepository({ createHttpClient({ null }, engine) }, TestTimeSource(), 60.seconds),
                ) { true }
            assertThat(repo.firstFreeSlot(clock()).getOrThrow()?.start).isEqualTo(Instant.parse("2026-11-08T07:00:00Z"))
            // None within 30 days: null (the chip says "No slots available"); today unreadable: a failure.
            days.clear()
            val empty =
                HomeRepository(
                    CalendarRepository({ createHttpClient({ null }, engine) }, TestTimeSource(), 60.seconds),
                ) { true }
            assertThat(empty.firstFreeSlot(clock()).getOrThrow()).isNull()
            days["2026-11-05"] = "fail"
            val failing =
                HomeRepository(
                    CalendarRepository({ createHttpClient({ null }, engine) }, TestTimeSource(), 60.seconds),
                ) { true }
            assertThat(failing.firstFreeSlot(clock()).isFailure).isTrue()
        }

    @Test
    fun `today's blocks and practice day come with the schedule`() =
        runTest {
            days["2026-11-05"] =
                "[" + visit(1, "2026-11-05T05:00:00Z", "2026-11-05T05:30:00Z") +
                """,{"type":"BLOCKED","startAt":"2026-11-05T07:00:00Z","endAt":"2026-11-05T08:00:00Z","blockId":9,"blockReason":"Lunch"}]"""
            val s = classToTest.schedule(clock()).getOrThrow()
            assertThat(s.blocks.map { it.blockId }).containsExactly(9L)
            assertThat(s.day).isEqualTo(LocalDate(2026, 11, 5))
        }
}
