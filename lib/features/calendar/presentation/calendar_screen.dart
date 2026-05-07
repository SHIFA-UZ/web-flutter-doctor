// lib/features/calendar/presentation/calendar_screen.dart
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:table_calendar/table_calendar.dart';

import 'package:shifa_doc_app_v1/app/router.dart';
import 'package:shifa_doc_app_v1/features/shell/presentation/shell_scope.dart';
import 'package:shifa_doc_app_v1/features/calendar/domain/calendar_models.dart';
import 'package:shifa_doc_app_v1/features/appointments/domain/appointment_models.dart';
import 'package:shifa_doc_app_v1/features/appointments/application/consultation_notes_provider.dart';
import 'package:shifa_doc_app_v1/core/api/consultation_notes_api.dart';
import 'package:shifa_doc_app_v1/features/patients/domain/patient_models.dart';
import 'package:shifa_doc_app_v1/state/calendar/calendar_controller.dart';
import 'package:shifa_doc_app_v1/state/patients/patient_documents_provider.dart';
import 'package:shifa_doc_app_v1/state/patients/patients_provider.dart';
import 'package:shifa_doc_app_v1/state/shell/shell_controller.dart';
import 'package:shifa_doc_app_v1/state/profile/profile_providers.dart';
import 'package:shifa_doc_app_v1/core/utils/patient_warning_utils.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/utils/timezone_utils.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';
import 'package:shifa_doc_app_v1/core/theme/app_colors.dart';

import 'package:shifa_doc_app_v1/state/appointments/appointment_invalidation.dart';

class CalendarScreen extends ConsumerStatefulWidget {
  const CalendarScreen({Key? key}) : super(key: key);

  @override
  ConsumerState<CalendarScreen> createState() => _CalendarScreenState();
}

