import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/layout/responsive.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_finance_actions.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_finance_providers.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_providers.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_treatment_plan_providers.dart';

/// Manual / helper entry point for Finance → Records.
Future<void> showClinicFinanceRecordDialog({
  required BuildContext context,
  required WidgetRef ref,
  required int clinicId,
}) =>
    showDialog<void>(
      context: context,
      builder: (ctx) => _ClinicFinanceRecordDialog(clinicId: clinicId),
    );

class _ClinicFinanceRecordDialog extends ConsumerStatefulWidget {
  final int clinicId;

  const _ClinicFinanceRecordDialog({required this.clinicId});

  @override
  ConsumerState<_ClinicFinanceRecordDialog> createState() =>
      _ClinicFinanceRecordDialogState();
}

class _ClinicFinanceRecordDialogState extends ConsumerState<_ClinicFinanceRecordDialog> {
  final _subCtrl = TextEditingController(text: '0');
  final _discCtrl = TextEditingController(text: '0');
  final _taxCtrl = TextEditingController(text: '0');
  final _notesCtrl = TextEditingController();
  DateTime? _dueDate;

  String _recordType = 'INVOICE';
  int? _patientId;
  int? _planId;

  bool _busy = false;

  static final _types = ['INVOICE', 'RECEIPT', 'ESTIMATE', 'CREDIT_NOTE'];

  static int _majorUnitsToMinor(String raw) {
    var s = raw.trim().replaceAll(' ', '').replaceAll(',', '');
    if (s.isEmpty) return 0;
    s = s.replaceAll(',', '.');
    final asDouble = double.tryParse(s);
    if (asDouble != null) return (asDouble * 100).round();
    final asInt = int.tryParse(raw.trim().replaceAll(' ', '').replaceAll(',', ''));
    return (asInt ?? 0) * 100;
  }

  @override
  void dispose() {
    _subCtrl.dispose();
    _discCtrl.dispose();
    _taxCtrl.dispose();
    _notesCtrl.dispose();
    super.dispose();
  }

  String? _dueDateIso() {
    if (_dueDate == null) return null;
    final d = _dueDate!;
    return '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';
  }

