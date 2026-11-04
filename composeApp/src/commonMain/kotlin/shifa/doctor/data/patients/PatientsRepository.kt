package shifa.doctor.data.patients

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import shifa.doctor.data.api.ApiConfig
import shifa.doctor.data.api.apiCall
import shifa.doctor.data.api.httpClient
import shifa.doctor.data.model.Page
import shifa.doctor.data.model.PatientOption

/**
 * Patients (area 06). For now only the booking list (CAL-7); PAT-02 adds the directory, typed errors and the cache.
 */
class PatientsRepository(
    private val client: () -> HttpClient = { httpClient },
) {
    /**
     * Everyone the doctor can book, by name: `GET /patients/for-assignment` page by page (500 each, at most [MAX_PAGES]).
     * The backend has no search there yet, so the picker filters locally (BE-33). Rows without an id are dropped.
     */
    suspend fun forAssignment(): Result<List<PatientOption>> =
        runCatching {
            val all = mutableListOf<PatientOption>()
            for (page in 0 until MAX_PAGES) {
                val p =
                    apiCall<Page<PatientOption>> {
                        client().get(ApiConfig.Endpoints.PATIENTS_FOR_ASSIGNMENT) {
                            parameter("page", page)
                            parameter("size", PAGE_SIZE)
                        }
                    }.getOrThrow()
                all += p.content.filterNotNull().filter { it.id != null }
                if (p.last) break
            }
            // Sorting by name on the server answers 500 (BE-33), so the list is ordered here.
            all.distinctBy { it.id }.sortedBy { it.name.orEmpty().lowercase() }
        }

    private companion object {
        const val PAGE_SIZE = 500
        const val MAX_PAGES = 20
    }
}

val patientsRepository by lazy { PatientsRepository() }
