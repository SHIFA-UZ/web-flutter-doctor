import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:shifa_doc_app_v1/core/api/api_providers.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_finance_actions.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_models.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_providers.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_treatment_plan_actions.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_treatment_plan_models.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_treatment_plan_providers.dart';

enum _WizardPayMode { unpaid, full, installments }

/// Full-screen wizard: create plan, lines, optional appointment links, payment / installments.
class TreatmentPlanWizardSheet {
  static Future<void> show(
    BuildContext context,
    WidgetRef ref, {
    required int clinicId,
    int? initialPatientId,
  }) {
    return showDialog<void>(
      context: context,
      barrierDismissible: false,
      builder: (ctx) => _TreatmentPlanWizardDialog(
        clinicId: clinicId,
        initialPatientId: initialPatientId,
      ),
    );
  }
}

class _TreatmentPlanWizardDialog extends ConsumerStatefulWidget {
  final int clinicId;
  final int? initialPatientId;

  const _TreatmentPlanWizardDialog({
    required this.clinicId,
    this.initialPatientId,
  });

  @override
  ConsumerState<_TreatmentPlanWizardDialog> createState() =>
      _TreatmentPlanWizardDialogState();
}

class _TreatmentPlanWizardDialogState extends ConsumerState<_TreatmentPlanWizardDialog> {
  /// 0 patient, 1 basics, 2 services, 3 appointments, 4 payment
  int _phase = 0;
  int? _patientId;
  int? _planId;

  final _titleCtrl = TextEditingController();
  final _symptomsCtrl = TextEditingController();
  final _diagnosisCtrl = TextEditingController();
  final _notesCtrl = TextEditingController();
  final _reminderDaysCtrl = TextEditingController(text: '3');
  int? _attendingDoctorId;

  final Map<int, int> _catalogQty = {};
  final Map<int, bool> _catalogPick = {};

  List<LineDetailDto> _linesForLink = [];
  final Map<int, int?> _lineAppointment = {};

  _WizardPayMode _payMode = _WizardPayMode.unpaid;
  String _payMethod = 'CASH';
  final _payMemoCtrl = TextEditingController();
  final List<TextEditingController> _instDueCtrls = [
    TextEditingController(),
    TextEditingController(),
  ];
  final List<TextEditingController> _instAmtCtrls = [
    TextEditingController(),
    TextEditingController(),
  ];

  String _patientSearch = '';
  List<ClinicPatientRow> _patientHits = [];
  bool _loadingPatients = false;
  bool _busy = false;

  @override
  void initState() {
    super.initState();
    final pid = widget.initialPatientId;
    if (pid != null) {
      _patientId = pid;
      _phase = 1;
    }
  }

  @override
  void dispose() {
    _titleCtrl.dispose();
    _symptomsCtrl.dispose();
    _diagnosisCtrl.dispose();
    _notesCtrl.dispose();
    _reminderDaysCtrl.dispose();
    _payMemoCtrl.dispose();
    for (final c in _instDueCtrls) {
      c.dispose();
    }
    for (final c in _instAmtCtrls) {
      c.dispose();
    }
    super.dispose();
  }

  String tr(BuildContext ctx, String key, String fallback) {
    final s = AppLocalizations.of(ctx)!.translate(key);
    if (s.isEmpty || s == key) return fallback;
    return s;
  }

  Future<void> _searchPatients() async {
    if (_patientSearch.trim().length < 2) return;
    setState(() => _loadingPatients = true);
    try {
      final api = ref.read(doctorApiClientProvider);
      final q = Uri.encodeQueryComponent(_patientSearch.trim());
      final res = await api.get(
        '/api/clinics/${widget.clinicId}/patients?page=0&size=40&q=$q',
      );
      if (res.statusCode != 200 || !mounted) return;
      final body = json.decode(utf8.decode(res.bodyBytes)) as Map<String, dynamic>;
      final content = body['content'] as List<dynamic>? ?? [];
      setState(() {
        _patientHits = content
            .whereType<Map>()
            .map((e) => ClinicPatientRow.fromJson(Map<String, dynamic>.from(e)))
            .toList();
      });
    } finally {
      if (mounted) setState(() => _loadingPatients = false);
    }
  }

