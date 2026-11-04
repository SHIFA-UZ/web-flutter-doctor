package shifa.doctor.data.debug

import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import shifa.doctor.data.api.ApiConfig
import shifa.doctor.data.api.apiCall
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.api.jwt
import shifa.doctor.data.auth.AuthRepository
import shifa.doctor.data.auth.Invite
import shifa.doctor.data.catalog.CatalogRepository
import shifa.doctor.data.config.BootGate
import shifa.doctor.data.config.PublicConfigRepository
import shifa.doctor.data.model.ApiException
import shifa.doctor.data.model.CheckExistingPatientRequest
import shifa.doctor.data.model.DoctorContact
import shifa.doctor.data.model.DoctorSettings
import shifa.doctor.data.model.ProfileUpdateRequest
import shifa.doctor.data.model.RegisterRequest
import shifa.doctor.data.profile.ProfileRepository
import shifa.doctor.data.settings.LanguageRepository
import shifa.doctor.utils.PickedImage
import java.io.File

// Every endpoint the app calls has a handler (CLAUDE.md "Debug builds"); this test lists them, so a new endpoint
// without one is noticed here.
class DemoBackendTest {
    private val forwarded = mutableListOf<String>()
    private var gate = DemoBootGate.None
    private val demo =
        DemoBackend(
            bootGate = { gate },
            passthrough = {
                HttpClient(
                    MockEngine {
                        forwarded += "${it.method.value} ${it.url.encodedPath}"
                        respond("{}", HttpStatusCode.OK)
                    },
                )
            },
            fixture = { File("src/commonMain/composeResources/files/demo/$it").readText() },
        )
    private val client = createHttpClient({ jwt(4_000_000_000L) }, demo.engine(), ApiConfig.BASE_URL)

    @Test
    fun `doctors_me answers from the scrubbed fixture`() =
        runTest {
            val me = apiCall<JsonObject> { client.get(ApiConfig.Endpoints.DOCTOR_ME) }.getOrThrow()
            assertThat(me["profile"]!!.jsonObject["firstName"]!!.jsonPrimitive.content).isEqualTo("Aziza")
            assertThat(me["contact"]!!.jsonObject["email"]!!.jsonPrimitive.content).isEqualTo("demo.doctor@shifa.dev")
            assertThat(forwarded).isEmpty()
        }

    @Test
    fun `the language sync is stored and shows in doctors_me`() =
        runTest {
            assertThat(LanguageRepository { client }.sync("ru").isSuccess).isTrue()
            val me = apiCall<JsonObject> { client.get(ApiConfig.Endpoints.DOCTOR_ME) }.getOrThrow()
            assertThat(me["settings"]!!.jsonObject["language"]!!.jsonPrimitive.content).isEqualTo("ru")
        }

    @Test
    fun `sign-in and unknown GETs reach the real backend, every other write is refused`() =
        runTest {
            val reset =
                apiCall<Unit> {
                    client.post("demo/never-handled") {
                        contentType(ContentType.Application.Json)
                        setBody("{}")
                    }
                }
            assertThat((reset.exceptionOrNull() as ApiException).code).isEqualTo("DEMO_WRITE_REFUSED")
            client.post(ApiConfig.Endpoints.LOGIN) {
                contentType(ContentType.Application.Json)
                setBody("{}")
            }
            client.get("public/doctors")
            val write =
                apiCall<Unit> {
                    client.put("doctors/me/profile") {
                        contentType(ContentType.Application.Json)
                        setBody("{}")
                    }
                }
            assertThat(forwarded).containsExactly("POST /api/auth/login", "GET /api/public/doctors").inOrder()
            assertThat((write.exceptionOrNull() as ApiException).code).isEqualTo("DEMO_WRITE_REFUSED")
        }

    @Test
    fun `public_config carries the splash gate the debug menu picked`() =
        runTest {
            val config = PublicConfigRepository({ client }, "1.0.1", "android")
            assertThat(config.bootGate("en").getOrNull()).isEqualTo(BootGate.None)
            gate = DemoBootGate.Maintenance
            assertThat(config.bootGate("en").getOrNull()).isEqualTo(BootGate.Maintenance(null))
            gate = DemoBootGate.Update
            assertThat(config.bootGate("en").getOrNull()).isInstanceOf(BootGate.Update::class.java)
            assertThat(forwarded).isEmpty()
        }

    @Test
    fun `verify-key opens both registrations from the demo keys and refuses others`() =
        runTest {
            val auth = AuthRepository { client }
            assertThat(auth.verifyKey(DemoBackend.DEMO_DOCTOR_KEY).getOrThrow()).isEqualTo(Invite("DEMO-DOCTOR", false))
            val reception = auth.verifyKey("demo-reception").getOrThrow()!!
            assertThat(reception.receptionist).isTrue()
            assertThat(reception.email).isEqualTo("demo.reception@shifa.dev")
            assertThat(auth.verifyKey("NOPE").getOrThrow()).isNull()
            assertThat(forwarded).isEmpty()
        }

