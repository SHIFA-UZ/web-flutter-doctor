package shifa.doctor.ui.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import shifa.doctor.data.tracking.TrackScreen
import shifa.doctor.data.tracking.trackName
import shifa.doctor.ui.profile.ProfileSection

class ShellScreenTest {
    private fun on(
        tab: ShellTab,
        vararg pushed: ShellRoute,
    ) = ShellUiState(tab = tab, stacks = mapOf(tab to listOf(ShellRoute.Root(tab)) + pushed))

    @Test
    fun `each tab's root is the tab's screen`() {
        ShellTab.entries.forEach { assertThat(shellScreen(ShellUiState(tab = it))?.id).isEqualTo(it.key) }
    }

    @Test
    fun `the topmost screen counts, not More over it, nor the route's data`() {
        val patient = on(ShellTab.Patients, ShellRoute.Patient(7))
        assertThat(shellScreen(patient)).isEqualTo(TrackScreen.Patient)
        assertThat(shellScreen(patient.copy(moreOpen = true))).isEqualTo(TrackScreen.Patient)
        assertThat(shellScreen(on(ShellTab.Home, ShellRoute.Search(1)))).isEqualTo(TrackScreen.PatientSearch)
        assertThat(shellScreen(on(ShellTab.Profile, ShellRoute.Reports))).isEqualTo(TrackScreen.Reports)
    }

    @Test
    fun `Shifa Intelligence and a screen opened over it`() {
        val over = on(ShellTab.Patients, ShellRoute.Patient(7)).copy(intelligence = true)
        assertThat(shellScreen(over)).isEqualTo(TrackScreen.Intelligence)
        val document = over.copy(overIntelligence = listOf(ShellRoute.Document(7, 3, "Blood test")))
        assertThat(shellScreen(document)).isEqualTo(TrackScreen.Document)
    }

    @Test
    fun `each profile section has its own screen, the debug menu none`() {
        ProfileSection.entries.forEach {
            assertThat(
                shellScreen(on(ShellTab.Profile, ShellRoute.Profile(it)))?.id,
            ).isEqualTo("profile_${it.trackName()}")
        }
        assertThat(shellScreen(on(ShellTab.Profile, ShellRoute.Debug))).isNull()
    }
}
