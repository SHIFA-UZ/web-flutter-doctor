package shifa.doctor.data.locations

import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import shifa.doctor.data.api.ApiConfig
import shifa.doctor.data.api.apiCall
import shifa.doctor.data.api.httpClient
import shifa.doctor.data.model.DoctorLocation
import shifa.doctor.data.model.LocationRequest

/**
 * The doctor's practice locations (LOC-1, 03 §3), one list for every screen (locations, schedule setup, service
 * prices) instead of Flutter's three copies. A save or delete is applied to the list at once with the backend's rules
 * (a new primary demotes the old one, deleting the primary promotes the next); the caller then reads the list again and
 * shows if that read fails.
 */
class LocationsRepository(
    private val client: () -> HttpClient = { httpClient },
) {
    private val _locations = MutableStateFlow<List<DoctorLocation>?>(null)

    // Every read and write takes a number; a read answers only if nothing newer happened meanwhile, so a slow first
    // read can't bring back what a save or delete changed.
    private val generation = atomic(0)

    /** Null until first read; primary first, then by id (the backend's order). */
    val locations: StateFlow<List<DoctorLocation>?> = _locations.asStateFlow()

    suspend fun refresh(): Result<List<DoctorLocation>> {
        val mine = generation.incrementAndGet()
        return apiCall<List<DoctorLocation>> { client().get(ApiConfig.Endpoints.DOCTOR_LOCATIONS) }
            .onSuccess { list -> if (generation.value == mine) _locations.value = list }
    }

    /** A write's result applied at once (the backend's rules), so the list is right even if the read after it fails. */
    private fun apply(change: (List<DoctorLocation>) -> List<DoctorLocation>) {
        generation.incrementAndGet()
        _locations.update { it?.let(change) }
    }

    /** Creates ([id] null) or updates a location. */
    suspend fun save(
        id: Long?,
        request: LocationRequest,
    ): Result<DoctorLocation> =
        apiCall<DoctorLocation> {
            val body: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            if (id == null) {
                client().post(ApiConfig.Endpoints.DOCTOR_LOCATIONS, body)
            } else {
                client().patch(ApiConfig.Endpoints.doctorLocation(id), body)
            }
        }.onSuccess { saved ->
            apply { list ->
                val others =
                    list.filter { it.id != saved.id }.map {
                        if (saved.isPrimary) {
                            it.copy(
                                isPrimary = false,
                            )
                        } else {
                            it
                        }
                    }
                (others + saved).sortedWith(compareBy({ !it.isPrimary }, { it.id }))
            }
        }

    /** 409 when it is the only one or still has rules or upcoming appointments (BE-34: one code for all three). */
    suspend fun delete(id: Long): Result<Unit> =
        apiCall<Unit> { client().delete(ApiConfig.Endpoints.doctorLocation(id)) }.onSuccess {
            apply { list ->
                val gone = list.firstOrNull { it.id == id }
                val rest = list.filter { it.id != id }
                // The backend promotes the next one (primary first, then by id) when the primary goes.
                if (gone?.isPrimary == true && rest.isNotEmpty()) {
                    listOf(
                        rest[0].copy(isPrimary = true),
                    ) + rest.drop(1)
                } else {
                    rest
                }
            }
        }

    fun clear() {
        generation.incrementAndGet()
        _locations.value = null
    }
}

val locationsRepository by lazy { LocationsRepository() }
