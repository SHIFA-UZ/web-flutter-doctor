// lib/features/schedule/presentation/setup_schedule_screen.dart
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:shifa_doc_app_v1/core/api/api_providers.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/utils/timezone_utils.dart';
import 'package:shifa_doc_app_v1/features/schedule/presentation/doctor_locations_screen.dart';
import 'package:shifa_doc_app_v1/state/locations/doctor_location_actions.dart';
import 'package:shifa_doc_app_v1/state/locations/doctor_location_models.dart';
import 'package:shifa_doc_app_v1/state/profile/profile_providers.dart';
import 'package:shifa_doc_app_v1/state/schedule/schedule_controller.dart';
import 'package:shifa_doc_app_v1/state/schedule/schedule_models.dart';
import 'package:shifa_doc_app_v1/state/schedule/schedule_actions.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';
import 'package:shifa_doc_app_v1/core/theme/app_colors.dart';

/// ScheduleScreen lets a doctor define weekly rules + validity end date
/// and persists them to the backend.
class ScheduleScreen extends ConsumerStatefulWidget {
  const ScheduleScreen({Key? key}) : super(key: key);

  @override
  ConsumerState<ScheduleScreen> createState() => _ScheduleScreenState();
}

class _ScheduleScreenState extends ConsumerState<ScheduleScreen> {
  // Display days in UI (must include Sunday to match backend 1..7)
  // These are the English keys used internally; we'll translate them in the UI
  static const List<String> _daysOfWeek = <String>[
    'Monday',
    'Tuesday',
    'Wednesday',
    'Thursday',
    'Friday',
    'Saturday',
    'Sunday',
  ];

  // Track expand/collapse per day card
  final Map<String, bool> _expanded = {for (final d in _daysOfWeek) d: false};

  bool _loading = true;
  bool _saving = false;
  List<DateSpecificRuleDto> _dateSpecificRules = [];
  List<ValidityPeriodDto> _existingValidityPeriods = [];
  bool _expandSectionOpen = false;
  DateTime? _existingValidFrom;
  DateTime? _existingValidUntil;

  // Multi-location support
  List<DoctorLocationDto> _locations = const [];
  int? _selectedLocationId;

  @override
  void initState() {
    super.initState();
    // Prefill from backend when the screen opens
    Future.microtask(_loadFromBackend);
  }

  Future<void> _loadFromBackend() async {
    setState(() => _loading = true);
    final api = ref.read(apiClientProvider);

    try {
      // 0) Load the doctor's practice locations first. The selected location (if any)
      // scopes schedule rules and date-specific expansions so the doctor can edit
      // each location's calendar independently.
      try {
        final locs = await fetchDoctorLocations(ref);
        if (mounted) {
          setState(() {
            _locations = locs;
            // Keep previous selection if still valid; otherwise pick primary / first.
            if (_selectedLocationId != null &&
                locs.any((l) => l.id == _selectedLocationId)) {
              // already valid
            } else if (locs.isNotEmpty) {
              final primary = locs.firstWhere(
                (l) => l.isPrimary,
                orElse: () => locs.first,
              );
              _selectedLocationId = primary.id;
            } else {
              _selectedLocationId = null;
            }
          });
        }
      } catch (_) {
        if (mounted) setState(() => _locations = const []);
      }

      // 1) Load existing rules (optionally filtered by the selected location).
      final rulesPath = _selectedLocationId != null
          ? '/api/schedule/rules?locationId=${_selectedLocationId}'
          : '/api/schedule/rules';
      final rulesRes = await api.get(rulesPath);
      if (rulesRes.statusCode == 200) {
        final list = (jsonDecode(utf8.decode(rulesRes.bodyBytes)) as List)
            .cast<Map<String, dynamic>>();

        // Expect RuleDto in your models
        final rules = list.map(RuleDto.fromJson).toList();
        ref.read(scheduleProvider.notifier).replaceWithBackendRules(rules);
      } else if (rulesRes.statusCode == 401) {
        final l10n = AppLocalizations.of(context)!;
        throw Exception(l10n.unauthorizedPleaseLoginAgain);
      } else {
        debugPrint('Load rules failed: ${rulesRes.statusCode} ${rulesRes.body}');
        final l10n = AppLocalizations.of(context)!;
        throw Exception(l10n.failedToLoadRules);
      }

      // 2) Load validity periods (multiple allowed) and optional profile range for initial form
      try {
        final periods = await fetchValidityPeriods(ref);
        if (mounted) setState(() => _existingValidityPeriods = periods);
      } catch (_) {
        // Optional: if backend does not support yet or error, keep empty
        if (mounted) setState(() => _existingValidityPeriods = []);
      }

      final meRes = await api.get('/api/doctors/me');
      if (meRes.statusCode == 200) {
        final obj =
            jsonDecode(utf8.decode(meRes.bodyBytes)) as Map<String, dynamic>;
        final profile = (obj['profile'] as Map<String, dynamic>?);
        final validFrom = profile?['scheduleValidFrom'] as String?;
        final validUntil = profile?['scheduleValidUntil'] as String?;

        // Prefill form with effective range (bounding box when multiple periods)
        if (validFrom != null && validFrom.isNotEmpty) {
          final dt = DateTime.tryParse(validFrom);
          if (dt != null) {
            _existingValidFrom = DateTime(dt.year, dt.month, dt.day);
            ref
                .read(scheduleProvider.notifier)
                .setStartDate(_existingValidFrom!);
          }
        }
        if (validUntil != null && validUntil.isNotEmpty) {
          final dt = DateTime.tryParse(validUntil);
          if (dt != null) {
            _existingValidUntil = DateTime(dt.year, dt.month, dt.day);
            ref
                .read(scheduleProvider.notifier)
                .setEndDate(_existingValidUntil!);
          }
        }
      }

      // 3) Load date-specific schedule rules (expansions) for the selected location.
      final expanded =
          await fetchDateSpecificRules(ref, locationId: _selectedLocationId);
      if (mounted) setState(() => _dateSpecificRules = expanded);
    } catch (e) {
      debugPrint('Schedule load error: $e');
      if (!mounted) return;
      final l10n = AppLocalizations.of(context)!;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(l10n.somethingWentWrong)));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  // ---------- UI helpers ----------
  Future<TimeOfDay?> _pickTime(TimeOfDay initial) {
    return showTimePicker(
      context: context,
      initialTime: initial,
      builder: (context, child) {
        // Enforce 24h format
        return MediaQuery(
          data: MediaQuery.of(context).copyWith(alwaysUse24HourFormat: true),
          child: child!,
        );
      },
    );
  }

