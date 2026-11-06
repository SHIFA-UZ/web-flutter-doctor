@file:Suppress("ktlint:standard:max-line-length")

package shifa.doctor.ui.care

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.care.CarePartnershipsRepository
import shifa.doctor.data.care.PartnershipAction
import shifa.doctor.data.model.PartnershipStatus
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.careAccepted
import shifa.doctor.generated.resources.careInviteExists
import shifa.doctor.generated.resources.carePostFailed
import shifa.doctor.stopAll
import java.util.Collections

@OptIn(ExperimentalCoroutinesApi::class)
class CareViewModelsTest {
    private val calls = Collections.synchronizedList(mutableListOf<String>())
    private var inviteConflict = false
    private var progressFails = false
    private var postFails = false
    private var slowProgress = false

    private fun p(
        id: Long,
        status: String,
        patient: Long,
        initiator: Long,
        partner: Long,
        updated: String,
        role: String? = null,
    ) =
        """{"id":$id,"status":"$status","patientId":$patient,"patientName":"P$patient","initiatingDoctorId":$initiator,"initiatingDoctorName":"D$initiator","partnerDoctorId":$partner,"partnerDoctorName":"D$partner","createdAt":"2026-10-01T00:00:00Z","updatedAt":"$updated"${role?.let { ",\"viewerRole\":\"$it\"" } ?: ""}}"""

    private val mine =
        listOf(
            p(1, "ACTIVE", 12, 9, 31, "2026-11-03T00:00:00Z"),
            p(2, "PENDING", 12, 32, 9, "2026-11-01T00:00:00Z"),
            p(3, "PENDING", 13, 9, 33, "2026-11-04T00:00:00Z"),
            p(4, "COMPLETED", 12, 9, 34, "2026-11-05T00:00:00Z"),
            // The server says which side: it wins over the ids.
            p(5, "PENDING", 14, 9, 35, "2026-11-02T00:00:00Z", role = "PARTNER"),
        )
    private var detailStatus = "PENDING"

    private val client =
        createHttpClient(
            { null },
            MockEngine { req ->
                val path = req.url.encodedPath.removePrefix("/api/")
                val q = req.url.encodedQuery
                val body = req.body.toByteArray().decodeToString()
                calls += "${req.method.value} $path" + (if (q.isEmpty()) "" else "?$q") + (if (body.isEmpty()) "" else " $body")
                val json = headersOf(HttpHeaders.ContentType, "application/json")

                fun ok(s: String) = respond(s, HttpStatusCode.OK, json)

                fun fail(code: HttpStatusCode) = respond("""{"status":${code.value},"code":"X"}""", code, json)
                when {
                    path == "care-partnerships/partners/search" && q.contains("q=slow") -> {
                        Thread.sleep(300)
                        ok("""[{"id":40,"firstName":"Old","lastName":"Answer"}]""")
                    }
                    path == "care-partnerships/partners/search" && q.contains("profession=ortho") ->
                        ok("""[{"id":32,"firstName":"Behzod","lastName":"Normatov","profession":"Orthodontist"}]""")
                    path == "care-partnerships/partners/search" ->
                        ok(
                            """[{"id":31,"firstName":"Madina","lastName":"Rasulova","profession":"Cardiologist","clinic":"RCC","locationCity":"Tashkent"},{"id":32,"firstName":"Behzod","lastName":"Normatov","profession":"Orthodontist"},{"id":0,"firstName":"Bad"}]""",
                        )
                    path == "care-partnerships" && req.method.value == "GET" -> ok(mine.joinToString(",", "[", "]"))
                    path == "care-partnerships" && inviteConflict -> fail(HttpStatusCode.Conflict)
                    path == "care-partnerships" -> ok(p(9, "PENDING", 12, 9, 31, "2026-11-06T00:00:00Z"))
                    path == "care-partnerships/2" -> ok(p(2, detailStatus, 12, 32, 9, "2026-11-01T00:00:00Z"))
                    path == "care-partnerships/2/accept" -> {
                        detailStatus = "ACTIVE"
                        ok(p(2, "ACTIVE", 12, 32, 9, "2026-11-06T00:00:00Z"))
                    }
                    path == "care-partnerships/2/progress" && req.method.value == "GET" && progressFails ->
                        fail(
                            HttpStatusCode.InternalServerError,
                        )
                    path == "care-partnerships/2/progress" && req.method.value == "GET" ->
                        ok(
                            if (slowProgress) {
                                Thread.sleep(300).let { "[]" }
                            } else {
                                """[{"id":2,"authorDoctorId":9,"authorDoctorName":"Me","body":"later","createdAt":"2026-11-02T00:00:00Z"},{"id":1,"authorDoctorId":32,"authorDoctorName":"D32","body":"first","createdAt":"2026-11-01T00:00:00Z"}]"""
                            },
                        )
                    path == "care-partnerships/2/progress" && postFails -> fail(HttpStatusCode.InternalServerError)
                    path == "care-partnerships/2/progress" ->
                        ok(
                            """{"id":3,"authorDoctorId":9,"authorDoctorName":"Me","body":"Saw them","createdAt":"2026-11-06T00:00:00Z"}""",
                        )
                    path == "care-partnerships/404" -> fail(HttpStatusCode.NotFound)
                    else -> ok("{}")
                }
            },
        )
    private val repo = CarePartnershipsRepository { client }
    private val main = UnconfinedTestDispatcher()
    private val created = mutableListOf<androidx.lifecycle.ViewModel>()

