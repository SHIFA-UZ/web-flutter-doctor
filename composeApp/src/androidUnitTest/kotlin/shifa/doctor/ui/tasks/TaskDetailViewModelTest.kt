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
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Test
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.model.TaskStatus
import shifa.doctor.data.tasks.TasksRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.couldNotLoadTask
import shifa.doctor.generated.resources.failedToCancel
import shifa.doctor.generated.resources.taskCancelled
import shifa.doctor.stopAll
import shifa.doctor.ui.navigation.ShellNotice

@OptIn(ExperimentalCoroutinesApi::class)
class TaskDetailViewModelTest {
    private val created = mutableListOf<androidx.lifecycle.ViewModel>()

    private val asked = java.util.concurrent.CopyOnWriteArrayList<String>()
    private var taskStatus = HttpStatusCode.OK
    private var cancelStatus = HttpStatusCode.OK
    private var checksFail = false
    private var submitted = false
    private val task = """{"id":7,"patientId":14,"patientName":"B","taskName":"BP","category":"VITAL","status":"%s","startDate":"2026-11-01","inputType":"NUMERIC","timesPerDay":2,"patientTimeZone":"Asia/Tashkent"}"""
    private val repo =
        TasksRepository({
            createHttpClient(
                { null },
                MockEngine { req ->
                    val path = req.url.encodedPath.substringAfter("/api/")
                    asked += req.method.value + " " + path
                    val json = headersOf(HttpHeaders.ContentType, "application/json")
                    when {
                        path == "tasks/7/check-ins" && checksFail ->
                            respond(
                                "{}",
                                HttpStatusCode.InternalServerError,
                                json,
                            )
                        path == "tasks/7/check-ins" && submitted ->
                            respond(
                                """[{"id":2,"scheduledDate":"2026-11-02","scheduledTime":"08:00","status":"COMPLETED","numericValue":118}]""",
                                HttpStatusCode.OK,
                                json,
                            )
                        path == "tasks/7/check-ins" ->
                            respond(
                                """[{"id":1,"scheduledDate":"2026-11-01","scheduledTime":"08:00","status":"COMPLETED","numericValue":120},{"id":2,"scheduledDate":"2026-11-02","scheduledTime":"08:00","status":"PENDING"},{"id":3,"scheduledDate":"2026-11-02","scheduledTime":"20:00","status":"PENDING"}]""",
                                HttpStatusCode.OK,
                                json,
                            )
                        path == "tasks/7/cancel" ->
                            respond(
                                if (cancelStatus == HttpStatusCode.OK) {
                                    task.format(
                                        "CANCELLED",
                                    )
                                } else {
                                    """{"status":409,"requestId":"r-c"}"""
                                },
                                cancelStatus,
                                json,
                            )
                        path.startsWith("doctors/me") -> respond("{}", HttpStatusCode.OK, json)
                        path == "tasks/7" && taskStatus != HttpStatusCode.OK ->
                            respond(
                                """{"status":${taskStatus.value},"requestId":"r-t"}""",
                                taskStatus,
                                json,
                            )
                        else -> respond(task.format("ACTIVE"), HttpStatusCode.OK, json)
                    }
                },
            )
        })

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() {
        created.stopAll()
        Dispatchers.resetMain()
        ShellNotice.pending.value = null
    }

    private fun settle(check: () -> Boolean) {
        repeat(300) {
            if (check()) return
            Thread.sleep(10)
        }
    }

    private fun loaded() =
        TaskDetailViewModel(7, repo).also { created += it }.also { vm ->
            vm.load().also {
                settle { vm.state.value.task != null && vm.state.value.checkIns != null }
            }
        }

    @Test
    fun `the task and its check-ins, newest first`() {
        val s = loaded().state.value
        assertThat(s.task!!.name).isEqualTo("BP")
        assertThat(s.checkIns!!.map { it.id }).containsExactly(3L, 2L, 1L).inOrder()
        assertThat(s.checkIns!!.last().date).isEqualTo(LocalDate(2026, 11, 1))
        assertThat(s.canCancel).isTrue()
    }

