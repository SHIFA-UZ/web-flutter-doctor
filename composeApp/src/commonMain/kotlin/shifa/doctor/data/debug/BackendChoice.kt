package shifa.doctor.data.debug

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import shifa.doctor.data.api.ApiConfig
import shifa.doctor.utils.isDebugBuild

/** Debug menu → Development backend (17 §1). Kept across launches; release builds always use production. */
object BackendChoice {
    enum class Backend(
        val baseUrl: String,
    ) {
        Production(ApiConfig.BASE_URL),
        Development(ApiConfig.DEV_BASE_URL),
    }

    private const val KEY = "debug_backend"
    private val settings by lazy { Settings() }
    private val _current by lazy { MutableStateFlow(if (isDebugBuild) saved() else Backend.Production) }
    val current: StateFlow<Backend> get() = _current

    private fun saved() = Backend.entries.firstOrNull { it.name == settings.getStringOrNull(KEY) } ?: Backend.Production

    /** The debug menu signs out first: a token from one backend means nothing on the other. */
    fun set(backend: Backend) {
        settings[KEY] = backend.name
        _current.value = if (isDebugBuild) backend else Backend.Production
    }
}
