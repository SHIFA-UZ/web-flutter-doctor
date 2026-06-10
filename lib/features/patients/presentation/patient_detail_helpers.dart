import 'package:flutter/material.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/theme/app_colors.dart';
import 'package:shifa_doc_app_v1/core/theme/app_design_system.dart';
import 'package:shifa_doc_app_v1/features/patients/domain/patient_models.dart';

enum PatientListTab { all, recent, favorites, followUps }

enum PatientSortOption { nameAsc, nameDesc, recent }

enum PatientStatusKind { active, atRisk, followUp }

class PatientStatusInfo {
  const PatientStatusInfo({required this.kind, required this.label});

  final PatientStatusKind kind;
  final String label;

  Color get backgroundColor {
    switch (kind) {
      case PatientStatusKind.active:
        return AppColors.primaryTeal.withValues(alpha: 0.12);
      case PatientStatusKind.atRisk:
        return const Color(0xFFFFF7ED);
      case PatientStatusKind.followUp:
        return const Color(0xFFF5F3FF);
    }
  }

  Color get textColor {
    switch (kind) {
      case PatientStatusKind.active:
        return AppColors.primaryTeal;
      case PatientStatusKind.atRisk:
        return AppDesignSystem.warning;
      case PatientStatusKind.followUp:
        return const Color(0xFF7C3AED);
    }
  }
}

int? patientAge(Patient patient) {
  final birthDate = patient.general.birthDate;
  if (birthDate == null) return null;
  final now = DateTime.now();
  var age = now.year - birthDate.year;
  if (now.month < birthDate.month ||
      (now.month == birthDate.month && now.day < birthDate.day)) {
    age--;
  }
  return age;
}

PatientStatusKind patientStatusKind(Patient patient) {
  switch (patient.clinicalStatus) {
    case 'AT_RISK':
      return PatientStatusKind.atRisk;
    case 'FOLLOW_UP':
      return PatientStatusKind.followUp;
    default:
      return PatientStatusKind.active;
  }
}

PatientStatusInfo patientStatus(Patient patient, AppLocalizations l10n) {
  final kind = patientStatusKind(patient);
  return PatientStatusInfo(
    kind: kind,
    label: _patientStatusLabel(kind, l10n),
  );
}

String _patientStatusLabel(PatientStatusKind kind, AppLocalizations l10n) {
  switch (kind) {
    case PatientStatusKind.atRisk:
      return l10n.translate('patientStatusAtRisk') ?? 'At Risk';
    case PatientStatusKind.followUp:
      return l10n.translate('patientStatusFollowUp') ?? 'Follow-up';
    case PatientStatusKind.active:
      return l10n.translate('patientStatusActive') ?? 'Active';
  }
}

String patientGenderLabel(
  Patient patient,
  AppLocalizations l10n, {
  String? fromForm,
}) {
  if (fromForm != null && fromForm.trim().isNotEmpty) {
    return translateGenderValue(l10n, fromForm.trim());
  }
  final gender = patient.general.gender;
  if (gender != null && gender.trim().isNotEmpty) {
    return translateGenderValue(l10n, gender.trim());
  }
  return '—';
}

/// Translates a raw gender value (e.g. 'Male') into the current locale.
String translateGenderValue(AppLocalizations l10n, String raw) {
  switch (raw.toLowerCase()) {
    case 'male':
      return l10n.translate('genderMale');
    case 'female':
      return l10n.translate('genderFemale');
    case 'other':
      return l10n.translate('genderOther');
    default:
      return raw;
  }
}

String patientAppointmentStatusLabel(AppLocalizations l10n, String status) {
  switch (status.trim().toUpperCase()) {
    case 'REQUESTED':
      return l10n.appointmentStatusRequested;
    case 'CONFIRMED':
      return l10n.appointmentStatusConfirmed;
    case 'CANCELLED':
      return l10n.appointmentStatusCancelled;
    case 'COMPLETED':
      return l10n.appointmentStatusCompleted;
    case 'IN_PROGRESS':
      return l10n.appointmentStatusInProgress;
    default:
      return status;
  }
}

String patientBloodGroupLabel(Patient patient, AppLocalizations l10n) {
  final value = patient.general.bloodGroup;
  if (value != null && value.trim().isNotEmpty) return value.trim();
  return l10n.translate('notSpecified') ?? '—';
}

String patientAllergiesLabel(Patient patient, AppLocalizations l10n) {
  final value = patient.general.allergies;
  if (value != null && value.trim().isNotEmpty) return value.trim();
  return l10n.translate('noKnownAllergies') ?? 'No known allergies';
}

String patientLanguageDisplay(Patient patient) {
  final language = patient.general.language;
  if (language == null || language.isEmpty) return '—';
  return language[0].toUpperCase() + language.substring(1);
}

String patientPhoneDisplay(Patient patient) {
  final phones = patient.general.allPhones;
  if (phones.isEmpty) return '—';
  return phones.first;
}

String formatPatientBirthDate(Patient patient) {
  final birthDate = patient.general.birthDate;
  if (birthDate == null) return '—';
  String two(int n) => n.toString().padLeft(2, '0');
  return '${two(birthDate.day)}.${two(birthDate.month)}.${birthDate.year}';
}