    @Test
    fun `404 is not found, any other failure can be retried, check-ins fail on their own`() {
        taskStatus = HttpStatusCode.NotFound
        val gone = TaskDetailViewModel(7, repo).also { created += it }.also { it.load() }
        settle { gone.state.value.error != null }
        assertThat(gone.state.value.notFound).isTrue()
        taskStatus = HttpStatusCode.InternalServerError
        val broken = TaskDetailViewModel(7, repo).also { created += it }.also { it.load() }
        settle { broken.state.value.error != null }
        assertThat(broken.state.value.notFound).isFalse()
        assertThat(broken.state.value.error?.text).isEqualTo(Res.string.couldNotLoadTask)
        assertThat(broken.state.value.error?.requestId).isEqualTo("r-t")
        taskStatus = HttpStatusCode.OK
        checksFail = true
        val partial = TaskDetailViewModel(7, repo).also { created += it }.also { it.load() }
        settle { partial.state.value.task != null }
        assertThat(partial.state.value.checkInsFailed).isTrue()
        assertThat(partial.state.value.checkIns).isNull()
    }

    @Test
    fun `cancel asks first, sends once, then goes back with the banner, a refusal says so`() {
        val classToTest = loaded()
        classToTest.askCancel(true)
        classToTest.cancel()
        classToTest.cancel()
        settle { classToTest.state.value.cancelled }
        assertThat(asked.count { it == "PATCH tasks/7/cancel" }).isEqualTo(1)
        assertThat(classToTest.state.value.task!!.status).isEqualTo(TaskStatus.Cancelled)
        assertThat(ShellNotice.pending.value?.text).isEqualTo(Res.string.taskCancelled)
        assertThat(ShellNotice.pending.value?.success).isTrue()
        cancelStatus = HttpStatusCode.Conflict
        val refused = loaded()
        refused.askCancel(true)
        refused.cancel()
        settle { !refused.state.value.cancelling && !refused.state.value.askCancel }
        assertThat(refused.state.value.cancelled).isFalse()
        assertThat(ShellNotice.pending.value?.text).isEqualTo(Res.string.failedToCancel)
        assertThat(ShellNotice.pending.value?.requestId).isEqualTo("r-c")
    }

    @Test
    fun `values read as people write them`() {
        assertThat(numberLabel(120.0)).isEqualTo("120")
        assertThat(numberLabel(36.6)).isEqualTo("36.6")
    }

    @Test
    fun `an open check-in shows what came in after a refresh, a 403 reads the profile again`() {
        val classToTest = loaded()
        classToTest.view(classToTest.state.value.checkIns!!.first { it.id == 2L })
        submitted = true
        classToTest.load()
        settle { classToTest.state.value.viewing?.numericValue != null }
        assertThat(classToTest.state.value.viewing!!.numericValue).isEqualTo(118.0)
        taskStatus = HttpStatusCode.Forbidden
        val client =
            createHttpClient(
                { null },
                MockEngine {
                        req ->
                    asked += req.url.encodedPath.substringAfter("/api/")
                    respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                },
            )
        val gated =
            TaskDetailViewModel(
                7,
                repo,
                shifa.doctor.data.profile.ProfileRepository { client },
            ).also { created += it }.also { it.load() }
        settle { asked.any { it.startsWith("doctors/me") } }
        assertThat(asked.any { it.startsWith("doctors/me") }).isTrue()
        assertThat(gated.state.value.error).isNotNull()
    }

    @Test
    fun `a task deleted meanwhile gives way to not found on refresh`() {
        val classToTest = loaded()
        taskStatus = HttpStatusCode.NotFound
        classToTest.load()
        settle { classToTest.state.value.notFound }
        assertThat(classToTest.state.value.task).isNull()
        assertThat(classToTest.state.value.canCancel).isFalse()
    }
}
