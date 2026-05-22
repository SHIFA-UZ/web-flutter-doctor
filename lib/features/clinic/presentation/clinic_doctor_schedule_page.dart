// lib/features/clinic/presentation/clinic_doctor_schedule_page.dart

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/providers/language_provider.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';
import 'package:shifa_doc_app_v1/features/calendar/domain/calendar_models.dart';
import 'package:shifa_doc_app_v1/features/calendar/presentation/calendar_screen.dart';
import 'package:shifa_doc_app_v1/state/calendar/calendar_controller.dart';
import 'package:shifa_doc_app_v1/core/utils/timezone_utils.dart';

/// Receptionist / owner scheduling for a specific doctor ([doctorProfileId]).
///
/// Runs with an isolated [calendarProvider] override so scheduling does not clash
/// with the main Calendar tab listener or corrupt global cached days.
///
/// Layout mirrors [CalendarScreen]: day entries on the left, month grid or slot
/// details on the right (same [CalendarMonthPanel] / [AnimatedSwitcher] pattern).
class ClinicDoctorScheduleRoute extends StatelessWidget {
  const ClinicDoctorScheduleRoute({
    super.key,
    required this.doctorProfileId,
    required this.doctorDisplayName,
    required this.clinicScheduleTimeZone,
    this.clinicStreetAddress,
  });

  final int doctorProfileId;
  final String doctorDisplayName;
  final String clinicScheduleTimeZone;
  final String? clinicStreetAddress;

  @override
  Widget build(BuildContext context) {
    return ProviderScope(
      overrides: [
        calendarProvider.overrideWith((ref) {
          return CalendarController(
            ref,
            initialResourceDoctorId: doctorProfileId,
          );
        }),
      ],
      child: ClinicDoctorScheduleScaffold(
        doctorDisplayName: doctorDisplayName,
        clinicScheduleTimeZone: clinicScheduleTimeZone,
        clinicStreetAddress: clinicStreetAddress,
      ),
    );
  }
}

class ClinicDoctorScheduleScaffold extends ConsumerStatefulWidget {
  const ClinicDoctorScheduleScaffold({
    super.key,
    required this.doctorDisplayName,
    required this.clinicScheduleTimeZone,
    this.clinicStreetAddress,
  });

  final String doctorDisplayName;
  final String clinicScheduleTimeZone;
  final String? clinicStreetAddress;

  @override
  ConsumerState<ClinicDoctorScheduleScaffold> createState() =>
      _ClinicDoctorScheduleScaffoldState();
}

