package shifa.doctor.data.patients

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import shifa.doctor.data.storage.FavoritesStorage

/** The doctor's starred patients: one flow for the directory and the detail, kept on the phone per doctor. */
class FavoritesRepository(
    private val store: FavoritesStorage = FavoritesStorage(),
) {
    private val _ids = MutableStateFlow<Set<Long>>(emptySet())
    val ids: StateFlow<Set<Long>> = _ids.asStateFlow()
    private var doctor: Long? = null

    /** The signed-in doctor's set (none when signed out). */
    fun load(doctorId: Long?) {
        doctor = doctorId
        _ids.value = doctorId?.let(store::get).orEmpty()
    }

    fun toggle(patientId: Long) {
        val next = _ids.value.let { if (patientId in it) it - patientId else it + patientId }
        _ids.value = next
        doctor?.let { store.set(it, next) }
    }
}

val favoritesRepository by lazy { FavoritesRepository() }
