package shifa.doctor.ui.home

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.LocalDate
import org.junit.Test
import shifa.doctor.data.model.Patient

class PatientSearchTest {
    private val people =
        (1L..10L).map {
            Patient(
                it,
                "Patient ${'K' + it.toInt()}",
                phones = listOf("+998 90 000 00 ${it.toString().padStart(2, '0')}"),
            )
        }

    private fun state(q: String) = PatientSearchUiState(people.reversed(), query = q, today = LocalDate(2026, 11, 5))

    @Test
    fun `blank shows the first 8 by name, a query every match by name, display id or phone digits`() {
        assertThat(state(" ").hits.map { it.id }).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L).inOrder()
        assertThat(state("patient l").hits.map { it.id }).containsExactly(1L)
        assertThat(state("P-00003").hits.map { it.id }).containsExactly(3L)
        assertThat(state("00 07").hits.map { it.id }).containsExactly(7L)
        // Letters with digits never match phones.
        assertThat(state("x 007").hits).isEmpty()
    }
}
