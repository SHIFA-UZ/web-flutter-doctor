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
         * The backend's flag when it sends one (BE-09, backend 2026-11-07: the calendar's `isVideo`, the appointment's
         * `isVideoConsultation`); else the booked location's "Video Consultation" (a backend from before BE-09).
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
    // BE-10: why it was cancelled (DOCTOR_UNAVAILABLE, PATIENT_REQUEST, RESCHEDULED, OTHER) and the note.
    val cancelReasonCode: String? = null,
    val cancelNote: String? = null,
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

/** A visit briefing's state for the sheet (flow 05/02 §4): SKIPPED reads as None, GENERATING as Pending. */
enum class BriefingStatus { None, Pending, Ready, Failed }

/** `GET /appointments/{id}/briefing`: Shifa AI's summary of the documents the patient attached before the visit. */
@Serializable
data class AppointmentBriefing(
    val appointmentId: Long = 0,
    val status: String = "NONE",
    val briefingText: String? = null,
    val documentCount: Int = 0,
    val generatedAt: String? = null,
    val error: String? = null,
) {
    val state: BriefingStatus
        get() =
            when (status.uppercase()) {
                "PENDING", "GENERATING" -> BriefingStatus.Pending
                "FAILED" -> BriefingStatus.Failed
                // A ready briefing without text is nothing to show.
                "READY" -> if (briefingText.isNullOrBlank()) BriefingStatus.None else BriefingStatus.Ready
                else -> BriefingStatus.None
            }

    val generated: Instant? get() = generatedAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
}