String patientDisplayId(Patient patient) {
  final id = int.tryParse(patient.id);
  if (id != null) return 'P-${id.toString().padLeft(5, '0')}';
  return 'P-${patient.id}';
}

DateTime? patientLastActivityDate(Patient patient) {
  if (patient.documents.isEmpty) return null;
  return patient.documents
      .map((d) => d.date)
      .reduce((a, b) => a.isAfter(b) ? a : b);
}

String patientLastVisitLabel(Patient patient, AppLocalizations l10n) {
  final last = patientLastActivityDate(patient);
  if (last == null) {
    return l10n.translate('noRecentVisit') ?? 'No recent visit';
  }
  const months = [
    'Jan',
    'Feb',
    'Mar',
    'Apr',
    'May',
    'Jun',
    'Jul',
    'Aug',
    'Sep',
    'Oct',
    'Nov',
    'Dec',
  ];
  final prefix = l10n.translate('lastVisit') ?? 'Last visit';
  return '$prefix: ${last.day} ${months[last.month - 1]} ${last.year}';
}

List<Patient> filterPatientsByTab(
  List<Patient> patients,
  PatientListTab tab,
  Set<String> favoriteIds,
) {
  switch (tab) {
    case PatientListTab.all:
      return patients;
    case PatientListTab.recent:
      return patients.length <= 20
          ? List<Patient>.from(patients.reversed)
          : patients.reversed.take(20).toList();
    case PatientListTab.favorites:
      return patients.where((p) => favoriteIds.contains(p.id)).toList();
    case PatientListTab.followUps:
      return patients
          .where((p) => patientStatusKind(p) == PatientStatusKind.followUp)
          .toList();
  }
}

List<Patient> sortPatients(
  List<Patient> patients,
  PatientSortOption sort,
) {
  final sorted = List<Patient>.from(patients);
  switch (sort) {
    case PatientSortOption.nameAsc:
      sorted.sort((a, b) => a.name.compareTo(b.name));
    case PatientSortOption.nameDesc:
      sorted.sort((a, b) => b.name.compareTo(a.name));
    case PatientSortOption.recent:
      sorted.sort((a, b) {
        final aDate = patientLastActivityDate(a);
        final bDate = patientLastActivityDate(b);
        if (aDate == null && bDate == null) {
          return b.name.compareTo(a.name);
        }
        if (aDate == null) return 1;
        if (bDate == null) return -1;
        return bDate.compareTo(aDate);
      });
  }
  return sorted;
}

class PatientActivityItem {
  const PatientActivityItem({
    required this.title,
    required this.subtitle,
    required this.date,
    required this.icon,
    required this.iconColor,
  });

  final String title;
  final String subtitle;
  final DateTime date;
  final IconData icon;
  final Color iconColor;
}

List<PatientActivityItem> buildPatientActivities(
  Patient patient,
  AppLocalizations l10n,
  Color brand,
) {
  final items = <PatientActivityItem>[];
  for (final doc in patient.documents) {
    final lower = doc.title.toLowerCase();
    IconData icon = Icons.insert_drive_file_outlined;
    Color color = brand;
    String title =
        l10n.translate('activityDocumentUploaded') ?? 'Document uploaded';
    String subtitle = doc.title;

    if (lower.contains('blood') || doc.category == 'BLOOD_TEST') {
      icon = Icons.science_outlined;
      color = const Color(0xFF0284C7);
      title = l10n.translate('activityLabResult') ?? 'Lab result uploaded';
      subtitle = doc.title;
    } else if (lower.contains('mri') || doc.category == 'MRI') {
      icon = Icons.description_outlined;
      title = l10n.translate('activityDocumentUploaded') ?? 'Document uploaded';
      subtitle = doc.title;
    } else if (lower.startsWith('form')) {
      icon = Icons.medication_outlined;
      color = AppDesignSystem.warning;
      title = l10n.translate('activityPrescription') ?? 'Prescription issued';
      subtitle = doc.title;
    }

    items.add(
      PatientActivityItem(
        title: title,
        subtitle: subtitle,
        date: doc.date,
        icon: icon,
        iconColor: color,
      ),
    );
  }

  items.sort((a, b) => b.date.compareTo(a.date));
  return items.take(6).toList();
}

List<String> buildAiFollowUpSuggestions(Patient patient, AppLocalizations l10n) {
  final suggestions = <String>[];
  if (patient.atRisk) {
    suggestions.add(
      l10n.translate('aiFollowUpChronic') ??
          'Schedule follow-up to review chronic condition management.',
    );
  }
  if (patient.followUpRequired) {
    suggestions.add(
      l10n.translate('aiFollowUpProphylaxis') ??
          'Prophylaxis or active care task requires follow-up scheduling.',
    );
  }
  if (patient.documents.isEmpty) {
    suggestions.add(
      l10n.translate('aiFollowUpDocuments') ??
          'Upload baseline labs to improve AI clinical insights.',
    );
  }
  if (!patient.hasAccount) {
    suggestions.add(
      l10n.translate('aiFollowUpPortal') ??
          'Create a patient portal account for appointment reminders.',
    );
  }
  if (suggestions.isEmpty) {
    suggestions.add(
      l10n.translate('aiFollowUpRoutine') ??
          'Routine check-up recommended within the next 6 months.',
    );
  }
  return suggestions.take(3).toList();
}
