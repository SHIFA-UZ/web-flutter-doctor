package shifa.doctor.ui.splash

import com.google.common.truth.Truth.assertThat
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import shifa.doctor.data.api.jwt
import shifa.doctor.data.config.BootGate
import shifa.doctor.data.session.Session
import shifa.doctor.data.session.SessionRepository
import shifa.doctor.data.settings.LanguageRepository
import shifa.doctor.data.storage.TokenStorage

@OptIn(ExperimentalCoroutinesApi::class)
class SplashViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val tokens = TokenStorage(MapSettings())
    private val sessions = SessionRepository(tokens, MapSettings(), LanguageRepository { error("unused") })

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private var gate: BootGate = BootGate.None

    private fun vm() = SplashViewModel({ Result.success(gate) }, sessions, { "en" }, splashLimitMs = 800)

    @Test
    fun `a returning doctor goes to the shell after the short brand moment, not 1_8 s`() =
        runTest(dispatcher) {
            val token = jwt(4_000_000_000L)
            tokens.token = token
            val vm = vm()
            advanceTimeBy(799)
            assertThat(vm.state.value).isEqualTo(SplashUiState.Loading)
            advanceUntilIdle()
            assertThat(vm.state.value).isEqualTo(SplashUiState.Finished(Session(token, "DOCTOR")))
            assertThat(testScheduler.currentTime).isLessThan(1_800)
        }

    @Test
    fun `no session goes to sign-in`() =
        runTest(dispatcher) {
            val vm = vm()
            advanceUntilIdle()
            assertThat(vm.state.value).isEqualTo(SplashUiState.Finished(null))
        }

    @Test
    fun `maintenance holds the app, and Try again clears it once the backend is back`() =
        runTest(dispatcher) {
            gate = BootGate.Maintenance(null)
            val vm = vm()
            advanceUntilIdle()
            assertThat(vm.state.value).isEqualTo(SplashUiState.Maintenance(null))
            gate = BootGate.None
            vm.retry()
            assertThat(vm.state.value).isEqualTo(SplashUiState.Maintenance(null, retrying = true))
            advanceUntilIdle()
            assertThat(vm.state.value).isEqualTo(SplashUiState.Finished(null))
        }

    @Test
    fun `an outdated build stops at the update frame`() =
        runTest(dispatcher) {
            gate = BootGate.Update("market://x")
            val vm = vm()
            advanceUntilIdle()
            assertThat(vm.state.value).isEqualTo(SplashUiState.UpdateRequired("market://x"))
        }

    @Test
    fun `a tap skips the brand moment, and a failed check never blocks launch`() =
        runTest(dispatcher) {
            val vm =
                SplashViewModel({ Result.failure(IllegalStateException()) }, sessions, { "en" }, splashLimitMs = 800)
            advanceTimeBy(100)
            vm.skip()
            advanceUntilIdle()
            assertThat(vm.state.value).isEqualTo(SplashUiState.Finished(null))
            assertThat(testScheduler.currentTime).isLessThan(800)
        }

    @Test
    fun `it starts on an immediate main dispatcher (the app's), not only a queued one`() =
        runTest(UnconfinedTestDispatcher()) {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val vm = vm()
            vm.skip()
            advanceUntilIdle()
            assertThat(vm.state.value).isEqualTo(SplashUiState.Finished(null))
        }
}
