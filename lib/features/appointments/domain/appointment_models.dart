// lib/features/appointments/domain/appointment_models.dart
import 'package:flutter/material.dart';

enum AppointmentStatus {
  requested,
  confirmed,
  cancelled,
  completed,
  inProgress;

  static AppointmentStatus? fromString(String? status) {
    if (status == null) return null;
    switch (status.toUpperCase()) {
      case 'REQUESTED':
        return AppointmentStatus.requested;
      case 'CONFIRMED':
        return AppointmentStatus.confirmed;
      case 'CANCELLED':
        return AppointmentStatus.cancelled;
      case 'COMPLETED':
        return AppointmentStatus.completed;
      case 'IN_PROGRESS':
        return AppointmentStatus.inProgress;
      default:
        return null;
    }
  }
}

class Appointment {
  final String id;
  final String patientName;
  final String? patientId; // Patient ID for fetching documents
  final String location; // 'Video Consultation' or clinic name
  final TimeOfDay start;
  final TimeOfDay end;
  final AppointmentStatus? status; // Appointment status from backend

  /// Visit reason / complaint from calendar booking.
  final String? reason;

  /// ✅ New: optional public photo URL (CDN/Firebase/etc.)
  final String? photoUrl;

  /// Pre-appointment AI briefing status: PENDING|READY|FAILED|SKIPPED|null
  final String? briefingStatus;

  /// Count of documents attached at booking time.
  final int attachmentCount;

  const Appointment({
    required this.id,
    required this.patientName,
    this.patientId,
    required this.location,
    required this.start,
    required this.end,
    this.status,
    this.reason,
    this.photoUrl,
    this.briefingStatus,
    this.attachmentCount = 0,
  });

  bool get isVideo => location.toLowerCase().contains('video');
  bool get isCompleted => status == AppointmentStatus.completed;
  bool get isInProgress => status == AppointmentStatus.inProgress;
  bool get hasVisitBriefing =>
      briefingStatus == 'READY' ||
      briefingStatus == 'PENDING' ||
      briefingStatus == 'FAILED' ||
      attachmentCount > 0;
}
