package shifa.doctor.data.storage

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set

/** The JWT (01 §4: `shifa_doctor_auth_token` in the secure store). Only the session repository writes it. */
class TokenStorage(
    private val settings: Settings = secureSettings,
) {
    var token: String?
        get() = settings.getStringOrNull(KEY)
        set(value) {
            if (value != null) settings[KEY] = value else settings.remove(KEY)
        }

    private companion object {
        const val KEY = "shifa_doctor_auth_token"
    }
}

val tokenStorage by lazy { TokenStorage() }