  Future<Duration?> _pickDuration([Duration? initial]) async {
    final options = <Duration>[
      const Duration(minutes: 10),
      const Duration(minutes: 15),
      const Duration(minutes: 20),
      const Duration(minutes: 30),
      const Duration(minutes: 45),
      const Duration(hours: 1),
    ];
    return showModalBottomSheet<Duration>(
      context: context,
      builder: (ctx) => SafeArea(
        child: ListView.separated(
          shrinkWrap: true,
          itemCount: options.length,
          separatorBuilder: (_, __) => const Divider(height: 1),
          itemBuilder: (context, i) {
            final l10n = AppLocalizations.of(context)!;
            return ListTile(
              title: Text('${options[i].inMinutes} ${l10n.minutes}'),
              onTap: () => Navigator.pop(context, options[i]),
            );
          },
        ),
      ),
    );
  }

  bool _endAfterStart(TimeOfDay s, TimeOfDay e) {
    final sm = s.hour * 60 + s.minute;
    final em = e.hour * 60 + e.minute;
    return em > sm;
  }

  // Avoid overlapping time ranges within a day
  bool _overlaps(TimeOfDay aS, TimeOfDay aE, TimeOfDay bS, TimeOfDay bE) {
    final a0 = aS.hour * 60 + aS.minute;
    final a1 = aE.hour * 60 + aE.minute;
    final b0 = bS.hour * 60 + bS.minute;
    final b1 = bE.hour * 60 + bE.minute;
    return (a0 < b1) && (b0 < a1);
  }

  String _fmt(TimeOfDay t) =>
      '${t.hour.toString().padLeft(2, '0')}:${t.minute.toString().padLeft(2, '0')}';

  // ---------- Actions ----------
  void _toggleDay(String day, bool enable) {
    ref.read(scheduleProvider.notifier).setDayEnabled(day, enable);
    setState(() => _expanded[day] = enable); // expand when enabling
  }