    @Test
    fun `the registration checks answer locally, register itself is refused`() =
        runTest {
            val auth = AuthRepository { client }
            assertThat(auth.isDoctor(email = "DEMO.DOCTOR@shifa.dev").getOrThrow()).isTrue()
            assertThat(auth.isDoctor(phone = "90 000 00 00").getOrThrow()).isTrue()
            assertThat(auth.isDoctor(email = "new@shifa.dev").getOrThrow()).isFalse()
            val patient = CheckExistingPatientRequest("A", "B", DemoBackend.DEMO_PATIENT_EMAIL)
            assertThat(auth.existingPatient(patient).getOrThrow()).isNotNull()
            assertThat(auth.existingPatient(patient.copy(email = "new@shifa.dev")).getOrThrow()).isNull()
            val register = auth.register(RegisterRequest("A", "B", "new@shifa.dev", "Secret1!", "DEMO-DOCTOR"))
            assertThat((register.exceptionOrNull() as ApiException).code).isEqualTo("DEMO_WRITE_REFUSED")
            assertThat(forwarded).isEmpty()
        }

    @Test
    fun `professions, clinics and the profile PATCH answer locally`() =
        runTest {
            val catalog = CatalogRepository({ client })
            assertThat(catalog.professions().getOrThrow().map { it.english }).contains("Dentist")
            assertThat(catalog.clinics("dental").getOrThrow().map { it.name }).containsExactly("Tashkent Demo Dental")
            assertThat(
                ProfileRepository { client }.update(ProfileUpdateRequest(address = "Chilonzor 5")).isSuccess,
            ).isTrue()
            val me = apiCall<JsonObject> { client.get(ApiConfig.Endpoints.DOCTOR_ME) }.getOrThrow()
            assertThat(me["profile"]!!.jsonObject["address"]!!.jsonPrimitive.content).isEqualTo("Chilonzor 5")
            assertThat(forwarded).isEmpty()
        }

    @Test
    fun `contact, billing and settings are merged into the demo doctor, me_profile follows`() =
        runTest {
            val profile = ProfileRepository({ client })
            profile.updateSettings(DoctorSettings(defaultStartTab = "calendar"))
            profile.updateContact(DoctorContact(phone = "+998901112233", email = "new@shifa.dev"))
            val me = profile.refresh().getOrThrow()
            assertThat(me.settings.defaultStartTab).isEqualTo("calendar")
            assertThat(me.contact.email).isEqualTo("new@shifa.dev")
            assertThat(profile.meProfile().getOrThrow().firstName).isEqualTo("Aziza")
            assertThat(forwarded).isEmpty()
        }

    @Test
    fun `a photo upload answers locally and shows in doctors_me`() =
        runTest {
            val profile = ProfileRepository({ client })
            val url = profile.uploadPhoto(PickedImage("photo.jpg", byteArrayOf(1, 2, 3))).getOrThrow()
            assertThat(url).contains("demo-photo.jpg?v=")
            assertThat(profile.refresh().getOrThrow().profile.photoUrl).endsWith("demo-photo.jpg")
            assertThat(forwarded).isEmpty()
        }

    @Test
    fun `the password change answers locally - the seed password is the current one`() =
        runTest {
            val auth = AuthRepository({ client })
            assertThat(auth.changePassword(DEMO_PASSWORD, "Abcdef1!").isSuccess).isTrue()
            assertThat(
                (auth.changePassword("wrong", "Abcdef1!").exceptionOrNull() as ApiException).status,
            ).isEqualTo(400)
            assertThat(forwarded).isEmpty()
        }

    @Test
    fun `a certificate upload answers locally and joins the list`() =
        runTest {
            val profile = ProfileRepository({ client })
            val url = profile.uploadCertificate("diploma.pdf", byteArrayOf(1), "application/pdf").getOrThrow()
            assertThat(profile.refresh().getOrThrow().profile.certificateUrls).contains(url)
            assertThat(forwarded).isEmpty()
        }

    @Test
    fun `services are kept in memory - create, edit, delete`() =
        runTest {
            val repo = shifa.doctor.data.services.ServicesRepository({ client })
            val before = repo.services().getOrThrow().size
            val body =
                shifa.doctor.data.model.UpsertService(
                    "Braces",
                    null,
                    true,
                    false,
                    null,
                    listOf(shifa.doctor.data.model.ServicePrice(100, "USD")),
                )
            val created = repo.save(null, body).getOrThrow()
            assertThat(repo.save(created.id, body.copy(title = "Braces 2")).getOrThrow().title).isEqualTo("Braces 2")
            assertThat(repo.services().getOrThrow().map { it.title }).contains("Braces 2")
            repo.delete(created.id).getOrThrow()
            assertThat(repo.services().getOrThrow()).hasSize(before)
            assertThat(forwarded).isEmpty()
        }

