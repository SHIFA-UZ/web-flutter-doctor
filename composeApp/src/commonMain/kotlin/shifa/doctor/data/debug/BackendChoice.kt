package shifa.doctor.data.debug

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import shifa.doctor.data.api.ApiConfig
import shifa.doctor.utils.isDebugBuild
import shifa.doctor.utils.isDevelopmentSite

/**
 * Debug menu → Development backend (17 §1). Kept across launches. Release builds use production, except the web app at
 * the development site, which uses the development backend.
 */
object BackendChoice {
    enum class Backend(
        val baseUrl: String,
    ) {
        Production(ApiConfig.BASE_URL),
        Development(ApiConfig.DEV_BASE_URL),
    }

    private const val KEY = "debug_backend"
    private val settings by lazy { Settings() }
    private val releaseBackend get() = if (isDevelopmentSite) Backend.Development else Backend.Production
    private val _current by lazy { MutableStateFlow(if (isDebugBuild) saved() else releaseBackend) }
    val current: StateFlow<Backend> get() = _current

    private fun saved() = Backend.entries.firstOrNull { it.name == settings.getStringOrNull(KEY) } ?: Backend.Production

    /** The debug menu signs out first: a token from one backend means nothing on the other. */
    fun set(backend: Backend) {
        settings[KEY] = backend.name
        _current.value = if (isDebugBuild) backend else releaseBackend
    }
}
