package shifa.doctor.data.model

import kotlinx.serialization.Serializable

/** A practice location (`/doctors/me/locations`, 03 §3); every location shares the practice time zone. */
@Serializable
data class DoctorLocation(
    val id: Long = 0,
    val label: String? = null,
    val clinic: String? = null,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationCountry: String? = null,
    val locationRegion: String? = null,
    val locationDistrict: String? = null,
    val locationCity: String? = null,
    val locationPostalCode: String? = null,
    val locationStreetAddress: String? = null,
    val isPrimary: Boolean = false,
)

/**
 * `POST` / `PATCH /doctors/me/locations[/{id}]`. A null field is left out and kept by the backend; "" clears it.
 * `address` mirrors the street address (Flutter did the same). `isPrimary = false` never demotes: the backend moves
 * the primary when another one becomes primary.
 */
@Serializable
data class LocationRequest(
    val label: String,
    val clinic: String? = null,
    val address: String? = null,
    val locationStreetAddress: String? = null,
    val locationCity: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isPrimary: Boolean = false,
)

/** `GET /geocode/reverse` and `/search` (BE-15): the backend's OpenStreetMap lookup; every part may be missing. */
@Serializable
data class GeocodedAddress(
    val formattedAddress: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationCountry: String? = null,
    val locationRegion: String? = null,
    val locationCity: String? = null,
    val locationDistrict: String? = null,
    val locationPostalCode: String? = null,
    val locationStreetAddress: String? = null,
)

@Serializable
data class GeocodeResults(
    val results: List<GeocodedAddress> = emptyList(),
)
