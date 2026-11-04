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
import shifa.doctor.data.api.ApiConfig.Endpoints
import shifa.doctor.data.api.apiJson
import shifa.doctor.data.api.createHttpEngine
import shifa.doctor.generated.resources.Res
import shifa.doctor.utils.PhoneE164
import shifa.doctor.utils.isDebugBuild

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

    private var services: List<JsonElement> = apiJson.parseToJsonElement(DEMO_SERVICES).jsonArray.toList()

    fun engine(): HttpClientEngine =
        MockEngine { request ->
            val body = if (request.method == HttpMethod.Get) "" else request.body.toByteArray().decodeToString()
            val reply = mutex.withLock { route(request, body) }
            if (reply != null) {
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
    )

    private suspend fun route(
        request: HttpRequestData,
        body: String,
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
            // The booking list (CAL-7): the demo calendar's patients and a few more, one page.
            get && path == Endpoints.PATIENTS_FOR_ASSIGNMENT -> ok(apiJson.parseToJsonElement(DEMO_PATIENTS))
            request.method == HttpMethod.Post && path == Endpoints.SCHEDULE_BOOK -> Reply(HttpStatusCode.OK, "")
            // Appointment actions (CAL-6) succeed without changing the generated days: nothing is stored.
            path.startsWith("appointments/") && !get &&
                (
                    request.method == HttpMethod.Delete ||
                        path.endsWith("/change") ||
                        path.endsWith("/notify-payment-reminder")
                ) -> Reply(HttpStatusCode.OK, "")
            get && path == Endpoints.DOCTOR_LOCATIONS -> ok(apiJson.parseToJsonElement(DEMO_LOCATIONS))
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
                    )
                else -> row(m, m + 30, "type" to "FREE_SLOT")
            }
        }
    return JsonArray(rows)
}
