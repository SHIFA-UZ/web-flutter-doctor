import 'package:flutter/material.dart';
import 'package:shifa_doc_app_v1/core/services/push_notification_service.dart';
import 'package:shifa_doc_app_v1/core/theme/app_colors.dart';
import 'package:shifa_doc_app_v1/core/utils/timezone_utils.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/features/notifications/domain/notification_model.dart';

/// Relative time for notifications: "5 min ago", "Yesterday 23:11", "Mar 5 • 23:11".
String formatNotificationTime(DateTime dateTime, AppLocalizations l10n) {
  final now = DateTime.now();
  final local = dateTime.toLocal();
  final today = DateTime(now.year, now.month, now.day);
  final yesterday = today.subtract(const Duration(days: 1));
  final dtDate = DateTime(local.year, local.month, local.day);

  final diff = now.difference(local);
  if (diff.inMinutes < 1) return l10n.timeJustNow;
  if (diff.inMinutes < 60) return l10n.timeMinAgo(diff.inMinutes);
  final h = local.hour.toString().padLeft(2, '0');
  final m = local.minute.toString().padLeft(2, '0');
  final timeStr = '$h:$m';
  if (diff.inHours < 24 && dtDate == today) return timeStr;
  if (dtDate == yesterday) return l10n.timeYesterday(timeStr);
  final monthStr = l10n.monthShort(local.month);
  return '$monthStr ${local.day} • $timeStr';
}

/// Section header: "Today", "Yesterday", or "Mar 5".
String dateSectionLabel(DateTime dateTime, AppLocalizations l10n) {
  final now = DateTime.now();
  final local = dateTime.toLocal();
  final today = DateTime(now.year, now.month, now.day);
  final yesterday = today.subtract(const Duration(days: 1));
  final dtDate = DateTime(local.year, local.month, local.day);

  if (dtDate == today) return l10n.today;
  if (dtDate == yesterday) return l10n.notificationYesterday;
  final monthStr = l10n.monthShort(local.month);
  return '$monthStr ${local.day}';
}

const String _kEnglishPatientBookedSuffix = ' booked an appointment.';
const String _kEnglishPatientBookedPrefix = 'Patient ';
const String _kEnglishBookedForMarker = ' booked an appointment for ';
const String _kEnglishBookedAtMarker = ' at ';
const String _kEnglishReminderMessage =
    'Your appointment is in about 1 hour. Please be ready.';

const Map<String, int> _kEnglishMonthNumbers = {
  'january': 1,
  'jan': 1,
  'february': 2,
  'feb': 2,
  'march': 3,
  'mar': 3,
  'april': 4,
  'apr': 4,
  'may': 5,
  'june': 6,
  'jun': 6,
  'july': 7,
  'jul': 7,
  'august': 8,
  'aug': 8,
  'september': 9,
  'sep': 9,
  'sept': 9,
  'october': 10,
  'oct': 10,
  'november': 11,
  'nov': 11,
  'december': 12,
  'dec': 12,
};

/// Localized notification body. For known types (e.g. appointment booked, reminder) returns
/// a locale-specific message; otherwise returns the backend message.
/// Also translates by content so old notifications with English text are localized.
String localizedNotificationMessage(
  DoctorNotificationModel notification,
  AppLocalizations l10n, {
  String? timeZone,
}) {
  final type = notification.type.trim();
  final message = notification.message.trim();

  switch (type) {
    case 'APPOINTMENT_BOOKED_BY_PATIENT':
      return _localizedPatientBooked(notification, l10n, timeZone: timeZone);
    case 'APPOINTMENT_REMINDER':
      return l10n.notificationMessageAppointmentReminder;
    default:
      break;
  }

  // Content-based fallback: translate known English messages even when type didn't match
  // (e.g. old API, different serialization, or cached response).
  if (message == _kEnglishReminderMessage) {
    return l10n.notificationMessageAppointmentReminder;
  }
  if (message.startsWith(_kEnglishPatientBookedPrefix) &&
      message.endsWith(_kEnglishPatientBookedSuffix)) {
    final name = message
        .substring(
          _kEnglishPatientBookedPrefix.length,
          message.length - _kEnglishPatientBookedSuffix.length,
        )
        .trim();
    return l10n.notificationMessagePatientBookedAppointmentNoTime(name.isEmpty ? 'Patient' : name);
  }
  final parsed = _parseEnglishBookedWithDateTime(message);
  if (parsed != null) {
    final dateStr =
        '${parsed.day} ${l10n.monthShort(parsed.month)} ${parsed.year}';
    return l10n.notificationMessagePatientBookedAppointment(
      parsed.name.isEmpty ? 'Patient' : parsed.name,
      dateStr,
      parsed.time,
    );
  }

  return notification.message;
}

