import 'dart:async';
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

/// Two-step wizard:
/// 1. live-search a patient (skipped when [initialPatientId] is provided)
/// 2. single combined form: title + diagnosis + notes + attending doctor +
///    services (with per-line appointment link) + payment.
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

class _TreatmentPlanWizardDialogState
    extends ConsumerState<_TreatmentPlanWizardDialog> {
  /// 0 = patient picker, 1 = combined form.
  int _phase = 0;
  int? _patientId;

  // Basics
  final _titleCtrl = TextEditingController();
  final _diagnosisCtrl = TextEditingController();
  final _notesCtrl = TextEditingController();
  final _reminderDaysCtrl = TextEditingController(text: '3');
  int? _attendingDoctorId;

  // Services + per-line appointment link
  final Map<int, bool> _catalogPick = {};
  final Map<int, int> _catalogQty = {};
  final Map<int, int?> _catalogAppt = {};

  // Cached list of appointments for the chosen patient (for per-line linking).
  List<ClinicPatientAppointmentDto> _patientAppts = [];
  bool _loadingAppts = false;

  // Payment
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

  // Patient search
  final _searchCtrl = TextEditingController();
  Timer? _searchDebounce;
  List<ClinicPatientRow> _patientHits = [];
  bool _searchingPatients = false;

  bool _busy = false;

  @override
  void initState() {
    super.initState();
    final pid = widget.initialPatientId;
    if (pid != null) {
      _patientId = pid;
      _phase = 1;
      WidgetsBinding.instance.addPostFrameCallback((_) => _loadAppointments());
    }
  }

  @override
  void dispose() {
    _searchDebounce?.cancel();
    _titleCtrl.dispose();
    _diagnosisCtrl.dispose();
    _notesCtrl.dispose();
    _reminderDaysCtrl.dispose();
    _payMemoCtrl.dispose();
    _searchCtrl.dispose();
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

  // --- Patient live search ---------------------------------------------------

  void _onSearchChanged(String value) {
    _searchDebounce?.cancel();
    final q = value.trim();
    if (q.isEmpty) {
      setState(() {
        _patientHits = [];
        _searchingPatients = false;
      });
      return;
    }
    if (q.length < 2) {
      setState(() {
        _patientHits = [];
      });
      return;
    }
    _searchDebounce = Timer(const Duration(milliseconds: 250), () {
      _runSearch(q);
    });
  }

  Future<void> _runSearch(String q) async {
    setState(() => _searchingPatients = true);
    try {
      final api = ref.read(doctorApiClientProvider);
      final enc = Uri.encodeQueryComponent(q);
      final res = await api.get(
        '/api/clinics/${widget.clinicId}/patients?page=0&size=40&q=$enc',
      );
      if (!mounted || res.statusCode != 200) return;
      final body =
          json.decode(utf8.decode(res.bodyBytes)) as Map<String, dynamic>;
      final content = body['content'] as List<dynamic>? ?? [];
      setState(() {
        _patientHits = content
            .whereType<Map>()
            .map((e) => ClinicPatientRow.fromJson(Map<String, dynamic>.from(e)))
            .toList();
      });
    } finally {
      if (mounted) setState(() => _searchingPatients = false);
    }
  }

  Future<void> _selectPatient(ClinicPatientRow row) async {
    setState(() {
      _patientId = row.patientId;
      _phase = 1;
      _patientHits = [];
      _searchCtrl.text = row.fullName;
    });
    await _loadAppointments();
  }

  Future<void> _loadAppointments() async {
    final pid = _patientId;
    if (pid == null) return;
    setState(() => _loadingAppts = true);
    try {
      final list = await fetchPatientAppointments(
        ref,
        clinicId: widget.clinicId,
        patientId: pid,
      );
      if (!mounted) return;
      setState(() => _patientAppts = list);
    } finally {
      if (mounted) setState(() => _loadingAppts = false);
    }
  }

  // --- Save -----------------------------------------------------------------

  List<Map<String, dynamic>> _buildLineRequests(List<ClinicCatalogItem> cat) {
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
      final apptId = _catalogAppt[id];
      lines.add({
        'catalogItemId': item.id,
        'title': item.title,
        'quantity': qty,
        'unitPriceMinor': item.defaultPriceMinor,
        'discountMinor': 0,
        'currency': item.currency,
        'sortOrder': order++,
        if (apptId != null) 'linkedAppointmentId': apptId,
      });
    }
    return lines;
  }

  Future<void> _save() async {
    final pid = _patientId;
    if (pid == null) return;

    final title = _titleCtrl.text.trim();
    if (title.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            tr(context, 'treatmentPlanWizardFillBasics', 'Enter a plan title'),
          ),
        ),
      );
      return;
    }

    final cat = ref.read(clinicCatalogProvider(widget.clinicId)).valueOrNull ?? [];
    final lineReqs = _buildLineRequests(cat);
    if (lineReqs.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            tr(context, 'treatmentPlanWizardPickServices',
                'Select at least one catalog service'),
          ),
        ),
      );
      return;
    }

    // Pre-validate installments before we mutate any data.
    List<Map<String, dynamic>>? installmentRows;
    if (_payMode == _WizardPayMode.installments) {
      installmentRows = <Map<String, dynamic>>[];
      for (var i = 0; i < _instDueCtrls.length && i < _instAmtCtrls.length; i++) {
        final d = _instDueCtrls[i].text.trim();
        final a = double.tryParse(
          _instAmtCtrls[i].text.trim().replaceAll(',', '.'),
        );
        if (d.isEmpty || a == null || a <= 0) continue;
        installmentRows.add({
          'dueDate': d,
          'amountMinor': (a * 100).round(),
        });
      }
      if (installmentRows.length < 2) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              tr(context, 'treatmentPlanWizardNeedTwoInstallments',
                  'Add at least 2 installments with valid date and amount'),
            ),
          ),
        );
        return;
      }
    }

    setState(() => _busy = true);
    try {
      final reminder = int.tryParse(_reminderDaysCtrl.text.trim());
      final created = await createTreatmentPlan(
        ref,
        clinicId: widget.clinicId,
        patientId: pid,
        title: title,
        diagnosis:
            _diagnosisCtrl.text.trim().isEmpty ? null : _diagnosisCtrl.text.trim(),
        notes: _notesCtrl.text.trim().isEmpty ? null : _notesCtrl.text.trim(),
        paymentReminderDays: reminder,
        attendingDoctorId: _attendingDoctorId,
      );
      if (created == null) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(tr(context, 'error', 'Could not create plan')),
            ),
          );
        }
        return;
      }
      final planId = created.id;

      final summary = await replaceTreatmentPlanLines(
        ref,
        planId: planId,
        lines: lineReqs,
      );
      if (summary == null) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text(tr(context, 'error', 'Could not save services'))),
          );
        }
        return;
      }

      final detail = await fetchTreatmentPlanDetail(ref, planId);
      final owed = detail?.summary.owedMinor ?? summary.owedMinor;
      final currency = detail?.summary.currency ?? summary.currency;

      if (_payMode == _WizardPayMode.full && owed > 0) {
        await recordClinicPayment(
          ref,
          clinicId: widget.clinicId,
          treatmentPlanId: planId,
          amountMinor: owed,
          currency: currency,
          method: _payMethod,
          memo: _payMemoCtrl.text.trim().isEmpty
              ? null
              : _payMemoCtrl.text.trim(),
        );
      } else if (_payMode == _WizardPayMode.installments &&
          installmentRows != null &&
          owed > 0) {
        final start = installmentRows.first['dueDate'] as String;
        await createInstallmentPlan(
          ref,
          clinicId: widget.clinicId,
          treatmentPlanId: planId,
          totalAmountMinor: owed,
          currency: currency,
          numInstallments: installmentRows.length,
          frequency: 'MONTHLY',
          startDate: start,
          scheduleItems: installmentRows,
        );
      }

      await patchTreatmentPlanStatus(ref, planId: planId, status: 'ACTIVE');
      ref.invalidate(treatmentPlansForPatientProvider([widget.clinicId, pid]));

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(tr(context, 'treatmentPlanWizardDone',
                'Treatment plan saved')),
          ),
        );
        Navigator.of(context).pop();
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  void _onBack() {
    if (_phase == 1 && widget.initialPatientId == null) {
      setState(() {
        _phase = 0;
        _patientId = null;
      });
      return;
    }
    Navigator.of(context).pop();
  }

  // --- Build ----------------------------------------------------------------

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
              Text(
                'Step ${_phase + 1}/2',
                style: const TextStyle(color: Colors.black54),
              ),
              const SizedBox(height: 12),
              Expanded(
                child: _phase == 0
                    ? _buildPatientPicker(context)
                    : _buildPlanForm(context, l10n, membersAsync, catalogAsync),
              ),
              const SizedBox(height: 12),
              Row(
                children: [
                  TextButton(
                    onPressed: _busy ? null : _onBack,
                    child: Text(l10n.translate('back')),
                  ),
                  const Spacer(),
                  if (_phase == 1)
                    ShifaPrimaryButton(
                      label: tr(context, 'treatmentPlanWizardFinish', 'Save'),
                      isLoading: _busy,
                      onPressed: _busy ? null : _save,
                    ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  // ---- Phase 0: live patient search ----------------------------------------

  Widget _buildPatientPicker(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        TextField(
          controller: _searchCtrl,
          autofocus: true,
          decoration: InputDecoration(
            labelText: tr(context, 'treatmentPlanWizardSearchPatient',
                'Search patient (type to filter)'),
            prefixIcon: const Icon(Icons.search),
            suffixIcon: _searchCtrl.text.isEmpty
                ? null
                : IconButton(
                    icon: const Icon(Icons.close),
                    onPressed: () {
                      _searchCtrl.clear();
                      _onSearchChanged('');
                    },
                  ),
          ),
          onChanged: _onSearchChanged,
        ),
        const SizedBox(height: 8),
        if (_searchingPatients) const LinearProgressIndicator(minHeight: 2),
        if (!_searchingPatients &&
            _patientHits.isEmpty &&
            _searchCtrl.text.trim().length >= 2)
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 16),
            child: Text(
              tr(context, 'treatmentPlanWizardNoPatients', 'No matching patients.'),
              textAlign: TextAlign.center,
              style: TextStyle(color: Colors.grey.shade600),
            ),
          ),
        Expanded(
          child: ListView.builder(
            itemCount: _patientHits.length,
            itemBuilder: (ctx, i) {
              final p = _patientHits[i];
              return ListTile(
                leading: CircleAvatar(
                  child: Text(
                    p.fullName.isEmpty ? '?' : p.fullName[0].toUpperCase(),
                  ),
                ),
                title: Text(p.fullName),
                subtitle: Text(p.phone ?? p.email ?? ''),
                onTap: () => _selectPatient(p),
              );
            },
          ),
        ),
      ],
    );
  }

  // ---- Phase 1: combined form ----------------------------------------------

  Widget _buildPlanForm(
    BuildContext context,
    AppLocalizations l10n,
    AsyncValue<List<ClinicMember>> membersAsync,
    AsyncValue<List<ClinicCatalogItem>> catalogAsync,
  ) {
    return SingleChildScrollView(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // Basics ------------------------------------------------------------
          _Section(
            title: tr(context, 'treatmentPlanWizardSectionBasics', 'Plan basics'),
            children: [
              TextField(
                controller: _titleCtrl,
                decoration: InputDecoration(
                  labelText: l10n.translate('treatmentPlanTitle'),
                ),
              ),
              const SizedBox(height: 8),
              TextField(
                controller: _diagnosisCtrl,
                decoration: InputDecoration(
                  labelText: l10n.translate('treatmentPlanDiagnosis'),
                ),
                maxLines: 2,
              ),
              const SizedBox(height: 8),
              TextField(
                controller: _notesCtrl,
                decoration: InputDecoration(
                  labelText: l10n.translate('treatmentPlanNotes'),
                ),
                maxLines: 2,
              ),
              const SizedBox(height: 8),
              TextField(
                controller: _reminderDaysCtrl,
                decoration: InputDecoration(
                  labelText: tr(context, 'treatmentPlanWizardReminderDays',
                      'Payment reminder (days)'),
                ),
                keyboardType: TextInputType.number,
              ),
              const SizedBox(height: 8),
              membersAsync.when(
                loading: () => const LinearProgressIndicator(minHeight: 2),
                error: (_, __) => const SizedBox.shrink(),
                data: (members) {
                  return DropdownButtonFormField<int?>(
                    initialValue: _attendingDoctorId,
                    decoration: InputDecoration(
                      labelText: tr(context, 'treatmentPlanWizardAttending',
                          'Attending doctor'),
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

          // Services + per-line appointment -----------------------------------
          _Section(
            title: tr(context, 'treatmentPlanWizardSectionServices',
                'Treatments / services'),
            subtitle: tr(
              context,
              'treatmentPlanWizardSectionServicesHint',
              'Pick from clinic catalog. Optionally assign each line to a visit.',
            ),
            children: [
              if (_loadingAppts)
                const Padding(
                  padding: EdgeInsets.symmetric(vertical: 8),
                  child: LinearProgressIndicator(minHeight: 2),
                ),
              catalogAsync.when(
                loading: () => const Center(child: CircularProgressIndicator()),
                error: (e, _) => Text('$e'),
                data: (items) {
                  final active = items.where((c) => c.active).toList();
                  if (active.isEmpty) {
                    return Text(
                      tr(context, 'treatmentPlanWizardNoCatalog',
                          'No catalog items in this clinic yet.'),
                      style: TextStyle(color: Colors.grey.shade600),
                    );
                  }
                  return Column(
                    children: active.map(_buildCatalogRow).toList(),
                  );
                },
              ),
            ],
          ),

          // Payment -----------------------------------------------------------
          _Section(
            title: tr(context, 'treatmentPlanWizardSectionPayment', 'Payment'),
            children: [
              _payRadio(
                _WizardPayMode.unpaid,
                tr(context, 'treatmentPlanWizardPayUnpaid', 'Leave unpaid'),
              ),
              _payRadio(
                _WizardPayMode.full,
                tr(context, 'treatmentPlanWizardPayFull',
                    'Pay in full now (clinic payment)'),
              ),
              _payRadio(
                _WizardPayMode.installments,
                tr(context, 'treatmentPlanWizardPayInstallments',
                    'Installments (custom schedule)'),
              ),
              if (_payMode == _WizardPayMode.full) ...[
                const SizedBox(height: 8),
                DropdownButtonFormField<String>(
                  initialValue: _payMethod,
                  decoration: InputDecoration(
                    labelText: tr(
                        context, 'treatmentPlanWizardMethod', 'Payment method'),
                  ),
                  items: const [
                    DropdownMenuItem(value: 'CASH', child: Text('CASH')),
                    DropdownMenuItem(value: 'TRANSFER', child: Text('TRANSFER')),
                    DropdownMenuItem(
                        value: 'CARD_EXTERNAL', child: Text('CARD')),
                  ],
                  onChanged: (v) => setState(() => _payMethod = v ?? 'CASH'),
                ),
                const SizedBox(height: 8),
                TextField(
                  controller: _payMemoCtrl,
                  decoration: InputDecoration(
                    labelText:
                        tr(context, 'treatmentPlanWizardMemo', 'Memo'),
                  ),
                ),
              ],
              if (_payMode == _WizardPayMode.installments) ...[
                const SizedBox(height: 8),
                Text(
                  tr(context, 'treatmentPlanWizardInstallHint',
                      'Due date YYYY-MM-DD, amount in major currency units'),
                  style: TextStyle(color: Colors.grey.shade600),
                ),
                const SizedBox(height: 6),
                ...List.generate(_instDueCtrls.length, (i) {
                  return Padding(
                    padding: const EdgeInsets.only(bottom: 8),
                    child: Row(
                      children: [
                        Expanded(
                          child: TextField(
                            controller: _instDueCtrls[i],
                            decoration: const InputDecoration(
                              labelText: 'Due (YYYY-MM-DD)',
                            ),
                          ),
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          child: TextField(
                            controller: _instAmtCtrls[i],
                            decoration:
                                const InputDecoration(labelText: 'Amount'),
                            keyboardType: const TextInputType.numberWithOptions(
                                decimal: true),
                          ),
                        ),
                      ],
                    ),
                  );
                }),
                Align(
                  alignment: Alignment.centerLeft,
                  child: TextButton.icon(
                    onPressed: () => setState(() {
                      _instDueCtrls.add(TextEditingController());
                      _instAmtCtrls.add(TextEditingController());
                    }),
                    icon: const Icon(Icons.add),
                    label: Text(tr(context, 'treatmentPlanWizardAddRow',
                        'Add installment row')),
                  ),
                ),
              ],
            ],
          ),
          const SizedBox(height: 12),
        ],
      ),
    );
  }

  Widget _payRadio(_WizardPayMode value, String label) {
    return RadioListTile<_WizardPayMode>(
      title: Text(label),
      value: value,
      // ignore: deprecated_member_use
      groupValue: _payMode,
      // ignore: deprecated_member_use
      onChanged: (v) => setState(() => _payMode = v ?? _WizardPayMode.unpaid),
      contentPadding: EdgeInsets.zero,
      dense: true,
    );
  }

  Widget _buildCatalogRow(ClinicCatalogItem c) {
    final picked = _catalogPick[c.id] ?? false;
    final qty = _catalogQty[c.id] ?? 1;
    final apptId = _catalogAppt[c.id];

    return Container(
      margin: const EdgeInsets.only(bottom: 6),
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
      decoration: BoxDecoration(
        border: Border.all(color: Colors.grey.shade200),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Checkbox(
                value: picked,
                onChanged: (v) => setState(() {
                  _catalogPick[c.id] = v ?? false;
                  _catalogQty[c.id] = qty;
                }),
              ),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(c.title,
                        style: const TextStyle(fontWeight: FontWeight.w500)),
                    Text(
                      '${(c.defaultPriceMinor / 100).toStringAsFixed(0)} ${c.currency}',
                      style: TextStyle(
                          fontSize: 12, color: Colors.grey.shade700),
                    ),
                  ],
                ),
              ),
              SizedBox(
                width: 72,
                child: TextFormField(
                  initialValue: '$qty',
                  enabled: picked,
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(
                      labelText: 'Qty', isDense: true),
                  onChanged: (s) =>
                      _catalogQty[c.id] = int.tryParse(s) ?? 1,
                ),
              ),
            ],
          ),
          if (picked)
            Padding(
              padding: const EdgeInsets.only(left: 48, top: 4, bottom: 4),
              child: DropdownButtonFormField<int?>(
                initialValue: apptId,
                isExpanded: true,
                decoration: InputDecoration(
                  labelText: tr(context, 'treatmentPlanWizardLineAppt',
                      'Link to visit (optional)'),
                  isDense: true,
                ),
                items: [
                  DropdownMenuItem<int?>(
                    value: null,
                    child: Text(tr(context, 'treatmentPlanWizardLineApptNone',
                        '— no visit —')),
                  ),
                  ..._patientAppts.map(
                    (a) => DropdownMenuItem<int?>(
                      value: a.id,
                      child: Text(
                        '${_shortDate(a.startAt)} · ${a.doctorName}',
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                  ),
                ],
                onChanged: (v) => setState(() => _catalogAppt[c.id] = v),
              ),
            ),
        ],
      ),
    );
  }

  String _shortDate(String iso) {
    try {
      final dt = DateTime.parse(iso).toLocal();
      final mm = dt.month.toString().padLeft(2, '0');
      final dd = dt.day.toString().padLeft(2, '0');
      final hh = dt.hour.toString().padLeft(2, '0');
      final mi = dt.minute.toString().padLeft(2, '0');
      return '${dt.year}-$mm-$dd $hh:$mi';
    } catch (_) {
      return iso;
    }
  }
}

class _Section extends StatelessWidget {
  final String title;
  final String? subtitle;
  final List<Widget> children;

  const _Section({
    required this.title,
    this.subtitle,
    required this.children,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(
            title,
            style: Theme.of(context)
                .textTheme
                .titleSmall
                ?.copyWith(fontWeight: FontWeight.w600),
          ),
          if (subtitle != null) ...[
            const SizedBox(height: 2),
            Text(
              subtitle!,
              style: TextStyle(fontSize: 12, color: Colors.grey.shade600),
            ),
          ],
          const SizedBox(height: 8),
          ...children,
        ],
      ),
    );
  }
}
