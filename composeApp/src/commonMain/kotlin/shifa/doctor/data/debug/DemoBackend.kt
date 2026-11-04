package shifa.doctor.data.debug

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.readBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import shifa.doctor.data.api.ApiConfig.Endpoints
import shifa.doctor.data.api.apiJson
import shifa.doctor.data.api.createHttpEngine
import shifa.doctor.generated.resources.Res
import shifa.doctor.utils.PhoneE164
import shifa.doctor.utils.currentInstant
import shifa.doctor.utils.isDebugBuild
import kotlin.time.Duration.Companion.seconds

/** Debug menu → Demo data: the shared client talks to [DemoBackend.shared]. Kept across launches; debug builds only. */
object DemoMode {
    private const val KEY = "debug_demo_mode"
    private val settings by lazy { Settings() }
    private val _enabled by lazy { MutableStateFlow(isDebugBuild && settings.getBoolean(KEY, false)) }
    val enabled: StateFlow<Boolean> get() = _enabled

    fun set(on: Boolean) {
        settings[KEY] = on && isDebugBuild
        _enabled.value = on && isDebugBuild
    }

    private const val GATE_KEY = "debug_demo_boot_gate"
    private val _gate by lazy {
        MutableStateFlow(
            DemoBootGate.entries.firstOrNull {
                it.name == settings.getStringOrNull(GATE_KEY)
            } ?: DemoBootGate.None,
        )
    }
    val bootGate: StateFlow<DemoBootGate> get() = _gate

    fun setBootGate(gate: DemoBootGate) {
        settings[GATE_KEY] = gate.name
        _gate.value = gate
    }
}

/** Debug menu → Splash gate: what the demo `public/config` asks for, so both BE-04 frames can be tried. */
enum class DemoBootGate { None, Update, Maintenance }

/**
 * A fake backend for the debug menu (CORE-08), so screens can be tried without touching production. Every endpoint
 * the app calls gets a handler here, answered from in-memory data seeded from real development responses
 * (JSON files in `files/demo/`, names and contacts replaced). `auth/login` goes to the real backend (signing in still works); a GET
 * without a handler reads the real backend (reads are safe); any other write is refused, so demo mode never changes
 * production. The data is reseeded on each launch and by [reset].
 * ponytail: one fixed data set; add states when a feature needs one this set doesn't have.
 */