  Future<void> _addSlot(String day) async {
    final notifier = ref.read(scheduleProvider.notifier);
    final state = ref.read(scheduleProvider);

    final start =
        await _pickTime(const TimeOfDay(hour: 8, minute: 0)) ??
        const TimeOfDay(hour: 8, minute: 0);
    final end =
        await _pickTime(const TimeOfDay(hour: 17, minute: 0)) ??
        const TimeOfDay(hour: 17, minute: 0);

    if (!_endAfterStart(start, end)) {
      final l10n = AppLocalizations.of(context)!;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(l10n.endTimeMustBeAfterStartTime)),
      );
      return;
    }

    // overlap guard
    final conflict = (state.slots[day] ?? const <TimeSlot>[]).any(
      (s) => _overlaps(s.start, s.end, start, end),
    );
    if (conflict) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(AppLocalizations.of(context)!.thisTimeOverlapsExistingSlot)),
      );
      return;
    }

    final duration =
        await _pickDuration(const Duration(minutes: 30)) ??
        const Duration(minutes: 30);

    notifier.addSlot(
      day,
      TimeSlot(start: start, end: end, slotDuration: duration),
    );
  }

  Future<void> _editSlot(String day, int index, TimeSlot slot) async {
    final notifier = ref.read(scheduleProvider.notifier);
    final state = ref.read(scheduleProvider);

    final newStart = await _pickTime(slot.start) ?? slot.start;
    final newEnd = await _pickTime(slot.end) ?? slot.end;

    if (!_endAfterStart(newStart, newEnd)) {
      final l10n = AppLocalizations.of(context)!;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(l10n.endTimeMustBeAfterStartTime)),
      );
      return;
    }

    // overlap guard against other slots of the same day
    final others = List<TimeSlot>.from(state.slots[day] ?? const <TimeSlot>[]);
    if (index >= 0 && index < others.length) {
      others.removeAt(index);
    }
    final conflict = others.any(
      (s) => _overlaps(s.start, s.end, newStart, newEnd),
    );
    if (conflict) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(AppLocalizations.of(context)!.thisTimeOverlapsExistingSlot)),
      );
      return;
    }

    final newDuration =
        await _pickDuration(slot.slotDuration) ?? slot.slotDuration;

    notifier.updateSlot(
      day,
      index,
      TimeSlot(start: newStart, end: newEnd, slotDuration: newDuration),
    );
  }

  void _removeSlot(String day, int index) {
    ref.read(scheduleProvider.notifier).removeSlot(day, index);
  }

  Future<void> _pickStartDate() async {
    final state = ref.read(scheduleProvider);
    final doctorTz =
        ref.read(profileAllProvider).valueOrNull?.profile['timeZone'] as String?;
    final today = getTodayInTimezone(doctorTz);
    final initial = state.startDate ?? today;
    final picked = await showDatePicker(
      context: context,
      initialDate: initial,
      firstDate: today,
      lastDate: state.endDate,
      helpText: AppLocalizations.of(context)!.selectScheduleStartDate,
    );
    if (picked != null) {
      ref
          .read(scheduleProvider.notifier)
          .setStartDate(DateTime(picked.year, picked.month, picked.day));
    }
  }

  Future<void> _pickEndDate() async {
    final state = ref.read(scheduleProvider);
    final doctorTz =
        ref.read(profileAllProvider).valueOrNull?.profile['timeZone'] as String?;
    final today = getTodayInTimezone(doctorTz);
    final minDate = state.startDate ?? today;
    final picked = await showDatePicker(
      context: context,
      initialDate: state.endDate.isAfter(minDate) ? state.endDate : minDate,
      firstDate: minDate,
      lastDate: today.add(const Duration(days: 365)),
      helpText: AppLocalizations.of(context)!.selectScheduleEndDate,
    );
    if (picked != null) {
      ref
          .read(scheduleProvider.notifier)
          .setEndDate(DateTime(picked.year, picked.month, picked.day));
    }
  }

  Future<void> _addExpansion() async {
    final l10n = AppLocalizations.of(context)!;
    final doctorTz =
        ref.read(profileAllProvider).valueOrNull?.profile['timeZone'] as String?;
    final today = getTodayInTimezone(doctorTz);
    final from = await showDatePicker(
      context: context,
      initialDate: today,
      firstDate: today,
      lastDate: today.add(const Duration(days: 365)),
      helpText: l10n.fromDate,
    );
    if (from == null || !mounted) return;
    final to = await showDatePicker(
      context: context,
      initialDate: from.isBefore(today) ? today : from,
      firstDate: from,
      lastDate: from.add(const Duration(days: 365)),
      helpText: l10n.toDate,
    );
    if (to == null || !mounted) return;
    if (to.isBefore(from)) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(l10n.toDate + ' must be on or after ' + l10n.fromDate)),
      );
      return;
    }
    final startT = await _pickTime(const TimeOfDay(hour: 17, minute: 0));
    if (startT == null || !mounted) return;
    final endDefault = startT.hour < 23
        ? TimeOfDay(hour: startT.hour + 1, minute: startT.minute)
        : const TimeOfDay(hour: 23, minute: 59);
    final endT = await _pickTime(endDefault);
    if (endT == null || !mounted) return;
    if (!_endAfterStart(startT, endT)) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(l10n.endTimeMustBeAfterStartTime)),
      );
      return;
    }
    final dur = await _pickDuration(const Duration(minutes: 30));
    if (dur == null || !mounted) return;
    try {
      await createDateSpecificRule(
        ref,
        startDate: '${from.year}-${from.month.toString().padLeft(2, '0')}-${from.day.toString().padLeft(2, '0')}',
        endDate: '${to.year}-${to.month.toString().padLeft(2, '0')}-${to.day.toString().padLeft(2, '0')}',
        startTime: '${startT.hour.toString().padLeft(2, '0')}:${startT.minute.toString().padLeft(2, '0')}',
        endTime: '${endT.hour.toString().padLeft(2, '0')}:${endT.minute.toString().padLeft(2, '0')}',
        slotMinutes: dur.inMinutes,
        locationId: _selectedLocationId,
      );
      if (!mounted) return;
      final list =
          await fetchDateSpecificRules(ref, locationId: _selectedLocationId);
      if (mounted) setState(() => _dateSpecificRules = list);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(l10n.expansionAdded)),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('${e.toString().replaceFirst('Exception: ', '')}')),
      );
    }
  }

  Future<void> _deleteDateSpecificRule(int id) async {
    try {
      await deleteDateSpecificRule(ref, id);
      if (!mounted) return;
      final list =
          await fetchDateSpecificRules(ref, locationId: _selectedLocationId);
      if (mounted) setState(() => _dateSpecificRules = list);
    } catch (e) {
      debugPrint('Delete date-specific rule error: $e');
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(AppLocalizations.of(context)!.somethingWentWrong)),
      );
    }
  }

  void _copyFromPreviousDay(String day) {
    final ok = ref.read(scheduleProvider.notifier).copyFromPreviousDay(day);
    final l10n = AppLocalizations.of(context)!;
    if (!ok) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            l10n.translate('noPreviousDayScheduleToCopy') ??
                'Previous day has no schedule to copy.',
          ),
        ),
      );
    } else {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            l10n.translate('scheduleCopiedFromPreviousDay') ??
                'Schedule copied from previous day.',
          ),
          duration: const Duration(seconds: 2),
        ),
      );
    }
  }

  Future<void> _copyFromOtherDay(String targetDay) async {
    final schedule = ref.read(scheduleProvider);
    final slotsByDay = schedule.slots;
    final sources = _daysOfWeek
        .where((d) => d != targetDay && (slotsByDay[d]?.isNotEmpty ?? false))
        .toList();
    final l10n = AppLocalizations.of(context)!;
    if (sources.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            l10n.translate('noSourceDaysToCopyFrom') ??
                'No other days have schedule to copy from.',
          ),
        ),
      );
      return;
    }

    final selected = await showDialog<String>(
      context: context,
      builder: (ctx) {
        final loc = AppLocalizations.of(ctx)!;
        return SimpleDialog(
          title: Text(
            loc.translate('copyScheduleFromDay') ??
                'Copy schedule from which day?',
          ),
          children: sources
              .map(
                (d) => SimpleDialogOption(
                  onPressed: () => Navigator.pop(ctx, d),
                  child: Text(_DayCard._translateDay(d, ctx)),
                ),
              )
              .toList(),
        );
      },
    );

    if (selected == null) return;

    final ok =
        ref.read(scheduleProvider.notifier).copyFromDay(selected, targetDay);
    if (!ok) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            l10n.translate('failedToCopySchedule') ??
                'Failed to copy schedule from selected day.',
          ),
        ),
      );
    } else {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            (l10n.translate('scheduleCopiedFromDay') ??
                    'Schedule copied from {day}.')
                .replaceFirst('{day}', _DayCard._translateDay(selected, context)),
          ),
          duration: const Duration(seconds: 2),
        ),
      );
    }
  }

  Future<void> _onLocationChanged(int? newId) async {
    if (newId == _selectedLocationId) return;
    setState(() => _selectedLocationId = newId);
    await _loadFromBackend();
  }

  Future<void> _openLocationsScreen() async {
    await Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => const DoctorLocationsScreen(),
      ),
    );
    // After editing locations, reload so the selector reflects add/remove/primary.
    if (mounted) await _loadFromBackend();
  }

  DoctorLocationDto? _effectiveCurrentLocation() {
    if (_locations.isEmpty) return null;
    if (_selectedLocationId != null) {
      for (final location in _locations) {
        if (location.id == _selectedLocationId) return location;
      }
    }
    if (_locations.length == 1) return _locations.first;
    for (final location in _locations) {
      if (location.isPrimary) return location;
    }
    return _locations.first;
  }

  Future<void> _save() async {
    final schedule = ref.read(scheduleProvider);
    final l10n = AppLocalizations.of(context)!;
    
    // Frontend validation: New range must not overlap existing (no replacement allowed)
    // Allowed: new entirely before existing (new end <= existing start) OR new entirely after existing (new start >= existing end)
    if (_existingValidFrom != null && _existingValidUntil != null) {
      final newStart = schedule.startDate ?? DateTime.now();
      final newEnd = schedule.endDate;
      final existingStart = _existingValidFrom!;
      final existingEnd = _existingValidUntil!;

      final newEndDate = DateTime(newEnd.year, newEnd.month, newEnd.day);
      final existingStartDate = DateTime(existingStart.year, existingStart.month, existingStart.day);
      final existingEndDate = DateTime(existingEnd.year, existingEnd.month, existingEnd.day);
      final newStartDate = DateTime(newStart.year, newStart.month, newStart.day);

      final entirelyBefore = !newEndDate.isAfter(existingStartDate);
      final entirelyAfter = !newStartDate.isBefore(existingEndDate);

      if (!entirelyBefore && !entirelyAfter) {
        if (!mounted) return;
        final existingRangeStr = '${existingStart.year}-'
            '${existingStart.month.toString().padLeft(2, '0')}-'
            '${existingStart.day.toString().padLeft(2, '0')} - '
            '${existingEnd.year}-'
            '${existingEnd.month.toString().padLeft(2, '0')}-'
            '${existingEnd.day.toString().padLeft(2, '0')}';
        final firstAllowedAfter = existingEnd.add(const Duration(days: 1));
        final l10nSafe = AppLocalizations.of(context)!;
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              '${l10nSafe.scheduleOverlapsExisting} '
              '${l10nSafe.existingSchedule}: $existingRangeStr. '
              '${l10nSafe.newScheduleMustBeBeforeOrAfter} '
              '${l10nSafe.before} ${existingStart.year}-${existingStart.month.toString().padLeft(2, '0')}-${existingStart.day.toString().padLeft(2, '0')}, '
              '${l10nSafe.orAfter} ${firstAllowedAfter.year}-${firstAllowedAfter.month.toString().padLeft(2, '0')}-${firstAllowedAfter.day.toString().padLeft(2, '0')}.',
            ),
            backgroundColor: Colors.red,
            duration: const Duration(seconds: 6),
          ),
        );
        return;
      }
    }
    
    setState(() => _saving = true);
    try {
      await saveScheduleToBackend(ref, locationId: _selectedLocationId);
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(l10n.scheduleSaved)));
      Navigator.pop(context);
    } catch (e) {
      debugPrint('Save schedule error: $e');
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(
        SnackBar(
          content: Text(l10n.somethingWentWrong),
          backgroundColor: Colors.red,
          duration: const Duration(seconds: 6),
        ),
      );
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final brand = Theme.of(context).colorScheme.primary;
    final schedule = ref.watch(scheduleProvider); // ScheduleState
    final slotsByDay = schedule.slots; // Map<String, List<TimeSlot>>
    final l10n = AppLocalizations.of(context)!;
    final currentLocation = _effectiveCurrentLocation();

    return Scaffold(
      backgroundColor: Colors.white,
      appBar: AppBar(
        title: Text(l10n.setupYourSchedule),
        backgroundColor: Colors.transparent,
        elevation: 0,
        foregroundColor: Colors.black,
        actions: [
          IconButton(
            tooltip: l10n.translate('manageLocations') ?? 'Manage locations',
            icon: const Icon(Icons.place_outlined),
            onPressed: _openLocationsScreen,
          ),
        ],
      ),
      body: SafeArea(
        child: _loading
            ? const Center(child: CircularProgressIndicator())
            : Padding(
                padding: const EdgeInsets.all(24),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.center,
                  children: [
                    Text(
                      l10n.selectWorkingDaysAndDefineSlots,
                      style: TextStyle(
                        fontSize: 14,
                        color: Colors.grey.shade700,
                      ),
                      textAlign: TextAlign.center,
                    ),
                    const SizedBox(height: 16),
                    if (currentLocation != null) ...[
                      _CurrentLocationBadge(location: currentLocation),
                      const SizedBox(height: 16),
                    ],

                    // Location selector — hidden for doctors with 0/1 location.
                    if (_locations.length > 1) ...[
                      _LocationSelector(
                        locations: _locations,
                        selectedId: _selectedLocationId,
                        onChanged: _onLocationChanged,
                        onManage: _openLocationsScreen,
                      ),
                      const SizedBox(height: 16),
                    ] else if (_locations.isEmpty) ...[
                      _NoLocationsHint(onManage: _openLocationsScreen),
                      const SizedBox(height: 16),
                    ],

                    // Existing calendar periods (multiple allowed)
                    if (_existingValidityPeriods.isNotEmpty) ...[
                      Align(
                        alignment: Alignment.centerLeft,
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              l10n.existingCalendarPeriods,
                              style: const TextStyle(
                                fontWeight: FontWeight.w600,
                                fontSize: 14,
                              ),
                            ),
                            const SizedBox(height: 6),
                            Wrap(
                              spacing: 8,
                              runSpacing: 4,
                              children: _existingValidityPeriods.map((p) {
                                return Chip(
                                  label: Text(
                                    '${p.validFrom} – ${p.validUntil}',
                                    style: const TextStyle(fontSize: 12),
                                  ),
                                  materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
                                );
                              }).toList(),
                            ),
                            const SizedBox(height: 6),
                            Text(
                              l10n.newPeriodMustNotOverlap,
                              style: TextStyle(
                                fontSize: 12,
                                color: Colors.grey.shade600,
                                fontStyle: FontStyle.italic,
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(height: 12),
                    ],

                    // Schedule validity: add new period (from when — until when)
                    Align(
                      alignment: Alignment.centerLeft,
                      child: Wrap(
                        crossAxisAlignment: WrapCrossAlignment.center,
                        spacing: 8,
                        runSpacing: 8,
                        children: [
                          Text(
                            _existingValidityPeriods.isEmpty
                                ? l10n.scheduleValidFrom
                                : l10n.scheduleValidFromNew,
                            style: const TextStyle(fontWeight: FontWeight.w600),
                          ),
                          ShifaSecondaryButton(
                            label: schedule.startDate != null
                                ? '${schedule.startDate!.year}-'
                                  '${schedule.startDate!.month.toString().padLeft(2, '0')}-'
                                  '${schedule.startDate!.day.toString().padLeft(2, '0')}'
                                : l10n.selectDate,
                            onPressed: _pickStartDate,
                          ),
                          const SizedBox(width: 16),
                          Text(
                            l10n.scheduleValidUntil,
                            style: const TextStyle(fontWeight: FontWeight.w600),
                          ),
                          ShifaSecondaryButton(
                            label: '${schedule.endDate.year}-'
                              '${schedule.endDate.month.toString().padLeft(2, '0')}-'
                              '${schedule.endDate.day.toString().padLeft(2, '0')}',
                            onPressed: _pickEndDate,
                          ),
                        ],
                      ),
                    ),

                    const SizedBox(height: 20),

                    // Expand schedule for specific dates
                    _ExpandScheduleSection(
                      brand: brand,
                      dateSpecificRules: _dateSpecificRules,
                      isExpanded: _expandSectionOpen,
                      onToggle: () => setState(() => _expandSectionOpen = !_expandSectionOpen),
                      onAddExpansion: _addExpansion,
                      onDeleteRule: _deleteDateSpecificRule,
                    ),
                    const SizedBox(height: 20),

                    // Days grid with editors
                    Expanded(
                      child: SingleChildScrollView(
                        child: LayoutBuilder(
                          builder: (context, constraints) {
                            const cross = 2;
                            const spacing = 16.0;
                            final tileWidth =
                                (constraints.maxWidth - spacing * (cross - 1)) /
                                cross;
                            return Wrap(
                              spacing: spacing,
                              runSpacing: spacing,
                              children: _daysOfWeek.map((day) {
                                final isSelected = slotsByDay.containsKey(day);
                                final isExpanded = _expanded[day] ?? false;
                                final slots =
                                    slotsByDay[day] ?? const <TimeSlot>[];
                                return SizedBox(
                                  width: tileWidth,
                                  child: _DayCard(
                                    day: day,
                                    isSelected: isSelected,
                                    isExpanded: isExpanded,
                                    slots: slots,
                                    brand: brand,
                                    onToggle: (val) => _toggleDay(day, val),
                                    onAdd: () => _addSlot(day),
                                    onEdit: (i, s) => _editSlot(day, i, s),
                                    onDelete: (i) => _removeSlot(day, i),
                                    onExpand: () => setState(
                                      () => _expanded[day] = !isExpanded,
                                    ),
                                    onCopyFromPrevious: () => _copyFromPreviousDay(day),
                                    onCopyFromOther: () => _copyFromOtherDay(day),
                                  ),
                                );
                              }).toList(),
                            );
                          },
                        ),
                      ),
                    ),

                    const SizedBox(height: 12),

                    // Save
                    ShifaPrimaryButton(
                      label: l10n.complete,
                      onPressed: _saving ? null : _save,
                      width: ButtonWidth.fill,
                      isLoading: _saving,
                    ),
                  ],
                ),
              ),
      ),
    );
  }
}

