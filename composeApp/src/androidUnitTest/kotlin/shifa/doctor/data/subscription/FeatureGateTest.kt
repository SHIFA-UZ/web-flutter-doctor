package shifa.doctor.data.subscription

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import shifa.doctor.data.model.DoctorSubscription

class FeatureGateTest {
    @Test
    fun `codes match the backend's`() {
        assertThat(DoctorFeature.RemoteCareTasks.key).isEqualTo("REMOTE_CARE_TASKS")
        assertThat(DoctorFeature.AskShifaAi.key).isEqualTo("ASK_SHIFA_AI")
        assertThat(DoctorFeature.PatientManagement.key).isEqualTo("PATIENT_MANAGEMENT")
    }

    @Test
    fun `an unknown plan allows nothing (D-03), the features list decides, the tier only without it`() {
        assertThat(FeatureGate.Unknown.known).isFalse()
        assertThat(DoctorFeature.entries.none { FeatureGate.Unknown.allows(it) }).isTrue()
        val listed = FeatureGate.of(DoctorSubscription("PREMIUM", listOf("CALENDAR", "ASK_SHIFA_AI")))
        assertThat(listed.allows(DoctorFeature.AskShifaAi)).isTrue()
        assertThat(listed.allows(DoctorFeature.RemoteCareTasks)).isFalse() // not listed, whatever the tier says
        val oldBackend = FeatureGate.of(DoctorSubscription("pro"))
        assertThat(oldBackend.allows(DoctorFeature.AskShifaAi)).isTrue()
        assertThat(oldBackend.allows(DoctorFeature.RemoteCareTasks)).isFalse()
        assertThat(FeatureGate.of(null)).isEqualTo(FeatureGate.Unknown)
        // A list that is there but empty includes nothing, whatever the tier.
        val empty = FeatureGate.of(DoctorSubscription("PREMIUM", emptyList()))
        assertThat(empty.known).isTrue()
        assertThat(DoctorFeature.entries.none { empty.allows(it) }).isTrue()
    }
}
