package shifa.doctor.ui.documents

import org.jetbrains.compose.resources.StringResource
import shifa.doctor.data.model.DocumentCategory
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.documentCategory_ALLERGY_REPORT
import shifa.doctor.generated.resources.documentCategory_APPOINTMENT_NOTE
import shifa.doctor.generated.resources.documentCategory_BIOPSY
import shifa.doctor.generated.resources.documentCategory_BLOOD_TEST
import shifa.doctor.generated.resources.documentCategory_CT_SCAN
import shifa.doctor.generated.resources.documentCategory_DISCHARGE_SUMMARY
import shifa.doctor.generated.resources.documentCategory_ECG
import shifa.doctor.generated.resources.documentCategory_EEG
import shifa.doctor.generated.resources.documentCategory_ENDOSCOPY
import shifa.doctor.generated.resources.documentCategory_FORM_025_2
import shifa.doctor.generated.resources.documentCategory_HOSPITAL_REPORT
import shifa.doctor.generated.resources.documentCategory_IMAGING_OTHER
import shifa.doctor.generated.resources.documentCategory_INTERNAL_NOTE
import shifa.doctor.generated.resources.documentCategory_LAB_RESULT
import shifa.doctor.generated.resources.documentCategory_MAMMOGRAPHY
import shifa.doctor.generated.resources.documentCategory_MRI
import shifa.doctor.generated.resources.documentCategory_OTHER_MEDICAL
import shifa.doctor.generated.resources.documentCategory_OTHER_PRIVATE
import shifa.doctor.generated.resources.documentCategory_PATHOLOGY
import shifa.doctor.generated.resources.documentCategory_PRESCRIPTION
import shifa.doctor.generated.resources.documentCategory_PRE_APPOINTMENT
import shifa.doctor.generated.resources.documentCategory_REFERRAL
import shifa.doctor.generated.resources.documentCategory_REMOTE_TASK_DOCUMENT
import shifa.doctor.generated.resources.documentCategory_STOOL_TEST
import shifa.doctor.generated.resources.documentCategory_ULTRASOUND
import shifa.doctor.generated.resources.documentCategory_URINE_TEST
import shifa.doctor.generated.resources.documentCategory_VACCINATION_RECORD
import shifa.doctor.generated.resources.documentCategory_XRAY

/** The category's name in the app's language (06 §4.1; the keys Flutter used). */
val DocumentCategory.label: StringResource
    get() =
        when (this) {
            DocumentCategory.BLOOD_TEST -> Res.string.documentCategory_BLOOD_TEST
            DocumentCategory.URINE_TEST -> Res.string.documentCategory_URINE_TEST
            DocumentCategory.STOOL_TEST -> Res.string.documentCategory_STOOL_TEST
            DocumentCategory.LAB_RESULT -> Res.string.documentCategory_LAB_RESULT
            DocumentCategory.MRI -> Res.string.documentCategory_MRI
            DocumentCategory.CT_SCAN -> Res.string.documentCategory_CT_SCAN
            DocumentCategory.XRAY -> Res.string.documentCategory_XRAY
            DocumentCategory.ULTRASOUND -> Res.string.documentCategory_ULTRASOUND
            DocumentCategory.MAMMOGRAPHY -> Res.string.documentCategory_MAMMOGRAPHY
            DocumentCategory.ECG -> Res.string.documentCategory_ECG
            DocumentCategory.EEG -> Res.string.documentCategory_EEG
            DocumentCategory.ENDOSCOPY -> Res.string.documentCategory_ENDOSCOPY
            DocumentCategory.BIOPSY -> Res.string.documentCategory_BIOPSY
            DocumentCategory.PATHOLOGY -> Res.string.documentCategory_PATHOLOGY
            DocumentCategory.IMAGING_OTHER -> Res.string.documentCategory_IMAGING_OTHER
            DocumentCategory.PRESCRIPTION -> Res.string.documentCategory_PRESCRIPTION
            DocumentCategory.VACCINATION_RECORD -> Res.string.documentCategory_VACCINATION_RECORD
            DocumentCategory.DISCHARGE_SUMMARY -> Res.string.documentCategory_DISCHARGE_SUMMARY
            DocumentCategory.REFERRAL -> Res.string.documentCategory_REFERRAL
            DocumentCategory.HOSPITAL_REPORT -> Res.string.documentCategory_HOSPITAL_REPORT
            DocumentCategory.ALLERGY_REPORT -> Res.string.documentCategory_ALLERGY_REPORT
            DocumentCategory.OTHER_MEDICAL -> Res.string.documentCategory_OTHER_MEDICAL
            DocumentCategory.PRE_APPOINTMENT -> Res.string.documentCategory_PRE_APPOINTMENT
            DocumentCategory.APPOINTMENT_NOTE -> Res.string.documentCategory_APPOINTMENT_NOTE
            DocumentCategory.REMOTE_TASK_DOCUMENT -> Res.string.documentCategory_REMOTE_TASK_DOCUMENT
            DocumentCategory.INTERNAL_NOTE -> Res.string.documentCategory_INTERNAL_NOTE
            DocumentCategory.OTHER_PRIVATE -> Res.string.documentCategory_OTHER_PRIVATE
            DocumentCategory.FORM_025_2 -> Res.string.documentCategory_FORM_025_2
        }
