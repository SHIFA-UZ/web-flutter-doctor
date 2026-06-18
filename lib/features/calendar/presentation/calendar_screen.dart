// lib/features/calendar/presentation/calendar_screen.dart
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:table_calendar/table_calendar.dart';

import 'package:shifa_doc_app_v1/app/router.dart';
import 'package:shifa_doc_app_v1/features/shell/presentation/shell_scope.dart';
import 'package:shifa_doc_app_v1/features/calendar/domain/calendar_day_occupancy.dart';
import 'package:shifa_doc_app_v1/features/calendar/domain/calendar_models.dart';
import 'package:shifa_doc_app_v1/features/calendar/domain/consecutive_slot_range.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:shifa_doc_app_v1/features/calendar/domain/calendar_staff_colors.dart';
import 'package:shifa_doc_app_v1/features/calendar/presentation/calendar_week_grid_view.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_models.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_providers.dart';
import 'package:shifa_doc_app_v1/features/clinic/presentation/finance_shared.dart';
import 'package:shifa_doc_app_v1/features/appointments/domain/appointment_models.dart';
import 'package:shifa_doc_app_v1/features/appointments/application/consultation_notes_provider.dart';
import 'package:shifa_doc_app_v1/core/api/consultation_notes_api.dart';
import 'package:shifa_doc_app_v1/features/patients/domain/patient_models.dart';
import 'package:shifa_doc_app_v1/features/patients/presentation/create_patient_sheet.dart';
import 'package:shifa_doc_app_v1/state/calendar/calendar_controller.dart';
import 'package:shifa_doc_app_v1/state/patients/patient_documents_provider.dart';
import 'package:shifa_doc_app_v1/state/patients/patients_provider.dart';
import 'package:shifa_doc_app_v1/state/shell/shell_controller.dart';
import 'package:shifa_doc_app_v1/features/shell/domain/doctor_shell_tab.dart';
import 'package:shifa_doc_app_v1/features/clinic/presentation/clinic_schedule_return_info.dart';
import 'package:shifa_doc_app_v1/state/profile/profile_providers.dart';
import 'package:shifa_doc_app_v1/core/utils/patient_warning_utils.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/localization/uzbek_latin_to_cyrillic.dart';
import 'package:shifa_doc_app_v1/core/providers/language_provider.dart';
import 'package:shifa_doc_app_v1/core/utils/timezone_utils.dart';
import 'package:shifa_doc_app_v1/core/theme/app_colors.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';
import 'package:shifa_doc_app_v1/core/layout/platform_layout.dart';
import 'package:shifa_doc_app_v1/core/layout/responsive.dart';
import 'package:shifa_doc_app_v1/state/appointments/appointment_invalidation.dart';
import 'package:intl/intl.dart';
import 'package:intl/intl.dart';

/// Latin Uzbek fallback strings rendered for Cyrillic locale (`uz` + Cyrl).
String _latinUzbekForDisplay(BuildContext context, String latinUzbek) {
  final loc = Localizations.localeOf(context);
  if (!loc.isUzbekCyrillic) return latinUzbek;
  return transliterateUzbekLatinToCyrillicUi(latinUzbek);
}

/// `intl` / [TableCalendar] only loads `en`, `uz`, `ru` in [main] â€” avoid invalid tags.
String _tableCalendarIntlLocale(BuildContext context) {
  final lc = Localizations.localeOf(context).languageCode.toLowerCase();
  if (lc == 'uz') return 'uz';
  if (lc == 'ru') return 'ru';
  return 'en';
}

/// Block-time dialog modes on [CalendarScreen].
enum _ScheduleBlockMode { entireDay, timeRange, dateRange }

/// Desktop calendar header split: title on the left, toolbar on the right.
enum _CalendarHeaderPart { title, toolbar, combined }

const double _kCalendarDesktopHeaderMinHeight = 52;

class CalendarScreen extends ConsumerStatefulWidget {
  const CalendarScreen({Key? key}) : super(key: key);

  @override
  ConsumerState<CalendarScreen> createState() => _CalendarScreenState();
}