    @Before fun setUp() = Dispatchers.setMain(main)

    @After fun tearDown() {
        created.stopAll()
        Dispatchers.resetMain()
    }

    private fun settle(check: () -> Boolean) {
        repeat(500) {
            if (check()) return
            Thread.sleep(10)
        }
    }

    private fun pause() {
        main.scheduler.advanceTimeBy(FindPartnerViewModel.DEBOUNCE.inWholeMilliseconds + 1)
        main.scheduler.runCurrent()
    }

    @Test
    fun `find searches on open and as typed, marks partners, picks one with its specialty, sends, a pending duplicate says so`() {
        val vm = FindPartnerViewModel(12, { "Uzbekistan" }, repo).also { created += it }
        settle { vm.state.value.hits != null && vm.state.value.partners.isNotEmpty() }
        // A row without an id is skipped; the patient's open partnerships mark their doctors (not the completed one).
        assertThat(vm.state.value.hits!!.map { it.id }).containsExactly(31L, 32L).inOrder()
        assertThat(vm.state.value.partners).containsExactly(9L, 31L, 32L)
        assertThat(vm.state.value.hits!!.first().subtitle).isEqualTo("Cardiologist · RCC · Tashkent")
        vm.pick(vm.state.value.hits!!.first())
        assertThat(vm.state.value.reason).isEqualTo("Cardiologist")
        // Typing searches after the pause; the picked doctor gone from the results lets go.
        vm.specialty(" ortho ")
        assertThat(calls.count { it.startsWith("GET care-partnerships/partners/search") }).isEqualTo(1)
        pause()
        settle { vm.state.value.hits?.size == 1 }
        assertThat(
            calls.last {
                it.startsWith("GET care-partnerships/partners/search")
            },
        ).isEqualTo("GET care-partnerships/partners/search?country=Uzbekistan&profession=ortho&limit=30")
        assertThat(vm.state.value.selected).isNull()
        val hit = vm.state.value.hits!!.single()
        vm.pick(hit)
        // Untouched, the specialty follows the doctor picked; typed, it stays.
        assertThat(vm.state.value.reason).isEqualTo("Orthodontist")
        vm.reason(" Braces review ")
        vm.pick(hit)
        vm.pick(hit)
        assertThat(vm.state.value.reason).isEqualTo(" Braces review ")
        vm.message("  Crowding  ")
        // A typed message asks before leaving.
        assertThat(vm.back()).isFalse()
        assertThat(vm.state.value.askDiscard).isTrue()
        vm.keep()
        inviteConflict = true
        vm.send()
        settle { vm.state.value.banner != null }
        assertThat(vm.state.value.banner!!.text).isEqualTo(Res.string.careInviteExists)
        inviteConflict = false
        vm.send()
        settle { vm.state.value.sent != null }
        assertThat(calls.last { it.startsWith("POST care-partnerships") })
            .isEqualTo(
                """POST care-partnerships {"patientId":12,"partnerDoctorId":32,"specialtyRequested":"Braces review","message":"Crowding"}""",
            )
        assertThat(vm.state.value.sent!!.id).isEqualTo(9L)
    }

    @Test
    fun `an older search answer never replaces a newer one, even while the next one waits for the pause`() {
        val vm = FindPartnerViewModel(12, repo = repo).also { created += it }
        settle { vm.state.value.hits != null }
        vm.query("slow")
        pause()
        vm.query("")
        pause()
        Thread.sleep(500)
        assertThat(vm.state.value.hits!!.map { it.id }).containsExactly(31L, 32L)
        // The slow answer lands while the new text still waits for its pause: dropped.
        vm.query("slow")
        pause()
        vm.query("x")
        Thread.sleep(500)
        assertThat(vm.state.value.hits!!.map { it.id }).containsExactly(31L, 32L)
        // No country known: none sent (the server's default).
        assertThat(
            calls.filter {
                it.startsWith("GET care-partnerships/partners/search")
            }.none { it.contains("country=") },
        ).isTrue()
    }

