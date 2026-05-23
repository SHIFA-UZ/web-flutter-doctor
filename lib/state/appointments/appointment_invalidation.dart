// Centralized invalidation for appointment-related providers.
// Call after: appointment create, update, delete, complete, or calendar modification.

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/core/utils/timezone_utils.dart';
import 'package:shifa_doc_app_v1/features/appointments/application/today_appointments_provider.dart';
import 'package:shifa_doc_app_v1/features/home/application/home_analytics_providers.dart';
import 'package:shifa_doc_app_v1/state/calendar/calendar_controller.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_finance_providers.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_providers.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_treatment_plan_providers.dart';
import 'package:shifa_doc_app_v1/state/profile/profile_providers.dart';

/// Refreshes **today** in [calendarProvider], then invalidates today list + analytics.
///
/// Home "Bugun" uses [todayAppointmentsProvider], which reads cached calendar
/// entries without refetching when the day was already loaded. After completing
/// an appointment, we must [refreshCalendarDay] for today so status updates appear.
///
/// Accepts [Ref] or [WidgetRef]. Safe to call without `await` (e.g. logout);
/// use `await` when you need the home list updated before navigating away.
/// [clinicWorkspaceId] when known (e.g. visit completion payload) avoids missing refresh
/// when [selectedClinicIdProvider] has not been synced yet.
Future<void> invalidateAppointmentRelatedProviders(
  dynamic ref, {
  int? clinicWorkspaceId,
}) async {
  try {
    String? doctorTimeZone;
    try {
      final me = await ref.read(meProfileProvider.future);
      doctorTimeZone = me.timeZone;
    } catch (_) {
      // Clinic staff has no /api/doctors/me — fall back only for doctors.
      try {
        final profile = await ref.read(profileAllProvider.future);
        doctorTimeZone = profile.profile['timeZone'] as String?;
      } catch (_) {}
    }
    if (doctorTimeZone != null && doctorTimeZone.isNotEmpty) {
      final todayInDoctorZone = getTodayInTimezone(doctorTimeZone);
      final todayKey = DateTime(
        todayInDoctorZone.year,
        todayInDoctorZone.month,
        todayInDoctorZone.day,
      );
      await refreshCalendarDay(ref, todayKey, doctorTimeZone);
    }
  } catch (e) {
    debugPrint('invalidateAppointmentRelatedProviders: $e');
  }
  // Best-effort invalidations: never let a single failure abort the caller,
  // otherwise success paths like "End appointment" surface a red error
  // snackbar after the backend has already accepted the change.
  try {
    ref.invalidate(todayAppointmentsProvider);
  } catch (e) {
    debugPrint('invalidate(todayAppointmentsProvider) failed: $e');
  }
  try {
    ref.invalidate(doctorAnalyticsOverviewProvider);
  } catch (e) {
    debugPrint('invalidate(doctorAnalyticsOverviewProvider) failed: $e');
  }
  // Treatment plan auto-completion runs server-side when an appointment is
  // completed (see TreatmentPlanStatusService), so the list of plans + the
  // finance dashboards may have moved. Invalidate them here so the next
  // navigation to the Clinic workspace shows the up-to-date status without
  // requiring a manual refresh.
  try {
    ref.invalidate(treatmentPlansForClinicProvider);
    ref.invalidate(treatmentPlansForPatientProvider);
  } catch (e) {
    debugPrint('invalidate(treatmentPlans*) failed: $e');
  }
  try {
    final clinicId = clinicWorkspaceId ?? ref.read(selectedClinicIdProvider);
    if (clinicId != null) {
      invalidateClinicFinanceTabDataForClinic(ref, clinicId);
    }
  } catch (e) {
    debugPrint('invalidateClinicFinanceTabDataForClinic failed: $e');
  }
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