class _CalendarScreenState extends ConsumerState<CalendarScreen>
    with WidgetsBindingObserver {
  DateTime _focusedDay = DateTime.now();
  DateTime? _selectedDay;
  /// First column of the multi-day grid; stays fixed when tapping slots in-grid.
  DateTime? _viewAnchorDay;
  CalendarEntry? _selectedEntry;
  bool _loadingDay = false;
  DateTime? _lastRefreshTime;
  bool _timeZoneHintDismissed = false;

  // Filter state
  bool _showAppointments = true;
  bool _showFreeSlots = true;
  bool _showBlockedTime = true;

  /// Number of consecutive days shown in the grid (1–7).
  int _dayViewCount = 1;

  /// Clinic staff whose grids are visible (always includes self once initialized).
  final Set<int> _visibleStaffDoctorIds = {};

  /// Cached calendar rows for colleagues (own schedule stays in [calendarProvider]).
  final Map<int, Map<DateTime, List<CalendarEntry>>> _staffEntriesByDoctor = {};

  /// Doctor profile id for the currently selected slot row.
  int? _selectedEntryDoctorProfileId;

  /// Avoid re-applying persisted staff selection for the same clinic.
  int? _staffPrefsRestoredForClinicId;

  /// When true, profile listener skips loading "today" so go-to-appointment can load the target day only.
  bool _skipInitialProfileLoad = false;

  /// Prevents scheduling duplicate go-to-appointment callbacks on rebuild.
  int? _goToAppointmentInFlight;

  /// Prevents duplicate quick-book callbacks on rebuild.
  bool _quickBookInFlight = false;

  /// Pre-selects video consultation when opening a free slot from quick actions.
  String? _initialBookingPlaceForSelection;

  DateTime _dayKey(DateTime d) => DateTime(d.year, d.month, d.day);
  String _two(int n) => n.toString().padLeft(2, '0');

  List<DateTime> get _visibleDays {
    final anchor = _viewAnchorDay ?? _selectedDay;
    if (anchor == null) return const [];
    final start = _dayKey(anchor);
    return List.generate(
      _dayViewCount,
      (i) => DateTime(start.year, start.month, start.day + i),
    );
  }

  String? _calendarDateRangeLabel(AppLocalizations l10n) {
    if (_selectedDay == null) return null;
    final days = _visibleDays;
    if (days.isEmpty) return null;
    if (days.length == 1) {
      final d = days.first;
      return '${d.day} ${l10n.monthName(d.month)} ${d.year}';
    }
    final first = days.first;
    final last = days.last;
    if (first.month == last.month && first.year == last.year) {
      return '${first.day}–${last.day} ${l10n.monthName(first.month)} ${first.year}';
    }
    if (first.year == last.year) {
      return '${first.day} ${l10n.monthName(first.month)} – '
          '${last.day} ${l10n.monthName(last.month)} ${last.year}';
    }
    return '${first.day} ${l10n.monthName(first.month)} ${first.year} – '
        '${last.day} ${l10n.monthName(last.month)} ${last.year}';
  }

  List<CalendarEntry> _entriesFor(DateTime? day) {
    if (day == null) return [];
    return _entriesForDoctor(day, _ownDoctorProfileId());
  }

  List<CalendarEntry> _entriesForDoctor(DateTime? day, int? doctorProfileId) {
    if (day == null) return [];
    final key = _dayKey(day);
    final ownId = _ownDoctorProfileId();
    final List<CalendarEntry> allEntries;
    if (doctorProfileId == null || doctorProfileId == ownId) {
      final entries = ref.watch(calendarProvider);
      allEntries = entries[key] ?? [];
    } else {
      allEntries = _staffEntriesByDoctor[doctorProfileId]?[key] ?? [];
    }

    return allEntries.where((e) {
      if (e.type == EntryType.appointment) return _showAppointments;
      if (e.type == EntryType.freeSlot) return _showFreeSlots;
      if (e.type == EntryType.blocked) return _showBlockedTime;
      return true;
    }).toList();
  }

  int? _ownDoctorProfileId() {
    final profile = ref.read(profileAllProvider).valueOrNull?.profile;
    final id = profile?['id'] ?? profile?['doctorId'] ?? profile?['doctorProfileId'];
    if (id is int) return id;
    if (id is num) return id.toInt();
    return int.tryParse(id?.toString() ?? '');
  }

  List<ClinicMember> _schedulableStaff(List<ClinicMember> members) {
    const roles = {'DOCTOR', 'OWNER'};
    return members
        .where((m) => roles.contains(m.membershipRole))
        .toList()
      ..sort(
        (a, b) =>
            a.displayName.toLowerCase().compareTo(b.displayName.toLowerCase()),
      );
  }

  void _ensureOwnStaffSelected() {
    final ownId = _ownDoctorProfileId();
    if (ownId != null && _visibleStaffDoctorIds.isEmpty) {
      _visibleStaffDoctorIds.add(ownId);
    }
  }

  List<int> get _orderedVisibleStaffDoctorIds {
    _ensureOwnStaffSelected();
    final ownId = _ownDoctorProfileId();
    final ids = _visibleStaffDoctorIds.toList();
    ids.sort((a, b) {
      if (ownId != null && a == ownId) return -1;
      if (ownId != null && b == ownId) return 1;
      return a.compareTo(b);
    });
    return ids;
  }

  String _staffDisplayName(int doctorProfileId, List<ClinicMember> members) {
    if (doctorProfileId == _ownDoctorProfileId()) {
      final l10n = AppLocalizations.of(context)!;
      return l10n.translate('mySchedule') ?? 'My schedule';
    }
    return doctorNameFromClinicMembers(doctorProfileId, members);
  }

  String _scheduleTimeZoneForStaff(int? doctorProfileId) {
    if (doctorProfileId == null || doctorProfileId == _ownDoctorProfileId()) {
      return _effectiveProfileTimeZone();
    }
    final clinic = ref.read(selectedClinicProvider);
    final tz = clinic?.timeZone.trim();
    return (tz != null && tz.isNotEmpty) ? tz : _effectiveProfileTimeZone();
  }

  List<int> _staffRosterIds(List<ClinicMember> staff) =>
      staff.map((m) => m.doctorProfileId).toList();

  Color _staffAccentColor(
    int doctorProfileId,
    List<ClinicMember> staff,
    Color brand,
  ) {
    if (staff.length < 2) return brand;
    return CalendarStaffColors.forDoctorInRoster(
      doctorProfileId,
      _staffRosterIds(staff),
      fallback: brand,
    );
  }

  Future<String> _staffSelectionPrefsKey(int clinicId) async {
    final profile = ref.read(profileAllProvider).valueOrNull?.profile;
    final id = profile?['id'] ?? profile?['doctorId'] ?? profile?['doctorProfileId'];
    return 'calendar_visible_staff_v1:$id:$clinicId';
  }

  Future<void> _savePersistedStaffSelection() async {
    final clinic = ref.read(selectedClinicProvider);
    if (clinic == null || _visibleStaffDoctorIds.isEmpty) return;
    try {
      final prefs = await SharedPreferences.getInstance();
      final key = await _staffSelectionPrefsKey(clinic.clinicId);
      await prefs.setStringList(
        key,
        _visibleStaffDoctorIds.map((id) => id.toString()).toList(),
      );
    } catch (e) {
      debugPrint('CalendarScreen: failed to save staff selection: $e');
    }
  }

  Future<void> _restoreStaffSelectionIfNeeded() async {
    final clinic = ref.read(selectedClinicProvider);
    if (clinic == null) {
      _ensureOwnStaffSelected();
      return;
    }
    if (_staffPrefsRestoredForClinicId == clinic.clinicId) return;

    List<ClinicMember> members;
    try {
      members = await ref.read(clinicMembersProvider(clinic.clinicId).future);
    } catch (e) {
      debugPrint('CalendarScreen: staff roster unavailable: $e');
      _ensureOwnStaffSelected();
      _staffPrefsRestoredForClinicId = clinic.clinicId;
      return;
    }

    final staff = _schedulableStaff(members);
    _staffPrefsRestoredForClinicId = clinic.clinicId;

    if (staff.length < 2) {
      _ensureOwnStaffSelected();
      return;
    }

    try {
      final prefs = await SharedPreferences.getInstance();
      final key = await _staffSelectionPrefsKey(clinic.clinicId);
      final raw = prefs.getStringList(key);
      if (raw == null || raw.isEmpty) {
        _ensureOwnStaffSelected();
        return;
      }

      final validIds = staff.map((m) => m.doctorProfileId).toSet();
      final restored = raw
          .map(int.tryParse)
          .whereType<int>()
          .where(validIds.contains)
          .toSet();
      if (restored.isEmpty) {
        _ensureOwnStaffSelected();
        return;
      }

      if (!mounted) return;
      setState(() {
        _visibleStaffDoctorIds
          ..clear()
          ..addAll(restored);
      });
    } catch (e) {
      debugPrint('CalendarScreen: failed to restore staff selection: $e');
      _ensureOwnStaffSelected();
    }
  }

  // Get RAW entries without filters (for checking if data actually has free slots)
  List<CalendarEntry> _rawEntriesFor(DateTime? day) {
    if (day == null) return [];
    final entries = ref.watch(calendarProvider);
    return entries[_dayKey(day)] ?? [];
  }

  // True if profile is still loading (haven't loaded calendar data yet)
  bool get _isWaitingForProfile {
    final profileAsync = ref.watch(profileAllProvider);
    return profileAsync.isLoading;
  }

  // CRITICAL: Check for free slots in RAW data (before filters)
  // This prevents showing "update schedule" warning when user filters out free slots
  bool get _hasFreeSlotsOnSelected {
    final items = _rawEntriesFor(_selectedDay);
    return items.any((e) => e.type == EntryType.freeSlot);
  }

  bool get _hasAppointmentsOnSelected {
    final items = _rawEntriesFor(_selectedDay);
    return items.any((e) => e.type == EntryType.appointment);
  }

  // Show update schedule warning only when the day looks like it has no schedule coverage
  // (no bookable openings *and* no bookings). Fully booked days still have appointments â€”
  // do not imply "extend your calendar horizon" in that case.
  bool get _shouldShowUpdateScheduleCard {
    if (_loadingDay || _isWaitingForProfile)
      return false; // Don't show while loading
    if (_selectedDay == null) return false;
    if (_hasAppointmentsOnSelected) return false;
    return !_hasFreeSlotsOnSelected;
  }

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);

    // If we're opening to a specific appointment (notification tap), use that day from frame one.
    final goToDay = ref.read(calendarGoToAppointmentDayProvider);
    final goToId = ref.read(calendarGoToAppointmentIdProvider);
    if (goToDay != null && goToId != null && goToId > 0) {
      _skipInitialProfileLoad = true;
      _selectedDay = DateTime(goToDay.year, goToDay.month, goToDay.day);
      _viewAnchorDay = _selectedDay;
      _focusedDay = _selectedDay!;
    } else {
      final doctorTz =
          ref.read(profileAllProvider).valueOrNull?.profile['timeZone']
              as String?;
      final today = getTodayInTimezone(doctorTz);
      _selectedDay = DateTime(today.year, today.month, today.day);
      _viewAnchorDay = _selectedDay;
      _focusedDay = _selectedDay!;
    }

    // Main shell Calendar is always the logged-in doctor. Clinic workspace has
    // its own calendar tab for other doctors.
    ref.read(calendarProvider.notifier).setResourceDoctorId(null);

    // Reactive: load calendar when profile is available (use UTC if timezone missing so calendar still loads)
    ref.listenManual(profileAllProvider, (previous, next) {
      if (_skipInitialProfileLoad) return;
      if (next.hasValue) {
        final tz = next.value?.profile['timeZone'] as String?;
        final effectiveTz = (tz != null && tz.trim().isNotEmpty) ? tz : 'UTC';
        if (_selectedDay != null &&
            mounted &&
            ref.read(shellProvider) == DoctorShellTab.calendar) {
          debugPrint(
            'CalendarScreen: Profile loaded (timezone: ${tz ?? "UTC fallback"}), loading calendar for $_selectedDay',
          );
          Future.microtask(() async {
            if (!mounted) return;
            await _restoreStaffSelectionIfNeeded();
            if (!mounted ||
                _selectedDay == null ||
                ref.read(shellProvider) != DoctorShellTab.calendar) {
              return;
            }
            _reloadCalendar(effectiveTz);
            _loadMonth(_focusedDay, effectiveTz);
          });
        }
      } else if (next.isLoading) {
        debugPrint('CalendarScreen: Waiting for profile to load...');
      } else if (next.hasError) {
        debugPrint('CalendarScreen: Profile load failed: ${next.error}');
      }
    }, fireImmediately: true);

    ref.listenManual(shellProvider, (previous, next) {
      if (next == DoctorShellTab.calendar &&
          previous != DoctorShellTab.calendar) {
        ref.read(calendarProvider.notifier).setResourceDoctorId(null);
        if (_selectedDay != null && mounted) {
          final tz =
              ref.read(profileAllProvider).valueOrNull?.profile['timeZone']
                  as String?;
          final effectiveTz =
              (tz != null && tz.trim().isNotEmpty) ? tz : 'UTC';
          Future.microtask(() async {
            if (!mounted) return;
            await _restoreStaffSelectionIfNeeded();
            if (!mounted) return;
            _reloadCalendar(effectiveTz);
            _loadMonth(_focusedDay, effectiveTz);
          });
        }
      }
    });
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    super.didChangeAppLifecycleState(state);
    if (state == AppLifecycleState.resumed &&
        _selectedDay != null &&
        ref.read(shellProvider) == DoctorShellTab.calendar) {
      // Refresh calendar when app resumes (throttled to avoid excessive API calls)
      final tz =
          ref.read(profileAllProvider).valueOrNull?.profile['timeZone']
              as String?;
      final effectiveTz = (tz != null && tz.trim().isNotEmpty) ? tz : 'UTC';
      final now = getNowInTimezone(effectiveTz);
      if (_lastRefreshTime == null ||
          now.difference(_lastRefreshTime!).inSeconds > 5) {
        _reloadCalendar(effectiveTz);
        _lastRefreshTime = now;
      }
    }
  }

  Future<void> _fetchDay(DateTime day, String doctorTimeZone) async {
    await ref
        .read(calendarProvider.notifier)
        .loadDay(day: day, doctorTimeZone: doctorTimeZone);
    _lastRefreshTime = getNowInTimezone(doctorTimeZone);
  }

  Future<void> _loadDay(DateTime day, String doctorTimeZone) async {
    setState(() => _loadingDay = true);
    try {
      debugPrint(
        'CalendarScreen: Loading day ${_ymd(day)} with timezone $doctorTimeZone',
      );
      await _fetchDay(day, doctorTimeZone);
      debugPrint('CalendarScreen: Successfully loaded day ${_ymd(day)}');
    } catch (e) {
      debugPrint('CalendarScreen: Failed to load day ${_ymd(day)}: $e');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              '${AppLocalizations.of(context)!.translate('failedToLoad') ?? 'Failed to load'}: $e',
            ),
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _loadingDay = false);
    }
  }

  Future<void> _loadVisibleDays(String doctorTimeZone) async {
    final days = _visibleDays;
    if (days.isEmpty) return;
    _ensureOwnStaffSelected();
    setState(() => _loadingDay = true);
    try {
      final ownId = _ownDoctorProfileId();
      final futures = <Future<void>>[];
      for (final doctorId in _visibleStaffDoctorIds) {
        if (doctorId == ownId) {
          futures.addAll(
            days.map(
              (day) => ref.read(calendarProvider.notifier).loadDay(
                    day: day,
                    doctorTimeZone: doctorTimeZone,
                  ),
            ),
          );
        } else {
          futures.addAll(
            days.map((day) => _loadStaffDoctorDay(doctorId, day, doctorTimeZone)),
          );
        }
      }
      await Future.wait(futures);
      _lastRefreshTime = getNowInTimezone(doctorTimeZone);
    } catch (e) {
      debugPrint('CalendarScreen: Failed to load visible days: $e');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              '${AppLocalizations.of(context)!.translate('failedToLoad') ?? 'Failed to load'}: $e',
            ),
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _loadingDay = false);
    }
  }

  Future<void> _loadStaffDoctorDay(
    int doctorProfileId,
    DateTime day,
    String doctorTimeZone,
  ) async {
    try {
      final entries = await ref
          .read(calendarProvider.notifier)
          .previewDayForDoctorProfile(
            day: day,
            doctorTimeZone: doctorTimeZone,
            doctorProfileId: doctorProfileId,
          );
      if (!mounted) return;
      setState(() {
        _staffEntriesByDoctor.putIfAbsent(doctorProfileId, () => {});
        _staffEntriesByDoctor[doctorProfileId]![_dayKey(day)] = entries;
      });
    } catch (e) {
      debugPrint(
        'CalendarScreen: Failed to load staff day '
        '$doctorProfileId ${_ymd(day)}: $e',
      );
    }
  }

  Future<void> _reloadDoctorCalendar(int? doctorProfileId, String tz) async {
    final ownId = _ownDoctorProfileId();
    if (doctorProfileId == null || doctorProfileId == ownId) {
      await _reloadCalendar(tz);
      return;
    }
    setState(() => _loadingDay = true);
    try {
      await Future.wait(
        _visibleDays.map((day) => _loadStaffDoctorDay(doctorProfileId, day, tz)),
      );
    } finally {
      if (mounted) setState(() => _loadingDay = false);
    }
  }

  Future<void> _reloadCalendar(String doctorTimeZone) async {
    if (!mounted || _selectedDay == null) return;
    _ensureOwnStaffSelected();

    if (PlatformLayout.useSinglePane(context)) {
      setState(() => _loadingDay = true);
      try {
        final ownId = _ownDoctorProfileId();
        final futures = <Future<void>>[];
        for (final doctorId in _visibleStaffDoctorIds) {
          if (doctorId == ownId) {
            futures.add(_fetchDay(_selectedDay!, doctorTimeZone));
          } else {
            futures.add(
              _loadStaffDoctorDay(doctorId, _selectedDay!, doctorTimeZone),
            );
          }
        }
        await Future.wait(futures);
      } finally {
        if (mounted) setState(() => _loadingDay = false);
      }
      return;
    }

    await _loadVisibleDays(doctorTimeZone);
  }

  String _effectiveProfileTimeZone() {
    final tz =
        ref.read(profileAllProvider).valueOrNull?.profile['timeZone'] as String?;
    return (tz != null && tz.trim().isNotEmpty) ? tz : 'UTC';
  }

  Future<void> _loadMonth(DateTime month, String doctorTimeZone) async {
    try {
      await ref.read(calendarProvider.notifier).loadMonth(
            month: month,
            doctorTimeZone: doctorTimeZone,
          );
    } catch (e) {
      debugPrint(
        'CalendarScreen: Failed to load month ${month.year}-${month.month}: $e',
      );
    }
  }

  String _ymd(DateTime d) =>
      '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';

  int _entryStartMinutes(CalendarEntry entry) =>
      entry.start.hour * 60 + entry.start.minute;

  void _selectCalendarEntry(
    CalendarEntry entry, {
    String? initialBookingPlace,
    int? doctorProfileId,
  }) {
    setState(() {
      _selectedEntry = entry;
      _selectedEntryDoctorProfileId = doctorProfileId ?? _ownDoctorProfileId();
      _initialBookingPlaceForSelection = initialBookingPlace;
    });
  }

  void _clearSelectedEntry() {
    setState(() {
      _selectedEntry = null;
      _selectedEntryDoctorProfileId = null;
      _initialBookingPlaceForSelection = null;
    });
  }

  void _toggleStaffDoctor(int doctorProfileId, String doctorTimeZone) {
    setState(() {
      if (_visibleStaffDoctorIds.contains(doctorProfileId)) {
        if (_visibleStaffDoctorIds.length <= 1) return;
        _visibleStaffDoctorIds.remove(doctorProfileId);
        _staffEntriesByDoctor.remove(doctorProfileId);
        if (_selectedEntryDoctorProfileId == doctorProfileId) {
          _selectedEntry = null;
          _selectedEntryDoctorProfileId = null;
          _initialBookingPlaceForSelection = null;
        }
      } else {
        _visibleStaffDoctorIds.add(doctorProfileId);
      }
    });
    _reloadCalendar(doctorTimeZone);
    _savePersistedStaffSelection();
  }

  Future<void> _handleQuickBookIntent(CalendarQuickBookIntent intent) async {
    final tz = _effectiveProfileTimeZone();
    final today = getTodayInTimezone(tz);
    final now = getNowInTimezone(tz);
    final l10n = AppLocalizations.of(context)!;
    final initialPlace =
        intent.preferVideoConsultation ? l10n.videoCall : null;

    if (!_showFreeSlots && mounted) {
      setState(() => _showFreeSlots = true);
    }

    CalendarEntry? picked;
    DateTime? pickedDay;

    for (var dayOffset = 0; dayOffset < 30; dayOffset++) {
      final day = DateTime(
        today.year,
        today.month,
        today.day,
      ).add(Duration(days: dayOffset));
      await _loadDay(day, tz);
      if (!mounted) return;

      final entries = ref.read(calendarProvider)[_dayKey(day)] ?? [];
      final freeSlots = entries
          .where((e) => e.type == EntryType.freeSlot)
          .toList()
        ..sort(
          (a, b) => _entryStartMinutes(a).compareTo(_entryStartMinutes(b)),
        );

      for (final slot in freeSlots) {
        if (dayOffset == 0) {
          final slotStart = timeOfDayToDateTimeInZone(slot.start, day, tz);
          if (slotStart.isBefore(now)) continue;
        }
        picked = slot;
        pickedDay = day;
        break;
      }

      if (picked != null) break;
    }

    if (!mounted) return;

    if (picked != null && pickedDay != null) {
      final day = pickedDay;
      setState(() {
        _selectedDay = DateTime(day.year, day.month, day.day);
        _viewAnchorDay = _selectedDay;
        _focusedDay = _selectedDay!;
        _selectedEntry = picked;
        _initialBookingPlaceForSelection = initialPlace;
      });
    } else {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(l10n.noSlotsAvailable),
        ),
      );
    }
  }

  bool _shouldShowTimeZoneMismatchHint(String? scheduleTimeZone) {
    final tz = scheduleTimeZone?.trim();
    if (tz == null || tz.isEmpty) return false;
    try {
      final scheduleNow = getNowInTimezone(tz);
      final deviceNow = DateTime.now();
      return scheduleNow.timeZoneOffset != deviceNow.timeZoneOffset;
    } catch (_) {
      return false;
    }
  }

  String _timeZoneMismatchHintMessage({
    required String scheduleTimeZone,
  }) {
    final localeCode = Localizations.localeOf(context).languageCode.toLowerCase();
    if (localeCode == 'uz') {
      return _latinUzbekForDisplay(
        context,
        'Joriy qurilma vaqt zonasi ($scheduleTimeZone bilan) mos emas. '
            'Siz slotlarni bir vaqt zonasida belgilagansiz, hozir esa boshqa vaqt zonasidasiz. '
            'Iltimos, uchrashuvlar bilan ishlaganda buni inobatga oling.',
      );
    }
    if (localeCode == 'ru') {
      return 'Ð¢ÐµÐºÑƒÑ‰Ð¸Ð¹ Ñ‡Ð°ÑÐ¾Ð²Ð¾Ð¹ Ð¿Ð¾ÑÑ ÑƒÑÑ‚Ñ€Ð¾Ð¹ÑÑ‚Ð²Ð° Ð½Ðµ ÑÐ¾Ð²Ð¿Ð°Ð´Ð°ÐµÑ‚ Ñ Ñ‡Ð°ÑÐ¾Ð²Ñ‹Ð¼ Ð¿Ð¾ÑÑÐ¾Ð¼ Ñ€Ð°ÑÐ¿Ð¸ÑÐ°Ð½Ð¸Ñ ($scheduleTimeZone). '
          'Ð¡Ð»Ð¾Ñ‚Ñ‹ Ð±Ñ‹Ð»Ð¸ Ð·Ð°Ð´Ð°Ð½Ñ‹ Ð² Ð¾Ð´Ð½Ð¾Ð¼ Ñ‡Ð°ÑÐ¾Ð²Ð¾Ð¼ Ð¿Ð¾ÑÑÐµ, Ð° ÑÐµÐ¹Ñ‡Ð°Ñ Ð²Ñ‹ Ð² Ð´Ñ€ÑƒÐ³Ð¾Ð¼. '
          'ÐŸÐ¾Ð¶Ð°Ð»ÑƒÐ¹ÑÑ‚Ð°, ÑƒÑ‡Ð¸Ñ‚Ñ‹Ð²Ð°Ð¹Ñ‚Ðµ ÑÑ‚Ð¾ Ð¿Ñ€Ð¸ Ñ€Ð°Ð±Ð¾Ñ‚Ðµ Ñ Ð¿Ñ€Ð¸Ñ‘Ð¼Ð°Ð¼Ð¸.';
    }
    return 'Your current device timezone does not match the calendar schedule timezone ($scheduleTimeZone). '
        'Your slots were defined in one timezone, but you are currently in another. '
        'Please keep this in mind while managing appointments.';
  }

  @override
  Widget build(BuildContext context) {
    // Go-to-appointment: use calendarGoToAppointmentDayProvider when set (notification tap).
    // CalendarScreen may already be alive on another day via KeepAlive — switch day first.
    final goToId = ref.watch(calendarGoToAppointmentIdProvider);
    if (goToId != null &&
        goToId > 0 &&
        mounted &&
        _goToAppointmentInFlight != goToId) {
      _goToAppointmentInFlight = goToId;
      WidgetsBinding.instance.addPostFrameCallback((_) async {
        if (!mounted) return;
        final appointmentId = goToId;
        final targetDay = ref.read(calendarGoToAppointmentDayProvider);
        ref.read(calendarGoToAppointmentIdProvider.notifier).state = null;
        ref.read(calendarGoToAppointmentDayProvider.notifier).state = null;

        final day = targetDay != null
            ? DateTime(targetDay.year, targetDay.month, targetDay.day)
            : _selectedDay;
        if (day == null) {
          _goToAppointmentInFlight = null;
          return;
        }

        if (_selectedDay == null ||
            _selectedDay!.year != day.year ||
            _selectedDay!.month != day.month ||
            _selectedDay!.day != day.day) {
          setState(() {
            _selectedDay = day;
            _viewAnchorDay = day;
            _focusedDay = day;
          });
        }

        final tz =
            ref.read(profileAllProvider).valueOrNull?.profile['timeZone']
                as String?;
        final effectiveTz =
            (tz != null && tz.trim().isNotEmpty) ? tz : 'UTC';
        try {
          await _loadDay(day, effectiveTz);
          if (!mounted) return;
          final entries =
              ref.read(calendarProvider)[_dayKey(day)] ?? <CalendarEntry>[];
          final match = entries
              .where((e) => e.appointmentId == appointmentId)
              .toList();
          if (match.isNotEmpty && mounted) {
            setState(() {
              _selectedEntry = match.first;
              _selectedEntryDoctorProfileId = _ownDoctorProfileId();
            });
          }
          _skipInitialProfileLoad = false;
        } catch (e) {
          debugPrint(
            'CalendarScreen: failed to go to appointment $appointmentId: $e',
          );
          _skipInitialProfileLoad = false;
        } finally {
          if (mounted) {
            _goToAppointmentInFlight = null;
          }
        }
      });
    }

    final quickBookIntent = ref.watch(calendarQuickBookIntentProvider);
    if (quickBookIntent != null && mounted && !_quickBookInFlight) {
      _quickBookInFlight = true;
      final intent = quickBookIntent;
      ref.read(calendarQuickBookIntentProvider.notifier).state = null;
      WidgetsBinding.instance.addPostFrameCallback((_) async {
        if (!mounted) return;
        try {
          await _handleQuickBookIntent(intent);
        } finally {
          if (mounted) _quickBookInFlight = false;
        }
      });
    }

    final l10n = AppLocalizations.of(context)!;
    final brand = Theme.of(context).colorScheme.primary;

    final profile = ref.watch(profileAllProvider).valueOrNull?.profile;
    final profileTimeZone = profile != null
        ? (profile['timeZone'] as String?)
        : null;
    final showTimeZoneHint =
        !_timeZoneHintDismissed &&
        _shouldShowTimeZoneMismatchHint(profileTimeZone);

    final useSinglePane = PlatformLayout.useSinglePane(context);

    return Scaffold(
      backgroundColor: const Color(0xFFF5F5F5),
      body: Padding(
        padding: Responsive.screenPadding(context),
        child: useSinglePane && _selectedEntry != null
            ? _buildSlotDetailsPanel(key: const ValueKey('details_mobile'))
            : useSinglePane
                ? _buildMobileCalendar(context, l10n, brand, showTimeZoneHint, profileTimeZone)
                : _buildDesktopCalendar(context, l10n, brand, showTimeZoneHint, profileTimeZone),
      ),
    );
  }

  Widget _buildMobileCalendar(
    BuildContext context,
    AppLocalizations l10n,
    Color brand,
    bool showTimeZoneHint,
    String? profileTimeZone,
  ) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (showTimeZoneHint) _buildTimeZoneHint(context, l10n, profileTimeZone),
        _buildCalendarHeaderRow(
          context,
          l10n,
          brand,
          part: _CalendarHeaderPart.combined,
        ),
        SizedBox(height: Responsive.sectionGap(context)),
        CalendarMonthPanel(
          compact: true,
          key: ValueKey(
            'calendar_mobile_${_selectedDay?.year ?? _focusedDay.year}_${_selectedDay?.month ?? _focusedDay.month}',
          ),
          focusedDay: _focusedDay,
          selectedDay: _selectedDay,
          onChanged: (d) {
            setState(() {
              _selectedDay = DateTime(d.year, d.month, d.day);
              _viewAnchorDay = _selectedDay;
              _focusedDay = _selectedDay!;
              _selectedEntry = null;
              _selectedEntryDoctorProfileId = null;
              _initialBookingPlaceForSelection = null;
            });
            final tz = ref
                    .read(profileAllProvider)
                    .valueOrNull
                    ?.profile['timeZone']
                as String?;
            final effectiveTz =
                (tz != null && tz.trim().isNotEmpty) ? tz : 'UTC';
            _reloadCalendar(effectiveTz);
          },
          onFocusedDayChanged: (d) {
            setState(() => _focusedDay = d);
            _loadMonth(d, _effectiveProfileTimeZone());
          },
          showUpdateCard: _shouldShowUpdateScheduleCard,
          onGoToSchedule: () {
            ShellScope.pushNamed(context, AppRoutes.setupSchedule);
          },
        ),
        SizedBox(height: Responsive.sectionGap(context)),
        Expanded(child: _buildCalendarEntriesList(context, l10n, brand)),
      ],
    );
  }

  Widget _buildDesktopCalendar(
    BuildContext context,
    AppLocalizations l10n,
    Color brand,
    bool showTimeZoneHint,
    String? profileTimeZone,
  ) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Expanded(
          flex: 3,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              if (showTimeZoneHint) _buildTimeZoneHint(context, l10n, profileTimeZone),
              _buildCalendarHeaderRow(
                context,
                l10n,
                brand,
                part: _CalendarHeaderPart.title,
              ),
              const SizedBox(height: 16),
              Expanded(child: _buildCalendarEntriesList(context, l10n, brand)),
            ],
          ),
        ),
        const SizedBox(width: 24),
        Expanded(
          flex: 2,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              _buildCalendarHeaderRow(
                context,
                l10n,
                brand,
                part: _CalendarHeaderPart.toolbar,
              ),
              const SizedBox(height: 16),
              Expanded(child: _buildCalendarRightPanel(context)),
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildTimeZoneHint(
    BuildContext context,
    AppLocalizations l10n,
    String? profileTimeZone,
  ) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Material(
        color: Colors.amber.shade50,
        borderRadius: BorderRadius.circular(8),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
          child: Row(
            children: [
              Icon(Icons.schedule, color: Colors.amber.shade800, size: 20),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  _timeZoneMismatchHintMessage(
                    scheduleTimeZone: profileTimeZone?.trim() ?? 'Unknown',
                  ),
                  style: TextStyle(fontSize: 13, color: Colors.amber.shade900),
                ),
              ),
              TextButton(
                onPressed: () => setState(() => _timeZoneHintDismissed = true),
                child: Text(l10n.translate('dismiss') ?? 'Dismiss'),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildCalendarHeaderRow(
    BuildContext context,
    AppLocalizations l10n,
    Color brand, {
    _CalendarHeaderPart part = _CalendarHeaderPart.combined,
  }) {
    final compactToolbar = PlatformLayout.useCompactToolbar(context);
    final useGrid = !PlatformLayout.useSinglePane(context);
    final dateLabel = _calendarDateRangeLabel(l10n);

    final filterControl = compactToolbar
        ? IconButton.filledTonal(
            onPressed: () => _showFilterDialog(context),
            icon: const Icon(Icons.tune),
            tooltip: l10n.filter,
          )
        : ShifaSecondaryButton(
            label: l10n.filter,
            onPressed: () => _showFilterDialog(context),
            icon: Icons.tune,
          );

    final blockControl = compactToolbar
        ? IconButton.filledTonal(
            onPressed: _selectedDay == null
                ? null
                : () => _showBlockTimeDialog(context),
            icon: const Icon(Icons.block),
            tooltip: l10n.translate('blockTime') ?? 'Block time',
          )
        : ShifaSecondaryButton(
            label: l10n.translate('blockTime') ?? 'Block time',
            onPressed: _selectedDay == null
                ? null
                : () => _showBlockTimeDialog(context),
            icon: Icons.block,
          );

    final dayViewControl = useGrid
        ? _DayViewCountSelector(
            value: _dayViewCount,
            brand: brand,
            compact: compactToolbar,
            onChanged: (count) {
              setState(() => _dayViewCount = count);
              _reloadCalendar(_effectiveProfileTimeZone());
            },
          )
        : null;

    final loadingIndicator = _loadingDay
        ? const SizedBox(
            width: 18,
            height: 18,
            child: CircularProgressIndicator(strokeWidth: 2),
          )
        : null;

    if (part == _CalendarHeaderPart.title) {
      return ConstrainedBox(
        constraints: const BoxConstraints(
          minHeight: _kCalendarDesktopHeaderMinHeight,
        ),
        child: Align(
          alignment: Alignment.centerLeft,
          child: Row(
            children: [
              Text(l10n.calendar, style: Responsive.pageTitleStyle(context)),
              if (dateLabel != null) ...[
                const SizedBox(width: 12),
                Flexible(
                  child: Text(
                    dateLabel,
                    style: Responsive.pageSubtitleStyle(context),
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
              ],
            ],
          ),
        ),
      );
    }

    if (part == _CalendarHeaderPart.toolbar) {
      return ConstrainedBox(
        constraints: const BoxConstraints(
          minHeight: _kCalendarDesktopHeaderMinHeight,
        ),
        child: Align(
          alignment: Alignment.centerRight,
          child: Wrap(
            alignment: WrapAlignment.end,
            crossAxisAlignment: WrapCrossAlignment.center,
            spacing: 8,
            runSpacing: 8,
            children: [
              blockControl,
              if (dayViewControl != null) dayViewControl,
              filterControl,
              if (loadingIndicator != null) loadingIndicator,
            ],
          ),
        ),
      );
    }

    // Mobile / combined: title and toolbar on one row.
    if (compactToolbar) {
      return Row(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(l10n.calendar, style: Responsive.pageTitleStyle(context)),
                if (dateLabel != null) ...[
                  const SizedBox(height: 2),
                  Text(
                    dateLabel,
                    style: Responsive.pageSubtitleStyle(context),
                    overflow: TextOverflow.ellipsis,
                  ),
                ],
              ],
            ),
          ),
          if (loadingIndicator != null)
            Padding(
              padding: const EdgeInsets.only(right: 4),
              child: loadingIndicator,
            ),
          blockControl,
          if (dayViewControl != null) dayViewControl,
          const SizedBox(width: 4),
          filterControl,
        ],
      );
    }

    return Row(
      children: [
        Text(l10n.calendar, style: Responsive.pageTitleStyle(context)),
        const SizedBox(width: 12),
        if (dateLabel != null)
          Flexible(
            child: Text(
              dateLabel,
              style: Responsive.pageSubtitleStyle(context),
              overflow: TextOverflow.ellipsis,
            ),
          ),
        const Spacer(),
        if (loadingIndicator != null)
          const Padding(
            padding: EdgeInsets.only(right: 8.0),
            child: SizedBox(
              width: 18,
              height: 18,
              child: CircularProgressIndicator(strokeWidth: 2),
            ),
          ),
        const SizedBox(width: 8),
        blockControl,
        if (dayViewControl != null) dayViewControl,
        const SizedBox(width: 8),
        filterControl,
      ],
    );
  }

  Future<void> _showFilterDialog(BuildContext context) async {
    bool tempShowAppointments = _showAppointments;
    bool tempShowFreeSlots = _showFreeSlots;
    bool tempShowBlockedTime = _showBlockedTime;

    final result = await showDialog<bool>(
      context: context,
      builder: (ctx) {
        final l10n = AppLocalizations.of(ctx)!;
        return StatefulBuilder(
          builder: (context, setDialogState) {
            return AlertDialog(
              title: Text(l10n.filter),
              content: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  CheckboxListTile(
                    title: Text(
                      l10n.translate('showAppointments') ?? 'Show Appointments',
                    ),
                    value: tempShowAppointments,
                    onChanged: (val) {
                      setDialogState(() {
                        tempShowAppointments = val ?? true;
                      });
                    },
                  ),
                  CheckboxListTile(
                    title: Text(
                      l10n.translate('showFreeSlots') ?? 'Show Free Slots',
                    ),
                    value: tempShowFreeSlots,
                    onChanged: (val) {
                      setDialogState(() {
                        tempShowFreeSlots = val ?? true;
                      });
                    },
                  ),
                  CheckboxListTile(
                    title: Text(
                      l10n.translate('showBlockedTime') ?? 'Show blocked time',
                    ),
                    value: tempShowBlockedTime,
                    onChanged: (val) {
                      setDialogState(() {
                        tempShowBlockedTime = val ?? true;
                      });
                    },
                  ),
                ],
              ),
              actions: [
                TextButton(
                  onPressed: () => Navigator.pop(ctx, false),
                  child: Text(l10n.cancel),
                ),
                ShifaPrimaryButton(
                  label: l10n.translate('apply') ?? 'Apply',
                  onPressed: () => Navigator.pop(ctx, true),
                  icon: Icons.check,
                ),
              ],
            );
          },
        );
      },
    );

    if (result == true && mounted) {
      setState(() {
        _showAppointments = tempShowAppointments;
        _showFreeSlots = tempShowFreeSlots;
        _showBlockedTime = tempShowBlockedTime;
      });

      final tz = ref.read(profileAllProvider).valueOrNull?.profile['timeZone']
          as String?;
      final effectiveTz = (tz != null && tz.trim().isNotEmpty) ? tz : 'UTC';
      if (_selectedDay != null) {
        await _reloadCalendar(effectiveTz);
      }
    }
  }

  bool _timeRangesOverlap(
    TimeOfDay aStart,
    TimeOfDay aEnd,
    TimeOfDay bStart,
    TimeOfDay bEnd,
  ) {
    final a0 = aStart.hour * 60 + aStart.minute;
    final a1 = aEnd.hour * 60 + aEnd.minute;
    final b0 = bStart.hour * 60 + bStart.minute;
    final b1 = bEnd.hour * 60 + bEnd.minute;
    return a0 < b1 && b0 < a1;
  }

  String _localToUtcIso(
    String doctorTimeZone,
    DateTime day,
    TimeOfDay time,
  ) {
    return CalendarController.localDateTimeToUtcIso(
      doctorTimeZone,
      day.year,
      day.month,
      day.day,
      time.hour,
      time.minute,
    );
  }

  Future<void> _showBlockTimeDialog(BuildContext context) async {
    final day = _selectedDay;
    if (day == null) return;

    final l10n = AppLocalizations.of(context)!;
    final doctorTimeZone = _effectiveProfileTimeZone();
    var mode = _ScheduleBlockMode.entireDay;
    var startTime = const TimeOfDay(hour: 9, minute: 0);
    var endTime = const TimeOfDay(hour: 17, minute: 0);
    var rangeStartDate = DateTime(day.year, day.month, day.day);
    var rangeEndDate = DateTime(day.year, day.month, day.day);
    var cancelOverlapping = true;
    var overlappingCount = 0;
    var loadingOverlapCount = false;
    final reasonCtrl = TextEditingController(
      text: l10n.translate('emergencyBlock') ?? 'Emergency',
    );
    var saving = false;

    Future<void> pickTime(
      BuildContext ctx,
      TimeOfDay initial,
      void Function(TimeOfDay) onPicked,
    ) async {
      final picked = await showTimePicker(
        context: ctx,
        initialTime: initial,
      );
      if (picked != null) onPicked(picked);
    }

    Future<void> pickDate(
      BuildContext ctx,
      DateTime initial,
      void Function(DateTime) onPicked,
    ) async {
      final picked = await showDatePicker(
        context: ctx,
        initialDate: initial,
        firstDate: DateTime(day.year - 1, day.month, day.day),
        lastDate: DateTime(day.year + 2, day.month, day.day),
      );
      if (picked != null) {
        onPicked(DateTime(picked.year, picked.month, picked.day));
      }
    }

    bool blockRangeValid(_ScheduleBlockMode currentMode) {
      switch (currentMode) {
        case _ScheduleBlockMode.entireDay:
          return true;
        case _ScheduleBlockMode.timeRange:
          return (endTime.hour * 60 + endTime.minute) >
              (startTime.hour * 60 + startTime.minute);
        case _ScheduleBlockMode.dateRange:
          return !rangeEndDate.isBefore(rangeStartDate);
      }
    }

    ({
      DateTime startDay,
      TimeOfDay startTod,
      DateTime endDay,
      TimeOfDay endTod,
    }) blockRangeForMode(_ScheduleBlockMode currentMode) {
      switch (currentMode) {
        case _ScheduleBlockMode.entireDay:
          return (
            startDay: day,
            startTod: const TimeOfDay(hour: 0, minute: 0),
            endDay: day,
            endTod: const TimeOfDay(hour: 23, minute: 59),
          );
        case _ScheduleBlockMode.timeRange:
          return (
            startDay: day,
            startTod: startTime,
            endDay: day,
            endTod: endTime,
          );
        case _ScheduleBlockMode.dateRange:
          return (
            startDay: rangeStartDate,
            startTod: const TimeOfDay(hour: 0, minute: 0),
            endDay: rangeEndDate,
            endTod: const TimeOfDay(hour: 23, minute: 59),
          );
      }
    }

    await showDialog<void>(
      context: context,
      builder: (ctx) {
        var overlapInitialized = false;

        Future<void> refreshOverlapCount(VoidCallback rebuild) async {
          if (!blockRangeValid(mode)) {
            rebuild();
            return;
          }
          final range = blockRangeForMode(mode);
          loadingOverlapCount = true;
          rebuild();
          try {
            final startAtUtc = _localToUtcIso(
              doctorTimeZone,
              range.startDay,
              range.startTod,
            );
            final endAtUtc = _localToUtcIso(
              doctorTimeZone,
              range.endDay,
              range.endTod,
            );
            overlappingCount = await ref
                .read(calendarProvider.notifier)
                .countOverlappingAppointmentsForBlock(
                  startAtUtc: startAtUtc,
                  endAtUtc: endAtUtc,
                );
          } catch (_) {
            overlappingCount = 0;
          } finally {
            loadingOverlapCount = false;
            rebuild();
          }
        }

        return StatefulBuilder(
          builder: (context, setDialogState) {
            if (!overlapInitialized) {
              overlapInitialized = true;
              WidgetsBinding.instance.addPostFrameCallback((_) {
                refreshOverlapCount(() => setDialogState(() {}));
              });
            }

            return AlertDialog(
              title: Text(l10n.translate('blockTimeTitle') ?? 'Block time'),
              content: SingleChildScrollView(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    RadioListTile<_ScheduleBlockMode>(
                      title: Text(
                        l10n.translate('blockEntireDay') ?? 'Block entire day',
                      ),
                      value: _ScheduleBlockMode.entireDay,
                      groupValue: mode,
                      onChanged: saving
                          ? null
                          : (v) {
                              if (v == null) return;
                              setDialogState(() {
                                mode = v;
                                overlappingCount = 0;
                              });
                              refreshOverlapCount(() => setDialogState(() {}));
                            },
                    ),
                    RadioListTile<_ScheduleBlockMode>(
                      title: Text(
                        l10n.translate('blockTimeRange') ?? 'Block time range',
                      ),
                      value: _ScheduleBlockMode.timeRange,
                      groupValue: mode,
                      onChanged: saving
                          ? null
                          : (v) {
                              if (v == null) return;
                              setDialogState(() {
                                mode = v;
                                overlappingCount = 0;
                              });
                              refreshOverlapCount(() => setDialogState(() {}));
                            },
                    ),
                    RadioListTile<_ScheduleBlockMode>(
                      title: Text(
                        l10n.translate('blockDateRange') ??
                            'Block multiple days',
                      ),
                      value: _ScheduleBlockMode.dateRange,
                      groupValue: mode,
                      onChanged: saving
                          ? null
                          : (v) {
                              if (v == null) return;
                              setDialogState(() {
                                mode = v;
                                overlappingCount = 0;
                              });
                              refreshOverlapCount(() => setDialogState(() {}));
                            },
                    ),
                    if (mode == _ScheduleBlockMode.timeRange) ...[
                      ListTile(
                        contentPadding: EdgeInsets.zero,
                        title: Text(l10n.start),
                        trailing: TextButton(
                          onPressed: saving
                              ? null
                              : () => pickTime(
                                    ctx,
                                    startTime,
                                    (t) {
                                      setDialogState(() => startTime = t);
                                      refreshOverlapCount(
                                        () => setDialogState(() {}),
                                      );
                                    },
                                  ),
                          child: Text(
                            '${_two(startTime.hour)}:${_two(startTime.minute)}',
                          ),
                        ),
                      ),
                      ListTile(
                        contentPadding: EdgeInsets.zero,
                        title: Text(
                          l10n.translate('bookingEndTime') ?? 'End time',
                        ),
                        trailing: TextButton(
                          onPressed: saving
                              ? null
                              : () => pickTime(
                                    ctx,
                                    endTime,
                                    (t) {
                                      setDialogState(() => endTime = t);
                                      refreshOverlapCount(
                                        () => setDialogState(() {}),
                                      );
                                    },
                                  ),
                          child: Text(
                            '${_two(endTime.hour)}:${_two(endTime.minute)}',
                          ),
                        ),
                      ),
                    ],
                    if (mode == _ScheduleBlockMode.dateRange) ...[
                      ListTile(
                        contentPadding: EdgeInsets.zero,
                        title: Text(l10n.translate('fromDate') ?? 'From date'),
                        trailing: TextButton(
                          onPressed: saving
                              ? null
                              : () => pickDate(
                                    ctx,
                                    rangeStartDate,
                                    (d) {
                                      setDialogState(() {
                                        rangeStartDate = d;
                                        if (rangeEndDate.isBefore(d)) {
                                          rangeEndDate = d;
                                        }
                                      });
                                      refreshOverlapCount(
                                        () => setDialogState(() {}),
                                      );
                                    },
                                  ),
                          child: Text(
                            '${rangeStartDate.day}.${rangeStartDate.month}.${rangeStartDate.year}',
                          ),
                        ),
                      ),
                      ListTile(
                        contentPadding: EdgeInsets.zero,
                        title: Text(l10n.translate('toDate') ?? 'To date'),
                        trailing: TextButton(
                          onPressed: saving
                              ? null
                              : () => pickDate(
                                    ctx,
                                    rangeEndDate,
                                    (d) {
                                      setDialogState(() => rangeEndDate = d);
                                      refreshOverlapCount(
                                        () => setDialogState(() {}),
                                      );
                                    },
                                  ),
                          child: Text(
                            '${rangeEndDate.day}.${rangeEndDate.month}.${rangeEndDate.year}',
                          ),
                        ),
                      ),
                    ],
                    TextField(
                      controller: reasonCtrl,
                      enabled: !saving,
                      decoration: InputDecoration(
                        labelText:
                            l10n.translate('blockReason') ?? 'Reason (optional)',
                        hintText: l10n.translate('blockReasonHint') ??
                            'Emergency, personal, etc.',
                      ),
                    ),
                    const SizedBox(height: 8),
                    CheckboxListTile(
                      contentPadding: EdgeInsets.zero,
                      title: Text(
                        l10n.translate('blockCancelOverlapping') ??
                            'Cancel overlapping appointments',
                      ),
                      subtitle: Text(
                        l10n.translate('blockCancelOverlappingHint') ??
                            'Patients will be notified automatically.',
                        style: TextStyle(
                          fontSize: 12,
                          color: Colors.grey.shade700,
                        ),
                      ),
                      value: cancelOverlapping,
                      onChanged: saving
                          ? null
                          : (v) => setDialogState(
                                () => cancelOverlapping = v ?? true,
                              ),
                    ),
                    if (loadingOverlapCount)
                      const Padding(
                        padding: EdgeInsets.only(top: 8),
                        child: LinearProgressIndicator(minHeight: 2),
                      )
                    else if (overlappingCount > 0) ...[
                      const SizedBox(height: 8),
                      Text(
                        cancelOverlapping
                            ? (l10n
                                      .translate('blockOverlapWillCancel')
                                      ?.replaceAll(
                                        '{{count}}',
                                        '$overlappingCount',
                                      ) ??
                                  '$overlappingCount appointment(s) will be cancelled.')
                            : (l10n.translate('blockOverlapWarning') ??
                                'Some existing appointments fall within this period.'),
                        style: TextStyle(
                          fontSize: 12,
                          color: cancelOverlapping
                              ? Colors.red.shade800
                              : Colors.orange.shade800,
                        ),
                      ),
                    ],
                  ],
                ),
              ),
              actions: [
                TextButton(
                  onPressed: saving ? null : () => Navigator.pop(ctx),
                  child: Text(l10n.cancel),
                ),
                ShifaPrimaryButton(
                  label: l10n.translate('blockTimeConfirm') ?? 'Block',
                  isLoading: saving,
                  onPressed: saving
                      ? null
                      : () async {
                          if (!blockRangeValid(mode)) {
                            ScaffoldMessenger.of(context).showSnackBar(
                              SnackBar(
                                content: Text(
                                  mode == _ScheduleBlockMode.dateRange
                                      ? (l10n.translate(
                                              'blockEndDateMustBeOnOrAfterStart',
                                            ) ??
                                            'End date must be on or after start date.')
                                      : l10n.endTimeMustBeAfterStartTime,
                                ),
                              ),
                            );
                            return;
                          }

                          setDialogState(() => saving = true);
                          try {
                            final range = blockRangeForMode(mode);
                            final startAtUtc = _localToUtcIso(
                              doctorTimeZone,
                              range.startDay,
                              range.startTod,
                            );
                            final endAtUtc = _localToUtcIso(
                              doctorTimeZone,
                              range.endDay,
                              range.endTod,
                            );
                            final cancelledCount = await ref
                                .read(calendarProvider.notifier)
                                .createScheduleBlock(
                                  day: range.startDay,
                                  startAtUtc: startAtUtc,
                                  endAtUtc: endAtUtc,
                                  doctorTimeZone: doctorTimeZone,
                                  reason: reasonCtrl.text.trim(),
                                  cancelOverlappingAppointments:
                                      cancelOverlapping,
                                  refreshThroughDay: range.endDay,
                                );
                            if (cancelOverlapping && cancelledCount > 0) {
                              await invalidateAppointmentRelatedProviders(ref);
                            }
                            if (context.mounted) {
                              Navigator.pop(ctx);
                              final successMsg = cancelledCount > 0
                                  ? (l10n
                                            .translate('blockTimeSuccessWithCancel')
                                            ?.replaceAll(
                                              '{{count}}',
                                              '$cancelledCount',
                                            ) ??
                                        'Time blocked. $cancelledCount appointment(s) cancelled.')
                                  : (l10n.translate('blockTimeSuccess') ??
                                      'Time blocked successfully');
                              ScaffoldMessenger.of(context).showSnackBar(
                                SnackBar(content: Text(successMsg)),
                              );
                            }
                          } catch (e) {
                            if (context.mounted) {
                              ScaffoldMessenger.of(context).showSnackBar(
                                SnackBar(content: Text('$e')),
                              );
                            }
                          } finally {
                            if (context.mounted) {
                              setDialogState(() => saving = false);
                            }
                          }
                        },
                ),
              ],
            );
          },
        );
      },
    );
    reasonCtrl.dispose();
  }

  Widget _buildCalendarEntriesList(
    BuildContext context,
    AppLocalizations l10n,
    Color brand,
  ) {
    if (_selectedDay == null) {
      return _EmptyCalendarHint(brand: brand);
    }

    final clinic = ref.watch(selectedClinicProvider);
    final staffMembers = clinic == null
        ? const <ClinicMember>[]
        : (ref.watch(clinicMembersProvider(clinic.clinicId)).valueOrNull ??
            const <ClinicMember>[]);
    final staff = _schedulableStaff(staffMembers);
    final visibleDoctorIds = _orderedVisibleStaffDoctorIds;
    final useGrid = !PlatformLayout.useSinglePane(context);
    final loading = _loadingDay || _isWaitingForProfile;

    void onTapEntry(CalendarEntry entry, DateTime day, int doctorProfileId) {
      setState(() => _selectedDay = _dayKey(day));
      _selectCalendarEntry(entry, doctorProfileId: doctorProfileId);
    }

    Widget buildGridForDoctor(int doctorProfileId, {required bool shrinkWrap}) {
      final accent = _staffAccentColor(doctorProfileId, staff, brand);
      return CalendarWeekGridView(
        days: _visibleDays,
        entriesForDay: (day) => _entriesForDoctor(day, doctorProfileId),
        onTapEntry: (entry, day) => onTapEntry(entry, day, doctorProfileId),
        selectedEntry: _selectedEntryDoctorProfileId == doctorProfileId
            ? _selectedEntry
            : null,
        brand: accent,
        loading: loading,
        shrinkWrap: shrinkWrap,
      );
    }

    Widget buildListForDoctor(int doctorProfileId) {
      final accent = _staffAccentColor(doctorProfileId, staff, brand);
      return CalendarDayEntriesList(
        entries: _entriesForDoctor(_selectedDay, doctorProfileId),
        onTap: (entry) => _selectCalendarEntry(
          entry,
          doctorProfileId: doctorProfileId,
        ),
        selected: _selectedEntryDoctorProfileId == doctorProfileId
            ? _selectedEntry
            : null,
        brand: accent,
        loading: loading,
      );
    }

    Widget staffHeader(int doctorProfileId) {
      final accent = _staffAccentColor(doctorProfileId, staff, brand);
      return Padding(
        padding: const EdgeInsets.only(bottom: 8),
        child: Row(
          children: [
            Container(
              width: 10,
              height: 10,
              decoration: BoxDecoration(color: accent, shape: BoxShape.circle),
            ),
            const SizedBox(width: 8),
            Expanded(
              child: Text(
                _staffDisplayName(doctorProfileId, staff),
                style: TextStyle(
                  fontWeight: FontWeight.w700,
                  fontSize: 14,
                  color: accent.withOpacity(0.95),
                ),
              ),
            ),
          ],
        ),
      );
    }

    final staffBar = staff.length >= 2
        ? _buildStaffDoctorBar(staff, brand)
        : null;

    if (useGrid) {
      if (visibleDoctorIds.length <= 1) {
        final doctorId =
            visibleDoctorIds.isNotEmpty ? visibleDoctorIds.first : _ownDoctorProfileId();
        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            if (staffBar != null) staffBar,
            Expanded(
              child: buildGridForDoctor(
                doctorId ?? _ownDoctorProfileId() ?? 0,
                shrinkWrap: false,
              ),
            ),
          ],
        );
      }

      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          if (staffBar != null) staffBar,
          Expanded(
            child: SingleChildScrollView(
              padding: const EdgeInsets.only(bottom: 8),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  for (var i = 0; i < visibleDoctorIds.length; i++) ...[
                    if (i > 0) const SizedBox(height: 20),
                    staffHeader(visibleDoctorIds[i]),
                    buildGridForDoctor(visibleDoctorIds[i], shrinkWrap: true),
                  ],
                ],
              ),
            ),
          ),
        ],
      );
    }

    if (visibleDoctorIds.length <= 1) {
      final doctorId =
          visibleDoctorIds.isNotEmpty ? visibleDoctorIds.first : _ownDoctorProfileId();
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          if (staffBar != null) staffBar,
          Expanded(
            child: buildListForDoctor(doctorId ?? _ownDoctorProfileId() ?? 0),
          ),
        ],
      );
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (staffBar != null) staffBar,
        Expanded(
          child: ListView.separated(
            itemCount: visibleDoctorIds.length,
            separatorBuilder: (_, __) => const SizedBox(height: 16),
            itemBuilder: (context, index) {
              final doctorId = visibleDoctorIds[index];
              return Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  staffHeader(doctorId),
                  buildListForDoctor(doctorId),
                ],
              );
            },
          ),
        ),
      ],
    );
  }

  Widget _buildStaffDoctorBar(List<ClinicMember> staff, Color brand) {
    final l10n = AppLocalizations.of(context)!;
    final tz = _effectiveProfileTimeZone();
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            l10n.translate('calendarStaffCalendars') ?? 'Staff calendars',
            style: TextStyle(
              fontSize: 12,
              fontWeight: FontWeight.w600,
              color: Colors.grey.shade700,
            ),
          ),
          const SizedBox(height: 6),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              for (final member in staff)
                Builder(
                  builder: (context) {
                    final accent =
                        _staffAccentColor(member.doctorProfileId, staff, brand);
                    final selected =
                        _visibleStaffDoctorIds.contains(member.doctorProfileId);
                    return FilterChip(
                      showCheckmark: false,
                      avatar: CircleAvatar(
                        backgroundColor: accent,
                        radius: 8,
                        child: selected
                            ? Icon(Icons.check, size: 12, color: Colors.white)
                            : null,
                      ),
                      label: Text(_staffDisplayName(member.doctorProfileId, staff)),
                      selected: selected,
                      onSelected: (_) =>
                          _toggleStaffDoctor(member.doctorProfileId, tz),
                      selectedColor: accent.withOpacity(0.16),
                      checkmarkColor: accent,
                      labelStyle: TextStyle(
                        color: selected ? accent.withOpacity(0.95) : null,
                        fontWeight: selected ? FontWeight.w600 : FontWeight.w500,
                      ),
                      side: BorderSide(
                        color: selected ? accent.withOpacity(0.55) : Colors.grey.shade300,
                      ),
                    );
                  },
                ),
            ],
          ),
        ],
      ),
    );
  }

  ({
    int? clinicDoctorProfileId,
    String? clinicDoctorDisplayName,
    String? scheduleTimeZone,
    String? primaryClinicVenueLabel,
  }) _slotPanelClinicContext(List<ClinicMember> staff) {
    final ownId = _ownDoctorProfileId();
    final selectedId = _selectedEntryDoctorProfileId;
    final clinic = ref.read(selectedClinicProvider);
    final isOther = selectedId != null && selectedId != ownId;
    return (
      clinicDoctorProfileId: isOther ? selectedId : null,
      clinicDoctorDisplayName:
          isOther ? _staffDisplayName(selectedId!, staff) : null,
      scheduleTimeZone: _scheduleTimeZoneForStaff(selectedId),
      primaryClinicVenueLabel: clinic?.address,
    );
  }

  Widget _buildSlotDetailsPanel({Key? key}) {
    final clinic = ref.watch(selectedClinicProvider);
    final staff = clinic == null
        ? const <ClinicMember>[]
        : _schedulableStaff(
            ref.watch(clinicMembersProvider(clinic.clinicId)).valueOrNull ??
                const <ClinicMember>[],
          );
    final ctx = _slotPanelClinicContext(staff);
    final tz = ctx.scheduleTimeZone ?? _effectiveProfileTimeZone();

    return CalendarSlotDetailsPanel(
      key: key,
      entry: _selectedEntry!,
      day: _selectedDay!,
      initialBookingPlace: _initialBookingPlaceForSelection,
      clinicDoctorProfileId: ctx.clinicDoctorProfileId,
      clinicDoctorDisplayName: ctx.clinicDoctorDisplayName,
      scheduleTimeZone: ctx.scheduleTimeZone,
      primaryClinicVenueLabel: ctx.primaryClinicVenueLabel,
      onSavedSuccessfully: () async {
        if (tz.trim().isNotEmpty) {
          await _reloadDoctorCalendar(_selectedEntryDoctorProfileId, tz);
        }
        if (mounted) _clearSelectedEntry();
      },
      onClose: _clearSelectedEntry,
    );
  }

  Widget _buildCalendarRightPanel(BuildContext context) {
    return AnimatedSwitcher(
      duration: const Duration(milliseconds: 250),
      switchInCurve: Curves.easeOut,
      switchOutCurve: Curves.easeIn,
      child: _selectedEntry == null
          ? CalendarMonthPanel(
              key: ValueKey(
                'calendar_${_selectedDay?.year ?? _focusedDay.year}_${_selectedDay?.month ?? _focusedDay.month}_${_selectedDay?.day ?? _focusedDay.day}',
              ),
              focusedDay: _focusedDay,
              selectedDay: _selectedDay,
              onChanged: (d) {
                setState(() {
                  _selectedDay = DateTime(d.year, d.month, d.day);
                  _viewAnchorDay = _selectedDay;
                  _focusedDay = _selectedDay!;
                  _selectedEntry = null;
                  _selectedEntryDoctorProfileId = null;
                  _initialBookingPlaceForSelection = null;
                });
                final tz = ref
                        .read(profileAllProvider)
                        .valueOrNull
                        ?.profile['timeZone']
                    as String?;
                final effectiveTz =
                    (tz != null && tz.trim().isNotEmpty) ? tz : 'UTC';
                _reloadCalendar(effectiveTz);
              },
              onFocusedDayChanged: (d) {
              setState(() => _focusedDay = d);
              _loadMonth(d, _effectiveProfileTimeZone());
            },
              showUpdateCard: _shouldShowUpdateScheduleCard,
              onGoToSchedule: () {
                ShellScope.pushNamed(context, AppRoutes.setupSchedule);
              },
            )
          : _buildSlotDetailsPanel(key: const ValueKey('details')),
    );
  }
}

