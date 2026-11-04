package shifa.doctor.data.storage

import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.KeychainSettings
import com.russhwolf.settings.Settings

@OptIn(ExperimentalSettingsImplementation::class)
internal actual fun createSecureSettings(): Settings = KeychainSettings(service = "shifa_doctor_auth_secure")
