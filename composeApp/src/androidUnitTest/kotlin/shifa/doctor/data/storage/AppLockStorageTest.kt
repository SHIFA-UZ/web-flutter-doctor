package shifa.doctor.data.storage

import com.google.common.truth.Truth.assertThat
import com.russhwolf.settings.MapSettings
import org.junit.Test

class AppLockStorageTest {
    private val settings = MapSettings()
    private val lock = AppLockStorage(settings)

    @Test
    fun `the PIN is stored as a salted hash, never as itself`() {
        assertThat(lock.setPin("482913")).isTrue()
        assertThat(settings.keys).doesNotContain("app_lock_pin")
        assertThat(settings.keys.map { settings.getStringOrNull(it) }).doesNotContain("482913")
        assertThat(lock.hasPin).isTrue()
    }

    @Test
    fun `4 to 6 digits only, and all lengths verify (Flutter's auto-check at 4 broke 5 and 6)`() {
        listOf("12", "1234567", "12a4").forEach { assertThat(lock.setPin(it)).isFalse() }
        listOf("1234", "12345", "123456").forEach {
            lock.setPin(it)
            assertThat(lock.verifyPin(it)).isTrue()
            assertThat(lock.verifyPin(it.dropLast(1))).isFalse()
        }
    }

    @Test
    fun `failed attempts persist and reset on success`() {
        lock.setPin("1234")
        repeat(3) { lock.verifyPin("0000") }
        assertThat(AppLockStorage(settings).failedAttempts).isEqualTo(3)
        lock.verifyPin("1234")
        assertThat(lock.failedAttempts).isEqualTo(0)
    }

    @Test
    fun `the same PIN gets a different salt each time`() {
        lock.setPin("1234")
        val first = settings.getStringOrNull("app_lock_pin_credential")
        lock.setPin("1234")
        assertThat(settings.getStringOrNull("app_lock_pin_credential")).isNotEqualTo(first)
    }

    @Test
    fun `turning the lock off forgets everything`() {
        lock.setPin("1234")
        lock.lockEnabled = true
        lock.biometricEnabled = true
        lock.clear()
        assertThat(settings.keys).isEmpty()
    }

    @Test
    fun `an unreadable credential uses up the attempts instead of failing forever`() {
        lock.setPin("1234")
        settings.putString("app_lock_pin_credential", "garbage")
        assertThat(lock.verifyPin("1234")).isFalse()
        assertThat(lock.failedAttempts).isEqualTo(AppLockStorage.MAX_ATTEMPTS)
    }
}
