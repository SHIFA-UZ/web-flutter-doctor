@file:Suppress("ktlint:standard:max-line-length") // JSON fixtures

package shifa.doctor.ui.patients

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
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Test
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.model.Patient
import shifa.doctor.data.patients.PatientsRepository
import shifa.doctor.data.profile.Gender
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.createPatientInvalid
import shifa.doctor.stopAll
import shifa.doctor.ui.navigation.ShellNotice

@OptIn(ExperimentalCoroutinesApi::class)
class CreatePatientViewModelTest {
    private val sent = mutableListOf<String>()
    private val methods = mutableListOf<String>()
    private var status = HttpStatusCode.OK
    private val engine =
        MockEngine { req ->
            sent += req.body.toByteArray().decodeToString()
            methods += "${req.method.value} ${req.url.encodedPath}"
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            if (status == HttpStatusCode.OK) {
                respond("""{"id":77,"name":"Anna Karimova"}""", status, json)
            } else {
                respond("""{"status":${status.value},"code":"X","requestId":"req-c"}""", status, json)
            }
        }
    private val created = mutableListOf<CreatePatientViewModel>()

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

    private fun opened() =
        CreatePatientViewModel(
            PatientsRepository(client = {
                createHttpClient({ null }, engine)
            }),
        ).also { created += it }

    @Test
    fun `the request has E164 phones once each, codes for language and blank fields left out`() {
        val classToTest = opened()
        classToTest.edit {
            it.copy(
                name = "  Anna Karimova ",
                phones = listOf("90 123 45 67", "+998901234567", ""),
                language = PatientLanguage.Russian,
                gender = Gender.Female,
                bloodGroup = "O+",
                birthDate = LocalDate(1990, 5, 15),
            )
        }
        classToTest.create()
        settle { classToTest.state.value.created != null }
        assertThat(sent.single()).isEqualTo(
            """{"name":"Anna Karimova","phones":["+998901234567"],"phone":"+998901234567","birthDate":"1990-05-15","language":"ru","chronicDisease":"","gender":"Female","bloodGroup":"O+"}""",
        )
        assertThat(classToTest.state.value.created?.id).isEqualTo(77)
    }

    @Test
    fun `no name, a bad phone or a bad email send nothing and say so after the first try`() {
        val classToTest = opened()
        classToTest.create()
        assertThat(classToTest.state.value.tried && classToTest.state.value.nameMissing).isTrue()
        classToTest.edit { it.copy(name = "Anna", phones = listOf("12"), email = "abc") }
        classToTest.create()
        assertThat(classToTest.state.value.badPhones).containsExactly(0)
        assertThat(classToTest.state.value.badEmail).isTrue()
        assertThat(sent).isEmpty()
    }

    @Test
    fun `a known phone stays open with the phone marked, a 400 says check the details`() {
        val classToTest = opened()
        classToTest.edit { it.copy(name = "Anna", phones = listOf("901234567")) }
        status = HttpStatusCode.Conflict
        classToTest.create()
        settle { !classToTest.state.value.saving && sent.isNotEmpty() }
        assertThat(classToTest.state.value.phoneTaken).isTrue()
        assertThat(classToTest.state.value.created).isNull()
        status = HttpStatusCode.BadRequest
        classToTest.edit { it.copy(phones = listOf("901234568")) }
        classToTest.create()
        settle { classToTest.state.value.banner != null }
        assertThat(classToTest.state.value.banner?.text).isEqualTo(Res.string.createPatientInvalid)
        assertThat(classToTest.state.value.banner?.requestId).isEqualTo("req-c")
    }

    @Test
    fun `closing asks only when something was typed, and language reads codes and words`() {
        val classToTest = opened()
        assertThat(classToTest.close()).isTrue()
        classToTest.edit { it.copy(address = "x") }
        assertThat(classToTest.close()).isFalse()
        assertThat(classToTest.state.value.askDiscard).isTrue()
        assertThat(PatientLanguage.of("uz")).isEqualTo(PatientLanguage.Uzbek)
        assertThat(PatientLanguage.of("Uzbek")).isEqualTo(PatientLanguage.Uzbek)
        assertThat(PatientLanguage.of("klingon")).isNull()
    }

    @Test
    fun `edit details starts filled, sends only what changed with cleared text as empty, and needs a change`() {
        val patient =
            Patient(
                12,
                "Anna Karimova",
                phones = listOf("+998901234567"),
                email = "anna@example.com",
                language = "ru",
                gender = "Female",
                birthDate = "1990-05-15",
            )
        val classToTest =
            CreatePatientViewModel(PatientsRepository(client = { createHttpClient({ null }, engine) }), patient)
                .also { created += it }
        val s = classToTest.state.value
        assertThat(s.form.name).isEqualTo("Anna Karimova")
        assertThat(s.form.language).isEqualTo(PatientLanguage.Russian)
        assertThat(s.form.gender).isEqualTo(Gender.Female)
        assertThat(s.dirty).isFalse()
        // Closing unchanged doesn't ask.
        assertThat(classToTest.close()).isTrue()

        classToTest.edit { it.copy(name = "Anna Karimova-Lee", email = "", allergies = "Latex") }
        classToTest.create()
        settle { classToTest.state.value.created != null }
        assertThat(methods.last()).isEqualTo("PATCH /api/patients/12")
        assertThat(sent.last()).isEqualTo("""{"name":"Anna Karimova-Lee","email":"","allergies":"Latex"}""")
    }

    @Test
    fun `editing away every phone asks before saving`() {
        val patient = Patient(12, "Anna Karimova", phones = listOf("+998901234567"))
        val classToTest =
            CreatePatientViewModel(PatientsRepository(client = { createHttpClient({ null }, engine) }), patient)
                .also { created += it }
        classToTest.edit { it.copy(phones = listOf("")) }
        classToTest.create()
        assertThat(classToTest.state.value.askRemovePhones).isTrue()
        assertThat(methods).isEmpty()
        classToTest.create(confirmed = true)
        settle { classToTest.state.value.created != null }
        assertThat(sent.last()).isEqualTo("""{"phones":[],"phone":""}""")
    }

    @Test
    fun `editing a patient deleted elsewhere hands back instead of retrying`() {
        status = HttpStatusCode.NotFound
        val patient = Patient(12, "Anna Karimova")
        val classToTest =
            CreatePatientViewModel(PatientsRepository(client = { createHttpClient({ null }, engine) }), patient)
                .also { created += it }
        classToTest.edit { it.copy(name = "Anna K") }
        classToTest.create()
        settle { !classToTest.state.value.saving && classToTest.state.value.gone }
        assertThat(classToTest.state.value.gone).isTrue()
        assertThat(classToTest.state.value.banner).isNull()
    }
}
