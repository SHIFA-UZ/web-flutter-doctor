import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/theme/app_colors.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_finance_actions.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_finance_models.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_finance_providers.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_treatment_plan_models.dart';

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
        ),
        const Divider(height: 1),
        Expanded(
          child: IndexedStack(
            index: _selectedSubTab,
            children: [
              _DashboardView(clinicId: widget.clinicId),
              _AppointmentLedgerView(clinicId: widget.clinicId),
              _InstallmentsFinanceView(clinicId: widget.clinicId),
              _DoctorEarningsPane(clinicId: widget.clinicId),
              _RecordsView(clinicId: widget.clinicId),
              _PaymentsView(clinicId: widget.clinicId),
            ],
          ),
        ),
      ],
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
            if (stats.doctorEarningsTop.isNotEmpty) ...[
              const SizedBox(height: 24),
              Text(
                l10n.translate('clinicFinanceDoctorEarnings'),
                style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w600),
              ),
              const SizedBox(height: 8),
              ...stats.doctorEarningsTop.map((d) {
                return ListTile(
                  dense: true,
                  title: Text('#${d.doctorProfileId}'),
                  subtitle: Text(l10n.translate('clinicFinanceDoctorEarningsHint')),
                  trailing: Text(
                    '${_formatMoney(d.grossMinor, stats.currency)} / '
                    '${_formatMoney(d.collectedMinor, stats.currency)}',
                  ),
                );
              }),
            ],
          ],
        ),
      ),
    );
  }
}

class _AppointmentLedgerView extends ConsumerWidget {
  final int clinicId;
  const _AppointmentLedgerView({required this.clinicId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context)!;
    final async = ref.watch(clinicAppointmentLedgerProvider(clinicId));
    return async.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => Center(child: Text('Error: $e')),
      data: (page) {
        final content = page['content'] as List<dynamic>? ?? [];
        if (content.isEmpty) {
          return Center(child: Text(l10n.translate('clinicFinanceNoLedgerRows')));
        }
        return ListView.separated(
          padding: const EdgeInsets.all(16),
          itemCount: content.length,
          separatorBuilder: (_, __) => const Divider(height: 1),
          itemBuilder: (ctx, i) {
            final row = AppointmentLedgerRowDto.fromJson(
              Map<String, dynamic>.from(content[i] as Map),
            );
            return ExpansionTile(
              title: Text('${row.patientName} · ${_formatDate(row.startAt)}'),
              subtitle: Text(
                '${row.planSimplePaymentStatus} · ${_formatMoney(row.visitTotalMinor, row.currency)}',
              ),
              children: [
                ListTile(
                  dense: true,
                  title: Text('${l10n.translate('clinicFinanceVisitServices')} · '
                      '${row.doctorName}'),
                ),
                ...row.services.map(
                  (s) => ListTile(
                    dense: true,
                    title: Text(s.title),
                    trailing: Text(_formatMoney(s.lineTotalMinor, row.currency)),
                  ),
                ),
              ],
            );
          },
        );
      },
    );
  }
}

class _InstallmentsFinanceView extends ConsumerWidget {
  final int clinicId;
  const _InstallmentsFinanceView({required this.clinicId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context)!;
    final canAct = ref.watch(canManageFinanceProvider);
    final overdueAsync = ref.watch(clinicOverdueProvider(clinicId));
    return overdueAsync.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => Center(child: Text('Error: $e')),
      data: (data) {
        final list = data['overdueInstallments'] as List<dynamic>? ?? [];
        if (list.isEmpty) {
          return Center(child: Text(l10n.translate('clinicFinanceNoInstallments')));
        }
        return ListView.separated(
          padding: const EdgeInsets.all(16),
          itemCount: list.length,
          separatorBuilder: (_, __) => const Divider(height: 1),
          itemBuilder: (ctx, i) {
            final m = Map<String, dynamic>.from(list[i] as Map);
            final id = (m['id'] as num?)?.toInt() ?? 0;
            final amt = (m['amountMinor'] as num?)?.toInt() ?? 0;
            final cur = m['currency']?.toString() ?? 'UZS';
            final due = m['dueDate']?.toString() ?? '';
            final st = m['status']?.toString() ?? '';
            return ListTile(
              title: Text('#$id · $st'),
              subtitle: Text(due),
              trailing: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Padding(
                    padding: const EdgeInsets.only(right: 8),
                    child: Text(_formatMoney(amt, cur)),
                  ),
                  if (canAct && st == 'PENDING') ...[
                    IconButton(
                      tooltip: l10n.translate('clinicFinanceMarkInstallmentPaid'),
                      icon: const Icon(Icons.check_circle_outline),
                      onPressed: () async {
                        final ok = await markInstallmentItemPaid(
                          ref,
                          clinicId: clinicId,
                          itemId: id,
                          method: 'CASH',
                        );
                        if (context.mounted) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(content: Text(ok ? 'OK' : 'Failed')),
                          );
                          if (ok) ref.invalidate(clinicOverdueProvider(clinicId));
                        }
                      },
                    ),
                    IconButton(
                      tooltip: l10n.translate('clinicFinanceNotifyInstallment'),
                      icon: const Icon(Icons.notifications_active_outlined),
                      onPressed: () async {
                        final ok = await notifyInstallmentItem(ref, clinicId: clinicId, itemId: id);
                        if (context.mounted) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(content: Text(ok ? 'OK' : 'Failed')),
                          );
                        }
                      },
                    ),
                  ],
                ],
              ),
            );
          },
        );
      },
    );
  }
}

class _DoctorEarningsPane extends ConsumerWidget {
  final int clinicId;
  const _DoctorEarningsPane({required this.clinicId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context)!;
    final async = ref.watch(clinicDoctorEarningsProvider(clinicId));
    return async.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => Center(child: Text('Error: $e')),
      data: (rows) {
        if (rows.isEmpty) {
          return Center(child: Text(l10n.translate('clinicFinanceNoLedgerRows')));
        }
        return ListView.separated(
          padding: const EdgeInsets.all(16),
          itemCount: rows.length,
          separatorBuilder: (_, __) => const Divider(height: 1),
          itemBuilder: (ctx, i) {
            final d = rows[i];
            return ListTile(
              title: Text('Doctor #${d.doctorProfileId}'),
              subtitle: Text(
                '${l10n.translate('clinicFinanceDoctorEarningsHint')} · visits ${d.visitCount}',
              ),
              trailing: Text(
                '${_formatMoney(d.grossMinor, 'UZS')} / ${_formatMoney(d.collectedMinor, 'UZS')}',
              ),
            );
          },
        );
      },
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
        '${record.uiPaymentStatus.isNotEmpty ? record.uiPaymentStatus : record.status} • '
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
