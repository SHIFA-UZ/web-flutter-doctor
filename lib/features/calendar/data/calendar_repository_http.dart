import 'package:dio/dio.dart';
import 'package:shifa_doc_app_v1/core/services/api_client.dart';
import 'package:shifa_doc_app_v1/features/calendar/domain/calendar_models.dart';

class CalendarRepositoryHttp {
  final Dio _dio = ApiClient().dio;

  /// Fetches calendar entries for a specific day from the backend.
  ///
  /// IMPORTANT: Backend sends ISO 8601 UTC timestamps (e.g., "2025-03-04T10:00:00Z")
  /// We parse these and convert to local timezone for display consistency.
  Future<List<CalendarEntry>> entriesFor(DateTime day) async {
    // Format date as YYYY-MM-DD for API
    final ymd =
        '${day.year}-${day.month.toString().padLeft(2, '0')}-${day.day.toString().padLeft(2, '0')}';

    final res = await _dio.get('/api/calendar?day=$ymd');
    final List data = res.data as List;

    return data.map((m) {
      // Backend sends ISO 8601 UTC strings: "2025-03-04T10:00:00Z"
      // Parse as UTC and convert to local time
      final startAtUtc = DateTime.parse(m['startAt'] as String);
      final endAtUtc = DateTime.parse(m['endAt'] as String);

      // Convert UTC to local time for display
      final startLocal = startAtUtc.toLocal();
      final endLocal = endAtUtc.toLocal();

      final typeStr = m['type'] as String;

      if (typeStr == 'APPOINTMENT') {
        return CalendarEntry.appointment(
          startTime: startLocal,
          endTime: endLocal,
          patientName: m['patientName'] as String? ?? 'Appointment',
          location: m['location'] as String? ?? 'Clinic',
          reason: m['reason'] as String? ?? 'Check Up',
          isVideo: (m['location'] as String? ?? '')
              .toLowerCase()
              .contains('video'),
        );
      } else {
        // FREE_SLOT
        return CalendarEntry.freeSlot(
          startTime: startLocal,
          endTime: endLocal,
        );
      }
    }).toList();
  }
}
