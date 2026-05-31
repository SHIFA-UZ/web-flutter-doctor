import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/theme/app_design_system.dart';
import 'package:shifa_doc_app_v1/features/home/application/home_analytics_providers.dart';

/// SMS reminder usage and cost for the selected reports date range.
class AnalyticsSmsUsageWidget extends ConsumerWidget {
  const AnalyticsSmsUsageWidget({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context)!;
    final async = ref.watch(doctorSmsUsageProvider);

    return async.when(
      loading: () => const Padding(
        padding: EdgeInsets.symmetric(vertical: 8),
        child: LinearProgressIndicator(minHeight: 2),
      ),
      error: (e, _) => Text(
        '${l10n.error}: $e',
        style: TextStyle(color: Colors.red.shade700, fontSize: 13),
      ),
      data: (usage) {
        if (!usage.smsRemindersAllowed && usage.sentCount == 0) {
          return const SizedBox.shrink();
        }
        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              l10n.translate('reportsSmsTitle') ?? 'SMS reminders',
              style: AppDesignSystem.h2(context),
            ),
            const SizedBox(height: 8),
            Row(
              children: [
                Expanded(
                  child: _StatTile(
                    label: l10n.translate('reportsSmsSent') ?? 'SMS sent',
                    value: '${usage.sentCount}',
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: _StatTile(
                    label: l10n.translate('reportsSmsSpent') ?? 'Total cost',
                    value: '${usage.totalCostMinor} ${usage.currency}',
                  ),
                ),
              ],
            ),
            const SizedBox(height: 6),
            Text(
              (l10n.translate('reportsSmsRateHint') ??
                      '{{price}} {{currency}} per SMS')
                  .replaceAll('{{price}}', '${usage.pricePerSmsMinor}')
                  .replaceAll('{{currency}}', usage.currency),
              style: AppDesignSystem.caption(context),
            ),
            if (!usage.smsRemindersAllowed) ...[
              const SizedBox(height: 8),
              Text(
                l10n.translate('reportsSmsNotAllowed') ??
                    'SMS reminders are not enabled for your account. Contact support.',
                style: TextStyle(fontSize: 12, color: Colors.orange.shade800),
              ),
            ],
          ],
        );
      },
    );
  }
}

class _StatTile extends StatelessWidget {
  const _StatTile({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: Colors.grey.shade50,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: Colors.grey.shade200),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: AppDesignSystem.caption(context)),
          const SizedBox(height: 4),
          Text(value, style: AppDesignSystem.h2(context)),
        ],
      ),
    );
  }
}
