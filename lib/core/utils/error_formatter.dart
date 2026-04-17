// lib/core/utils/error_formatter.dart
// Centralized error message sanitization for user display

import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';

/// Sanitize exception messages for user display.
/// Removes technical details and returns user-friendly localized message.
///
/// Usage:
/// ```dart
/// try {
///   await someApiCall();
/// } catch (e) {
///   final message = sanitizeErrorMessage(e, l10n);
///   ScaffoldMessenger.of(context).showSnackBar(
///     SnackBar(content: Text(message)),
///   );
/// }
/// ```
String sanitizeErrorMessage(dynamic error, AppLocalizations l10n) {
  final errorString = error.toString();

  // Strip "Exception: " prefix if present
  String message = errorString.replaceFirst(RegExp(r'^Exception:\s*'), '');

  // Map common error patterns to localized messages
  if (message.contains('Unauthorized') || message.contains('401')) {
    return l10n.translate('unauthorized') ?? 'Unauthorized. Please login again.';
  }

  if (message.contains('Failed to load') || message.contains('Failed to fetch')) {
    return l10n.translate('failedToLoad') ?? 'Failed to load data';
  }

  if (message.contains('Network') || message.contains('Connection') || message.contains('SocketException')) {
    return l10n.translate('networkError') ?? 'Network error. Please check your connection.';
  }

  if (message.contains('Timeout') || message.contains('timeout')) {
    return l10n.translate('requestTimeout') ?? 'Request timed out. Please try again.';
  }

  if (message.contains('403') || message.contains('Forbidden')) {
    return l10n.translate('accessDenied') ?? 'Access denied';
  }

  if (message.contains('404') || message.contains('Not found')) {
    return l10n.translate('notFound') ?? 'Resource not found';
  }

  if (message.contains('500') || message.contains('Internal Server Error')) {
    return l10n.translate('serverError') ?? 'Server error. Please try again later.';
  }

  // If message is too long (likely technical), show generic error
  if (message.length > 100) {
    return l10n.translate('somethingWentWrong') ?? 'Something went wrong';
  }

  // If message contains HTML or JSON, show generic error
  if (message.contains('<html') || message.contains('<!DOCTYPE') || message.contains('{')) {
    return l10n.translate('somethingWentWrong') ?? 'Something went wrong';
  }

  // If message is short and clean, return it (backend might send user-friendly messages)
  return message;
}

/// Format camelCase or snake_case text to Title Case for display.
///
/// Examples:
/// - "userCreated" → "User Created"
/// - "user_created" → "User Created"
/// - "USER_CREATED" → "User Created"
/// - "maxUploadSize" → "Max Upload Size"
///
/// Usage:
/// ```dart
/// Text(formatFieldName(backendFieldName))
/// ```
String formatFieldName(String text) {
  if (text.isEmpty) return text;

  // Handle UPPER_SNAKE_CASE or snake_case
  if (text.contains('_')) {
    return text
        .split('_')
        .map((word) => word.isEmpty
            ? ''
            : word[0].toUpperCase() + word.substring(1).toLowerCase())
        .join(' ');
  }

  // Handle camelCase
  final words = <String>[];
  final buffer = StringBuffer();

  for (int i = 0; i < text.length; i++) {
    final char = text[i];
    if (i > 0 && char == char.toUpperCase() && char != char.toLowerCase()) {
      words.add(buffer.toString());
      buffer.clear();
    }
    buffer.write(char);
  }
  if (buffer.isNotEmpty) words.add(buffer.toString());

  return words
      .map((word) => word.isEmpty
          ? ''
          : word[0].toUpperCase() + word.substring(1).toLowerCase())
      .join(' ');
}

/// Format backend enum values to user-friendly text.
///
/// Examples:
/// - "DOCTOR" → "Doctor"
/// - "PENDING" → "Pending"
/// - "VIDEO_CALL" → "Video Call"
String formatEnumValue(String value) {
  return formatFieldName(value);
}
