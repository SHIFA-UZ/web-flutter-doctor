/// Calendar domain models with consistent DateTime handling.
///
/// IMPORTANT: All DateTime fields are stored in LOCAL timezone for consistent display.
/// Backend sends ISO 8601 UTC strings - repositories must convert to local.

enum EntryType { appointment, freeSlot }

class CalendarEntry {
  final EntryType type;
  final DateTime startTime; // Local time (converted from UTC)
  final DateTime endTime; // Local time (converted from UTC)

  // Appointment-only fields
  final String? patientName;
  final bool isVideo;
  final String location;
  final String reason;

  CalendarEntry.appointment({
    required this.startTime,
    required this.endTime,
    required this.patientName,
    required this.location,
    required this.reason,
    this.isVideo = false,
  }) : type = EntryType.appointment;

  CalendarEntry.freeSlot({
    required this.startTime,
    required this.endTime,
  })  : type = EntryType.freeSlot,
        patientName = null,
        isVideo = false,
        location = '',
        reason = '';

  /// Get the date component (normalized to midnight local)
  DateTime get day => DateTime(startTime.year, startTime.month, startTime.day);

  /// Format time for display (HH:mm)
  static String formatTime(DateTime time) {
    return '${time.hour.toString().padLeft(2, '0')}:${time.minute.toString().padLeft(2, '0')}';
  }

  /// Format time range for display (HH:mm - HH:mm)
  String get timeRange => '${formatTime(startTime)} - ${formatTime(endTime)}';

  /// Convert to UTC for backend communication
  DateTime get startTimeUtc => startTime.toUtc();
  DateTime get endTimeUtc => endTime.toUtc();

  /// Convert to ISO 8601 UTC string for backend
  String get startAtIso => startTimeUtc.toIso8601String();
  String get endAtIso => endTimeUtc.toIso8601String();

  CalendarEntry copyWith({
    DateTime? startTime,
    DateTime? endTime,
    String? patientName,
    bool? isVideo,
    String? location,
    String? reason,
  }) {
    if (type == EntryType.appointment) {
      return CalendarEntry.appointment(
        startTime: startTime ?? this.startTime,
        endTime: endTime ?? this.endTime,
        patientName: patientName ?? this.patientName,
        location: location ?? this.location,
        reason: reason ?? this.reason,
        isVideo: isVideo ?? this.isVideo,
      );
    } else {
      return CalendarEntry.freeSlot(
        startTime: startTime ?? this.startTime,
        endTime: endTime ?? this.endTime,
      );
    }
  }
}