/// Segmented control for 1–7 day grid width on desktop calendar.
class _DayViewCountSelector extends StatelessWidget {
  const _DayViewCountSelector({
    required this.value,
    required this.brand,
    required this.compact,
    required this.onChanged,
  });

  final int value;
  final Color brand;
  final bool compact;
  final ValueChanged<int> onChanged;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final tooltip =
        l10n.translate('calendarDayViewCount') ?? 'Days shown in grid';

    return Tooltip(
      message: tooltip,
      child: SegmentedButton<int>(
        segments: [
          for (var i = 1; i <= 7; i++)
            ButtonSegment(
              value: i,
              label: Text('$i', style: TextStyle(fontSize: compact ? 12 : 13)),
            ),
        ],
        selected: {value},
        onSelectionChanged: (selected) {
          if (selected.isNotEmpty) onChanged(selected.first);
        },
        style: ButtonStyle(
          visualDensity: compact ? VisualDensity.compact : VisualDensity.standard,
          tapTargetSize: MaterialTapTargetSize.shrinkWrap,
        ),
        showSelectedIcon: false,
      ),
    );
  }
}

/// ---- Left: Empty state hint ----
class _EmptyCalendarHint extends StatelessWidget {
  const _EmptyCalendarHint({required this.brand, Key? key}) : super(key: key);
  final Color brand;
  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          // Illustration placeholder
          Container(
            width: 160,
            height: 160,
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(80),
              boxShadow: [
                BoxShadow(
                  color: Colors.black12,
                  blurRadius: 10,
                  offset: const Offset(0, 4),
                ),
              ],
            ),
            child: Icon(Icons.event_available, color: brand, size: 64),
          ),
          const SizedBox(height: 12),
          Text(
            AppLocalizations.of(
                  context,
                )!.translate('selectDatesToSeeSchedule') ??
                'Select dates to see your schedule',
            style: TextStyle(color: Colors.grey.shade600),
          ),
        ],
      ),
    );
  }
}

