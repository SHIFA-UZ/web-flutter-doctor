package shifa.doctor.data.storage

import com.russhwolf.settings.Settings
import com.russhwolf.settings.StorageSettings

// The browser has no secure enclave: the token lives in localStorage (the user's call, 2026-11-06, WEB-05). Doctor
// tokens expire after 12 hours and logout clears it.
internal actual fun createSecureSettings(): Settings = StorageSettings()
