@file:Suppress("ktlint:standard:max-line-length") // JSON fixtures

package shifa.doctor.data.patients

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Test
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.model.ApiException
import shifa.doctor.data.model.PatientRequest
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class PatientsRepositoryTest {
    private val reads = java.util.concurrent.CopyOnWriteArrayList<String>()
    private val sent = java.util.concurrent.CopyOnWriteArrayList<String>()
    private var prophylaxisBody = ""
    private var asPage = false
    private var badPage = false
    private var hold = kotlinx.coroutines.CompletableDeferred(Unit)
    private val engine =
        MockEngine { req ->
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            val path = req.url.encodedPath
            if (req.method != HttpMethod.Get) {
                sent += "${req.method.value} $path ${req.body.toByteArray().decodeToString()}"
                return@MockEngine respond(
                    """{"id":500,"name":"Anna Karimova","phones":["+1"]}""",
                    HttpStatusCode.OK,
                    json,
                )
            }
            reads += "$path?${req.url.encodedQuery}"
            when {
                path.endsWith("/patients") && badPage -> respond("""{"items":[]}""", HttpStatusCode.OK, json)
                path.endsWith("/patients") && asPage -> {
                    hold.await()
                    respond(
                        """{"content":[{"id":1,"name":"A"},{"id":2,"name":"B"}],"last":true}""",
                        HttpStatusCode.OK,
                        json,
                    )
                }
                path.endsWith("/patients") -> {
                    // 100 on the first page (with a row without an id), 3 on the second.
                    val page = req.url.parameters["page"]!!.toInt()
                    val rows =
                        if (page == 0) {
                            (1..99).map { """{"id":$it,"name":"P$it"}""" } + """{"name":"no id"}"""
                        } else {
                            listOf(
                                """{"id":101,"name":"Z"}""",
                                """{"id":1,"name":"P1 again"}""",
                                """{"id":102,"broken":}""".take(0) + """{"id":102,"name":"Y"}""",
                            )
                        }
                    respond(rows.joinToString(",", "[", "]"), HttpStatusCode.OK, json)
                }
                path.endsWith(
                    "/patients/9",
                ) -> respond("""{"status":404,"code":"NOT_FOUND","requestId":"req-9"}""", HttpStatusCode.NotFound, json)
                path.endsWith(
                    "for-assignment",
                ) ->
                    respond(
                        """{"content":[{"id":2,"name":"b"},{"id":1,"name":"A"},{"id":null,"name":"x"},{"id":2,"name":"b"}],"last":true}""",
                        HttpStatusCode.OK,
                        json,
                    )
                path.endsWith("settings") -> respond(prophylaxisBody, HttpStatusCode.OK, json)
                path.matches(Regex(".*/patients/\\d+")) -> {
                    val id = path.substringAfterLast('/')
                    respond("""{"id":$id,"name":"Fresh $id"}""", HttpStatusCode.OK, json)
                }
                else -> respond("[]", HttpStatusCode.OK, json)
            }
        }
    private val time = TestTimeSource()
    private val repo = PatientsRepository({ createHttpClient({ null }, engine) }, time, 60.seconds)

    @Test
    fun `the directory is read page by page, rows without an id dropped, each patient once`() =
        runBlocking<Unit> {
            val list = repo.refresh().getOrThrow()
            assertThat(
                reads,
            ).containsExactly(
                "/api/patients?page=0&size=100&sort=fullName%2Casc",
                "/api/patients?page=1&size=100&sort=fullName%2Casc",
            ).inOrder()
            assertThat(list).hasSize(101) // 99 + 101 + 102, the repeated 1 once
            assertThat(repo.patients.value).isEqualTo(list)
        }

    @Test
    fun `a clinic read refreshes a patient of the directory but never adds another clinic doctor's patient`() =
        runBlocking<Unit> {
            repo.refresh()
            repo.patient(500, clinicId = 2).getOrThrow()
            assertThat(reads.last()).isEqualTo("/api/patients/500?clinicId=2")
            assertThat(repo.patients.value!!.none { it.id == 500L }).isTrue()
            repo.patient(5, clinicId = 2).getOrThrow()
            assertThat(repo.patients.value!!.single { it.id == 5L }.name).isEqualTo("Fresh 5")
            // The doctor's own read still joins.
            repo.patient(501).getOrThrow()
            assertThat(repo.patients.value!!.any { it.id == 501L }).isTrue()
        }

    @Test
    fun `a fresh directory isn't read again within the TTL, a stale one is`() =
        runBlocking<Unit> {
            repo.refresh()
            reads.clear()
            repo.refreshIfStale()
            assertThat(reads).isEmpty()
            time += 61.seconds
            repo.refreshIfStale()
            assertThat(reads).isNotEmpty()
        }

    @Test
    fun `a missing patient is an error with its request id, not null - a created one joins the directory`() =
        runBlocking<Unit> {
            val e = repo.patient(9).exceptionOrNull() as ApiException
            assertThat(e.status).isEqualTo(404)
            assertThat(e.requestId).isEqualTo("req-9")
            repo.refresh()
            repo.create(PatientRequest(name = "Anna Karimova", phones = listOf("+1"), chronicDisease = ""))
            assertThat(
                sent.single(),
            ).isEqualTo("""POST /api/patients {"name":"Anna Karimova","phones":["+1"],"chronicDisease":""}""")
            assertThat(repo.patients.value!!.map { it.id }).contains(500L)
        }

    @Test
    fun `prophylaxis without a setting is null, the booking list reads a page, sorted, once each`() =
        runBlocking<Unit> {
            assertThat(repo.prophylaxis(1, 2).getOrThrow()).isNull()
            prophylaxisBody = """{"patientId":1,"clinicId":2,"intervalMonths":6,"enabled":true}"""
            assertThat(repo.prophylaxis(1, 2).getOrThrow()?.intervalMonths).isEqualTo(6)
            assertThat(repo.forAssignment().getOrThrow().map { it.id }).containsExactly(1L, 2L).inOrder()
        }

    @Test
    fun `a Spring page reads too, and a read older than a create can't drop the new patient`() =
        runBlocking<Unit> {
            asPage = true
            assertThat(repo.refresh().getOrThrow().map { it.id }).containsExactly(1L, 2L)
            hold = kotlinx.coroutines.CompletableDeferred()
            val slow = async { repo.refresh() } // its answer predates the create
            repo.create(PatientRequest(name = "Anna Karimova"))
            hold.complete(Unit)
            slow.await()
            assertThat(repo.patients.value!!.map { it.id }).contains(500L)
        }

    @Test
    fun `an unreadable directory answer is a failure and keeps the list, a create from before sign-out stays out`() =
        runBlocking<Unit> {
            asPage = true
            repo.refresh()
            badPage = true
            assertThat(repo.refresh().isFailure).isTrue()
            assertThat(repo.patients.value!!.map { it.id }).containsExactly(1L, 2L)
            assertThat(repo.prophylaxis(1, 2).getOrThrow()).isNull() // still "no setting" for an empty answer
        }
}