/// Payload for the global notification tap handler (FCM + in-app taps).
Map<String, dynamic> notificationNavigationPayload(
  DoctorNotificationModel notification,
) {
  return {
    'notificationId': notification.id,
    'type': notification.type,
    if (notification.appointmentId != null)
      'appointmentId': notification.appointmentId,
    if (notification.appointmentStartAt != null)
      'appointmentStartAt':
          notification.appointmentStartAt!.toUtc().toIso8601String(),
    if (notification.patientId != null) 'patientId': notification.patientId,
    if (notification.documentId != null) 'documentId': notification.documentId,
    if (notification.documentTitle != null)
      'documentTitle': notification.documentTitle,
    if (notification.documentAccessRequestId != null)
      'documentAccessRequestId': notification.documentAccessRequestId,
    if (notification.taskId != null) 'taskId': notification.taskId,
  };
}

/// Opens calendar / patient / task details using the same routing as push taps.
void navigateToNotificationTarget(DoctorNotificationModel notification) {
  PushNotificationService()
      .deliverPayloadFromApp(notificationNavigationPayload(notification));
}

/// Localized notification title. Prefers type-based labels over raw backend English titles.
String localizedNotificationTitle(
  DoctorNotificationModel notification,
  AppLocalizations l10n,
) {
  final typeLabel = humanLabelForNotificationType(notification.type, l10n);
  if (typeLabel != l10n.notificationGeneric) return typeLabel;

  switch (notification.title.trim()) {
    case 'New Appointment Booked':
      return l10n.notificationTypeAppointmentBooked;
    case 'Appointment cancelled':
    case 'Appointment Cancelled':
      return l10n.notificationTypeAppointmentCancelled;
    case 'Task completed':
    case 'Task Completed':
      return l10n.notificationTypeTaskCompleted;
    case 'Task assigned':
    case 'Task Assigned':
      return l10n.notificationTypeTaskAssigned;
    default:
      return notification.title;
  }
}

({String name, int day, int month, int year, String time})?
    _parseEnglishBookedWithDateTime(String message) {
  if (!message.startsWith(_kEnglishPatientBookedPrefix) ||
      !message.contains(_kEnglishBookedForMarker)) {
    return null;
  }

  final forIdx = message.indexOf(_kEnglishBookedForMarker);
  if (forIdx <= _kEnglishPatientBookedPrefix.length) return null;

  final name =
      message.substring(_kEnglishPatientBookedPrefix.length, forIdx).trim();
  final afterFor =
      message.substring(forIdx + _kEnglishBookedForMarker.length);
  final atIdx = afterFor.lastIndexOf(_kEnglishBookedAtMarker);
  if (atIdx < 0) return null;

  final datePart = afterFor.substring(0, atIdx).trim();
  var timePart = afterFor.substring(atIdx + _kEnglishBookedAtMarker.length).trim();
  if (timePart.endsWith('.')) {
    timePart = timePart.substring(0, timePart.length - 1);
  }

  final dateMatch = RegExp(r'^(\d+)\s+(\w+)\s+(\d+)$').firstMatch(datePart);
  if (dateMatch == null) return null;

  final day = int.tryParse(dateMatch.group(1)!);
  final month = _kEnglishMonthNumbers[dateMatch.group(2)!.toLowerCase()];
  final year = int.tryParse(dateMatch.group(3)!);
  if (day == null || month == null || year == null) return null;

  return (name: name, day: day, month: month, year: year, time: timePart);
}

