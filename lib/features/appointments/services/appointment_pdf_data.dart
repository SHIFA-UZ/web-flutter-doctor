// lib/features/appointments/services/appointment_pdf_data.dart

import 'dart:typed_data';

/// Input data for generating the appointment summary PDF.
/// All fields used in the report; optional fields can be null/empty.
class AppointmentPdfData {
  final String appointmentId;
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
