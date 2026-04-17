/// Utility functions for consistent time handling across the app.
///
/// CRITICAL: Backend sends times as ISO 8601 UTC strings (e.g., "2025-03-04T10:00:00Z")
/// All times should be converted to local timezone for display.
/// When sending to backend, convert back to UTC and format as ISO 8601.

class TimeUtils {
  /// Parse ISO 8601 UTC string from backend and convert to local time
  ///
  /// Backend format: "2025-03-04T10:00:00Z" (UTC)
  /// Returns: DateTime in local timezone for display
  static DateTime parseUtcToLocal(String isoUtcString) {
    final utcTime = DateTime.parse(isoUtcString);
    return utcTime.toLocal();
  }

  /// Convert local DateTime to ISO 8601 UTC string for backend
  ///
  /// Input: DateTime in local timezone
  /// Returns: "2025-03-04T10:00:00.000Z" (UTC)
  static String formatLocalToUtcIso(DateTime localTime) {
    return localTime.toUtc().toIso8601String();
  }

  /// Format DateTime as HH:mm for display (assumes local time)
  static String formatTime(DateTime time) {
    return '${time.hour.toString().padLeft(2, '0')}:${time.minute.toString().padLeft(2, '0')}';
  }

  /// Format time range as "HH:mm - HH:mm" (assumes local times)
  static String formatTimeRange(DateTime start, DateTime end) {
    return '${formatTime(start)} - ${formatTime(end)}';
  }

  /// Format date as "DD Month YYYY"
  static String formatDate(DateTime date) {
    return '${date.day} ${_monthName(date.month)} ${date.year}';
  }

  /// Format date as "DD.MM.YYYY"
  static String formatDateShort(DateTime date) {
    return '${date.day.toString().padLeft(2, '0')}.${date.month.toString().padLeft(2, '0')}.${date.year}';
  }

  /// Format date as YYYY-MM-DD for API calls
  static String formatDateForApi(DateTime date) {
    return '${date.year}-${date.month.toString().padLeft(2, '0')}-${date.day.toString().padLeft(2, '0')}';
  }

  /// Format DateTime as "DD Month YYYY, HH:mm" (assumes local time)
  static String formatDateTime(DateTime dateTime) {
    return '${formatDate(dateTime)}, ${formatTime(dateTime)}';
  }

  /// Normalize a DateTime to midnight local time (removes time component)
  static DateTime normalizeDate(DateTime date) {
    return DateTime(date.year, date.month, date.day);
  }

  /// Check if two DateTimes are on the same day (ignoring time)
  static bool isSameDay(DateTime a, DateTime b) {
    return a.year == b.year && a.month == b.month && a.day == b.day;
  }

  /// Get a list of month names
  static String _monthName(int month) {
    const months = [
      'January',
      'February',
      'March',
      'April',
      'May',
      'June',
      'July',
      'August',
      'September',
      'October',
      'November',
      'December',
    ];
    return months[month - 1];
  }

  /// Format duration in minutes as "Xh Ym" or "Ym"
  static String formatDuration(int minutes) {
    final h = minutes ~/ 60;
    final m = minutes % 60;
    if (h > 0) {
      return m > 0 ? '${h}h ${m}m' : '${h}h';
    }
    return '${m}m';
  }

  /// Calculate duration between two DateTimes in minutes
  static int durationInMinutes(DateTime start, DateTime end) {
    return end.difference(start).inMinutes;
  }

  /// Get current time in local timezone
  static DateTime nowLocal() {
    return DateTime.now();
  }

  /// Get current time in UTC
  static DateTime nowUtc() {
    return DateTime.now().toUtc();
  }
}