class _CalendarScreenState extends ConsumerState<CalendarScreen>
    with WidgetsBindingObserver {
  DateTime _focusedDay = DateTime.now();
  DateTime? _selectedDay;
  CalendarEntry? _selectedEntry;
  bool _loadingDay = false;
  DateTime? _lastRefreshTime;
  bool _timeZoneHintDismissed = false;

  // Filter state
  bool _showAppointments = true;
  bool _showFreeSlots = true;

  /// When true, profile listener skips loading "today" so go-to-appointment can load the target day only.
  bool _skipInitialProfileLoad = false;

  DateTime _dayKey(DateTime d) => DateTime(d.year, d.month, d.day);
  String _two(int n) => n.toString().padLeft(2, '0');

  List<CalendarEntry> _entriesFor(DateTime? day) {
    if (day == null) return [];
    final entries = ref.watch(calendarProvider);
    final allEntries = entries[_dayKey(day)] ?? [];

    // Apply filters
    return allEntries.where((e) {
      if (e.type == EntryType.appointment) return _showAppointments;
      if (e.type == EntryType.freeSlot) return _showFreeSlots;
      return true;
    }).toList();
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

  // Show update schedule warning only if:
  // 1. Not loading (prevents flash during refresh)
  // 2. Day is selected
  // 3. No free slots exist in the actual data (regardless of filters)
  bool get _shouldShowUpdateScheduleCard {
    if (_loadingDay || _isWaitingForProfile)
      return false; // Don't show while loading
    if (_selectedDay == null) return false;
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
      _focusedDay = _selectedDay!;
    } else {
      final doctorTz =
          ref.read(profileAllProvider).valueOrNull?.profile['timeZone']
              as String?;
      final today = getTodayInTimezone(doctorTz);
      _selectedDay = DateTime(today.year, today.month, today.day);
      _focusedDay = _selectedDay!;
    }

    // Reactive: load calendar when profile is available (use UTC if timezone missing so calendar still loads)
    ref.listenManual(profileAllProvider, (previous, next) {
      if (_skipInitialProfileLoad) return;
      if (next.hasValue) {
        final tz = next.value?.profile['timeZone'] as String?;
        final effectiveTz = (tz != null && tz.trim().isNotEmpty) ? tz : 'UTC';
        if (_selectedDay != null && mounted) {
          debugPrint(
            'CalendarScreen: Profile loaded (timezone: ${tz ?? "UTC fallback"}), loading calendar for $_selectedDay',
          );
          _loadDay(_selectedDay!, effectiveTz);
        }
      } else if (next.isLoading) {
        debugPrint('CalendarScreen: Waiting for profile to load...');
      } else if (next.hasError) {
        debugPrint('CalendarScreen: Profile load failed: ${next.error}');
      }
    }, fireImmediately: true);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    super.didChangeAppLifecycleState(state);
    if (state == AppLifecycleState.resumed && _selectedDay != null) {
      // Refresh calendar when app resumes (throttled to avoid excessive API calls)
      final tz =
          ref.read(profileAllProvider).valueOrNull?.profile['timeZone']
              as String?;
      final effectiveTz = (tz != null && tz.trim().isNotEmpty) ? tz : 'UTC';
      final now = getNowInTimezone(effectiveTz);
      if (_lastRefreshTime == null ||
          now.difference(_lastRefreshTime!).inSeconds > 5) {
        _loadDay(_selectedDay!, effectiveTz);
        _lastRefreshTime = now;
      }
    }
  }

  Future<void> _loadDay(DateTime day, String doctorTimeZone) async {
    setState(() => _loadingDay = true);
    try {
      debugPrint(
        'CalendarScreen: Loading day ${_ymd(day)} with timezone $doctorTimeZone',
      );
      await ref
          .read(calendarProvider.notifier)
          .loadDay(day: day, doctorTimeZone: doctorTimeZone);
      // Record refresh time in doctor's timezone for consistency
      _lastRefreshTime = getNowInTimezone(doctorTimeZone);
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

  String _ymd(DateTime d) =>
      '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';

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
      return 'Joriy qurilma vaqt zonasi ($scheduleTimeZone bilan) mos emas. '
          'Siz slotlarni bir vaqt zonasida belgilagansiz, hozir esa boshqa vaqt zonasidasiz. '
          'Iltimos, uchrashuvlar bilan ishlaganda buni inobatga oling.';
    }
    if (localeCode == 'ru') {
      return 'Текущий часовой пояс устройства не совпадает с часовым поясом расписания ($scheduleTimeZone). '
          'Слоты были заданы в одном часовом поясе, а сейчас вы в другом. '
          'Пожалуйста, учитывайте это при работе с приёмами.';
    }
    return 'Your current device timezone does not match the calendar schedule timezone ($scheduleTimeZone). '
        'Your slots were defined in one timezone, but you are currently in another. '
        'Please keep this in mind while managing appointments.';
  }

  @override
  Widget build(BuildContext context) {
    // Go-to-appointment: day already set in initState from calendarGoToAppointmentDayProvider.
    // Just load that day and select the slot; clear providers in callback (not during build).
    final goToId = ref.watch(calendarGoToAppointmentIdProvider);
    if (goToId != null && goToId > 0 && _selectedDay != null && mounted) {
      WidgetsBinding.instance.addPostFrameCallback((_) async {
        if (!mounted) return;
        ref.read(calendarGoToAppointmentIdProvider.notifier).state = null;
        ref.read(calendarGoToAppointmentDayProvider.notifier).state = null;
        final appointmentId = goToId;
        final day = _selectedDay!;
        final tz =
            ref.read(profileAllProvider).valueOrNull?.profile['timeZone']
                as String?;
        if (tz == null || tz.isEmpty) {
          _skipInitialProfileLoad = false;
          return;
        }
        try {
          await _loadDay(day, tz);
          if (!mounted) return;
          final entries =
              ref.read(calendarProvider)[_dayKey(day)] ?? <CalendarEntry>[];
          final match = entries
              .where((e) => e.appointmentId == appointmentId)
              .toList();
          if (match.isNotEmpty && mounted) {
            setState(() => _selectedEntry = match.first);
          }
          _skipInitialProfileLoad = false;
        } catch (e) {
          debugPrint(
            'CalendarScreen: failed to go to appointment $appointmentId: $e',
          );
          _skipInitialProfileLoad = false;
        }
      });
    }

    final l10n = AppLocalizations.of(context)!;
    final brand = Theme.of(context).colorScheme.primary;
    final dateLabel = _selectedDay == null
        ? null
        : '${_selectedDay!.day} ${l10n.monthName(_selectedDay!.month)} ${_selectedDay!.year}';

    final profile = ref.watch(profileAllProvider).valueOrNull?.profile;
    final profileTimeZone = profile != null
        ? (profile['timeZone'] as String?)
        : null;
    final showTimeZoneHint =
        !_timeZoneHintDismissed &&
        _shouldShowTimeZoneMismatchHint(profileTimeZone);

    return Scaffold(
      backgroundColor: const Color(0xFFF5F5F5),
      body: Padding(
        padding: const EdgeInsets.all(24),
        child: Row(
          children: [
            // ---------- Left: header + entries list ----------
            Expanded(
              flex: 3,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  if (showTimeZoneHint)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 12),
                      child: Material(
                        color: Colors.amber.shade50,
                        borderRadius: BorderRadius.circular(8),
                        child: Padding(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 12,
                            vertical: 8,
                          ),
                          child: Row(
                            children: [
                              Icon(
                                Icons.schedule,
                                color: Colors.amber.shade800,
                                size: 20,
                              ),
                              const SizedBox(width: 8),
                              Expanded(
                                child: Text(
                                  _timeZoneMismatchHintMessage(
                                    scheduleTimeZone: profileTimeZone?.trim() ?? 'Unknown',
                                  ),
                                  style: TextStyle(
                                    fontSize: 13,
                                    color: Colors.amber.shade900,
                                  ),
                                ),
                              ),
                              TextButton(
                                onPressed: () => setState(
                                  () => _timeZoneHintDismissed = true,
                                ),
                                child: Text(
                                  l10n.translate('dismiss') ?? 'Dismiss',
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ),
                  // Top bar
                  Row(
                    children: [
                      Text(
                        l10n.calendar,
                        style: const TextStyle(
                          fontSize: 28,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      const SizedBox(width: 12),
                      if (dateLabel != null)
                        InkWell(
                          onTap: () async {
                            final picked = await showDatePicker(
                              context: context,
                              initialDate: _selectedDay ?? DateTime.now(),
                              firstDate: DateTime(2020),
                              lastDate: DateTime(2030),
                            );
                            if (picked != null) {
                              setState(() {
                                _selectedDay = DateTime(
                                  picked.year,
                                  picked.month,
                                  picked.day,
                                );
                                _focusedDay = _selectedDay!;
                                _selectedEntry = null;
                              });
                              final tz =
                                  ref
                                          .read(profileAllProvider)
                                          .valueOrNull
                                          ?.profile['timeZone']
                                      as String?;
                              final effectiveTz =
                                  (tz != null && tz.trim().isNotEmpty)
                                  ? tz
                                  : 'UTC';
                              _loadDay(_selectedDay!, effectiveTz);
                            }
                          },
                          borderRadius: BorderRadius.circular(20),
                          child: Container(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 12,
                              vertical: 6,
                            ),
                            decoration: BoxDecoration(
                              color: Colors.white,
                              borderRadius: BorderRadius.circular(20),
                              border: Border.all(color: brand.withOpacity(0.4)),
                            ),
                            child: Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Text(
                                  dateLabel,
                                  style: TextStyle(
                                    color: brand,
                                    fontWeight: FontWeight.w600,
                                  ),
                                ),
                                const SizedBox(width: 4),
                                Icon(
                                  Icons.calendar_today,
                                  size: 16,
                                  color: brand,
                                ),
                              ],
                            ),
                          ),
                        ),
                      const Spacer(),
                      if (_loadingDay)
                        const Padding(
                          padding: EdgeInsets.only(right: 8.0),
                          child: SizedBox(
                            width: 18,
                            height: 18,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          ),
                        ),
                      const SizedBox(width: 8),
                      ShifaSecondaryButton(
                        label: l10n.filter,
                        onPressed: () async {
                          // Store current filter state as starting point
                          bool tempShowAppointments = _showAppointments;
                          bool tempShowFreeSlots = _showFreeSlots;

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
                                            l10n.translate(
                                                  'showAppointments',
                                                ) ??
                                                'Show Appointments',
                                          ),
                                          value: tempShowAppointments,
                                          onChanged: (val) {
                                            setDialogState(() {
                                              tempShowAppointments =
                                                  val ?? true;
                                            });
                                          },
                                        ),
                                        CheckboxListTile(
                                          title: Text(
                                            l10n.translate('showFreeSlots') ??
                                                'Show Free Slots',
                                          ),
                                          value: tempShowFreeSlots,
                                          onChanged: (val) {
                                            setDialogState(() {
                                              tempShowFreeSlots = val ?? true;
                                            });
                                          },
                                        ),
                                      ],
                                    ),
                                    actions: [
                                      TextButton(
                                        onPressed: () =>
                                            Navigator.pop(ctx, false),
                                        child: Text(l10n.cancel),
                                      ),
                                      ShifaPrimaryButton(
                                        label: l10n.translate('apply') ?? 'Apply',
                                        onPressed: () =>
                                            Navigator.pop(ctx, true),
                                        icon: Icons.check,
                                      ),
                                    ],
                                  );
                                },
                              );
                            },
                          );

                          // Apply filters and refresh if user clicked Apply
                          if (result == true && mounted) {
                            setState(() {
                              _showAppointments = tempShowAppointments;
                              _showFreeSlots = tempShowFreeSlots;
                            });

                            // Refresh the current day to reflect filter changes
                            final tz =
                                ref
                                        .read(profileAllProvider)
                                        .valueOrNull
                                        ?.profile['timeZone']
                                    as String?;
                            final effectiveTz =
                                (tz != null && tz.trim().isNotEmpty)
                                ? tz
                                : 'UTC';
                            if (_selectedDay != null) {
                              await _loadDay(_selectedDay!, effectiveTz);
                            }
                          }
                        },
                        icon: Icons.tune,
                      ),
                    ],
                  ),
                  const SizedBox(height: 16),
                  // Content list
                  Expanded(
                    child: _selectedDay == null
                        ? _EmptyCalendarHint(brand: brand)
                        : _DayEntriesList(
                            entries: _entriesFor(_selectedDay),
                            onTap: (entry) {
                              setState(() => _selectedEntry = entry);
                            },
                            selected: _selectedEntry,
                            brand: brand,
                            loading: _loadingDay || _isWaitingForProfile,
                          ),
                  ),
                ],
              ),
            ),

            const SizedBox(width: 24),

            // ---------- Right: calendar OR slot details ----------
            Expanded(
              flex: 2,
              child: AnimatedSwitcher(
                duration: const Duration(milliseconds: 250),
                switchInCurve: Curves.easeOut,
                switchOutCurve: Curves.easeIn,
                child: _selectedEntry == null
                    ? _CalendarPanel(
                        key: ValueKey(
                          'calendar_${_selectedDay?.year ?? _focusedDay.year}_${_selectedDay?.month ?? _focusedDay.month}_${_selectedDay?.day ?? _focusedDay.day}',
                        ),
                        focusedDay: _focusedDay,
                        selectedDay: _selectedDay,
                        onChanged: (d) {
                          setState(() {
                            _selectedDay = DateTime(d.year, d.month, d.day);
                            _focusedDay = _selectedDay!;
                            _selectedEntry = null;
                          });
                          final tz =
                              ref
                                      .read(profileAllProvider)
                                      .valueOrNull
                                      ?.profile['timeZone']
                                  as String?;
                          final effectiveTz =
                              (tz != null && tz.trim().isNotEmpty) ? tz : 'UTC';
                          _loadDay(_selectedDay!, effectiveTz);
                        },
                        onFocusedDayChanged: (d) {
                          setState(() => _focusedDay = d);
                        },
                        showUpdateCard: _shouldShowUpdateScheduleCard,
                        onGoToSchedule: () {
                          ShellScope.pushNamed(
                            context,
                            AppRoutes.setupSchedule,
                          );
                        },
                      )
                    : _SlotDetailsPanel(
                        key: const ValueKey('details'),
                        entry: _selectedEntry!,
                        day: _selectedDay!,
                        onSavedSuccessfully: () async {
                          final tz =
                              ref
                                      .read(profileAllProvider)
                                      .valueOrNull
                                      ?.profile['timeZone']
                                  as String?;
                          if (tz != null && tz.trim().isNotEmpty)
                            await _loadDay(_selectedDay!, tz);
                          if (mounted) setState(() => _selectedEntry = null);
                        },
                        onClose: () => setState(() => _selectedEntry = null),
                      ),
              ),
            ),
          ],
        ),
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
class _DayEntriesList extends StatelessWidget {
  const _DayEntriesList({
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
        final isSelected = identical(e, selected);
        final isVideoLocation = (e.location).toLowerCase().contains('video');
        final locationLabel = isVideoLocation ? l10n.videoCall : e.location;

        return Container(
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(12),
            border: Border.all(
              color: isSelected ? brand : Colors.transparent,
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
          child: ListTile(
            contentPadding: const EdgeInsets.symmetric(
              horizontal: 16,
              vertical: 6,
            ),
            onTap: () => onTap(e),
            leading: e.type == EntryType.freeSlot
                ? CircleAvatar(
                    backgroundColor: Colors.white,
                    foregroundColor: brand,
                    child: const Icon(Icons.add),
                  )
                : CircleAvatar(
                    backgroundColor: Colors.grey.shade300,
                    // ✅ Show patient photo when available
                    backgroundImage:
                        (e.photoUrl != null && e.photoUrl!.isNotEmpty)
                        ? NetworkImage(e.photoUrl!)
                        : null,
                    child: (e.photoUrl == null || e.photoUrl!.isEmpty)
                        ? const Icon(Icons.person, color: Colors.white)
                        : null,
                  ),
            title: e.type == EntryType.freeSlot
                ? Text(
                    AppLocalizations.of(context)!.freeSlots,
                    style: const TextStyle(fontWeight: FontWeight.w600),
                  )
                : Text(
                    e.patientName ?? AppLocalizations.of(context)!.appointments,
                    style: const TextStyle(fontWeight: FontWeight.w600),
                  ),
            subtitle: locationLabel.trim().isEmpty
                ? null
                : Padding(
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
            trailing: Column(
              crossAxisAlignment: CrossAxisAlignment.end,
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Text(
                  _fmtRange(e.start, e.end),
                  style: const TextStyle(fontWeight: FontWeight.w600),
                ),
                if (e.type == EntryType.appointment)
                  Text(
                    e.reason,
                    style: TextStyle(fontSize: 12, color: Colors.grey.shade600),
                  ),
              ],
            ),
          ),
        );
      },
    );
  }
}

