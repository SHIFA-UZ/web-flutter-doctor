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
import shifa.doctor.data.profile.ProfileRepository
import shifa.doctor.data.tasks.TasksRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.templatesLoadFailed
import shifa.doctor.stopAll

@OptIn(ExperimentalCoroutinesApi::class)
class TemplatesViewModelTest {
    private val created = mutableListOf<androidx.lifecycle.ViewModel>()

    private var status = HttpStatusCode.OK
    private val asked = java.util.concurrent.CopyOnWriteArrayList<String>()
    private val client =
        createHttpClient(
            { null },
            MockEngine { req ->
                val path = req.url.encodedPath.substringAfter("/api/")
                asked += path
                val json = headersOf(HttpHeaders.ContentType, "application/json")
                when {
                    path.startsWith("doctors/me") -> respond("{}", HttpStatusCode.OK, json)
                    status != HttpStatusCode.OK ->
                        respond(
                            """{"status":${status.value},"requestId":"r-t"}""",
                            status,
                            json,
                        )
                    else ->
                        respond(
                            """[{"taskName":"BP","category":"VITAL","timesPerDay":2,"startTime":"08:00","intervalHours":12,"inputType":"NUMERIC"},{"taskName":"BP","category":"VITAL","timesPerDay":1}]""",
                            status,
                            json,
                        )
                }
            },
        )

    private fun vm() =
        TemplatesViewModel(
            TasksRepository({ client }),
            ProfileRepository { client },
        ).also { created += it }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

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

    @Test
    fun `templates read, twins kept, a later failure keeps them`() {
        val classToTest = vm().also { it.load() }
        settle { classToTest.state.value.templates != null }
        assertThat(classToTest.state.value.templates!!.map { it.name }).containsExactly("BP", "BP")
        assertThat(
            classToTest.state.value.templates!!.first().schedule.times.map {
                it.toString()
            },
        ).containsExactly("08:00", "20:00").inOrder()
        status = HttpStatusCode.InternalServerError
        classToTest.load()
        settle { classToTest.state.value.error != null }
        assertThat(classToTest.state.value.templates).hasSize(2)
        assertThat(classToTest.state.value.failed).isFalse()
    }

    @Test
    fun `a first failure is not the empty state, a 403 reads the profile again`() {
        status = HttpStatusCode.Forbidden
        val classToTest = vm().also { it.load() }
        settle { classToTest.state.value.failed && asked.any { it.startsWith("doctors/me") } }
        assertThat(classToTest.state.value.templates).isNull()
        assertThat(classToTest.state.value.failed).isTrue()
        assertThat(asked.any { it.startsWith("doctors/me") }).isTrue()
        status = HttpStatusCode.InternalServerError
        val other = vm().also { it.load() }
        settle { other.state.value.failed }
        assertThat(other.state.value.error?.text).isEqualTo(Res.string.templatesLoadFailed)
        // Retry: the skeleton again (not the failure with a live Retry) until the answer.
        status = HttpStatusCode.OK
        other.load()
        settle { other.state.value.templates != null }
        assertThat(other.state.value.failed).isFalse()
        assertThat(other.state.value.templates).hasSize(2)
    }
}
