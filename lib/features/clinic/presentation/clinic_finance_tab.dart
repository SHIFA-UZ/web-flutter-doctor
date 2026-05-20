import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/theme/app_colors.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_finance_models.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_finance_providers.dart';

class ClinicFinanceTab extends ConsumerStatefulWidget {
  final int clinicId;

  const ClinicFinanceTab({super.key, required this.clinicId});

  @override
  ConsumerState<ClinicFinanceTab> createState() => _ClinicFinanceTabState();
}

class _ClinicFinanceTabState extends ConsumerState<ClinicFinanceTab> {
  int _selectedSubTab = 0;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return Column(
      children: [
        _buildSubTabBar(l10n),
        const Divider(height: 1),
        Expanded(
          child: IndexedStack(
            index: _selectedSubTab,
            children: [
              _DashboardView(clinicId: widget.clinicId),
              _RecordsView(clinicId: widget.clinicId),
              _PaymentsView(clinicId: widget.clinicId),
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildSubTabBar(AppLocalizations l10n) {
    final tabs = [
      l10n.translate('clinicFinanceDashboard'),
      l10n.translate('clinicFinanceRecords'),
      l10n.translate('clinicFinancePayments'),
    ];
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: Row(
        children: List.generate(tabs.length, (i) {
          final selected = _selectedSubTab == i;
          return Padding(
            padding: const EdgeInsets.only(right: 8),
            child: ChoiceChip(
              label: Text(tabs[i]),
              selected: selected,
              onSelected: (_) => setState(() => _selectedSubTab = i),
              selectedColor: AppColors.primaryTeal.withValues(alpha: 0.15),
              labelStyle: TextStyle(
                color: selected ? AppColors.primaryTeal : Colors.grey.shade700,
                fontWeight: selected ? FontWeight.w600 : FontWeight.normal,
              ),
            ),
          );
        }),
      ),
    );
  }
}

class _DashboardView extends ConsumerWidget {
  final int clinicId;
  const _DashboardView({required this.clinicId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context)!;
    final dashboardAsync = ref.watch(clinicFinanceDashboardProvider(clinicId));

    return dashboardAsync.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => Center(child: Text('Error: $e')),
      data: (stats) => SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              l10n.translate('clinicFinanceDashboard'),
              style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 16),
            Wrap(
              spacing: 16,
              runSpacing: 16,
              children: [
                _KpiCard(
                  title: l10n.translate('clinicFinanceTotalRevenue'),
                  value: _formatMoney(stats.totalRevenueMinor, stats.currency),
                  color: Colors.green,
                ),
                _KpiCard(
                  title: l10n.translate('clinicFinanceOutstanding'),
                  value: _formatMoney(stats.outstandingMinor, stats.currency),
                  color: Colors.orange,
                ),
                _KpiCard(
                  title: l10n.translate('clinicFinanceOverdueCount'),
                  value: stats.overdueCount.toString(),
                  color: Colors.red,
                ),
                _KpiCard(
                  title: l10n.translate('clinicFinanceCollectionRate'),
                  value: '${(stats.collectionRate * 100).toStringAsFixed(1)}%',
                  color: AppColors.primaryTeal,
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _RecordsView extends ConsumerWidget {
  final int clinicId;
  const _RecordsView({required this.clinicId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context)!;
    final recordsAsync = ref.watch(clinicFinancialRecordsProvider(clinicId));

    return recordsAsync.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => Center(child: Text('Error: $e')),
      data: (records) {
        if (records.isEmpty) {
          return Center(
            child: Text(l10n.translate('clinicFinanceNoRecords')),
          );
        }
        return ListView.separated(
          padding: const EdgeInsets.all(16),
          itemCount: records.length,
          separatorBuilder: (_, __) => const Divider(height: 1),
          itemBuilder: (context, index) {
            final r = records[index];
            return _FinancialRecordTile(record: r);
          },
        );
      },
    );
  }
}

class _PaymentsView extends ConsumerWidget {
  final int clinicId;
  const _PaymentsView({required this.clinicId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context)!;
    final paymentsAsync = ref.watch(clinicPaymentHistoryProvider(clinicId));

    return paymentsAsync.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => Center(child: Text('Error: $e')),
      data: (payments) {
        if (payments.isEmpty) {
          return Center(
            child: Text(l10n.translate('clinicFinanceNoPayments')),
          );
        }
        return ListView.separated(
          padding: const EdgeInsets.all(16),
          itemCount: payments.length,
          separatorBuilder: (_, __) => const Divider(height: 1),
          itemBuilder: (context, index) {
            final p = payments[index];
            return ListTile(
              leading: _methodIcon(p.method),
              title: Text(_formatMoney(p.amountMinor, p.currency)),
              subtitle: Text('${p.method} • ${_formatDate(p.recordedAt)}'),
              trailing: p.memo != null
                  ? Text(
                      p.memo!,
                      style: TextStyle(
                          color: Colors.grey.shade600, fontSize: 12),
                    )
                  : null,
            );
          },
        );
      },
    );
  }

  Widget _methodIcon(String method) {
    switch (method) {
      case 'CASH':
        return const Icon(Icons.payments_outlined, color: Colors.green);
      case 'CARD_EXTERNAL':
        return const Icon(Icons.credit_card, color: Colors.blue);
      case 'TRANSFER':
        return const Icon(Icons.account_balance, color: Colors.purple);
      default:
        return const Icon(Icons.receipt_long, color: Colors.grey);
    }
  }
}

class _FinancialRecordTile extends StatelessWidget {
  final FinancialRecordRow record;
  const _FinancialRecordTile({required this.record});

  @override
  Widget build(BuildContext context) {
    return ListTile(
      leading: _statusBadge(record.status),
      title: Text(
        '${record.recordType} ${record.recordNumber ?? '#${record.id}'}',
        style: const TextStyle(fontWeight: FontWeight.w500),
      ),
      subtitle: Text(
        '${_formatMoney(record.totalMinor, record.currency)} • '
        'Paid: ${_formatMoney(record.paidMinor, record.currency)}',
      ),
      trailing: record.remainingMinor > 0
          ? Text(
              _formatMoney(record.remainingMinor, record.currency),
              style: const TextStyle(
                  color: Colors.red, fontWeight: FontWeight.w600),
            )
          : const Icon(Icons.check_circle, color: Colors.green, size: 20),
    );
  }

  Widget _statusBadge(String status) {
    Color color;
    switch (status) {
      case 'PAID':
        color = Colors.green;
        break;
      case 'OVERDUE':
        color = Colors.red;
        break;
      case 'PARTIALLY_PAID':
        color = Colors.orange;
        break;
      case 'ISSUED':
        color = Colors.blue;
        break;
      default:
        color = Colors.grey;
    }
    return CircleAvatar(
      radius: 6,
      backgroundColor: color,
    );
  }
}

class _KpiCard extends StatelessWidget {
  final String title;
  final String value;
  final Color color;

  const _KpiCard({
    required this.title,
    required this.value,
    required this.color,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 200,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: color.withValues(alpha: 0.2)),
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
          const SizedBox(height: 8),
          Text(
            value,
            style: TextStyle(
              fontSize: 22,
              fontWeight: FontWeight.bold,
              color: color,
            ),
          ),
        ],
      ),
    );
  }
}

String _formatMoney(int amountMinor, String currency) {
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

String _formatDate(String isoDate) {
  try {
    final dt = DateTime.parse(isoDate);
    return '${dt.day.toString().padLeft(2, '0')}.${dt.month.toString().padLeft(2, '0')}.${dt.year}';
  } catch (_) {
    return isoDate;
  }
}
