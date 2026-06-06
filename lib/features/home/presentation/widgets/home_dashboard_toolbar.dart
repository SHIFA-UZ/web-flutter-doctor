import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:shifa_doc_app_v1/core/layout/responsive.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/providers/language_provider.dart';
import 'package:shifa_doc_app_v1/core/theme/app_colors.dart';
import 'package:shifa_doc_app_v1/core/theme/app_design_system.dart';
import 'package:shifa_doc_app_v1/features/home/application/home_dashboard_date_range_provider.dart';

/// Shared date-range picker + export button for Home and Reports screens.
class HomeDashboardToolbar extends ConsumerWidget {
  const HomeDashboardToolbar({
    super.key,
    required this.onExport,
    this.exporting = false,
  });

  final VoidCallback onExport;
  final bool exporting;

  Future<void> _pickRange(BuildContext context, WidgetRef ref) async {
    final l10n = AppLocalizations.of(context)!;
    final current = ref.read(homeDashboardDateRangeProvider);
    final picked = await showDateRangePicker(
      context: context,
      firstDate: DateTime.now().subtract(const Duration(days: 365)),
      lastDate: DateTime.now().add(const Duration(days: 30)),
      initialDateRange: DateTimeRange(start: current.start, end: current.end),
      helpText: l10n.translate('selectDateRange'),
    );
    if (picked != null) {
      ref
          .read(homeDashboardDateRangeProvider.notifier)
          .setRange(picked.start, picked.end);
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context)!;
    final brand = Theme.of(context).colorScheme.primary;
    final range = ref.watch(homeDashboardDateRangeProvider);
    final locale = ref.watch(languageProvider).locale.toString();
    final rangeLabel = formatDashboardDateRange(range, locale);

    if (Responsive.isMobile(context)) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          _DateRangeButton(label: rangeLabel, onTap: () => _pickRange(context, ref)),
          const SizedBox(height: 8),
          _ExportButton(exporting: exporting, onExport: onExport, brand: brand, l10n: l10n),
        ],
      );
    }

    // Shrink-wrap: this toolbar is often placed inside a parent Row with
    // mainAxisSize.min (e.g. HomeGreetingHeader). Spacer/Expanded need bounded width.
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        _DateRangeButton(label: rangeLabel, onTap: () => _pickRange(context, ref)),
        const SizedBox(width: 12),
        _ExportButton(exporting: exporting, onExport: onExport, brand: brand, l10n: l10n),
      ],
    );
  }
}

class _DateRangeButton extends StatelessWidget {
  const _DateRangeButton({required this.label, required this.onTap});

  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return OutlinedButton.icon(
      onPressed: onTap,
      icon: const Icon(Icons.calendar_month_outlined, size: 18),
      label: Text(label),
      style: OutlinedButton.styleFrom(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
        side: const BorderSide(color: AppDesignSystem.border),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      ),
    );
  }
}

class _ExportButton extends StatelessWidget {
  const _ExportButton({
    required this.exporting,
    required this.onExport,
    required this.brand,
    required this.l10n,
  });

  final bool exporting;
  final VoidCallback onExport;
  final Color brand;
  final AppLocalizations l10n;

  @override
  Widget build(BuildContext context) {
    return FilledButton.icon(
      onPressed: exporting ? null : onExport,
      icon: exporting
          ? SizedBox(
              width: 16,
              height: 16,
              child: CircularProgressIndicator(strokeWidth: 2, color: brand),
            )
          : const Icon(Icons.download_outlined, size: 18),
      label: Text(l10n.translate('export')),
      style: FilledButton.styleFrom(
        backgroundColor: brand,
        foregroundColor: Colors.white,
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      ),
    );
  }
}
