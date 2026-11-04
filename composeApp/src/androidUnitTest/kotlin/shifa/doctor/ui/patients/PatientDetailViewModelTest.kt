@file:Suppress("ktlint:standard:max-line-length") // JSON fixtures

package shifa.doctor.ui.patients

import com.google.common.truth.Truth.assertThat
import com.russhwolf.settings.MapSettings
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.patients.FavoritesRepository
import shifa.doctor.data.patients.PatientsRepository
import shifa.doctor.data.storage.FavoritesStorage
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.chronicDiseaseUpdated
import shifa.doctor.generated.resources.failedToUpdate
import shifa.doctor.generated.resources.patientAccountExists
import shifa.doctor.generated.resources.phoneUpdated
import shifa.doctor.generated.resources.reportsSmsNotAllowed
import shifa.doctor.generated.resources.saved
import shifa.doctor.generated.resources.smsReminderSaved
import shifa.doctor.generated.resources.smsServiceUnavailable
import shifa.doctor.generated.resources.smsTestSent
import shifa.doctor.stopAll
import shifa.doctor.utils.PracticeClock

@OptIn(ExperimentalCoroutinesApi::class)
class PatientDetailViewModelTest {
    private var phones = """["+998901234567"]"""
    private var account = false
    private var gone = false
    private var patchStatus = HttpStatusCode.OK
    private var smsStatus = HttpStatusCode.OK
    private var accountStatus = HttpStatusCode.OK
    private val bodies = mutableListOf<String>()
    private val calls = mutableListOf<String>()

    private fun patient() =
        """{"id":7,"name":"Dilnoza Karimova","phones":$phones,"birthDate":"1990-11-04","language":"ru","gender":"FEMALE","chronicDisease":"Diabetes","hasAccount":$account,"username":${if (account) "\"dilnoza.k\"" else "null"},"smsReminderEnabled":false,"smsReminderHoursBefore":24,"clinicalStatus":"AT_RISK","atRisk":true}"""

