package shifa.doctor.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test

// Ported from Flutter's test/doctor_display_name_test.dart (08 §4.4).
class DoctorNameTest {
    @Test
    fun `prefix, first and last, trimmed`() {
        assertThat(doctorDisplayName(" Aziza ", "Karimova ", "Dr. med.")).isEqualTo("Dr. med. Aziza Karimova")
    }

    @Test
    fun `no name gives nothing (the screen says Your name), no prefix gives the name alone`() {
        assertThat(doctorDisplayName("", null, "Dr. med.")).isEqualTo("")
        assertThat(doctorDisplayName("Aziza", "Karimova", null)).isEqualTo("Aziza Karimova")
        assertThat(doctorDisplayName(null, "Karimova", "Dr. med.")).isEqualTo("Dr. med. Karimova")
    }
}
