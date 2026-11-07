@file:Suppress("DEPRECATION") // security-crypto is deprecated but has no replacement yet.

package shifa.doctor.data.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings

private const val FILE = "shifa_doctor_auth_secure"
private lateinit var appContext: Context

/** Called from ShifaDoctorApp before anything reads the token. */
fun initSecureSettings(context: Context) {
    appContext = context.applicationContext
}

internal actual fun createSecureSettings(): Settings =
    SharedPreferencesSettings(
        try {
            open()
        } catch (e: Exception) {
            // The keystore key is gone (backup restore, OS update): the file can't be decrypted, start over.
            appContext.deleteSharedPreferences(FILE)
            open()
        },
    )

private fun open(): SharedPreferences =
    EncryptedSharedPreferences.create(
        appContext,
        FILE,
        MasterKey.Builder(appContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
