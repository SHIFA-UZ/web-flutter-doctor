package shifa.doctor.data.services

import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import shifa.doctor.data.api.ApiConfig
import shifa.doctor.data.api.apiCall
import shifa.doctor.data.api.httpClient
import shifa.doctor.data.model.DoctorLocation
import shifa.doctor.data.model.DoctorService
import shifa.doctor.data.model.ServiceGroup
import shifa.doctor.data.model.UpsertService

/** The doctor's services and prices (SVC-01) and their groups (SVC-02); every write's status is checked (08 §2 bug). */
class ServicesRepository(
    private val client: () -> HttpClient = { httpClient },
) {
    suspend fun services(): Result<List<DoctorService>> =
        apiCall<List<DoctorService>> { client().get(ApiConfig.Endpoints.DOCTOR_SERVICES) }

    suspend fun groups(): Result<List<ServiceGroup>> =
        apiCall<List<ServiceGroup>> { client().get(ApiConfig.Endpoints.SERVICE_GROUPS) }
            .map { list -> list.sortedWith(compareBy({ it.sortOrder }, { it.name.lowercase() })) }

    suspend fun locations(): Result<List<DoctorLocation>> =
        apiCall<List<DoctorLocation>> { client().get(ApiConfig.Endpoints.DOCTOR_LOCATIONS) }

    /** Creates when [id] is null, else updates. */
    suspend fun save(
        id: Long?,
        service: UpsertService,
    ): Result<DoctorService> =
        apiCall<DoctorService> {
            val path = id?.let { ApiConfig.Endpoints.doctorService(it) } ?: ApiConfig.Endpoints.DOCTOR_SERVICES
            val body: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {
                contentType(ContentType.Application.Json)
                setBody(service)
            }
            if (id == null) client().post(path, body) else client().patch(path, body)
        }

    suspend fun delete(id: Long): Result<Unit> =
        apiCall<Unit> {
            client().delete(
                ApiConfig.Endpoints.doctorService(id),
            )
        }

    suspend fun saveGroup(group: ServiceGroup): Result<ServiceGroup> =
        apiCall<ServiceGroup> {
            val body = ServiceGroup(name = group.name, sortOrder = group.sortOrder)
            val id = group.id
            if (id == null) {
                client().post(ApiConfig.Endpoints.SERVICE_GROUPS) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            } else {
                client().patch(ApiConfig.Endpoints.serviceGroup(id)) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            }
        }

    suspend fun deleteGroup(id: Long): Result<Unit> =
        apiCall<Unit> {
            client().delete(ApiConfig.Endpoints.serviceGroup(id))
        }
}