/// ---- Left: Day entries list ----
class CalendarDayEntriesList extends StatelessWidget {
  const CalendarDayEntriesList({
    Key? key,
    required this.entries,
    required this.onTap,
    required this.selected,
    required this.brand,
    this.loading = false,
  }) : super(key: key);

  final List<CalendarEntry> entries;
  final ValueChanged<CalendarEntry> onTap;
  final CalendarEntry? selected;
  final Color brand;
  final bool loading;

  String _two(int n) => n.toString().padLeft(2, '0');
  String _fmtRange(TimeOfDay s, TimeOfDay e) =>
      '${_two(s.hour)}:${_two(s.minute)} - ${_two(e.hour)}:${_two(e.minute)}';

  int _rowDurationMinutes(CalendarEntry e) =>
      ((e.end.hour * 60 + e.end.minute) - (e.start.hour * 60 + e.start.minute))
          .clamp(0, 24 * 60);

  String _abbrevDuration(BuildContext ctx, CalendarEntry e) {
    final m = _rowDurationMinutes(e);
    if (m <= 0) return '';
    final h = m ~/ 60;
    final r = m % 60;
    final l10n = AppLocalizations.of(ctx)!;
    final mins = l10n.minutes;
    if (h > 0 && r > 0) return '${h}h ${r} $mins';
    if (h > 0) return '${h}h';
    return '$r $mins';
  }
  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    if (loading) {
      return const Center(child: CircularProgressIndicator());
    }
    if (entries.isEmpty) {
      return Center(
        child: Text(
          l10n.translate('noItemsForThisDay') ?? 'No items for this day',
          style: TextStyle(color: Colors.grey.shade600),
        ),
      );
    }

    return ListView.separated(
      itemCount: entries.length,
      separatorBuilder: (_, __) => const SizedBox(height: 12),
      itemBuilder: (context, i) {
        final e = entries[i];
        final dur = _rowDurationMinutes(e);
        final minH =
            e.type == EntryType.appointment
                ? (68.0 + dur * 0.45).clamp(68.0, 154.0)
                : 64.0;
        final isSelected = identical(e, selected);
        final isVideoLocation = (e.location).toLowerCase().contains('video');
        final locationLabel = isVideoLocation ? l10n.videoCall : e.location;
        final isBlocked = e.type == EntryType.blocked;
        final isCompleted = e.type == EntryType.appointment &&
            (e.status?.trim().toUpperCase() ?? '') == 'COMPLETED';

        return Container(
          decoration: BoxDecoration(
            color: isBlocked ? Colors.red.shade50 : Colors.white,
            borderRadius: BorderRadius.circular(12),
            border: Border.all(
              color: isSelected
                  ? brand
                  : (isBlocked ? Colors.red.shade200 : Colors.transparent),
              width: isSelected ? 2 : 1,
            ),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withOpacity(0.04),
                blurRadius: 8,
                offset: const Offset(0, 4),
              ),
            ],
          ),
          child: Material(
            color: Colors.transparent,
            child: InkWell(
              borderRadius: BorderRadius.circular(11),
              onTap: () => onTap(e),
              child: ConstrainedBox(
                constraints: BoxConstraints(minHeight: minH),
                child: Padding(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 16,
                    vertical: 8,
                  ),
                  // ListTile clamps trailing height to its tile computation; time + badge + reason
                  // overflows by a few px. IntrinsicHeight lets the row grow with the tallest column.
                  child: Stack(
                    children: [
                      IntrinsicHeight(
                        child: Row(
                      crossAxisAlignment: CrossAxisAlignment.center,
                      children: [
                        e.type == EntryType.freeSlot
                            ? CircleAvatar(
                                backgroundColor: Colors.white,
                                foregroundColor: brand,
                                child: const Icon(Icons.add),
                              )
                            : e.type == EntryType.blocked
                            ? CircleAvatar(
                                backgroundColor: Colors.red.shade100,
                                foregroundColor: Colors.red.shade700,
                                child: const Icon(Icons.block),
                              )
                            : CircleAvatar(
                                backgroundColor: Colors.grey.shade300,
                                backgroundImage:
                                    (e.photoUrl != null &&
                                            e.photoUrl!.isNotEmpty)
                                        ? NetworkImage(e.photoUrl!)
                                        : null,
                                child:
                                    (e.photoUrl == null || e.photoUrl!.isEmpty)
                                    ? const Icon(
                                        Icons.person,
                                        color: Colors.white,
                                      )
                                    : null,
                              ),
                        const SizedBox(width: 16),
                        Expanded(
                          child: Column(
                            mainAxisAlignment: MainAxisAlignment.center,
                            crossAxisAlignment: CrossAxisAlignment.start,
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Align(
                                alignment: Alignment.centerLeft,
                                child: e.type == EntryType.freeSlot
                                    ? Text(
                                        AppLocalizations.of(context)!.freeSlots,
                                        style: const TextStyle(
                                          fontWeight: FontWeight.w600,
                                        ),
                                        maxLines: 2,
                                        overflow: TextOverflow.ellipsis,
                                      )
                                    : e.type == EntryType.blocked
                                    ? Text(
                                        e.blockReason?.trim().isNotEmpty == true
                                            ? e.blockReason!.trim()
                                            : (AppLocalizations.of(context)!
                                                      .translate('blockedTime') ??
                                                  'Blocked'),
                                        style: TextStyle(
                                          fontWeight: FontWeight.w600,
                                          color: Colors.red.shade800,
                                        ),
                                        maxLines: 2,
                                        overflow: TextOverflow.ellipsis,
                                      )
                                    : Text(
                                        e.patientName ??
                                            AppLocalizations.of(
                                                  context,
                                                )!.appointments,
                                        style: TextStyle(
                                          fontWeight: FontWeight.w600,
                                          decoration: isCompleted
                                              ? TextDecoration.lineThrough
                                              : null,
                                          decorationColor:
                                              Colors.grey.shade600,
                                        ),
                                        maxLines: 2,
                                        overflow: TextOverflow.ellipsis,
                                      ),
                              ),
                              if (locationLabel.trim().isNotEmpty &&
                                  e.type != EntryType.blocked)
                                Padding(
                                  padding: const EdgeInsets.only(top: 4),
                                  child: Align(
                                    alignment: Alignment.centerLeft,
                                    child: Container(
                                      padding: const EdgeInsets.symmetric(
                                        horizontal: 8,
                                        vertical: 4,
                                      ),
                                      decoration: BoxDecoration(
                                        color: isVideoLocation
                                            ? Colors.blue.shade50
                                            : Colors.teal.shade50,
                                        borderRadius: BorderRadius.circular(999),
                                        border: Border.all(
                                          color: isVideoLocation
                                              ? Colors.blue.shade100
                                              : Colors.teal.shade100,
                                        ),
                                      ),
                                      child: Row(
                                        mainAxisSize: MainAxisSize.min,
                                        children: [
                                          Icon(
                                            isVideoLocation
                                                ? Icons.videocam
                                                : Icons.location_on_outlined,
                                            size: 13,
                                            color: isVideoLocation
                                                ? Colors.blue.shade700
                                                : Colors.teal.shade700,
                                          ),
                                          const SizedBox(width: 4),
                                          Flexible(
                                            child: Text(
                                              locationLabel,
                                              maxLines: 1,
                                              overflow: TextOverflow.ellipsis,
                                              style: TextStyle(
                                                fontSize: 11,
                                                fontWeight: FontWeight.w600,
                                                color: isVideoLocation
                                                    ? Colors.blue.shade700
                                                    : Colors.teal.shade700,
                                              ),
                                            ),
                                          ),
                                        ],
                                      ),
                                    ),
                                  ),
                                ),
                            ],
                          ),
                        ),
                        Flexible(
                          fit: FlexFit.loose,
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.end,
                            mainAxisAlignment: MainAxisAlignment.center,
                            mainAxisSize: MainAxisSize.min,
                            children: [
                            Text(
                              _fmtRange(e.start, e.end),
                              style: TextStyle(
                                fontWeight: FontWeight.w600,
                                decoration: isCompleted
                                    ? TextDecoration.lineThrough
                                    : null,
                                decorationColor: Colors.grey.shade600,
                              ),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            ),
                            if (e.type == EntryType.appointment && dur > 5)
                              Padding(
                                padding: const EdgeInsets.only(top: 2),
                                child: Container(
                                  padding: const EdgeInsets.symmetric(
                                    horizontal: 6,
                                    vertical: 2,
                                  ),
                                  decoration: BoxDecoration(
                                    color: brand.withOpacity(0.06),
                                    borderRadius: BorderRadius.circular(999),
                                  ),
                                  child: Text(
                                    _abbrevDuration(context, e),
                                    style: TextStyle(
                                      fontSize: 10,
                                      fontWeight: FontWeight.w600,
                                      color: brand,
                                    ),
                                  ),
                                ),
                              ),
                            if (e.type == EntryType.blocked)
                              Padding(
                                padding: const EdgeInsets.only(top: 2),
                                child: Text(
                                  AppLocalizations.of(context)!
                                          .translate('blockedTime') ??
                                      'Blocked',
                                  style: TextStyle(
                                    fontSize: 12,
                                    color: Colors.red.shade700,
                                  ),
                                ),
                              ),
                            if (e.type == EntryType.appointment)
                              Padding(
                                padding: const EdgeInsets.only(top: 2),
                                child: Text(
                                  e.reason,
                                  style: TextStyle(
                                    fontSize: 12,
                                    color: Colors.grey.shade600,
                                  ),
                                ),
                              ),
                          ],
                        ),
                        ),
                      ],
                    ),
                      ),
                      if (isCompleted)
                        Positioned.fill(
                          child: Center(
                            child: IgnorePointer(
                              child: Container(
                                height: 1,
                                margin: const EdgeInsets.symmetric(
                                  horizontal: 8,
                                ),
                                color: Colors.grey.shade500.withOpacity(0.5),
                              ),
                            ),
                          ),
                        ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        );
      },
    );
  }
}

/// ---- Right: Calendar panel (customizable table_calendar) ----
///
/// Shared by [CalendarScreen] and flows that need the same month grid (e.g. clinic scheduling).
class CalendarMonthPanel extends ConsumerWidget {
  const CalendarMonthPanel({
    super.key,
    required this.focusedDay,
    required this.selectedDay,
    required this.onChanged,
    this.onFocusedDayChanged,
    required this.showUpdateCard,
    this.onGoToSchedule,
    this.compact = false,
  });

  final DateTime focusedDay;
  final DateTime? selectedDay;
  final ValueChanged<DateTime> onChanged;
  final ValueChanged<DateTime>? onFocusedDayChanged;
  final bool showUpdateCard;
  final bool compact;

  /// Used when [showUpdateCard] is true; optional otherwise.
  final VoidCallback? onGoToSchedule;

  CalendarDayOccupancy? _occupancyForDay(
    Map<DateTime, List<CalendarEntry>> entries,
    DateTime day,
  ) {
    final key = DateTime(day.year, day.month, day.day);
    if (!entries.containsKey(key)) return null;
    return CalendarDayOccupancy.fromEntries(entries[key] ?? const []);
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final intlLocale = _tableCalendarIntlLocale(context);
    final entries = ref.watch(calendarProvider);
    final isCompact = compact || PlatformLayout.useCompactToolbar(context);

    final headerStyle = HeaderStyle(
      formatButtonVisible: false,
      titleCentered: true,
      leftChevronIcon: Icon(Icons.chevron_left, size: isCompact ? 22 : 28),
      rightChevronIcon: Icon(Icons.chevron_right, size: isCompact ? 22 : 28),
      headerPadding: EdgeInsets.symmetric(vertical: isCompact ? 4 : 8),
      titleTextStyle: TextStyle(
        fontSize: isCompact ? 15 : 16,
        fontWeight: FontWeight.w600,
      ),
    );
    final daysOfWeekStyle = DaysOfWeekStyle(
      weekdayStyle: TextStyle(
        color: Colors.grey.shade700,
        fontWeight: FontWeight.w600,
        fontSize: isCompact ? 11 : 13,
      ),
      weekendStyle: TextStyle(
        color: Colors.grey.shade700,
        fontWeight: FontWeight.w600,
        fontSize: isCompact ? 11 : 13,
      ),
    );

    String weekdayLabel(DateTime day) {
      if (isCompact) {
        return DateFormat('EEEEE', intlLocale).format(day);
      }
      final l10n = AppLocalizations.of(context)!;
      return switch (day.weekday) {
        DateTime.monday => l10n.monday,
        DateTime.tuesday => l10n.tuesday,
        DateTime.wednesday => l10n.wednesday,
        DateTime.thursday => l10n.thursday,
        DateTime.friday => l10n.friday,
        DateTime.saturday => l10n.saturday,
        DateTime.sunday => l10n.sunday,
        _ => '',
      };
    }

    Widget buildDayCell(
      DateTime day, {
      required bool isSelected,
      required bool isToday,
      required bool isOutside,
    }) {
      final occupancy = isOutside ? null : _occupancyForDay(entries, day);
      return _OccupancyDayCell(
        day: day,
        occupancy: occupancy,
        isSelected: isSelected,
        isToday: isToday,
        isOutside: isOutside,
        compact: isCompact,
      );
    }

    final calendar = TableCalendar(
      locale: intlLocale,
      firstDay: DateTime(2020),
      lastDay: DateTime(2030),
      startingDayOfWeek: StartingDayOfWeek.monday,
      focusedDay: focusedDay,
      rowHeight: isCompact ? 36 : 48,
      daysOfWeekHeight: isCompact ? 24 : 20,
      selectedDayPredicate: (day) =>
          selectedDay != null && isSameDay(day, selectedDay!),
      onDaySelected: (selected, focused) {
        onChanged(
          DateTime(selected.year, selected.month, selected.day),
        );
        if (onFocusedDayChanged != null) onFocusedDayChanged!(focused);
      },
      onPageChanged: onFocusedDayChanged,
      calendarStyle: CalendarStyle(
        outsideDaysVisible: false,
        cellPadding: isCompact ? EdgeInsets.zero : const EdgeInsets.all(4),
        selectedDecoration: const BoxDecoration(
          color: Colors.transparent,
          shape: BoxShape.circle,
        ),
        todayDecoration: const BoxDecoration(
          color: Colors.transparent,
          shape: BoxShape.circle,
        ),
        defaultDecoration: const BoxDecoration(
          color: Colors.transparent,
          shape: BoxShape.circle,
        ),
        markerDecoration: const BoxDecoration(shape: BoxShape.circle),
      ),
      headerStyle: headerStyle,
      daysOfWeekStyle: daysOfWeekStyle,
      calendarBuilders: CalendarBuilders(
        defaultBuilder: (ctx, day, focusedDay) => buildDayCell(
          day,
          isSelected: false,
          isToday: isSameDay(day, DateTime.now()),
          isOutside: false,
        ),
        selectedBuilder: (ctx, day, focusedDay) => buildDayCell(
          day,
          isSelected: true,
          isToday: isSameDay(day, DateTime.now()),
          isOutside: false,
        ),
        todayBuilder: (ctx, day, focusedDay) {
          final selected =
              selectedDay != null && isSameDay(day, selectedDay!);
          return buildDayCell(
            day,
            isSelected: selected,
            isToday: true,
            isOutside: false,
          );
        },
        outsideBuilder: (ctx, day, focusedDay) => buildDayCell(
          day,
          isSelected: false,
          isToday: false,
          isOutside: true,
        ),
        dowBuilder: (ctx, day) {
          final label = weekdayLabel(day);
          final isWeekend =
              day.weekday == DateTime.saturday ||
              day.weekday == DateTime.sunday;
          return Center(
            child: ExcludeSemantics(
              child: Text(
                label,
                maxLines: 1,
                overflow: TextOverflow.clip,
                style: isWeekend
                    ? daysOfWeekStyle.weekendStyle
                    : daysOfWeekStyle.weekdayStyle,
              ),
            ),
          );
        },
        headerTitleBuilder: (ctx, focusedMonth) {
          final l10n = AppLocalizations.of(ctx)!;
          return Text(
            '${l10n.monthName(focusedMonth.month)} ${focusedMonth.year}',
            style: headerStyle.titleTextStyle,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            textAlign: headerStyle.titleCentered
                ? TextAlign.center
                : TextAlign.start,
          );
        },
      ),
    );

    return Column(
      key: key,
      mainAxisSize: MainAxisSize.min,
      children: [
        if (!isCompact)
          Align(
            alignment: Alignment.centerLeft,
            child: Text(
              '${focusedDay.year}',
              style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w700),
            ),
          ),
        if (!isCompact) const SizedBox(height: 12),
        Container(
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(16),
            boxShadow: [
              BoxShadow(
                color: Colors.black12.withOpacity(0.06),
                blurRadius: 12,
                offset: const Offset(0, 6),
              ),
            ],
          ),
          padding: EdgeInsets.all(isCompact ? 4 : 8),
          child: calendar,
        ),
        const SizedBox(height: 12),
        if (!isCompact)
          Padding(
            padding: const EdgeInsets.only(left: 4, right: 4, bottom: 4),
            child: Text(
              AppLocalizations.of(context)!
                      .translate('calendarOccupancyLegend') ??
                  'Dark = fully booked · No fill = open availability',
              style: TextStyle(fontSize: 11, color: Colors.grey.shade600),
              textAlign: TextAlign.center,
            ),
          ),
        if (showUpdateCard) ...[
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(12),
              boxShadow: [
                BoxShadow(
                  color: Colors.black12.withOpacity(0.06),
                  blurRadius: 12,
                  offset: const Offset(0, 6),
                ),
              ],
            ),
            child: Row(
              children: [
                const Icon(Icons.info, color: Colors.amber),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    AppLocalizations.of(
                          context,
                        )!.translate('updateScheduleMessage') ??
                        'Update schedule\nYour calendar does not provide booking slots this far ahead. Please update your schedule.',
                    style: TextStyle(color: Colors.grey.shade700),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),
          ShifaPrimaryButton(
            label: AppLocalizations.of(context)!.translate('goToSchedule') ??
                'Go To Schedule',
            onPressed: onGoToSchedule ?? () {},
            width: ButtonWidth.fill,
          ),
        ],
      ],
    );
  }
}