    private fun json(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = Triple(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private val engine =
        MockEngine { req ->
            val path = req.url.encodedPath.removePrefix("/api/")
            calls += "${req.method.value} $path"
            (req.body as? OutgoingContent.ByteArrayContent)?.let { bodies += it.bytes().decodeToString() }
            val error = { s: HttpStatusCode -> json("""{"status":${s.value},"code":"X","requestId":"req-$path"}""", s) }
            val (body, status, headers) =
                when {
                    path == "patients/99" || (gone && path == "patients/7") -> error(HttpStatusCode.NotFound)
                    path == "patients/98" -> error(HttpStatusCode.InternalServerError)
                    path == "patients/7" && req.method == HttpMethod.Patch ->
                        if (patchStatus == HttpStatusCode.OK) {
                            // The server's copy with the sent fields applied.
                            val sent = Json.parseToJsonElement(bodies.last()).jsonObject
                            json(JsonObject(Json.parseToJsonElement(patient()).jsonObject + sent).toString())
                        } else {
                            error(patchStatus)
                        }
                    path == "patients/7" -> json(patient())
                    path == "patients" -> json("[${patient()}]")
                    path.endsWith("/appointments") ->
                        json(
                            // Completed 20 Oct 20:00 UTC (21 Oct in Tashkent); a later cancelled one and a future one don't count.
                            """[{"id":1,"startAt":"2026-10-20T20:00:00Z","endAt":"2026-10-20T20:30:00Z","status":"COMPLETED"},
                               {"id":2,"startAt":"2026-10-30T09:00:00Z","endAt":"2026-10-30T09:30:00Z","status":"CANCELLED"},
                               {"id":3,"startAt":"2026-12-01T09:00:00Z","endAt":"2026-12-01T09:30:00Z","status":"COMPLETED"}]""",
                        )
                    path.endsWith(
                        "/send-test-sms",
                    ) -> if (smsStatus == HttpStatusCode.OK) json("{}") else error(smsStatus)
                    path.endsWith("/create-account") ->
                        if (accountStatus == HttpStatusCode.OK) {
                            account = true
                            json("""{"username":"dilnoza.k","oneTimePassword":"K7Q4M29X"}""")
                        } else {
                            error(accountStatus)
                        }
                    else -> error(HttpStatusCode.NotFound)
                }
            respond(body, status, headers)
        }
    private val repo = PatientsRepository(client = { createHttpClient({ null }, engine) })
    private val favorites = FavoritesRepository(FavoritesStorage(MapSettings()))
    private val warned = mutableSetOf<Long>()
    private val created = mutableListOf<PatientDetailViewModel>()
    private val clock =
        PracticeClock("Asia/Tashkent", { Instant.parse("2026-11-04T03:00:00Z") }, { TimeZone.of("Asia/Tashkent") })

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

    private fun opened(
        id: Long = 7,
        sms: Boolean = true,
    ) = PatientDetailViewModel(
        id,
        repo,
        favorites,
        flowOf(DetailDoctor(42, "Asia/Tashkent", sms)),
        { clock },
        warned,
    ).also {
        created += it
        it.load()
        settle { it.state.value.lastVisit != null || it.state.value.notFound || it.state.value.error != null }
    }

    @Test
    fun `opens the patient with the last completed visit on the practice date and warns about a chronic disease once`() {
        val classToTest = opened()
        val s = classToTest.state.value
        assertThat(s.patient?.name).isEqualTo("Dilnoza Karimova")
        assertThat(s.patient?.chronic).isEqualTo("Diabetes")
        assertThat(s.lastVisit).isEqualTo(LocalDate(2026, 10, 21))
        assertThat(s.today).isEqualTo(LocalDate(2026, 11, 4))
        assertThat(s.smsAllowed).isTrue()
        assertThat(s.chronicWarning).isTrue()
        classToTest.acknowledgeChronic()
        assertThat(classToTest.state.value.chronicWarning).isFalse()
        // Opening the same patient again in this session doesn't warn again.
        assertThat(opened().state.value.chronicWarning).isFalse()
    }

    @Test
    fun `the star is shared with the directory and kept per doctor`() {
        val classToTest = opened()
        assertThat(classToTest.state.value.favorite).isFalse()
        classToTest.toggleFavorite()
        assertThat(classToTest.state.value.favorite).isTrue()
        assertThat(favorites.ids.value).containsExactly(7L)
        classToTest.toggleFavorite()
        assertThat(classToTest.state.value.favorite).isFalse()
    }

    @Test
    fun `a missing patient says so, a failed read offers retry with its request id`() {
        assertThat(opened(99).state.value.notFound).isTrue()
        // A copy from the directory doesn't keep a deleted patient on screen.
        runBlocking { repo.refresh() }
        gone = true
        val cached = PatientDetailViewModel(7, repo, favorites, flowOf(DetailDoctor()), { clock }, warned)
        created += cached
        assertThat(cached.state.value.patient?.name).isEqualTo("Dilnoza Karimova")
        cached.load()
        settle { cached.state.value.notFound }
        assertThat(cached.state.value.notFound).isTrue()
        gone = false
        val failed = opened(98).state.value
        assertThat(failed.notFound).isFalse()
        assertThat(failed.patient).isNull()
        assertThat(failed.error?.requestId).isEqualTo("req-patients/98")
    }

    @Test
    fun `an SMS change saves at once and rolls back when it fails`() {
        val classToTest = opened()
        classToTest.setSms(true)
        settle { !classToTest.state.value.saving }
        assertThat(classToTest.state.value.patient?.smsReminderEnabled).isTrue()
        assertThat(classToTest.state.value.banner?.text).isEqualTo(Res.string.smsReminderSaved)
        assertThat(bodies.last()).isEqualTo("""{"smsReminderEnabled":true}""")

        patchStatus = HttpStatusCode.InternalServerError
        classToTest.setSmsHours(1)
        settle { !classToTest.state.value.saving }
        assertThat(bodies.last()).isEqualTo("""{"smsReminderHoursBefore":1}""")
        assertThat(classToTest.state.value.patient?.smsHours).isEqualTo(24)
        assertThat(classToTest.state.value.banner?.text).isEqualTo(Res.string.failedToUpdate)
    }

    @Test
    fun `without a phone nothing is sent`() {
        phones = "[]"
        val classToTest = opened()
        classToTest.setSms(true)
        assertThat(calls.none { it.startsWith("PATCH") }).isTrue()
        assertThat(classToTest.state.value.patient?.smsReminderEnabled).isFalse()
    }

    @Test
    fun `the test SMS names a plan without SMS and a provider that is down`() {
        val classToTest = opened()
        classToTest.testSms()
        settle { !classToTest.state.value.testing }
        assertThat(classToTest.state.value.banner?.text).isEqualTo(Res.string.smsTestSent)
        smsStatus = HttpStatusCode.Forbidden
        classToTest.testSms()
        settle { !classToTest.state.value.testing }
        assertThat(classToTest.state.value.banner?.text).isEqualTo(Res.string.reportsSmsNotAllowed)
        smsStatus = HttpStatusCode.ServiceUnavailable
        classToTest.testSms()
        settle { !classToTest.state.value.testing }
        assertThat(classToTest.state.value.banner?.text).isEqualTo(Res.string.smsServiceUnavailable)
    }

    @Test
    fun `a new patient-app login is shown once and the account flag comes from a fresh read`() {
        val classToTest = opened()
        classToTest.createAccount()
        settle { classToTest.state.value.patient?.hasAccount == true }
        assertThat(classToTest.state.value.account?.oneTimePassword).isEqualTo("K7Q4M29X")
        assertThat(classToTest.state.value.patient?.username).isEqualTo("dilnoza.k")
        classToTest.closeAccount()
        assertThat(classToTest.state.value.account).isNull()

        accountStatus = HttpStatusCode.Conflict
        classToTest.createAccount()
        settle { !classToTest.state.value.creatingAccount }
        assertThat(classToTest.state.value.banner?.text).isEqualTo(Res.string.patientAccountExists)
        assertThat(classToTest.state.value.account).isNull()
    }

    @Test
    fun `dropdowns save one field at once, clearing with an empty value, and roll back on failure`() {
        val classToTest = opened()
        classToTest.setChronic(ChronicDisease.None)
        settle { !classToTest.state.value.saving }
        assertThat(bodies.last()).isEqualTo("""{"chronicDisease":""}""")
        assertThat(classToTest.state.value.patient?.chronic).isNull()
        assertThat(classToTest.state.value.banner?.text).isEqualTo(Res.string.chronicDiseaseUpdated)
        classToTest.setChronic(ChronicDisease.DiabetesType2)
        settle { !classToTest.state.value.saving }
        assertThat(bodies.last()).isEqualTo("""{"chronicDisease":"Diabetes (Type 2)"}""")
        classToTest.setGender(null)
        settle { !classToTest.state.value.saving }
        assertThat(bodies.last()).isEqualTo("""{"gender":""}""")
        assertThat(classToTest.state.value.banner?.text).isEqualTo(Res.string.saved)

        patchStatus = HttpStatusCode.InternalServerError
        classToTest.setBloodGroup("O+")
        settle { !classToTest.state.value.saving }
        assertThat(bodies.last()).isEqualTo("""{"bloodGroup":"O+"}""")
        assertThat(classToTest.state.value.patient?.bloodGroup).isNull()
        assertThat(classToTest.state.value.banner?.text).isEqualTo(Res.string.failedToUpdate)
    }

    @Test
    fun `phones save as one E164 list, a bad one or a taken one keeps the typing, and the last one asks first`() {
        val classToTest = opened()
        classToTest.editPhones(listOf("+998901234567", "12"))
        classToTest.savePhones()
        assertThat(classToTest.state.value.badPhones).containsExactly(1)
        assertThat(calls.none { it.startsWith("PATCH") }).isTrue()

        patchStatus = HttpStatusCode.Conflict
        classToTest.editPhones(listOf("+998901234567", "90 111 22 33"))
        classToTest.savePhones()
        settle { !classToTest.state.value.saving }
        assertThat(bodies.last()).isEqualTo("""{"phones":["+998901234567","+998901112233"],"phone":"+998901234567"}""")
        assertThat(classToTest.state.value.phoneTaken).isTrue()
        assertThat(classToTest.state.value.phones).hasSize(2)

        patchStatus = HttpStatusCode.OK
        classToTest.savePhones()
        settle { !classToTest.state.value.saving }
        assertThat(classToTest.state.value.phones).isNull()
        assertThat(classToTest.state.value.patient?.allPhones).containsExactly("+998901234567", "+998901112233")
        assertThat(classToTest.state.value.banner?.text).isEqualTo(Res.string.phoneUpdated)
        assertThat(classToTest.state.value.phoneTaken).isFalse()

        classToTest.editPhones(listOf(""))
        classToTest.savePhones()
        assertThat(classToTest.state.value.askRemovePhones).isTrue()
        classToTest.savePhones(confirmed = true)
        settle { !classToTest.state.value.saving }
        assertThat(bodies.last()).isEqualTo("""{"phones":[],"phone":""}""")
    }

    @Test
    fun `typed allergies ask before leaving the tab or the screen, and save on their own`() {
        val classToTest = opened()
        classToTest.select(RecordTab.Medical)
        classToTest.editAllergies("Penicillin ")
        classToTest.select(RecordTab.Overview)
        assertThat(classToTest.state.value.askDiscard).isTrue()
        assertThat(classToTest.state.value.tab).isEqualTo(RecordTab.Medical)
        assertThat(classToTest.discard()).isEqualTo(RecordTab.Overview)
        assertThat(classToTest.state.value.tab).isEqualTo(RecordTab.Overview)
        assertThat(classToTest.state.value.allergies).isNull()

        classToTest.select(RecordTab.Medical)
        classToTest.editAllergies("Penicillin ")
        assertThat(classToTest.back()).isFalse()
        classToTest.saveAllergies()
        settle { !classToTest.state.value.saving }
        assertThat(bodies.last()).isEqualTo("""{"allergies":"Penicillin"}""")
        assertThat(classToTest.state.value.allergiesDirty).isFalse()
        assertThat(classToTest.back()).isTrue()

        // A failed save shows the stored value again; Edit details supersedes typing left in the tab.
        patchStatus = HttpStatusCode.InternalServerError
        classToTest.editAllergies("Latex")
        classToTest.saveAllergies()
        settle { !classToTest.state.value.saving }
        assertThat(classToTest.state.value.allergyText).isEqualTo("Penicillin")
        classToTest.editPhones(listOf("+998901234567", "+998901112233"))
        classToTest.editAllergies("Latex")
        classToTest.edited(classToTest.state.value.patient!!.copy(allergies = "Dust"))
        assertThat(classToTest.state.value.allergyText).isEqualTo("Dust")
        // A field the sheet didn't change keeps its typing.
        assertThat(classToTest.state.value.phones).hasSize(2)
    }

    @Test
    fun `a save to a patient deleted elsewhere leaves the record`() {
        val classToTest = opened()
        patchStatus = HttpStatusCode.NotFound
        classToTest.setGender(null)
        settle { !classToTest.state.value.saving }
        assertThat(classToTest.state.value.notFound).isTrue()
    }
}
