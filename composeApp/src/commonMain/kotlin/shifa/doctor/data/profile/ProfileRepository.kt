package shifa.doctor.data.profile

import io.ktor.client.HttpClient
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import shifa.doctor.data.api.ApiConfig
import shifa.doctor.data.api.apiCall
import shifa.doctor.data.api.httpClient
import shifa.doctor.data.model.ProfileUpdateRequest

/** The signed-in doctor's profile (PROF-* grows this). */
class ProfileRepository(
    private val client: () -> HttpClient = { httpClient },
) {
    /** `PATCH /doctors/me/profile` with only the given keys. */
    suspend fun update(request: ProfileUpdateRequest): Result<Unit> =
        apiCall<Unit> {
            client().patch(ApiConfig.Endpoints.DOCTOR_PROFILE) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }
}