class _OccupancyDayCell extends StatelessWidget {
  const _OccupancyDayCell({
    required this.day,
    required this.occupancy,
    required this.isSelected,
    required this.isToday,
    required this.isOutside,
    this.compact = false,
  });

  final DateTime day;
  final CalendarDayOccupancy? occupancy;
  final bool isSelected;
  final bool isToday;
  final bool isOutside;
  final bool compact;

  @override
  Widget build(BuildContext context) {
    final cellSize = compact ? 30.0 : 36.0;
    final dayStyle = TextStyle(
      fontSize: compact ? 13 : 14,
      color: isToday ? AppColors.primaryTeal : Colors.grey.shade800,
      fontWeight: isToday ? FontWeight.w600 : FontWeight.normal,
    );

    if (isOutside) {
      return Center(
        child: Text(
          '${day.day}',
          style: TextStyle(color: Colors.grey.shade400, fontSize: compact ? 12 : 14),
        ),
      );
    }

    if (isSelected) {
      return Center(
        child: Container(
          width: cellSize,
          height: cellSize,
          decoration: const BoxDecoration(
            color: AppColors.primaryTeal,
            shape: BoxShape.circle,
          ),
          alignment: Alignment.center,
          child: Text(
            '${day.day}',
            style: TextStyle(
              color: Colors.white,
              fontWeight: FontWeight.w600,
              fontSize: compact ? 13 : 14,
            ),
          ),
        ),
      );
    }

    final freeRatio = occupancy?.freeRatio;
    final background = occupancyBackgroundColor(freeRatio);

    if (background == null) {
      return Center(
        child: Text(
          '${day.day}',
          style: dayStyle,
        ),
      );
    }

    return Center(
      child: Container(
        width: cellSize,
        height: cellSize,
        decoration: BoxDecoration(
          color: background,
          shape: BoxShape.circle,
          border: isToday
              ? Border.all(color: AppColors.primaryTeal, width: compact ? 1.5 : 2)
              : null,
        ),
        alignment: Alignment.center,
        child: Text(
          '${day.day}',
          style: TextStyle(
            color: occupancyTextColor(freeRatio),
            fontWeight: isToday ? FontWeight.w600 : FontWeight.normal,
          ),
        ),
      ),
    );
  }
}

/// Read-only place field for already booked appointments.
class AppointmentPlaceDropdown extends ConsumerWidget {
  const AppointmentPlaceDropdown({
    Key? key,
    required this.entry,
    this.clinicVenueLabelOverride,
  }) : super(key: key);
  final CalendarEntry entry;