/// ---- Right: Calendar panel (customizable table_calendar) ----
class _CalendarPanel extends StatelessWidget {
  const _CalendarPanel({
    Key? key,
    required this.focusedDay,
    required this.selectedDay,
    required this.onChanged,
    this.onFocusedDayChanged,
    required this.showUpdateCard,
    required this.onGoToSchedule,
  }) : super(key: key);

  final DateTime focusedDay;
  final DateTime? selectedDay;
  final ValueChanged<DateTime> onChanged;
  final ValueChanged<DateTime>? onFocusedDayChanged;
  final bool showUpdateCard;
  final VoidCallback onGoToSchedule;

  @override
  Widget build(BuildContext context) {
    final brand = Theme.of(context).colorScheme.primary;

    return Column(
      key: key,
      children: [
        Align(
          alignment: Alignment.centerLeft,
          child: Text(
            '${focusedDay.year}',
            style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w700),
          ),
        ),
        const SizedBox(height: 12),
        SingleChildScrollView(
          child: Container(
            constraints: const BoxConstraints(minHeight: 380),
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
            padding: const EdgeInsets.all(8),
            child: TableCalendar(
              locale: Localizations.localeOf(context).toString(),
              firstDay: DateTime(2020),
              lastDay: DateTime(2030),
              focusedDay: focusedDay,
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
                selectedDecoration: BoxDecoration(
                  color: brand,
                  shape: BoxShape.circle,
                ),
                selectedTextStyle: const TextStyle(
                  color: Colors.white,
                  fontWeight: FontWeight.w600,
                ),
                todayDecoration: BoxDecoration(
                  color: brand.withOpacity(0.3),
                  shape: BoxShape.circle,
                ),
                todayTextStyle: TextStyle(
                  color: brand,
                  fontWeight: FontWeight.w600,
                ),
                defaultTextStyle: TextStyle(color: Colors.grey.shade800),
                weekendTextStyle: TextStyle(color: Colors.grey.shade800),
                outsideTextStyle: TextStyle(color: Colors.grey.shade400),
                markerDecoration: const BoxDecoration(shape: BoxShape.circle),
              ),
              headerStyle: HeaderStyle(
                formatButtonVisible: false,
                titleCentered: true,
                leftChevronIcon: const Icon(Icons.chevron_left),
                rightChevronIcon: const Icon(Icons.chevron_right),
                headerPadding: const EdgeInsets.symmetric(vertical: 8),
                titleTextStyle: const TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.w600,
                ),
              ),
              daysOfWeekStyle: DaysOfWeekStyle(
                weekdayStyle: TextStyle(
                  color: Colors.grey.shade700,
                  fontWeight: FontWeight.w600,
                ),
                weekendStyle: TextStyle(
                  color: Colors.grey.shade700,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ),
          ),
        ),
        const SizedBox(height: 12),
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
            onPressed: onGoToSchedule,
            width: ButtonWidth.fill,
          ),
        ],
      ],
    );
  }
}