    @Test
    fun `the calendar answers locally - slots, a lunch block and two appointments on even dates`() =
        runTest {
            val repo = shifa.doctor.data.calendar.CalendarRepository({ client })
            val day = repo.day(shifa.doctor.data.calendar.DayKey(kotlinx.datetime.LocalDate(2026, 11, 4))).getOrThrow()
            val counts = shifa.doctor.data.model.DayOccupancy.of(day)
            assertThat(counts.appointments).isEqualTo(2)
            assertThat(counts.blocked).isEqualTo(1)
            assertThat(counts.freeSlots).isEqualTo(12)
            assertThat(forwarded).isEmpty()
        }

    @Test
    fun `appointment cancel, change and the payment reminder answer locally`() =
        runTest {
            val repo = shifa.doctor.data.appointments.AppointmentsRepository { client }
            assertThat(repo.cancel(101).isSuccess).isTrue()
            assertThat(repo.change(102, kotlinx.datetime.Instant.parse("2026-11-04T10:00:00Z"), 60).isSuccess).isTrue()
            assertThat(repo.remindPayment(102).isSuccess).isTrue()
            assertThat(forwarded).isEmpty()
        }

    @Test
    fun `the booking list and a booking answer locally`() =
        runTest {
            val list = shifa.doctor.data.patients.PatientsRepository { client }.forAssignment().getOrThrow()
            assertThat(list.map { it.id }).containsAtLeast(11L, 12L)
            val booked =
                shifa.doctor.data.appointments.AppointmentsRepository { client }.book(
                    shifa.doctor.data.appointments.BookRequest(
                        "2026-11-06T04:00:00Z",
                        30,
                        11,
                        false,
                        "Check-up",
                        null,
                        1,
                    ),
                )
            assertThat(booked.isSuccess).isTrue()
            assertThat(forwarded).isEmpty()
        }

    @Test
    fun `blocks answer locally, the count from the generated days`() =
        runTest {
            val repo = shifa.doctor.data.calendar.BlocksRepository { client }
            val day = kotlinx.datetime.Instant.parse("2026-11-03T19:00:00Z") // 4 November in Tashkent: two appointments
            val next = kotlinx.datetime.Instant.parse("2026-11-04T19:00:00Z")
            assertThat(repo.overlapping(day, next).getOrThrow()).isEqualTo(2)
            assertThat(repo.create(day, next, "Vacation", true).getOrThrow()).isEqualTo(2)
            assertThat(repo.remove(9001).isSuccess).isTrue()
            assertThat(forwarded).isEmpty()
        }

    @Test
    fun `locations are kept in memory - a new primary demotes the old one, the last one can't go`() =
        runTest {
            val repo = shifa.doctor.data.locations.LocationsRepository { client }
            repo.refresh().getOrThrow() // the screen reads the list before any change
            repo.save(null, shifa.doctor.data.model.LocationRequest("Evening", isPrimary = true)).getOrThrow()
            val list = repo.locations.value!!
            assertThat(
                list.map {
                    it.label to it.isPrimary
                },
            ).containsExactly("Evening" to true, "Shifa Demo Clinic" to false).inOrder()
            assertThat(repo.delete(list[0].id).isSuccess).isTrue()
            assertThat(repo.locations.value!!.single().isPrimary).isTrue()
            assertThat(
                (
                    repo.delete(
                        repo.locations.value!!.single().id,
                    ).exceptionOrNull() as shifa.doctor.data.model.ApiException
                ).status,
            ).isEqualTo(409)
            assertThat(forwarded).isEmpty()
        }

    @Test
    fun `the schedule answers locally - rules kept per location, periods added, date-specific hours`() =
        runTest {
            val repo = shifa.doctor.data.schedule.ScheduleRepository { client }

            fun day(s: String) = kotlinx.datetime.LocalDate.parse(s)

            fun hours(
                on: String,
                at: Long,
            ) = shifa.doctor.data.model.DateSpecificRuleDto(
                on,
                on,
                "10:00",
                "14:00",
                30,
                at,
            )
            val week = repo.rules(1).getOrThrow()
            assertThat(week.ranges(1)).hasSize(2)
            repo.saveRules(1, week.with(1, emptyList())).getOrThrow()
            assertThat(repo.rules(1).getOrThrow().ranges(1)).isEmpty()
            repo.addPeriod(day("2027-01-01"), day("2027-02-28"), day("2026-11-04")).getOrThrow()
            assertThat(repo.periods().getOrThrow()).hasSize(2)
            val first = repo.addDateSpecific(hours("2026-12-27", 1)).getOrThrow()
            val second = repo.addDateSpecific(hours("2026-12-28", 2)).getOrThrow()
            repo.deleteDateSpecific(first.id!!).getOrThrow()
            val third = repo.addDateSpecific(hours("2026-12-29", 1)).getOrThrow()
            assertThat(third.id).isNotEqualTo(second.id) // ids never come back after a delete
            assertThat(repo.dateSpecific(1).getOrThrow().map { it.id }).containsExactly(third.id)
            assertThat(forwarded).isEmpty()
        }
}