  /// When scheduling on behalf of another doctor, show clinic street rather than logged-in doctor.
  final String? clinicVenueLabelOverride;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context)!;
    final profileAsync = ref.watch(profileAllProvider);
    final profile = profileAsync.valueOrNull?.profile;

    final override =
        clinicVenueLabelOverride?.trim().isNotEmpty == true
            ? clinicVenueLabelOverride!.trim()
            : null;

    final doctorAddress =
        override ??
            (profile != null
                    ? ((profile['address'] as String?)?.trim().isNotEmpty == true
                          ? (profile['address'] as String).trim()
                          : (profile['locationStreetAddress'] as String?)?.trim())
                    : null)
                ?.trim();
    final clinicOption = (doctorAddress != null && doctorAddress.isNotEmpty)
        ? doctorAddress
        : (l10n.translate('clinicAddress') ?? 'Clinic Address');
    final videoOption = l10n.videoCall;
    final isVideo = entry.location.toLowerCase().contains('video');
    final currentValue = isVideo
        ? videoOption
        : (entry.location.isEmpty ? clinicOption : entry.location);
    final placeLabel = currentValue.trim().isEmpty ? clinicOption : currentValue;

    return Card(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      elevation: 0,
      color: Colors.white,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              l10n.place,
              style: const TextStyle(fontWeight: FontWeight.w600),
            ),
            const SizedBox(height: 8),
            Container(
              width: double.infinity,
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
              decoration: BoxDecoration(
                border: Border.all(color: Colors.grey.shade400),
                borderRadius: BorderRadius.circular(8),
                color: Colors.grey.shade50,
              ),
              child: Row(
                children: [
                  Icon(
                    isVideo ? Icons.videocam_outlined : Icons.location_on_outlined,
                    size: 18,
                    color: Colors.grey.shade700,
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      placeLabel,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(color: Colors.grey.shade800),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 4),
            Text(
              l10n.translate('appointmentPlaceLockedHint') ??
                  'Booked appointment location is informational and cannot be changed here.',
              style: TextStyle(
                fontSize: 12,
                color: Colors.grey.shade600,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// ---- Right: Slot details panel (booking) ----
/// Now stateful so we can hold selected patient & selected place,
/// and perform booking ONLY when "Save" is pressed.
class CalendarSlotDetailsPanel extends ConsumerStatefulWidget {
  const CalendarSlotDetailsPanel({
    Key? key,
    required this.entry,
    required this.day,
    this.scheduleTimeZone,
    this.primaryClinicVenueLabel,
    this.initialBookingPlace,
    this.clinicDoctorProfileId,
    this.clinicDoctorDisplayName,
    required this.onSavedSuccessfully,
    required this.onClose,
  }) : super(key: key);

  final CalendarEntry entry;
  final DateTime day;

  /// When scheduling for another doctor, use this IANA TZ for semantics instead of logged-in profile.
  final String? scheduleTimeZone;

  /// Fallback label for clinic/in-person bookings (typically selected clinic street).
  final String? primaryClinicVenueLabel;

  /// Set when this panel is shown inside [ClinicDoctorScheduleRoute].
  final int? clinicDoctorProfileId;
  final String? clinicDoctorDisplayName;

  /// Pre-selects clinic vs video when booking a free slot (e.g. quick action).
  final String? initialBookingPlace;

  /// Called by panel when save succeeds (to reload list / close panel).
  final Future<void> Function() onSavedSuccessfully;

  /// Called when user discards / closes the panel without saving.
  final VoidCallback onClose;

  @override
  ConsumerState<CalendarSlotDetailsPanel> createState() => CalendarSlotDetailsPanelState();
}

class CalendarSlotDetailsPanelState extends ConsumerState<CalendarSlotDetailsPanel> {
  // Selection state for assignment
  int? _selectedPatientId;
  String? _selectedPatientName;

  // Place selection: 'Clinic Address' or 'Video Consultation'
  String? _selectedPlace;
  late final TextEditingController _reasonCtrl;

  bool _saving = false;
  bool _hasUnsavedChanges = false;
  bool _showAiSummary = false;
  bool _sendingPaymentReminder = false;
  String _initialReason = '';
  String _initialPlace = '';

  TimeOfDay? _bookingEndExclusive;
  /// When booking a free slot, user may pick a different start row than [widget.entry].
  CalendarEntry? _bookingFreeSlot;
  TimeOfDay? _adjustedAppointmentEndExclusive;
  int _initialFreeSlotEndRepr = -1;
  int _initialFreeSlotStartRepr = -1;
  int _initialAppointmentEndRepr = -1;
  bool _seededFromDependencies = false;

  String _two(int n) => n.toString().padLeft(2, '0');
  String _fmtDate(BuildContext context, DateTime d) =>
      '${_two(d.day)} ${AppLocalizations.of(context)!.monthName(d.month)} ${d.year}';
  String _fmtTime(TimeOfDay t) => '${_two(t.hour)}:${_two(t.minute)}';

  int _todMinutes(TimeOfDay t) => t.hour * 60 + t.minute;

  String _defaultClinicPlace(AppLocalizations l10n) =>
      l10n.translate('clinicAddress') ?? 'Clinic Address';

  /// Active free-slot row for booking (start anchor for API + end chaining).
  CalendarEntry get _effectiveFreeSlot => _bookingFreeSlot ?? widget.entry;

  TimeOfDay get _effectiveBookingStart => _effectiveFreeSlot.start;

  /// End time for bookings from the tapped slot row until multi-slot selection confirms.
  TimeOfDay get _effectiveBookingEnd =>
      _bookingEndExclusive ?? _effectiveFreeSlot.end;

  /// Preview header end for appointments when adjusting length.
  TimeOfDay get _detailHeaderEnd {
    if (widget.entry.type == EntryType.appointment) {
      return _adjustedAppointmentEndExclusive ?? widget.entry.end;
    }
    return _effectiveBookingEnd;
  }

  /// TZ used for interpreting schedule times, bookings, cancellations, etc.
  String _calendarTz() {
    final o = widget.scheduleTimeZone?.trim();
    if (o != null && o.isNotEmpty) return o;
    final tz =
        ref.read(profileAllProvider).valueOrNull?.profile['timeZone']
            as String?;
    if (tz != null && tz.trim().isNotEmpty) return tz.trim();
    return 'UTC';
  }

  bool get _isAppointment => widget.entry.type == EntryType.appointment;
  bool get _isBlocked => widget.entry.type == EntryType.blocked;
  String get _statusUpper => (widget.entry.status ?? '').trim().toUpperCase();
  bool get _isCompletedStatus => _statusUpper == 'COMPLETED';
  bool get _isCancelledStatus => _statusUpper == 'CANCELLED';
  bool get _isInProgressStatus => _statusUpper == 'IN_PROGRESS';
  bool get _isVideoAppointment =>
      _isAppointment &&
      (widget.entry.isVideo ||
          widget.entry.location.toLowerCase().contains('video'));

  bool get _paymentPending =>
      (widget.entry.paymentStatus ?? '').trim().toUpperCase() == 'PENDING';

  /// Video consult with unpaid balance â€” show "remind to pay" for the doctor.
  bool get _canAdjustAppointmentDuration =>
      _isAppointment &&
      !_isPastAppointment &&
      !_isCancelledStatus &&
      !_isCompletedStatus &&
      widget.entry.appointmentId != null &&
      (widget.entry.startAtUtc ?? '').trim().isNotEmpty &&
      (widget.entry.endAtUtc ?? '').trim().isNotEmpty;

  bool get _appointmentDurationDirty =>
      _canAdjustAppointmentDuration &&
      _todMinutes(_adjustedAppointmentEndExclusive ?? widget.entry.end) !=
          _initialAppointmentEndRepr;

  /// Video consult with unpaid balance â€” show "remind to pay" for the doctor.
  bool get _shouldShowEncouragePayment =>
      _isVideoAppointment &&
      !_isPastAppointment &&
      !_isCancelledStatus &&
      !_isCompletedStatus &&
      _paymentPending &&
      widget.entry.appointmentId != null;

  Future<void> _openPatientProfileFromDetails() async {
    final patientId = widget.entry.patientId?.toString();
    if (patientId == null || patientId.trim().isEmpty) return;

    final openedFromRootOverlay = ShellScope.of(context) == null;
    Object pushArgs = patientId;

    if (openedFromRootOverlay) {
      final doctorId = widget.clinicDoctorProfileId;
      final doctorName = widget.clinicDoctorDisplayName?.trim();
      final timeZone = widget.scheduleTimeZone?.trim();
      if (doctorId != null &&
          doctorName != null &&
          doctorName.isNotEmpty &&
          timeZone != null &&
          timeZone.isNotEmpty) {
        pushArgs = <String, dynamic>{
          'patientId': patientId,
          'clinicScheduleReturn': ClinicScheduleReturnInfo(
            doctorProfileId: doctorId,
            doctorDisplayName: doctorName,
            clinicScheduleTimeZone: timeZone,
            clinicStreetAddress: widget.primaryClinicVenueLabel,
          ).toMap(),
        };
      }
      Navigator.of(context, rootNavigator: true).pop();
    }

    ref.read(shellProvider.notifier).setTab(DoctorShellTab.patients);
    ShellScope.pushIntoShell(pushArgs);
  }

  Future<void> _removeScheduleBlock() async {
    final blockId = widget.entry.blockId;
    if (blockId == null) return;

    final l10n = AppLocalizations.of(context)!;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(l10n.translate('unblockTime') ?? 'Remove block'),
        content: Text(
          l10n.translate('unblockConfirm') ??
              'Remove this block? Free slots will become available again.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: Text(l10n.cancel),
          ),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: Text(l10n.translate('unblockTime') ?? 'Remove block'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;

    final doctorTimeZone = _calendarTz();
    try {
      setState(() => _saving = true);
      await ref.read(calendarProvider.notifier).deleteScheduleBlock(
            blockId: blockId,
            day: widget.day,
            doctorTimeZone: doctorTimeZone,
            actingAsDoctorProfileId: widget.clinicDoctorProfileId,
          );
      await widget.onSavedSuccessfully();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              l10n.translate('unblockSuccess') ?? 'Block removed',
            ),
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('$e')),
        );
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  DateTime _appointmentStartDateTimeInDoctorZone() {
    final doctorTimeZone = _calendarTz();
    final startUtcRaw = widget.entry.startAtUtc;
    if (startUtcRaw != null && startUtcRaw.trim().isNotEmpty) {
      final parsed = DateTime.parse(startUtcRaw);
      final asUtc = parsed.isUtc
          ? parsed
          : DateTime.utc(
              parsed.year,
              parsed.month,
              parsed.day,
              parsed.hour,
              parsed.minute,
              parsed.second,
              parsed.millisecond,
              parsed.microsecond,
            );
      return utcToTimezone(asUtc, doctorTimeZone);
    }
    return timeOfDayToDateTimeInZone(
      widget.entry.start,
      widget.day,
      doctorTimeZone,
    );
  }

  DateTime _appointmentEndDateTimeInDoctorZone() {
    final doctorTimeZone = _calendarTz();
    final endUtcRaw = widget.entry.endAtUtc;
    if (endUtcRaw != null && endUtcRaw.trim().isNotEmpty) {
      final parsed = DateTime.parse(endUtcRaw);
      final asUtc = parsed.isUtc
          ? parsed
          : DateTime.utc(
              parsed.year,
              parsed.month,
              parsed.day,
              parsed.hour,
              parsed.minute,
              parsed.second,
              parsed.millisecond,
              parsed.microsecond,
            );
      return utcToTimezone(asUtc, doctorTimeZone);
    }
    return timeOfDayToDateTimeInZone(
      widget.entry.end,
      widget.day,
      doctorTimeZone,
    );
  }

  bool get _canStartVideoByTimeWindow {
    if (!_isVideoAppointment) return true;
    if (_isInProgressStatus || _isCompletedStatus) return true;

    final doctorTimeZone = _calendarTz();
    final nowInDoctorZone = getNowInTimezone(doctorTimeZone);

    final appointmentStartInDoctorZone = _appointmentStartDateTimeInDoctorZone();
    final joinAllowedFrom = appointmentStartInDoctorZone.subtract(
      const Duration(minutes: 5),
    );
    if (nowInDoctorZone.isBefore(joinAllowedFrom)) return false;

    final appointmentEndInDoctorZone = _appointmentEndDateTimeInDoctorZone();
    final coldJoinCutoff = appointmentEndInDoctorZone.add(
      const Duration(hours: 1),
    );
    if (!nowInDoctorZone.isBefore(coldJoinCutoff)) {
      return false;
    }

    return true;
  }

  /// For inline hint when video start is disabled: too early vs. too late (1h after end).
  bool get _isVideoPastColdJoinGraceHint {
    if (!_isVideoAppointment || _isInProgressStatus || _isCompletedStatus) {
      return false;
    }
    final doctorTimeZone = _calendarTz();
    final nowInDoctorZone = getNowInTimezone(doctorTimeZone);
    final coldJoinCutoff = _appointmentEndDateTimeInDoctorZone()
        .add(const Duration(hours: 1));
    return !nowInDoctorZone.isBefore(coldJoinCutoff);
  }

  void _seedStateFromEntry() {
    final l10n = AppLocalizations.of(context)!;
    final clinicPlace = _defaultClinicPlace(l10n);
    if (_isAppointment) {
      _selectedPlace = widget.entry.location.toLowerCase().contains('video')
          ? l10n.videoCall
          : clinicPlace;
    } else {
      _selectedPlace = widget.initialBookingPlace ?? clinicPlace;
    }
    _initialPlace = _selectedPlace ?? clinicPlace;
    final seedReason = widget.entry.reason.trim().isEmpty
        ? 'Check Up'
        : widget.entry.reason.trim();
    _reasonCtrl.text = seedReason;
    _initialReason = seedReason;
    _hasUnsavedChanges = false;
    _showAiSummary = false;
    if (!_isAppointment) {
      _bookingFreeSlot = null;
      _bookingEndExclusive = null;
      _initialFreeSlotEndRepr = _todMinutes(widget.entry.end);
      _initialFreeSlotStartRepr = _todMinutes(widget.entry.start);
    } else {
      _adjustedAppointmentEndExclusive = null;
      _initialAppointmentEndRepr = _todMinutes(widget.entry.end);
    }
  }

  @override
  void initState() {
    super.initState();
    _reasonCtrl = TextEditingController();
    _reasonCtrl.addListener(_syncDirtyState);
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (!_seededFromDependencies) {
      _seededFromDependencies = true;
      _seedStateFromEntry();
    }
  }

  @override
  void didUpdateWidget(covariant CalendarSlotDetailsPanel oldWidget) {
    super.didUpdateWidget(oldWidget);
    final changed =
        oldWidget.entry.type != widget.entry.type ||
        oldWidget.entry.appointmentId != widget.entry.appointmentId ||
        oldWidget.entry.startAtUtc != widget.entry.startAtUtc ||
        oldWidget.entry.endAtUtc != widget.entry.endAtUtc ||
        oldWidget.initialBookingPlace != widget.initialBookingPlace;
    if (changed) {
      _seedStateFromEntry();
    }
  }

  @override
  void dispose() {
    _reasonCtrl.removeListener(_syncDirtyState);
    _reasonCtrl.dispose();
    super.dispose();
  }

  void _syncDirtyState() {
    final bookingFreeStartDirty =
        widget.entry.type == EntryType.freeSlot &&
        (_initialFreeSlotStartRepr < 0 ||
            _todMinutes(_effectiveBookingStart) != _initialFreeSlotStartRepr);
    final bookingFreeEndDirty =
        widget.entry.type == EntryType.freeSlot &&
        (_initialFreeSlotEndRepr < 0 ||
            _todMinutes(_effectiveBookingEnd) != _initialFreeSlotEndRepr);
    final isDirty =
        (_reasonCtrl.text.trim() != _initialReason) ||
        ((_selectedPlace ?? '').trim() != _initialPlace) ||
        bookingFreeStartDirty ||
        bookingFreeEndDirty ||
        (_canAdjustAppointmentDuration && _appointmentDurationDirty);
    if (isDirty != _hasUnsavedChanges && mounted) {
      setState(() => _hasUnsavedChanges = isDirty);
    }
  }

  Future<bool> _confirmDiscardIfDirty() async {
    if (!_hasUnsavedChanges) return true;
    final l10n = AppLocalizations.of(context)!;
    final decision = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(l10n.discard),
        content: Text(
          l10n.translate('unsavedChangesMessage') ??
              'You have unsaved changes. Discard them?',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: Text(l10n.cancel),
          ),
          ShifaPrimaryButton(
            label: l10n.discard,
            onPressed: () => Navigator.pop(ctx, true),
            variant: ButtonVariant.destructive,
          ),
        ],
      ),
    );
    return decision == true;
  }

  Future<int?> _pickDuration(BuildContext context) async {
    final options = <int>[10, 15, 20, 30, 45, 60];
    return showModalBottomSheet<int>(
      context: context,
      builder: (ctx) => SafeArea(
        child: ListView.separated(
          shrinkWrap: true,
          itemCount: options.length,
          separatorBuilder: (_, __) => const Divider(height: 1),
          itemBuilder: (_, i) => ListTile(
            title: Text(
              '${options[i]} ${AppLocalizations.of(context)!.translate('minutes') ?? 'minutes'}',
            ),
            onTap: () => Navigator.pop(ctx, options[i]),
          ),
        ),
      ),
    );
  }

  Future<String?> _editText(
    BuildContext context,
    String title,
    String initial,
  ) async {
    final controller = TextEditingController(text: initial);
    return showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(title),
        content: TextField(controller: controller, autofocus: true),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: Text(AppLocalizations.of(ctx)!.cancel),
          ),
          TextButton(
            onPressed: () => Navigator.pop(ctx, controller.text),
            child: Text(AppLocalizations.of(ctx)!.ok),
          ),
        ],
      ),
    );
  }

  String _durationLabel(TimeOfDay s, TimeOfDay e) {
    final sm = s.hour * 60 + s.minute;
    final em = e.hour * 60 + e.minute;
    final d = (em - sm).clamp(0, 24 * 60);
    final h = d ~/ 60;
    final m = d % 60;
    return h > 0 ? '${h}h ${m}m' : '${m}m';
  }

  int _durationMinutes(TimeOfDay s, TimeOfDay e) =>
      (e.hour * 60 + e.minute) - (s.hour * 60 + s.minute);

  /// True if the selected entry is an appointment that has already ended (in the past).
  /// CRITICAL: Uses doctor's timezone for consistent "past" determination
  bool get _isPastAppointment {
    final e = widget.entry;
    if (e.type != EntryType.appointment || e.endAtUtc == null) return false;
    final endUtc = DateTime.parse(e.endAtUtc!);
    final endInstant = endUtc.isUtc
        ? endUtc
        : DateTime.utc(
            endUtc.year,
            endUtc.month,
            endUtc.day,
            endUtc.hour,
            endUtc.minute,
            endUtc.second,
            endUtc.millisecond,
          );
    // Use doctor's timezone for "now" to match all other appointment timing
    final doctorTimeZone = _calendarTz();
    final nowInDoctorZone = getNowInTimezone(doctorTimeZone);
    return endInstant.isBefore(nowInDoctorZone.toUtc());
  }

  /// True when the appointment's calendar day is today in the doctor's timezone.
  bool get _isAppointmentOnToday {
    final doctorTimeZone = _calendarTz();
    final todayInDoctorZone = getTodayInTimezone(doctorTimeZone);
    final slotDay = DateTime(widget.day.year, widget.day.month, widget.day.day);
    return slotDay.year == todayInDoctorZone.year &&
        slotDay.month == todayInDoctorZone.month &&
        slotDay.day == todayInDoctorZone.day;
  }

  /// In-clinic visits from earlier today may still be started (matches home screen).
  bool get _canStartInClinicDespitePastDue =>
      _isAppointment &&
      !_isVideoAppointment &&
      _isPastAppointment &&
      _isAppointmentOnToday &&
      !_isInProgressStatus &&
      !_isCompletedStatus &&
      !_isCancelledStatus;

  /// Past-due appointments that cannot be started from the calendar panel.
  bool get _isPastDueStartBlocked =>
      _isPastAppointment &&
      !_isInProgressStatus &&
      !_isCompletedStatus &&
      !_canStartInClinicDespitePastDue;

  /// True if the selected entry is a free slot on a past *day* (cannot assign patient).
  /// Slots on *today* are allowed (whole day), e.g. at 2 PM you can still assign morning slots on the same day.
  /// CRITICAL: Uses doctor's timezone to determine "today" vs "past day"
  bool get _isPastFreeSlot {
    if (widget.entry.type != EntryType.freeSlot) return false;
    final doctorTimeZone = _calendarTz();
    final todayInDoctorZone = getTodayInTimezone(doctorTimeZone);
    final slotDay = DateTime(widget.day.year, widget.day.month, widget.day.day);
    return slotDay.isBefore(todayInDoctorZone);
  }

  Future<void> _showChangeSlotDialog(BuildContext context) async {
    DateTime? selectedDate = widget.day;
    CalendarEntry? selectedSlot;

    final result = await showDialog<bool>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (context, setDialogState) {
          final l10n = AppLocalizations.of(ctx)!;
          return AlertDialog(
            title: Text(l10n.changeSlot),
            content: SizedBox(
              width: double.maxFinite,
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  // Date picker
                  ListTile(
                    title: Text(l10n.selectDate),
                    subtitle: Text(
                      selectedDate != null
                          ? '${selectedDate!.day} ${l10n.monthName(selectedDate!.month)} ${selectedDate!.year}'
                          : l10n.translate('notSelected') ?? 'Not selected',
                    ),
                    trailing: const Icon(Icons.calendar_today),
                    onTap: () async {
                      // Use doctor's timezone to prevent booking past days in doctor's calendar
                      final doctorTz = _calendarTz();
                      final todayInDoctorZone = getTodayInTimezone(doctorTz);
                      final picked = await showDatePicker(
                        context: context,
                        locale: localeForMaterialIntl(
                          Localizations.localeOf(context),
                        ),
                        initialDate: selectedDate ?? todayInDoctorZone,
                        firstDate: todayInDoctorZone,
                        lastDate: DateTime(2030),
                      );
                      if (picked != null) {
                        setDialogState(() {
                          selectedDate = DateTime(
                            picked.year,
                            picked.month,
                            picked.day,
                          );
                          selectedSlot = null; // Reset slot when date changes
                        });
                      }
                    },
                  ),
                  const Divider(),
                  // Available slots list
                  if (selectedDate != null) ...[
                    const SizedBox(height: 8),
                    Text(
                      l10n.availableSlots,
                      style: const TextStyle(fontWeight: FontWeight.bold),
                    ),
                    const SizedBox(height: 8),
                    FutureBuilder<List<CalendarEntry>>(
                      future: _loadAvailableSlots(selectedDate!),
                      builder: (context, snapshot) {
                        if (snapshot.connectionState ==
                            ConnectionState.waiting) {
                          return const Padding(
                            padding: EdgeInsets.all(16.0),
                            child: Center(child: CircularProgressIndicator()),
                          );
                        }
                        if (snapshot.hasError) {
                          return Padding(
                            padding: const EdgeInsets.all(16.0),
                            child: Text(
                              '${l10n.translate('errorLoadingSlots') ?? 'Error loading slots'}: ${snapshot.error}',
                            ),
                          );
                        }
                        final slots = snapshot.data ?? [];
                        if (slots.isEmpty) {
                          return Padding(
                            padding: const EdgeInsets.all(16.0),
                            child: Text(l10n.noSlotsAvailable),
                          );
                        }
                        return ConstrainedBox(
                          constraints: const BoxConstraints(maxHeight: 300),
                          child: ListView.builder(
                            shrinkWrap: true,
                            itemCount: slots.length,
                            itemBuilder: (context, index) {
                              final slot = slots[index];
                              final isSelected =
                                  selectedSlot != null &&
                                  selectedSlot!.start == slot.start &&
                                  selectedSlot!.end == slot.end;
                              return ListTile(
                                title: Text(
                                  '${_two(slot.start.hour)}:${_two(slot.start.minute)} - ${_two(slot.end.hour)}:${_two(slot.end.minute)}',
                                ),
                                subtitle: Text(
                                  _durationLabel(slot.start, slot.end),
                                ),
                                selected: isSelected,
                                onTap: () {
                                  setDialogState(() {
                                    selectedSlot = slot;
                                  });
                                },
                                trailing: isSelected
                                    ? const Icon(
                                        Icons.check,
                                        color: Colors.blue,
                                      )
                                    : null,
                              );
                            },
                          ),
                        );
                      },
                    ),
                  ] else
                    Padding(
                      padding: const EdgeInsets.all(16.0),
                      child: Text(
                        l10n.translate('pleaseSelectDateFirst') ??
                            'Please select a date first',
                      ),
                    ),
                ],
              ),
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(ctx, false),
                child: Text(l10n.cancel),
              ),
              ShifaPrimaryButton(
                label: l10n.changeSlot,
                onPressed: (selectedDate != null && selectedSlot != null)
                    ? () => Navigator.pop(ctx, true)
                    : null,
              ),
            ],
          );
        },
      ),
    );

    if (result == true &&
        selectedDate != null &&
        selectedSlot != null &&
        widget.entry.appointmentId != null) {
      final doctorTimeZone = _calendarTz();
      try {
        setState(() => _saving = true);
        final slotMinutes = _durationMinutes(
          selectedSlot!.start,
          selectedSlot!.end,
        );
        await ref
            .read(calendarProvider.notifier)
            .changeAppointmentSlot(
              appointmentId: widget.entry.appointmentId!,
              day: widget.day,
              newDay: selectedDate!,
              newStartTime: selectedSlot!.start,
              slotMinutes: slotMinutes,
              doctorTimeZone: doctorTimeZone,
            );
        // Refresh Home's "Today" list and this day in calendar
        await invalidateAppointmentRelatedProviders(ref);
        await refreshCalendarDay(ref, widget.day, doctorTimeZone);
        await widget.onSavedSuccessfully();
        if (mounted) {
          final l10n = AppLocalizations.of(context)!;
          ScaffoldMessenger.of(
            context,
          ).showSnackBar(SnackBar(content: Text(l10n.slotChanged)));
        }
      } catch (e) {
        if (mounted) {
          final l10n = AppLocalizations.of(context)!;
          final doctorTimeZone = _calendarTz();
          await refreshCalendarDay(ref, widget.day, doctorTimeZone);
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(
                '${l10n.translate('failedToChangeSlot') ?? 'Failed to change slot'}: $e',
              ),
            ),
          );
        }
      } finally {
        if (mounted) setState(() => _saving = false);
      }
    }
  }

  List<TimeOfDay> _buildAppointmentAdjustEndChoices(
    List<CalendarEntry> dayEntries,
  ) {
    final tz = _calendarTz();
    final startIso = widget.entry.startAtUtc ?? '';
    final endIso = widget.entry.endAtUtc ?? '';
    if (startIso.isEmpty || endIso.isEmpty) return [widget.entry.end];

    final grain = scheduleGrainMinutesGuess(
      entries: dayEntries,
      venueId: widget.entry.locationId,
      defaultGrain: (_durationMinutes(widget.entry.start, widget.entry.end))
              .clamp(5, 240)
              .toInt(),
    );

    final shorten = shorteningEndWallsGrainUtc(
      appointmentStartUtc: startIso,
      appointmentEndUtcExclusive: endIso,
      grainMinutes: grain,
      doctorTimeZone: tz,
    );

    final extend = extendEndWallsChainFromUtcBoundary(
      dayEntries: dayEntries,
      cursorUtcStartIso: endIso,
      anchorVenueId: widget.entry.locationId,
      doctorTimeZone: tz,
    );

    final byKey = <int, TimeOfDay>{};
    void addTod(TimeOfDay t) {
      byKey.putIfAbsent(_todMinutes(t), () => t);
    }

    addTod(widget.entry.end);
    for (final t in shorten) {
      addTod(t);
    }
    for (final t in extend) {
      addTod(t);
    }

    final out = byKey.values.toList()
      ..sort((a, b) => _todMinutes(a).compareTo(_todMinutes(b)));
    return out;
  }

  Future<void> _persistAppointmentDuration() async {
    if (!_canAdjustAppointmentDuration || _saving) return;
    final chosen = _adjustedAppointmentEndExclusive;
    if (chosen == null) return;
    if (_todMinutes(chosen) == _initialAppointmentEndRepr) return;

    final tz = _calendarTz();
    final slotMinutes = appointmentSlotMinutesUtcStartWallEnd(
      appointmentStartUtcIso: widget.entry.startAtUtc!,
      endExclusiveWall: chosen,
      calendarDay: widget.day,
      doctorTimeZone: tz,
    );
    if (slotMinutes < 5) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            AppLocalizations.of(context)!.translate('invalidDuration') ??
                'Invalid duration.',
          ),
        ),
      );
      return;
    }

    try {
      setState(() => _saving = true);
      await ref
          .read(calendarProvider.notifier)
          .changeAppointmentSlot(
            appointmentId: widget.entry.appointmentId!,
            day: widget.day,
            newDay: widget.day,
            newStartTime: widget.entry.start,
            slotMinutes: slotMinutes,
            doctorTimeZone: tz,
          );
      await invalidateAppointmentRelatedProviders(ref);
      await refreshCalendarDay(ref, widget.day, tz);
      await widget.onSavedSuccessfully();

      _initialAppointmentEndRepr = _todMinutes(chosen);
      _adjustedAppointmentEndExclusive = null;

      if (mounted) {
        final l10n = AppLocalizations.of(context)!;
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(l10n.slotChanged)),
        );
      }
      _syncDirtyState();
    } catch (e) {
      final l10n = AppLocalizations.of(context)!;
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              '${l10n.translate('failedToChangeSlot') ?? 'Failed to change slot'}: $e',
            ),
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<List<CalendarEntry>> _loadAvailableSlots(DateTime day) async {
    final tz = _calendarTz();
    try {
      final doctorId = widget.clinicDoctorProfileId;
      final List<CalendarEntry> entries;
      if (doctorId != null) {
        entries = await ref
            .read(calendarProvider.notifier)
            .previewDayForDoctorProfile(
              day: day,
              doctorTimeZone: tz,
              doctorProfileId: doctorId,
            );
      } else {
        await ref
            .read(calendarProvider.notifier)
            .loadDay(day: day, doctorTimeZone: tz);
        entries =
            ref.read(calendarProvider)[DateTime(day.year, day.month, day.day)] ??
                [];
      }
      return entries.where((e) => e.type == EntryType.freeSlot).toList();
    } catch (e) {
      return [];
    }
  }

  Future<void> _assignOnSave() async {
    if (_saving) return;

    // Validate selections
    final l10n = AppLocalizations.of(context)!;
    if (_selectedPatientId == null) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(l10n.pleaseSelectPatient)));
      return;
    }
    if (_selectedPlace == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            l10n.translate('pleaseChoosePlace') ?? 'Please choose a place',
          ),
        ),
      );
      return;
    }

    // Check for chronic disease warning before booking
    try {
      final patientAsync = ref.read(
        patientByIdProvider(_selectedPatientId!.toString()),
      );
      final patient = await patientAsync.when(
        data: (p) => Future.value(p),
        loading: () => Future.value(null),
        error: (_, __) => Future.value(null),
      );

      if (patient != null &&
          patient.general.chronicDisease != null &&
          patient.general.chronicDisease!.isNotEmpty &&
          patient.general.chronicDisease != 'None' &&
          mounted) {
        await showChronicDiseaseWarning(
          context,
          patient.name,
          patient.general.chronicDisease!,
        );
      }
    } catch (e) {
      // Silently fail - don't block booking
      debugPrint('Error checking chronic disease: $e');
    }

    // Derive location/isVideo from place
    final selectedPlaceNorm = (_selectedPlace ?? '').toLowerCase();
    final isVideo =
        selectedPlaceNorm.contains('video') || _selectedPlace == l10n.videoCall;
    final location = isVideo ? 'Video Consultation' : 'Clinic Address';
    final reason = _reasonCtrl.text.trim().isEmpty
        ? 'Check Up'
        : _reasonCtrl.text.trim();

    try {
      setState(() => _saving = true);

      final doctorTimeZone = _calendarTz();
      await ref
          .read(calendarProvider.notifier)
          .bookFreeSlotRemote(
            day: widget.day,
            slot: _effectiveFreeSlot,
            patientId: _selectedPatientId!,
            doctorTimeZone: doctorTimeZone,
            location: location,
            reason: reason,
            isVideo: isVideo,
            endExclusive: _effectiveBookingEnd,
            actingAsDoctorProfileId: widget.clinicDoctorProfileId,
          );

      // Refresh Home's "Today" list and this day in calendar
      await invalidateAppointmentRelatedProviders(ref);
      await refreshCalendarDay(ref, widget.day, doctorTimeZone);

      // Inform parent to reload list and close panel
      await widget.onSavedSuccessfully();

      // Confirmation
      final l10n = AppLocalizations.of(context)!;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            '${l10n.translate('patientAssigned') ?? 'Patient assigned'}: ${_selectedPatientName ?? l10n.patient}',
          ),
        ),
      );
      _initialReason = _reasonCtrl.text.trim();
      _initialPlace = _selectedPlace ?? '';
      _syncDirtyState();
    } catch (e) {
      final doctorTimeZone = _calendarTz();
      await refreshCalendarDay(ref, widget.day, doctorTimeZone);
      final l10n = AppLocalizations.of(context)!;
      debugPrint('bookFreeSlot error: $e');
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            l10n.translate('bookingRangeUnavailable') ??
                'Selected time range is no longer fully available â€” calendar refreshed.',
          ),
        ),
      );
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Appointment _toAppointment() {
    return Appointment(
      id: widget.entry.appointmentId.toString(),
      patientName: widget.entry.patientName ?? 'Patient',
      patientId: widget.entry.patientId?.toString(),
      location: widget.entry.location,
      start: widget.entry.start,
      end: widget.entry.end,
      status:
          AppointmentStatus.fromString(widget.entry.status) ??
          AppointmentStatus.confirmed,
      photoUrl: widget.entry.photoUrl,
      reason: widget.entry.reason.isNotEmpty ? widget.entry.reason : null,
    );
  }

  Future<void> _openAppointmentWorkspace() async {
    if (!_isAppointment || _isCancelledStatus) return;
    final appt = _toAppointment();
    if (!mounted) return;
    await ShellScope.pushNamed(
      context,
      appt.isVideo ? AppRoutes.videoCall : AppRoutes.inPerson,
      arguments: appt,
    );
  }

  PatientDocument? _pickSavedAppointmentSummaryDoc(
    List<PatientDocument> docs,
    AppLocalizations l10n,
  ) {
    if (docs.isEmpty) return null;

    final titlePrefixes = <String>{
      l10n.appointmentDocumentation.toLowerCase(),
      'appointment documentation',
      'uchrashuv hujjatlari',
      'Ð´Ð¾ÐºÑƒÐ¼ÐµÐ½Ñ‚Ð°Ñ†Ð¸Ñ Ð¿Ñ€Ð¸ÐµÐ¼Ð°',
    };

    final candidates = docs.where((d) {
      final title = d.title.trim().toLowerCase();
      if (title.isEmpty) return false;
      return titlePrefixes.any(title.contains);
    }).toList();

    if (candidates.isEmpty) return null;

    final target = DateTime(
      widget.day.year,
      widget.day.month,
      widget.day.day,
      widget.entry.start.hour,
      widget.entry.start.minute,
    );

    bool sameDay(DateTime a, DateTime b) =>
        a.year == b.year && a.month == b.month && a.day == b.day;

    final sameDayCandidates =
        candidates.where((d) => sameDay(d.date, target)).toList();
    final pool = sameDayCandidates.isNotEmpty ? sameDayCandidates : candidates;

    pool.sort((a, b) {
      final aDelta = (a.date.difference(target).inMinutes).abs();
      final bDelta = (b.date.difference(target).inMinutes).abs();
      if (aDelta != bDelta) return aDelta.compareTo(bDelta);
      return b.date.compareTo(a.date);
    });

    return pool.first;
  }

  Future<void> _openSavedSummaryPdf() async {
    final l10n = AppLocalizations.of(context)!;
    final patientId = widget.entry.patientId?.toString();
    if (patientId == null || patientId.trim().isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            l10n.translate('patientIdNotAvailable') ?? 'Patient ID not available.',
          ),
        ),
      );
      return;
    }

    try {
      ref.invalidate(patientDocumentsProvider(PatientDocumentsKey(patientId: patientId)));
      final docs = await ref.read(patientDocumentsProvider(PatientDocumentsKey(patientId: patientId)).future);
      final doc = _pickSavedAppointmentSummaryDoc(docs, l10n);

      if (doc == null) {
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              l10n.translate('noSummaryYet') ??
                  'No saved appointment documentation found.',
            ),
          ),
        );
        return;
      }

      final openedFromRootOverlay = ShellScope.of(context) == null;
      final pushArgs = <String, dynamic>{
        'patientId': patientId,
        'documentId': doc.id,
        'documentTitle': doc.title,
        'openDocumentViewer': true,
      };
      if (openedFromRootOverlay) {
        final doctorId = widget.clinicDoctorProfileId;
        final doctorName = widget.clinicDoctorDisplayName?.trim();
        final timeZone = widget.scheduleTimeZone?.trim();
        if (doctorId != null &&
            doctorName != null &&
            doctorName.isNotEmpty &&
            timeZone != null &&
            timeZone.isNotEmpty) {
          pushArgs['clinicScheduleReturn'] = ClinicScheduleReturnInfo(
            doctorProfileId: doctorId,
            doctorDisplayName: doctorName,
            clinicScheduleTimeZone: timeZone,
            clinicStreetAddress: widget.primaryClinicVenueLabel,
          ).toMap();
        }
        Navigator.of(context, rootNavigator: true).pop();
      }

      ref.read(shellProvider.notifier).setTab(DoctorShellTab.patients);
      ShellScope.pushIntoShell(pushArgs);
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            '${l10n.error}: $e',
          ),
        ),
      );
    }
  }

  String _primaryCtaLabel(AppLocalizations l10n) {
    String t(String key, String fallback) {
      final v = l10n.translate(key);
      if (v == null || v.trim().isEmpty || v.trim() == key) return fallback;
      return v;
    }

    if (_isCompletedStatus) {
      return t('openSummary', 'Open Summary');
    }
    if (_isInProgressStatus) {
      return t('continueAppointment', 'Continue Appointment');
    }
    if (_canStartInClinicDespitePastDue) {
      return l10n.startAppointment;
    }
    if (_isPastDueStartBlocked) {
      return t('appointmentEnded', 'Appointment Ended');
    }
    return l10n.startAppointment;
  }

  Widget _buildStatusChip(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final lang = Localizations.localeOf(context).languageCode.toLowerCase();
    String t(String key, String fallback) {
      final v = l10n.translate(key);
      if (v == null || v.trim().isEmpty || v.trim() == key) return fallback;
      return v;
    }

    IconData icon = Icons.info_outline;
    Color bg = Colors.grey.shade100;
    Color fg = Colors.grey.shade800;
    String label = _statusUpper.isEmpty
        ? t('unknown', 'UNKNOWN')
        : _statusUpper;

    if (_isCompletedStatus) {
      icon = Icons.check_circle_outline;
      bg = Colors.green.shade50;
      fg = Colors.green.shade800;
      label = l10n.complete;
    } else if (_isCancelledStatus) {
      icon = Icons.cancel_outlined;
      bg = Colors.red.shade50;
      fg = Colors.red.shade700;
      label = l10n.cancel;
    } else if (_isInProgressStatus) {
      icon = Icons.timelapse;
      bg = Colors.blue.shade50;
      fg = Colors.blue.shade700;
      label = t('inProgress', 'In Progress');
    } else if (_statusUpper == 'CONFIRMED') {
      icon = Icons.verified_outlined;
      bg = Colors.amber.shade50;
      fg = Colors.amber.shade800;
      final confirmedFallback = lang == 'uz'
          ? _latinUzbekForDisplay(context, 'Tasdiqlangan')
          : (lang == 'ru' ? 'ÐŸÐ¾Ð´Ñ‚Ð²ÐµÑ€Ð¶Ð´ÐµÐ½Ð¾' : 'Confirmed');
      label = t('confirmed', confirmedFallback);
    }

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: fg.withValues(alpha: 0.25)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 13, color: fg),
          const SizedBox(width: 6),
          Text(
            label,
            style: TextStyle(
              fontSize: 11,
              fontWeight: FontWeight.w700,
              color: fg,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildPaymentChip(BuildContext context) {
    final raw = (widget.entry.paymentStatus ?? '').trim().toUpperCase();
    final l10n = AppLocalizations.of(context)!;

    IconData icon = Icons.payments_outlined;
    Color bg = Colors.grey.shade100;
    Color fg = Colors.grey.shade800;
    String paymentText = raw.isEmpty
        ? l10n.translate('paymentUnknown')
        : l10n.translate('paymentStateRaw').replaceAll('{{state}}', raw);

    if (raw == 'PAID') {
      icon = Icons.check_circle_outline;
      bg = Colors.green.shade50;
      fg = Colors.green.shade800;
      paymentText = l10n.translate('paymentPaid');
    } else if (raw == 'PENDING') {
      icon = Icons.schedule;
      bg = Colors.orange.shade50;
      fg = Colors.orange.shade800;
      paymentText = l10n.translate('paymentPending');
    } else if (raw == 'FAILED') {
      icon = Icons.error_outline;
      bg = Colors.red.shade50;
      fg = Colors.red.shade700;
      paymentText = l10n.translate('paymentFailed');
    } else if (raw == 'NOT_REQUIRED') {
      icon = Icons.info_outline;
      bg = Colors.blueGrey.shade50;
      fg = Colors.blueGrey.shade700;
      paymentText = l10n.translate('paymentNotRequired');
    }
    final label = l10n.translate('paymentLabel').replaceAll('{{status}}', paymentText);

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: fg.withValues(alpha: 0.25)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 13, color: fg),
          const SizedBox(width: 6),
          Text(
            label,
            style: TextStyle(
              fontSize: 11,
              fontWeight: FontWeight.w700,
              color: fg,
            ),
          ),
        ],
      ),
    );
  }

  String _compactDateTime(String raw) {
    final dt = DateTime.tryParse(raw);
    if (dt == null) {
      final trimmed = raw.trim();
      if (trimmed.length > 16)
        return trimmed.substring(0, 16).replaceFirst('T', ' ');
      return trimmed.replaceFirst('T', ' ');
    }
    return '${dt.year}-${_two(dt.month)}-${_two(dt.day)} ${_two(dt.hour)}:${_two(dt.minute)}';
  }

  Future<void> _encouragePayment() async {
    final id = widget.entry.appointmentId;
    if (id == null) return;
    final l10n = AppLocalizations.of(context)!;
    setState(() => _sendingPaymentReminder = true);
    try {
      await ref
          .read(calendarProvider.notifier)
          .notifyPaymentReminder(appointmentId: id);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(l10n.translate('paymentReminderSent'))),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            l10n
                .translate('paymentReminderFailed')
                .replaceAll('{{error}}', '$e'),
          ),
        ),
      );
    } finally {
      if (mounted) setState(() => _sendingPaymentReminder = false);
    }
  }

  Future<void> _copyDocText(String text, AppLocalizations l10n) async {
    if (text.trim().isEmpty) return;
    await Clipboard.setData(ClipboardData(text: text));
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(l10n.translate('copied') ?? 'Copied'),
        duration: const Duration(milliseconds: 900),
      ),
    );
  }

  Widget _docCard({
    required String title,
    required String subtitle,
    required String body,
    required Color brand,
    required AppLocalizations l10n,
  }) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.grey.shade200),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  title,
                  style: TextStyle(
                    fontWeight: FontWeight.w700,
                    color: Colors.grey.shade900,
                  ),
                ),
              ),
              Text(
                subtitle,
                style: TextStyle(fontSize: 11, color: Colors.grey.shade600),
              ),
              const SizedBox(width: 6),
              IconButton(
                tooltip: l10n.translate('copy') ?? 'Copy',
                onPressed: () => _copyDocText(body, l10n),
                icon: Icon(Icons.copy, size: 16, color: brand),
                splashRadius: 18,
                constraints: const BoxConstraints(minWidth: 28, minHeight: 28),
                padding: EdgeInsets.zero,
              ),
            ],
          ),
          const SizedBox(height: 8),
          SelectionArea(
            child: SelectableText(
              body.trim().isEmpty ? 'â€”' : body,
              style: const TextStyle(fontSize: 13, height: 1.35),
            ),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final brand = Theme.of(context).colorScheme.primary;
    final subtleText = Colors.grey.shade600;
    final lang = Localizations.localeOf(context).languageCode.toLowerCase();
    final aiDocsFallback = lang == 'uz'
        ? _latinUzbekForDisplay(context, 'Uchrashuv hujjatlari')
        : (lang == 'ru' ? 'Ð”Ð¾ÐºÑƒÐ¼ÐµÐ½Ñ‚Ð°Ñ†Ð¸Ñ Ð¿Ñ€Ð¸ÐµÐ¼Ð°' : 'Appointment Documentation');
    final notesTabFallback = lang == 'uz'
        ? _latinUzbekForDisplay(context, 'Uchrashuv yozuvlari')
        : (lang == 'ru' ? 'Ð—Ð°Ð¿Ð¸ÑÐ¸ Ð¿Ñ€Ð¸ÐµÐ¼Ð°' : 'Appointment Notes');
    final summaryPdfTabFallback = lang == 'uz'
        ? _latinUzbekForDisplay(context, 'Xulosa PDF')
        : (lang == 'ru' ? 'PDF ÑÐ²Ð¾Ð´ÐºÐ°' : 'Summary PDF');
    String t(String key, String fallback) {
      final v = l10n.translate(key);
      if (v == null || v.trim().isEmpty || v.trim() == key) return fallback;
      return v;
    }

    final appointmentId = widget.entry.appointmentId?.toString();
    final consultationNotesAsync = appointmentId == null
        ? const AsyncValue.data(<ConsultationNoteDto>[])
        : ref.watch(consultationNotesForAppointmentProvider(appointmentId));
    final patientId = widget.entry.patientId?.toString();
    final patientDocsAsync = patientId == null
        ? const AsyncValue.data(<PatientDocument>[])
        : ref.watch(patientDocumentsProvider(PatientDocumentsKey(patientId: patientId)));

    // Build Dropdown items for place selection
    final placeOptions = <String>{
      _defaultClinicPlace(l10n),
      l10n.videoCall,
    }.where((v) => v.trim().isNotEmpty).toList(growable: false);
    final selectedPlaceValue = placeOptions.contains(_selectedPlace)
        ? _selectedPlace
        : _defaultClinicPlace(l10n);

    final calendarDayKey =
        DateTime(widget.day.year, widget.day.month, widget.day.day);
    final dayEntriesList =
        ref.watch(calendarProvider)[calendarDayKey] ?? <CalendarEntry>[];

    final freeSlotStartOptions = widget.entry.type == EntryType.freeSlot
        ? (dayEntriesList
              .where(
                (e) =>
                    e.type == EntryType.freeSlot &&
                    e.locationId == widget.entry.locationId,
              )
              .toList()
            ..sort(
              (a, b) => _todMinutes(a.start).compareTo(_todMinutes(b.start)),
            ))
        : <CalendarEntry>[];

    final freeSlotEndOptions = widget.entry.type == EntryType.freeSlot
        ? consecutiveEndTimesForFreeSlot(
            dayEntries: dayEntriesList,
            startSlot: _effectiveFreeSlot,
            doctorTimeZone: _calendarTz(),
          )
        : const <TimeOfDay>[];

    CalendarEntry? _matchingFreeSlotStart(List<CalendarEntry> opts) {
      if (opts.isEmpty) return null;
      final target = _todMinutes(_effectiveBookingStart);
      for (final o in opts) {
        if (_todMinutes(o.start) == target) return o;
      }
      return opts.first;
    }

    final freeStartDropdownValue = _matchingFreeSlotStart(freeSlotStartOptions);

    TimeOfDay _matchingTod(TimeOfDay needle, List<TimeOfDay> opts) {
      if (opts.isEmpty) return needle;
      final nk = _todMinutes(needle);
      for (final o in opts) {
        if (_todMinutes(o) == nk) return o;
      }
      return opts.first;
    }

    final freeEndDropdownValue = _matchingTod(
      _effectiveBookingEnd,
      freeSlotEndOptions,
    );

    final appointmentAdjustEnds = _canAdjustAppointmentDuration
        ? _buildAppointmentAdjustEndChoices(dayEntriesList)
        : const <TimeOfDay>[];

    final appointmentAdjustSelected =
        _canAdjustAppointmentDuration && appointmentAdjustEnds.isNotEmpty
        ? _matchingTod(
            _adjustedAppointmentEndExclusive ?? widget.entry.end,
            appointmentAdjustEnds,
          )
        : null;

    return Column(
      children: [
        Align(
          alignment: Alignment.centerLeft,
          child: Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      _isAppointment
                          ? t('appointmentDetails', 'Appointment Details')
                          : _isBlocked
                          ? (l10n.translate('blockTimeTitle') ?? 'Block time')
                          : (l10n.translate('slotDetails') ?? 'Slot details'),
                      style: TextStyle(
                        fontSize: 20,
                        color: Colors.grey.shade900,
                        fontWeight: FontWeight.w800,
                        letterSpacing: -0.2,
                      ),
                    ),
                    const SizedBox(height: 3),
                    Text(
                      '${_fmtDate(context, widget.day)} â€¢ ${_fmtTime(_effectiveBookingStart)} - ${_fmtTime(_detailHeaderEnd)}',
                      style: TextStyle(
                        color: subtleText,
                        fontSize: 12,
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      '${t('durationLabelShort', 'Duration')}: ${_durationLabel(_effectiveBookingStart, _detailHeaderEnd)}',
                      style: TextStyle(
                        color: subtleText,
                        fontSize: 11,
                      ),
                    ),
                  ],
                ),
              ),
              if (_isAppointment)
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    _buildStatusChip(context),
                    if (_isVideoAppointment) _buildPaymentChip(context),
                  ],
                ),
            ],
          ),
        ),
        const SizedBox(height: 14),

        // ---------------- Main content ----------------
        Expanded(
          child: ListView(
            padding: const EdgeInsets.only(bottom: 8),
            children: [
              if (_isBlocked) ...[
                Card(
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(14),
                  ),
                  elevation: 0,
                  color: Colors.red.shade50,
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Icon(Icons.block, color: Colors.red.shade700),
                            const SizedBox(width: 8),
                            Expanded(
                              child: Text(
                                widget.entry.blockReason?.trim().isNotEmpty == true
                                    ? widget.entry.blockReason!.trim()
                                    : (l10n.translate('blockedTime') ?? 'Blocked'),
                                style: TextStyle(
                                  fontWeight: FontWeight.w700,
                                  color: Colors.red.shade900,
                                ),
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 8),
                        Text(
                          l10n.translate('blockOverlapInfo') ??
                              'Patients cannot book new appointments during this blocked period.',
                          style: TextStyle(
                            fontSize: 13,
                            color: Colors.red.shade800,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 10),
              ],
              if (_isAppointment) ...[
                Card(
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(14),
                  ),
                  elevation: 0,
                  color: Colors.white,
                  child: ListTile(
                    contentPadding: const EdgeInsets.symmetric(
                      horizontal: 16,
                      vertical: 10,
                    ),
                    leading: CircleAvatar(
                      backgroundColor: Colors.grey.shade300,
                      backgroundImage:
                          (widget.entry.photoUrl != null &&
                              widget.entry.photoUrl!.isNotEmpty)
                          ? NetworkImage(widget.entry.photoUrl!)
                          : null,
                      child:
                          (widget.entry.photoUrl == null ||
                              widget.entry.photoUrl!.isEmpty)
                          ? const Icon(Icons.person, color: Colors.white)
                          : null,
                    ),
                    title: InkWell(
                      onTap: widget.entry.patientId == null
                          ? null
                          : _openPatientProfileFromDetails,
                      borderRadius: BorderRadius.circular(8),
                      child: Padding(
                        padding: const EdgeInsets.symmetric(vertical: 4),
                        child: Text(
                          widget.entry.patientName ??
                              t('unknownPatient', 'Unknown patient'),
                        ),
                      ),
                    ),
                    trailing: widget.entry.patientId == null
                        ? null
                        : const Icon(Icons.chevron_right),
                  ),
                ),
                const SizedBox(height: 10),
                Card(
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(14),
                  ),
                  elevation: 0,
                  color: Colors.white,
                  child: ExpansionTile(
                    key: ValueKey(
                      widget.entry.appointmentId ?? widget.entry.startAtUtc,
                    ),
                    initiallyExpanded: _showAiSummary,
                    onExpansionChanged: (v) =>
                        setState(() => _showAiSummary = v),
                    title: Text(
                      t('aiDocumentation', aiDocsFallback),
                      style: TextStyle(
                        fontWeight: FontWeight.w700,
                        color: Colors.grey.shade900,
                      ),
                    ),
                    childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 14),
                    children: [
                      DefaultTabController(
                        length: 2,
                        child: Column(
                          children: [
                            TabBar(
                              labelColor: brand,
                              unselectedLabelColor: Colors.grey.shade600,
                              indicatorColor: brand,
                              labelStyle: const TextStyle(
                                fontWeight: FontWeight.w700,
                              ),
                              tabs: [
                                Tab(
                                  text: t('appointmentNotes', notesTabFallback),
                                ),
                                Tab(
                                  text: t('summaryPdf', summaryPdfTabFallback),
                                ),
                              ],
                            ),
                            const SizedBox(height: 10),
                            SizedBox(
                              height: 280,
                              child: TabBarView(
                                children: [
                                  consultationNotesAsync.when(
                                    data: (notes) {
                                      if (notes.isEmpty) {
                                        return Align(
                                          alignment: Alignment.topLeft,
                                          child: Text(
                                            t('noSummaryYet', 'No summary yet'),
                                            style: TextStyle(
                                              color: Colors.grey.shade600,
                                            ),
                                          ),
                                        );
                                      }
                                      return ListView.separated(
                                        itemCount: notes.length,
                                        separatorBuilder: (_, __) =>
                                            const SizedBox(height: 8),
                                        itemBuilder: (context, i) {
                                          final n = notes[i];
                                          return _docCard(
                                            title: n.isFromAi
                                                ? t(
                                                    'fromShifaAi',
                                                    'From Shifa AI',
                                                  )
                                                : t(
                                                    'appointmentNote',
                                                    'Appointment Note',
                                                  ),
                                            subtitle: _compactDateTime(
                                              n.createdAt,
                                            ),
                                            body: n.displayText,
                                            brand: brand,
                                            l10n: l10n,
                                          );
                                        },
                                      );
                                    },
                                    loading: () => const Center(
                                      child: SizedBox(
                                        height: 18,
                                        width: 18,
                                        child: CircularProgressIndicator(
                                          strokeWidth: 2,
                                        ),
                                      ),
                                    ),
                                    error: (_, __) => Align(
                                      alignment: Alignment.topLeft,
                                      child: Text(
                                        l10n.translate('failedToLoad') ??
                                            'Failed to load',
                                        style: TextStyle(
                                          color: Colors.red.shade600,
                                        ),
                                      ),
                                    ),
                                  ),
                                  patientDocsAsync.when(
                                    data: (docs) {
                                      final summaryDoc =
                                          _pickSavedAppointmentSummaryDoc(
                                        docs,
                                        l10n,
                                      );
                                      if (summaryDoc == null) {
                                        return Align(
                                          alignment: Alignment.topLeft,
                                          child: Text(
                                            t(
                                              'noSummaryYet',
                                              'No summary yet',
                                            ),
                                            style: TextStyle(
                                              color: Colors.grey.shade600,
                                            ),
                                          ),
                                        );
                                      }
                                      return Column(
                                        crossAxisAlignment:
                                            CrossAxisAlignment.stretch,
                                        children: [
                                          _docCard(
                                            title: summaryDoc.title,
                                            subtitle:
                                                '${_fmtDate(context, summaryDoc.date)} ${_two(summaryDoc.date.hour)}:${_two(summaryDoc.date.minute)}',
                                            body: t(
                                              'appointmentDocumentation',
                                              'Appointment Documentation',
                                            ),
                                            brand: brand,
                                            l10n: l10n,
                                          ),
                                          const SizedBox(height: 10),
                                          ShifaPrimaryButton(
                                            label: t(
                                              'openSummary',
                                              'Open Summary',
                                            ),
                                            onPressed: _openSavedSummaryPdf,
                                            icon: Icons.picture_as_pdf,
                                            width: ButtonWidth.fill,
                                          ),
                                        ],
                                      );
                                    },
                                    loading: () => const Center(
                                      child: SizedBox(
                                        height: 18,
                                        width: 18,
                                        child: CircularProgressIndicator(
                                          strokeWidth: 2,
                                        ),
                                      ),
                                    ),
                                    error: (_, __) => Align(
                                      alignment: Alignment.topLeft,
                                      child: Text(
                                        l10n.translate('failedToLoad') ??
                                            'Failed to load',
                                        style: TextStyle(
                                          color: Colors.red.shade600,
                                        ),
                                      ),
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 10),
              ],
              // If FREE SLOT, show patient selection + dropdown place (or past-slot message)
              if (widget.entry.type == EntryType.freeSlot) ...[
                if (_isPastFreeSlot)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 10.0),
                    child: Card(
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(14),
                      ),
                      elevation: 0,
                      color: Colors.orange.shade50,
                      child: Padding(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 16,
                          vertical: 12,
                        ),
                        child: Row(
                          children: [
                            Icon(
                              Icons.info_outline,
                              color: Colors.orange.shade700,
                              size: 24,
                            ),
                            const SizedBox(width: 12),
                            Expanded(
                              child: Text(
                                l10n.translate('pastSlotCannotAssign') ??
                                    'This slot is in the past. You cannot assign a patient.',
                                style: TextStyle(color: Colors.orange.shade900),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  )
                else ...[
                  // Assign Patient (selection only; booking on Save)
                  Card(
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(14),
                    ),
                    elevation: 0,
                    color: Colors.white,
                    child: ListTile(
                      contentPadding: const EdgeInsets.symmetric(
                        horizontal: 16,
                        vertical: 8,
                      ),
                      leading: CircleAvatar(
                        backgroundColor: Colors.grey.shade300,
                        child: const Icon(
                          Icons.person_add,
                          color: Colors.white,
                        ),
                      ),
                      title: Text(
                        l10n.translate('assignPatient') ?? 'Assign Patient',
                      ),
                      subtitle: _selectedPatientName == null
                          ? null
                          : Text(
                              '${l10n.translate('selected') ?? 'Selected'}: $_selectedPatientName',
                            ),
                      trailing: const Icon(Icons.chevron_right),
                      onTap: () async {
                        // Load patients for assignment (id + name only)
                        List<PatientAssignmentItem> list;
                        try {
                          list = await ref
                              .read(patientsForAssignmentProvider.notifier)
                              .loadPatientsForAssignment();
                        } catch (e, st) {
                          debugPrint('Assign patient â€“ load failed: $e');
                          debugPrint('$st');
                          if (mounted) {
                            final msg = e is Exception ? e.toString() : '$e';
                            final isAuth =
                                msg.contains('401') ||
                                msg.contains('Unauthorized');
                            final isForbidden =
                                msg.contains('403') ||
                                msg.contains('Forbidden');
                            String display;
                            if (isAuth) {
                              display =
                                  l10n.translate(
                                    'unauthorizedPleaseLoginAgain',
                                  ) ??
                                  'Please sign in again.';
                            } else if (isForbidden) {
                              display =
                                  l10n.translate('failedToLoadPatients') ??
                                  'Failed to load patients. Please try again.';
                            } else {
                              display = msg.length > 80
                                  ? '${msg.substring(0, 80)}â€¦'
                                  : msg;
                            }
                            ScaffoldMessenger.of(
                              context,
                            ).showSnackBar(SnackBar(content: Text(display)));
                          }
                          return;
                        }

                        final selected = await showModalBottomSheet<
                            PatientAssignmentItem>(
                          context: context,
                          isScrollControlled: true,
                          builder: (ctx) => _AssignPatientSheet(
                            items: list,
                            l10n: AppLocalizations.of(ctx)!,
                          ),
                        );

                        if (selected != null && mounted) {
                          setState(() {
                            _selectedPatientId = int.tryParse(selected.id);
                            _selectedPatientName = selected.name;
                          });
                        }
                      },
                    ),
                  ),

                  const SizedBox(height: 10),

                  // Place dropdown (required for booking)
                  Card(
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(14),
                    ),
                    elevation: 0,
                    color: Colors.white,
                    child: Padding(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 16,
                        vertical: 12,
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            l10n.place,
                            style: const TextStyle(fontWeight: FontWeight.w600),
                          ),
                          const SizedBox(height: 8),
                          DropdownButtonFormField<String>(
                            value: selectedPlaceValue,
                            items: placeOptions
                                .map(
                                  (opt) => DropdownMenuItem<String>(
                                    value: opt,
                                    child: Text(opt),
                                  ),
                                )
                                .toList(),
                            onChanged: (val) {
                              setState(() => _selectedPlace = val);
                              _syncDirtyState();
                            },
                            decoration: InputDecoration(
                              border: OutlineInputBorder(
                                borderRadius: BorderRadius.circular(12),
                                borderSide: BorderSide(
                                  color: Colors.grey.shade300,
                                ),
                              ),
                              contentPadding: const EdgeInsets.symmetric(
                                horizontal: 12,
                                vertical: 12,
                              ),
                              hintText:
                                  l10n.translate('choosePlace') ??
                                  'Choose place',
                            ),
                          ),
                          if (selectedPlaceValue != null) ...[
                            const SizedBox(height: 6),
                            Text(
                              selectedPlaceValue == l10n.videoCall
                                  ? l10n.translate('willBeBookedAsVideoCall') ??
                                        'This will be booked as a video call.'
                                  : l10n.translate('willBeBookedAtClinic') ??
                                        'This will be booked at the clinic address.',
                              style: TextStyle(color: Colors.grey.shade600),
                            ),
                          ],
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(height: 10),
                  if (freeSlotStartOptions.isNotEmpty) ...[
                    Card(
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(14),
                      ),
                      elevation: 0,
                      color: Colors.white,
                      child: Padding(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 16,
                          vertical: 12,
                        ),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              l10n.translate('startTime') ?? 'Start time',
                              style:
                                  const TextStyle(fontWeight: FontWeight.w600),
                            ),
                            const SizedBox(height: 8),
                            DropdownButtonFormField<CalendarEntry>(
                              isExpanded: true,
                              value: freeStartDropdownValue,
                              decoration: InputDecoration(
                                border: OutlineInputBorder(
                                  borderRadius: BorderRadius.circular(12),
                                  borderSide: BorderSide(
                                    color: Colors.grey.shade300,
                                  ),
                                ),
                              ),
                              items: [
                                for (final o in freeSlotStartOptions)
                                  DropdownMenuItem<CalendarEntry>(
                                    value: o,
                                    child: Text(_fmtTime(o.start)),
                                  ),
                              ],
                              onChanged: (entry) {
                                if (entry == null) return;
                                setState(() {
                                  _bookingFreeSlot = entry;
                                  _bookingEndExclusive = null;
                                });
                                _syncDirtyState();
                              },
                            ),
                          ],
                        ),
                      ),
                    ),
                    const SizedBox(height: 10),
                  ],
                  if (freeSlotEndOptions.isNotEmpty) ...[
                    Card(
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(14),
                      ),
                      elevation: 0,
                      color: Colors.white,
                      child: Padding(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 16,
                          vertical: 12,
                        ),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              l10n.translate('bookingEndTime') ?? 'End time',
                              style:
                                  const TextStyle(fontWeight: FontWeight.w600),
                            ),
                            const SizedBox(height: 8),
                            DropdownButtonFormField<TimeOfDay>(
                              isExpanded: true,
                              value: freeEndDropdownValue,
                              decoration: InputDecoration(
                                border: OutlineInputBorder(
                                  borderRadius: BorderRadius.circular(12),
                                  borderSide: BorderSide(
                                    color: Colors.grey.shade300,
                                  ),
                                ),
                              ),
                              items: [
                                for (final o in freeSlotEndOptions)
                                  DropdownMenuItem<TimeOfDay>(
                                    value: o,
                                    child: Text(_fmtTime(o)),
                                  ),
                              ],
                              onChanged: (v) {
                                if (v == null) return;
                                setState(() => _bookingEndExclusive = v);
                                _syncDirtyState();
                              },
                            ),
                            const SizedBox(height: 6),
                            Text(
                              '${t('durationLabelShort', 'Duration')}: ${_durationLabel(_effectiveBookingStart, freeEndDropdownValue)}',
                              style: TextStyle(
                                fontSize: 12,
                                color: Colors.grey.shade600,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                    const SizedBox(height: 10),
                  ],

                  Card(
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(14),
                    ),
                    elevation: 0,
                    color: Colors.white,
                    child: Padding(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 16,
                        vertical: 12,
                      ),
                      child: TextField(
                        controller: _reasonCtrl,
                        decoration: InputDecoration(
                          border: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(12),
                          ),
                          labelText: l10n.translate('reason') ?? 'Reason',
                        ),
                        onChanged: (_) => _syncDirtyState(),
                      ),
                    ),
                  ),
                ],
              ],

              if (!_isBlocked) ...[
              const SizedBox(height: 10),

              // Date & Time â€” tap opens Change Slot dialog for (non-past) appointments
              Card(
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(14),
                ),
                elevation: 0,
                color: Colors.white,
                child: ListTile(
                  contentPadding: const EdgeInsets.symmetric(
                    horizontal: 16,
                    vertical: 12,
                  ),
                  title: Text(
                    AppLocalizations.of(context)!.translate('dateAndTime') ??
                        'Date and Time',
                    style: const TextStyle(fontWeight: FontWeight.w600),
                  ),
                  subtitle: Text(
                    '${_fmtDate(context, widget.day)}, ${_fmtTime(_effectiveBookingStart)} - ${_fmtTime(_detailHeaderEnd)}',
                    style: TextStyle(color: Colors.grey.shade700),
                  ),
                  trailing: const Icon(Icons.chevron_right),
                  onTap:
                      widget.entry.type == EntryType.appointment &&
                          !_isPastAppointment
                      ? () async {
                          await _showChangeSlotDialog(context);
                        }
                      : null,
                ),
              ),

              if (appointmentAdjustSelected != null) ...[
                const SizedBox(height: 10),
                Card(
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(14),
                  ),
                  elevation: 0,
                  color: Colors.white,
                  child: Padding(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 16,
                      vertical: 12,
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          t('adjustAppointmentDuration', 'Adjust duration'),
                          style: const TextStyle(fontWeight: FontWeight.w600),
                        ),
                        const SizedBox(height: 8),
                        DropdownButtonFormField<TimeOfDay>(
                          isExpanded: true,
                          value: appointmentAdjustSelected,
                          decoration: InputDecoration(
                            border: OutlineInputBorder(
                              borderRadius: BorderRadius.circular(12),
                              borderSide: BorderSide(
                                color: Colors.grey.shade300,
                              ),
                            ),
                          ),
                          items: [
                            for (final o in appointmentAdjustEnds)
                              DropdownMenuItem<TimeOfDay>(
                                value: o,
                                child: Text(_fmtTime(o)),
                              ),
                          ],
                          onChanged: (v) {
                            if (v == null) return;
                            setState(() => _adjustedAppointmentEndExclusive = v);
                            _syncDirtyState();
                          },
                        ),
                        const SizedBox(height: 10),
                        ShifaPrimaryButton(
                          label: t(
                            'applyAppointmentDuration',
                            'Apply duration change',
                          ),
                          onPressed: (!_appointmentDurationDirty || _saving)
                              ? null
                              : () {
                                  _persistAppointmentDuration();
                                },
                          width: ButtonWidth.fill,
                        ),
                      ],
                    ),
                  ),
                ),
              ],

              if (widget.entry.type == EntryType.appointment) ...[
                const SizedBox(height: 10),
                // Place (appointments only â€“ dropdown: doctor's clinic address or VIDEO CONSULTATION)
                AppointmentPlaceDropdown(
                  entry: widget.entry,
                  clinicVenueLabelOverride: widget.primaryClinicVenueLabel,
                ),
                if (_shouldShowEncouragePayment) ...[
                  const SizedBox(height: 12),
                  SizedBox(
                    width: double.infinity,
                    child: OutlinedButton.icon(
                      onPressed: _saving || _sendingPaymentReminder
                          ? null
                          : _encouragePayment,
                      icon: _sendingPaymentReminder
                          ? SizedBox(
                              width: 18,
                              height: 18,
                              child: CircularProgressIndicator(
                                strokeWidth: 2,
                                color: Theme.of(context).colorScheme.primary,
                              ),
                            )
                          : const Icon(Icons.notifications_active_outlined),
                      label: Text(l10n.translate('encouragePayment')),
                    ),
                  ),
                ],
              ],
              ],
            ],
          ),
        ),

        const SizedBox(height: 8),
        Container(
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(
            color: Colors.grey.shade50,
            border: Border.all(color: Colors.grey.shade200),
            borderRadius: BorderRadius.circular(14),
          ),
          child: Column(
            children: [
              if (_isBlocked) ...[
                ShifaPrimaryButton(
                  label: l10n.translate('unblockTime') ?? 'Remove block',
                  onPressed: _saving ? null : _removeScheduleBlock,
                  width: ButtonWidth.fill,
                  isLoading: _saving,
                ),
                const SizedBox(height: 10),
                ShifaSecondaryButton(
                  label: AppLocalizations.of(context)!.close,
                  onPressed: _saving ? null : widget.onClose,
                  width: ButtonWidth.fill,
                ),
              ] else ...[
              // ---------- Primary action ----------
              ShifaPrimaryButton(
                // Match Home/Today behavior: video appointments become startable
                // only from 5 minutes before scheduled start.
                label: widget.entry.type == EntryType.freeSlot
                    ? AppLocalizations.of(context)!.save
                    : _primaryCtaLabel(l10n),
                onPressed:
                    (_saving ||
                        (widget.entry.type == EntryType.freeSlot &&
                            _isPastFreeSlot) ||
                        (widget.entry.type == EntryType.appointment &&
                            _isPastDueStartBlocked) ||
                        (widget.entry.type == EntryType.appointment &&
                            _isVideoAppointment &&
                            !_canStartVideoByTimeWindow &&
                            !_isInProgressStatus &&
                            !_isCompletedStatus))
                    ? null
                    : () async {
                        if (widget.entry.type == EntryType.freeSlot) {
                          if (_isPastFreeSlot) return;
                          // Perform assignment only on Save
                          await _assignOnSave();
                        } else {
                          if (_isCompletedStatus) {
                            await _openSavedSummaryPdf();
                          } else {
                            await _openAppointmentWorkspace();
                          }
                        }
                      },
                width: ButtonWidth.fill,
                isLoading: _saving,
              ),
              const SizedBox(height: 10),

              // For appointments: Cancel and Discard buttons (disabled for past appointments)
              // For free slots: Discard button
              if (widget.entry.type == EntryType.appointment) ...[
                if (_isPastDueStartBlocked)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 8.0),
                    child: Text(
                      AppLocalizations.of(
                            context,
                          )!.translate('pastAppointmentNoChange') ??
                          'Past appointments cannot be changed or cancelled.',
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        color: Colors.grey.shade700,
                      ),
                    ),
                  ),
                if (_isVideoAppointment &&
                    !_canStartVideoByTimeWindow &&
                    !_isInProgressStatus &&
                    !_isCompletedStatus)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 8.0),
                    child: Text(
                      _isVideoPastColdJoinGraceHint
                          ? AppLocalizations.of(context)!
                              .videoCallTooLateAfterOneHour
                          : (AppLocalizations.of(context)!
                                  .translate('videoCallAvailableFiveMinBefore') ??
                              'You can start 5 minutes before the appointment.'),
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        color: Colors.grey.shade700,
                      ),
                    ),
                  ),
                Row(
                  children: [
                    Expanded(
                      child: ShifaPrimaryButton(
                        label: AppLocalizations.of(context)!.cancel,
                        onPressed:
                            _saving ||
                                _isPastAppointment ||
                                _isCompletedStatus ||
                                _isCancelledStatus
                            ? null
                            : () async {
                                // Show confirmation dialog
                                final l10n = AppLocalizations.of(context)!;
                                final confirmed = await showDialog<bool>(
                                  context: context,
                                  builder: (ctx) => AlertDialog(
                                    title: Text(l10n.cancelAppointment),
                                    content: Text(l10n.cancelConfirm),
                                    actions: [
                                      TextButton(
                                        onPressed: () =>
                                            Navigator.pop(ctx, false),
                                        child: Text(l10n.no),
                                      ),
                                      TextButton(
                                        onPressed: () =>
                                            Navigator.pop(ctx, true),
                                        child: Text(
                                          l10n.translate('yesCancel') ??
                                              'Yes, Cancel',
                                        ),
                                      ),
                                    ],
                                  ),
                                );

                                if (confirmed == true &&
                                    widget.entry.appointmentId != null) {
                                  final doctorTimeZone = _calendarTz();
                                  try {
                                    setState(() => _saving = true);
                                    await ref
                                        .read(calendarProvider.notifier)
                                        .cancelAppointment(
                                          appointmentId:
                                              widget.entry.appointmentId!,
                                          day: widget.day,
                                          doctorTimeZone: doctorTimeZone,
                                        );
                                    // Refresh Home's "Today" list and both old and new days in calendar
                                    await invalidateAppointmentRelatedProviders(ref);
                                    await refreshCalendarDay(
                                      ref,
                                      widget.day,
                                      doctorTimeZone,
                                    );
                                    await widget.onSavedSuccessfully();
                                    if (mounted) {
                                      ScaffoldMessenger.of(
                                        context,
                                      ).showSnackBar(
                                        SnackBar(
                                          content: Text(
                                            l10n.appointmentCancelled,
                                          ),
                                        ),
                                      );
                                    }
                                  } catch (e) {
                                    if (mounted) {
                                      ScaffoldMessenger.of(
                                        context,
                                      ).showSnackBar(
                                        SnackBar(
                                          content: Text(
                                            '${l10n.translate('failedToCancel') ?? 'Failed to cancel'}: $e',
                                          ),
                                        ),
                                      );
                                    }
                                  } finally {
                                    if (mounted)
                                      setState(() => _saving = false);
                                  }
                                }
                              },
                        variant: ButtonVariant.destructive,
                        isLoading: _saving,
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: ShifaSecondaryButton(
                        label: AppLocalizations.of(context)!.discard,
                        onPressed: _saving
                            ? null
                            : () async {
                                final shouldClose =
                                    await _confirmDiscardIfDirty();
                                if (shouldClose && mounted) widget.onClose();
                              },
                        variant: ButtonVariant.destructive,
                      ),
                    ),
                  ],
                ),
              ] else ...[
                // For free slots: Discard button
                ShifaSecondaryButton(
                  label: AppLocalizations.of(context)!.discard,
                  onPressed: _saving
                      ? null
                      : () async {
                          final shouldClose = await _confirmDiscardIfDirty();
                          if (shouldClose && mounted) widget.onClose();
                        },
                  width: ButtonWidth.fill,
                  variant: ButtonVariant.destructive,
                ),
              ],
              ],
            ],
          ),
        ),
      ],
    );
  }
}