  Future<void> _submit() async {
    final l10n = AppLocalizations.of(context)!;
    final pid = _patientId;
    if (pid == null) {
      ScaffoldMessenger.maybeOf(context)?.showSnackBar(
        SnackBar(content: Text(l10n.translate('required'))),
      );
      return;
    }
    setState(() => _busy = true);
    try {
      if (_planId != null) {
        await createFinancialRecord(
          ref,
          clinicId: widget.clinicId,
          patientId: pid,
          treatmentPlanId: _planId,
          recordType: _recordType,
          dueDate: _dueDateIso(),
          notes: _notesCtrl.text.trim().isEmpty ? null : _notesCtrl.text.trim(),
        );
      } else {
        await createFinancialRecord(
          ref,
          clinicId: widget.clinicId,
          patientId: pid,
          recordType: _recordType,
          subtotalMinor: _majorUnitsToMinor(_subCtrl.text),
          discountMinor: _majorUnitsToMinor(_discCtrl.text),
          taxMinor: _majorUnitsToMinor(_taxCtrl.text),
          dueDate: _dueDateIso(),
          notes: _notesCtrl.text.trim().isEmpty ? null : _notesCtrl.text.trim(),
        );
      }
      refreshClinicFinancialData(ref, widget.clinicId);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(l10n.translate('clinicRecordsFormSuccess'))),
      );
      Navigator.of(context).pop();
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          backgroundColor: Colors.red,
          content: Text('${l10n.translate('clinicRecordsFormFailed')}: $e'),
        ),
      );
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final patientsAsync = ref.watch(clinicPatientsFirstPageProvider(widget.clinicId));
    final formBody = _buildFormBody(context, l10n, patientsAsync);
    final actions = [
      TextButton(
        onPressed: _busy ? null : () => Navigator.pop(context),
        child: Text(l10n.cancel),
      ),
      FilledButton(
        onPressed: _busy ? null : _submit,
        child: _busy
            ? const SizedBox(
                width: 18,
                height: 18,
                child: CircularProgressIndicator(strokeWidth: 2),
              )
            : Text(l10n.translate('clinicRecordsFormCreate')),
      ),
    ];

    if (Responsive.isMobile(context)) {
      return Dialog.fullscreen(
        child: Scaffold(
          appBar: AppBar(
            title: Text(l10n.translate('clinicRecordsFormTitle')),
            leading: IconButton(
              icon: const Icon(Icons.close),
              onPressed: _busy ? null : () => Navigator.pop(context),
            ),
          ),
          body: SingleChildScrollView(
            padding: const EdgeInsets.all(16),
            child: formBody,
          ),
          bottomNavigationBar: SafeArea(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.end,
                children: actions,
              ),
            ),
          ),
        ),
      );
    }

    return AlertDialog(
      title: Text(l10n.translate('clinicRecordsFormTitle')),
      content: SizedBox(
        width: Responsive.dialogMaxWidth(context),
        child: SingleChildScrollView(child: formBody),
      ),
      actions: actions,
    );
  }

  Widget _buildFormBody(
    BuildContext context,
    AppLocalizations l10n,
    AsyncValue<ClinicPatientsPage> patientsAsync,
  ) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
            patientsAsync.when(
              data: (page) => DropdownButtonFormField<int?>(
                key: ValueKey<Object?>(_patientId),
                decoration:
                    InputDecoration(labelText: l10n.translate('clinicRecordsFormPatient')),
                initialValue: _patientId,
                items: [
                  for (final p in page.content)
                    DropdownMenuItem<int?>(
                      value: p.patientId,
                      child: Text(
                        p.fullName.isEmpty
                            ? l10n
                                .translate('clinicPatientNumber')
                                .replaceAll('{{id}}', '${p.patientId}')
                            : p.fullName,
                      ),
                    ),
                ],
                onChanged: _busy
                    ? null
                    : (v) => setState(() {
                          _patientId = v;
                          _planId = null;
                        }),
              ),
              loading: () => const LinearProgressIndicator(),
              error: (e, _) => Text('$e'),
            ),
            const SizedBox(height: 12),
            if (_patientId != null)
              Consumer(
                builder: (context, cref, _) {
                  final plansAsync = cref.watch(
                    treatmentPlansForPatientProvider(<int>[
                      widget.clinicId,
                      _patientId!,
                    ]),
                  );
                  return plansAsync.when(
                    loading: () => const Padding(
                      padding: EdgeInsets.symmetric(vertical: 8),
                      child: LinearProgressIndicator(),
                    ),
                    error: (e, _) => Text('$e'),
                    data: (plans) => DropdownButtonFormField<int?>(
                      key: ValueKey<int?>(_planId),
                      decoration: InputDecoration(
                        labelText: l10n.translate('clinicRecordsFormPlanOptional'),
                      ),
                      initialValue: _planId,
                      items: [
                        const DropdownMenuItem<int?>(value: null, child: Text('—')),
                        for (final pl in plans)
                          DropdownMenuItem<int?>(
                            value: pl.id,
                            child: Text(
                              (pl.title != null && pl.title!.trim().isNotEmpty)
                                  ? '${pl.title} (#${pl.id})'
                                  : '#${pl.id}',
                            ),
                          ),
                      ],
                      onChanged: _busy ? null : (v) => setState(() => _planId = v),
                    ),
                  );
                },
              ),
            if (_patientId != null) const SizedBox(height: 12),
            DropdownButtonFormField<String>(
              key: ValueKey<String>(_recordType),
              decoration: InputDecoration(labelText: l10n.translate('clinicRecordsFormType')),
              initialValue: _recordType,
              items: [
                for (final t in _types)
                  DropdownMenuItem(
                    value: t,
                    child: Text(l10n.clinicRecordTypeLabel(t)),
                  ),
              ],
              onChanged: _busy ? null : (v) => setState(() => _recordType = v ?? _recordType),
            ),
            const SizedBox(height: 12),
            if (_planId == null) ...[
              TextField(
                controller: _subCtrl,
                keyboardType: const TextInputType.numberWithOptions(decimal: true),
                inputFormatters: [FilteringTextInputFormatter.allow(RegExp(r'^[0-9.,\-]*$'))],
                decoration: InputDecoration(labelText: l10n.translate('clinicRecordsFormSubtotal')),
              ),
              const SizedBox(height: 8),
              TextField(
                controller: _discCtrl,
                keyboardType: const TextInputType.numberWithOptions(decimal: true),
                inputFormatters: [FilteringTextInputFormatter.allow(RegExp(r'^[0-9.,\-]*$'))],
                decoration: InputDecoration(labelText: l10n.translate('clinicRecordsFormDiscount')),
              ),
              const SizedBox(height: 8),
              TextField(
                controller: _taxCtrl,
                keyboardType: const TextInputType.numberWithOptions(decimal: true),
                inputFormatters: [FilteringTextInputFormatter.allow(RegExp(r'^[0-9.,\-]*$'))],
                decoration: InputDecoration(labelText: l10n.translate('clinicRecordsFormTax')),
              ),
            ] else
              Padding(
                padding: const EdgeInsets.only(top: 8, bottom: 8),
                child: Text(
                  l10n.translate('clinicRecordsFormPlanTotalsHint'),
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(color: Colors.grey.shade700),
                ),
              ),
            ListTile(
              contentPadding: EdgeInsets.zero,
              title: Text(l10n.translate('clinicRecordsFormDueDate')),
              subtitle: Text(
                _dueDate == null
                    ? '—'
                    : '${_dueDate!.day.toString().padLeft(2, '0')}.'
                      '${_dueDate!.month.toString().padLeft(2, '0')}.${_dueDate!.year}',
              ),
              trailing: IconButton(
                icon: const Icon(Icons.calendar_today_outlined),
                onPressed: _busy
                    ? null
                    : () async {
                        final now = DateTime.now();
                        final d = await showDatePicker(
                          context: context,
                          initialDate: now,
                          firstDate: DateTime(now.year - 1),
                          lastDate: DateTime(now.year + 5),
                        );
                        if (d != null) setState(() => _dueDate = d);
                      },
              ),
            ),
            TextField(
              controller: _notesCtrl,
              maxLines: 2,
              decoration: InputDecoration(labelText: l10n.translate('clinicRecordsFormNotes')),
            ),
      ],
    );
  }
}
