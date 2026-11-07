package shifa.doctor.data.storage

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set

/** The location the doctor last worked on in the schedule, per doctor (flow 04/10 §4: `active_location_id:<doctorId>`). */
class ActiveLocationStorage(
    private val settings: Settings = Settings(),
) {
    fun get(doctorId: Long): Long? = settings.getLongOrNull(key(doctorId))

    fun set(
        doctorId: Long,
        locationId: Long,
    ) {
        settings[key(doctorId)] = locationId
    }

    private fun key(doctorId: Long) = "active_location_id:$doctorId"
}
