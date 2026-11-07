@file:Suppress("ktlint:standard:max-line-length") // JSON fixtures

package shifa.doctor.ui.schedule

import com.google.common.truth.Truth.assertThat
import com.russhwolf.settings.MapSettings
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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Test
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.calendar.CalendarRepository
import shifa.doctor.data.locations.LocationsRepository
import shifa.doctor.data.schedule.RangeProblem
import shifa.doctor.data.schedule.ScheduleRepository
import shifa.doctor.data.schedule.TimeRange
import shifa.doctor.data.storage.ActiveLocationStorage
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.scheduleSaved
import shifa.doctor.generated.resources.scheduleSavedPeriodFailed
import shifa.doctor.stopAll
import shifa.doctor.ui.navigation.ShellNotice
import shifa.doctor.utils.PracticeClock

// Practice zone Tashkent, today 4 November 2026. Locations 2 (primary) and 1; location 2 works Monday 09–13.
@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleViewModelTest {
    private val clock =
        PracticeClock("Asia/Tashkent", { Instant.parse("2026-11-04T03:00:00Z") }, { TimeZone.of("Asia/Tashkent") })
    private val writes = java.util.concurrent.CopyOnWriteArrayList<String>()
    private var rulesFail = false
    private var periodFails = false
    private var datesFail = false
    private var periodsJson = """[{"validFrom":"2026-09-30","validUntil":"2026-12-31"}]"""
    private var hold1 = CompletableDeferred(Unit)
    private var holdPut = CompletableDeferred(Unit)
    private val store = ActiveLocationStorage(MapSettings())
    private val engine =
        MockEngine { req ->
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            val path = req.url.encodedPath
            if (req.method != HttpMethod.Get) {
                if (req.method == HttpMethod.Put) holdPut.await()
                writes += "${req.method.value} $path?${req.url.encodedQuery} ${req.body.toByteArray().decodeToString()}"
                return@MockEngine when {
                    path.endsWith(
                        "valid-range",
                    ) && periodFails ->
                        respond(
                            """{"status":400,"code":"BAD_REQUEST","requestId":"req-p"}""",
                            HttpStatusCode.BadRequest,
                            json,
                        )
                    path.endsWith(
                        "date-specific",
                    ) ->
                        respond(
                            """{"id":7,"startDate":"2026-12-27","endDate":"2026-12-27","startTime":"10:00","endTime":"14:00","slotMinutes":30,"locationId":2}""",
                            HttpStatusCode.OK,
                            json,
                        )
                    else -> respond("[]", HttpStatusCode.OK, json)
                }
            }
            when {
                path.endsWith(
                    "locations",
                ) ->
                    respond(
                        """[{"id":2,"label":"Primary","isPrimary":true},{"id":1,"label":"Main","isPrimary":false}]""",
                        HttpStatusCode.OK,
                        json,
                    )
                path.endsWith("date-specific") && datesFail -> respond("", HttpStatusCode.ServiceUnavailable)
                path.endsWith(
                    "rules",
                ) && rulesFail ->
                    respond(
                        """{"status":500,"code":"INTERNAL","requestId":"req-r"}""",
                        HttpStatusCode.InternalServerError,
                        json,
                    )
                path.endsWith("rules") -> {
                    if (req.url.parameters["locationId"] == "1") hold1.await()
                    val two = req.url.parameters["locationId"] == "2"
                    respond(
                        if (two) """[{"weekday":1,"startTime":"09:00","endTime":"13:00","slotMinutes":30,"locationId":2}]""" else "[]",
                        HttpStatusCode.OK,
                        json,
                    )
                }
                path.endsWith("validity-periods") -> respond(periodsJson, HttpStatusCode.OK, json)
                else -> respond("[]", HttpStatusCode.OK, json)
            }
        }
    private val created = mutableListOf<ScheduleViewModel>()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

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

    private fun opened(): ScheduleViewModel {
        val client = { createHttpClient({ null }, engine) }
        val vm =
            ScheduleViewModel(
                ScheduleRepository(
                    client,
                ),
                LocationsRepository(client),
                CalendarRepository(client),
                clock,
                store,
                {
                    99L
                },
            )
        created += vm
        vm.load()
        settle { vm.state.value.saved != null || vm.state.value.loadError != null }
        return vm
    }

    private fun t(s: String) = LocalTime.parse(s)

    @Test
    fun `the primary location is read first, and Save stays off until its rules are in`() {
        rulesFail = true
        val failed = opened()
        assertThat(failed.state.value.locationId).isEqualTo(2)
        assertThat(failed.state.value.loadError?.requestId).isEqualTo("req-r")
        failed.save()
        assertThat(writes).isEmpty() // a failed read never saves an empty template
        rulesFail = false
        val classToTest = opened()
        assertThat(classToTest.state.value.week.ranges(1).single()).isEqualTo(TimeRange(t("09:00"), t("13:00"), 30))
        assertThat(classToTest.state.value.canSave).isFalse() // nothing changed yet
    }

    @Test
    fun `a day takes ranges checked like the backend, copies and spreads to Mon-Fri`() {
        val classToTest = opened()
        classToTest.openDay(1)
        classToTest.ui { it.copy(draft = RangeDraft(12 * 60, 15 * 60, 30)) }
        classToTest.addRange()
        assertThat(classToTest.state.value.problem).isEqualTo(RangeProblem.Overlaps)
        classToTest.ui { it.copy(draft = RangeDraft(14 * 60, 18 * 60, 30)) }
        classToTest.addRange()
        assertThat(classToTest.state.value.week.ranges(1)).hasSize(2)
        classToTest.applyToWeekdays()
        assertThat((1..5).map { classToTest.state.value.week.ranges(it).size }).containsExactly(2, 2, 2, 2, 2)
        classToTest.openDay(6)
        classToTest.copyFrom(1)
        assertThat(classToTest.state.value.week.ranges(6)).hasSize(2)
        assertThat(classToTest.state.value.dirty).isTrue()
    }

    @Test
    fun `save replaces the location's rules, adds the period after the last one, and closes`() {
        val classToTest = opened()
        classToTest.openDay(2)
        classToTest.addRange()
        classToTest.startPeriod()
        assertThat(classToTest.state.value.newPeriod).isEqualTo(LocalDate(2027, 1, 1) to LocalDate(2027, 3, 2))
        classToTest.save()
        settle { classToTest.state.value.closed }
        assertThat(writes[0]).startsWith("""PUT /api/schedule/rules?locationId=2 [{"weekday":1""")
        assertThat(
            writes[0],
        ).contains(""""weekday":2,"startTime":"09:00","endTime":"17:00","slotMinutes":30,"locationId":2""")
        assertThat(
            writes[1],
        ).isEqualTo("""PATCH /api/schedule/valid-range? {"validFrom":"2027-01-01","validUntil":"2027-03-02"}""")
        assertThat(ShellNotice.pending.value?.text).isEqualTo(Res.string.scheduleSaved)
    }

    @Test
    fun `a failed period after saved hours says so and keeps the hours saved`() {
        val classToTest = opened()
        classToTest.openDay(2)
        classToTest.addRange()
        classToTest.startPeriod()
        periodFails = true
        classToTest.save()
        settle { classToTest.state.value.banner != null }
        val s = classToTest.state.value
        assertThat(s.banner?.text).isEqualTo(Res.string.scheduleSavedPeriodFailed)
        assertThat(s.saved).isEqualTo(s.week) // the hours went through
        assertThat(s.closed).isFalse()
    }

    @Test
    fun `switching location with edits asks first, a period across the existing one is refused`() {
        val classToTest = opened()
        classToTest.openDay(3)
        classToTest.addRange()
        classToTest.selectLocation(1)
        assertThat(classToTest.state.value.askDiscard).isTrue()
        classToTest.discard()
        settle { classToTest.state.value.locationId == 1L && classToTest.state.value.saved != null }
        assertThat(classToTest.state.value.week.days).isEmpty()

        classToTest.ui { it.copy(newFrom = LocalDate(2026, 12, 1), newUntil = LocalDate(2027, 2, 1)) }
        assertThat(classToTest.state.value.periodOk).isFalse()
        assertThat(classToTest.state.value.canSave).isFalse()
    }

    @Test
    fun `extra hours on dates are written at once`() {
        val classToTest = opened()
        classToTest.openDateHours()
        classToTest.editDateHours {
            it.copy(
                from = LocalDate(2026, 12, 27),
                to = LocalDate(2026, 12, 27),
                hours = RangeDraft(10 * 60, 14 * 60, 30),
            )
        }
        classToTest.addDateHours()
        settle { classToTest.state.value.dates.isNotEmpty() }
        assertThat(writes.single()).isEqualTo(
            """POST /api/schedule/date-specific? {"startDate":"2026-12-27","endDate":"2026-12-27","startTime":"10:00","endTime":"14:00","slotMinutes":30,"locationId":2}""",
        )
        assertThat(classToTest.state.value.dateDraft).isNull()
        classToTest.deleteDateHours(7)
        settle { classToTest.state.value.dates.isEmpty() }
        assertThat(writes.last()).startsWith("DELETE /api/schedule/date-specific/7")
    }

    @Test
    fun `an older read can't fill the form after a newer one - A to B to A`() {
        val classToTest = opened() // location 2
        hold1 = CompletableDeferred()
        classToTest.selectLocation(1) // its rules are held
        settle { classToTest.state.value.locationId == 1L }
        classToTest.selectLocation(2)
        settle { classToTest.state.value.locationId == 2L && classToTest.state.value.saved != null }
        hold1.complete(Unit)
        Thread.sleep(100)
        assertThat(classToTest.state.value.locationId).isEqualTo(2)
        assertThat(
            classToTest.state.value.week.ranges(1),
        ).hasSize(1) // location 2's Monday, not location 1's empty week
    }

    @Test
    fun `nothing can be edited while saving`() {
        val classToTest = opened()
        classToTest.openDay(2)
        classToTest.addRange()
        holdPut = CompletableDeferred()
        classToTest.save()
        settle { classToTest.state.value.saving }
        val sent = classToTest.state.value.week
        classToTest.openDay(3)
        classToTest.addRange()
        assertThat(classToTest.state.value.week).isEqualTo(sent)
        holdPut.complete(Unit)
        settle { classToTest.state.value.closed }
    }

    @Test
    fun `a first setup without any period adds 60 days from today`() {
        periodsJson = "[]"
        val classToTest = opened()
        classToTest.openDay(2)
        classToTest.addRange()
        classToTest.save()
        settle { classToTest.state.value.closed }
        assertThat(
            writes.last(),
        ).isEqualTo("""PATCH /api/schedule/valid-range? {"validFrom":"2026-11-04","validUntil":"2027-01-03"}""")
    }

    @Test
    fun `a failed first period stays pending so Save can try again`() {
        periodsJson = "[]"
        periodFails = true
        val classToTest = opened()
        classToTest.openDay(2)
        classToTest.addRange()
        classToTest.save()
        settle { classToTest.state.value.banner != null }
        assertThat(classToTest.state.value.newPeriod).isEqualTo(LocalDate(2026, 11, 4) to LocalDate(2027, 1, 3))
        assertThat(classToTest.state.value.canSave).isTrue()
    }

    @Test
    fun `a new period never ends more than a year ahead, and the location is remembered`() {
        periodsJson = """[{"validFrom":"2026-09-30","validUntil":"2027-10-30"}]"""
        val classToTest = opened()
        classToTest.startPeriod()
        assertThat(classToTest.state.value.newPeriod).isEqualTo(LocalDate(2027, 10, 31) to LocalDate(2027, 11, 4))
        assertThat(classToTest.state.value.periodOk).isTrue()
        classToTest.ui { it.copy(newUntil = LocalDate(2027, 11, 5)) }
        assertThat(classToTest.state.value.periodOk).isFalse()

        classToTest.ui { it.copy(newFrom = null, newUntil = null) }
        classToTest.selectLocation(1)
        settle { classToTest.state.value.locationId == 1L && classToTest.state.value.saved != null }
        assertThat(opened().state.value.locationId).isEqualTo(1) // a new screen opens where the doctor left off
    }

    @Test
    fun `extra hours that can't be read block the screen with Retry instead of looking empty`() {
        datesFail = true
        val classToTest = opened()
        assertThat(classToTest.state.value.loadError).isNotNull()
        assertThat(classToTest.state.value.saved).isNull()
    }
}
