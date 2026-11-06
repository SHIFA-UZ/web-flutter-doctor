package shifa.doctor.data.clinics

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonElement
import shifa.doctor.data.api.ApiConfig
import shifa.doctor.data.api.apiCall
import shifa.doctor.data.api.httpClient
import shifa.doctor.data.api.rows
import shifa.doctor.data.model.ClinicMember
import shifa.doctor.data.model.ClinicOverviewStats
import shifa.doctor.data.model.MyClinic
import shifa.doctor.data.session.sessionRepository
import shifa.doctor.utils.Jwt

/** The signed-in user's clinics and the one they work in now ([selected]). */
data class ClinicsState(
    val clinics: List<MyClinic> = emptyList(),
    val selectedId: Long? = null,
    // The list has been read once: until then the workspace isn't known (no flicker of the Clinic tab, 09 §1.6).
    val known: Boolean = false,
) {
    /** The chosen clinic while still a member, else the first (Flutter's default); null for a solo doctor. */
    val selected: MyClinic? get() = clinics.firstOrNull { it.clinicId == selectedId } ?: clinics.firstOrNull()

    val hasWorkspace: Boolean get() = clinics.isNotEmpty()
}

/**
 * Clinic memberships (CLINIC-01, 09 §1.1, §6): `GET /me/clinics`, kept in [state]; a failed read keeps the last list
 * (sticky, so the workspace doesn't vanish on a bad connection). The chosen clinic is remembered per user under
 * `clinic_workspace_selected_id:<user>` (Flutter's key, the token's subject for the user) and survives a restart; a
 * clinic the user left falls back to the first.
 */
class ClinicsRepository(
    private val client: () -> HttpClient = { httpClient },
    private val settings: Settings = Settings(),
    private val user: () -> String? = { Jwt.subject(sessionRepository.session.value?.token) },
) {
    private val _state = MutableStateFlow(ClinicsState())
    val state: StateFlow<ClinicsState> = _state.asStateFlow()

    // Whose list [state] holds: another sign-in starts afresh.
    private var loadedFor: String? = null

    /**
     * `GET /me/clinics`: the clinics the signed-in user belongs to; empty for a solo doctor. Another sign-in starts from
     * nothing (the previous account's clinics never show), and an answer that comes back after the user changed is
     * dropped.
     */
    suspend fun mine(): Result<List<MyClinic>> {
        val who = user()
        if (who != loadedFor) {
            loadedFor = who
            _state.value = ClinicsState()
        }
        return apiCall<List<MyClinic>> { client().get(ApiConfig.Endpoints.ME_CLINICS) }
            .onSuccess { list ->
                if (user() != who) return@onSuccess
                _state.value = ClinicsState(list, who?.let { settings.getLongOrNull(key(it)) }, known = true)
            }
    }

    /** [state] belongs to the signed-in user (false after another sign-in, until it's read again). */
    val isCurrent: Boolean get() = loadedFor == user()

    /** The clinic the user works in now (read first when not known yet, or for another user). */
    suspend fun selected(): Result<MyClinic?> {
        if (!_state.value.known || loadedFor != user()) mine().onFailure { return Result.failure(it) }
        return Result.success(_state.value.selected)
    }

    /** The clinic switcher (09 §1.1): remembered for this user. */
    fun select(clinicId: Long) {
        if (_state.value.clinics.none { it.clinicId == clinicId }) return
        user()?.let { settings[key(it)] = clinicId }
        _state.update { it.copy(selectedId = clinicId) }
    }

    /** `GET /clinics/{id}/overview-stats`: today's appointments, active doctors, this month's patients. */
    suspend fun overviewStats(clinicId: Long): Result<ClinicOverviewStats> =
        apiCall<ClinicOverviewStats> { client().get(ApiConfig.Endpoints.clinicOverviewStats(clinicId)) }

    /** `GET /clinics/{id}/members`: the clinic's doctors and staff (one bad row doesn't drop the list). */
    suspend fun members(clinicId: Long): Result<List<ClinicMember>> =
        apiCall<List<JsonElement>> { client().get(ApiConfig.Endpoints.clinicMembers(clinicId)) }
            .map { it.rows(ClinicMember.serializer(), "clinic members") }

    private fun key(user: String) = "clinic_workspace_selected_id:$user"
}

val clinicsRepository by lazy { ClinicsRepository() }
