import 'package:shifa_doc_app_v1/features/shell/domain/doctor_shell_tab.dart';

/// Backend-persisted keys for the doctor main shell starting tab.
abstract final class DoctorStartTab {
  static const chat = 'chat';
  static const home = 'home';
  static const calendar = 'calendar';
  static const patients = 'patients';
  static const clinic = 'clinic';
  static const tasks = 'tasks';
  static const reports = 'reports';
  static const notifications = 'notifications';
  static const profile = 'profile';

  static const defaultKey = home;

  static const doctorKeys = [
    chat,
    home,
    calendar,
    patients,
    clinic,
    tasks,
    reports,
    notifications,
    profile,
  ];

  static const clinicStaffKeys = [
    chat,
    clinic,
    notifications,
    profile,
  ];

  static String normalize(String? raw, {required bool isClinicStaff}) {
    final allowed = isClinicStaff ? clinicStaffKeys : doctorKeys;
    final trimmed = raw?.trim().toLowerCase() ?? '';
    if (allowed.contains(trimmed)) return trimmed;
    return defaultKey;
  }

  static int? shellIndexForKey(
    String key, {
    required bool isClinicStaff,
  }) {
    if (isClinicStaff) {
      return switch (key) {
        chat => 0,
        clinic => 1,
        notifications => 2,
        profile => 3,
        _ => null,
      };
    }
    return switch (key) {
      chat => DoctorShellTab.chat,
      home => DoctorShellTab.home,
      calendar => DoctorShellTab.calendar,
      patients => DoctorShellTab.patients,
      clinic => DoctorShellTab.clinic,
      tasks => DoctorShellTab.tasks,
      reports => DoctorShellTab.reports,
      notifications => DoctorShellTab.notifications,
      profile => DoctorShellTab.profile,
      _ => null,
    };
  }

  /// Resolves a stored preference to a shell tab index, applying role/feature gates.
  static int resolveShellIndex({
    required String? rawKey,
    required bool isClinicStaff,
    required bool canUseTasks,
  }) {
    final key = normalize(rawKey, isClinicStaff: isClinicStaff);
    final mapped = shellIndexForKey(key, isClinicStaff: isClinicStaff);
    if (mapped == null) {
      return isClinicStaff ? 1 : DoctorShellTab.home;
    }
    if (!isClinicStaff &&
        mapped == DoctorShellTab.tasks &&
        !canUseTasks) {
      return DoctorShellTab.home;
    }
    return mapped;
  }

  /// Factory default tab index before user preference is applied.
  static int factoryDefaultIndex({required bool isClinicStaff}) =>
      isClinicStaff ? 1 : DoctorShellTab.home;
}
