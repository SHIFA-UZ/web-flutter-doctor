package shifa.doctor.data.patients

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import shifa.doctor.data.api.ApiConfig
import shifa.doctor.data.api.apiCall
import shifa.doctor.data.api.apiJson
import shifa.doctor.data.api.httpClient
import shifa.doctor.data.api.httpLogger
import shifa.doctor.data.api.rows
import shifa.doctor.data.api.usable
import shifa.doctor.data.model.Patient
import shifa.doctor.data.model.PatientAccount
import shifa.doctor.data.model.PatientAppointment
import shifa.doctor.data.model.PatientOption
import shifa.doctor.data.model.PatientRequest
import shifa.doctor.data.model.ProphylaxisSetting
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Patients (PAT-02, 06 §6–7): the doctor's directory as one [patients] flow, read page by page and kept until a
 * pull-to-refresh, a change, or [ttl] (Flutter read every page again on every tab switch); single patients, create and
 * update (the directory follows), appointments with the doctor, the patient-app account, the test SMS, prophylaxis, and
 * the booking list. Errors stay typed (`ApiException`): nothing is swallowed into null (Flutter's `patientByIdProvider`).
 */
class PatientsRepository(
    private val client: () -> HttpClient = { httpClient },
    private val time: TimeSource = TimeSource.Monotonic,
    private val ttl: Duration = 60.seconds,
) {
    private val _patients = MutableStateFlow<List<Patient>?>(null)

    /** Null until first read; in name order. */
    val patients: StateFlow<List<Patient>?> = _patients.asStateFlow()
    private var readAt: TimeMark? = null

    // Reads and writes are numbered: a read publishes only if nothing (a create, an update, a sign-out) came after it.
    private val generation = atomic(0)

    // Moves on at sign-out: an answer for the previous doctor never lands in the next one's list.
    private val sessions = atomic(0)

    /**
     * `GET /patients?page=&size=100&sort=fullName,asc`, a list or a Spring page, until the last page (at most [MAX_PAGES],
     * logged if reached); rows without an id are dropped, each patient once.
     */
    suspend fun refresh(): Result<List<Patient>> {
        val mine = generation.incrementAndGet()
        return runCatching {
            val all = mutableListOf<Patient>()
            val first = page(0)
            val (firstRows, firstLast) = pageOf(first, PAGE_SIZE)
            all += firstRows.patients()
            val total = ((first as? JsonObject)?.get("totalPages") as? JsonPrimitive)?.intOrNull
            if (!firstLast && total != null) {
                tooMany(total > MAX_PAGES)
                // A Spring page says how many pages there are: the rest are read four at a time, in order.
                (1 until total).chunked(BATCH).forEach { batch ->
                    coroutineScope { batch.map { async { page(it) } }.awaitAll() }
                        .forEach { all += pageOf(it, PAGE_SIZE).first.patients() }
                }
            } else if (!firstLast) {
                // A bare list doesn't: page after page until a short one.
                for (page in 1 until MAX_PAGES) {
                    val (rows, last) = pageOf(page(page), PAGE_SIZE)
                    all += rows.patients()
                    if (last) break
                    tooMany(page == MAX_PAGES - 1)
                }
            }
            all.distinctBy { it.id }
        }.onSuccess {
            if (generation.value == mine) {
                _patients.value = it
                readAt = time.markNow()
            }
        }
    }

    private suspend fun page(page: Int): JsonElement =
        apiCall<JsonElement> {
            client().get(ApiConfig.Endpoints.PATIENTS) {
                parameter("page", page)
                parameter("size", PAGE_SIZE)
                parameter("sort", "fullName,asc")
            }
        }.getOrThrow()

    private fun List<JsonElement>.patients() = rows(Patient.serializer(), "patients").filter { it.id != 0L }

    // Not silently cut: the list stays as it was and the caller sees the failure.
    private fun tooMany(reached: Boolean) {
        if (!reached) return
        httpLogger.log("FAILED patients: more than $MAX_PAGES pages")
        error("too many patients")
    }

    /** The directory if it was read within [ttl], else read again (a tab switch doesn't refetch everything). */
    suspend fun refreshIfStale(): Result<List<Patient>> {
        val cached = _patients.value
        if (cached != null && readAt?.let { it.elapsedNow() < ttl } == true) return Result.success(cached)
        return refresh()
    }

    /**
     * One patient, in a clinic's scope when [clinicId] is given; the directory takes the fresh copy. A clinic read
     * only refreshes a patient already in the doctor's own directory: another clinic doctor's patient never joins it.
     */
    suspend fun patient(
        id: Long,
        clinicId: Long? = null,
    ): Result<Patient> {
        // A read applies only if nothing newer happened meanwhile (an update, another read, a sign-out).
        val mine = generation.value
        return apiCall<Patient> {
            client().get(ApiConfig.Endpoints.patient(id)) { clinicId?.let { parameter("clinicId", it) } }
        }.onSuccess { if (generation.value == mine) remember(it, join = clinicId == null) }
    }

    suspend fun create(request: PatientRequest): Result<Patient> {
        val session = sessions.value
        return apiCall<Patient> {
            client().post(ApiConfig.Endpoints.PATIENTS) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }.onSuccess { if (sessions.value == session) remember(it) }
    }

    suspend fun update(
        id: Long,
        request: PatientRequest,
    ): Result<Patient> {
        val session = sessions.value
        return apiCall<Patient> {
            client().patch(ApiConfig.Endpoints.patient(id)) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }.onSuccess { if (sessions.value == session) remember(it) }
    }

    /**
     * The patient's visits. The doctor's own route answers only the visits with that doctor (for staff: with one
     * doctor of the clinic), and 404 for a clinic patient who only saw a colleague. So from a clinic's workspace the
     * clinic's route decides (every doctor's visits there, but without place, reason or the cancelled ones), and the
     * own route's fuller rows replace the same visits where it answers.
     */
    suspend fun appointments(
        id: Long,
        clinicId: Long? = null,
    ): Result<List<PatientAppointment>> {
        if (clinicId == null) return visits(ApiConfig.Endpoints.patientAppointments(id))
        return coroutineScope {
            val own = async { visits(ApiConfig.Endpoints.patientAppointments(id)) }
            visits(ApiConfig.Endpoints.clinicPatientAppointments(clinicId, id)).map { clinic ->
                val mine = own.await().getOrNull().orEmpty()
                (mine + clinic).distinctBy { it.id }.sortedByDescending { it.startAt }
            }
        }
    }

    private suspend fun visits(path: String): Result<List<PatientAppointment>> =
        apiCall<List<JsonElement>> { client().get(path) }
            .map { it.rows(PatientAppointment.serializer(), "patient appointments") }

    suspend fun createAccount(id: Long): Result<PatientAccount> =
        apiCall<PatientAccount> {
            client().post(ApiConfig.Endpoints.patientCreateAccount(id)) {
                contentType(ContentType.Application.Json)
                setBody(JsonObject(emptyMap()))
            }
        }

    /** 403 without the SMS feature, 503 when the SMS provider is down (06 §7): the caller shows which. */
    suspend fun sendTestSms(id: Long): Result<Unit> =
        apiCall<Unit> {
            client().post(ApiConfig.Endpoints.patientTestSms(id)) {
                contentType(ContentType.Application.Json)
                setBody(JsonObject(emptyMap()))
            }
        }

    /** The clinic's prophylaxis reminder for this patient, or null when none is set (an empty answer). */
    suspend fun prophylaxis(
        patientId: Long,
        clinicId: Long,
    ): Result<ProphylaxisSetting?> =
        // "No setting" is a 200 with an empty body and no content type (dev, 2026-11-06), which Ktor can't decode:
        // read as text, null when blank; an unreadable setting is a logged failure, never the defaults.
        apiCall<String> {
            client().get(ApiConfig.Endpoints.PROPHYLAXIS_SETTINGS) {
                parameter("patientId", patientId)
                parameter("clinicId", clinicId)
            }
        }.fold(
            { text ->
                if (text.isBlank()) {
                    Result.success(null)
                } else {
                    Result.success(text).usable("prophylaxis setting") {
                        apiJson.decodeFromString(ProphylaxisSetting.serializer(), it)
                    }
                }
            },
            { Result.failure(it) },
        )

    suspend fun saveProphylaxis(setting: ProphylaxisSetting): Result<ProphylaxisSetting> =
        apiCall<ProphylaxisSetting> {
            client().put(ApiConfig.Endpoints.PROPHYLAXIS_SETTINGS) {
                contentType(ContentType.Application.Json)
                setBody(setting.copy(lastSentAt = null))
            }
        }

    /**
     * Everyone the doctor can book (CAL-7), by name: `GET /patients/for-assignment` page by page (500 each, at most
     * [MAX_PAGES]); a list or a page answer both read. There is no search there yet, so the picker filters locally
     * (BE-33). Rows without an id are dropped, and each patient comes once.
     */
    suspend fun forAssignment(): Result<List<PatientOption>> =
        runCatching {
            val all = mutableListOf<PatientOption>()
            for (page in 0 until MAX_PAGES) {
                val json =
                    apiCall<JsonElement> {
                        client().get(ApiConfig.Endpoints.PATIENTS_FOR_ASSIGNMENT) {
                            parameter("page", page)
                            parameter("size", ASSIGN_PAGE)
                            parameter("sort", "fullName,asc")
                        }
                    }.getOrThrow()
                val (rows, last) = pageOf(json, ASSIGN_PAGE)
                all += rows.rows(PatientOption.serializer(), "booking patients").filter { it.id != null }
                if (last) break
            }
            all.distinctBy { it.id }.sortedBy { it.name.orEmpty().lowercase() }
        }

    fun clear() {
        sessions.incrementAndGet()
        generation.incrementAndGet()
        _patients.value = null
        readAt = null
    }

    /**
     * A fresh copy of one patient replaces (or, with [join], joins) the directory, keeping the name order; older reads
     * are dropped.
     */
    private fun remember(
        p: Patient,
        join: Boolean = true,
    ) {
        generation.incrementAndGet()
        _patients.update {
                list ->
            list?.takeIf { join || it.any { x -> x.id == p.id } }
                ?.let { (it.filter { x -> x.id != p.id } + p).sortedBy { x -> x.name.lowercase() } }
                ?: list
        }
    }

    /** The rows of a list answer, or of a Spring page (`content`, `last`); a short list is the last page. */
    private fun pageOf(
        json: JsonElement,
        size: Int,
    ): Pair<List<JsonElement>, Boolean> {
        val page = json as? JsonObject
        // Anything else (null, a primitive, an object without `content`) is a failure, not an empty directory.
        val rows = (json as? JsonArray) ?: page?.get("content") as? JsonArray ?: error("unreadable patients page")
        val last = (page?.get("last") as? JsonPrimitive)?.booleanOrNull ?: (rows.size < size)
        return rows.toList() to last
    }

    private companion object {
        const val PAGE_SIZE = 100
        const val ASSIGN_PAGE = 500
        const val MAX_PAGES = 200
        const val BATCH = 4
    }
}

val patientsRepository by lazy { PatientsRepository() }
