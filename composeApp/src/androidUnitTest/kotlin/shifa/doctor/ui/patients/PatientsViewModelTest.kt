@file:Suppress("ktlint:standard:max-line-length") // JSON fixtures

package shifa.doctor.ui.patients

import com.google.common.truth.Truth.assertThat
import com.russhwolf.settings.MapSettings
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Test
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.model.PatientStatus
import shifa.doctor.data.patients.FavoritesRepository
import shifa.doctor.data.patients.PatientsRepository
import shifa.doctor.data.storage.FavoritesStorage
import shifa.doctor.data.tracking.RecordingTracker
import shifa.doctor.data.tracking.events.PatientsEvents
import shifa.doctor.stopAll
import shifa.doctor.utils.PracticeClock

@OptIn(ExperimentalCoroutinesApi::class)
class PatientsViewModelTest {
    private val scheduler = TestCoroutineScheduler()
    private var reads = 0
    private var fail = false

    // 23 patients: "Patient 01" … "Patient 23"; 7 is at risk, 8 follow-up, 9 has an unknown status; 5 has a phone.
    private val rows =
        (1..23).joinToString(",", "[", "]") { i ->
            val status = mapOf(7 to "AT_RISK", 8 to "FOLLOW_UP", 9 to "ARCHIVED")[i] ?: "ACTIVE"
            val phone = if (i == 5) ""","phones":["+998 (90) 123-45-67"]""" else ""
            """{"id":$i,"name":"Patient ${i.toString().padStart(2, '0')}","clinicalStatus":"$status"$phone}"""
        }
    private val engine =
        MockEngine {
            reads++
            if (fail) {
                respond(
                    """{"status":500,"code":"INTERNAL","requestId":"req-p"}""",
                    HttpStatusCode.InternalServerError,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            } else {
                respond(rows, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
    private val settings = MapSettings()
    private val repo = PatientsRepository(client = { createHttpClient({ null }, engine) })
    private val created = mutableListOf<PatientsViewModel>()
    private val clock =
        PracticeClock("Asia/Tashkent", { Instant.parse("2026-11-04T03:00:00Z") }, { TimeZone.of("Asia/Tashkent") })

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))

    @After fun tearDown() {
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
        PatientsViewModel(
            repo,
            FavoritesRepository(FavoritesStorage(settings)),
            flowOf(42L to "Asia/Tashkent"),
            { clock },
        ).also {
            created += it
            it.load()
            settle { it.state.value.patients != null || it.state.value.error != null }
        }

    private fun typed(
        vm: PatientsViewModel,
        text: String,
    ) {
        vm.type(text)
        scheduler.advanceTimeBy(301)
        scheduler.runCurrent()
    }

    @Test
    fun `ten per page, the page resets when the view changes, sort either way`() {
        val classToTest = opened()
        val s = classToTest.state.value
        assertThat(s.pages).isEqualTo(3)
        assertThat(s.shown.first().name).isEqualTo("Patient 01")
        classToTest.page(2)
        assertThat(classToTest.state.value.shown).hasSize(3)
        classToTest.filter { it.copy(descending = true) }
        assertThat(classToTest.state.value.page).isEqualTo(0)
        assertThat(classToTest.state.value.shown.first().name).isEqualTo("Patient 23")
    }

    @Test
    fun `search waits for typing to stop and matches name, display id and phone digits`() {
        val classToTest = opened()
        classToTest.type("patient 1")
        assertThat(classToTest.state.value.visible).hasSize(23) // not yet
        scheduler.advanceTimeBy(301)
        scheduler.runCurrent()
        assertThat(
            classToTest.state.value.visible.map {
                it.id
            },
        ).containsExactly(10L, 11L, 12L, 13L, 14L, 15L, 16L, 17L, 18L, 19L)
        typed(classToTest, "P-00007")
        assertThat(classToTest.state.value.visible.single().id).isEqualTo(7)
        typed(classToTest, "90 123") // stored "+998 (90) 123-45-67"
        assertThat(classToTest.state.value.visible.single().id).isEqualTo(5)
    }

    @Test
    fun `follow-ups, a status filter where unknown counts as active, and clear filters`() {
        val classToTest = opened()
        classToTest.filter { it.copy(tab = PatientTab.FollowUps) }
        assertThat(classToTest.state.value.visible.single().id).isEqualTo(8)
        classToTest.filter { it.copy(tab = PatientTab.All, status = PatientStatus.Active) }
        assertThat(classToTest.state.value.visible.map { it.id }).contains(9L)
        assertThat(classToTest.state.value.visible.map { it.id }).doesNotContain(7L)
        classToTest.clearFilters()
        assertThat(classToTest.state.value.visible).hasSize(23)
    }

    @Test
    fun `favourites survive a new screen, the directory isn't read again while fresh`() {
        val classToTest = opened()
        classToTest.toggleFavorite(3)
        classToTest.toggleFavorite(4)
        classToTest.toggleFavorite(3)
        val again = opened()
        assertThat(again.state.value.favorites).containsExactly(4L)
        again.filter { it.copy(tab = PatientTab.Favorites) }
        assertThat(again.state.value.visible.single().id).isEqualTo(4)
        assertThat(reads).isEqualTo(1) // the second screen used the fresh directory
    }

    @Test
    fun `a failed first read says so with its request id`() {
        fail = true
        val classToTest = opened()
        assertThat(classToTest.state.value.patients).isNull()
        assertThat(classToTest.state.value.error?.requestId).isEqualTo("req-p")
    }

    @Test
    fun `a shrinking list keeps the page in range, opening a sheet keeps the page, words don't match phones`() {
        val classToTest = opened()
        classToTest.page(2)
        classToTest.ui { it.copy(choosingStatus = true) }
        assertThat(classToTest.state.value.page).isEqualTo(2)
        classToTest.ui { it.copy(patients = it.patients!!.take(15)) } // as after a refresh
        assertThat(classToTest.state.value.currentPage).isEqualTo(1)
        assertThat(classToTest.state.value.shown).hasSize(5)
        typed(classToTest, "Alice 90") // no Alice: the digits alone must not match the phone
        assertThat(classToTest.state.value.visible).isEmpty()
    }

    @Test
    fun `search is tracked by its result count and filters by the choice, never the text`() {
        val tracker = RecordingTracker()
        val classToTest =
            PatientsViewModel(
                repo,
                FavoritesRepository(FavoritesStorage(settings)),
                flowOf(42L to "Asia/Tashkent"),
                { clock },
                tracker,
            ).also { created += it }
        classToTest.load()
        settle { classToTest.state.value.patients != null }
        typed(classToTest, "patient 1")
        typed(classToTest, "")
        classToTest.filter { it.copy(tab = PatientTab.FollowUps) }
        classToTest.filter { it.copy(tab = PatientTab.FollowUps) }
        assertThat(tracker.events)
            .containsExactly(
                PatientsEvents.PatientsSearched("6-20"),
                PatientsEvents.PatientsFiltered("follow_ups", null, false),
            ).inOrder()
    }

    @Test
    fun `a search before the directory loaded isn't tracked as no results`() {
        val tracker = RecordingTracker()
        val classToTest =
            PatientsViewModel(
                repo,
                FavoritesRepository(FavoritesStorage(settings)),
                flowOf(42L to "Asia/Tashkent"),
                { clock },
                tracker,
            ).also { created += it }
        typed(classToTest, "patient 1")
        assertThat(classToTest.state.value.patients).isNull()
        assertThat(tracker.events).isEmpty()
    }
}
