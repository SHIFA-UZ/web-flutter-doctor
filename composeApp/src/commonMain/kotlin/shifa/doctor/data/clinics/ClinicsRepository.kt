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
}
