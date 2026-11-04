package shifa.doctor.data.model

import kotlinx.serialization.Serializable

/** One weekly range (`/schedule/rules`): weekday 1 = Monday … 7 = Sunday, wall times "HH:mm" in the practice zone. */
@Serializable
data class RuleDto(
    val weekday: Int,
    val startTime: String,
    val endTime: String,
    val slotMinutes: Int,
    val locationId: Long? = null,
)

/** Extra hours on a date span (`/schedule/date-specific`), dates "YYYY-MM-DD", times "HH:mm". */
@Serializable
data class DateSpecificRuleDto(
    val startDate: String,
    val endDate: String,
    val startTime: String,
    val endTime: String,
    val slotMinutes: Int,
    val locationId: Long? = null,
    val id: Long? = null,
)

/** A period the weekly template is valid in (`/schedule/validity-periods`, `PATCH /schedule/valid-range`). */
@Serializable
data class ValidityPeriodDto(
    val validFrom: String,
    val validUntil: String,
)
