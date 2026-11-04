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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
            get && path == Endpoints.ME_PROFILE -> ok(meProfile())
            get && path == Endpoints.ME_CLINICS -> ok(apiJson.parseToJsonElement(DEMO_CLINICS))
            // Fixed counts so the badges show; the lists behind them come with their features.
            get && path == Endpoints.CHAT_UNREAD -> ok(apiJson.parseToJsonElement("""{"count":3}"""))
            get && path == Endpoints.NOTIFICATIONS_UNREAD -> ok(apiJson.parseToJsonElement("""{"count":12}"""))
            get && path == Endpoints.TASKS -> ok(apiJson.parseToJsonElement("[]"))
            // Counts for the profile's completeness until SVC-01 / LOC-1 bring the real lists.
            get && path == Endpoints.DOCTOR_SERVICES ->
                ok(
                    apiJson.parseToJsonElement("""[{"id":1,"title":"Consultation"}]"""),
                )
            get && path == Endpoints.DOCTOR_LOCATIONS ->
                ok(
                    apiJson.parseToJsonElement("""[{"id":1,"label":"Shifa Demo Clinic"}]"""),
                )
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
