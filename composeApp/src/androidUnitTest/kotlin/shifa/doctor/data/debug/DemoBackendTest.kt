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
            val list = shifa.doctor.data.patients.PatientsRepository(client = { client }).forAssignment().getOrThrow()
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

    @Test
    fun `patients are kept in memory - the directory, one, create and edit`() =
        runTest {
            val repo = shifa.doctor.data.patients.PatientsRepository(client = { client })
            assertThat(repo.refresh().getOrThrow().map { it.id }).containsExactly(11L, 12L, 13L, 14L)
            val made =
                repo.create(
                    shifa.doctor.data.model.PatientRequest(name = "Anna Karimova", phones = listOf("+998901112233")),
                ).getOrThrow()
            assertThat(made.id).isEqualTo(15L)
            val edited =
                repo.update(
                    made.id,
                    shifa.doctor.data.model.PatientRequest(chronicDisease = "Asthma"),
                ).getOrThrow()
            assertThat(edited.name to edited.chronicDisease).isEqualTo("Anna Karimova" to "Asthma")
            assertThat(repo.patient(13).getOrThrow().status).isEqualTo(shifa.doctor.data.model.PatientStatus.AtRisk)
            assertThat(repo.createAccount(13).getOrThrow().username).isNotEmpty()
            assertThat(forwarded).isEmpty()
        }

    @Test
    fun `documents answer locally - the list, an open one as a PDF, the locked one 404, request access`() =
        runTest {
            val repo = shifa.doctor.data.documents.DocumentsRepository { client }
            val docs = repo.list(12).getOrThrow()
            assertThat(docs.map { it.locked }).containsExactly(false, true).inOrder()
            val file = repo.download(12, docs[0].id).getOrThrow()
            assertThat(file.isPdf).isTrue()
            assertThat(file.fileName).isEqualTo("demo-document.pdf")
            assertThat(file.bytes.decodeToString()).endsWith("%%EOF\n")
            assertThat(repo.download(12, docs[1].id).exceptionOrNull())
                .isInstanceOf(shifa.doctor.data.model.ApiException.NotFound::class.java)
            assertThat(repo.requestAccess(12, docs[1].id).isSuccess).isTrue()
            val up =
                repo.upload(
                    12,
                    shifa.doctor.data.documents.UploadFile(
                        "%PDF-1.4".encodeToByteArray(),
                        "scan.pdf",
                        "application/pdf",
                    ),
                    "Scan",
                    "XRAY",
                ).getOrThrow()
            assertThat(up.title to up.category).isEqualTo("Scan" to "XRAY")
            assertThat(repo.list(12).getOrThrow().first().id).isEqualTo(up.id)
            // Kept for that patient only, and it opens as what was uploaded.
            assertThat(repo.list(14).getOrThrow().map { it.id }).doesNotContain(up.id)
            val back = repo.download(12, up.id).getOrThrow()
            assertThat(back.bytes.decodeToString()).isEqualTo("%PDF-1.4")
            assertThat(back.mime).isEqualTo("application/pdf")
            assertThat(forwarded).isEmpty()
        }

    @Test
    fun `forms answer locally - create, update, signature request, link, ICD search`() =
        runTest {
            val repo = shifa.doctor.data.forms.FormsRepository { client }
            assertThat(repo.list(12).getOrThrow()).isEmpty()
            val made =
                repo.save(
                    shifa.doctor.data.model.PatientForm(
                        patientId = 12,
                        date = "2026-11-04",
                        fullName = "Jasur Karimov",
                    ),
                ).getOrThrow()
            val edited = repo.save(made.copy(diagnosis = "Caries")).getOrThrow()
            assertThat(edited.id to edited.diagnosis).isEqualTo(made.id to "Caries")
            assertThat(repo.requestSignature(12, made.id!!).getOrThrow().signatureRequested).isTrue()
            assertThat(repo.linkDocument(12, made.id!!, 1000).getOrThrow().documentId).isEqualTo(1000L)
            assertThat(repo.list(12).getOrThrow().single().diagnosis).isEqualTo("Caries")
            // A save replaces the form's content: an emptied chart is gone; the server's own fields stay.
            val charted = repo.save(edited.copy(dentalChart = mapOf("11" to "C"))).getOrThrow()
            assertThat(charted.dentalChart).containsExactly("11", "C")
            val cleared = repo.save(charted.copy(dentalChart = emptyMap(), signatureRequested = false)).getOrThrow()
            assertThat(cleared.dentalChart).isEmpty()
            assertThat(cleared.signatureRequested).isTrue()
            assertThat(repo.list(14).getOrThrow()).isEmpty()
            assertThat(repo.searchIcd10("caries", "en").getOrThrow().map { it.code }).containsExactly("K02.0", "K02.1")
            assertThat(forwarded).isEmpty()
        }

    @Test
    fun `the clinical engine answers locally - groups, diseases, chips, synthesize with variables`() =
        runTest {
            val repo = shifa.doctor.data.clinical.ClinicalEngineRepository { client }
            val group = repo.groups().getOrThrow().single()
            val diseases = repo.diseases(group.groupId).getOrThrow()
            assertThat(diseases.map { it.diseaseId }).containsExactly("dis.001", "dis.003").inOrder()
            assertThat(diseases.all { it.chips.isEmpty() }).isTrue()
            val detail = repo.disease("dis.003").getOrThrow()
            assertThat(detail.chips).hasSize(3)
            assertThat(
                repo.searchChips("сладк", "ru").getOrThrow().map { it.chipId },
            ).containsExactly("dis.003.complaints.1")
            assertThat(repo.sharedTemplates("xray").getOrThrow().single().chipId).isEqualTo("shared.xray.caries")
            val fields =
                repo.synthesize(
                    "en",
                    "dis.003",
                    listOf(
                        shifa.doctor.data.model.ClinicalChipSelection("dis.003.morbi.1", mapOf("X" to "3")),
                        shifa.doctor.data.model.ClinicalChipSelection("occ.norm"),
                        shifa.doctor.data.model.ClinicalChipSelection("shared.xray.caries"),
                    ),
                ).getOrThrow()
            assertThat(
                fields,
            ).containsExactly(
                "morbi",
                "Reacting to food for 3 months",
                "occlusion",
                "Orthognathic occlusion",
                "xray",
                "Enamel radiolucency",
            )
            assertThat(repo.recordUsage("dis.003").isSuccess).isTrue()
            assertThat(forwarded).isEmpty()
        }

    @Test
    fun `a demo visit answers with its patient, and its signature arrives 15 s after the request`() =
        runTest {
            val repo = shifa.doctor.data.appointments.AppointmentsRepository({ client })
            val visit = repo.detail(102).getOrThrow()
            assertThat(visit.patientId to visit.patientName).isEqualTo(12L to "Jasur Karimov")
            assertThat(visit.kind).isEqualTo(shifa.doctor.data.model.VisitKind.InPerson)
            assertThat(visit.signature).isEqualTo(shifa.doctor.data.model.SignatureState.None)
            assertThat(repo.requestSignature(102).isSuccess).isTrue()
            assertThat(
                repo.detail(102).getOrThrow().signature,
            ).isEqualTo(shifa.doctor.data.model.SignatureState.Waiting)
            assertThat(repo.detail(999).exceptionOrNull()).isInstanceOf(ApiException.NotFound::class.java)
            assertThat(repo.requestSignature(999).exceptionOrNull()).isInstanceOf(ApiException.NotFound::class.java)
            // Busy days are even: from the 31st (then the 1st) the visit is found two days on.
            assertThat(demoAppointment(101, null, kotlinx.datetime.Instant.parse("2026-12-31T05:00:00Z"))).isNotNull()
            val asked = kotlinx.datetime.Instant.parse("2026-11-04T05:00:00Z")
            val later = demoAppointment(101, asked, asked + kotlin.time.Duration.parse("16s"))!!
            assertThat(later["patientSignedAt"]?.jsonPrimitive?.content).isEqualTo("2026-11-04T05:00:15Z")
            assertThat(forwarded).isEmpty()
        }

    @Test
    fun `a demo visit's scribe draft becomes its note, with the doctor's own, and completing reports both`() =
        runTest {
            val repo = shifa.doctor.data.consultation.ConsultationRepository({ client })
            val draft = repo.drafts(101).getOrThrow().single()
            assertThat(draft.icdSuggestions.single().code).isEqualTo("K04.0")
            assertThat(repo.scribeStatus(101).getOrThrow().hasScribeNote).isFalse()
            assertThat(repo.confirmDraft(draft.id, 11, 101).isSuccess).isTrue()
            assertThat(
                repo.confirmDraft(draft.id, 11, 101).exceptionOrNull(),
            ).isInstanceOf(ApiException.NotFound::class.java)
            assertThat(repo.saveNotes(101, "Review in a few days").isSuccess).isTrue()
            assertThat(repo.saveNotes(101, "Review in a week").isSuccess).isTrue()
            val notes = repo.notes(101).getOrThrow()
            assertThat(notes.map { it.fromAi }).containsExactly(true, false).inOrder()
            assertThat(notes.first().displayText).doesNotContain("TRANSCRIPT")
            assertThat(repo.scribeStatus(101).getOrThrow().hasScribeNote).isTrue()
            val done = repo.complete(101).getOrThrow()
            assertThat(done.hasDocumentation to done.hasScribeNote).isEqualTo(true to true)
            assertThat(repo.drafts(102).getOrThrow()).isEmpty()
            // Completing saves the doctor's notes first (one manual note per visit).
            repo.complete(102, doctorNotes = "Follow up").getOrThrow()
            assertThat(shifa.doctor.data.model.joinedManualNotes(repo.notes(102).getOrThrow())).isEqualTo("Follow up")
            assertThat(forwarded).isEmpty()
        }

    @Test
    fun `the demo patient's active plan, its open lines, and fulfilling one`() =
        runTest {
            val repo = shifa.doctor.data.treatmentplans.TreatmentPlansRepository({ client })
            val plan = repo.plans(1, 12, status = "ACTIVE").getOrThrow().single()
            assertThat(repo.plans(1, 99, status = "ACTIVE").getOrThrow()).isEmpty()
            assertThat(repo.plans(2, 12, status = "ACTIVE").getOrThrow()).isEmpty()
            assertThat(
                repo.planServices(1).getOrThrow().map {
                    it.key
                },
            ).containsExactly("catalog:1", "catalog:2", "doctor:1:service:3")
            val detail = repo.detail(plan.id).getOrThrow()
            assertThat(detail.lines.map { it.tooth }).containsExactly("36", "36", "46", null).inOrder()
            val open = repo.candidates(plan.id, 102).getOrThrow()
            assertThat(open.map { it.lineId }).doesNotContain(13L)
            val after = repo.fulfill(plan.id, 102, listOf(open.first().lineId)).getOrThrow()
            assertThat(after.summary.linesCompletedCount).isEqualTo(2)
            assertThat(repo.candidates(plan.id, 102).getOrThrow()).hasSize(open.size - 1)
            // A payment moves paid and appears in the ledger with its visit.
            val finance = shifa.doctor.data.finance.FinanceRepository({ client })
            finance.recordPayment(
                1,
                shifa.doctor.data.finance.RecordPayment(plan.id, 5_000_000, "UZS", "CASH", 102),
            ).getOrThrow()
            assertThat(repo.detail(plan.id).getOrThrow().summary.paidMinor).isEqualTo(plan.paidMinor + 5_000_000)
            assertThat(finance.payments(1).getOrThrow().single().linkedAppointmentId).isEqualTo(102L)
            // Every line done: the plan is completed, no longer among the active ones.
            repo.fulfill(plan.id, 102, open.drop(1).map { it.lineId }).getOrThrow()
            assertThat(
                repo.detail(plan.id).getOrThrow().summary.status,
            ).isEqualTo(shifa.doctor.data.model.PlanStatus.Completed)
            assertThat(repo.plans(1, 12, status = "ACTIVE").getOrThrow()).isEmpty()
            assertThat(repo.plans(1, 12, status = "COMPLETED").getOrThrow()).hasSize(1)
            assertThat(forwarded).isEmpty()
        }

    @Test
    fun `the demo video visit has a room, the others are refused like the backend`() =
        runTest {
            val repo = shifa.doctor.data.video.VideoRepository({ client })
            assertThat(repo.token(101).getOrThrow().roomName).isEqualTo("shifa-demo")
            assertThat(
                repo.token(102).exceptionOrNull(),
            ).isInstanceOf(shifa.doctor.data.model.ApiException.Forbidden::class.java)
            assertThat(forwarded).isEmpty()
        }

    @Test
    fun `a demo briefing is ready for the video visit, failed for the other until asked again`() =
        runTest {
            val repo = shifa.doctor.data.appointments.AppointmentsRepository({ client })
            assertThat(repo.briefing(101).getOrThrow().state).isEqualTo(shifa.doctor.data.model.BriefingStatus.Ready)
            assertThat(repo.briefing(102).getOrThrow().state).isEqualTo(shifa.doctor.data.model.BriefingStatus.Failed)
            assertThat(
                repo.retryBriefing(102).getOrThrow().state,
            ).isEqualTo(shifa.doctor.data.model.BriefingStatus.Pending)
            val asked = kotlinx.datetime.Instant.parse("2026-11-04T05:00:00Z")
            val later = demoBriefing(102, asked, asked + kotlin.time.Duration.parse("7s"))
            assertThat(later["status"]?.jsonPrimitive?.content).isEqualTo("READY")
            assertThat(forwarded).isEmpty()
        }

    @Test
    fun `demo speech to text answers a fixed sentence and takes reports`() =
        runTest {
            val repo = shifa.doctor.data.speech.TranscriptionRepository({ client })
            val t =
                repo.transcribe(
                    byteArrayOf(1, 2),
                    shifa.doctor.data.storage.AppLanguage.English,
                    "rec-1",
                ).getOrThrow()
            assertThat(t.text).isEqualTo("Pain in the lower left molar for three days.")
            assertThat(t.language).isEqualTo("en")
            assertThat(
                repo.report(t.text, shifa.doctor.data.storage.AppLanguage.English, null, null).isSuccess,
            ).isTrue()
            assertThat(forwarded).isEmpty()
        }

    @Test
    fun `a demo scribe recording gives the visit a draft`() =
        runTest {
            val repo = shifa.doctor.data.consultation.ConsultationRepository({ client })
            assertThat(repo.drafts(102).getOrThrow()).isEmpty()
            assertThat(
                repo.uploadRecording(
                    102,
                    byteArrayOf(1),
                    shifa.doctor.data.storage.AppLanguage.English,
                    "rec-1",
                ).isSuccess,
            ).isTrue()
            val draft = repo.drafts(102).getOrThrow().single()
            assertThat(draft.id).isEqualTo("demo-scribe-102")
            assertThat(repo.confirmDraft(draft.id, 12, 102).isSuccess).isTrue()
            assertThat(
                repo.uploadRecording(
                    999,
                    byteArrayOf(1),
                    shifa.doctor.data.storage.AppLanguage.English,
                    null,
                ).isFailure,
            ).isTrue()
            assertThat(forwarded).isEmpty()
        }

    @Test
    fun `analytics answer locally, today and the trend from the demo calendar`() =
        runTest {
            val trend =
                apiCall<kotlinx.serialization.json.JsonArray> {
                    client.get(ApiConfig.Endpoints.ANALYTICS_TREND) { url.parameters.append("days", "30") }
                }.getOrThrow()
            assertThat(trend).hasSize(30)
            for (e in listOf(
                ApiConfig.Endpoints.ANALYTICS_OVERVIEW,
                ApiConfig.Endpoints.ANALYTICS_TYPES,
                ApiConfig.Endpoints.ANALYTICS_ENGAGEMENT,
                ApiConfig.Endpoints.ANALYTICS_SMS,
            )) {
                assertThat(apiCall<JsonObject> { client.get(e) }.getOrThrow()).isNotEmpty()
            }
            assertThat(forwarded).isEmpty()
        }

    @Test
    fun `the demo chat lists, opens, sends, reads and counts locally`() =
        runTest {
            val repo = shifa.doctor.data.chat.ChatRepository({ client }, { ApiConfig.BASE_URL })
            val list = repo.conversations().getOrThrow()
            assertThat(
                list.map { it.name },
            ).containsExactly("Dilnoza Rahimova", "Jasur Karimov", "Bekzod Aliyev").inOrder()
            val unread = apiCall<JsonObject> { client.get(ApiConfig.Endpoints.CHAT_UNREAD) }.getOrThrow()
            assertThat(unread["count"]!!.jsonPrimitive.content).isEqualTo("3")
            assertThat(repo.thread(801).getOrThrow().messages).hasSize(3)
            repo.send(shifa.doctor.data.model.SendMessageRequest(803, "text", text = "Got it")).getOrThrow()
            assertThat(repo.conversations().getOrThrow().first().id).isEqualTo(803L)
            repo.markRead(801).getOrThrow()
            assertThat(
                apiCall<JsonObject> {
                    client.get(ApiConfig.Endpoints.CHAT_UNREAD)
                }.getOrThrow()["count"]!!.jsonPrimitive.content,
            ).isEqualTo("1")
            assertThat(repo.start(patientId = 13).getOrThrow().name).isEqualTo("Malika Tursunova")
            assertThat(repo.search("jas").getOrThrow().map { it.id }).containsExactly(12L)
            // A colleague gets their own conversation, not a patient's.
            val colleague = repo.start(doctorId = 21).getOrThrow()
            assertThat(colleague.isDoctor).isTrue()
            assertThat(repo.start(doctorId = 21).getOrThrow().id).isEqualTo(colleague.id)
            assertThat(repo.start(patientId = 13).getOrThrow().id).isNotEqualTo(colleague.id)
            assertThat(repo.upload(byteArrayOf(1), "a.jpg", "image/jpeg").getOrThrow()).contains("/demo/")
            assertThat(forwarded).isEmpty()
        }

    @Test
    fun `the demo inbox pages, reads one or all and counts locally`() =
        runTest {
            val repo = shifa.doctor.data.notifications.NotificationsRepository({ client })

            suspend fun count() =
                apiCall<JsonObject> { client.get(ApiConfig.Endpoints.NOTIFICATIONS_UNREAD) }
                    .getOrThrow()["count"]!!
                    .jsonPrimitive.content
            val first = repo.page(0, size = 4).getOrThrow()
            assertThat(first.items.map { it.id }).containsExactly(501L, 502L, 503L, 504L).inOrder()
            assertThat(first.last).isFalse()
            assertThat(repo.page(1, size = 4).getOrThrow().items.first().id).isEqualTo(505L)
            val rest = repo.page(2, size = 4).getOrThrow()
            assertThat(rest.items.map { it.id }).containsExactly(509L)
            assertThat(rest.last).isTrue()
            assertThat(count()).isEqualTo("5")
            repo.markRead(501).getOrThrow()
            assertThat(count()).isEqualTo("4")
            repo.markAllRead().getOrThrow()
            assertThat(count()).isEqualTo("0")
            assertThat(forwarded).isEmpty()
        }

    @Test
    fun `the demo tasks list, filter, open, create and cancel locally`() =
        runTest {
            val repo = shifa.doctor.data.tasks.TasksRepository({ client })
            assertThat(repo.tasks().getOrThrow().map { it.id }).containsExactly(501L, 502L, 503L).inOrder()
            assertThat(repo.tasks(status = shifa.doctor.data.model.TaskStatus.Active).getOrThrow()).hasSize(2)
            assertThat(repo.checkIns(501).getOrThrow()).hasSize(6)
            assertThat(repo.templates().getOrThrow()).hasSize(2)
            val made =
                repo
                    .create(
                        shifa.doctor.data.model.CreateTaskRequest(
                            13,
                            "Weight",
                            "VITAL",
                            1,
                            "2026-11-06",
                            "NUMERIC",
                            false,
                            startTime = "09:00",
                        ),
                    ).getOrThrow()
            assertThat(made.patientName).isEqualTo("Malika Tursunova")
            assertThat(repo.tasks(patientId = 13).getOrThrow().map { it.id }).containsExactly(made.id)
            assertThat(repo.cancel(made.id).getOrThrow().status).isEqualTo(shifa.doctor.data.model.TaskStatus.Cancelled)
            // An id the demo never made answers 404 here; it never reaches the real backend (real patient data).
            assertThat(
                repo.task(999).exceptionOrNull(),
            ).isInstanceOf(shifa.doctor.data.model.ApiException.NotFound::class.java)
            assertThat(
                repo.checkIns(999).exceptionOrNull(),
            ).isInstanceOf(shifa.doctor.data.model.ApiException.NotFound::class.java)
            val chat = shifa.doctor.data.chat.ChatRepository({ client }, { ApiConfig.BASE_URL })
            assertThat(
                chat.thread(999).exceptionOrNull(),
            ).isInstanceOf(shifa.doctor.data.model.ApiException.NotFound::class.java)
            assertThat(forwarded).isEmpty()
        }
}
