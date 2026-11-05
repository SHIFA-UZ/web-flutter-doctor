@file:Suppress("ktlint:standard:max-line-length")

package shifa.doctor.ui.tasks

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
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
import shifa.doctor.data.model.TaskCategory
import shifa.doctor.data.model.TaskStatus
import shifa.doctor.data.tasks.TasksRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.tasksLoadFailed

@OptIn(ExperimentalCoroutinesApi::class)
class TasksViewModelTest {
    private var fail = false
    private var forbidden = false
    private var reads = 0
    private val asked = mutableListOf<String>()
    private val list =
        """[{"id":1,"patientId":14,"patientName":"Bekzod Aliyev","taskName":"Blood pressure","category":"VITAL","status":"ACTIVE","startDate":"2026-11-01"},
           {"id":2,"patientId":11,"patientName":"Dilnoza","taskName":"Metformin","category":"MEDICATION","status":"ACTIVE","startDate":"2026-11-01"},
           {"id":3,"patientId":12,"patientName":"Jasur","taskName":"Walk","category":"EXERCISE","status":"COMPLETED","startDate":"2026-10-01"},
           {"id":4,"patientId":13,"patientName":"Malika","taskName":"Weight","category":"OTHER","status":"EXPIRED","startDate":"2026-09-01"}]"""
    private val repo =
        TasksRepository({
            createHttpClient(
                { null },
                MockEngine { req ->
                    reads++
                    asked += req.url.encodedPath.substringAfter("/api/")
                    val json = headersOf(HttpHeaders.ContentType, "application/json")
                    if (forbidden) {
                        respond("""{"status":403,"code":"FEATURE_NOT_AVAILABLE"}""", HttpStatusCode.Forbidden, json)
                    } else if (fail) {
                        respond(
                            """{"status":500,"requestId":"r-9"}""",
                            HttpStatusCode.InternalServerError,
                            json,
                        )
                    } else {
                        respond(list, HttpStatusCode.OK, json)
                    }
                },
            )
        })

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private fun settle(check: () -> Boolean) {
        repeat(300) {
            if (check()) return
            Thread.sleep(10)
        }
    }

    private fun loaded() =
        TasksViewModel(
            repo,
        ).also { vm -> vm.refresh().also { settle { vm.state.value.tasks != null } } }

    @Test
    fun `counts over every task, filters combine on the phone`() {
        val classToTest = loaded()
        val s = classToTest.state.value
        assertThat(
            listOf(s.count(TaskStatus.Active), s.count(TaskStatus.Completed), s.count(TaskStatus.Expired)),
        ).containsExactly(2, 1, 1).inOrder()
        classToTest.query("bekzod")
        assertThat(classToTest.state.value.shown.map { it.id }).containsExactly(1L)
        classToTest.query("")
        classToTest.toggleStatus(TaskStatus.Active)
        classToTest.category(TaskCategory.Medication)
        assertThat(classToTest.state.value.shown.map { it.id }).containsExactly(2L)
        classToTest.toggleStatus(TaskStatus.Active) // the card again: off
        assertThat(classToTest.state.value.status).isNull()
        classToTest.clearFilters()
        assertThat(classToTest.state.value.shown).hasSize(4)
        assertThat(classToTest.state.value.filtered).isFalse()
    }

    @Test
    fun `a failed read keeps the list under a banner, a first failure is not the empty card's list`() {
        val classToTest = loaded()
        fail = true
        classToTest.refresh()
        settle { classToTest.state.value.error != null }
        assertThat(classToTest.state.value.tasks).hasSize(4)
        assertThat(classToTest.state.value.error?.text).isEqualTo(Res.string.tasksLoadFailed)
        assertThat(classToTest.state.value.error?.requestId).isEqualTo("r-9")
        val fresh = TasksViewModel(repo)
        fresh.refresh()
        settle { fresh.state.value.error != null }
        // Never read: the list is unknown (not "no tasks"), even once the banner is closed.
        assertThat(fresh.state.value.tasks).isNull()
        assertThat(fresh.state.value.failed).isTrue()
        fresh.dismissError()
        assertThat(fresh.state.value.failed).isTrue()
    }

    @Test
    fun `a 403 reads the profile again so the shell can close the tab`() {
        val profile =
            shifa.doctor.data.profile.ProfileRepository {
                createHttpClient(
                    { null },
                    MockEngine {
                            req ->
                        asked += req.url.encodedPath.substringAfter("/api/")
                        respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                    },
                )
            }
        forbidden = true
        val classToTest = TasksViewModel(repo, profile)
        classToTest.refresh()
        settle { asked.any { it.startsWith("doctors/me") } }
        assertThat(asked.any { it.startsWith("doctors/me") }).isTrue()
        assertThat(classToTest.state.value.error).isNotNull()
        classToTest.dismissError()
        assertThat(classToTest.state.value.error).isNull()
    }

    @Test
    fun `Refresh during the first read replaces it`() {
        val classToTest = TasksViewModel(repo)
        classToTest.refresh()
        classToTest.refresh()
        settle { classToTest.state.value.tasks != null }
        assertThat(classToTest.state.value.tasks).hasSize(4)
        assertThat(classToTest.state.value.refreshing).isFalse()
    }
}