/// Bottom sheet content for assigning a patient to a slot, with search by name or ID.
class _AssignPatientSheet extends ConsumerStatefulWidget {
  const _AssignPatientSheet({required this.items, required this.l10n});

  final List<PatientAssignmentItem> items;
  final AppLocalizations l10n;

  @override
  ConsumerState<_AssignPatientSheet> createState() =>
      _AssignPatientSheetState();
}

class _AssignPatientSheetState extends ConsumerState<_AssignPatientSheet> {
  final TextEditingController _searchController = TextEditingController();
  final FocusNode _searchFocus = FocusNode();
  late List<PatientAssignmentItem> _items;

  @override
  void initState() {
    super.initState();
    _items = List<PatientAssignmentItem>.from(widget.items);
  }

  @override
  void dispose() {
    _searchController.dispose();
    _searchFocus.dispose();
    super.dispose();
  }

  Future<void> _createAndSelectPatient() async {
    final created = await showCreatePatientSheet(
      context,
      ref,
      reloadPatientsList: true,
    );
    if (created == null || !mounted) return;

    await ref
        .read(patientsForAssignmentProvider.notifier)
        .loadPatientsForAssignment();

    final item = PatientAssignmentItem(
      id: created.id,
      name: created.name,
      photoUrl: created.photoUrl,
    );

    if (!mounted) return;
    Navigator.pop(context, item);
  }