// ====================== Location selector ======================

class _LocationSelector extends StatelessWidget {
  const _LocationSelector({
    required this.locations,
    required this.selectedId,
    required this.onChanged,
    required this.onManage,
  });

  final List<DoctorLocationDto> locations;
  final int? selectedId;
  final ValueChanged<int?> onChanged;
  final VoidCallback onManage;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        color: Colors.grey.shade50,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.grey.shade300),
      ),
      child: Row(
        children: [
          const Icon(Icons.place_outlined, size: 20),
          const SizedBox(width: 8),
          Expanded(
            child: DropdownButtonHideUnderline(
              child: DropdownButton<int>(
                isExpanded: true,
                value: selectedId,
                hint: Text(l10n.translate('selectLocation') ?? 'Select location'),
                items: locations
                    .map(
                      (l) => DropdownMenuItem<int>(
                        value: l.id,
                        child: Text(
                          l.isPrimary ? '${l.label} · ★' : l.label,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                    )
                    .toList(),
                onChanged: onChanged,
              ),
            ),
          ),
          TextButton.icon(
            onPressed: onManage,
            icon: const Icon(Icons.settings_outlined, size: 18),
            label: Text(l10n.translate('manage') ?? 'Manage'),
          ),
        ],
      ),
    );
  }
}

