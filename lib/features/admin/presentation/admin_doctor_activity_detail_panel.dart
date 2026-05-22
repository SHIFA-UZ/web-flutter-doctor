// lib/features/admin/presentation/admin_doctor_activity_detail_panel.dart
import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/features/admin/domain/admin_models.dart';
import 'package:shifa_doc_app_v1/state/admin/admin_provider_params.dart';
import 'package:shifa_doc_app_v1/state/admin/admin_providers.dart';

class AdminDoctorActivityDetailPanel extends ConsumerWidget {
  final int doctorId;
  final String? fromIso;
  final String? toIso;
  final VoidCallback onClose;

  const AdminDoctorActivityDetailPanel({
    super.key,
    required this.doctorId,
    required this.fromIso,
    required this.toIso,
    required this.onClose,
  });

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
  Widget build(BuildContext context, WidgetRef ref) {
    final params = DoctorActivityDetailParams(doctorId: doctorId, fromIso: fromIso, toIso: toIso);
    final async = ref.watch(adminDoctorActivityDetailProvider(params));

    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: async.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (e, _) => Text('Detail error: $e'),
          data: (detail) {
            final row = detail.row;
            final series = detail.dailySeries;
            return Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Row(
                  children: [
                    Expanded(child: Text(row.doctorName, style: Theme.of(context).textTheme.titleMedium)),
                    IconButton(icon: const Icon(Icons.close), onPressed: onClose),
                  ],
                ),
                Text(row.clinicName ?? '—', style: TextStyle(color: Colors.grey.shade700)),
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
            );
          },
        ),
      ),
    );
  }
}
