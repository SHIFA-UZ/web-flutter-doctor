@file:Suppress("ktlint:standard:max-line-length") // JSON fixtures

package shifa.doctor.data.calendar

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Test
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.utils.PracticeClock

class CalendarSelectorsTest {
    // Practice zone Tashkent (UTC+5); now is 2026-11-04 10:15 there (05:15 UTC).
    private val now = Instant.parse("2026-11-04T05:15:00Z")
    private val clock = PracticeClock("Asia/Tashkent", { now }, { TimeZone.of("Asia/Tashkent") })

    private fun appt(
        id: Int,
        start: String,
        end: String,
        status: String = "CONFIRMED",
    ) =
        """{"type":"APPOINTMENT","startAt":"$start","endAt":"$end","appointmentId":$id,"patientName":"P$id","status":"$status"}"""

    // Nov 4: one done, one cancelled, one in progress (05:00–05:30 UTC), one later; Nov 5–6: nothing; Nov 7: one.
    private val days =
        mapOf(
            "2026-11-04" to "[${appt(
                1,
                "2026-11-04T04:00:00Z",
                "2026-11-04T04:30:00Z",
                "COMPLETED",
            )},${appt(
                2,
                "2026-11-04T04:30:00Z",
                "2026-11-04T05:00:00Z",
                "CANCELLED",
            )},${appt(
                3,
                "2026-11-04T05:00:00Z",
                "2026-11-04T05:30:00Z",
            )},${appt(4, "2026-11-04T09:00:00Z", "2026-11-04T09:30:00Z")}]",
            "2026-11-07" to "[${appt(9, "2026-11-07T04:00:00Z", "2026-11-07T04:30:00Z")}]",
        )
    private val requested = java.util.concurrent.CopyOnWriteArrayList<String>()
    private var failDay: String? = null
    private val engine =
        MockEngine { req ->
            val day = req.url.parameters["day"]!!
            requested += day
            if (day == failDay) {
                respond(
                    """{"status":500,"code":"INTERNAL"}""",
                    HttpStatusCode.InternalServerError,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            } else {
                respond(days[day] ?: "[]", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
    private val repo = CalendarRepository({ createHttpClient({ null }, engine) })

    @Test
    fun `today's appointments leave out cancelled ones`() =
        runBlocking {
            assertThat(
                repo.todayAppointments(clock).getOrThrow().map { it.appointmentId },
            ).containsExactly(1L, 3L, 4L).inOrder()
        }

    @Test
    fun `next is the one in progress, then the next today, then the first in the next days`() =
        runBlocking {
            assertThat(repo.nextAppointment(clock).getOrThrow()?.appointmentId).isEqualTo(3)
            val later = PracticeClock("Asia/Tashkent", { Instant.parse("2026-11-04T06:00:00Z") })
            assertThat(repo.nextAppointment(later).getOrThrow()?.appointmentId).isEqualTo(4)
            val evening = PracticeClock("Asia/Tashkent", { Instant.parse("2026-11-04T15:00:00Z") })
            assertThat(repo.nextAppointment(evening).getOrThrow()?.appointmentId).isEqualTo(9)
            // Days already read came from cache: Nov 4 once, Nov 5–7 once each.
            assertThat(requested).containsExactly("2026-11-04", "2026-11-05", "2026-11-06", "2026-11-07").inOrder()
        }

    @Test
    fun `nothing within the horizon is null`() =
        runBlocking {
            val farAfter = PracticeClock("Asia/Tashkent", { Instant.parse("2026-11-08T00:00:00Z") })
            assertThat(repo.nextAppointment(farAfter, horizonDays = 3).getOrThrow()).isNull()
        }

    @Test
    fun `a change reads today and the touched day once each and tells everyone`() =
        runBlocking {
            repo.day(DayKey(LocalDate(2026, 11, 4)))
            requested.clear()
            coroutineScope {
                val event = async { AppointmentChanges.events.first() }
                yield()
                repo.changed(clock, LocalDate(2026, 11, 7), LocalDate(2026, 11, 4))
                assertThat(event.await()).containsExactly(LocalDate(2026, 11, 4), LocalDate(2026, 11, 7))
            }
            // Once each; the reads of one batch run together, so their order isn't fixed.
            assertThat(requested).containsExactly("2026-11-07", "2026-11-04")
            Unit
        }

    @Test
    fun `a day that can't be read before the hit is a failure, not a later appointment`() =
        runBlocking {
            failDay = "2026-11-05"
            val evening = PracticeClock("Asia/Tashkent", { Instant.parse("2026-11-04T15:00:00Z") })
            assertThat(repo.nextAppointment(evening).isFailure).isTrue()
        }

    @Test
    fun `a change also marks other locations' copies of those days stale`() =
        runBlocking {
            val other = DayKey(LocalDate(2026, 11, 7), locationId = 5)
            repo.day(other)
            requested.clear()
            repo.changed(clock, LocalDate(2026, 11, 7))
            repo.day(other)
            assertThat(requested.count { it == "2026-11-07" }).isEqualTo(2) // own refresh + the stale location copy
        }
}