  List<String> _parseSymptoms() {
    return _symptomsCtrl.text
        .split(',')
        .map((s) => s.trim())
        .where((s) => s.isNotEmpty)
        .toList();
  }

  Future<bool> _ensurePlanBasics() async {
    final title = _titleCtrl.text.trim();
    if (title.isEmpty) return false;
    final reminder = int.tryParse(_reminderDaysCtrl.text.trim());
    if (_planId == null) {
      final pid = _patientId;
      if (pid == null) return false;
      final created = await createTreatmentPlan(
        ref,
        clinicId: widget.clinicId,
        patientId: pid,
        title: title,
        diagnosis: _diagnosisCtrl.text.trim().isEmpty ? null : _diagnosisCtrl.text.trim(),
        notes: _notesCtrl.text.trim().isEmpty ? null : _notesCtrl.text.trim(),
        paymentReminderDays: reminder,
        attendingDoctorId: _attendingDoctorId,
        symptoms: _parseSymptoms().isEmpty ? null : _parseSymptoms(),
      );
      if (created == null) return false;
      _planId = created.id;
    } else {
      await patchTreatmentPlan(
        ref,
        planId: _planId!,
        title: title,
        diagnosis: _diagnosisCtrl.text.trim().isEmpty ? null : _diagnosisCtrl.text.trim(),
        notes: _notesCtrl.text.trim().isEmpty ? null : _notesCtrl.text.trim(),
        paymentReminderDays: reminder,
        attendingDoctorId: _attendingDoctorId,
        symptoms: _parseSymptoms(),
      );
    }
    return true;
  }

  Future<bool> _saveLines() async {
    final planId = _planId;
    if (planId == null) return false;
    final cat = ref.read(clinicCatalogProvider(widget.clinicId)).valueOrNull ?? [];
    final lines = <Map<String, dynamic>>[];
    var order = 0;
    for (final id in _catalogPick.keys.where((k) => _catalogPick[k] == true)) {
      ClinicCatalogItem? item;
      for (final c in cat) {
        if (c.id == id) {
          item = c;
          break;
        }
      }
      if (item == null) continue;
      final qty = _catalogQty[id] ?? 1;
      if (qty < 1) continue;
      lines.add({
        'catalogItemId': item.id,
        'title': item.title,
        'quantity': qty,
        'unitPriceMinor': item.defaultPriceMinor,
        'discountMinor': 0,
        'currency': item.currency,
        'sortOrder': order++,
      });
    }
    if (lines.isEmpty) return false;
    final summary = await replaceTreatmentPlanLines(ref, planId: planId, lines: lines);
    if (summary == null) return false;
    final detail = await fetchTreatmentPlanDetail(ref, planId);
    if (detail == null) return false;
    setState(() {
      _linesForLink = detail.lines;
      _lineAppointment.clear();
      for (final l in _linesForLink) {
        _lineAppointment[l.id] = l.linkedAppointment?.id;
      }
    });
    return true;
  }

  Future<bool> _saveLinks() async {
    final planId = _planId;
    if (planId == null) return true;
    final pairs = <Map<String, dynamic>>[];
    for (final line in _linesForLink) {
      pairs.add({'lineId': line.id, 'appointmentId': _lineAppointment[line.id]});
    }
    await linkTreatmentPlanAppointments(ref, planId: planId, pairs: pairs);
    return true;
  }