String _localizedPatientBooked(
  DoctorNotificationModel notification,
  AppLocalizations l10n, {
  String? timeZone,
}) {
  String name = notification.patientName?.trim() ?? '';
  if (name.isEmpty &&
      notification.message.startsWith(_kEnglishPatientBookedPrefix) &&
      (notification.message.contains(_kEnglishPatientBookedSuffix) ||
          notification.message.contains(' booked an appointment for '))) {
    final bookedIdx = notification.message.indexOf(' booked an appointment');
    if (bookedIdx > _kEnglishPatientBookedPrefix.length) {
      name = notification.message
          .substring(_kEnglishPatientBookedPrefix.length, bookedIdx)
          .trim();
    }
  }
  if (name.isEmpty) name = 'Patient';

  final startAt = notification.appointmentStartAt;
  if (startAt != null) {
    final local = utcToTimezone(startAt, timeZone);
    final dateStr = '${local.day} ${l10n.monthShort(local.month)} ${local.year}';
    final timeStr = formatTimeForDisplay(local);
    return l10n.notificationMessagePatientBookedAppointment(name, dateStr, timeStr);
  }

  final parsed = _parseEnglishBookedWithDateTime(notification.message);
  if (parsed != null) {
    final dateStr =
        '${parsed.day} ${l10n.monthShort(parsed.month)} ${parsed.year}';
    return l10n.notificationMessagePatientBookedAppointment(
      parsed.name.isEmpty ? name : parsed.name,
      dateStr,
      parsed.time,
    );
  }

  return l10n.notificationMessagePatientBookedAppointmentNoTime(name);
}

/// Human-readable label; no raw event codes.
String humanLabelForNotificationType(String type, AppLocalizations l10n) {
  switch (type) {
    case 'APPOINTMENT_BOOKED_BY_PATIENT':
    case 'APPOINTMENT_REMINDER':
      return l10n.notificationTypeAppointmentBooked;
    case 'APPOINTMENT_CANCELLED_BY_PATIENT':
    case 'APPOINTMENT_CANCELLED':
      return l10n.notificationTypeAppointmentCancelled;
    case 'TASK_COMPLETED':
      return l10n.notificationTypeTaskCompleted;
    case 'TASK_ASSIGNED':
      return l10n.notificationTypeTaskAssigned;
    case 'DOCUMENT_ACCESS_REQUEST':
      return l10n.notificationTypeDocumentAccessRequest;
    case 'DOCUMENT_ACCESS_APPROVED':
      return l10n.notificationTypeDocumentAccessApproved;
    case 'DOCUMENT_ACCESS_REJECTED':
      return l10n.notificationTypeDocumentAccessRejected;
    case 'AI_SCRIBE_READY':
      return l10n.notificationTypeAiScribeReady;
    default:
      return l10n.notificationGeneric;
  }
}

/// Semantic color and icon for notification type.
({Color color, IconData icon}) styleForNotificationType(String type) {
  switch (type) {
    case 'APPOINTMENT_BOOKED_BY_PATIENT':
    case 'APPOINTMENT_REMINDER':
      return (color: const Color(0xFF1976D2), icon: Icons.calendar_today_rounded); // Blue
    case 'APPOINTMENT_CANCELLED_BY_PATIENT':
    case 'APPOINTMENT_CANCELLED':
      return (color: const Color(0xFFC62828), icon: Icons.event_busy_rounded); // Red
    case 'TASK_COMPLETED':
      return (color: const Color(0xFF2E7D32), icon: Icons.check_circle_rounded); // Green
    case 'TASK_ASSIGNED':
      return (color: const Color(0xFFF9A825), icon: Icons.assignment_rounded); // Amber
    case 'DOCUMENT_ACCESS_REQUEST':
    case 'DOCUMENT_ACCESS_APPROVED':
    case 'DOCUMENT_ACCESS_REJECTED':
      return (color: const Color(0xFF6A1B9A), icon: Icons.description_rounded); // Purple
    case 'AI_SCRIBE_READY':
      return (color: AppColors.primaryTeal, icon: Icons.auto_awesome_rounded); // Teal
    default:
      return (color: const Color(0xFF616161), icon: Icons.notifications_rounded); // Grey
  }
}

/// Filter categories for notifications.
enum NotificationFilter {
  all,
  appointments,
  tasks,
  messages,
}

bool notificationMatchesFilter(String type, NotificationFilter filter) {
  switch (filter) {
    case NotificationFilter.all:
      return true;
    case NotificationFilter.appointments:
      return type.startsWith('APPOINTMENT_');
    case NotificationFilter.tasks:
      return type.startsWith('TASK_');
    case NotificationFilter.messages:
      return type.contains('MESSAGE') || type == 'DOCUMENT_ACCESS_REQUEST' ||
          type == 'DOCUMENT_ACCESS_APPROVED' || type == 'DOCUMENT_ACCESS_REJECTED';
  }
}