class _CurrentLocationBadge extends StatelessWidget {
  const _CurrentLocationBadge({required this.location});

  final DoctorLocationDto location;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        color: Colors.blue.shade50,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.blue.shade100),
      ),
      child: Row(
        children: [
          const Icon(Icons.location_on_outlined, size: 18),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              '${l10n.translate('selectedLocation') ?? 'Current location'}: ${location.label}',
              style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600),
              overflow: TextOverflow.ellipsis,
            ),
          ),
          if (location.isPrimary)
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
              decoration: BoxDecoration(
                color: Colors.blue.shade100,
                borderRadius: BorderRadius.circular(999),
              ),
              child: Text(
                l10n.translate('primary') ?? 'Primary',
                style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w600),
              ),
            ),
        ],
      ),
    );
  }
}

class _NoLocationsHint extends StatelessWidget {
  const _NoLocationsHint({required this.onManage});

  final VoidCallback onManage;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: Colors.amber.shade50,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.amber.shade200),
      ),
      child: Row(
        children: [
          const Icon(Icons.info_outline, color: Colors.orange),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              l10n.translate('addFirstLocationHint') ??
                  'Add at least one practice location to organize your schedule.',
              style: const TextStyle(fontSize: 13),
            ),
          ),
          TextButton(
            onPressed: onManage,
            child: Text(l10n.translate('addLocation') ?? 'Add location'),
          ),
        ],
      ),
    );
  }
}

