import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:shifa_doc_app_v1/core/api/api_providers.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/features/clinic/presentation/treatment_plan_wizard_sheet.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_models.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_treatment_plan_models.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_treatment_plan_providers.dart';

class ClinicTreatmentPlansTab extends ConsumerStatefulWidget {
  final int clinicId;

  const ClinicTreatmentPlansTab({super.key, required this.clinicId});

  @override
  ConsumerState<ClinicTreatmentPlansTab> createState() => _ClinicTreatmentPlansTabState();
}

class _ClinicTreatmentPlansTabState extends ConsumerState<ClinicTreatmentPlansTab> {
  int? _selectedPatientId;
  final _searchCtrl = TextEditingController();
  List<ClinicPatientRow> _hits = [];
  bool _searching = false;

  @override
  void dispose() {
    _searchCtrl.dispose();
    super.dispose();
  }

  Future<void> _runSearch() async {
    final q = _searchCtrl.text.trim();
    if (q.length < 2) return;
    setState(() => _searching = true);
    try {
      final api = ref.read(doctorApiClientProvider);
      final enc = Uri.encodeQueryComponent(q);
      final res = await api.get(
        '/api/clinics/${widget.clinicId}/patients?page=0&size=40&q=$enc',
      );
      if (!mounted || res.statusCode != 200) return;
      final body = json.decode(utf8.decode(res.bodyBytes)) as Map<String, dynamic>;
      final content = body['content'] as List<dynamic>? ?? [];
      setState(() {
        _hits = content
            .whereType<Map>()
            .map((e) => ClinicPatientRow.fromJson(Map<String, dynamic>.from(e)))
            .toList();
      });
    } finally {
      if (mounted) setState(() => _searching = false);
    }
  }

  String _money(int minor, String currency) {
    final v = minor / 100;
    return '${v.toStringAsFixed(2)} $currency';
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final pid = _selectedPatientId;
    final plansAsync = pid == null
        ? const AsyncValue.data(<TreatmentPlanSummaryDto>[])
        : ref.watch(treatmentPlansForPatientProvider([widget.clinicId, pid]));

    return Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              Expanded(
                child: TextField(
                  controller: _searchCtrl,
                  decoration: InputDecoration(
                    labelText: l10n.translate('clinicTreatmentPlansSearchPatient'),
                    suffixIcon: IconButton(
                      icon: const Icon(Icons.search),
                      onPressed: _searching ? null : _runSearch,
                    ),
                  ),
                  onSubmitted: (_) => _runSearch(),
                ),
              ),
              const SizedBox(width: 12),
              FilledButton.icon(
                onPressed: () => TreatmentPlanWizardSheet.show(
                  context,
                  ref,
                  clinicId: widget.clinicId,
                  initialPatientId: _selectedPatientId,
                ),
                icon: const Icon(Icons.add),
                label: Text(l10n.translate('clinicTreatmentPlansNew')),
              ),
            ],
          ),
          if (_searching) const LinearProgressIndicator(),
          const SizedBox(height: 8),
          if (pid == null)
            Expanded(
              child: Center(
                child: Text(
                  l10n.translate('clinicTreatmentPlansSelectPatient'),
                  textAlign: TextAlign.center,
                ),
              ),
            )
          else ...[
            Text(
              '${l10n.translate('clinicTreatmentPlansForPatient')}: #$pid',
              style: const TextStyle(fontWeight: FontWeight.w600),
            ),
            const SizedBox(height: 8),
            Expanded(
              child: plansAsync.when(
                loading: () => const Center(child: CircularProgressIndicator()),
                error: (e, _) => Center(child: Text('$e')),
                data: (plans) {
                  if (plans.isEmpty) {
                    return Center(child: Text(l10n.translate('clinicTreatmentPlansEmpty')));
                  }
                  return ListView.separated(
                    itemCount: plans.length,
                    separatorBuilder: (_, __) => const Divider(height: 1),
                    itemBuilder: (ctx, i) {
                      final p = plans[i];
                      return ListTile(
                        title: Text(p.title ?? l10n.translate('clinicTreatmentPlansUntitled')),
                        subtitle: Text(
                          '${p.planPaymentStatus} · ${_money(p.owedMinor, p.currency)} '
                          '${l10n.translate('clinicTreatmentPlansOutstanding')}',
                        ),
                        trailing: const Icon(Icons.chevron_right),
                        onTap: () {
                          showDialog<void>(
                            context: context,
                            builder: (ctx2) => AlertDialog(
                              title: Text('Plan #${p.id}'),
                              content: SingleChildScrollView(
                                child: Text(
                                  '${l10n.translate('treatmentPlanDiagnosis')}: ${p.diagnosis ?? '—'}\n'
                                  '${l10n.translate('treatmentPlanNotes')}: ${p.notes ?? '—'}',
                                ),
                              ),
                              actions: [
                                TextButton(
                                  onPressed: () => Navigator.pop(ctx2),
                                  child: Text(l10n.close),
                                ),
                              ],
                            ),
                          );
                        },
                      );
                    },
                  );
                },
              ),
            ),
          ],
          if (pid == null && _hits.isNotEmpty) ...[
            const SizedBox(height: 8),
            Text(
              l10n.translate('clinicTreatmentPlansPickFromSearch'),
              style: TextStyle(color: Colors.grey.shade700),
            ),
            SizedBox(
              height: 160,
              child: ListView.builder(
                itemCount: _hits.length,
                itemBuilder: (ctx, i) {
                  final r = _hits[i];
                  return ListTile(
                    dense: true,
                    title: Text(r.fullName),
                    subtitle: Text(r.phone ?? r.email ?? ''),
                    onTap: () => setState(() => _selectedPatientId = r.patientId),
                  );
                },
              ),
            ),
          ],
        ],
      ),
    );
  }
}
