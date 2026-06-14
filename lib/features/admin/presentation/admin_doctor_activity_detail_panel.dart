// lib/features/admin/presentation/admin_doctor_activity_detail_panel.dart
import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/core/api/api_providers.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';
import 'package:shifa_doc_app_v1/state/admin/admin_actions.dart';
import 'package:shifa_doc_app_v1/features/admin/domain/admin_models.dart';
import 'package:shifa_doc_app_v1/state/admin/admin_providers.dart';

class AdminDoctorActivityDetailPanel extends ConsumerStatefulWidget {
  final int doctorId;
  final String? fromIso;
  final String? toIso;
  /// Matches parent list refresh; detail loads only after Refresh was clicked.
  final int refreshToken;
  final VoidCallback onClose;
  final Future<void> Function(AdminDoctorActivityRow row)? onDownloadContract;
  final bool contractPdfLoading;

  const AdminDoctorActivityDetailPanel({
    super.key,
    required this.doctorId,
    required this.fromIso,
    required this.toIso,
    required this.refreshToken,
    required this.onClose,
    this.onDownloadContract,
    this.contractPdfLoading = false,
  });

  @override
  ConsumerState<AdminDoctorActivityDetailPanel> createState() =>
      _AdminDoctorActivityDetailPanelState();
}

class _AdminDoctorActivityDetailPanelState extends ConsumerState<AdminDoctorActivityDetailPanel> {
  bool _loading = false;
  Object? _error;
  AdminDoctorActivityDetail? _detail;
  int _loadedForToken = 0;

  @override
  void initState() {
    super.initState();
    _maybeLoadDetail();
  }