    @Test
    fun `the list puts my answers first, filters by side and status, and narrows to the patient until Show all`() {
        val vm = PartnershipsViewModel(12, { 9L }, repo).also { created += it }
        vm.load()
        settle { vm.state.value.all != null }
        val s = vm.state.value
        // Patient 12 only: the invite to me first, then the latest change.
        assertThat(vm.rows(s)!!.map { it.id }).containsExactly(2L, 4L, 1L).inOrder()
        vm.showAll()
        assertThat(vm.rows(vm.state.value)!!.map { it.id }).containsExactly(5L, 2L, 4L, 3L, 1L).inOrder()
        vm.filter(PartnershipFilter.NeedsAnswer)
        assertThat(vm.rows(vm.state.value)!!.map { it.id }).containsExactly(2L, 5L)
        vm.filter(PartnershipFilter.Sent)
        assertThat(vm.rows(vm.state.value)!!.map { it.id }).containsExactly(3L)
        vm.filter(PartnershipFilter.Finished)
        assertThat(vm.rows(vm.state.value)!!.map { it.id }).containsExactly(4L)
        vm.filter(PartnershipFilter.Active)
        assertThat(vm.rows(vm.state.value)!!.map { it.id }).containsExactly(1L)
        // Neither viewerRole nor a known id: no side is guessed.
        val unknown = PartnershipsViewModel(null, { null }, repo).also { created += it }
        unknown.load()
        settle { unknown.state.value.all != null }
        assertThat(unknown.state.value.all!!.first { it.id == 2L }.iInitiated(null)).isNull()
        assertThat(unknown.state.value.all!!.first { it.id == 5L }.iInitiated(null)).isFalse()
    }

    @Test
    fun `the detail asks before an action, takes the server's answer, posts updates only when active and keeps a failed one`() {
        val vm = PartnershipViewModel(2, repo).also { created += it }
        vm.load()
        settle { vm.state.value.partnership != null && vm.state.value.progress != null }
        assertThat(vm.state.value.progress!!.map { it.body }).containsExactly("first", "later").inOrder()
        // Pending: nothing to post.
        vm.draft("x")
        vm.post()
        assertThat(calls.none { it.startsWith("POST care-partnerships/2/progress") }).isTrue()
        vm.ask(PartnershipAction.Accept)
        assertThat(calls.none { it.startsWith("POST care-partnerships/2/accept") }).isTrue()
        vm.act()
        settle { vm.state.value.partnership?.status == PartnershipStatus.Active }
        assertThat(
            calls.single {
                it.startsWith("POST care-partnerships/2/accept")
            },
        ).isEqualTo("POST care-partnerships/2/accept {}")
        assertThat(vm.state.value.confirm).isNull()
        assertThat(vm.state.value.banner!!.text).isEqualTo(Res.string.careAccepted)
        postFails = true
        vm.draft("  Saw them  ")
        vm.post()
        settle { vm.state.value.banner?.text == Res.string.carePostFailed }
        assertThat(vm.state.value.draft).isEqualTo("  Saw them  ")
        postFails = false
        vm.post()
        settle { vm.state.value.draft.isEmpty() }
        assertThat(
            calls.last {
                it.startsWith("POST care-partnerships/2/progress")
            },
        ).isEqualTo("""POST care-partnerships/2/progress {"body":"Saw them"}""")
        assertThat(vm.state.value.progress!!.last().body).isEqualTo("Saw them")

        // A read that started before a post can't hide the posted update.
        slowProgress = true
        vm.loadProgress()
        vm.draft("Second")
        vm.post()
        settle { vm.state.value.draft.isEmpty() }
        Thread.sleep(500)
        assertThat(vm.state.value.progress!!.map { it.id }).contains(3L)
        slowProgress = false

        progressFails = true
        val other = PartnershipViewModel(2, repo).also { created += it }
        other.load()
        settle { other.state.value.partnership != null && other.state.value.progressFailed }
        assertThat(other.state.value.error).isNull()
        val gone = PartnershipViewModel(404, repo).also { created += it }
        gone.load()
        settle { gone.state.value.notFound }
    }
}