// ====================== Expand schedule section ======================

class _ExpandScheduleSection extends StatelessWidget {
  const _ExpandScheduleSection({
    required this.brand,
    required this.dateSpecificRules,
    required this.isExpanded,
    required this.onToggle,
    required this.onAddExpansion,
    required this.onDeleteRule,
  });

  final Color brand;
  final List<DateSpecificRuleDto> dateSpecificRules;
  final bool isExpanded;
  final VoidCallback onToggle;
  final VoidCallback onAddExpansion;
  final void Function(int id) onDeleteRule;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return Container(
      decoration: BoxDecoration(
        color: Colors.grey.shade100,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: brand.withOpacity(0.5)),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          InkWell(
            borderRadius: BorderRadius.circular(12),
            onTap: onToggle,
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
              child: Row(
                children: [
                  Icon(Icons.date_range, color: brand, size: 22),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Text(
                      l10n.expandScheduleForDates,
                      style: const TextStyle(
                        fontSize: 15,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                  Icon(
                    isExpanded ? Icons.keyboard_arrow_up : Icons.keyboard_arrow_down,
                    color: Colors.grey.shade600,
                  ),
                ],
              ),
            ),
          ),
          if (isExpanded)
            Padding(
              padding: const EdgeInsets.fromLTRB(12, 0, 12, 12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    l10n.expandScheduleHint,
                    style: TextStyle(fontSize: 13, color: Colors.grey.shade700),
                  ),
                  const SizedBox(height: 12),
                  ShifaSecondaryButton(
                    label: l10n.addExpansion,
                    onPressed: onAddExpansion,
                    icon: Icons.add,
                    width: ButtonWidth.fill,
                  ),
                  if (dateSpecificRules.isNotEmpty) ...[
                    const SizedBox(height: 12),
                    Text(
                      l10n.timePeriod,
                      style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600),
                    ),
                    const SizedBox(height: 8),
                    ListView.separated(
                      shrinkWrap: true,
                      physics: const NeverScrollableScrollPhysics(),
                      itemCount: dateSpecificRules.length,
                      separatorBuilder: (_, __) => const SizedBox(height: 8),
                      itemBuilder: (context, i) {
                        final r = dateSpecificRules[i];
                        return Card(
                          elevation: 0,
                          margin: EdgeInsets.zero,
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(8),
                            side: BorderSide(color: Colors.grey.shade300),
                          ),
                          child: Padding(
                            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                            child: Row(
                              children: [
                                Expanded(
                                  child: Text(
                                    '${r.startDate} – ${r.endDate} · ${r.startTime}–${r.endTime} (${r.slotMinutes} ${l10n.minutes})',
                                    style: const TextStyle(fontSize: 13),
                                  ),
                                ),
                                IconButton(
                                  icon: const Icon(Icons.delete_outline, color: AppColors.destructiveRed, size: 20),
                                  onPressed: r.id != null ? () => onDeleteRule(r.id!) : null,
                                ),
                              ],
                            ),
                          ),
                        );
                      },
                    ),
                  ] else
                    Padding(
                      padding: const EdgeInsets.only(top: 8),
                      child: Text(
                        l10n.noDateSpecificRules,
                        style: TextStyle(fontSize: 13, color: Colors.grey.shade600),
                      ),
                    ),
                ],
              ),
            ),
        ],
      ),
    );
  }
}

