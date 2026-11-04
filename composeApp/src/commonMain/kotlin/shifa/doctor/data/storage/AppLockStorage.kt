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
    val hasPin: Boolean get() = settings.hasKey(KEY_HASH)

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

    /** Saves [pin] (4–6 digits, else false) with a fresh salt. */
    fun setPin(pin: String): Boolean {
        if (!isValidPin(pin)) return false
        val salt = secureRandomBytes(SALT_BYTES)
        settings[KEY_SALT] = Base64.encode(salt)
        settings[KEY_HASH] = Base64.encode(hash(pin, salt))
        settings.remove(KEY_ATTEMPTS)
        return true
    }

    /** True when [pin] matches; a mismatch counts a failed attempt, a match resets the count. */
    fun verifyPin(pin: String): Boolean {
        val salt = settings.getStringOrNull(KEY_SALT)?.let(Base64::decode) ?: return false
        val stored = settings.getStringOrNull(KEY_HASH)?.let(Base64::decode) ?: return false
        val ok = constantTimeEquals(hash(pin, salt), stored)
        if (ok) settings.remove(KEY_ATTEMPTS) else settings[KEY_ATTEMPTS] = failedAttempts + 1
        return ok
    }

    /** Turning the lock off forgets the PIN and the biometric choice (01 §7.1). */
    fun clear() {
        listOf(KEY_HASH, KEY_SALT, KEY_ENABLED, KEY_BIOMETRIC, KEY_ATTEMPTS).forEach(settings::remove)
    }

    companion object {
        const val MAX_ATTEMPTS = 5
        private const val SALT_BYTES = 16
        private const val ITERATIONS = 10_000
        private const val KEY_HASH = "app_lock_pin_hash"
        private const val KEY_SALT = "app_lock_pin_salt"
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
