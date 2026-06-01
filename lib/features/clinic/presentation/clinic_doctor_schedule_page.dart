// lib/features/clinic/presentation/clinic_doctor_schedule_page.dart

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:shifa_doc_app_v1/core/layout/responsive.dart';
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
/// Layout mirrors [CalendarScreen]: on mobile, month grid above day entries;
/// slot details full-screen when selected. Desktop keeps day entries left,
/// month grid or details on the right.
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
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _reloadDay(_selectedDay);
      _loadMonth(_focusedDay);
    });
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

  Future<void> _loadMonth(DateTime month) async {
    try {
      await ref.read(calendarProvider.notifier).loadMonth(
            month: month,
            doctorTimeZone: _effectiveTz(),
          );
    } catch (_) {}
  }

  Future<void> _showFilterDialog() async {
    final l10n = AppLocalizations.of(context)!;
    bool tempShowAppointments = _showAppointments;
    bool tempShowFreeSlots = _showFreeSlots;

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
                    title: Text(dl.translate('showAppointments')),
                    value: tempShowAppointments,
                    onChanged: (val) {
                      setDialogState(() {
                        tempShowAppointments = val ?? true;
                      });
                    },
                  ),
                  CheckboxListTile(
                    title: Text(dl.translate('showFreeSlots')),
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
                  onPressed: () => Navigator.pop(ctx, false),
                  child: Text(dl.cancel),
                ),
                ShifaPrimaryButton(
                  label: dl.translate('apply'),
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
      });
      await _reloadDay(_selectedDay);
    }
  }

  Future<void> _pickDate() async {
    if (_loadingDay) return;
    final picked = await showDatePicker(
      context: context,
      locale: localeForMaterialIntl(Localizations.localeOf(context)),
      initialDate: _selectedDay,
      firstDate: DateTime(2020),
      lastDate: DateTime(2030),
    );
    if (picked != null && mounted) {
      setState(() {
        _selectedDay = DateTime(picked.year, picked.month, picked.day);
        _focusedDay = _selectedDay;
        _selectedEntry = null;
      });
      await _reloadDay(_selectedDay);
    }
  }

  Widget _buildMonthPanel({required bool compact}) {
    final selected = _selectedDay;
    return CalendarMonthPanel(
      compact: compact,
      key: ValueKey(
        'clinic_cal_${selected.year}_${selected.month}_${selected.day}',
      ),
      focusedDay: _focusedDay,
      selectedDay: selected,
      onChanged: (d) async {
        setState(() {
          _selectedDay = DateTime(d.year, d.month, d.day);
          _focusedDay = _selectedDay;
          _selectedEntry = null;
        });
        await _reloadDay(_selectedDay);
      },
      onFocusedDayChanged: (d) {
        setState(() => _focusedDay = d);
        _loadMonth(d);
      },
      showUpdateCard: false,
      onGoToSchedule: null,
    );
  }

  Widget _buildEntriesList(Color brand) {
    return CalendarDayEntriesList(
      entries: _entriesFor(_selectedDay),
      onTap: (entry) => setState(() => _selectedEntry = entry),
      selected: _selectedEntry,
      brand: brand,
      loading: _loadingDay,
    );
  }

  Widget _buildSlotDetailsPanel() {
    final selected = _selectedDay;
    return CalendarSlotDetailsPanel(
      key: ValueKey(
        '${_selectedEntry!.type}_${_selectedEntry!.start}_${selected.millisecondsSinceEpoch}',
      ),
      entry: _selectedEntry!,
      day: selected,
      scheduleTimeZone: _effectiveTz(),
      primaryClinicVenueLabel: widget.clinicStreetAddress,
      onSavedSuccessfully: () async {
        await _reloadDay(selected);
        if (mounted) setState(() => _selectedEntry = null);
      },
      onClose: () => setState(() => _selectedEntry = null),
    );
  }

  Widget _buildDoctorHint(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          widget.doctorDisplayName,
          style: Theme.of(context).textTheme.titleMedium?.copyWith(
                color: Colors.grey.shade700,
                fontWeight: FontWeight.w600,
              ),
          maxLines: 2,
          overflow: TextOverflow.ellipsis,
        ),
        const SizedBox(height: 6),
        Text(
          l10n.translate('clinicSchedulePreviewHint'),
          style: Theme.of(context).textTheme.bodySmall?.copyWith(
                color: Colors.black54,
              ),
        ),
      ],
    );
  }

  Widget _buildHeaderRow(
    BuildContext context,
    AppLocalizations l10n,
    Color brand, {
    required bool isMobile,
  }) {
    final selected = _selectedDay;
    final dateLabel =
        '${selected.day} ${l10n.monthName(selected.month)} ${selected.year}';

    final filterControl = isMobile
        ? IconButton.filledTonal(
            onPressed: _loadingDay ? null : _showFilterDialog,
            icon: const Icon(Icons.tune),
            tooltip: l10n.filter,
          )
        : ShifaSecondaryButton(
            label: l10n.filter,
            onPressed: _loadingDay ? null : _showFilterDialog,
            icon: Icons.tune,
          );

    final loadingIndicator = _loadingDay
        ? const Padding(
            padding: EdgeInsets.only(right: 8),
            child: SizedBox(
              width: 18,
              height: 18,
              child: CircularProgressIndicator(strokeWidth: 2),
            ),
          )
        : null;

    if (isMobile) {
      return Row(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(l10n.calendar, style: Responsive.pageTitleStyle(context)),
                const SizedBox(height: 2),
                Text(
                  dateLabel,
                  style: Responsive.pageSubtitleStyle(context),
                  overflow: TextOverflow.ellipsis,
                ),
              ],
            ),
          ),
          if (loadingIndicator != null) loadingIndicator,
          filterControl,
        ],
      );
    }

    return Row(
      children: [
        Text(l10n.calendar, style: Responsive.pageTitleStyle(context)),
        const SizedBox(width: 12),
        InkWell(
          onTap: _loadingDay ? null : _pickDate,
          borderRadius: BorderRadius.circular(20),
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(20),
              border: Border.all(color: brand.withValues(alpha: 0.4)),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Flexible(
                  child: Text(
                    dateLabel,
                    style: TextStyle(
                      color: brand,
                      fontWeight: FontWeight.w600,
                    ),
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
                const SizedBox(width: 4),
                Icon(Icons.calendar_today, size: 16, color: brand),
              ],
            ),
          ),
        ),
        const Spacer(),
        if (loadingIndicator != null) loadingIndicator,
        const SizedBox(width: 8),
        filterControl,
      ],
    );
  }

  Widget _buildMobileBody(
    BuildContext context,
    AppLocalizations l10n,
    Color brand,
  ) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _buildDoctorHint(context),
        SizedBox(height: Responsive.sectionGap(context)),
        _buildHeaderRow(context, l10n, brand, isMobile: true),
        SizedBox(height: Responsive.sectionGap(context)),
        _buildMonthPanel(compact: true),
        SizedBox(height: Responsive.sectionGap(context)),
        Expanded(child: _buildEntriesList(brand)),
      ],
    );
  }

  Widget _buildDesktopBody(
    BuildContext context,
    AppLocalizations l10n,
    Color brand,
  ) {
    final selected = _selectedDay;
    return Row(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Expanded(
          flex: 3,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _buildDoctorHint(context),
              const SizedBox(height: 16),
              _buildHeaderRow(context, l10n, brand, isMobile: false),
              const SizedBox(height: 16),
              Expanded(child: _buildEntriesList(brand)),
            ],
          ),
        ),
        const SizedBox(width: 24),
        Expanded(
          flex: 2,
          child: AnimatedSwitcher(
            duration: const Duration(milliseconds: 250),
            switchInCurve: Curves.easeOut,
            switchOutCurve: Curves.easeIn,
            child: _selectedEntry == null
                ? _buildMonthPanel(compact: false)
                : _buildSlotDetailsPanel(),
          ),
        ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final brand = Theme.of(context).colorScheme.primary;
    final isMobile = Responsive.isMobile(context);

    return Scaffold(
      backgroundColor: const Color(0xFFF5F5F5),
      appBar: AppBar(
        title: Text(
          widget.doctorDisplayName,
          overflow: TextOverflow.ellipsis,
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.close),
            onPressed: () => Navigator.of(context).pop(),
          ),
        ],
      ),
      body: SafeArea(
        child: Padding(
          padding: Responsive.screenPadding(context),
          child: isMobile && _selectedEntry != null
              ? _buildSlotDetailsPanel()
              : isMobile
                  ? _buildMobileBody(context, l10n, brand)
                  : _buildDesktopBody(context, l10n, brand),
        ),
      ),
    );
  }
}