// ====================== Day Card ======================

class _DayCard extends StatelessWidget {
  const _DayCard({
    required this.day,
    required this.isSelected,
    required this.isExpanded,
    required this.slots,
    required this.brand,
    required this.onToggle,
    required this.onAdd,
    required this.onEdit,
    required this.onDelete,
    required this.onExpand,
    required this.onCopyFromPrevious,
    required this.onCopyFromOther,
  });

  final String day;
  final bool isSelected;
  final bool isExpanded;
  final List<TimeSlot> slots;
  final Color brand;

  final ValueChanged<bool> onToggle;
  final VoidCallback onAdd;
  final void Function(int, TimeSlot) onEdit;
  final void Function(int) onDelete;
  final VoidCallback onExpand;
   final VoidCallback onCopyFromPrevious;
   final VoidCallback onCopyFromOther;

  String _fmt(TimeOfDay t) =>
      '${t.hour.toString().padLeft(2, '0')}:${t.minute.toString().padLeft(2, '0')}';

  static String _translateDay(String day, BuildContext context) {
    final l10n = AppLocalizations.of(context);
    if (l10n == null) return day;
    switch (day) {
      case 'Monday': return l10n.monday;
      case 'Tuesday': return l10n.tuesday;
      case 'Wednesday': return l10n.wednesday;
      case 'Thursday': return l10n.thursday;
      case 'Friday': return l10n.friday;
      case 'Saturday': return l10n.saturday;
      case 'Sunday': return l10n.sunday;
      default: return day;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: Colors.grey.shade100,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: isSelected ? brand : Colors.transparent,
          width: 2,
        ),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          InkWell(
            borderRadius: BorderRadius.circular(10),
            onTap: () => onToggle(!isSelected),
            child: Container(
              height: 48,
              padding: const EdgeInsets.symmetric(horizontal: 12),
              child: Row(
                children: [
                  Checkbox(
                    value: isSelected,
                    onChanged: (val) => onToggle(val ?? false),
                    activeColor: brand,
                  ),
                  Builder(
                    builder: (context) => Text(
                      _DayCard._translateDay(day, context),
                      style: const TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                  ),
                  const Spacer(),
                  if (isSelected)
                    PopupMenuButton<String>(
                      icon: Icon(Icons.more_vert, color: Colors.grey.shade600),
                      onSelected: (value) {
                        switch (value) {
                          case 'prev':
                            onCopyFromPrevious();
                            break;
                          case 'other':
                            onCopyFromOther();
                            break;
                        }
                      },
                      itemBuilder: (ctx) {
                        final l10n = AppLocalizations.of(ctx)!;
                        return [
                          PopupMenuItem(
                            value: 'prev',
                            child: Text(l10n.translate('copyFromPreviousDay') ?? 'Copy from previous day'),
                          ),
                          PopupMenuItem(
                            value: 'other',
                            child: Text(l10n.translate('copyFromAnotherDay') ?? 'Copy from another day'),
                          ),
                        ];
                      },
                    ),
                  IconButton(
                    icon: Icon(
                      isExpanded
                          ? Icons.keyboard_arrow_up
                          : Icons.keyboard_arrow_down,
                      color: Colors.grey.shade600,
                    ),
                    onPressed: isSelected ? onExpand : null,
                  ),
                ],
              ),
            ),
          ),
          if (isSelected && isExpanded)
            Padding(
              padding: const EdgeInsets.fromLTRB(12, 8, 12, 12),
              child: _SlotEditorInline(
                day: day,
                slots: slots,
                onAdd: onAdd,
                onEdit: onEdit,
                onDelete: onDelete,
                brand: brand,
              ),
            ),
        ],
      ),
    );
  }
}

