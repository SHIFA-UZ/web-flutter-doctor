package shifa.doctor.data.applock

import com.google.common.truth.Truth.assertThat
import com.russhwolf.settings.MapSettings
import org.junit.Test
import shifa.doctor.data.storage.AppLockStorage
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class AppLockRepositoryTest {
    private val store = AppLockStorage(MapSettings())
    private val time = TestTimeSource()
    private val classToTest = AppLockRepository({ store }, time)

    @Test
    fun `a session locks only when the lock is on with a 4-6 digit ASCII PIN`() {
        classToTest.onSessionStarted()
        assertThat(classToTest.locked.value).isFalse()
        assertThat(classToTest.turnOn("123").isFailure).isTrue()
        assertThat(classToTest.turnOn("١٢٣٤").isFailure).isTrue() // Arabic-Indic digits: the keypad can't type them
        assertThat(classToTest.turnOn("1234").isSuccess).isTrue()
        assertThat(classToTest.turnOn("123456").isSuccess).isTrue()
        classToTest.onSessionStarted()
        assertThat(classToTest.locked.value).isTrue()
        classToTest.onSessionEnded()
        assertThat(classToTest.locked.value).isFalse()
    }

    @Test
    fun `coming back locks after five seconds away, not before, and not while suppressed`() {
        classToTest.turnOn("123456")
        classToTest.onBackground()
        time += 4.seconds
        classToTest.onForeground()
        assertThat(classToTest.locked.value).isFalse()
        classToTest.suppress(true)
        classToTest.onBackground()
        time += 30.seconds
        classToTest.onForeground()
        assertThat(classToTest.locked.value).isFalse()
        classToTest.suppress(false)
        classToTest.onBackground()
        time += 5.seconds
        classToTest.onForeground()
        assertThat(classToTest.locked.value).isTrue()
    }

    @Test
    fun `wrong PINs count down, the fifth forgets the PIN, the right one unlocks`() {
        classToTest.turnOn("123456")
        classToTest.onSessionStarted()
        assertThat(classToTest.check("000000")).isEqualTo(PinCheck.Wrong(4))
        assertThat(classToTest.check("123456")).isEqualTo(PinCheck.Unlocked)
        assertThat(classToTest.locked.value).isFalse()
        classToTest.onSessionStarted()
        repeat(4) { classToTest.check("000000") }
        assertThat(classToTest.check("000000")).isEqualTo(PinCheck.LockedOut)
        assertThat(classToTest.hasPin).isFalse()
        assertThat(classToTest.locked.value).isFalse()
    }

    @Test
    fun `turning off needs the PIN and forgets biometrics too`() {
        classToTest.turnOn("123456")
        classToTest.setBiometric(true)
        assertThat(classToTest.biometricEnabled).isTrue()
        assertThat(classToTest.turnOff("111111").getOrNull()).isEqualTo(PinCheck.Wrong(4))
        assertThat(classToTest.enabled).isTrue()
        assertThat(classToTest.turnOff("123456").getOrNull()).isEqualTo(PinCheck.Unlocked)
        assertThat(classToTest.enabled).isFalse()
        assertThat(classToTest.biometricEnabled).isFalse()
    }

    @Test
    fun `a store that can't answer signs out instead of locking forever`() {
        val broken = AppLockRepository({ error("keystore gone") }, time)
        assertThat(broken.check("123456")).isEqualTo(PinCheck.LockedOut)
        assertThat(broken.turnOn("123456").isFailure).isTrue()
    }
}
