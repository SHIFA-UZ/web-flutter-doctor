package shifa.doctor.data.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.yearsUntil
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** A row of `GET /patients/for-assignment` (the light list for booking, 15 §patients). */
@Serializable
data class PatientOption(
    val id: Long? = null,
    val name: String? = null,
    val photoUrl: String? = null,
)

/** A Spring page; only what walking the pages needs. */
@Serializable
data class Page<T>(
    val content: List<T?> = emptyList(),
    val last: Boolean = true,
)

/** The doctor's view of a patient's status (`clinicalStatus`); anything new reads as [Unknown], never a crash. */
enum class PatientStatus { Active, AtRisk, FollowUp, Unknown }

/**
 * A patient (`/patients`, 06 §8), read leniently: `phones[]` falls back to `phone`, SMS hours are 1 or 24, an unknown
 * status is [PatientStatus.Unknown]. Display id, age and status live here only (Flutter had three helper files).
 */
@Serializable
data class Patient(
    val id: Long = 0,
    val name: String = "",
    val phone: String? = null,
    val phones: List<String?> = emptyList(),
    val email: String? = null,
    val address: String? = null,
    val birthDate: String? = null,
    val language: String? = null,
    val photoUrl: String? = null,
    val chronicDisease: String? = null,
    val hasAccount: Boolean = false,
    val username: String? = null,
    val locationCountry: String? = null,
    val locationRegion: String? = null,
    val locationDistrict: String? = null,
    val locationCity: String? = null,
    val locationPostalCode: String? = null,
    val locationStreetAddress: String? = null,
    val timeZone: String? = null,
    val smsReminderEnabled: Boolean = false,
    // Read as written: anything but 1 means 24 (see [smsHours]), even a value that isn't a number.
    val smsReminderHoursBefore: JsonPrimitive? = null,
    val gender: String? = null,
    val bloodGroup: String? = null,
    val allergies: String? = null,
    val clinicalStatus: String? = null,
    val atRisk: Boolean = false,
    val followUpRequired: Boolean = false,
) {
    /** "P-00018" (06 §1.3). */
    val displayId: String get() = "P-" + id.toString().padStart(5, '0')

    val status: PatientStatus
        get() =
            when (clinicalStatus?.trim()?.uppercase()) {
                null, "", "ACTIVE" -> PatientStatus.Active
                "AT_RISK" -> PatientStatus.AtRisk
                "FOLLOW_UP" -> PatientStatus.FollowUp
                else -> PatientStatus.Unknown
            }

    /** `phones[]` (trimmed, once each); the single `phone` only when the list has nothing usable. */
    val allPhones: List<String>
        get() {
            fun clean(list: List<String?>) = list.mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }.distinct()
            return clean(phones).ifEmpty { clean(listOf(phone)) }
        }

    /** Only 1 or 24 hours exist (06 §8); anything else reads as 24. */
    val smsHours: Int get() = if (smsReminderHoursBefore?.intOrNull == 1) 1 else 24

    /** "Street, City, District, Region" (flow 06/05) from the parts the patient has, else the legacy `address`. */
    val formattedLocation: String?
        get() =
            listOf(locationStreetAddress, locationCity, locationDistrict, locationRegion)
                .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
                .joinToString(", ")
                .ifEmpty { address?.trim()?.takeIf(String::isNotEmpty) }

    /** The chronic disease worth a warning: set, and not "None" (the Flutter picker stored that word). */
    val chronic: String?
        get() = chronicDisease?.trim()?.takeIf { it.isNotEmpty() && !it.equals("none", ignoreCase = true) }

    val birth: LocalDate? get() = birthDate?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }

    /** Whole years on [today] (the practice date, injected so tests and the practice zone decide). */
    fun age(today: LocalDate): Int? = birth?.takeIf { it <= today }?.yearsUntil(today)

    /**
     * The directory search (PAT-03, HOME-05): name, id, display id, or phone digits for a number-like query of 3+
     * digits ("90 123", "+998…"), so "Alice 123" doesn't match phones. A blank query matches everyone.
     */
    fun matches(query: String): Boolean {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return true
        val digits = q.filter(Char::isDigit).takeIf { q.none(Char::isLetter) }.orEmpty()
        return name.lowercase().contains(q) ||
            id.toString() == q ||
            displayId.lowercase().contains(q) ||
            (digits.length >= MIN_PHONE_DIGITS && allPhones.any { it.filter(Char::isDigit).contains(digits) })
    }
}

private const val MIN_PHONE_DIGITS = 3

/** An appointment of this patient with the doctor (`GET /patients/{id}/appointments`). */
@Serializable
data class PatientAppointment(
    val id: Long = 0,
    val startAt: String = "",
    val endAt: String = "",
    val status: String? = null,
    val location: String? = null,
    val isVideo: Boolean = false,
    val reason: String? = null,
)

/** Prophylaxis reminders for a patient in a clinic (`/prophylaxis/settings`). */
@Serializable
data class ProphylaxisSetting(
    val patientId: Long,
    val clinicId: Long,
    val intervalMonths: Int = 12,
    val enabled: Boolean = true,
    val lastSentAt: String? = null,
)

/**
 * `POST /patients` and `PATCH /patients/{id}` (06 §7). Null fields are left out (on a PATCH the backend keeps them);
 * `""` clears a text field (e.g. the chronic disease). Birth date "YYYY-MM-DD".
 */
@Serializable
data class PatientRequest(
    val name: String? = null,
    val phones: List<String>? = null,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val birthDate: String? = null,
    val language: String? = null,
    val chronicDisease: String? = null,
    val gender: String? = null,
    val bloodGroup: String? = null,
    val allergies: String? = null,
    val smsReminderEnabled: Boolean? = null,
    val smsReminderHoursBefore: Int? = null,
)

/** `POST /patients/{id}/create-account`: the patient app login to hand over once. */
@Serializable
data class PatientAccount(
    val username: String = "",
    val oneTimePassword: String = "",
)
