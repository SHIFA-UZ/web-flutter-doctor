@file:Suppress("ktlint:standard:max-line-length") // JSON fixtures

package shifa.doctor.data.calendar

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.Test
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.model.CalendarEntry
import shifa.doctor.data.model.DayOccupancy
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class CalendarRepositoryTest {
    // Shapes from dev 2026-10-30 (shortened): a free slot, a video appointment, a block, an unknown type, a broken row.
    private val day =
        """[
        {"type":"FREE_SLOT","startAt":"2026-10-30T04:00:00Z","endAt":"2026-10-30T04:30:00Z","locationId":2,"locationLabel":"Shifa Dev Clinic","attachmentCount":0,"signatureRequested":false},
        {"type":"APPOINTMENT","startAt":"2026-10-30T07:00:00Z","endAt":"2026-10-30T07:30:00Z","appointmentId":7,"patientId":15,"patientName":"Gulnora Yusupova","patientPhotoUrl":null,"location":"Video Consultation","reason":"Seed: confirmed","status":"CONFIRMED","paymentStatus":"PAID","signatureRequested":false,"locationId":null,"briefingStatus":null,"attachmentCount":2},
        {"type":"BLOCKED","startAt":"2026-10-30T08:00:00Z","endAt":"2026-10-30T09:00:00Z","blockId":3,"blockReason":"Lunch"},
        {"type":"HOLD","startAt":"2026-10-30T06:00:00Z","endAt":"2026-10-30T06:30:00Z"},
        {"type":"APPOINTMENT","startAt":"not a time","endAt":"2026-10-30T07:30:00Z","appointmentId":8}
        ]"""
    private val requests = java.util.concurrent.CopyOnWriteArrayList<String>()
    private var body = day
    private val engine =
        MockEngine { req ->
            requests += req.url.encodedQuery
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
    private val time = TestTimeSource()
    private val classToTest = CalendarRepository({ createHttpClient({ null }, engine) }, time, 60.seconds)
    private val oct30 = DayKey(LocalDate(2026, 10, 30))

    @Test
    fun `a day reads every row on its own, sorted, unknown types as free slots`() =
        runBlocking {
            val entries = classToTest.day(oct30).getOrThrow()
            assertThat(
                entries.map {
                    it::class.simpleName
                },
            ).containsExactly("FreeSlot", "FreeSlot", "Appointment", "Blocked").inOrder()
            val appt = entries[2] as CalendarEntry.Appointment
            assertThat(appt.isVideo).isTrue()
            assertThat(appt.attachmentCount).isEqualTo(2)
            assertThat(appt.start).isEqualTo(Instant.parse("2026-10-30T07:00:00Z"))
            assertThat((entries[3] as CalendarEntry.Blocked).reason).isEqualTo("Lunch")
            assertThat(requests.single()).isEqualTo("day=2026-10-30")
            assertThat(DayOccupancy.of(entries)).isEqualTo(DayOccupancy(2, 1, 1))
            assertThat(DayOccupancy.of(entries).freeRatio).isEqualTo(0.5f)
            assertThat(DayOccupancy(0, 0, 3).freeRatio).isEqualTo(0f)
        }

    @Test
    fun `a wrapped answer and a colleague's day both read`() =
        runBlocking {
            body = """{"content":$day}"""
            assertThat(classToTest.previewDay(oct30.date, 42).getOrThrow()).hasSize(4)
            assertThat(requests.single()).isEqualTo("day=2026-10-30&doctorId=42")
            assertThat(classToTest.state.value.days).isEmpty() // a preview isn't cached
        }

    @Test
    fun `a day is read again only after 60 s, or when forced or invalidated`() =
        runBlocking {
            classToTest.day(oct30)
            classToTest.day(oct30)
            assertThat(requests).hasSize(1)
            time += 61.seconds
            classToTest.day(oct30)
            assertThat(requests).hasSize(2)
            classToTest.day(oct30, force = true)
            classToTest.invalidate(oct30)
            classToTest.day(oct30)
            assertThat(requests).hasSize(4)
        }

    @Test
    fun `a month fetches only the days it hasn't got, and sign-out empties the cache`() =
        runBlocking {
            classToTest.day(oct30)
            val dates = (1..6).map { LocalDate(2026, 10, 25 + it) }
            classToTest.month(dates)
            assertThat(requests).hasSize(6) // 30 was cached: 26–29 and 31 are new
            assertThat(classToTest.state.value.days).hasSize(6)
            classToTest.clear()
            assertThat(classToTest.state.value.days).isEmpty()
        }

    @Test
    fun `an older answer never overwrites a forced one, and nothing lands after sign-out`() =
        runBlocking {
            val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
            var calls = 0
            val slowFirst =
                MockEngine { _ ->
                    val n = ++calls
                    if (n == 1) gate.await() // the first (older) request answers last
                    respond(
                        if (n == 1) "[]" else day,
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val repo = CalendarRepository({ createHttpClient({ null }, slowFirst) }, time, 60.seconds)
            coroutineScope {
                val older = async { repo.day(oct30) }
                yield()
                repo.day(oct30, force = true)
                gate.complete(Unit)
                older.await()
            }
            assertThat(repo.state.value.days[oct30]).hasSize(4) // the forced answer, not the older empty one
            val gate2 = kotlinx.coroutines.CompletableDeferred<Unit>()
            val late =
                CalendarRepository(
                    {
                        createHttpClient(
                            { null },
                            MockEngine {
                                gate2.await()
                                respond(day, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                            },
                        )
                    },
                    time,
                )
            coroutineScope {
                val read = async { late.day(oct30) }
                yield()
                late.clear()
                gate2.complete(Unit)
                read.await()
            }
            assertThat(late.state.value.days).isEmpty()
        }

    @Test
    fun `a read after clear never joins the one clear dropped, it asks again and caches`() =
        runBlocking {
            val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
            var calls = 0
            val repo =
                CalendarRepository(
                    {
                        createHttpClient(
                            { null },
                            MockEngine {
                                if (++calls == 1) gate.await()
                                respond(day, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                            },
                        )
                    },
                    time,
                )
            coroutineScope {
                val dropped = async { repo.day(oct30) }
                yield()
                repo.clear()
                // Not forced: before the fix it joined the dropped read and returned without caching.
                repo.day(oct30).getOrThrow()
                gate.complete(Unit)
                dropped.await()
            }
            assertThat(calls).isEqualTo(2)
            assertThat(repo.state.value.days[oct30]).hasSize(4)
        }

    @Test
    fun `reads of the same day share one request, the location is part of the key`() =
        runBlocking {
            coroutineScope { (1..3).map { async { classToTest.day(oct30) } }.awaitAll() }
            assertThat(requests).hasSize(1)
            classToTest.day(oct30.copy(locationId = 2))
            assertThat(requests.last()).isEqualTo("day=2026-10-30&locationId=2")
            classToTest.invalidate(oct30)
            classToTest.month(listOf(oct30.date))
            assertThat(requests).hasSize(3) // invalidated, so the month read it again
        }
}
