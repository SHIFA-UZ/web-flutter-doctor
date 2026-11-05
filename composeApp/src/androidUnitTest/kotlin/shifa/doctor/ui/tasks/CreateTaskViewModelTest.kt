@file:Suppress("ktlint:standard:max-line-length")

package shifa.doctor.ui.tasks

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.After
import org.junit.Before
import org.junit.Test
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.model.PatientOption
import shifa.doctor.data.patients.PatientsRepository
import shifa.doctor.data.tasks.ScheduleMode
import shifa.doctor.data.tasks.TasksRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.customTimesAddAtLeastOne
import shifa.doctor.generated.resources.failedToCreateTask
import shifa.doctor.generated.resources.pleaseSelectPatient
import shifa.doctor.generated.resources.taskCreated
import shifa.doctor.generated.resources.timeAlreadyInList
import shifa.doctor.ui.navigation.ShellNotice

@OptIn(ExperimentalCoroutinesApi::class)
class CreateTaskViewModelTest {
    private val posted = mutableListOf<String>()
    private var status = HttpStatusCode.OK
    private val today = LocalDate(2026, 11, 5)

    private fun body(req: HttpRequestData) = (req.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString()

    private val client =
        createHttpClient(
            { null },
            MockEngine { req ->
                val json = headersOf(HttpHeaders.ContentType, "application/json")
                if (req.method.value == "POST") {
                    posted += body(req).orEmpty()
                    if (status == HttpStatusCode.OK) {
                        respond(
                            """{"id":9,"patientId":14,"patientName":"B","taskName":"T","category":"VITAL","status":"ACTIVE","startDate":"2026-11-05","inputType":"NUMERIC","timesPerDay":1}""",
                            status,
                            json,
                        )
                    } else {
                        respond("""{"status":400,"requestId":"r-c"}""", status, json)
                    }
                } else {
                    respond("""[{"id":14,"name":"Bekzod Aliyev"}]""", HttpStatusCode.OK, json)
                }
            },
        )

    private fun vm() =
        CreateTaskViewModel(
            today,
            null,
            null,
            TasksRepository({ client }),
            PatientsRepository({ client }),
        )

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        ShellNotice.pending.value = null
    }

    private fun settle(check: () -> Boolean) {
        repeat(300) {
            if (check()) return
            Thread.sleep(10)
        }
    }

    @Test
    fun `Create checks first, then sends once and closes with the banner`() {
        val classToTest = vm()
        classToTest.create()
        assertThat(ShellNotice.pending.value?.text).isEqualTo(Res.string.pleaseSelectPatient)
        assertThat(classToTest.state.value.tried).isTrue()
        assertThat(posted).isEmpty()
        classToTest.pickPatient(PatientOption(14, "Bekzod Aliyev", null))
        classToTest.edit { it.copy(name = "BP", endDate = LocalDate(2026, 11, 30)) }
        classToTest.mode(ScheduleMode.Custom)
        classToTest.removeSlot(0) // never below one
        assertThat(classToTest.state.value.form.customTimes).hasSize(1)
        classToTest.create()
        classToTest.create()
        settle { classToTest.state.value.done }
        assertThat(posted).hasSize(1)
        assertThat(posted.single()).contains("\"customTimes\":[\"08:00\"]")
        assertThat(ShellNotice.pending.value?.text).isEqualTo(Res.string.taskCreated)
    }

    @Test
    fun `a refused create says so and stays, a repeat slot is refused`() {
        status = HttpStatusCode.BadRequest
        val classToTest = vm()
        classToTest.pickPatient(PatientOption(14, "Bekzod Aliyev", null))
        classToTest.edit { it.copy(name = "BP", endDate = LocalDate(2026, 11, 30)) }
        classToTest.mode(ScheduleMode.Custom)
        classToTest.addSlot()
        assertThat(classToTest.state.value.form.customTimes).containsExactly(LocalTime(8, 0), LocalTime(9, 0)).inOrder()
        classToTest.setSlot(1, LocalTime(8, 0))
        assertThat(ShellNotice.pending.value?.text).isEqualTo(Res.string.timeAlreadyInList)
        assertThat(classToTest.state.value.form.customTimes).hasSize(2)
        classToTest.create()
        settle { !classToTest.state.value.saving && posted.isNotEmpty() }
        assertThat(classToTest.state.value.done).isFalse()
        assertThat(ShellNotice.pending.value?.text).isEqualTo(Res.string.failedToCreateTask)
        assertThat(ShellNotice.pending.value?.requestId).isEqualTo("r-c")
    }

    @Test
    fun `custom with no slots says so, back with changes asks, a later start drops an earlier end`() {
        val classToTest = vm()
        assertThat(classToTest.back()).isTrue()
        classToTest.pickPatient(PatientOption(14, "Bekzod Aliyev", null))
        classToTest.edit { it.copy(name = "BP", mode = ScheduleMode.Custom, customTimes = emptyList()) }
        classToTest.create()
        assertThat(ShellNotice.pending.value?.text).isEqualTo(Res.string.customTimesAddAtLeastOne)
        assertThat(classToTest.back()).isFalse()
        assertThat(classToTest.state.value.askDiscard).isTrue()
        classToTest.endDate(LocalDate(2026, 11, 10))
        classToTest.startDate(LocalDate(2026, 11, 20))
        assertThat(classToTest.state.value.form.endDate).isNull()
    }

    @Test
    fun `the picker reads the patients once`() {
        val classToTest = vm()
        classToTest.openPicker()
        settle { classToTest.state.value.patients != null }
        assertThat(classToTest.state.value.patients!!.map { it.id }).containsExactly(14L)
        assertThat(classToTest.state.value.picking).isTrue()
    }
}
