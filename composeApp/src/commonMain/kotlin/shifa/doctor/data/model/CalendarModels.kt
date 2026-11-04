package shifa.doctor.data.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import shifa.doctor.utils.PracticeClock

/** One row of `GET /calendar?day=` as the backend sends it (dev 2026-11-04); every field may be missing. */
@Serializable
data class CalendarEntryDto(
    val type: String? = null,
    val startAt: String? = null,
    val endAt: String? = null,
    val appointmentId: Long? = null,
    val blockId: Long? = null,
    val blockReason: String? = null,
    val patientId: Long? = null,
    val patientName: String? = null,
    val patientPhotoUrl: String? = null,
    val photoUrl: String? = null,
    val location: String? = null,
    val locationId: Long? = null,
    val locationLabel: String? = null,
    val reason: String? = null,
    val status: String? = null,
    val paymentStatus: String? = null,
    val signatureRequested: Boolean = false,
    val patientSignedAt: String? = null,
    val briefingStatus: String? = null,
    val attachmentCount: Int = 0,
)

/** A calendar row on the doctor's day (03 §7): times are UTC instants; screens show them through `PracticeClock`. */
sealed interface CalendarEntry {
    val start: Instant
    val end: Instant
    val locationId: Long?
    val locationLabel: String?

    data class FreeSlot(
        override val start: Instant,
        override val end: Instant,
        override val locationId: Long? = null,
        override val locationLabel: String? = null,
    ) : CalendarEntry

    data class Appointment(
        override val start: Instant,
        override val end: Instant,
        val appointmentId: Long,
        val patientId: Long?,
        val patientName: String?,
        val photoUrl: String?,
        val status: String?,
        val paymentStatus: String?,
        val reason: String,
        // "Video Consultation" or the clinic address as the patient booked it.
        val location: String?,
        override val locationId: Long? = null,
        override val locationLabel: String? = null,
        val signatureRequested: Boolean = false,
        val patientSignedAt: Instant? = null,
        val briefingStatus: String? = null,
        val attachmentCount: Int = 0,
    ) : CalendarEntry {
        // ponytail: the calendar has no video flag yet (BE-09); the booked location text says "Video". Replace with
        // the backend's `isVideo` when it ships.
        val isVideo: Boolean get() = location?.contains("video", ignoreCase = true) == true

        val cancelled: Boolean get() = status.equals("CANCELLED", ignoreCase = true)
    }

    data class Blocked(
        override val start: Instant,
        override val end: Instant,
        val blockId: Long?,
        val reason: String?,
        override val locationId: Long? = null,
        override val locationLabel: String? = null,
    ) : CalendarEntry

    companion object {
        /**
         * A DTO to an entry; null when it has no readable times (it is skipped, not the whole day). Any type other than
         * APPOINTMENT or BLOCKED is a free slot (03 §6); an appointment without an id is skipped too.
         */
        fun from(dto: CalendarEntryDto): CalendarEntry? {
            val start = PracticeClock.parseInstant(dto.startAt) ?: return null
            val end = PracticeClock.parseInstant(dto.endAt) ?: return null
            if (end <= start) return null
            return when (dto.type?.uppercase()) {
                "APPOINTMENT" ->
                    Appointment(
                        start,
                        end,
                        dto.appointmentId ?: return null,
                        dto.patientId,
                        dto.patientName,
                        dto.patientPhotoUrl ?: dto.photoUrl,
                        dto.status,
                        dto.paymentStatus,
                        dto.reason.orEmpty(),
                        dto.location,
                        dto.locationId,
                        dto.locationLabel,
                        dto.signatureRequested,
                        PracticeClock.parseInstant(dto.patientSignedAt),
                        dto.briefingStatus,
                        dto.attachmentCount,
                    )
                "BLOCKED" -> Blocked(start, end, dto.blockId, dto.blockReason, dto.locationId, dto.locationLabel)
                else -> FreeSlot(start, end, dto.locationId, dto.locationLabel)
            }
        }
    }
}

/** How full a day is, for the month picker's shading (03 §1.3). */
data class DayOccupancy(
    val freeSlots: Int,
    val appointments: Int,
    val blocked: Int,
    // A block that starts at midnight in the practice zone: the whole day is off (the board's red day).
    val fullDayBlock: Boolean = false,
) {
    /** Free share of the day's rows, blocked ones included (03 §1.3), or null for a day without any; fully blocked is 0. */
    val freeRatio: Float? get() = (freeSlots + appointments + blocked).takeIf { it > 0 }?.let { freeSlots.toFloat() / it }

    companion object {
        /** [startsAtMidnight] says whether an instant is 00:00 on the practice wall clock. */
        fun of(
            entries: List<CalendarEntry>,
            startsAtMidnight: (Instant) -> Boolean = { false },
        ) = DayOccupancy(
            entries.count { it is CalendarEntry.FreeSlot },
            entries.count { it is CalendarEntry.Appointment && !it.cancelled },
            entries.count { it is CalendarEntry.Blocked },
            entries.any { it is CalendarEntry.Blocked && startsAtMidnight(it.start) },
        )
    }
}
