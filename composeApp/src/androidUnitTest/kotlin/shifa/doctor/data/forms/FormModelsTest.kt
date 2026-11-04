@file:Suppress("ktlint:standard:max-line-length") // JSON fixtures

package shifa.doctor.data.forms

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.LocalDate
import org.junit.Test
import shifa.doctor.data.api.apiJson
import shifa.doctor.data.model.FormRequest
import shifa.doctor.data.model.PatientForm

class FormModelsTest {
    @Test
    fun `a form reads leniently and a save leaves out the server's own fields`() {
        val form =
            apiJson.decodeFromString(
                PatientForm.serializer(),
                """{"id":5,"patientId":14,"templateId":"025-2","date":"2026-03-12","fullName":"Jasur Aliyev","formNumber":"12",
                   "dentalChart":{"11":"C","__dentition__":"permanent"},"followups":[{"date":"2026-03-20","clinicalFindings":"ok"}],
                   "diagnosisSystem":"ICD10","signatureRequested":true,"patientSignedAt":"2026-03-13T10:00:00Z",
                   "patientSignatureImageBase64":"AAAA","createdByDoctorId":7,"something":"new"}""",
            )
        assertThat(form.formNumber).isEqualTo(12)
        assertThat(form.day).isEqualTo(LocalDate(2026, 3, 12))
        assertThat(form.followups.single().day).isEqualTo(LocalDate(2026, 3, 20))
        assertThat(form.signed).isTrue()
        val sent = apiJson.encodeToString(FormRequest.serializer(), form.request())
        assertThat(sent).doesNotContain("patientSigned")
        assertThat(sent).doesNotContain("signatureRequested")
        assertThat(sent).doesNotContain("Signature")
        assertThat(sent).doesNotContain("createdByDoctorId")
        // One ICD spelling on the wire.
        assertThat(sent).contains("\"diagnosisSystem\":\"ICD10\"")
        assertThat(sent).contains("\"dentalChart\":{\"11\":\"C\",\"__dentition__\":\"permanent\"}")
        assertThat(sent).doesNotContain("\"job\"")
        assertThat(sent).doesNotContain("clinicalChipSelections")
    }

    @Test
    fun `dates go as days, an empty form leaves its chart and lists out`() {
        val form =
            PatientForm(
                patientId = 14,
                date = "2026-03-12T00:00:00Z",
                fullName = "A",
                diagnosisSystem = "ICD-10",
                followups = listOf(shifa.doctor.data.model.FormFollowup("2026-03-20T09:00:00Z", "ok")),
            )
        val sent = apiJson.encodeToString(FormRequest.serializer(), form.request())
        assertThat(sent).contains("\"date\":\"2026-03-12\"")
        assertThat(sent).contains("\"date\":\"2026-03-20\"")
        assertThat(sent).contains("\"diagnosisSystem\":\"ICD10\"")
        val empty = apiJson.encodeToString(FormRequest.serializer(), form.copy(followups = emptyList()).request())
        assertThat(empty).doesNotContain("dentalChart")
        assertThat(empty).doesNotContain("followups")
        assertThat(empty).doesNotContain("clinicalChipSelections")
    }

    @Test
    fun `the dental codes are Flutter's twelve, read as written`() {
        assertThat(
            DentalCode.entries.map {
                it.code
            },
        ).containsExactly("O", "R", "C", "P", "Pt", "П", "A", "I", "II", "III", "K", "И").inOrder()
        assertThat(DentalCode.of(" Pt ")).isEqualTo(DentalCode.Pt)
        assertThat(DentalCode.of("pt")).isNull()
        assertThat(DentalCode.of("")).isNull()
    }
}