  @override
  void didUpdateWidget(covariant AdminDoctorActivityDetailPanel oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.refreshToken != oldWidget.refreshToken ||
        widget.doctorId != oldWidget.doctorId) {
      _maybeLoadDetail();
    }
  }

  Future<void> _maybeLoadDetail() async {
    if (widget.refreshToken <= 0 || _loading) return;
    if (_loadedForToken == widget.refreshToken &&
        _detail != null &&
        _detail!.row.doctorId == widget.doctorId) {
      return;
    }

    setState(() {
      _loading = true;
      _error = null;
    });

    try {
      final detail = await ref.read(adminActionsProvider).getDoctorActivityDetail(
            doctorId: widget.doctorId,
            fromIso: widget.fromIso,
            toIso: widget.toIso,
          );
      if (!mounted) return;
      setState(() {
        _detail = detail;
        _loading = false;
        _loadedForToken = widget.refreshToken;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e;
        _loading = false;
      });
    }
  }

  Widget _pill(String t) {
    return Chip(
      padding: const EdgeInsets.symmetric(horizontal: 8),
      visualDensity: VisualDensity.compact,
      label: Text(t, style: const TextStyle(fontSize: 12)),
    );
  }

  Widget _trend(BuildContext context, String title, List<AdminDoctorActivityDailyPoint> pts) {
    final maxVal = pts.isEmpty
        ? 1.0
        : pts
            .map((e) => e.count.toDouble())
            .reduce((a, b) => a > b ? a : b)
            .clamp(1.0, double.infinity);
    final color = Theme.of(context).colorScheme.primary;
    return Padding(
      padding: const EdgeInsets.only(bottom: 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(title, style: Theme.of(context).textTheme.titleSmall),
          const SizedBox(height: 6),
          SizedBox(
            height: 132,
            child: pts.isEmpty
                ? Center(
                    child: Text('No data', style: TextStyle(color: Colors.grey.shade600, fontSize: 13)),
                  )
                : LineChart(
                    LineChartData(
                      titlesData: const FlTitlesData(show: false),
                      borderData: FlBorderData(show: false),
                      gridData: FlGridData(
                        show: true,
                        drawVerticalLine: false,
                        horizontalInterval: maxVal > 5 ? (maxVal / 4).ceilToDouble() : 1,
                        getDrawingHorizontalLine: (_) => FlLine(color: Colors.grey.shade200, strokeWidth: 1),
                      ),
                      lineBarsData: [
                        LineChartBarData(
                          spots: List.generate(pts.length, (i) => FlSpot(i.toDouble(), pts[i].count.toDouble())),
                          isCurved: true,
                          color: color,
                          barWidth: 2,
                          dotData: const FlDotData(show: false),
                        ),
                      ],
                      minY: 0,
                      maxY: maxVal * 1.15,
                    ),
                  ),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    if (widget.refreshToken <= 0) {
      return SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(
                children: [
                  const Expanded(child: Text('Doctor detail')),
                  IconButton(icon: const Icon(Icons.close), onPressed: widget.onClose),
                ],
              ),
              const SizedBox(height: 24),
              Text(
                'Click Refresh on the list to load activity data.',
                style: TextStyle(color: Colors.grey.shade700, fontSize: 14),
              ),
            ],
          ),
        ),
      );
    }

    if (_loading && _detail == null) {
      return const SafeArea(child: Center(child: CircularProgressIndicator()));
    }
    if (_error != null && _detail == null) {
      return SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Text('Detail error: $_error'),
        ),
      );
    }

    final detail = _detail;
    if (detail == null) {
      return const SafeArea(child: Center(child: CircularProgressIndicator()));
    }

    final row = detail.row;
    final series = detail.dailySeries;

    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                Expanded(child: Text(row.doctorName, style: Theme.of(context).textTheme.titleMedium)),
                IconButton(icon: const Icon(Icons.close), onPressed: widget.onClose),
              ],
            ),
            Text(row.clinicName ?? '—', style: TextStyle(color: Colors.grey.shade700)),
            if (row.earlyPartnerContractNumber != null) ...[
              const SizedBox(height: 6),
              Text(
                'Contract: ${row.earlyPartnerContractNumber}',
                style: TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                  color: Colors.grey.shade800,
                ),
              ),
            ],
            const SizedBox(height: 12),
            _AdminSmsRemindersToggle(
              doctorId: widget.doctorId,
              initialAllowed: row.smsRemindersAllowed,
              smsSentCount: row.smsSentCount,
              smsOwedMinor: row.smsOwedMinor,
              smsCurrency: row.smsCurrency,
              pricePerSmsMinor: row.smsPricePerUnitMinor,
              onChanged: () => _maybeLoadDetail(),
            ),
            if (widget.onDownloadContract != null) ...[
              const SizedBox(height: 12),
              ShifaSecondaryButton(
                label: widget.contractPdfLoading ? 'Generating PDF…' : 'Partnership contract PDF',
                onPressed: widget.contractPdfLoading ? null : () => widget.onDownloadContract!(row),
              ),
            ],
            const SizedBox(height: 12),
            Wrap(
              spacing: 6,
              runSpacing: 6,
              children: [
                _pill('Booked ${row.appointmentsBooked}'),
                _pill('Completed ${row.appointmentsCompleted}'),
                _pill('AI req ${row.aiRequests}'),
                _pill('Video ${row.videoAppointments}'),
              ],
            ),
            const Divider(height: 24),
            Expanded(
              child: ListView(
                padding: EdgeInsets.zero,
                children: [
                  _trend(context, 'Appointments / day', series['appointments'] ?? const []),
                  _trend(context, 'Completed / day', series['completed'] ?? const []),
                  _trend(context, 'AI requests / day', series['aiRequests'] ?? const []),
                  _trend(context, 'Documents', series['documents'] ?? const []),
                  _trend(context, 'Treatment plans', series['treatmentPlans'] ?? const []),
                  _trend(context, 'Remote tasks', series['remoteTasks'] ?? const []),
                  _trend(context, 'Consultation notes', series['consultationNotes'] ?? const []),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _AdminSmsRemindersToggle extends ConsumerStatefulWidget {
  const _AdminSmsRemindersToggle({
    required this.doctorId,
    required this.initialAllowed,
    required this.smsSentCount,
    required this.smsOwedMinor,
    required this.smsCurrency,
    required this.pricePerSmsMinor,
    required this.onChanged,
  });

  final int doctorId;
  final bool initialAllowed;
  final int smsSentCount;
  final int smsOwedMinor;
  final String smsCurrency;
  final int pricePerSmsMinor;
  final VoidCallback onChanged;

  @override
  ConsumerState<_AdminSmsRemindersToggle> createState() =>
      _AdminSmsRemindersToggleState();
}

class _AdminSmsRemindersToggleState extends ConsumerState<_AdminSmsRemindersToggle> {
  late bool _allowed;
  bool _saving = false;

  @override
  void initState() {
    super.initState();
    _allowed = widget.initialAllowed;
  }

  @override
  void didUpdateWidget(covariant _AdminSmsRemindersToggle oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.initialAllowed != widget.initialAllowed) {
      _allowed = widget.initialAllowed;
    }
  }

  Future<void> _onChanged(bool value) async {
    setState(() {
      _saving = true;
      _allowed = value;
    });
    try {
      await AdminActions(apiClient: ref.read(apiClientProvider))
          .setDoctorSmsRemindersAllowed(doctorId: widget.doctorId, allowed: value);
      widget.onChanged();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(value ? 'SMS reminders enabled for doctor' : 'SMS reminders disabled for doctor')),
        );
      }
    } catch (e) {
      if (mounted) {
        setState(() => _allowed = !value);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed: $e'), backgroundColor: Colors.red),
        );
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: Colors.grey.shade50,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: Colors.grey.shade300),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'SMS appointment reminders',
            style: Theme.of(context).textTheme.titleSmall,
          ),
          const SizedBox(height: 4),
          Text(
            'Allow this doctor to enable 24h SMS reminders for patients. '
            'Billed at ${widget.pricePerSmsMinor} ${widget.smsCurrency} per SMS.',
            style: TextStyle(fontSize: 12, color: Colors.grey.shade700),
          ),
          const SizedBox(height: 4),
          Text(
            'Period: ${widget.smsSentCount} sent · ${widget.smsOwedMinor} ${widget.smsCurrency} owed',
            style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600),
          ),
          SwitchListTile(
            contentPadding: EdgeInsets.zero,
            title: const Text('Allow SMS reminders', style: TextStyle(fontSize: 13)),
            value: _allowed,
            onChanged: _saving ? null : _onChanged,
          ),
          if (_saving) const LinearProgressIndicator(minHeight: 2),
        ],
      ),
    );
  }
}
