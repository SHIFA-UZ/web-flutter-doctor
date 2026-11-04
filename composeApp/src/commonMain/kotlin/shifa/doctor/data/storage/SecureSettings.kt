package shifa.doctor.data.storage

import com.russhwolf.settings.Settings

/** 01 §4: the secure store `shifa_doctor_auth_secure` (Keychain on iOS, EncryptedSharedPreferences on Android). */
internal expect fun createSecureSettings(): Settings

internal val secureSettings: Settings by lazy { createSecureSettings() }
