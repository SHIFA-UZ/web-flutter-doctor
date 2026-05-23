// lib/features/appointments/services/appointment_pdf_data.dart

import 'dart:typed_data';

/// One dental service line row for PDF rendering.
class AppointmentPdfDentalLine {
  final String tooth;
  final String serviceTitle;
  final int amountMinor;
  final String currency;

  const AppointmentPdfDentalLine({
    required this.tooth,
    required this.serviceTitle,
    required this.amountMinor,
    required this.currency,
  });
}

/// Structured stomatological billing section (replaces embedding plain text in notes).
class AppointmentPdfDentalBilling {
  final String header;
  final List<AppointmentPdfDentalLine> lines;
  final int subtotalMinor;
  final double? discountPercent;
  final int totalMinor;
  final String currency;

  const AppointmentPdfDentalBilling({
    required this.header,
    required this.lines,
    required this.subtotalMinor,
    this.discountPercent,
    required this.totalMinor,
    required this.currency,
  });
}

/// Input data for generating the appointment summary PDF.
/// All fields used in the report; optional fields can be null/empty.
class AppointmentPdfData {
  final String appointmentId;
  /// In-person clinic or location label (omit for video consultations).
  final String? clinicName;

  final String patientName;
  final String? patientId;
  final String? dateOfBirth;
  final String? gender;

  final String? doctorName;
  final String? specialization;
  final String? licenseNumber;

  final String appointmentType; // 'Video' | 'Face-to-face' (or localized)
  final String? duration; // e.g. "30 min"

  final String dateStr; // yyyy-MM-dd
  final String timeStr; // HH:mm
  /// Appointment date (used to format dayOfWeek and fullDate in the PDF).
  final DateTime appointmentDate;

  /// Doctor's clinical notes (do NOT translate).
  final String? notes;

  /// Optional structured dental billing table (shown when non-null with lines).
  final AppointmentPdfDentalBilling? dentalBilling;

  /// Optional sections (render only if non-empty).
  final String? diagnosis;
  final String? diagnosisCode;
  final String? diagnosisDisplay;
  final String? diagnosisSystem; // e.g. "ICD10"
  final String? prescriptions;
  final String? recommendations;
  final String? followUpDate;

  /// Patient digital signature (MVP): image bytes and signed-at time for PDF.
  final Uint8List? patientSignatureImageBytes;
  final DateTime? patientSignedAt;

  /// When true (dental documentation mode), PDF includes Uzbek sterilization attestation
  /// beside the patient signature.
  final bool isDentalDocumentation;

  const AppointmentPdfData({
    required this.appointmentId,
    this.clinicName,
    required this.patientName,
    this.patientId,
    this.dateOfBirth,
    this.gender,
    this.doctorName,
    this.specialization,
    this.licenseNumber,
    required this.appointmentType,
    this.duration,
    required this.dateStr,
    required this.timeStr,
    required this.appointmentDate,
    this.notes,
    this.dentalBilling,
    this.diagnosis,
    this.diagnosisCode,
    this.diagnosisDisplay,
    this.diagnosisSystem,
    this.prescriptions,
    this.recommendations,
    this.followUpDate,
    this.patientSignatureImageBytes,
    this.patientSignedAt,
    this.isDentalDocumentation = false,
  });
}