/// Dropdown for Place in slot details: doctor's clinic address (from backend) or VIDEO CONSULTATION.
class _AppointmentPlaceDropdown extends ConsumerWidget {
  const _AppointmentPlaceDropdown({Key? key, required this.entry})
    : super(key: key);
  final CalendarEntry entry;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context)!;
    final profileAsync = ref.watch(profileAllProvider);
    final profile = profileAsync.valueOrNull?.profile;
    final doctorAddress =
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
    final options = <String>{
      clinicOption,
      videoOption,
    }.where((v) => v.trim().isNotEmpty).toList(growable: false);
    final isVideo = entry.location.toLowerCase().contains('video');
    final currentValue = isVideo
        ? videoOption
        : (entry.location.isEmpty ? clinicOption : entry.location);
    // If current location is neither option (e.g. legacy text), show it or fallback to clinic
    final value = options.contains(currentValue)
        ? currentValue
        : (options.isNotEmpty ? options.first : null);

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
            DropdownButtonFormField<String>(
              value: value,
              isExpanded: true,
              decoration: InputDecoration(
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(8),
                ),
                contentPadding: const EdgeInsets.symmetric(
                  horizontal: 12,
                  vertical: 10,
                ),
              ),
              items: options
                  .map(
                    (String opt) => DropdownMenuItem<String>(
                      value: opt,
                      child: Text(
                        opt,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(color: Colors.grey.shade800),
                      ),
                    ),
                  )
                  .toList(),
              onChanged: (_) {
                // Place change for existing appointment could be wired to an API when available.
              },
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
class _SlotDetailsPanel extends ConsumerStatefulWidget {
  const _SlotDetailsPanel({
    Key? key,
    required this.entry,
    required this.day,
    required this.onSavedSuccessfully,
    required this.onClose,
  }) : super(key: key);

