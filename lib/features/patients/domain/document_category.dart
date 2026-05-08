import 'package:flutter/material.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';

/// Document category codes shared with the backend.
///
/// The first group ("medical results") makes a doctor upload visible to every
/// doctor that has access to the patient. The second group ("doctor private")
/// keeps the document creator-only + access-grant model.
class DocumentCategory {
  /// Stable wire/storage code (must match the Kotlin enum
  /// `PatientDocumentCategory.code`).
  final String code;
  final IconData icon;
  final bool isMedicalResult;

  const DocumentCategory({
    required this.code,
    required this.icon,
    required this.isMedicalResult,
  });

  /// Localized display label looked up via [AppLocalizations.translate] using
  /// the key `documentCategory.<code>` and falling back to a humanised version
  /// of [code] if no translation is registered.
  String label(AppLocalizations l10n) {
    final key = 'documentCategory_$code';
    final translated = l10n.translate(key);
    if (translated != null && translated.isNotEmpty) return translated;
    return code
        .split('_')
        .map((p) => p.isEmpty ? p : '${p[0]}${p.substring(1).toLowerCase()}')
        .join(' ');
  }
}

const List<DocumentCategory> kMedicalResultCategories = [
  DocumentCategory(code: 'BLOOD_TEST', icon: Icons.bloodtype, isMedicalResult: true),
  DocumentCategory(code: 'URINE_TEST', icon: Icons.science, isMedicalResult: true),
  DocumentCategory(code: 'STOOL_TEST', icon: Icons.science, isMedicalResult: true),
  DocumentCategory(code: 'LAB_RESULT', icon: Icons.biotech, isMedicalResult: true),
  DocumentCategory(code: 'MRI', icon: Icons.medical_services, isMedicalResult: true),
  DocumentCategory(code: 'CT_SCAN', icon: Icons.medical_services, isMedicalResult: true),
  DocumentCategory(code: 'XRAY', icon: Icons.medical_information, isMedicalResult: true),
  DocumentCategory(code: 'ULTRASOUND', icon: Icons.monitor_heart, isMedicalResult: true),
  DocumentCategory(code: 'MAMMOGRAPHY', icon: Icons.medical_services, isMedicalResult: true),
  DocumentCategory(code: 'ECG', icon: Icons.monitor_heart, isMedicalResult: true),
  DocumentCategory(code: 'EEG', icon: Icons.psychology, isMedicalResult: true),
  DocumentCategory(code: 'ENDOSCOPY', icon: Icons.medical_information, isMedicalResult: true),
  DocumentCategory(code: 'BIOPSY', icon: Icons.biotech, isMedicalResult: true),
  DocumentCategory(code: 'PATHOLOGY', icon: Icons.biotech, isMedicalResult: true),
  DocumentCategory(code: 'IMAGING_OTHER', icon: Icons.image, isMedicalResult: true),
  DocumentCategory(code: 'PRESCRIPTION', icon: Icons.medication, isMedicalResult: true),
  DocumentCategory(code: 'VACCINATION_RECORD', icon: Icons.vaccines, isMedicalResult: true),
  DocumentCategory(code: 'DISCHARGE_SUMMARY', icon: Icons.local_hospital, isMedicalResult: true),
  DocumentCategory(code: 'REFERRAL', icon: Icons.assignment, isMedicalResult: true),
  DocumentCategory(code: 'HOSPITAL_REPORT', icon: Icons.local_hospital, isMedicalResult: true),
  DocumentCategory(code: 'ALLERGY_REPORT', icon: Icons.warning_amber, isMedicalResult: true),
  DocumentCategory(code: 'OTHER_MEDICAL', icon: Icons.folder_shared, isMedicalResult: true),
];

const List<DocumentCategory> kPrivateCategories = [
  DocumentCategory(code: 'APPOINTMENT_NOTE', icon: Icons.note_alt, isMedicalResult: false),
  DocumentCategory(code: 'REMOTE_TASK_DOCUMENT', icon: Icons.assignment_late, isMedicalResult: false),
  DocumentCategory(code: 'INTERNAL_NOTE', icon: Icons.lock_outline, isMedicalResult: false),
  DocumentCategory(code: 'OTHER_PRIVATE', icon: Icons.folder, isMedicalResult: false),
];

/// All categories the doctor app exposes in the upload picker.
List<DocumentCategory> get kAllDoctorCategories =>
    [...kMedicalResultCategories, ...kPrivateCategories];

DocumentCategory? findDoctorCategory(String? code) {
  if (code == null || code.isEmpty) return null;
  for (final c in kAllDoctorCategories) {
    if (c.code == code) return c;
  }
  return null;
}
