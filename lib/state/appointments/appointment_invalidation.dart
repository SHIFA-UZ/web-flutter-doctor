// Centralized invalidation for appointment-related providers.
// Call after: appointment create, update, delete, or calendar modification.

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/features/appointments/application/today_appointments_provider.dart';
import 'package:shifa_doc_app_v1/features/home/application/home_analytics_providers.dart';
import 'package:shifa_doc_app_v1/state/calendar/calendar_controller.dart';

/// Invalidates today appointments and doctor analytics.
/// Use after creating, updating, or deleting an appointment.
///
/// NOTE: Does NOT invalidate calendarProvider (StateNotifier) because that would
/// wipe all cached calendar data. Calendar screen manages its own refresh via loadDay().
/// Accepts [Ref] or [WidgetRef] so it can be called from controllers or widgets.
void invalidateAppointmentRelatedProviders(dynamic ref) {
  ref.invalidate(todayAppointmentsProvider);
  // DON'T invalidate calendarProvider - it's a StateNotifier with cached data
  // Calendar screen will refresh via its own lifecycle hooks and loadDay() calls
  ref.invalidate(doctorAnalyticsOverviewProvider);
}

/// Refresh a specific day in the calendar without wiping all cached data.
/// Use this after booking/canceling appointments instead of invalidating the entire calendar.
Future<void> refreshCalendarDay(
  dynamic ref,
  DateTime day,
  String doctorTimeZone,
) async {
  try {
    await ref
        .read(calendarProvider.notifier)
        .loadDay(day: day, doctorTimeZone: doctorTimeZone, forceRefresh: true);
  } catch (e) {
    // Silent fail - calendar will retry on next user interaction
    debugPrint('Failed to refresh calendar day: $e');
  }
}