  final CalendarEntry entry;
  final DateTime day;

  /// Called by panel when save succeeds (to reload list / close panel).
  final Future<void> Function() onSavedSuccessfully;

  /// Called when user discards / closes the panel without saving.
  final VoidCallback onClose;

  @override
  ConsumerState<_SlotDetailsPanel> createState() => _SlotDetailsPanelState();
}

class _SlotDetailsPanelState extends ConsumerState<_SlotDetailsPanel> {
  // Selection state for assignment
  int? _selectedPatientId;
  String? _selectedPatientName;

  // Place selection: 'Clinic Address' or 'Video Consultation'
  String? _selectedPlace;
  late final TextEditingController _reasonCtrl;

  bool _saving = false;
  bool _hasUnsavedChanges = false;
  bool _showAiSummary = true;
  String _initialReason = '';
  String _initialPlace = '';

  String _two(int n) => n.toString().padLeft(2, '0');
  String _fmtDate(BuildContext context, DateTime d) =>
      '${_two(d.day)} ${AppLocalizations.of(context)!.monthName(d.month)} ${d.year}';
  String _fmtTime(TimeOfDay t) => '${_two(t.hour)}:${_two(t.minute)}';

  bool get _isAppointment => widget.entry.type == EntryType.appointment;
  String get _statusUpper => (widget.entry.status ?? '').trim().toUpperCase();
  bool get _isCompletedStatus => _statusUpper == 'COMPLETED';
  bool get _isCancelledStatus => _statusUpper == 'CANCELLED';
  bool get _isVideoAppointment =>
      _isAppointment &&
      (widget.entry.isVideo ||
          widget.entry.location.toLowerCase().contains('video'));