// ====================== Inline slot editor ======================

class _SlotEditorInline extends StatelessWidget {
  const _SlotEditorInline({
    required this.day,
    required this.slots,
    required this.onAdd,
    required this.onEdit,
    required this.onDelete,
    required this.brand,
  });

  final String day;
  final List<TimeSlot> slots;
  final VoidCallback onAdd;
  final void Function(int, TimeSlot) onEdit;
  final void Function(int) onDelete;
  final Color brand;

  String _fmt(TimeOfDay t) =>
      '${t.hour.toString().padLeft(2, '0')}:${t.minute.toString().padLeft(2, '0')}';

  String _translateDay(String day, BuildContext context) {
    final l10n = AppLocalizations.of(context);
    if (l10n == null) return day;
    switch (day) {
      case 'Monday': return l10n.monday;
      case 'Tuesday': return l10n.tuesday;
      case 'Wednesday': return l10n.wednesday;
      case 'Thursday': return l10n.thursday;
      case 'Friday': return l10n.friday;
      case 'Saturday': return l10n.saturday;
      case 'Sunday': return l10n.sunday;
      default: return day;
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return Container(
      decoration: BoxDecoration(
        color: Colors.grey.shade50,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: brand.withOpacity(0.3)),
      ),
      padding: const EdgeInsets.all(12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            '${_translateDay(day, context)} ${l10n.daySlots}',
            style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600),
          ),
          const SizedBox(height: 8),

          // Slots list
          SizedBox(
            height: 220,
            child: slots.isEmpty
                ? Center(child: Text(l10n.noSlotsYet))
                : ListView.separated(
                    itemCount: slots.length,
                    separatorBuilder: (_, __) => const SizedBox(height: 12),
                    itemBuilder: (context, index) {
                      final slot = slots[index];
                      return Card(
                        elevation: 0,
                        margin: EdgeInsets.zero,
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(8),
                          side: BorderSide(color: Colors.grey.shade300),
                        ),
                        child: Padding(
                          padding: const EdgeInsets.all(12),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                l10n.timePeriod,
                                style: const TextStyle(
                                  fontSize: 12,
                                  fontWeight: FontWeight.w500,
                                ),
                              ),
                              const SizedBox(height: 8),
                              InkWell(
                                borderRadius: BorderRadius.circular(8),
                                onTap: () => onEdit(index, slot),
                                child: Container(
                                  padding: const EdgeInsets.symmetric(
                                    horizontal: 12,
                                    vertical: 12,
                                  ),
                                  decoration: BoxDecoration(
                                    color: Colors.white,
                                    borderRadius: BorderRadius.circular(8),
                                    border: Border.all(
                                      color: Colors.grey.shade300,
                                    ),
                                  ),
                                  child: Row(
                                    mainAxisAlignment:
                                        MainAxisAlignment.spaceBetween,
                                    children: [
                                      Text(
                                        '${_fmt(slot.start)} - ${_fmt(slot.end)}',
                                      ),
                                      const Icon(Icons.edit, size: 18),
                                    ],
                                  ),
                                ),
                              ),
                              const SizedBox(height: 12),

                              Text(
                                l10n.slotTimeframe,
                                style: const TextStyle(
                                  fontSize: 12,
                                  fontWeight: FontWeight.w500,
                                ),
                              ),
                              const SizedBox(height: 8),
                              InkWell(
                                borderRadius: BorderRadius.circular(8),
                                onTap: () => onEdit(index, slot),
                                child: Container(
                                  padding: const EdgeInsets.symmetric(
                                    horizontal: 12,
                                    vertical: 12,
                                  ),
                                  decoration: BoxDecoration(
                                    color: Colors.white,
                                    borderRadius: BorderRadius.circular(8),
                                    border: Border.all(
                                      color: Colors.grey.shade300,
                                    ),
                                  ),
                                  child: Row(
                                    mainAxisAlignment:
                                        MainAxisAlignment.spaceBetween,
                                    children: [
                                      Text(
                                        '${slot.slotDuration.inMinutes} ${l10n.minutes}',
                                      ),
                                      const Icon(Icons.edit, size: 18),
                                    ],
                                  ),
                                ),
                              ),
                              const SizedBox(height: 8),

                              Align(
                                alignment: Alignment.centerRight,
                                child: TextButton.icon(
                                  onPressed: () => onDelete(index),
                                  icon: const Icon(
                                    Icons.delete_outline,
                                    color: AppColors.destructiveRed,
                                  ),
                                  label: Text(
                                    l10n.remove,
                                    style: const TextStyle(color: AppColors.destructiveRed),
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ),
                      );
                    },
                  ),
          ),

          const SizedBox(height: 8),

          // Add slot
          ShifaSecondaryButton(
            label: l10n.add,
            onPressed: onAdd,
            icon: Icons.add,
            width: ButtonWidth.fill,
          ),
        ],
      ),
    );
  }
}
