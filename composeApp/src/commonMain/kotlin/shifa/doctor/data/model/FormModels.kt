package shifa.doctor.data.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/** The form templates (06 §3.1): only 025-2 today, Uzbekistan's dental outpatient record. */
object FormTemplates {
    const val F025_2 = "025-2"
    val all = listOf(F025_2)
}

/** A clinical-engine chip picked on a form (02 §5): the chip and the values filled into it. */
@Serializable
data class ClinicalChipSelection(
    val chipId: String,
    val variables: Map<String, String> = emptyMap(),
)

/** A return visit row on a 025-2 form ("Повторные посещения"). */
@Serializable
data class FormFollowup(
    val date: String,
    val clinicalFindings: String = "",
    val doctorName: String? = null,
) {
    val day: LocalDate? get() = runCatching { LocalDate.parse(date.take(10)) }.getOrNull()
}

/**
 * A patient form as `/patients/{id}/forms` answers (06 §3.2; dev's OpenAPI `PatientFormDto`). The signature fields are
 * the server's (set when the patient signs in their app) and never sent back; [dentalChart] uses the codec's keys
 * ([shifa.doctor.data.forms.DentalChartCodec]).
 */
@Serializable
data class PatientForm(
    val id: Long? = null,
    val patientId: Long = 0,
    val templateId: String = FormTemplates.F025_2,
    val date: String = "",
    val fullName: String = "",
    val gender: String? = null,
    val address: String? = null,
    val age: Int? = null,
    val job: String? = null,
    val diagnosis: String? = null,
    val diagnosisCode: String? = null,
    val diagnosisDisplay: String? = null,
    val diagnosisSystem: String? = null,
    val complaints: String? = null,
    val otherIllnesses: String? = null,
    val moreDetails: String? = null,
    val visualCheckup: String? = null,
    val occlusion: String? = null,
    val oralCavityCondition: String? = null,
    val xrayLabData: String? = null,
    val treatment: String? = null,
    val treatmentResult: String? = null,
    val recommendations: String? = null,
    val doctorName: String? = null,
    val doctorClinic: String? = null,
    // Read leniently: an old row may hold it as a string.
    val formNumber: Int? = null,
    val dentalChart: Map<String, String> = emptyMap(),
    val followups: List<FormFollowup> = emptyList(),
    val documentId: Long? = null,
    val signatureRequested: Boolean = false,
    val patientSignedAt: String? = null,
    val patientSignatureImageBase64: String? = null,
    val clinicalDiseaseId: String? = null,
    val clinicalChipSelections: List<ClinicalChipSelection> = emptyList(),
    val createdByDoctorId: Long? = null,
) {
    val day: LocalDate? get() = runCatching { LocalDate.parse(date.take(10)) }.getOrNull()

    val signed: Boolean get() = !patientSignedAt.isNullOrBlank()

    /**
     * What a save sends (06 §3.3): the form without the server's own fields, dates as `YYYY-MM-DD`, an empty chart and
     * empty lists left out.
     */
    fun request(): FormRequest =
        FormRequest(
            id = id,
            patientId = patientId,
            templateId = templateId,
            date = isoDay(date),
            fullName = fullName,
            gender = gender,
            address = address,
            age = age,
            job = job,
            diagnosis = diagnosis,
            diagnosisCode = diagnosisCode,
            diagnosisDisplay = diagnosisDisplay,
            diagnosisSystem = diagnosisSystem?.let(::icdSystem),
            complaints = complaints,
            otherIllnesses = otherIllnesses,
            moreDetails = moreDetails,
            visualCheckup = visualCheckup,
            occlusion = occlusion,
            oralCavityCondition = oralCavityCondition,
            xrayLabData = xrayLabData,
            treatment = treatment,
            treatmentResult = treatmentResult,
            recommendations = recommendations,
            doctorName = doctorName,
            doctorClinic = doctorClinic,
            formNumber = formNumber,
            dentalChart = dentalChart.ifEmpty { null },
            followups = followups.map { it.copy(date = isoDay(it.date)) }.ifEmpty { null },
            documentId = documentId,
            clinicalDiseaseId = clinicalDiseaseId,
            clinicalChipSelections = clinicalChipSelections.ifEmpty { null },
        )

    companion object {
        /** One ICD system spelling (spec FORM-01 Fix): Flutter sent "ICD10" from the picker, "ICD-10" from the engine. */
        const val ICD10 = "ICD10"

        fun icdSystem(raw: String): String = if (raw.replace("-", "").equals("ICD10", ignoreCase = true)) ICD10 else raw

        /** A date or timestamp as `YYYY-MM-DD` (the backend's LocalDate); as written when it isn't one. */
        fun isoDay(raw: String): String = runCatching { LocalDate.parse(raw.take(10)).toString() }.getOrDefault(raw)
    }
}

/** `POST /patients/{id}/forms` and `PUT …/forms/{formId}`: null fields are left out. */
@Serializable
data class FormRequest(
    val id: Long? = null,
    val patientId: Long,
    val templateId: String,
    val date: String,
    val fullName: String,
    val gender: String? = null,
    val address: String? = null,
    val age: Int? = null,
    val job: String? = null,
    val diagnosis: String? = null,
    val diagnosisCode: String? = null,
    val diagnosisDisplay: String? = null,
    val diagnosisSystem: String? = null,
    val complaints: String? = null,
    val otherIllnesses: String? = null,
    val moreDetails: String? = null,
    val visualCheckup: String? = null,
    val occlusion: String? = null,
    val oralCavityCondition: String? = null,
    val xrayLabData: String? = null,
    val treatment: String? = null,
    val treatmentResult: String? = null,
    val recommendations: String? = null,
    val doctorName: String? = null,
    val doctorClinic: String? = null,
    val formNumber: Int? = null,
    val dentalChart: Map<String, String>? = null,
    val followups: List<FormFollowup>? = null,
    val documentId: Long? = null,
    val clinicalDiseaseId: String? = null,
    val clinicalChipSelections: List<ClinicalChipSelection>? = null,
)