  Future<void> _openPatientProfileFromDetails() async {
    final patientId = widget.entry.patientId?.toString();
    if (patientId == null || patientId.trim().isEmpty) return;

    ref.read(shellProvider.notifier).setTab(3);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      ShellScope.pushNamed(
        context,
        AppRoutes.patientsWithSelection,
        arguments: patientId,
      );
    });
  }

  bool get _isInProgressStatus => _statusUpper == 'IN_PROGRESS';

  bool get _canStartVideoByTimeWindow {
    if (!_isVideoAppointment) return true;
    if (_isInProgressStatus || _isCompletedStatus) return true;

    final doctorTimeZone =
        ref.read(profileAllProvider).valueOrNull?.profile['timeZone']
            as String?;
    final nowInDoctorZone = getNowInTimezone(doctorTimeZone);

    DateTime appointmentStartInDoctorZone;
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
      appointmentStartInDoctorZone = utcToTimezone(asUtc, doctorTimeZone);
    } else {
      appointmentStartInDoctorZone = timeOfDayToDateTimeInZone(
        widget.entry.start,
        widget.day,
        doctorTimeZone,
      );
    }

    final joinAllowedFrom = appointmentStartInDoctorZone.subtract(
      const Duration(minutes: 5),
    );
    return nowInDoctorZone.isAfter(joinAllowedFrom) ||
        nowInDoctorZone.isAtSameMomentAs(joinAllowedFrom);
  }

  void _seedStateFromEntry() {
    _selectedPlace = _isAppointment
        ? (widget.entry.location.toLowerCase().contains('video')
              ? 'Video Consultation'
              : 'Clinic Address')
        : null;
    _initialPlace = _selectedPlace ?? '';
    final seedReason = widget.entry.reason.trim().isEmpty
        ? 'Check Up'
        : widget.entry.reason.trim();
    _reasonCtrl.text = seedReason;
    _initialReason = seedReason;
    _hasUnsavedChanges = false;
  }

  @override
  void initState() {
    super.initState();
    _reasonCtrl = TextEditingController();
    _seedStateFromEntry();
    _reasonCtrl.addListener(_syncDirtyState);
  }

  @override
  void didUpdateWidget(covariant _SlotDetailsPanel oldWidget) {
    super.didUpdateWidget(oldWidget);
    final changed =
        oldWidget.entry.type != widget.entry.type ||
        oldWidget.entry.appointmentId != widget.entry.appointmentId ||
        oldWidget.entry.startAtUtc != widget.entry.startAtUtc ||
        oldWidget.entry.endAtUtc != widget.entry.endAtUtc;
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
    final isDirty =
        (_reasonCtrl.text.trim() != _initialReason) ||
        ((_selectedPlace ?? '').trim() != _initialPlace);
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
    final doctorTimeZone =
        ref.read(profileAllProvider).valueOrNull?.profile['timeZone']
            as String?;
    final nowInDoctorZone = getNowInTimezone(doctorTimeZone);
    return endInstant.isBefore(nowInDoctorZone.toUtc());
  }

  /// True if the selected entry is a free slot on a past *day* (cannot assign patient).
  /// Slots on *today* are allowed (whole day), e.g. at 2 PM you can still assign morning slots on the same day.
  /// CRITICAL: Uses doctor's timezone to determine "today" vs "past day"
  bool get _isPastFreeSlot {
    if (widget.entry.type != EntryType.freeSlot) return false;
    final doctorTimeZone =
        ref.read(profileAllProvider).valueOrNull?.profile['timeZone']
            as String?;
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
                      final doctorTz =
                          ref
                                  .read(profileAllProvider)
                                  .valueOrNull
                                  ?.profile['timeZone']
                              as String?;
                      final todayInDoctorZone = getTodayInTimezone(doctorTz);
                      final picked = await showDatePicker(
                        context: context,
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
      final doctorTimeZone =
          ref.read(profileAllProvider).valueOrNull?.profile['timeZone']
              as String?;
      if (doctorTimeZone == null || doctorTimeZone.trim().isEmpty) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(
                AppLocalizations.of(context)!.translate('failedToChangeSlot') ??
                    'Profile timezone not available.',
              ),
            ),
          );
        }
        return;
      }
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
        invalidateAppointmentRelatedProviders(ref);
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

  Future<List<CalendarEntry>> _loadAvailableSlots(DateTime day) async {
    final tz =
        ref.read(profileAllProvider).valueOrNull?.profile['timeZone']
            as String?;
    if (tz == null || tz.trim().isEmpty) return [];
    try {
      await ref
          .read(calendarProvider.notifier)
          .loadDay(day: day, doctorTimeZone: tz);
      final entries =
          ref.read(calendarProvider)[DateTime(day.year, day.month, day.day)] ??
          [];
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

      final doctorTimeZone =
          ref.read(profileAllProvider).valueOrNull?.profile['timeZone']
              as String?;
      if (doctorTimeZone == null || doctorTimeZone.trim().isEmpty) {
        throw Exception('Profile timezone not available. Please try again.');
      }
      await ref
          .read(calendarProvider.notifier)
          .bookFreeSlotRemote(
            day: widget.day,
            slot: widget.entry,
            patientId: _selectedPatientId!,
            doctorTimeZone: doctorTimeZone,
            location: location,
            reason: reason,
            isVideo: isVideo,
          );

      // Refresh Home's "Today" list and this day in calendar
      invalidateAppointmentRelatedProviders(ref);
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
      final l10n = AppLocalizations.of(context)!;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            '${l10n.translate('failedToAssign') ?? 'Failed to assign'}: $e',
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
      'документация приема',
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
      ref.invalidate(patientDocumentsProvider(patientId));
      final docs = await ref.read(patientDocumentsProvider(patientId).future);
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

      ref.read(shellProvider.notifier).setTab(3);
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted) return;
        ShellScope.pushNamed(
          context,
          AppRoutes.patientsWithSelection,
          arguments: {
            'patientId': patientId,
            'documentId': doc.id,
            'documentTitle': doc.title,
            'openDocumentViewer': true,
          },
        );
      });
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

    if (_isPastAppointment && !_isInProgressStatus && !_isCompletedStatus) {
      return t('appointmentEnded', 'Appointment Ended');
    }
    if (_isCompletedStatus) {
      return t('openSummary', 'Open Summary');
    }
    if (_isInProgressStatus) {
      return t('continueAppointment', 'Continue Appointment');
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
          ? 'Tasdiqlangan'
          : (lang == 'ru' ? 'Подтверждено' : 'Confirmed');
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
              body.trim().isEmpty ? '—' : body,
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
        ? 'Uchrashuv hujjatlari'
        : (lang == 'ru' ? 'Документация приема' : 'Appointment Documentation');
    final notesTabFallback = lang == 'uz'
        ? 'Uchrashuv yozuvlari'
        : (lang == 'ru' ? 'Записи приема' : 'Appointment Notes');
    final summaryPdfTabFallback = lang == 'uz'
        ? 'Xulosa PDF'
        : (lang == 'ru' ? 'PDF сводка' : 'Summary PDF');
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
        : ref.watch(patientDocumentsProvider(patientId));

    // Build Dropdown items for place selection
    final placeOptions = <String>{
      l10n.translate('clinicAddress') ?? 'Clinic Address',
      l10n.videoCall,
    }.where((v) => v.trim().isNotEmpty).toList(growable: false);
    final selectedPlaceValue =
        _selectedPlace != null && placeOptions.contains(_selectedPlace)
        ? _selectedPlace
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
                      '${_fmtDate(context, widget.day)} • ${_fmtTime(widget.entry.start)} - ${_fmtTime(widget.entry.end)}',
                      style: TextStyle(
                        color: subtleText,
                        fontSize: 12,
                        fontWeight: FontWeight.w500,
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
                          debugPrint('Assign patient – load failed: $e');
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
                                  ? '${msg.substring(0, 80)}…'
                                  : msg;
                            }
                            ScaffoldMessenger.of(
                              context,
                            ).showSnackBar(SnackBar(content: Text(display)));
                          }
                          return;
                        }

                        if (list.isEmpty) {
                          if (mounted) {
                            ScaffoldMessenger.of(context).showSnackBar(
                              SnackBar(
                                content: Text(
                                  l10n.translate('noPatientsAvailable') ??
                                      'No patients available',
                                ),
                              ),
                            );
                          }
                          return;
                        }

                        final selectedId = await showModalBottomSheet<String>(
                          context: context,
                          isScrollControlled: true,
                          builder: (ctx) => _AssignPatientSheet(
                            items: list,
                            l10n: AppLocalizations.of(ctx)!,
                          ),
                        );

                        if (selectedId != null && mounted) {
                          final item = list.firstWhere(
                            (p) => p.id == selectedId,
                            orElse: () => list.first,
                          );
                          setState(() {
                            _selectedPatientId = int.tryParse(selectedId);
                            _selectedPatientName = item.name;
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

              const SizedBox(height: 10),

              // Date & Time — tap opens Change Slot dialog for (non-past) appointments
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
                    '${_fmtDate(context, widget.day)}, ${_fmtTime(widget.entry.start)} - ${_fmtTime(widget.entry.end)}',
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

              if (widget.entry.type == EntryType.appointment) ...[
                const SizedBox(height: 10),
                // Place (appointments only – dropdown: doctor's clinic address or VIDEO CONSULTATION)
                _AppointmentPlaceDropdown(entry: widget.entry),
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
                            _isPastAppointment &&
                            !_isInProgressStatus &&
                            !_isCompletedStatus) ||
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
                if (_isPastAppointment)
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
                      AppLocalizations.of(
                            context,
                          )!.translate('videoCallAvailableFiveMinBefore') ??
                          'You can start 5 minutes before the appointment.',
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
                                  final doctorTimeZone =
                                      ref
                                              .read(profileAllProvider)
                                              .valueOrNull
                                              ?.profile['timeZone']
                                          as String?;
                                  if (doctorTimeZone == null ||
                                      doctorTimeZone.trim().isEmpty) {
                                    if (mounted) {
                                      ScaffoldMessenger.of(
                                        context,
                                      ).showSnackBar(
                                        SnackBar(
                                          content: Text(
                                            l10n.translate('failedToCancel') ??
                                                'Profile timezone not available.',
                                          ),
                                        ),
                                      );
                                    }
                                    return;
                                  }
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
                                    invalidateAppointmentRelatedProviders(ref);
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
          ),
        ),
      ],
    );
  }
}

