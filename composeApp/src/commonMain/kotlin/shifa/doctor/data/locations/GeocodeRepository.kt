package shifa.doctor.data.locations

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import shifa.doctor.data.api.ApiConfig
import shifa.doctor.data.api.apiCall
import shifa.doctor.data.api.httpClient
import shifa.doctor.data.model.GeocodeResults
import shifa.doctor.data.model.GeocodedAddress
import shifa.doctor.data.storage.languageStorage

/**
 * The address at a map pin (BE-15, flow 08/04): the backend's OpenStreetMap lookup, so no map key ships in the app.
 * 30 lookups a minute per account; null when nothing is there.
 */
class GeocodeRepository(
    private val client: () -> HttpClient = { httpClient },
    private val language: () -> String = { languageStorage.language.value.backendCode },
) {
    suspend fun reverse(
        latitude: Double,
        longitude: Double,
    ): Result<GeocodedAddress?> =
        apiCall<GeocodeResults> {
            client().get(ApiConfig.Endpoints.GEOCODE_REVERSE) {
                parameter("lat", latitude)
                parameter("lng", longitude)
                parameter("lang", language())
            }
        }.map { it.results.firstOrNull() }
}
