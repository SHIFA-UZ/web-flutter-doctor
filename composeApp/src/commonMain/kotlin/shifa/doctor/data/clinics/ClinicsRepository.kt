package shifa.doctor.data.clinics

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import shifa.doctor.data.api.ApiConfig
import shifa.doctor.data.api.apiCall
import shifa.doctor.data.api.httpClient
import shifa.doctor.data.model.MyClinic

/** Clinic memberships (area 09 grows this into the workspace). */
class ClinicsRepository(
    private val client: () -> HttpClient = { httpClient },
) {
    /** `GET /me/clinics`: the clinics the signed-in user belongs to; empty for a solo doctor. */
    suspend fun mine(): Result<List<MyClinic>> =
        apiCall<List<MyClinic>> {
            client().get(
                ApiConfig.Endpoints.ME_CLINICS,
            )
        }

    /**
     * The clinic the doctor works in now: Flutter's selected clinic defaults to the first membership; null for a solo
     * doctor. ponytail: always the first until CLINIC-01 adds choosing (and remembering) one.
     */
    suspend fun selected(): Result<MyClinic?> = mine().map { it.firstOrNull() }
}

val clinicsRepository = ClinicsRepository()