  List<PatientAssignmentItem> get _filteredItems {
    final query = _searchController.text.trim().toLowerCase();
    if (query.isEmpty) return _items;
    return _items.where((p) {
      return p.name.toLowerCase().contains(query) ||
          p.id.toLowerCase().contains(query);
    }).toList();
  }

  @override
  Widget build(BuildContext context) {
    return DraggableScrollableSheet(
      initialChildSize: 0.7,
      minChildSize: 0.5,
      maxChildSize: 0.95,
      expand: false,
      builder: (context, scrollController) => Material(
        color: Theme.of(context).dialogBackgroundColor,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(16)),
        child: SafeArea(
          top: false,
          child: Column(
            children: [
              // Drag handle
              Padding(
                padding: const EdgeInsets.only(top: 12, bottom: 4),
                child: Container(
                  width: 40,
                  height: 4,
                  decoration: BoxDecoration(
                    color: Colors.grey.shade300,
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
              ),
              // Search bar â€“ always visible at top
              Container(
                width: double.infinity,
                padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
                color: Theme.of(context).dialogBackgroundColor,
                child: Row(
                  children: [
                    Expanded(
                      child: TextField(
                        controller: _searchController,
                        focusNode: _searchFocus,
                        decoration: InputDecoration(
                          hintText: widget.l10n.searchByNameOrId,
                          prefixIcon:
                              const Icon(Icons.search, color: Colors.grey),
                          border: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(12),
                          ),
                          filled: true,
                          fillColor: Colors.grey.shade100,
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 14,
                          ),
                        ),
                        onChanged: (_) => setState(() {}),
                      ),
                    ),
                    const SizedBox(width: 8),
                    IconButton.filled(
                      onPressed: _createAndSelectPatient,
                      icon: const Icon(Icons.add),
                      tooltip: widget.l10n.translate('newPatient') ??
                          'New Patient',
                      style: IconButton.styleFrom(
                        backgroundColor: Theme.of(context).colorScheme.primary,
                        foregroundColor: Colors.white,
                        minimumSize: const Size(48, 48),
                      ),
                    ),
                  ],
                ),
              ),
              const Divider(height: 1),
              // Patient list (id + name only)
              Expanded(
                child: _filteredItems.isEmpty
                    ? Center(
                        child: Text(
                          widget.l10n.noPatientsFound,
                          style: TextStyle(
                            color: Colors.grey.shade600,
                            fontSize: 14,
                          ),
                        ),
                      )
                    : ListView.builder(
                        controller: scrollController,
                        itemCount: _filteredItems.length,
                        itemBuilder: (context, index) {
                          final p = _filteredItems[index];
                          return ListTile(
                            leading: CircleAvatar(
                              backgroundColor: Colors.grey.shade300,
                              backgroundImage:
                                  (p.photoUrl != null && p.photoUrl!.isNotEmpty)
                                  ? NetworkImage(p.photoUrl!)
                                  : null,
                              child: (p.photoUrl == null || p.photoUrl!.isEmpty)
                                  ? const Icon(
                                      Icons.person,
                                      color: Colors.white,
                                    )
                                  : null,
                            ),
                            title: Text(p.name),
                            subtitle: Text(
                              '${widget.l10n.translate('id')}: ${p.id}',
                            ),
                            onTap: () => Navigator.pop(context, p),
                          );
                        },
                      ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
