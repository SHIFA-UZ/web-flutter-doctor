package shifa.doctor.data.storage

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import shifa.doctor.utils.secureRandomBytes
import shifa.doctor.utils.sha256
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * App-lock settings (01 §4, §7.1) in the secure store. The PIN is never stored: only a random salt and an iterated
 * salted SHA-256 (fixes bug 1, Flutter kept it in plaintext). Nothing here is cleared on logout, as in Flutter; the
 * failed-attempt count survives a rebuilt lock screen (Flutter kept it in widget state).
 *
 * ponytail: a 4–6 digit PIN has at most a million values, so the hash only slows a copied store down; the real
 * protection is the Keychain / EncryptedSharedPreferences around it and the 5-attempt sign-out.
 */
@OptIn(ExperimentalEncodingApi::class)
class AppLockStorage(
    private val settings: Settings = secureSettings,
) {
    val hasPin: Boolean get() = settings.hasKey(KEY_CREDENTIAL)

    var lockEnabled: Boolean
        get() = settings.getBoolean(KEY_ENABLED, false)
        set(value) {
            settings[KEY_ENABLED] = value
        }

    var biometricEnabled: Boolean
        get() = settings.getBoolean(KEY_BIOMETRIC, false)
        set(value) {
            settings[KEY_BIOMETRIC] = value
        }

    val failedAttempts: Int get() = settings.getInt(KEY_ATTEMPTS, 0)

    /**
     * Saves [pin] (4–6 digits, else false) with a fresh salt. Salt and hash are one value, written in one go, so a
     * failed write can't leave a new salt next to the old hash (a PIN nobody could enter).
     */
    fun setPin(pin: String): Boolean {
        if (!isValidPin(pin)) return false
        val salt = secureRandomBytes(SALT_BYTES)
        settings[KEY_CREDENTIAL] = Base64.encode(salt) + SEPARATOR + Base64.encode(hash(pin, salt))
        settings.remove(KEY_ATTEMPTS)
        return true
    }

    /**
     * True when [pin] matches; a mismatch counts a failed attempt, a match resets the count. A credential that can't
     * be read counts as all attempts used, so the lock ends in the sign-out fallback instead of never opening.
     */
    fun verifyPin(pin: String): Boolean {
        val parts = settings.getStringOrNull(KEY_CREDENTIAL)?.split(SEPARATOR)
        val salt = parts?.getOrNull(0)?.let { runCatching { Base64.decode(it) }.getOrNull() }
        val stored = parts?.getOrNull(1)?.let { runCatching { Base64.decode(it) }.getOrNull() }
        if (salt == null || stored == null) {
            settings[KEY_ATTEMPTS] = MAX_ATTEMPTS
            return false
        }
        val ok = constantTimeEquals(hash(pin, salt), stored)
        if (ok) settings.remove(KEY_ATTEMPTS) else settings[KEY_ATTEMPTS] = failedAttempts + 1
        return ok
    }

    /** Turning the lock off forgets the PIN and the biometric choice (01 §7.1). */
    fun clear() {
        listOf(KEY_CREDENTIAL, KEY_ENABLED, KEY_BIOMETRIC, KEY_ATTEMPTS).forEach(settings::remove)
    }

    companion object {
        const val MAX_ATTEMPTS = 5
        private const val SALT_BYTES = 16
        private const val ITERATIONS = 10_000

        // "salt.hash", both Base64 (which never contains a dot).
        private const val KEY_CREDENTIAL = "app_lock_pin_credential"
        private const val SEPARATOR = "."
        private const val KEY_ENABLED = "app_lock_enabled"
        private const val KEY_BIOMETRIC = "biometric_enabled"
        private const val KEY_ATTEMPTS = "app_lock_failed_attempts"

        fun isValidPin(pin: String) = pin.length in 4..6 && pin.all(Char::isDigit)

        private fun hash(
            pin: String,
            salt: ByteArray,
        ): ByteArray {
            var h = sha256(salt + pin.encodeToByteArray())
            repeat(ITERATIONS - 1) { h = sha256(salt + h) }
            return h
        }

        private fun constantTimeEquals(
            a: ByteArray,
            b: ByteArray,
        ): Boolean {
            if (a.size != b.size) return false
            var diff = 0
            for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
            return diff == 0
        }
    }
}
