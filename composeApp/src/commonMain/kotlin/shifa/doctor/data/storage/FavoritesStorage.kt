package shifa.doctor.data.storage

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set

/** The doctor's starred patients on this phone (flow 06/01: they survive a restart; Flutter kept them in memory). */
class FavoritesStorage(
    private val settings: Settings = Settings(),
) {
    fun get(doctorId: Long): Set<Long> =
        settings.getStringOrNull(key(doctorId)).orEmpty().split(',').mapNotNull { it.trim().toLongOrNull() }.toSet()

    fun set(
        doctorId: Long,
        ids: Set<Long>,
    ) {
        settings[key(doctorId)] = ids.sorted().joinToString(",")
    }

    private fun key(doctorId: Long) = "patient_favorites:$doctorId"
}