  Future<void> _finish() async {
    final planId = _planId;
    if (planId == null) return;
    setState(() => _busy = true);
    try {
      final detail = await fetchTreatmentPlanDetail(ref, planId);
      final owed = detail?.summary.owedMinor ?? 0;
      final currency = detail?.summary.currency ?? 'UZS';

      if (_payMode == _WizardPayMode.full && owed > 0) {
        await recordClinicPayment(
          ref,
          clinicId: widget.clinicId,
          treatmentPlanId: planId,
          amountMinor: owed,
          currency: currency,
          method: _payMethod,
          memo: _payMemoCtrl.text.trim().isEmpty ? null : _payMemoCtrl.text.trim(),
        );
      } else if (_payMode == _WizardPayMode.installments && owed > 0) {
        final rows = <Map<String, dynamic>>[];
        for (var i = 0; i < _instDueCtrls.length && i < _instAmtCtrls.length; i++) {
          final d = _instDueCtrls[i].text.trim();
          final a = double.tryParse(_instAmtCtrls[i].text.trim().replaceAll(',', '.'));
          if (d.isEmpty || a == null || a <= 0) continue;
          rows.add({
            'dueDate': d,
            'amountMinor': (a * 100).round(),
          });
        }
        if (rows.length < 2) {
          if (mounted) {
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(
                content: Text(
                  tr(context, 'treatmentPlanWizardNeedTwoInstallments', 'Add at least 2 installments'),
                ),
              ),
            );
          }
          return;
        }
        final start = rows.first['dueDate'] as String;
        await createInstallmentPlan(
          ref,
          clinicId: widget.clinicId,
          treatmentPlanId: planId,
          totalAmountMinor: owed,
          currency: currency,
          numInstallments: rows.length,
          frequency: 'MONTHLY',
          startDate: start,
          scheduleItems: rows,
        );
      }

      await patchTreatmentPlanStatus(ref, planId: planId, status: 'ACTIVE');
      if (_patientId != null) {
        ref.invalidate(treatmentPlansForPatientProvider([widget.clinicId, _patientId!]));
      }
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(tr(context, 'treatmentPlanWizardDone', 'Treatment plan saved'))),
        );
        Navigator.of(context).pop();
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _onNext() async {
    setState(() => _busy = true);
    try {
      if (_phase == 0) {
        if (_patientId == null) return;
        setState(() => _phase = 1);
        return;
      }
      if (_phase == 1) {
        final ok = await _ensurePlanBasics();
        if (!ok) {
          if (mounted) {
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(content: Text(tr(context, 'treatmentPlanWizardFillBasics', 'Enter a title'))),
            );
          }
          return;
        }
        setState(() => _phase = 2);
        return;
      }
      if (_phase == 2) {
        final ok = await _saveLines();
        if (!ok) {
          if (mounted) {
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(
                content: Text(tr(context, 'treatmentPlanWizardPickServices', 'Select at least one service')),
              ),
            );
          }
          return;
        }
        setState(() => _phase = 3);
        return;
      }
      if (_phase == 3) {
        await _saveLinks();
        setState(() => _phase = 4);
        return;
      }
      if (_phase == 4) {
        await _finish();
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  void _onBack() {
    if (_phase <= 1 && widget.initialPatientId != null) {
      Navigator.of(context).pop();
      return;
    }
    if (_phase <= 0) {
      Navigator.of(context).pop();
      return;
    }
    setState(() {
      _phase--;
      if (_phase < 1 && widget.initialPatientId != null) _phase = 1;
    });
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final membersAsync = ref.watch(clinicMembersProvider(widget.clinicId));
    final catalogAsync = ref.watch(clinicCatalogProvider(widget.clinicId));

    return Dialog.fullscreen(
      child: Scaffold(
        appBar: AppBar(
          title: Text(tr(context, 'treatmentPlanWizardTitle', 'Treatment plan')),
          leading: IconButton(
            icon: const Icon(Icons.close),
            onPressed: _busy ? null : () => Navigator.of(context).pop(),
          ),
        ),
        body: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text('Step ${_phase + 1}/5', style: const TextStyle(color: Colors.black54)),
              const SizedBox(height: 12),
              Expanded(child: _buildPhaseBody(context, l10n, membersAsync, catalogAsync)),
              const SizedBox(height: 12),
              Row(
                children: [
                  TextButton(onPressed: _busy ? null : _onBack, child: Text(l10n.translate('back'))),
                  const Spacer(),
                  if (_phase > 0)
                    ShifaPrimaryButton(
                      label: _phase == 4
                          ? tr(context, 'treatmentPlanWizardFinish', 'Finish')
                          : l10n.translate('continue'),
                      isLoading: _busy,
                      onPressed: _busy ? null : _onNext,
                    ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildPhaseBody(
    BuildContext context,
    AppLocalizations l10n,
    AsyncValue<List<ClinicMember>> membersAsync,
    AsyncValue<List<ClinicCatalogItem>> catalogAsync,
  ) {
    if (_phase == 0) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          TextField(
            decoration: InputDecoration(
              labelText: tr(context, 'treatmentPlanWizardSearchPatient', 'Search patient'),
              suffixIcon: IconButton(
                icon: const Icon(Icons.search),
                onPressed: _loadingPatients ? null : _searchPatients,
              ),
            ),
            onChanged: (v) => _patientSearch = v,
            onSubmitted: (_) => _searchPatients(),
          ),
          const SizedBox(height: 8),
          if (_loadingPatients) const LinearProgressIndicator(),
          Expanded(
            child: ListView.builder(
              itemCount: _patientHits.length,
              itemBuilder: (ctx, i) {
                final p = _patientHits[i];
                return ListTile(
                  title: Text(p.fullName),
                  subtitle: Text(p.phone ?? p.email ?? ''),
                  onTap: () => setState(() {
                    _patientId = p.patientId;
                    _phase = 1;
                  }),
                );
              },
            ),
          ),
        ],
      );
    }

    if (_phase == 1) {
      return SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            TextField(
              controller: _titleCtrl,
              decoration: InputDecoration(labelText: l10n.translate('treatmentPlanTitle')),
            ),
            TextField(
              controller: _symptomsCtrl,
              decoration: InputDecoration(
                labelText: tr(context, 'treatmentPlanWizardSymptoms', 'Symptoms (comma separated)'),
              ),
            ),
            TextField(
              controller: _diagnosisCtrl,
              decoration: InputDecoration(labelText: l10n.translate('treatmentPlanDiagnosis')),
            ),
            TextField(
              controller: _notesCtrl,
              decoration: InputDecoration(labelText: l10n.translate('treatmentPlanNotes')),
              maxLines: 2,
            ),
            TextField(
              controller: _reminderDaysCtrl,
              decoration: InputDecoration(
                labelText: tr(context, 'treatmentPlanWizardReminderDays', 'Payment reminder (days)'),
              ),
              keyboardType: TextInputType.number,
            ),
            const SizedBox(height: 8),
            membersAsync.when(
              loading: () => const Center(child: CircularProgressIndicator()),
              error: (_, __) => const SizedBox.shrink(),
              data: (members) {
                return DropdownButtonFormField<int?>(
                  value: _attendingDoctorId,
                  decoration: InputDecoration(
                    labelText: tr(context, 'treatmentPlanWizardAttending', 'Attending doctor'),
                  ),
                  items: [
                    const DropdownMenuItem<int?>(value: null, child: Text('—')),
                    ...members.map(
                      (m) => DropdownMenuItem<int?>(
                        value: m.doctorProfileId,
                        child: Text(m.displayName),
                      ),
                    ),
                  ],
                  onChanged: (v) => setState(() => _attendingDoctorId = v),
                );
              },
            ),
          ],
        ),
      );
    }

    if (_phase == 2) {
      return catalogAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(child: Text('$e')),
        data: (items) {
          return ListView(
            children: items.where((c) => c.active).map((c) {
              final picked = _catalogPick[c.id] ?? false;
              final qty = _catalogQty[c.id] ?? 1;
              return CheckboxListTile(
                value: picked,
                onChanged: (v) => setState(() {
                  _catalogPick[c.id] = v ?? false;
                  _catalogQty[c.id] = qty;
                }),
                title: Text(c.title),
                subtitle: Text('${(c.defaultPriceMinor / 100).toStringAsFixed(0)} ${c.currency}'),
                secondary: SizedBox(
                  width: 72,
                  child: TextFormField(
                    initialValue: '$qty',
                    keyboardType: TextInputType.number,
                    decoration: const InputDecoration(labelText: 'Qty'),
                    onChanged: (s) => _catalogQty[c.id] = int.tryParse(s) ?? 1,
                  ),
                ),
              );
            }).toList(),
          );
        },
      );
    }

    if (_phase == 3) {
      final planId = _planId;
      if (planId == null || _patientId == null) return const SizedBox.shrink();
      return FutureBuilder<List<ClinicPatientAppointmentDto>>(
        future: fetchPatientAppointments(ref, clinicId: widget.clinicId, patientId: _patientId!),
        builder: (ctx, snap) {
          if (!snap.hasData) {
            return const Center(child: CircularProgressIndicator());
          }
          final appts = snap.data!;
          return ListView(
            children: _linesForLink.map((line) {
              return Padding(
                padding: const EdgeInsets.only(bottom: 8),
                child: Row(
                  children: [
                    Expanded(child: Text(line.title, maxLines: 2, overflow: TextOverflow.ellipsis)),
                    DropdownButton<int?>(
                      value: _lineAppointment[line.id],
                      items: [
                        const DropdownMenuItem<int?>(value: null, child: Text('—')),
                        ...appts.map(
                          (a) => DropdownMenuItem<int?>(
                            value: a.id,
                            child: Text('${a.startAt} · ${a.doctorName}'),
                          ),
                        ),
                      ],
                      onChanged: (v) => setState(() => _lineAppointment[line.id] = v),
                    ),
                  ],
                ),
              );
            }).toList(),
          );
        },
      );
    }

    return SingleChildScrollView(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          RadioListTile<_WizardPayMode>(
            title: Text(tr(context, 'treatmentPlanWizardPayUnpaid', 'Leave unpaid')),
            value: _WizardPayMode.unpaid,
            groupValue: _payMode,
            onChanged: (v) => setState(() => _payMode = v!),
          ),
          RadioListTile<_WizardPayMode>(
            title: Text(tr(context, 'treatmentPlanWizardPayFull', 'Pay in full now (clinic payment)')),
            value: _WizardPayMode.full,
            groupValue: _payMode,
            onChanged: (v) => setState(() => _payMode = v!),
          ),
          RadioListTile<_WizardPayMode>(
            title: Text(tr(context, 'treatmentPlanWizardPayInstallments', 'Installments')),
            value: _WizardPayMode.installments,
            groupValue: _payMode,
            onChanged: (v) => setState(() => _payMode = v!),
          ),
          if (_payMode == _WizardPayMode.full) ...[
            DropdownButtonFormField<String>(
              value: _payMethod,
              decoration: InputDecoration(labelText: tr(context, 'treatmentPlanWizardMethod', 'Method')),
              items: const [
                DropdownMenuItem(value: 'CASH', child: Text('CASH')),
                DropdownMenuItem(value: 'TRANSFER', child: Text('TRANSFER')),
                DropdownMenuItem(value: 'CARD_EXTERNAL', child: Text('CARD')),
              ],
              onChanged: (v) => setState(() => _payMethod = v ?? 'CASH'),
            ),
            TextField(
              controller: _payMemoCtrl,
              decoration: InputDecoration(labelText: tr(context, 'treatmentPlanWizardMemo', 'Memo')),
            ),
          ],
          if (_payMode == _WizardPayMode.installments) ...[
            Text(tr(context, 'treatmentPlanWizardInstallHint', 'Due date YYYY-MM-DD, amount in major units')),
            ...List.generate(_instDueCtrls.length, (i) {
              return Padding(
                padding: const EdgeInsets.only(bottom: 8),
                child: Row(
                  children: [
                    Expanded(
                      child: TextField(
                        controller: _instDueCtrls[i],
                        decoration: const InputDecoration(labelText: 'Due'),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: TextField(
                        controller: _instAmtCtrls[i],
                        decoration: const InputDecoration(labelText: 'Amount'),
                        keyboardType: const TextInputType.numberWithOptions(decimal: true),
                      ),
                    ),
                  ],
                ),
              );
            }),
            TextButton(
              onPressed: () => setState(() {
                _instDueCtrls.add(TextEditingController());
                _instAmtCtrls.add(TextEditingController());
              }),
              child: Text(tr(context, 'treatmentPlanWizardAddRow', 'Add row')),
            ),
          ],
        ],
      ),
    );
  }
}