class DemoBackend(
    private val bootGate: () -> DemoBootGate = { DemoMode.bootGate.value },
    passthrough: () -> HttpClient = { HttpClient(createHttpEngine()) },
    private val fixture: suspend (String) -> String = { Res.readBytes("files/demo/$it").decodeToString() },
) {
    private val real by lazy(passthrough)
    private val mutex = Mutex()
    private var me: JsonObject? = null

    private var groups: List<JsonElement> = emptyList()
    private var lastGroupId = 0L
    private var rules: Map<String?, List<JsonElement>> = emptyMap()
    private var periods: List<JsonElement> =
        listOf(
            JsonObject(mapOf("validFrom" to JsonPrimitive("2026-09-01"), "validUntil" to JsonPrimitive("2026-12-31"))),
        )
    private var dates: List<JsonElement> = emptyList()
    private var people: List<JsonElement> =
        listOf(11L to "Dilnoza Rahimova", 12L to "Jasur Karimov", 13L to "Malika Tursunova", 14L to "Bekzod Aliyev")
            .map { (id, name) -> JsonObject(demoPatient(id, name)) }
    private var lastPatientId = 14L

    // Uploaded documents per patient id, newest first, and their files by document id.
    private var uploads: Map<String, List<JsonElement>> = emptyMap()
    private val uploadFiles = mutableMapOf<Long, Pair<ByteArray, String>>()

    // APPT-02: when each visit's signature was asked; the demo patient signs 15 s later.
    // APPT-06: when a briefing was asked again; it is ready 6 s later.
    private val briefingRetried = mutableMapOf<Long, kotlinx.datetime.Instant>()

    // AI-11: each visit's saved notes, and Shifa AI's pending drafts (one for the video visit until saved or dropped).
    private val visitNotes = mutableMapOf<Long, List<JsonObject>>()
    private var visitDrafts = mapOf(DEMO_DRAFT_ID to 101L)

    /** The visit's manual note replaced (the backend keeps one per visit and updates it), else added. */
    private fun upsertManualNote(
        visit: Long,
        text: String,
    ) {
        val notes = visitNotes[visit].orEmpty()
        val manual = notes.indexOfLast { it["source"]?.jsonPrimitive?.content == "MANUAL" }
        val id = notes.getOrNull(manual)?.get("id")?.jsonPrimitive?.longOrNull ?: (500L + visit)
        val note = demoNote(id, text, "MANUAL")
        visitNotes[visit] = if (manual >= 0) notes.toMutableList().also { it[manual] = note } else notes + note
    }

    private val signatureAsked = mutableMapOf<Long, kotlinx.datetime.Instant>()
    private var forms: Map<String, List<JsonElement>> = emptyMap()
    private var lastFormId = 500L
    private var lastDateId = 0L
    private var places: List<JsonElement> = apiJson.parseToJsonElement(DEMO_LOCATIONS).jsonArray.toList()

    private var services: List<JsonElement> = apiJson.parseToJsonElement(DEMO_SERVICES).jsonArray.toList()

    fun engine(): HttpClientEngine =
        MockEngine { request ->
            val raw = if (request.method == HttpMethod.Get) ByteArray(0) else request.body.toByteArray()
            val body = raw.decodeToString()
            val reply = mutex.withLock { route(request, body, raw) }
            val file = reply?.file
            if (file != null) {
                respond(
                    file,
                    reply.status,
                    headersOf(
                        HttpHeaders.ContentType to listOf(reply.fileType),
                        HttpHeaders.ContentDisposition to
                            listOf(
                                "inline; filename=\"demo-document.${reply.fileType.substringAfter(
                                    '/',
                                ).replace("jpeg", "jpg")}\"",
                            ),
                    ),
                )
            } else if (reply != null) {
                respond(
                    reply.body,
                    reply.status,
                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            } else {
                val forwarded =
                    real.request(request.url) {
                        method = request.method
                        request.headers.forEach { name, values ->
                            if (name != HttpHeaders.ContentType && name != HttpHeaders.ContentLength) {
                                values.forEach { headers.append(name, it) }
                            }
                        }
                        setBody(request.body)
                    }
                val type = forwarded.headers[HttpHeaders.ContentType] ?: ContentType.Application.Json.toString()
                respond(forwarded.readBytes(), forwarded.status, headersOf(HttpHeaders.ContentType, type))
            }
        }

    suspend fun reset() = mutex.withLock { me = null }

    private class Reply(
        val status: HttpStatusCode,
        val body: String,
        // A file answer (a document download), sent instead of [body].
        val file: ByteArray? = null,
        val fileType: String = ContentType.Application.Pdf.toString(),
    )

    private suspend fun route(
        request: HttpRequestData,
        body: String,
        raw: ByteArray,
    ): Reply? {
        val get = request.method == HttpMethod.Get
        // The API host is the chosen backend's (production or development); other hosts are files.
        val api =
            BackendChoice.Backend.entries.map { Url(it.baseUrl) }.firstOrNull { it.host == request.url.host }
                ?: return if (get) null else refused()
        val path = request.url.encodedPath.removePrefix(api.encodedPath)
        // Only signing in reaches the real backend (it returns the token the real GETs need). Every other auth call
        // writes (register, OTP, password reset), so it is refused like any other write.
        if (path == Endpoints.LOGIN) return null
        return when {
            get && path == Endpoints.DOCTOR_ME -> ok(doctorMe())
            get && path == Endpoints.PUBLIC_CONFIG -> ok(publicConfig(bootGate()))
            request.method == HttpMethod.Put && path == Endpoints.DOCTOR_LANGUAGE -> setLanguage(body)
            request.method == HttpMethod.Post && path == Endpoints.VERIFY_KEY -> ok(verifyKey(body))
            request.method == HttpMethod.Post && path == Endpoints.CHECK_EXISTING_DOCTOR -> existingDoctor(body)
            request.method == HttpMethod.Post && path == Endpoints.CHECK_EXISTING_PATIENT -> ok(existingPatient(body))
            get && path == Endpoints.PUBLIC_PROFESSIONS -> ok(apiJson.parseToJsonElement(DEMO_PROFESSIONS))
            get && path == Endpoints.PUBLIC_CLINICS -> ok(clinics(request.url.parameters["search"]))
            request.method == HttpMethod.Patch && path == Endpoints.DOCTOR_PROFILE -> merge("profile", body)
            request.method == HttpMethod.Patch && path == Endpoints.DOCTOR_CONTACT ->
                merge(
                    "contact",
                    body,
                    replace = true,
                )
            request.method == HttpMethod.Patch && path == Endpoints.DOCTOR_BILLING ->
                merge(
                    "billing",
                    body,
                    replace = true,
                )
            request.method == HttpMethod.Patch && path == Endpoints.DOCTOR_SETTINGS ->
                merge(
                    "settings",
                    body,
                    replace = true,
                )
            request.method == HttpMethod.Post && path == Endpoints.DOCTOR_PHOTO -> photo()
            request.method == HttpMethod.Post && path == Endpoints.DOCTOR_CERTIFICATE -> certificate()
            // The demo password is the seed accounts' one; anything else is "current password is incorrect".
            request.method == HttpMethod.Post && path == Endpoints.RESET_PASSWORD ->
                if (jsonField(body, "currentPassword") == DEMO_PASSWORD) {
                    ok(JsonObject(mapOf("message" to JsonPrimitive("Password updated successfully"))))
                } else {
                    fail(HttpStatusCode.BadRequest, "BAD_REQUEST")
                }
            get && path == Endpoints.ME_PROFILE -> ok(meProfile())
            get && path == Endpoints.ME_CLINICS -> ok(apiJson.parseToJsonElement(DEMO_CLINICS))
            // Fixed counts so the badges show; the lists behind them come with their features.
            get && path == Endpoints.CHAT_UNREAD -> ok(apiJson.parseToJsonElement("""{"count":3}"""))
            get && path == Endpoints.NOTIFICATIONS_UNREAD -> ok(apiJson.parseToJsonElement("""{"count":12}"""))
            get && path == Endpoints.TASKS -> ok(apiJson.parseToJsonElement("[]"))
            // Services kept in memory (SVC-01); the location list until LOC-1.
            get && path == Endpoints.DOCTOR_SERVICES -> ok(JsonArray(services))
            request.method == HttpMethod.Post && path == Endpoints.DOCTOR_SERVICES -> saveService(null, body)
            request.method == HttpMethod.Patch && path.startsWith(Endpoints.DOCTOR_SERVICES + "/") ->
                saveService(path.substringAfterLast('/').toLongOrNull(), body)
            request.method == HttpMethod.Delete && path.startsWith(Endpoints.DOCTOR_SERVICES + "/") -> {
                val id = path.substringAfterLast('/').toLongOrNull()
                services = services.filterNot { it.jsonObject["id"]?.jsonPrimitive?.content == id.toString() }
                Reply(HttpStatusCode.NoContent, "")
            }
            get && path == Endpoints.SERVICE_GROUPS -> ok(JsonArray(groups))
            // Plans like dev's; the hosted pages are placeholders (nothing reaches Stripe in demo mode).
            get && path == Endpoints.CALENDAR -> ok(demoCalendar(request.url.parameters["day"]))
            get && path == Endpoints.SUBSCRIPTION_PLANS -> ok(apiJson.parseToJsonElement(DEMO_PLANS))
            // The demo doctor's plan is admin-set: no paid subscription.
            get && path == Endpoints.SUBSCRIPTION_ME -> ok(JsonNull)
            request.method == HttpMethod.Post && path == Endpoints.SUBSCRIPTION_CHECKOUT ->
                ok(
                    apiJson.parseToJsonElement(
                        """{"checkoutUrl":"https://example.com/shifa-demo-checkout","sessionId":"demo"}""",
                    ),
                )
            request.method == HttpMethod.Post && path == Endpoints.STRIPE_CONNECT_ONBOARD ->
                ok(
                    apiJson.parseToJsonElement(
                        """{"accountId":"acct_demo0001","onboardingUrl":"https://example.com/shifa-demo-onboarding"}""",
                    ),
                )
            request.method == HttpMethod.Post && path == Endpoints.SERVICE_GROUPS -> saveGroup(null, body)
            request.method == HttpMethod.Patch && path.startsWith(Endpoints.SERVICE_GROUPS + "/") ->
                saveGroup(path.substringAfterLast('/').toLongOrNull(), body)
            request.method == HttpMethod.Delete && path.startsWith(Endpoints.SERVICE_GROUPS + "/") -> {
                val id = path.substringAfterLast('/')
                groups = groups.filterNot { it.jsonObject["id"]?.jsonPrimitive?.content == id }
                // Like the backend's ON DELETE SET NULL: its services become ungrouped.
                services =
                    services.map { svc ->
                        val o = svc.jsonObject
                        if (o["groupId"]?.jsonPrimitive?.content == id) {
                            JsonObject(
                                o + ("groupId" to JsonNull) + ("groupName" to JsonNull),
                            )
                        } else {
                            svc
                        }
                    }
                Reply(HttpStatusCode.NoContent, "")
            }
            // Blocks (CAL-8): the count from the generated days; creating and removing answer without storing.
            get && path == Endpoints.SCHEDULE_BLOCKS_OVERLAP ->
                ok(
                    JsonObject(
                        mapOf(
                            "count" to
                                JsonPrimitive(
                                    demoOverlap(request.url.parameters["startAt"], request.url.parameters["endAt"]),
                                ),
                        ),
                    ),
                )
            request.method == HttpMethod.Post && path == Endpoints.SCHEDULE_BLOCKS -> {
                val sent = apiJson.parseToJsonElement(body).jsonObject
                val cancel = sent["cancelOverlappingAppointments"]?.jsonPrimitive?.content == "true"
                val n =
                    if (cancel) {
                        demoOverlap(
                            sent["startAt"]?.jsonPrimitive?.content,
                            sent["endAt"]?.jsonPrimitive?.content,
                        )
                    } else {
                        0
                    }
                ok(JsonObject(sent + ("id" to JsonPrimitive(9001)) + ("cancelledAppointmentCount" to JsonPrimitive(n))))
            }
            request.method == HttpMethod.Delete &&
                path.startsWith(
                    Endpoints.SCHEDULE_BLOCKS + "/",
                )
            -> Reply(HttpStatusCode.OK, "")
            // Patients (PAT-02) in memory: the directory, one patient, create, edit, their appointments, account, SMS.
            get && path == Endpoints.PATIENTS ->
                ok(JsonArray(if ((request.url.parameters["page"] ?: "0") == "0") people else emptyList()))
            get && path.matches(Regex("patients/\\d+")) ->
                people.firstOrNull {
                    it.jsonObject["id"]?.jsonPrimitive?.content ==
                        path.substringAfterLast(
                            '/',
                        )
                }?.let(::ok)
                    ?: fail(HttpStatusCode.NotFound, "NOT_FOUND")
            request.method == HttpMethod.Post && path == Endpoints.PATIENTS -> {
                val saved = JsonObject(demoPatient(++lastPatientId, "") + apiJson.parseToJsonElement(body).jsonObject)
                people = people + saved
                ok(saved)
            }
            request.method == HttpMethod.Patch && path.matches(Regex("patients/\\d+")) -> {
                val id = path.substringAfterLast('/')
                val old =
                    people.firstOrNull { it.jsonObject["id"]?.jsonPrimitive?.content == id }?.jsonObject
                        ?: return fail(HttpStatusCode.NotFound, "NOT_FOUND")
                val saved =
                    JsonObject(old + apiJson.parseToJsonElement(body).jsonObject.filterValues { it !is JsonNull })
                people = people.map { if (it.jsonObject["id"]?.jsonPrimitive?.content == id) saved else it }
                ok(saved)
            }
            get && path.matches(Regex("patients/\\d+/appointments")) -> ok(JsonArray(emptyList()))
            // Documents (DOCS-01): a shared blood test anyone can open, and the patient's own ECG, locked (404 to download).
            get && path.matches(Regex("patients/\\d+/documents")) ->
                ok(
                    JsonArray(
                        uploads[path.split('/')[1]].orEmpty() + apiJson.parseToJsonElement(DEMO_DOCUMENTS).jsonArray,
                    ),
                )
            // An upload (DOCS-03) joins the list on top, named and typed from its form fields.
            request.method == HttpMethod.Post && path.matches(Regex("patients/\\d+/documents")) -> {
                // A part's headers (name quoted or not, then any others), a blank line, the value.
                fun field(name: String) =
                    Regex("name=\"?$name\"?(?:\\r\\n[^\\r\\n]+)*\\r\\n\\r\\n([^\\r]*)").find(body)?.groupValues?.get(1)
                val id = 1000L + uploadFiles.size
                filePart(raw)?.let { uploadFiles[id] = it }
                val saved =
                    JsonObject(
                        mapOf(
                            "id" to JsonPrimitive(id),
                            "title" to JsonPrimitive(field("title") ?: "Document"),
                            // The server's day (UTC), as the backend stamps an upload.
                            "date" to JsonPrimitive(currentInstant().toString().take(10)),
                            "url" to JsonPrimitive("demo"),
                            "canView" to JsonPrimitive(true),
                            "category" to JsonPrimitive(field("category")),
                            "creatorLabel" to JsonPrimitive("Demo Doctor"),
                            "creatorType" to JsonPrimitive("DOCTOR"),
                        ),
                    )
                val patient = path.split('/')[1]
                uploads = uploads + (patient to listOf(saved) + uploads[patient].orEmpty())
                ok(saved)
            }
            get && path.matches(Regex("patients/\\d+/documents/\\d+/download")) ->
                when (val id = path.split('/')[3].toLong()) {
                    902L -> Reply(HttpStatusCode.NotFound, "")
                    // An upload opens as what was uploaded.
                    in uploadFiles ->
                        uploadFiles.getValue(id).let {
                                (bytes, type) ->
                            Reply(HttpStatusCode.OK, "", bytes, type)
                        }
                    else -> Reply(HttpStatusCode.OK, "", demoPdf())
                }
            // A re-saved form's new PDF replaces the document's file (FORM-02).
            request.method == HttpMethod.Put && path.matches(Regex("patients/\\d+/documents/\\d+")) -> {
                val id = path.substringAfterLast('/').toLong()
                filePart(raw)?.let { uploadFiles[id] = it }
                ok(
                    JsonObject(
                        mapOf(
                            "id" to JsonPrimitive(id),
                            "title" to JsonPrimitive("Document"),
                            "canView" to JsonPrimitive(true),
                        ),
                    ),
                )
            }
            // Forms (FORM-02) in memory per patient: list, create, update, the signature request, the PDF link.
            get && path.matches(Regex("patients/\\d+/forms")) -> ok(JsonArray(forms[path.split('/')[1]].orEmpty()))
            request.method == HttpMethod.Post && path.matches(Regex("patients/\\d+/forms")) -> {
                val patient = path.split('/')[1]
                val saved =
                    JsonObject(apiJson.parseToJsonElement(body).jsonObject + ("id" to JsonPrimitive(++lastFormId)))
                forms = forms + (patient to forms[patient].orEmpty() + saved)
                ok(saved)
            }
            path.matches(Regex("patients/\\d+/forms/\\d+(/request-signature|/link-document)?")) && !get -> {
                val parts = path.split('/')
                val (patient, formId) = parts[1] to parts[3]
                val old =
                    forms[patient]
                        .orEmpty()
                        .firstOrNull { it.jsonObject["id"]?.jsonPrimitive?.content == formId }
                        ?.jsonObject ?: return fail(HttpStatusCode.NotFound, "NOT_FOUND")
                val change =
                    when (parts.getOrNull(4)) {
                        "request-signature" -> mapOf("signatureRequested" to JsonPrimitive(true))
                        "link-document" ->
                            mapOf(
                                "documentId" to JsonPrimitive(request.url.parameters["documentId"]?.toLongOrNull()),
                            )
                        // A save replaces what the form holds (an emptied chart or list is left out of the body):
                        // only the server's own fields carry over.
                        else ->
                            old.filterKeys { it in FORM_SERVER_FIELDS } + apiJson.parseToJsonElement(body).jsonObject
                    }
                val saved = JsonObject(if (parts.getOrNull(4) == null) change else old + change)
                forms = forms + (patient to forms[patient].orEmpty().map { if (it == old) saved else it })
                ok(saved)
            }
            // Clinical engine (CLIN-01): one group, two diseases with chips, occlusion and shared texts; synthesize
            // joins the picked chips' texts per field (with their [X] filled in), as the backend's result reads.
            path.startsWith(
                Endpoints.CLINICAL_ENGINE + "/",
            ) -> clinical(path.removePrefix(Endpoints.CLINICAL_ENGINE + "/"), request, body)
            get && path == Endpoints.ICD10_SEARCH -> {
                val q = request.url.parameters["q"].orEmpty().trim().lowercase()
                ok(
                    JsonArray(
                        apiJson.parseToJsonElement(DEMO_ICD10).jsonArray.filter { q in it.toString().lowercase() },
                    ),
                )
            }
            request.method == HttpMethod.Post && path.matches(Regex("patients/\\d+/documents/\\d+/request-access")) ->
                ok(JsonObject(emptyMap()))
            request.method == HttpMethod.Post && path.matches(Regex("patients/\\d+/create-account")) ->
                ok(
                    JsonObject(
                        mapOf(
                            "username" to JsonPrimitive("demo.patient"),
                            "oneTimePassword" to JsonPrimitive("Demo-1234"),
                        ),
                    ),
                )
            request.method == HttpMethod.Post &&
                path.matches(
                    Regex("patients/\\d+/send-test-sms"),
                )
            -> Reply(HttpStatusCode.OK, "")
            get && path == Endpoints.PROPHYLAXIS_SETTINGS -> Reply(HttpStatusCode.OK, "")
            request.method == HttpMethod.Put && path == Endpoints.PROPHYLAXIS_SETTINGS ->
                ok(
                    apiJson.parseToJsonElement(body),
                )
            // The booking list (CAL-7): the demo calendar's patients and a few more, one page.
            get && path == Endpoints.PATIENTS_FOR_ASSIGNMENT -> ok(apiJson.parseToJsonElement(DEMO_PATIENTS))
            request.method == HttpMethod.Post && path == Endpoints.SCHEDULE_BOOK -> Reply(HttpStatusCode.OK, "")
            // A visit (APPT-02): the demo calendar's appointment on the next busy day, its signature as asked.
            get && path.matches(Regex("appointments/\\d+")) ->
                demoAppointment(path.substringAfter('/').toLong(), signatureAsked[path.substringAfter('/').toLong()])
                    ?.let { ok(it) } ?: Reply(HttpStatusCode.NotFound, """{"status":404,"code":"NOT_FOUND"}""")
            request.method == HttpMethod.Put && path.matches(Regex("appointments/\\d+/request-signature")) -> {
                val id = path.split('/')[1].toLong()
                // An unknown visit is 404, as the backend answers.
                if (demoAppointment(id, null) == null) {
                    Reply(HttpStatusCode.NotFound, """{"status":404,"code":"NOT_FOUND"}""")
                } else {
                    signatureAsked.getOrPut(id) { currentInstant() }
                    Reply(HttpStatusCode.OK, "")
                }
            }
            // The visit briefing (APPT-06): ready for the video visit, failed for the clinic one until asked again.
            get && path.matches(Regex("appointments/\\d+/briefing")) ->
                ok(demoBriefing(path.split('/')[1].toLong(), briefingRetried[path.split('/')[1].toLong()]))
            request.method == HttpMethod.Post && path.matches(Regex("appointments/\\d+/briefing/retry")) -> {
                val id = path.split('/')[1].toLong()
                briefingRetried[id] = currentInstant()
                ok(demoBriefing(id, briefingRetried[id]))
            }
            // A visit's notes (AI-11).
            path.matches(Regex("appointments/\\d+/consultation-notes")) -> {
                val id = path.split('/')[1].toLong()
                if (!get) {
                    val text =
                        runCatching { apiJson.parseToJsonElement(body).jsonObject["body"]?.jsonPrimitive?.content }
                            .getOrNull()
                    if (!text.isNullOrBlank()) upsertManualNote(id, text.trim())
                    Reply(HttpStatusCode.OK, "")
                } else {
                    ok(JsonArray(visitNotes[id].orEmpty()))
                }
            }
            get && path.matches(Regex("appointments/\\d+/draft-notes")) ->
                ok(
                    JsonArray(
                        visitDrafts.filterValues { it == path.split('/')[1].toLong() }.keys.map {
                            apiJson.parseToJsonElement(DEMO_DRAFT)
                        },
                    ),
                )
            path.matches(Regex("ai/draft/[^/]+/(confirm|discard)")) && !get -> {
                val draft = path.split('/')[2]
                val visit = visitDrafts[draft]
                if (visit == null) {
                    Reply(HttpStatusCode.NotFound, """{"status":404,"code":"NOT_FOUND"}""")
                } else {
                    visitDrafts = visitDrafts - draft
                    if (path.endsWith("confirm")) {
                        val text = apiJson.parseToJsonElement(DEMO_DRAFT).jsonObject["body"]?.jsonPrimitive?.content
                        visitNotes[visit] = visitNotes[visit].orEmpty() + demoNote(900L, text, "AI_DRAFT")
                    }
                    Reply(HttpStatusCode.OK, """{"success":true}""")
                }
            }
            get && path.matches(Regex("appointments/\\d+/scribe-status")) -> {
                val ai =
                    visitNotes[
                        path.split(
                            '/',
                        )[1].toLong(),
                    ].orEmpty().any { it["source"]?.jsonPrimitive?.content == "AI_DRAFT" }
                ok(
                    JsonObject(
                        mapOf(
                            "status" to JsonPrimitive(if (ai) "ready" else "none"),
                            "hasDocumentation" to JsonPrimitive(ai),
                            "hasScribeNote" to JsonPrimitive(ai),
                        ),
                    ),
                )
            }
            request.method == HttpMethod.Put && path.matches(Regex("appointments/\\d+/complete")) -> {
                val id = path.split('/')[1].toLong()
                // The doctor's notes are saved first, as the backend does.
                runCatching { apiJson.parseToJsonElement(body).jsonObject["doctorNotes"]?.jsonPrimitive?.content }
                    .getOrNull()
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { upsertManualNote(id, it) }
                val notes = visitNotes[id].orEmpty()
                val ai = notes.any { it["source"]?.jsonPrimitive?.content == "AI_DRAFT" }
                ok(
                    JsonObject(
                        mapOf(
                            "status" to JsonPrimitive("COMPLETED"),
                            "scribePending" to JsonPrimitive(false),
                            "hasDocumentation" to JsonPrimitive(notes.isNotEmpty()),
                            "hasScribeNote" to JsonPrimitive(ai),
                        ),
                    ),
                )
            }
            // Appointment actions (CAL-6) succeed without changing the generated days: nothing is stored.
            path.startsWith("appointments/") && !get &&
                (
                    request.method == HttpMethod.Delete ||
                        path.endsWith("/change") ||
                        path.endsWith("/notify-payment-reminder")
                ) -> Reply(HttpStatusCode.OK, "")
            // The schedule (SCHED-1) in memory: rules per location, validity periods, date-specific hours.
            get && path == Endpoints.SCHEDULE_RULES ->
                ok(
                    JsonArray(
                        rules[request.url.parameters["locationId"]] ?: demoRules(request.url.parameters["locationId"]),
                    ),
                )
            request.method == HttpMethod.Put && path == Endpoints.SCHEDULE_RULES -> {
                rules = rules + (
                    request.url.parameters["locationId"] to
                        apiJson.parseToJsonElement(
                            body,
                        ).jsonArray.toList()
                )
                ok(apiJson.parseToJsonElement(body))
            }
            get && path == Endpoints.SCHEDULE_VALIDITY_PERIODS -> ok(JsonArray(periods))
            request.method == HttpMethod.Patch && path == Endpoints.SCHEDULE_VALID_RANGE -> {
                val sent = apiJson.parseToJsonElement(body).jsonObject
                val inside =
                    periods.any {
                        val o = it.jsonObject
                        sent["validFrom"]!!.jsonPrimitive.content >= o["validFrom"]!!.jsonPrimitive.content &&
                            sent["validUntil"]!!.jsonPrimitive.content <= o["validUntil"]!!.jsonPrimitive.content
                    }
                if (!inside) periods = periods + sent
                ok(sent)
            }
            // Without locationId every location's rows, like the backend.
            get && path == Endpoints.SCHEDULE_DATE_SPECIFIC -> {
                val at = request.url.parameters["locationId"]
                ok(JsonArray(dates.filter { at == null || it.jsonObject["locationId"]?.jsonPrimitive?.content == at }))
            }
            request.method == HttpMethod.Post && path == Endpoints.SCHEDULE_DATE_SPECIFIC -> {
                val saved =
                    JsonObject(apiJson.parseToJsonElement(body).jsonObject + ("id" to JsonPrimitive(++lastDateId)))
                dates = dates + saved
                ok(saved)
            }
            request.method == HttpMethod.Delete && path.startsWith(Endpoints.SCHEDULE_DATE_SPECIFIC + "/") -> {
                dates = dates.filterNot { it.jsonObject["id"]?.jsonPrimitive?.content == path.substringAfterLast('/') }
                Reply(HttpStatusCode.OK, "")
            }
            // Locations (LOC-1) in memory: one primary, the last one can't be deleted (the backend's 409).
            get && path == Endpoints.DOCTOR_LOCATIONS -> ok(JsonArray(places))
            request.method == HttpMethod.Post && path == Endpoints.DOCTOR_LOCATIONS -> savePlace(null, body)
            request.method == HttpMethod.Patch && path.startsWith(Endpoints.DOCTOR_LOCATIONS + "/") ->
                savePlace(path.substringAfterLast('/').toLongOrNull(), body)
            request.method == HttpMethod.Delete && path.startsWith(Endpoints.DOCTOR_LOCATIONS + "/") -> {
                val id = path.substringAfterLast('/')
                if (places.size <= 1) return fail(HttpStatusCode.Conflict, "CONFLICT")
                val gone = places.firstOrNull { it.jsonObject["id"]?.jsonPrimitive?.content == id }
                places = places - listOfNotNull(gone)
                if (gone?.jsonObject?.get("isPrimary")?.jsonPrimitive?.content == "true") {
                    places = listOf(
                        JsonObject(places.first().jsonObject + ("isPrimary" to JsonPrimitive(true))),
                    ) + places.drop(1)
                }
                Reply(HttpStatusCode.NoContent, "")
            }
            get -> null
            else -> refused()
        }
    }

    // The dev answer (googleMapsApiKey removed: D-06) plus the BE-04 block the debug menu asks for.
    private fun publicConfig(gate: DemoBootGate): JsonObject {
        val block =
            when (gate) {
                DemoBootGate.None -> null
                DemoBootGate.Update ->
                    """{"minVersion":{"android":"99.0.0","ios":"99.0.0"},
                    "storeUrl":{"android":"https://play.google.com/store/apps/details?id=com.shifa.doc"}}"""
                DemoBootGate.Maintenance -> """{"maintenance":{"active":true,"message":{}}}"""
            }
        val base = """{"transcriptionFeedbackEnabled":"false"${block?.let { ""","doctorApp":$it""" } ?: ""}}"""
        return apiJson.parseToJsonElement(base).jsonObject
    }

    private suspend fun doctorMe(): JsonObject =
        me ?: apiJson.parseToJsonElement(
            fixture("doctors-me.json"),
        ).jsonObject.also {
            me = it
        }

    // Checking a key writes nothing. DEMO-DOCTOR and DEMO-RECEPTION open the two registrations; any other key is invalid.
    private fun verifyKey(body: String): JsonObject {
        val key = jsonField(body, "key")
        val answer =
            when (key?.trim()?.uppercase()) {
                DEMO_DOCTOR_KEY -> """{"valid":true}""" // a doctor invitation has no purpose
                DEMO_RECEPTION_KEY ->
                    """{"valid":true,"purpose":"CLINIC_RECEPTIONIST_INVITE","clinicId":1,
                    "clinicName":"Demo Clinic","emailSentTo":"demo.reception@shifa.dev"}"""
                else -> """{"valid":false}"""
            }
        return apiJson.parseToJsonElement(answer).jsonObject
    }

    // The registration checks only read: the demo doctor's address has a doctor account, DEMO_PATIENT_EMAIL a patient
    // one, any other address neither. `register` itself writes, so it is refused.
    private suspend fun existingDoctor(body: String): Reply {
        val email = jsonField(body, "email")?.trim()?.lowercase()
        val phone = jsonField(body, "phone")?.let(PhoneE164::normalize)
        val contact = doctorMe()["contact"]?.jsonObject
        val demoEmail = contact?.get("email")?.jsonPrimitive?.content?.lowercase()
        val demoPhone = contact?.get("phone")?.jsonPrimitive?.content?.let(PhoneE164::normalize)
        return if ((email != null && email == demoEmail) || (phone != null && phone == demoPhone)) {
            ok(apiJson.parseToJsonElement("""{"firstName":"Aziza","lastName":"Demo"}"""))
        } else {
            fail(HttpStatusCode.NotFound, "NOT_FOUND")
        }
    }

    private fun existingPatient(body: String): JsonObject {
        val found = jsonField(body, "email")?.trim()?.lowercase() == DEMO_PATIENT_EMAIL
        val answer =
            if (found) {
                """{"found":true,"fullName":"Dilnoza Demo","email":"$DEMO_PATIENT_EMAIL"}"""
            } else {
                """{"found":false}"""
            }
        return apiJson.parseToJsonElement(answer).jsonObject
    }

    private fun clinics(search: String?): JsonElement {
        val all = listOf(1 to "Shifa Demo Clinic", 2 to "Tashkent Demo Dental", 3 to "Samarkand Demo Centre")
        val hits = all.filter { search == null || it.second.contains(search, ignoreCase = true) }
        return apiJson.parseToJsonElement(
            hits.joinToString(",", "[", "]") { """{"id":${it.first},"name":"${it.second}"}""" },
        )
    }

    // Like the backend: the profile PATCH is partial (keys sent are merged), contact, billing and settings replace the
    // whole block (an omitted nullable key is cleared). The block is returned like the backend's DTO.
    private suspend fun merge(
        block: String,
        body: String,
        replace: Boolean = false,
    ): Reply {
        val patch =
            runCatching { apiJson.parseToJsonElement(body).jsonObject }.getOrNull()
                ?: return fail(HttpStatusCode.BadRequest, "VALIDATION")
        val current = doctorMe()
        val merged = if (replace) patch else JsonObject((current[block]?.jsonObject ?: JsonObject(emptyMap())) + patch)
        me = JsonObject(current + (block to merged))
        return ok(merged)
    }

    /** The upload itself isn't kept: the demo answers with a picture URL that 404s, so the brand fallback shows. */
    private suspend fun photo(): Reply {
        val url = JsonPrimitive("https://api-dev.shifa.uz/files/doctors/demo-photo.jpg")
        val current = doctorMe()
        val profile = current["profile"]?.jsonObject ?: JsonObject(emptyMap())
        me = JsonObject(current + ("profile" to JsonObject(profile + ("photoUrl" to url))))
        return ok(JsonObject(mapOf("photoUrl" to url)))
    }

    /** Create or merge a location; a new primary demotes the others; primary first, then by id (the backend's order). */
    private fun savePlace(
        id: Long?,
        body: String,
    ): Reply {
        val sent = apiJson.parseToJsonElement(body).jsonObject
        val old = places.firstOrNull { it.jsonObject["id"]?.jsonPrimitive?.content == id?.toString() }?.jsonObject
        if (id != null && old == null) return fail(HttpStatusCode.NotFound, "NOT_FOUND")
        val lastId = places.maxOfOrNull { it.jsonObject["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0 } ?: 0
        val newId = id ?: (lastId + 1)
        val primary =
            sent["isPrimary"]?.jsonPrimitive?.content == "true" || places.isEmpty() ||
                old?.get("isPrimary")?.jsonPrimitive?.content == "true"
        // Blank clears, a missing key keeps (the backend's PATCH).
        val merged =
            (old.orEmpty() + sent.filterValues { it !is JsonNull }).mapValues { (_, v) ->
                if (v is JsonPrimitive && v.isString && v.content.isBlank()) JsonNull else v
            } + ("id" to JsonPrimitive(newId)) + ("isPrimary" to JsonPrimitive(primary))
        val saved = JsonObject(merged)
        places =
            (
                places.filterNot { it.jsonObject["id"]?.jsonPrimitive?.content == newId.toString() }.map {
                    if (primary) JsonObject(it.jsonObject + ("isPrimary" to JsonPrimitive(false))) else it
                } + saved
            ).sortedWith(
                compareBy({
                    it.jsonObject["isPrimary"]?.jsonPrimitive?.content != "true"
                }, { it.jsonObject["id"]?.jsonPrimitive?.content?.toLongOrNull() }),
            )
        return ok(saved)
    }

    /** Create ([id] null) or rename / reorder a group. */
    private fun saveGroup(
        id: Long?,
        body: String,
    ): Reply {
        val sent =
            runCatching {
                apiJson.parseToJsonElement(body).jsonObject
            }.getOrNull() ?: return fail(HttpStatusCode.BadRequest, "VALIDATION")
        // Ids never come back after a delete, like a database sequence.
        val newId = id ?: ++lastGroupId
        val saved = JsonObject(sent + ("id" to JsonPrimitive(newId)))
        groups =
            if (id == null) {
                groups + saved
            } else {
                groups.map {
                    if (it.jsonObject["id"]?.jsonPrimitive?.content == id.toString()) saved else it
                }
            }
        return ok(saved)
    }

    /** Create ([id] null) or replace a service, answering it with its id like the backend. */
    private fun saveService(
        id: Long?,
        body: String,
    ): Reply {
        val sent =
            runCatching {
                apiJson.parseToJsonElement(body).jsonObject
            }.getOrNull() ?: return fail(HttpStatusCode.BadRequest, "VALIDATION")
        val newId =
            id ?: (
                (
                    services.mapNotNull {
                        it.jsonObject["id"]?.jsonPrimitive?.content?.toLongOrNull()
                    }.maxOrNull() ?: 0
                ) + 1
            )
        val saved = JsonObject(sent + ("id" to JsonPrimitive(newId)) + ("clinicManaged" to JsonPrimitive(false)))
        services =
            if (id == null) {
                services + saved
            } else {
                services.map {
                    if (it.jsonObject["id"]?.jsonPrimitive?.content == id.toString()) saved else it
                }
            }
        return ok(saved)
    }

    /** Like the backend: the new file's URL joins the profile's certificate list (a JSON array in a string). */
    private suspend fun certificate(): Reply {
        val current = doctorMe()
        val profile = current["profile"]?.jsonObject ?: JsonObject(emptyMap())
        val list =
            runCatching {
                apiJson.decodeFromString<List<String>>(
                    profile["certificates"]?.jsonPrimitive?.content ?: "[]",
                )
            }
                .getOrDefault(emptyList())
        val url = "https://api-dev.shifa.uz/certificates/demo-${list.size + 1}.pdf"
        val json = apiJson.encodeToString(ListSerializer(String.serializer()), list + url)
        me = JsonObject(current + ("profile" to JsonObject(profile + ("certificates" to JsonPrimitive(json)))))
        return ok(JsonObject(mapOf("photoUrl" to JsonPrimitive(url))))
    }

    private suspend fun meProfile(): JsonObject {
        val current = doctorMe()
        val profile = current["profile"]?.jsonObject ?: JsonObject(emptyMap())
        val contact = current["contact"]?.jsonObject ?: JsonObject(emptyMap())
        val keep = listOf("id", "firstName", "lastName", "photoUrl", "timeZone")
        return JsonObject(
            profile.filterKeys { it in keep } + contact + ("role" to JsonPrimitive("DOCTOR")),
        )
    }

    private fun jsonField(
        body: String,
        name: String,
    ) = runCatching { apiJson.parseToJsonElement(body).jsonObject[name]?.jsonPrimitive?.content }.getOrNull()

    private suspend fun setLanguage(body: String): Reply {
        val language =
            runCatching { apiJson.parseToJsonElement(body).jsonObject["language"]?.jsonPrimitive?.content }.getOrNull()
                ?: return fail(HttpStatusCode.BadRequest, "VALIDATION")
        val current = doctorMe()
        val settings = JsonObject(current["settings"]!!.jsonObject + ("language" to JsonPrimitive(language)))
        me = JsonObject(current + ("settings" to settings))
        return Reply(HttpStatusCode.NoContent, "")
    }

    private fun clinical(
        path: String,
        request: HttpRequestData,
        body: String,
    ): Reply {
        val engine = apiJson.parseToJsonElement(DEMO_CLINICAL).jsonObject
        val diseases = engine.getValue("diseases").jsonArray

        fun chips() = diseases.flatMap { it.jsonObject.getValue("chips").jsonArray }
        return when {
            path == "groups" -> ok(engine.getValue("groups"))
            path.matches(Regex("groups/[^/]+/diseases")) ->
                ok(
                    JsonArray(
                        diseases.map {
                            JsonObject(it.jsonObject - "chips")
                        }.filter { it["groupId"]?.jsonPrimitive?.content == path.split('/')[1] },
                    ),
                )
            path.startsWith("diseases/") ->
                diseases.firstOrNull {
                    it.jsonObject["diseaseId"]?.jsonPrimitive?.content ==
                        path.substringAfter(
                            '/',
                        )
                }?.let(::ok)
                    ?: fail(HttpStatusCode.NotFound, "NOT_FOUND")
            path == "doctors/me/top-diagnoses" -> ok(JsonArray(emptyList()))
            path == "chips/search" -> {
                val q = request.url.parameters["q"].orEmpty().lowercase()
                ok(
                    JsonArray(
                        chips().filter {
                            q.isNotBlank() && q in
                                it.jsonObject.getValue(
                                    "labels",
                                ).toString().lowercase()
                        },
                    ),
                )
            }
            path == "occlusion/chips" -> ok(engine.getValue("occlusion"))
            path.startsWith("shared/") ->
                ok(
                    JsonArray(
                        engine.getValue("shared").jsonArray.filter {
                            it.jsonObject["type"]?.jsonPrimitive?.content == path.substringAfter('/')
                        },
                    ),
                )
            path == "synthesize" -> {
                val sent = apiJson.parseToJsonElement(body).jsonObject
                val locale = sent["locale"]?.jsonPrimitive?.content ?: "ru"
                // Shared texts are picked as chips with a `shared.<templateId>` id.
                val shared =
                    engine.getValue("shared").jsonArray.map { t ->
                        JsonObject(
                            t.jsonObject + (
                                "chipId" to
                                    JsonPrimitive(
                                        "shared." + t.jsonObject["templateId"]?.jsonPrimitive?.content,
                                    )
                            ),
                        )
                    }
                val all = chips() + engine.getValue("occlusion").jsonArray + shared
                val fields = mutableMapOf<String, String>()
                sent["selections"]?.jsonArray.orEmpty().forEach { sel ->
                    val chip =
                        all.firstOrNull { it.jsonObject["chipId"] == sel.jsonObject["chipId"] }?.jsonObject
                            ?: return@forEach
                    var text = chip.getValue("labels").jsonObject[locale]?.jsonPrimitive?.content.orEmpty()
                    sel.jsonObject["variables"]?.jsonObject?.forEach {
                            (k, v) ->
                        text = text.replace("[$k]", v.jsonPrimitive.content)
                    }
                    val field = chip["field"]?.jsonPrimitive?.content ?: "occlusion"
                    fields[field] = listOfNotNull(fields[field], text).joinToString(" ")
                }
                ok(JsonObject(mapOf("fields" to JsonObject(fields.mapValues { JsonPrimitive(it.value) }))))
            }
            path == "record-usage" -> Reply(HttpStatusCode.OK, "")
            else -> fail(HttpStatusCode.NotFound, "NOT_FOUND")
        }
    }

    private fun ok(json: JsonElement) = Reply(HttpStatusCode.OK, apiJson.encodeToString(JsonElement.serializer(), json))

    // The backend's error body shape (17 §1), so the app's error path is exercised too.
    private fun fail(
        status: HttpStatusCode,
        code: String,
    ) = Reply(status, """{"error":${status.value},"status":${status.value},"code":"$code","requestId":"demo"}""")

    private fun refused() = fail(HttpStatusCode.Forbidden, "DEMO_WRITE_REFUSED")

    companion object {
        const val DEMO_DOCTOR_KEY = "DEMO-DOCTOR"
        const val DEMO_RECEPTION_KEY = "DEMO-RECEPTION"
        const val DEMO_PATIENT_EMAIL = "demo.patient@shifa.dev"

        // The shape of dev's GET /me/clinics (2026-11-04), one demo clinic.
        private const val DEMO_CLINICS =
            """[{"clinicId":1,"name":"Shifa Demo Clinic","timeZone":"Asia/Tashkent","currency":"UZS",
            "phone":"+998900000001","email":"clinic@shifa.dev","address":"Chilonzor 9, Tashkent",
            "membershipRole":"DOCTOR","isPracticeClinic":false,"defaultDoctorRevenueSharePercent":null,
            "canManageFinanceSettings":false}]"""

        // A slice of the dev catalogue (2026-11-04: 138 entries of {id, english, uzbek, name, category, displayOrder}).
        private const val DEMO_PROFESSIONS =
            """[{"english":"Dentist","uzbek":"Stomatolog","category":"Dental"},
            {"english":"Orthodontist","uzbek":"Ortodont","category":"Dental"},
            {"english":"Cardiologist","uzbek":"Kardiolog","category":"Medical"},
            {"english":"Neurologist","uzbek":"Nevrolog","category":"Medical"},
            {"english":"Pediatrician","uzbek":"Pediatr","category":"Children & Development"},
            {"english":"Urologist","uzbek":"Urolog","category":"Surgical"}]"""
        val shared by lazy { DemoBackend() }
    }
}

internal const val DEMO_PASSWORD = "Dev12345!"

private const val DEMO_SERVICES =
    """[{"id":1,"title":"Free first consultation","description":null,"isActive":true,"isFreeConsultation":true,""" +
        """"clinicManaged":false,"groupId":null,"groupName":null,"prices":[]},""" +
        """{"id":2,"title":"Consultation","description":"First visit with an examination","isActive":true,""" +
        """"isFreeConsultation":false,"clinicManaged":false,"groupId":null,"groupName":null,"prices":[""" +
        """{"amountMinor":15000000,"currency":"UZS","locationId":null},""" +
        """{"amountMinor":2000,"currency":"USD","locationId":1}]},""" +
        """{"id":3,"title":"Cleaning","description":null,"isActive":true,"isFreeConsultation":false,""" +
        """"clinicManaged":true,"groupId":null,"groupName":null,"prices":[""" +
        """{"amountMinor":30000000,"currency":"UZS","locationId":null}]}]"""

private const val DEMO_LOCATIONS =
    """[{"id":1,"label":"Shifa Demo Clinic","clinic":"Shifa Demo Clinic","locationCity":"Tashkent","isPrimary":true}]"""

private const val DEMO_PLANS =
    """[{"code":"BASIC","name":"Basic","priceMinor":9900,"currency":"EUR","interval":"MONTH"},""" +
        """{"code":"PRO","name":"Pro","priceMinor":19900,"currency":"EUR","interval":"MONTH"}]"""

private const val DEMO_PATIENTS =
    """{"content":[{"id":11,"name":"Dilnoza Rahimova","photoUrl":null},""" +
        """{"id":12,"name":"Jasur Karimov","photoUrl":null},{"id":13,"name":"Malika Tursunova","photoUrl":null},""" +
        """{"id":14,"name":"Bekzod Aliyev","photoUrl":null},{"id":15,"name":"","photoUrl":null}],"last":true}"""

/** A demo patient row with the fields `/patients` answers (the rest omitted). */
internal fun demoPatient(
    id: Long,
    name: String,
): Map<String, JsonElement> =
    mapOf(
        "id" to JsonPrimitive(id),
        "name" to JsonPrimitive(name),
        "phones" to JsonArray(listOf(JsonPrimitive("+99890000" + id.toString().padStart(4, '0')))),
        "birthDate" to JsonPrimitive("1990-0" + (1 + id % 9) + "-15"),
        "language" to JsonPrimitive("uz"),
        "hasAccount" to JsonPrimitive(id % 2 == 0L),
        "smsReminderEnabled" to JsonPrimitive(false),
        "smsReminderHoursBefore" to JsonPrimitive(24),
        "clinicalStatus" to JsonPrimitive(if (id == 13L) "AT_RISK" else "ACTIVE"),
    )

/** The demo week like the demo calendar: Monday to Friday 09:00–13:00 and 14:00–17:00, 30-minute slots. */
internal fun demoRules(locationId: String?): List<JsonElement> =
    (1..5).flatMap { day ->
        listOf("09:00" to "13:00", "14:00" to "17:00").map { (from, to) ->
            JsonObject(
                mapOf(
                    "weekday" to JsonPrimitive(day),
                    "startTime" to JsonPrimitive(from),
                    "endTime" to JsonPrimitive(to),
                    "slotMinutes" to JsonPrimitive(30),
                    "locationId" to (locationId?.toLongOrNull()?.let { JsonPrimitive(it) } ?: JsonNull),
                ),
            )
        }
    }

/** Appointments of the generated days overlapping [startAt, endAt) (at most two months are looked at). */
internal fun demoOverlap(
    startAt: String?,
    endAt: String?,
): Int {
    val start = runCatching { kotlinx.datetime.Instant.parse(startAt.orEmpty()) }.getOrNull() ?: return 0
    val end = runCatching { kotlinx.datetime.Instant.parse(endAt.orEmpty()) }.getOrNull() ?: return 0
    val zone = kotlinx.datetime.TimeZone.of("Asia/Tashkent")
    val first = start.toLocalDateTime(zone).date
    return (0 until 62).asSequence()
        .map { first.plus(kotlinx.datetime.DatePeriod(days = it)) }
        .takeWhile { it.atStartOfDayIn(zone) < end }
        .flatMap { demoCalendar(it.toString()).asSequence() }
        .count {
            val o = it.jsonObject
            val from = kotlinx.datetime.Instant.parse(o["startAt"]!!.jsonPrimitive.content)
            val to = kotlinx.datetime.Instant.parse(o["endAt"]!!.jsonPrimitive.content)
            o["type"]?.jsonPrimitive?.content == "APPOINTMENT" && from < end && to > start
        }
}

/**
 * The demo calendar's appointment [id] as `GET /appointments/{id}` answers it, on the next busy day from today (busy
 * days are even, so one of the next three: the 31st and the 1st are both odd); signed 15 s after [asked].
 */
internal fun demoAppointment(
    id: Long,
    asked: kotlinx.datetime.Instant?,
    now: kotlinx.datetime.Instant = currentInstant(),
): JsonObject? {
    val zone = kotlinx.datetime.TimeZone.of("Asia/Tashkent")
    val today = now.toLocalDateTime(zone).date
    val row =
        (0..2).map { today.plus(it, kotlinx.datetime.DateTimeUnit.DAY) }
            .flatMap { demoCalendar(it.toString()) }
            .map { it.jsonObject }
            .firstOrNull { it["appointmentId"]?.jsonPrimitive?.longOrNull == id } ?: return null
    val signed = asked != null && now >= asked + 15.seconds
    return JsonObject(
        row.filterKeys {
            it in setOf("startAt", "endAt", "patientId", "patientName", "location", "status", "paymentStatus", "reason")
        } +
            mapOf(
                "id" to JsonPrimitive(id),
                "doctorId" to JsonPrimitive(1),
                "signatureRequested" to JsonPrimitive(asked != null),
                "patientSignedAt" to if (signed) JsonPrimitive((asked + 15.seconds).toString()) else JsonNull,
                "fulfilledLineIds" to JsonArray(emptyList()),
                "documentIds" to JsonArray(emptyList()),
                "attachmentCount" to JsonPrimitive(0),
                "alreadySigned" to JsonPrimitive(signed),
            ),
    )
}

/** A demo visit's briefing: 101 ready, others failed; asked again, pending for 6 s and then ready. */
internal fun demoBriefing(
    id: Long,
    retried: kotlinx.datetime.Instant?,
    now: kotlinx.datetime.Instant = currentInstant(),
): JsonObject {
    val status =
        when {
            retried != null && now < retried + 6.seconds -> "PENDING"
            retried != null || id == 101L -> "READY"
            else -> "FAILED"
        }
    val text =
        "**Reason:** follow-up after treatment.\n- Blood count from 22.10: haemoglobin slightly low.\n" +
            "- No allergies recorded.\nCheck the haemoglobin trend before prescribing."
    return JsonObject(
        mapOf(
            "appointmentId" to JsonPrimitive(id),
            "status" to JsonPrimitive(status),
            "briefingText" to if (status == "READY") JsonPrimitive(text) else JsonNull,
            "documentCount" to JsonPrimitive(1),
            "generatedAt" to
                if (status == "READY") {
                    JsonPrimitive(
                        (retried ?: now - 3600.seconds).toString(),
                    )
                } else {
                    JsonNull
                },
            "error" to JsonNull,
        ),
    )
}

private const val DEMO_DRAFT_ID = "6f1c2a7e-0d3b-4c55-9a51-2b8e3f4d1a90"

// A scribe draft for the demo video visit (AI-11): SOAP, then the transcript the app hides.
private val DEMO_DRAFT =
    """{"id":"$DEMO_DRAFT_ID","aiLabel":"AI Scribe","createdAt":"2026-11-04T05:20:00Z",
    "body":"SUBJECTIVE:\nNight pain in the lower left molar for three days.\n\nASSESSMENT:\nAcute pulpitis of 36.\n\nPLAN:\nPulp removal, temporary filling, review in a week.\n\nTRANSCRIPT:\nDoctor: What brings you in today?",
    "icdSuggestions":[{"code":"K04.0","title":"Pulpitis","confidence":0.82,"isTop":true}]}"""

private fun demoNote(
    id: Long,
    body: String?,
    source: String,
) = JsonObject(
    mapOf(
        "id" to JsonPrimitive(id),
        "body" to (body?.let(::JsonPrimitive) ?: JsonNull),
        "source" to JsonPrimitive(source),
        "createdAt" to JsonPrimitive(currentInstant().toString()),
    ),
)

/**
 * A demo day (Tashkent, UTC+5): 30-minute slots 09:00–17:00, lunch blocked 13:00–14:00, and on even dates two
 * appointments (one by video, one in the clinic) so the calendar has something to show.
 */
internal fun demoCalendar(day: String?): JsonArray {
    val date = runCatching { kotlinx.datetime.LocalDate.parse(day ?: "") }.getOrNull() ?: return JsonArray(emptyList())
    val zone = kotlinx.datetime.TimeZone.of("Asia/Tashkent")

    fun at(minutes: Int) =
        kotlinx.datetime.LocalDateTime(
            date,
            kotlinx.datetime.LocalTime(minutes / 60, minutes % 60),
        ).toInstant(zone).toString()

    fun row(
        start: Int,
        end: Int,
        vararg fields: Pair<String, Any>,
    ) = JsonObject(
        mapOf(
            "startAt" to JsonPrimitive(at(start)),
            "endAt" to JsonPrimitive(at(end)),
            "locationId" to JsonPrimitive(1),
            "locationLabel" to JsonPrimitive("Shifa Demo Clinic"),
        ) + fields.associate { (k, v) -> k to if (v is Number) JsonPrimitive(v) else JsonPrimitive(v.toString()) },
    )
    val busy = date.dayOfMonth % 2 == 0
    val lunch = 13 * 60
    val rows =
        (9 * 60 until 17 * 60 step 30).mapNotNull { m ->
            when {
                m == lunch -> row(m, m + 60, "type" to "BLOCKED", "blockId" to 1, "blockReason" to "Lunch")
                m == lunch + 30 -> null
                busy && m == 10 * 60 ->
                    row(
                        m,
                        m + 30,
                        "type" to "APPOINTMENT",
                        "appointmentId" to 101,
                        "patientId" to 11,
                        "patientName" to "Dilnoza Rahimova",
                        "location" to "Video Consultation",
                        "reason" to "Follow-up",
                        "status" to "CONFIRMED",
                        "paymentStatus" to "PAID",
                        "attachmentCount" to 1,
                        "briefingStatus" to "READY",
                    )
                busy && m == 15 * 60 ->
                    row(
                        m,
                        m + 30,
                        "type" to "APPOINTMENT",
                        "appointmentId" to 102,
                        "patientId" to 12,
                        "patientName" to "Jasur Karimov",
                        "location" to "Shifa Demo Clinic",
                        "reason" to "First visit",
                        "status" to "REQUESTED",
                        "paymentStatus" to "PENDING",
                        "briefingStatus" to "FAILED",
                    )
                else -> row(m, m + 30, "type" to "FREE_SLOT")
            }
        }
    return JsonArray(rows)
}

private const val DEMO_DOCUMENTS =
    """[{"id":901,"title":"Complete blood count","date":"2026-10-22","url":"demo","canView":true,"creatorLabel":"Demo Doctor",
    "creatorType":"DOCTOR","category":"BLOOD_TEST","isSharedWithTeam":true,"uploadedByPatient":false,"appointmentId":101},
    {"id":902,"title":"ECG","date":"2026-09-29","url":null,"canView":false,"creatorLabel":"Demo Patient",
    "creatorType":"PATIENT","category":"ECG","isSharedWithTeam":false,"uploadedByPatient":true,"appointmentId":null}]"""

/** A valid one-page PDF ("Shifa demo document") with a correct cross-reference table, for the viewer. */
private fun demoPdf(): ByteArray {
    val text = "BT /F1 18 Tf 72 720 Td (Shifa demo document) Tj ET"
    val objects =
        listOf(
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R " +
                "/Resources << /Font << /F1 5 0 R >> >> >>",
            "<< /Length ${text.length} >>\nstream\n$text\nendstream",
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
        )
    val out = StringBuilder("%PDF-1.4\n")
    val offsets = objects.mapIndexed { i, body -> out.length.also { out.append("${i + 1} 0 obj\n$body\nendobj\n") } }
    val xref = out.length
    out.append("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n")
    offsets.forEach { out.append(it.toString().padStart(10, '0')).append(" 00000 n \n") }
    out.append("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
    return out.toString().encodeToByteArray()
}

/** The `file` part of a multipart body: its bytes and content type (byte offsets: the file is binary). */
private fun filePart(raw: ByteArray): Pair<ByteArray, String>? {
    fun find(
        pattern: String,
        from: Int,
    ): Int {
        val p = pattern.encodeToByteArray()
        for (i in from..raw.size - p.size) if (p.indices.all { raw[i + it] == p[it] }) return i
        return -1
    }
    val named = find("name=file", 0).takeIf { it >= 0 } ?: find("name=\"file\"", 0).takeIf { it >= 0 } ?: return null
    val start = find("\r\n\r\n", named).takeIf { it >= 0 } ?: return null
    val end = find("\r\n--", start + 4).takeIf { it >= 0 } ?: return null
    val headers = raw.copyOfRange(named, start).decodeToString()
    val type =
        Regex("Content-Type: *([^\r\n]+)", RegexOption.IGNORE_CASE).find(headers)?.groupValues?.get(1)?.trim()
            ?: "application/octet-stream"
    return raw.copyOfRange(start + 4, end) to type
}

private const val DEMO_ICD10 =
    """[{"code":"K02.0","title":"Caries enamel","subtitle":"K02"},{"code":"K02.1","title":"Caries dentine","subtitle":"K02"},
    {"code":"K04.0","title":"Pulpitis","subtitle":"K04"},{"code":"K05.1","title":"Chronic gingivitis","subtitle":"K05"},
    {"code":"K08.1","title":"Loss of teeth due to accident, extraction or local periodontal disease","subtitle":"K08"}]"""

private val FORM_SERVER_FIELDS =
    setOf(
        "id",
        "patientId",
        "signatureRequested",
        "patientSignedAt",
        "patientSignatureImageBase64",
        "createdByDoctorId",
    )

private const val DEMO_CLINICAL =
    """{"groups":[{"groupId":"grp.caries","sortOrder":1,"names":{"ru":"Кариес","uz":"Karies","en":"Caries"}}],
    "diseases":[
     {"diseaseId":"dis.003","number":3,"groupId":"grp.caries","slug":"moderate_caries","icdCodes":["K02.1"],
      "names":{"ru":"Средний кариес","uz":"O'rta kariyes","en":"Moderate caries"},
      "chips":[{"chipId":"dis.003.complaints.1","field":"complaints","labels":{"ru":"Боль от сладкого","uz":"Shirinlikdan og'riq","en":"Pain from sweets"},"variables":[],"priority":10},
       {"chipId":"dis.003.morbi.1","field":"morbi","labels":{"ru":"Реакция на пищу около [X] месяцев","uz":"[X] oy oldin reaksiya boshlangan","en":"Reacting to food for [X] months"},"variables":["X"],"priority":50},
       {"chipId":"dis.003.treatment_1.1","field":"treatment_1","labels":{"ru":"Препарирование и пломбирование","uz":"Preparatsiya va plomba","en":"Preparation and filling"},"variables":[],"priority":20}]},
     {"diseaseId":"dis.001","number":1,"groupId":"grp.caries","slug":"initial_caries","icdCodes":["K02.0"],
      "names":{"ru":"Начальный кариес","uz":"Boshlang'ich kariyes","en":"Initial caries"},
      "chips":[{"chipId":"dis.001.complaints.1","field":"complaints","labels":{"ru":"Жалоб нет","uz":"Shikoyat yo'q","en":"No complaints"},"variables":[],"priority":10}]}],
    "occlusion":[{"chipId":"occ.norm","angleClass":"I","icdHint":"K07.2","labels":{"ru":"Ортогнатический прикус","uz":"Ortognatik tishlam","en":"Orthognathic occlusion"},"variables":[],"priority":10}],
    "shared":[{"templateId":"mucosa.A","type":"mucosa","field":"oral_cavity","labels":{"ru":"Абсолютная норма","uz":"Mutloq me'yor","en":"Normal"},"priority":10,"correlates":[]},
     {"templateId":"xray.caries","type":"xray","field":"xray","labels":{"ru":"Очаг в эмали","uz":"Emalda o'choq","en":"Enamel radiolucency"},"priority":50,"correlates":["grp.caries"]}]}"""
