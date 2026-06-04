import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/utils/timezone_utils.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_providers.dart';

/// Selected calendar month in the clinic timezone, or null for all-time.
final clinicFinanceMonthFilterProvider =
    StateProvider.family<({int year, int month})?, int>((ref, clinicId) => null);

/// UTC ISO range (`from` inclusive, `to` exclusive) for finance APIs.
({String? fromIso, String? toIso}) financeMonthRangeIso(
  dynamic ref,
  int clinicId,
) {
  final month = ref.watch(clinicFinanceMonthFilterProvider(clinicId));
  if (month == null) return (fromIso: null, toIso: null);
  final clinic = ref.watch(selectedClinicProvider);
  final range = monthRangeUtcInTimezone(
    month.year,
    month.month,
    clinic?.timeZone,
  );
  return (
    fromIso: range.fromUtc.toIso8601String(),
    toIso: range.toUtc.toIso8601String(),
  );
}

List<({int year, int month})> financeRecentMonths(String? timezoneId, {int count = 18}) {
  final today = getTodayInTimezone(timezoneId);
  var year = today.year;
  var month = today.month;
  final months = <({int year, int month})>[];
  for (var i = 0; i < count; i++) {
    months.add((year: year, month: month));
    month--;
    if (month < 1) {
      month = 12;
      year--;
    }
  }
  return months;
}

const _uzbekMonthNames = [
  'yanvar',
  'fevral',
  'mart',
  'aprel',
  'may',
  'iyun',
  'iyul',
  'avgust',
  'sentabr',
  'oktabr',
  'noyabr',
  'dekabr',
];

String formatFinanceMonthLabel(
  BuildContext context,
  int year,
  int month,
) {
  final locale = Localizations.localeOf(context);
  if (locale.languageCode == 'uz') {
    final name = _uzbekMonthNames[month - 1];
    return '$name, $year';
  }
  return DateFormat.yMMMM(locale.toString()).format(DateTime(year, month));
}

/// Dropdown shared by Finance dashboard and doctor earnings.
class FinanceMonthFilterBar extends ConsumerWidget {
  final int clinicId;

  const FinanceMonthFilterBar({super.key, required this.clinicId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context)!;
    final clinic = ref.watch(selectedClinicProvider);
    final selected = ref.watch(clinicFinanceMonthFilterProvider(clinicId));
    final months = financeRecentMonths(clinic?.timeZone);

    return InputDecorator(
      decoration: InputDecoration(
        labelText: l10n.translate('clinicFinanceMonthFilter'),
        isDense: true,
        contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
        border: OutlineInputBorder(borderRadius: BorderRadius.circular(8)),
      ),
      child: DropdownButtonHideUnderline(
        child: DropdownButton<({int year, int month})?>(
          isExpanded: true,
          value: selected,
          items: [
            DropdownMenuItem(
              value: null,
              child: Text(l10n.translate('clinicFinanceMonthAllTime')),
            ),
            ...months.map(
              (m) => DropdownMenuItem(
                value: m,
                child: Text(formatFinanceMonthLabel(context, m.year, m.month)),
              ),
            ),
          ],
          onChanged: (value) {
            ref.read(clinicFinanceMonthFilterProvider(clinicId).notifier).state =
                value;
          },
        ),
      ),
    );
  }
}

bool financeInstantInMonthRange(
  String iso,
  DateTime? fromUtc,
  DateTime? toUtc,
) {
  if (fromUtc == null || toUtc == null) return true;
  final instant = DateTime.parse(iso).toUtc();
  return !instant.isBefore(fromUtc) && instant.isBefore(toUtc);
}
