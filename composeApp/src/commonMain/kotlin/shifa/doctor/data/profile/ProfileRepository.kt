package shifa.doctor.data.profile

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonElement
import shifa.doctor.data.api.ApiConfig
import shifa.doctor.data.api.apiCall
import shifa.doctor.data.api.httpClient
import shifa.doctor.data.model.DoctorBilling
import shifa.doctor.data.model.DoctorContact
import shifa.doctor.data.model.DoctorMe
import shifa.doctor.data.model.DoctorProfile
import shifa.doctor.data.model.DoctorSettings
import shifa.doctor.data.model.MeProfile
import shifa.doctor.data.model.ProfileUpdateRequest

/**
 * The signed-in doctor's profile (PROF-01, 08 §1, §10). [me] is the last `GET /doctors/me`, shared by the shell
 * (start tab, plan, clinic) and the profile screens; each successful PATCH updates it in place, so no screen shows a
 * stale value. [clear] forgets it when the session ends.
 */
class ProfileRepository(
    private val client: () -> HttpClient = { httpClient },
) {
    private val _me = MutableStateFlow<DoctorMe?>(null)
    val me: StateFlow<DoctorMe?> = _me.asStateFlow()

    // Bumped by [clear]: an answer to a request started before a sign-out never lands in the next session's profile.
    private var generation = 0

    // Bumped by every read and every saved write: a read that started before a newer read or save answered never
    // overwrites it (two quick resumes, a save during a refresh).
    private var version = 0

    /** `GET /doctors/me`. */
    suspend fun refresh(): Result<DoctorMe> {
        val started = generation
        val mine = ++version
        return apiCall<DoctorMe> { client().get(ApiConfig.Endpoints.DOCTOR_ME) }.onSuccess {
            if (started == generation && mine == version) _me.value = it
        }
    }

    /** How many services the doctor offers (`GET /doctors/me/services`; SVC-01 reads the services themselves). */
    suspend fun servicesCount(): Result<Int> =
        apiCall<List<JsonElement>> { client().get(ApiConfig.Endpoints.DOCTOR_SERVICES) }.map { it.size }

    /** How many practice locations the doctor has (`GET /doctors/me/locations`; LOC-1 reads them). */
    suspend fun locationsCount(): Result<Int> =
        apiCall<List<JsonElement>> { client().get(ApiConfig.Endpoints.DOCTOR_LOCATIONS) }.map { it.size }

    /** `GET /me/profile`: name, photo and role for either role (the clinic-staff profile, 08 §1.12). */
    suspend fun meProfile(): Result<MeProfile> = apiCall<MeProfile> { client().get(ApiConfig.Endpoints.ME_PROFILE) }

    /** `PATCH /doctors/me/profile` with only the given keys; the profile it answers with replaces the stored one. */
    suspend fun update(request: ProfileUpdateRequest): Result<DoctorProfile> =
        patch<ProfileUpdateRequest, DoctorProfile>(ApiConfig.Endpoints.DOCTOR_PROFILE, request) { me, saved ->
            me.copy(profile = saved)
        }

    /** `PATCH /doctors/me/contact`: the backend sets the whole block (a missing email clears it). */
    suspend fun updateContact(contact: DoctorContact): Result<DoctorContact> =
        patch<DoctorContact, DoctorContact>(ApiConfig.Endpoints.DOCTOR_CONTACT, contact) { me, saved ->
            me.copy(contact = saved)
        }

    /** `PATCH /doctors/me/billing`: the whole block. */
    suspend fun updateBilling(billing: DoctorBilling): Result<DoctorBilling> =
        patch<DoctorBilling, DoctorBilling>(ApiConfig.Endpoints.DOCTOR_BILLING, billing) { me, saved ->
            me.copy(billing = saved)
        }

    /** `PATCH /doctors/me/settings`: the whole block. */
    suspend fun updateSettings(settings: DoctorSettings): Result<DoctorSettings> =
        patch<DoctorSettings, DoctorSettings>(ApiConfig.Endpoints.DOCTOR_SETTINGS, settings) { me, saved ->
            me.copy(settings = saved)
        }

    fun clear() {
        generation++
        _me.value = null
    }

    private suspend inline fun <reified B, reified T> patch(
        path: String,
        body: B,
        crossinline into: (DoctorMe, T) -> DoctorMe,
    ): Result<T> {
        val started = generation
        return apiCall<T> {
            client().patch(path) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }.onSuccess { saved ->
            if (started == generation) {
                version++
                _me.update { it?.let { me -> into(me, saved) } }
            }
        }
    }
}

/** The app's one profile (like `sessionRepository`). */
val profileRepository by lazy { ProfileRepository() }
