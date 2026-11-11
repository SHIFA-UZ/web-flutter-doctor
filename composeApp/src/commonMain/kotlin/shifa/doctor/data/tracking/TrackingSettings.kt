package shifa.doctor.data.tracking

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set

/**
 * What tracking keeps on the device, in plain Settings (neither is a secret): the doctor's opt-out (D3: tracking is
 * allowed until switched off) and the distinct id last handed to the SDK, which outlives the process like the SDK's
 * own copy does. The store is created on first use: a no-argument `Settings()` fails in plain JUnit tests.
 */
class TrackingSettings(
    settings: () -> Settings = { Settings() },
) {
    private val settings by lazy(settings)

    var optedOut: Boolean
        get() = settings.getBoolean(OPT_OUT, false)
        set(value) {
            settings[OPT_OUT] = value
        }

    var identified: String?
        get() = settings.getStringOrNull(IDENTIFIED)
        set(value) {
            if (value == null) settings.remove(IDENTIFIED) else settings[IDENTIFIED] = value
        }

    private companion object {
        const val OPT_OUT = "tracking_opt_out"
        const val IDENTIFIED = "tracking_identified"
    }
}
