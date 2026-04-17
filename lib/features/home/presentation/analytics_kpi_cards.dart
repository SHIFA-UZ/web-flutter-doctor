// KPI cards for home screen: appointments today, completed, cancelled, new patients.

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/features/home/application/home_analytics_providers.dart';

class AnalyticsKpiCards extends ConsumerWidget {
  const AnalyticsKpiCards({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(doctorAnalyticsOverviewProvider);
    final l10n = AppLocalizations.of(context)!;
    final brand = Theme.of(context).colorScheme.primary;

    return async.when(
      loading: () => _KpiSkeleton(l10n: l10n),
      error: (e, _) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 8),
        child: Text(
          l10n.error,
          style: TextStyle(color: Colors.grey.shade600, fontSize: 13),
        ),
      ),
      data: (overview) => LayoutBuilder(
        builder: (context, constraints) {
          final isNarrow = constraints.maxWidth < 500;
          return Wrap(
            spacing: 12,
            runSpacing: 12,
            children: [
              _KpiCard(
                title: l10n.appointmentsToday,
                value: overview.appointmentsToday.toString(),
                icon: Icons.calendar_today,
                color: brand,
              ),
              _KpiCard(
                title: l10n.completedToday,
                value: overview.completedToday.toString(),
                icon: Icons.check_circle_outline,
                color: Colors.green.shade700,
              ),
              _KpiCard(
                title: l10n.cancelledToday,
                value: overview.cancelledToday.toString(),
                icon: Icons.cancel_outlined,
                color: Colors.orange.shade700,
              ),
              _KpiCard(
                title: l10n.newPatientsToday,
                value: overview.newPatientsToday.toString(),
                icon: Icons.person_add_outlined,
                color: Colors.indigo.shade700,
              ),
            ],
          );
        },
      ),
    );
  }
}

class _KpiCard extends StatelessWidget {
  final String title;
  final String value;
  final IconData icon;
  final Color color;

  const _KpiCard({
    required this.title,
    required this.value,
    required this.icon,
    required this.color,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.05),
            blurRadius: 8,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 22, color: color),
          const SizedBox(width: 10),
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                value,
                style: const TextStyle(
                  fontSize: 18,
                  fontWeight: FontWeight.bold,
                ),
              ),
              Text(
                title,
                style: TextStyle(
                  fontSize: 11,
                  color: Colors.grey.shade600,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _KpiSkeleton extends StatelessWidget {
  final AppLocalizations l10n;

  const _KpiSkeleton({required this.l10n});

  @override
  Widget build(BuildContext context) {
    return Wrap(
      spacing: 12,
      runSpacing: 12,
      children: List.generate(
        4,
        (_) => Container(
          width: 140,
          height: 56,
          decoration: BoxDecoration(
            color: Colors.grey.shade200,
            borderRadius: BorderRadius.circular(12),
          ),
        ),
      ),
    );
  }
}