/// Bottom sheet content for assigning a patient to a slot, with search by name or ID.
class _AssignPatientSheet extends StatefulWidget {
  const _AssignPatientSheet({required this.items, required this.l10n});

  final List<PatientAssignmentItem> items;
  final AppLocalizations l10n;

  @override
  State<_AssignPatientSheet> createState() => _AssignPatientSheetState();
}

class _AssignPatientSheetState extends State<_AssignPatientSheet> {
  final TextEditingController _searchController = TextEditingController();
  final FocusNode _searchFocus = FocusNode();

  @override
  void dispose() {
    _searchController.dispose();
    _searchFocus.dispose();
    super.dispose();
  }

  List<PatientAssignmentItem> get _filteredItems {
    final query = _searchController.text.trim().toLowerCase();
    if (query.isEmpty) return widget.items;
    return widget.items.where((p) {
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
              // Search bar – always visible at top
              Container(
                width: double.infinity,
                padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
                color: Theme.of(context).dialogBackgroundColor,
                child: TextField(
                  controller: _searchController,
                  focusNode: _searchFocus,
                  decoration: InputDecoration(
                    hintText: widget.l10n.searchByNameOrId,
                    prefixIcon: const Icon(Icons.search, color: Colors.grey),
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
                            onTap: () => Navigator.pop(context, p.id),
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
