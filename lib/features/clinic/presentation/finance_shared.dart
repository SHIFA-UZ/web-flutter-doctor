import 'package:flutter/material.dart';

import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/layout/responsive.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_models.dart';

class FinanceKpiCard extends StatelessWidget {
  final String title;
  final String? subtitle;
  final String value;
  final Color color;
  final bool selected;
  final VoidCallback? onTap;

  const FinanceKpiCard({
    required this.title,
    this.subtitle,
    required this.value,
    required this.color,
    this.selected = false,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final borderColor =
        selected ? color : color.withValues(alpha: 0.2);
    final backgroundColor = selected
        ? color.withValues(alpha: 0.16)
        : color.withValues(alpha: 0.08);
    final compact = Responsive.useCompactToolbar(context);
    final screenW = MediaQuery.sizeOf(context).width;
    // Two cards per row on phones; fixed desktop tile otherwise.
    final cardWidth = compact
        ? ((screenW - 48) / 2).clamp(140.0, 200.0)
        : 200.0;

    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(12),
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 180),
          width: cardWidth,
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: backgroundColor,
            borderRadius: BorderRadius.circular(12),
            border: Border.all(
              color: borderColor,
              width: selected ? 2 : 1,
            ),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                title,
                style: TextStyle(
                  fontSize: 12,
                  color: Colors.grey.shade700,
                  fontWeight: FontWeight.w500,
                ),
              ),
              if (subtitle != null && subtitle!.isNotEmpty) ...[
                const SizedBox(height: 2),
                Text(
                  subtitle!,
                  style: TextStyle(
                    fontSize: 10,
                    color: Colors.grey.shade500,
                  ),
                ),
              ],
              const SizedBox(height: 8),
              Text(
                value,
                style: TextStyle(
                  fontSize: compact ? 18 : 22,
                  fontWeight: FontWeight.bold,
                  color: color,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

String formatFinanceMoney(int amountMinor, String currency) {
  if (currency == 'UZS') {
    final whole = amountMinor ~/ 100;
    final formatted = whole.toString().replaceAllMapped(
      RegExp(r'(\d)(?=(\d{3})+$)'),
      (m) => '${m[1]} ',
    );
    return '$formatted $currency';
  }
  final amount = amountMinor / 100;
  return '${amount.toStringAsFixed(2)} $currency';
}

String formatFinanceDate(String isoDate) {
  try {
    final dt = DateTime.parse(isoDate);
    return '${dt.day.toString().padLeft(2, '0')}.${dt.month.toString().padLeft(2, '0')}.${dt.year}';
  } catch (_) {
    return isoDate;
  }
}

String doctorNameFromClinicMembers(int profileId, List<ClinicMember> members) {
  for (final m in members) {
    if (m.doctorProfileId == profileId) return m.displayName;
  }
  return '#$profileId';
}

String formatOptionalFinanceMoney(int? amountMinor, String currency) {
  if (amountMinor == null) return '—';
  return formatFinanceMoney(amountMinor, currency);
}

String formatOptionalPercent(int? percent) =>
    percent == null ? '—' : '$percent%';

/// Compact, colour-coded status chip that doubles as a status picker. When
/// [enabled] is true the user can tap it to open a popup menu of the other
/// available statuses; otherwise it renders as a read-only pill (used for
/// PAID rows, which the backend forbids editing).
class FinanceStatusPill extends StatelessWidget {
  final String status;
  final String label;
  final Color color;
  final bool enabled;
  final List<PopupMenuEntry<String>> entries;
  final ValueChanged<String> onSelected;

  const FinanceStatusPill({
    required this.status,
    required this.label,
    required this.color,
    required this.enabled,
    required this.entries,
    required this.onSelected,
  });

  @override
  Widget build(BuildContext context) {
    final pill = Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: color.withValues(alpha: 0.4)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(
            label,
            style: TextStyle(
              color: color,
              fontWeight: FontWeight.w600,
              fontSize: 12,
            ),
          ),
          if (enabled) ...[
            const SizedBox(width: 4),
            Icon(Icons.arrow_drop_down, size: 18, color: color),
          ],
        ],
      ),
    );

    if (!enabled || entries.isEmpty) return pill;

    return PopupMenuButton<String>(
      tooltip: AppLocalizations.of(context)?.translate('clinicChangeStatus') ??
          'Change status',
      onSelected: onSelected,
      itemBuilder: (_) => entries,
      child: pill,
    );
  }
}

/// Toolbar export menu for finance tables (CSV + PDF).
class FinanceExportButton extends StatelessWidget {
  final String exportLabel;
  final String csvLabel;
  final String pdfLabel;
  final VoidCallback onExportCsv;
  final VoidCallback onExportPdf;

  const FinanceExportButton({
    required this.exportLabel,
    required this.csvLabel,
    required this.pdfLabel,
    required this.onExportCsv,
    required this.onExportPdf,
  });

  @override
  Widget build(BuildContext context) {
    return PopupMenuButton<String>(
      tooltip: exportLabel,
      icon: const Icon(Icons.download_outlined),
      onSelected: (value) {
        if (value == 'csv') {
          onExportCsv();
        } else if (value == 'pdf') {
          onExportPdf();
        }
      },
      itemBuilder: (_) => [
        PopupMenuItem(
          value: 'csv',
          child: Row(
            children: [
              const Icon(Icons.table_chart_outlined, size: 18),
              const SizedBox(width: 8),
              Text(csvLabel),
            ],
          ),
        ),
        PopupMenuItem(
          value: 'pdf',
          child: Row(
            children: [
              const Icon(Icons.picture_as_pdf_outlined, size: 18),
              const SizedBox(width: 8),
              Text(pdfLabel),
            ],
          ),
        ),
      ],
    );
  }
}
