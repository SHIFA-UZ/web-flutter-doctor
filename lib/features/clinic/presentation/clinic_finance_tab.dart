import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/theme/app_colors.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_finance_providers.dart';

import 'finance_appointment_ledger_view.dart';
import 'finance_dashboard_view.dart';
import 'finance_doctor_earnings_view.dart';
import 'finance_installments_view.dart';
import 'finance_payments_view.dart';
import 'finance_records_view.dart';

class ClinicFinanceTab extends ConsumerStatefulWidget {
  final int clinicId;

  const ClinicFinanceTab({super.key, required this.clinicId});

  @override
  ConsumerState<ClinicFinanceTab> createState() => _ClinicFinanceTabState();
}

class _ClinicFinanceTabState extends ConsumerState<ClinicFinanceTab> {
  int _selectedSubTab = 0;

  void _selectSubTab(int index) {
    if (_selectedSubTab == index) return;
    setState(() => _selectedSubTab = index);
    refreshClinicFinancialData(ref, widget.clinicId);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return Column(
      children: [
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
          child: Row(
            children: List.generate(6, (i) {
              final tabs = [
                l10n.translate('clinicFinanceDashboard'),
                l10n.translate('clinicFinanceByAppointment'),
                l10n.translate('clinicFinanceInstallments'),
                l10n.translate('clinicFinanceDoctorEarnings'),
                l10n.translate('clinicFinanceRecords'),
                l10n.translate('clinicFinancePayments'),
              ];
              final selected = _selectedSubTab == i;
              return Padding(
                padding: const EdgeInsets.only(right: 6),
                child: ChoiceChip(
                  label: Text(tabs[i], overflow: TextOverflow.ellipsis),
                  selected: selected,
                  onSelected: (_) => _selectSubTab(i),
                  selectedColor: AppColors.primaryTeal.withValues(alpha: 0.15),
                  labelStyle: TextStyle(
                    color: selected ? AppColors.primaryTeal : Colors.grey.shade700,
                    fontWeight: selected ? FontWeight.w600 : FontWeight.normal,
                  ),
                ),
              );
            }),
          ),
        ),
        const Divider(height: 1),
        Expanded(
          child: IndexedStack(
            index: _selectedSubTab,
            children: [
              FinanceDashboardView(clinicId: widget.clinicId),
              FinanceAppointmentLedgerView(clinicId: widget.clinicId),
              FinanceInstallmentsView(clinicId: widget.clinicId),
              FinanceDoctorEarningsPane(
                clinicId: widget.clinicId,
                isActive: _selectedSubTab == 3,
              ),
              FinanceRecordsView(clinicId: widget.clinicId),
              FinancePaymentsView(clinicId: widget.clinicId),
            ],
          ),
        ),
      ],
    );
  }
}
