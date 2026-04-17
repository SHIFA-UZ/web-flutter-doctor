// lib/features/appointments/domain/appointment_models.dart

/// Represents an appointment with a patient.
///
/// IMPORTANT: All DateTime fields are stored in LOCAL timezone for consistent display.
/// Backend sends ISO 8601 UTC strings - repositories must convert to local.
class Appointment {
  final String id;
  final String? doctorId;
  final String? patientId;
  final String patientName;
  final String location; // 'Video Consultation' or clinic name
  final DateTime startTime; // Local time (converted from UTC)
  final DateTime endTime; // Local time (converted from UTC)

  const Appointment({
    required this.id,
    this.doctorId,
    this.patientId,
    required this.patientName,
    required this.location,
    required this.startTime,
    required this.endTime,
  });

  bool get isVideo => location.toLowerCase().contains('video');

  /// Format time for display (HH:mm)
  static String formatTime(DateTime time) {
    return '${time.hour.toString().padLeft(2, '0')}:${time.minute.toString().padLeft(2, '0')}';
  }

  /// Format time range for display (HH:mm - HH:mm)
  String get timeRange => '${formatTime(startTime)} - ${formatTime(endTime)}';

  /// Get the date component (normalized to midnight local)
  DateTime get day => DateTime(startTime.year, startTime.month, startTime.day);

  /// Convert to UTC for backend communication
  DateTime get startTimeUtc => startTime.toUtc();
  DateTime get endTimeUtc => endTime.toUtc();

  /// Convert to ISO 8601 UTC string for backend
  String get startAtIso => startTimeUtc.toIso8601String();
  String get endAtIso => endTimeUtc.toIso8601String();

  Appointment copyWith({
    String? id,
    String? doctorId,
    String? patientId,
    String? patientName,
    String? location,
    DateTime? startTime,
    DateTime? endTime,
  }) {
    return Appointment(
      id: id ?? this.id,
      doctorId: doctorId ?? this.doctorId,
      patientId: patientId ?? this.patientId,
      patientName: patientName ?? this.patientName,
      location: location ?? this.location,
      startTime: startTime ?? this.startTime,
      endTime: endTime ?? this.endTime,
    );
  }
}