class _ClinicDoctorScheduleScaffoldState
    extends ConsumerState<ClinicDoctorScheduleScaffold> {
  DateTime _focusedDay = DateTime.now();
  late DateTime _selectedDay;

  CalendarEntry? _selectedEntry;
  bool _loadingDay = false;

  bool _showAppointments = true;
  bool _showFreeSlots = true;

  DateTime _dayKey(DateTime d) =>
      DateTime(d.year, d.month, d.day);

  String _effectiveTz() {
    final t = widget.clinicScheduleTimeZone.trim();
    return t.isEmpty ? 'UTC' : t;
  }

  List<CalendarEntry> _entriesFor(DateTime? day) {
    if (day == null) return [];
    final entries = ref.watch(calendarProvider);
    final allEntries = entries[_dayKey(day)] ?? [];
    return allEntries.where((e) {
      if (e.type == EntryType.appointment) return _showAppointments;
      if (e.type == EntryType.freeSlot) return _showFreeSlots;
      return true;
    }).toList();
  }

  @override
  void initState() {
    super.initState();
    final today = getTodayInTimezone(_effectiveTz());
    _selectedDay = DateTime(today.year, today.month, today.day);
    _focusedDay = _selectedDay;
    WidgetsBinding.instance.addPostFrameCallback((_) => _reloadDay(_selectedDay));
  }

  Future<void> _reloadDay(DateTime day) async {
    setState(() => _loadingDay = true);
    try {
      await ref.read(calendarProvider.notifier).loadDay(
            day: day,
            doctorTimeZone: _effectiveTz(),
            forceRefresh: true,
          );
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('$e')),
        );
      }
    } finally {
      if (mounted) setState(() => _loadingDay = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final brand = Theme.of(context).colorScheme.primary;
    final selected = _selectedDay;
    final dateLabel =
        '${selected.day} ${l10n.monthName(selected.month)} ${selected.year}';

    return Scaffold(
      backgroundColor: const Color(0xFFF5F5F5),
      appBar: AppBar(
        title: Text(widget.doctorDisplayName),
        actions: [
          IconButton(
            icon: const Icon(Icons.close),
            onPressed: () => Navigator.of(context).pop(),
          ),
        ],
      ),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              // ---------- Left: same rhythm as CalendarScreen ----------
              Expanded(
                flex: 3,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      widget.doctorDisplayName,
                      style: Theme.of(context).textTheme.titleMedium?.copyWith(
                            color: Colors.grey.shade700,
                            fontWeight: FontWeight.w600,
                          ),
                    ),
                    const SizedBox(height: 6),
                    Text(
                      l10n.translate('clinicSchedulePreviewHint'),
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(
                            color: Colors.black54,
                          ),
                    ),
                    const SizedBox(height: 16),
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
                        InkWell(
                          onTap: _loadingDay
                              ? null
                              : () async {
                                  final picked = await showDatePicker(
                                    context: context,
                                    locale: localeForMaterialIntl(
                                      Localizations.localeOf(context),
                                    ),
                                    initialDate: selected,
                                    firstDate: DateTime(2020),
                                    lastDate: DateTime(2030),
                                  );
                                  if (picked != null && mounted) {
                                    setState(() {
                                      _selectedDay =
                                          DateTime(picked.year, picked.month, picked.day);
                                      _focusedDay = _selectedDay;
                                      _selectedEntry = null;
                                    });
                                    await _reloadDay(_selectedDay);
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
                              border: Border.all(
                                color: brand.withValues(alpha: 0.4),
                              ),
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
                            padding: EdgeInsets.only(right: 8),
                            child: SizedBox(
                              width: 18,
                              height: 18,
                              child:
                                  CircularProgressIndicator(strokeWidth: 2),
                            ),
                          ),
                        const SizedBox(width: 8),
                        ShifaSecondaryButton(
                          label: l10n.filter,
                          onPressed: _loadingDay
                              ? null
                              : () async {
                                  bool tempShowAppointments =
                                      _showAppointments;
                                  bool tempShowFreeSlots =
                                      _showFreeSlots;

                                  final result = await showDialog<bool>(
                                    context: context,
                                    builder: (ctx) {
                                      final dl = AppLocalizations.of(ctx)!;
                                      return StatefulBuilder(
                                        builder: (context, setDialogState) {
                                          return AlertDialog(
                                            title: Text(dl.filter),
                                            content: Column(
                                              mainAxisSize: MainAxisSize.min,
                                              children: [
                                                CheckboxListTile(
                                                  title: Text(
                                                    dl.translate(
                                                          'showAppointments',
                                                        ) ??
                                                        'Show appointments',
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
                                                    dl.translate(
                                                          'showFreeSlots',
                                                        ) ??
                                                        'Show free slots',
                                                  ),
                                                  value: tempShowFreeSlots,
                                                  onChanged: (val) {
                                                    setDialogState(() {
                                                      tempShowFreeSlots =
                                                          val ?? true;
                                                    });
                                                  },
                                                ),
                                              ],
                                            ),
                                            actions: [
                                              TextButton(
                                                onPressed: () =>
                                                    Navigator.pop(ctx, false),
                                                child: Text(dl.cancel),
                                              ),
                                              ShifaPrimaryButton(
                                                label:
                                                    dl.translate('apply') ??
                                                    'Apply',
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

                                  if (result == true && mounted) {
                                    setState(() {
                                      _showAppointments = tempShowAppointments;
                                      _showFreeSlots = tempShowFreeSlots;
                                    });
                                    await _reloadDay(selected);
                                  }
                                },
                          icon: Icons.tune,
                        ),
                      ],
                    ),
                    const SizedBox(height: 16),
                    Expanded(
                      child: CalendarDayEntriesList(
                        entries: _entriesFor(selected),
                        onTap: (entry) {
                          setState(() => _selectedEntry = entry);
                        },
                        selected: _selectedEntry,
                        brand: brand,
                        loading: _loadingDay,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 24),
              // ---------- Right: calendar or details (matches CalendarScreen) ----------
              Expanded(
                flex: 2,
                child: AnimatedSwitcher(
                  duration: const Duration(milliseconds: 250),
                  switchInCurve: Curves.easeOut,
                  switchOutCurve: Curves.easeIn,
                  child: _selectedEntry == null
                      ? CalendarMonthPanel(
                          key: ValueKey(
                            'clinic_cal_${selected.year}_${selected.month}_${selected.day}',
                          ),
                          focusedDay: _focusedDay,
                          selectedDay: selected,
                          onChanged: (d) async {
                            setState(() {
                              _selectedDay =
                                  DateTime(d.year, d.month, d.day);
                              _focusedDay = _selectedDay;
                              _selectedEntry = null;
                            });
                            await _reloadDay(_selectedDay);
                          },
                          onFocusedDayChanged: (d) {
                            setState(() => _focusedDay = d);
                          },
                          showUpdateCard: false,
                          onGoToSchedule: null,
                        )
                      : CalendarSlotDetailsPanel(
                          key: ValueKey(
                            '${_selectedEntry!.type}_${_selectedEntry!.start}_${selected.millisecondsSinceEpoch}',
                          ),
                          entry: _selectedEntry!,
                          day: selected,
                          scheduleTimeZone: _effectiveTz(),
                          primaryClinicVenueLabel: widget.clinicStreetAddress,
                          onSavedSuccessfully: () async {
                            await _reloadDay(selected);
                            if (mounted) {
                              setState(() => _selectedEntry = null);
                            }
                          },
                          onClose: () =>
                              setState(() => _selectedEntry = null),
                        ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
