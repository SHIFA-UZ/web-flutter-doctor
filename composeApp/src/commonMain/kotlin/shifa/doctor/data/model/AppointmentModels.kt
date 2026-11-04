package shifa.doctor.data.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/** In the clinic or on video: the visit screen and its join rules depend on it (05 §1). */
enum class VisitKind {
    InPerson,
    Video,
    ;

    companion object {
        /**
         * The backend's flag when it sends one; else the booked location's "Video Consultation".
         * ponytail: the doctor's `GET /appointments/{id}` and the calendar carry no flag yet (BE-09); drop the word
         * check when they do.
         */
        fun of(
            isVideo: Boolean?,
            location: String?,
        ): VisitKind = if (isVideo ?: (location?.contains("video", ignoreCase = true) == true)) Video else InPerson
    }
}

/** The patient's signature on a visit (05 §2.2): not asked, asked and waiting, or given. */
enum class SignatureState { None, Waiting, Signed }

/**
 * `GET /appointments/{id}` as the doctor reads it (APPT-01/02; the OpenAPI schema of that name is the patient's: the
 * doctor's response carries the patient, the signature and the linked plan, verified on dev 2026-11-04). Times are
 * UTC strings; an unreadable one is null.
 */
@Serializable
data class AppointmentDetail(
    val id: Long = 0,
    val patientId: Long? = null,
    val patientName: String? = null,
    val doctorId: Long? = null,
    val startAt: String? = null,
    val endAt: String? = null,
    val location: String? = null,
    val status: String? = null,
    val paymentStatus: String? = null,
    val paymentAmountMinor: Long? = null,
    val paymentCurrency: String? = null,
    val signatureRequested: Boolean = false,
    val patientSignedAt: String? = null,
    val patientSignatureImageBase64: String? = null,
    val linkedPlanId: Long? = null,
    val linkedPlanTitle: String? = null,
    val fulfilledLineIds: List<Long> = emptyList(),
    val briefingStatus: String? = null,
    val attachmentCount: Int = 0,
    val alreadySigned: Boolean = false,
    val documentIds: List<Long> = emptyList(),
    val notes: String? = null,
    val reason: String? = null,
    val isVideoConsultation: Boolean? = null,
) {
    val start: Instant? get() = startAt?.let { runCatching { Instant.parse(it) }.getOrNull() }

    val end: Instant? get() = endAt?.let { runCatching { Instant.parse(it) }.getOrNull() }

    val kind: VisitKind get() = VisitKind.of(isVideoConsultation, location)

    val signedAt: Instant? get() = patientSignedAt?.let { runCatching { Instant.parse(it) }.getOrNull() }

    val signature: SignatureState
        get() =
            when {
                !patientSignedAt.isNullOrBlank() || alreadySigned -> SignatureState.Signed
                signatureRequested -> SignatureState.Waiting
                else -> SignatureState.None
            }

    val completed: Boolean get() = status.equals("COMPLETED", ignoreCase = true)
}
