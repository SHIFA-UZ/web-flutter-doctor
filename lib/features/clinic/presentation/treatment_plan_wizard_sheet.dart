import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:shifa_doc_app_v1/core/api/api_providers.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_finance_actions.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_finance_providers.dart';
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
  /// Cooldown (days) between automatic outstanding-balance reminders sent to
  /// the patient. Selected from a 1..30 dropdown; defaults to 3.
  int _reminderDays = 3;

  // Care team: explicit list of doctors that will work on this plan.
  // The first id in this list is also used as the legacy `attendingDoctorId`.
  final List<int> _selectedDoctorIds = [];

  // New free slots picked through this wizard, keyed by doctor id.
  // Each entry is saved to the backend with one "book-slots" call right
  // before the wizard closes so the plan + visits + lines are atomic.
  final Map<int, List<_PickedSlot>> _doctorSlots = {};

  // Services + per-line appointment link.
  //
  // Keyed by [PlanServiceOption.key] so the picker can hold both clinic
  // catalog rows (`catalog:<id>`) and doctor-only profile services
  // (`doctor:<docId>:service:<svcId>`) in the same state without colliding.
  final Map<String, bool> _servicePick = {};
  final Map<String, int> _serviceQty = {};
  /// Per-service "Link to visit" selection. Either an existing appointment id
  /// (kind = `existing`) or a tentative tempId pointing at a [_PickedSlot]
  /// from [_doctorSlots] (kind = `tentative`).
  final Map<String, _LineLinkRef?> _serviceLink = {};

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

  /// Stable family key for plan services that honours the current attending
  /// doctor selection. When no doctors are picked the wizard shows the full
  /// clinic catalog (helpful before the care team is filled in).
  PlanServicesKey get _planServicesKey =>
      PlanServicesKey(clinicId: widget.clinicId, doctorIds: _selectedDoctorIds);

  /// Synchronously read the current plan-services list from the provider
  /// cache. Returns an empty list when not yet loaded; callers either await
  /// the future or fall back to an empty result.
  List<PlanServiceOption> _readPlanServices() {
    return ref.read(planServicesProvider(_planServicesKey)).valueOrNull ??
        const <PlanServiceOption>[];
  }

  /// Builds the line payload for `POST /api/treatment-plans/{id}/lines`.
  ///
  /// Returns `(requests, serviceKeyByOrder)` so the save flow can later map a
  /// picked service key → freshly-created line id after re-fetching the plan
  /// detail. Lines linked to a tentative slot leave `linkedAppointmentId`
  /// unset; the link is established afterwards via `/book-slots` with
  /// `lineId`.
  ///
  /// Doctor-only services have no clinic catalog id; for those we omit
  /// `catalogItemId` from the payload (backend already accepts a null
  /// `catalogItemId` and stores the line with its denormalised title/price).
  (List<Map<String, dynamic>>, List<String>) _buildLineRequests(
    List<PlanServiceOption> services,
  ) {
    final lines = <Map<String, dynamic>>[];
    final serviceKeyByOrder = <String>[];
    var order = 0;
    for (final key in _servicePick.keys.where((k) => _servicePick[k] == true)) {
      PlanServiceOption? item;
      for (final s in services) {
        if (s.key == key) {
          item = s;
          break;
        }
      }
      if (item == null) continue;
      final qty = _serviceQty[key] ?? 1;
      if (qty < 1) continue;
      final ref = _serviceLink[key];
      final existingApptId =
          (ref != null && ref.existing) ? ref.appointmentId : null;
      lines.add({
        if (item.catalogItemId != null) 'catalogItemId': item.catalogItemId,
        'title': item.title,
        'quantity': qty,
        'unitPriceMinor': item.defaultPriceMinor,
        'discountMinor': 0,
        'currency': item.currency,
        'sortOrder': order,
        if (existingApptId != null) 'linkedAppointmentId': existingApptId,
      });
      serviceKeyByOrder.add(key);
      order += 1;
    }
    return (lines, serviceKeyByOrder);
  }

  /// Live total (minor units) for the currently picked services. Used by the
  /// installment editor so amounts can be checked against the plan total
  /// without having to first save the plan.
  int _liveTotalMinor() {
    final services = _readPlanServices();
    var total = 0;
    for (final key in _servicePick.keys.where((k) => _servicePick[k] == true)) {
      PlanServiceOption? item;
      for (final s in services) {
        if (s.key == key) {
          item = s;
          break;
        }
      }
      if (item == null) continue;
      final qty = _serviceQty[key] ?? 1;
      if (qty < 1) continue;
      total += item.defaultPriceMinor * qty;
    }
    return total;
  }

  String _liveCurrency() {
    final services = _readPlanServices();
    for (final key in _servicePick.keys.where((k) => _servicePick[k] == true)) {
      for (final s in services) {
        if (s.key == key) return s.currency;
      }
    }
    return 'UZS';
  }

  /// Sum of installment rows the user has already filled in (in minor units).
  int _allocatedMinor() {
    var sum = 0;
    for (final c in _instAmtCtrls) {
      final v = double.tryParse(c.text.trim().replaceAll(',', '.'));
      if (v == null || v <= 0) continue;
      sum += (v * 100).round();
    }
    return sum;
  }

  /// Total - already allocated. Floored at 0 so the user never sees a
  /// negative remaining label.
  int _remainingMinor() {
    final r = _liveTotalMinor() - _allocatedMinor();
    return r < 0 ? 0 : r;
  }

  String _formatMoney(int minor, String currency) {
    final v = minor / 100;
    return '${v.toStringAsFixed(2)} $currency';
  }

  /// Returns major units (e.g. 250.00) as a string with no trailing zeros so
  /// the user can edit it cleanly. `0` returns empty string.
  String _minorToFieldString(int minor) {
    if (minor <= 0) return '';
    final v = minor / 100;
    if (v == v.roundToDouble()) return v.toStringAsFixed(0);
    return v.toStringAsFixed(2);
  }

  Future<void> _pickInstallmentDate(int index) async {
    final now = DateTime.now();
    DateTime initial;
    final current = _instDueCtrls[index].text.trim();
    try {
      initial = current.isEmpty ? now : DateTime.parse(current);
    } catch (_) {
      initial = now;
    }
    final picked = await showDatePicker(
      context: context,
      initialDate: initial.isBefore(now) ? now : initial,
      firstDate: now.subtract(const Duration(days: 1)),
      lastDate: DateTime(now.year + 5),
    );
    if (picked == null) return;
    final mm = picked.month.toString().padLeft(2, '0');
    final dd = picked.day.toString().padLeft(2, '0');
    setState(() => _instDueCtrls[index].text = '${picked.year}-$mm-$dd');
  }

  void _addInstallmentRow() {
    final remaining = _remainingMinor();
    setState(() {
      _instDueCtrls.add(TextEditingController());
      _instAmtCtrls.add(
        TextEditingController(text: _minorToFieldString(remaining)),
      );
    });
  }

  void _removeInstallmentRow(int index) {
    if (_instAmtCtrls.length <= 2) return;
    setState(() {
      _instDueCtrls.removeAt(index).dispose();
      _instAmtCtrls.removeAt(index).dispose();
    });
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

    // Snapshot the current visible plan-services list. If it isn't loaded
    // yet (user clicked Save before the provider resolved) await it so we
    // don't silently drop selected lines.
    var services = _readPlanServices();
    if (services.isEmpty) {
      try {
        services =
            await ref.read(planServicesProvider(_planServicesKey).future);
      } catch (_) {
        services = const <PlanServiceOption>[];
      }
    }
    final (lineReqs, serviceKeysByOrder) = _buildLineRequests(services);
    if (lineReqs.isEmpty) {
      if (!mounted) return;
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
        if (!mounted) return;
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
      final reminder = _reminderDays;
      final primaryDoctorId =
          _selectedDoctorIds.isEmpty ? null : _selectedDoctorIds.first;
      final created = await createTreatmentPlan(
        ref,
        clinicId: widget.clinicId,
        patientId: pid,
        title: title,
        diagnosis:
            _diagnosisCtrl.text.trim().isEmpty ? null : _diagnosisCtrl.text.trim(),
        notes: _notesCtrl.text.trim().isEmpty ? null : _notesCtrl.text.trim(),
        paymentReminderDays: reminder,
        attendingDoctorId: primaryDoctorId,
        attendingDoctorIds: _selectedDoctorIds.isEmpty
            ? null
            : List<int>.from(_selectedDoctorIds),
      );
      if (created == null) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(
                tr(
                  context,
                  'treatmentPlanWizardCouldNotCreatePlan',
                  'Could not create plan',
                ),
              ),
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
            SnackBar(
              content: Text(
                tr(
                  context,
                  'treatmentPlanWizardCouldNotSaveServices',
                  'Could not save services',
                ),
              ),
            ),
          );
        }
        return;
      }

      // Now that lines exist, book any tentative slots and (when a service
      // line points at one) link the new appointment back to its line.
      final allPicked = _allPickedSlots();
      if (allPicked.isNotEmpty) {
        // Map picked-service-key -> created lineId. The backend appends rows
        // in `sortOrder`, mirroring the order we sent them in (and therefore
        // `serviceKeysByOrder`), so the i-th picked service maps to the i-th
        // line. This works for both clinic catalog rows and doctor-only
        // services (which have no catalogItemId).
        var detailForLines = await fetchTreatmentPlanDetail(ref, planId);
        final lineIdByServiceKey = <String, int>{};
        if (detailForLines != null) {
          final lines = detailForLines.lines;
          for (var i = 0;
              i < serviceKeysByOrder.length && i < lines.length;
              i++) {
            lineIdByServiceKey[serviceKeysByOrder[i]] = lines[i].id;
          }
        }
        // Picked service key per slot tempId (one slot can power at most one
        // service line).
        final serviceKeyForSlot = <String, String>{};
        for (final entry in _serviceLink.entries) {
          final v = entry.value;
          if (v == null || v.existing || v.slotTempId == null) continue;
          serviceKeyForSlot[v.slotTempId!] = entry.key;
        }
        final slotRequests = allPicked.map((s) {
          final svcKey = serviceKeyForSlot[s.tempId];
          final lineId = svcKey == null ? null : lineIdByServiceKey[svcKey];
          return <String, dynamic>{
            'doctorId': s.doctorId,
            'startAt': s.startAt,
            'slotMinutes': s.slotMinutes,
            if (s.locationId != null) 'locationId': s.locationId,
            if (lineId != null) 'lineId': lineId,
            'notes': title,
          };
        }).toList();
        try {
          await bookTreatmentPlanSlots(
            ref,
            planId: planId,
            slots: slotRequests,
          );
        } catch (e) {
          if (mounted) {
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(
                content: Text(
                  '${tr(context, 'treatmentPlanWizardSlotBookFailed', 'Could not book some visits')}: $e',
                ),
              ),
            );
          }
          // Continue with the rest of the save: the plan + lines already
          // exist; the doctor can retry booking from the plan detail later.
        }
      }

      final detail = await fetchTreatmentPlanDetail(ref, planId);
      final owed = detail?.summary.owedMinor ?? summary.owedMinor;
      final currency = detail?.summary.currency ?? summary.currency;

      if (_payMode == _WizardPayMode.full && owed > 0) {
        try {
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
        } catch (e) {
          if (mounted) {
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(
                content: Text(
                  '${tr(context, 'treatmentPlanWizardPaymentFailed', 'Could not record payment')}: $e',
                ),
                backgroundColor: Colors.red.shade800,
              ),
            );
          }
        }
      } else if (_payMode == _WizardPayMode.installments &&
          installmentRows != null &&
          owed > 0) {
        // Cross-check the user's installment sum against the actual plan total
        // *before* hitting the backend. This both prevents a doomed POST and
        // gives a clear, immediate error if the rows don't balance.
        final installmentSum = installmentRows.fold<int>(
          0,
          (acc, row) => acc + ((row['amountMinor'] as num?)?.toInt() ?? 0),
        );
        if (installmentSum != owed) {
          if (mounted) {
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(
                content: Text(
                  '${tr(context, 'treatmentPlanWizardInstallSumMismatch', 'Installment total does not match plan total')}: '
                  '${_formatMoney(installmentSum, currency)} vs ${_formatMoney(owed, currency)}',
                ),
              ),
            );
          }
        } else {
          final start = installmentRows.first['dueDate'] as String;
          try {
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
          } catch (e) {
            if (mounted) {
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(
                  content: Text(
                    '${tr(context, 'treatmentPlanWizardInstallFailed', 'Plan saved but installment schedule could not be created')}: $e',
                  ),
                ),
              );
            }
          }
        }
      }

      await patchTreatmentPlanStatus(ref, planId: planId, status: 'ACTIVE');
      ref.invalidate(treatmentPlansForPatientProvider([widget.clinicId, pid]));
      ref.invalidate(treatmentPlansForClinicProvider);
      ref.invalidate(clinicFinanceDashboardProvider(widget.clinicId));
      ref.invalidate(clinicInstallmentItemsProvider);
      ref.invalidate(clinicOverdueProvider(widget.clinicId));

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
    // Unified service catalog (clinic catalog + doctor-only profile services)
    // filtered by the currently selected attending doctors. When no doctors
    // are selected the picker shows the full clinic catalog so users can
    // start picking services before naming the care team.
    final planServicesAsync =
        ref.watch(planServicesProvider(_planServicesKey));

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
                l10n
                    .translate('treatmentPlanWizardStep')
                    .replaceAll('{{current}}', '${_phase + 1}')
                    .replaceAll('{{total}}', '2'),
                style: const TextStyle(color: Colors.black54),
              ),
              const SizedBox(height: 12),
              Expanded(
                child: _phase == 0
                    ? _buildPatientPicker(context)
                    : _buildPlanForm(
                        context,
                        l10n,
                        membersAsync,
                        planServicesAsync,
                      ),
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
    AsyncValue<List<PlanServiceOption>> planServicesAsync,
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
              DropdownButtonFormField<int>(
                initialValue: _reminderDays,
                isExpanded: true,
                decoration: InputDecoration(
                  labelText: tr(context, 'treatmentPlanWizardReminderDays',
                      'Payment reminder (days)'),
                  helperText: tr(
                    context,
                    'treatmentPlanWizardReminderDaysHelp',
                    'How often the patient is reminded about an unpaid balance.',
                  ),
                ),
                items: [
                  for (var d = 1; d <= 30; d++)
                    DropdownMenuItem<int>(
                      value: d,
                      child: Text(
                        d == 1
                            ? tr(context, 'treatmentPlanWizardReminderDay1',
                                'Every day')
                            : tr(
                                  context,
                                  'treatmentPlanWizardReminderDaysN',
                                  'Every {n} days',
                                ).replaceAll('{n}', '$d'),
                      ),
                    ),
                ],
                onChanged: (v) {
                  if (v == null) return;
                  setState(() => _reminderDays = v);
                },
              ),
            ],
          ),

          // Care team & scheduled visits --------------------------------------
          _Section(
            title: tr(context, 'treatmentPlanWizardSectionCareTeam',
                'Care team & visits'),
            subtitle: tr(
              context,
              'treatmentPlanWizardSectionCareTeamHint',
              'Add every doctor involved. Pick free slots per doctor to schedule visits for this patient.',
            ),
            children: [
              _buildCareTeamSection(context, membersAsync),
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
              planServicesAsync.when(
                loading: () => const Center(child: CircularProgressIndicator()),
                error: (e, _) => Text('$e'),
                data: (items) {
                  final active = items.where((c) => c.active).toList();
                  if (active.isEmpty) {
                    // Distinguish "clinic has nothing" from "filtered set is
                    // empty"; if the user has chosen attending doctors but
                    // none of them offers any service, that's actionable
                    // feedback ("pick more doctors or add services").
                    final filteredByDoctors = _selectedDoctorIds.isNotEmpty;
                    return Text(
                      filteredByDoctors
                          ? tr(
                              context,
                              'treatmentPlanWizardNoServicesForDoctors',
                              'No services available for the selected '
                              'doctor(s). Pick more doctors or add services '
                              'in Clinic → Services or in the doctor\'s '
                              'profile.',
                            )
                          : tr(context, 'treatmentPlanWizardNoCatalog',
                              'No catalog items in this clinic yet.'),
                      style: TextStyle(color: Colors.grey.shade600),
                    );
                  }
                  return Column(
                    children:
                        active.map(_buildPlanServiceRow).toList(),
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
                  items: [
                    DropdownMenuItem(
                      value: 'CASH',
                      child: Text(l10n.clinicPaymentMethodLabel('CASH')),
                    ),
                    DropdownMenuItem(
                      value: 'TRANSFER',
                      child: Text(l10n.clinicPaymentMethodLabel('TRANSFER')),
                    ),
                    DropdownMenuItem(
                      value: 'CARD_EXTERNAL',
                      child: Text(
                        l10n.clinicPaymentMethodLabel('CARD_EXTERNAL'),
                      ),
                    ),
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
                _buildInstallmentSummary(context),
                const SizedBox(height: 8),
                ...List.generate(_instDueCtrls.length, (i) {
                  return _buildInstallmentRow(context, i);
                }),
                Align(
                  alignment: Alignment.centerLeft,
                  child: TextButton.icon(
                    onPressed: _addInstallmentRow,
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

  Widget _buildInstallmentSummary(BuildContext context) {
    final currency = _liveCurrency();
    final total = _liveTotalMinor();
    final allocated = _allocatedMinor();
    final remaining = total - allocated;
    final overshoot = remaining < 0;

    Color remainingColor() {
      if (total <= 0) return Colors.grey.shade600;
      if (overshoot) return Colors.red.shade700;
      if (remaining == 0) return Colors.green.shade700;
      return Colors.orange.shade700;
    }

    Widget cell(String label, String value, {Color? valueColor}) {
      return Expanded(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(label,
                style: TextStyle(fontSize: 11, color: Colors.grey.shade600)),
            Text(
              value,
              style: TextStyle(
                fontWeight: FontWeight.w600,
                color: valueColor,
                fontFeatures: const [FontFeature.tabularFigures()],
              ),
            ),
          ],
        ),
      );
    }

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        color: Colors.grey.shade50,
        border: Border.all(color: Colors.grey.shade200),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(
        children: [
          cell(
            tr(context, 'treatmentPlanWizardInstallTotal', 'Plan total'),
            _formatMoney(total, currency),
          ),
          cell(
            tr(context, 'treatmentPlanWizardInstallAllocated', 'Allocated'),
            _formatMoney(allocated, currency),
          ),
          cell(
            overshoot
                ? tr(context, 'treatmentPlanWizardInstallOver',
                    'Over by')
                : tr(context, 'treatmentPlanWizardInstallRemaining',
                    'Remaining'),
            _formatMoney(remaining.abs(), currency),
            valueColor: remainingColor(),
          ),
        ],
      ),
    );
  }

  Widget _buildInstallmentRow(BuildContext context, int i) {
    final dueText = _instDueCtrls[i].text.trim();
    final hasDate = dueText.isNotEmpty;
    final canRemove = _instAmtCtrls.length > 2;

    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Numbered chip
          Padding(
            padding: const EdgeInsets.only(top: 14, right: 8),
            child: Container(
              width: 22,
              height: 22,
              alignment: Alignment.center,
              decoration: BoxDecoration(
                color: Theme.of(context).colorScheme.primary.withValues(alpha: 0.1),
                borderRadius: BorderRadius.circular(11),
              ),
              child: Text(
                '${i + 1}',
                style: TextStyle(
                  fontSize: 11,
                  fontWeight: FontWeight.w600,
                  color: Theme.of(context).colorScheme.primary,
                ),
              ),
            ),
          ),
          // Due date picker (tap to open)
          Expanded(
            flex: 5,
            child: InkWell(
              borderRadius: BorderRadius.circular(4),
              onTap: () => _pickInstallmentDate(i),
              child: InputDecorator(
                decoration: InputDecoration(
                  labelText: tr(context, 'treatmentPlanWizardInstallDue',
                      'Due date'),
                  isDense: true,
                  suffixIcon: const Icon(Icons.calendar_today, size: 18),
                ),
                child: Text(
                  hasDate
                      ? dueText
                      : tr(context, 'treatmentPlanWizardInstallTapDate',
                          'Tap to pick'),
                  style: TextStyle(
                    color: hasDate ? null : Colors.grey.shade500,
                  ),
                ),
              ),
            ),
          ),
          const SizedBox(width: 8),
          // Amount (live-updates remaining)
          Expanded(
            flex: 4,
            child: TextField(
              controller: _instAmtCtrls[i],
              decoration: InputDecoration(
                labelText: tr(context, 'treatmentPlanWizardInstallAmount',
                    'Amount'),
                isDense: true,
              ),
              keyboardType: const TextInputType.numberWithOptions(decimal: true),
              onChanged: (_) => setState(() {}),
            ),
          ),
          // Remove row
          IconButton(
            tooltip: canRemove
                ? tr(context, 'treatmentPlanWizardInstallRemove',
                    'Remove row')
                : null,
            icon: Icon(Icons.delete_outline,
                color: canRemove ? Colors.grey.shade700 : Colors.grey.shade300),
            onPressed: canRemove ? () => _removeInstallmentRow(i) : null,
          ),
        ],
      ),
    );
  }

  // --- Care team & visits ---------------------------------------------------

  Widget _buildCareTeamSection(
    BuildContext context,
    AsyncValue<List<ClinicMember>> membersAsync,
  ) {
    return membersAsync.when(
      loading: () => const LinearProgressIndicator(minHeight: 2),
      error: (_, __) => Text(
        tr(context, 'treatmentPlanWizardMembersError',
            'Could not load clinic doctors.'),
      ),
      data: (members) {
        final doctorMembers = members
            .where((m) => m.doctorProfileId > 0)
            .toList()
          ..sort((a, b) => a.displayName
              .toLowerCase()
              .compareTo(b.displayName.toLowerCase()));
        if (doctorMembers.isEmpty) {
          return Text(
            tr(context, 'treatmentPlanWizardNoDoctors',
                'No doctors in this clinic.'),
            style: TextStyle(color: Colors.grey.shade600),
          );
        }
        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Multi-select chip list
            Wrap(
              spacing: 6,
              runSpacing: 6,
              children: doctorMembers.map((m) {
                final selected = _selectedDoctorIds.contains(m.doctorProfileId);
                return FilterChip(
                  label: Text(m.displayName),
                  selected: selected,
                  onSelected: (v) {
                    setState(() {
                      if (v) {
                        if (!_selectedDoctorIds.contains(m.doctorProfileId)) {
                          _selectedDoctorIds.add(m.doctorProfileId);
                        }
                      } else {
                        _selectedDoctorIds.remove(m.doctorProfileId);
                        // Removing a doctor also drops their picked slots and
                        // any per-line links that referenced them.
                        final removedSlots = _doctorSlots.remove(m.doctorProfileId);
                        if (removedSlots != null) {
                          final removedIds = removedSlots.map((s) => s.tempId).toSet();
                          _serviceLink.updateAll((_, ref) {
                            if (ref != null &&
                                !ref.existing &&
                                removedIds.contains(ref.slotTempId)) {
                              return null;
                            }
                            return ref;
                          });
                        }
                      }
                    });
                    // Doctor set changed → the visible plan-services list
                    // about to be loaded may no longer contain some picks
                    // (e.g. doctor-only services from a removed doctor).
                    // Prune those picks once the next snapshot arrives so the
                    // user never silently saves an invisible line.
                    _pruneStalePicks();
                  },
                );
              }).toList(),
            ),
            const SizedBox(height: 8),

            // Per-doctor slot picker
            ..._selectedDoctorIds.map((doctorId) {
              final m = doctorMembers.firstWhere(
                (m) => m.doctorProfileId == doctorId,
                orElse: () => ClinicMember(
                  doctorProfileId: doctorId,
                  userId: 0,
                  displayName: tr(context, 'treatmentPlanWizardDoctorNumber',
                          'Doctor #$doctorId')
                      .replaceAll('{{id}}', '$doctorId'),
                  membershipRole: 'DOCTOR',
                ),
              );
              final slots = _doctorSlots[doctorId] ?? const <_PickedSlot>[];
              return _DoctorVisitsRow(
                doctorName: m.displayName,
                slots: slots,
                onPick: () => _openSlotPickerForDoctor(doctorId, m.displayName),
                onRemoveSlot: (tempId) => _removePickedSlot(doctorId, tempId),
                tr: (key, fb) => tr(context, key, fb),
                shortDateTime: _shortDate,
              );
            }),
          ],
        );
      },
    );
  }

  Future<void> _openSlotPickerForDoctor(int doctorId, String doctorName) async {
    final now = DateTime.now();
    final day = await showDatePicker(
      context: context,
      initialDate: now,
      firstDate: now,
      lastDate: now.add(const Duration(days: 365)),
    );
    if (day == null || !mounted) return;
    final mm = day.month.toString().padLeft(2, '0');
    final dd = day.day.toString().padLeft(2, '0');
    final iso = '${day.year}-$mm-$dd';

    // Set of {startAt} for slots already picked on this doctor so the sheet
    // can hide them from the new pick list.
    final existingStarts = (_doctorSlots[doctorId] ?? const <_PickedSlot>[])
        .map((s) => s.startAt)
        .toSet();

    final picked = await showModalBottomSheet<List<_PickedSlot>>(
      context: context,
      isScrollControlled: true,
      builder: (ctx) => _FreeSlotsSheet(
        clinicId: widget.clinicId,
        doctorId: doctorId,
        doctorName: doctorName,
        dayIso: iso,
        alreadyPickedStarts: existingStarts,
        ref: ref,
        translate: (key, fb) => tr(ctx, key, fb),
      ),
    );
    if (picked == null || picked.isEmpty || !mounted) return;
    setState(() {
      final list = _doctorSlots.putIfAbsent(doctorId, () => <_PickedSlot>[]);
      list.addAll(picked);
    });
  }

  void _removePickedSlot(int doctorId, String tempId) {
    setState(() {
      _doctorSlots[doctorId]?.removeWhere((s) => s.tempId == tempId);
      _serviceLink.updateAll((_, ref) {
        if (ref != null && !ref.existing && ref.slotTempId == tempId) {
          return null;
        }
        return ref;
      });
    });
  }

  /// Drops picks/qty/links for services that are no longer in the visible
  /// plan-services list. Called after the attending doctor set changes; the
  /// new plan-services snapshot may not contain doctor-only services that
  /// belonged to a doctor the user just removed.
  ///
  /// Awaits the next snapshot (rather than reading the stale one) so a fresh
  /// fetch triggered by the doctor change has a chance to land first.
  void _pruneStalePicks() {
    // Use the future so we wait for the new (filtered) snapshot to land.
    ref
        .read(planServicesProvider(_planServicesKey).future)
        .then((services) {
      if (!mounted) return;
      final visibleKeys = services.map((s) => s.key).toSet();
      final stale = _servicePick.keys
          .where((k) => !visibleKeys.contains(k))
          .toList();
      if (stale.isEmpty) return;
      setState(() {
        for (final k in stale) {
          _servicePick.remove(k);
          _serviceQty.remove(k);
          _serviceLink.remove(k);
        }
      });
    }).catchError((_) {
      // Failed snapshot doesn't justify dropping user picks; just leave the
      // state untouched and let the next successful load handle it.
    });
  }

  /// Every tentative slot picked across all doctors, in render order.
  List<_PickedSlot> _allPickedSlots() {
    final out = <_PickedSlot>[];
    for (final id in _selectedDoctorIds) {
      out.addAll(_doctorSlots[id] ?? const <_PickedSlot>[]);
    }
    return out;
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

  Widget _buildPlanServiceRow(PlanServiceOption c) {
    final picked = _servicePick[c.key] ?? false;
    final qty = _serviceQty[c.key] ?? 1;
    final link = _serviceLink[c.key];

    // Build a unified key for the dropdown's `value`. Existing appointment
    // = 'a:<id>'. Tentative slot = 's:<tempId>'. null = "no visit".
    String? currentKey;
    if (link != null) {
      currentKey = link.existing ? 'a:${link.appointmentId}' : 's:${link.slotTempId}';
    }
    final tentativeSlots = _allPickedSlots();

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
                  _servicePick[c.key] = v ?? false;
                  _serviceQty[c.key] = qty;
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
                    // Doctor-attribution chips — let the user see which doctor
                    // this service comes from (especially useful when several
                    // attending doctors are picked and each has their own
                    // catalog).
                    Padding(
                      padding: const EdgeInsets.only(top: 4),
                      child: _serviceSourceChips(context, c),
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
                  decoration: InputDecoration(
                    labelText: tr(context, 'treatmentPlanWizardQty', 'Qty'),
                    isDense: true,
                  ),
                  onChanged: (s) =>
                      _serviceQty[c.key] = int.tryParse(s) ?? 1,
                ),
              ),
            ],
          ),
          if (picked)
            Padding(
              padding: const EdgeInsets.only(left: 48, top: 4, bottom: 4),
              child: DropdownButtonFormField<String?>(
                initialValue: currentKey,
                isExpanded: true,
                decoration: InputDecoration(
                  labelText: tr(context, 'treatmentPlanWizardLineAppt',
                      'Link to visit (optional)'),
                  isDense: true,
                ),
                items: [
                  DropdownMenuItem<String?>(
                    value: null,
                    child: Text(tr(context, 'treatmentPlanWizardLineApptNone',
                        '— no visit —')),
                  ),
                  // New, tentative slots picked through this wizard.
                  if (tentativeSlots.isNotEmpty) ...[
                    ...tentativeSlots.map(
                      (s) => DropdownMenuItem<String?>(
                        value: 's:${s.tempId}',
                        child: Text(
                          '${tr(context, 'treatmentPlanWizardSlotNewBadge', 'NEW')} · '
                          '${_shortDate(s.startAt)} · ${s.doctorName}',
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                    ),
                  ],
                  // Existing patient appointments.
                  ..._patientAppts.map(
                    (a) => DropdownMenuItem<String?>(
                      value: 'a:${a.id}',
                      child: Text(
                        '${_shortDate(a.startAt)} · ${a.doctorName}',
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                  ),
                ],
                onChanged: (v) => setState(() {
                  if (v == null) {
                    _serviceLink[c.key] = null;
                    return;
                  }
                  if (v.startsWith('a:')) {
                    final id = int.tryParse(v.substring(2));
                    _serviceLink[c.key] = id == null
                        ? null
                        : _LineLinkRef.existing(id);
                  } else if (v.startsWith('s:')) {
                    _serviceLink[c.key] = _LineLinkRef.tentative(v.substring(2));
                  }
                }),
              ),
            ),
        ],
      ),
    );
  }

  /// Source/owner chips below a service line. A doctor-owned service shows a
  /// single chip with the doctor name. A clinic catalog row applicable to a
  /// limited doctor subset shows one chip per offering doctor (so the user
  /// understands which selected attending doctors can actually deliver it);
  /// when the row applies to everyone we show a single "Clinic catalog" chip
  /// instead to avoid noisy "all doctors" lists.
  Widget _serviceSourceChips(BuildContext context, PlanServiceOption c) {
    final isDoctorOwned = c.isDoctorService;
    // When clinic catalog row offers to every clinic doctor we treat the chip
    // as a single neutral "Clinic catalog" marker — listing every doctor name
    // is just visual noise in that case.
    final allClinicWide = !isDoctorOwned &&
        c.offeredByDoctorIds.length >= 2 &&
        _selectedDoctorIds.isEmpty;
    final tags = (isDoctorOwned || !allClinicWide) && c.offeredByDoctorNames.isNotEmpty
        ? c.offeredByDoctorNames
            .map((n) => tr(context, 'treatmentPlanWizardServiceFromDoctor',
                    'From {{name}}')
                .replaceAll('{{name}}', n))
            .toList()
        : <String>[
            tr(context, 'treatmentPlanWizardServiceFromClinic',
                'Clinic catalog'),
          ];
    final color = isDoctorOwned ? Colors.indigo : Colors.teal;
    return Wrap(
      spacing: 4,
      runSpacing: 4,
      children: tags
          .map(
            (label) => Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
              decoration: BoxDecoration(
                color: color.withValues(alpha: 0.10),
                borderRadius: BorderRadius.circular(999),
                border: Border.all(
                  color: color.withValues(alpha: 0.30),
                  width: 0.5,
                ),
              ),
              child: Text(
                label,
                style: TextStyle(
                  color: color.shade800,
                  fontSize: 10,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ),
          )
          .toList(),
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

/// A free slot the doctor has tentatively picked during the wizard. Booked
/// against the backend only on Save (atomic with the plan + lines).
class _PickedSlot {
  /// Stable wizard-local identifier; used to wire this slot into the
  /// per-service "Link to visit" dropdown before any backend ids exist.
  final String tempId;
  final int doctorId;
  final String doctorName;
  final String startAt; // ISO instant
  final String endAt; // ISO instant
  final int slotMinutes;
  final int? locationId;
  final String? locationLabel;

  _PickedSlot({
    required this.tempId,
    required this.doctorId,
    required this.doctorName,
    required this.startAt,
    required this.endAt,
    required this.slotMinutes,
    this.locationId,
    this.locationLabel,
  });
}

/// What a service line is "linked to" in the wizard. Either an existing
/// appointment (id from the patient's calendar) or a tentative slot picked
/// during the wizard (resolved into a real appointment on Save).
class _LineLinkRef {
  final bool existing;
  final int? appointmentId;
  final String? slotTempId;

  const _LineLinkRef.existing(this.appointmentId)
      : existing = true,
        slotTempId = null;

  const _LineLinkRef.tentative(this.slotTempId)
      : existing = false,
        appointmentId = null;
}

/// One row inside the "Care team & visits" section: a doctor's name, a list
/// of currently-picked slot chips (with × to drop them), and a button to
/// open the day picker → free-slot sheet for that doctor.
class _DoctorVisitsRow extends StatelessWidget {
  final String doctorName;
  final List<_PickedSlot> slots;
  final VoidCallback onPick;
  final void Function(String tempId) onRemoveSlot;
  final String Function(String, String) tr;
  final String Function(String) shortDateTime;

  const _DoctorVisitsRow({
    required this.doctorName,
    required this.slots,
    required this.onPick,
    required this.onRemoveSlot,
    required this.tr,
    required this.shortDateTime,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(top: 8),
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
      decoration: BoxDecoration(
        color: Colors.grey.shade50,
        border: Border.all(color: Colors.grey.shade200),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  doctorName,
                  style: const TextStyle(fontWeight: FontWeight.w500),
                ),
              ),
              TextButton.icon(
                onPressed: onPick,
                icon: const Icon(Icons.event_available, size: 16),
                label: Text(tr('treatmentPlanWizardPickSlots', 'Pick free slots')),
              ),
            ],
          ),
          if (slots.isEmpty)
            Padding(
              padding: const EdgeInsets.only(top: 2, bottom: 2),
              child: Text(
                tr('treatmentPlanWizardNoSlotsPicked',
                    'No visits scheduled yet.'),
                style: TextStyle(fontSize: 12, color: Colors.grey.shade600),
              ),
            )
          else
            Padding(
              padding: const EdgeInsets.only(top: 6),
              child: Wrap(
                spacing: 6,
                runSpacing: 6,
                children: slots
                    .map(
                      (s) => InputChip(
                        label: Text(shortDateTime(s.startAt)),
                        onDeleted: () => onRemoveSlot(s.tempId),
                      ),
                    )
                    .toList(),
              ),
            ),
        ],
      ),
    );
  }
}

/// Modal sheet that loads a doctor's free slots for a single day and lets
/// the user multi-select them. Returns the picked slots when the user taps
/// Done, or null on cancel.
class _FreeSlotsSheet extends StatefulWidget {
  final int clinicId;
  final int doctorId;
  final String doctorName;
  final String dayIso;
  final Set<String> alreadyPickedStarts;
  final WidgetRef ref;
  final String Function(String, String) translate;

  const _FreeSlotsSheet({
    required this.clinicId,
    required this.doctorId,
    required this.doctorName,
    required this.dayIso,
    required this.alreadyPickedStarts,
    required this.ref,
    required this.translate,
  });

  @override
  State<_FreeSlotsSheet> createState() => _FreeSlotsSheetState();
}

class _FreeSlotsSheetState extends State<_FreeSlotsSheet> {
  bool _loadingLocations = true;
  bool _loadingSlots = false;
  String? _error;
  List<PlanDoctorLocationDto> _locations = [];
  int? _selectedLocationId;
  List<FreeSlotDto> _slots = [];
  final Set<String> _selected = {};

  @override
  void initState() {
    super.initState();
    _loadLocations();
  }

  /// API location filter: real ids only; legacy synthetic rows use null.
  int? get _apiLocationId {
    final id = _selectedLocationId;
    if (id == null || id <= 0) return null;
    return id;
  }

  Future<void> _loadLocations() async {
    setState(() {
      _loadingLocations = true;
      _error = null;
    });
    try {
      final locs = await fetchTreatmentPlanDoctorLocations(
        widget.ref,
        clinicId: widget.clinicId,
        doctorId: widget.doctorId,
      );
      if (!mounted) return;
      PlanDoctorLocationDto? initial;
      if (locs.isNotEmpty) {
        for (final l in locs) {
          if (l.isPrimary) {
            initial = l;
            break;
          }
        }
        initial ??= locs.first;
      }
      setState(() {
        _locations = locs;
        _selectedLocationId = initial?.id;
        _loadingLocations = false;
      });
      await _loadSlots();
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e.toString();
        _loadingLocations = false;
      });
    }
  }

  Future<void> _loadSlots() async {
    setState(() {
      _loadingSlots = true;
      _error = null;
      _selected.clear();
    });
    try {
      final list = await fetchTreatmentPlanFreeSlots(
        widget.ref,
        clinicId: widget.clinicId,
        doctorId: widget.doctorId,
        dayIso: widget.dayIso,
        locationId: _apiLocationId,
      );
      if (!mounted) return;
      setState(() {
        _slots = list
            .where((s) => !widget.alreadyPickedStarts.contains(s.startAt))
            .toList();
        _loadingSlots = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e.toString();
        _loadingSlots = false;
      });
    }
  }

  void _onLocationChanged(int? locationId) {
    if (locationId == _selectedLocationId) return;
    setState(() => _selectedLocationId = locationId);
    _loadSlots();
  }

  String _formatTime(String iso) {
    try {
      final dt = DateTime.parse(iso).toLocal();
      final hh = dt.hour.toString().padLeft(2, '0');
      final mi = dt.minute.toString().padLeft(2, '0');
      return '$hh:$mi';
    } catch (_) {
      return iso;
    }
  }

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Padding(
        padding: EdgeInsets.only(
          left: 16,
          right: 16,
          top: 12,
          bottom: MediaQuery.of(context).viewInsets.bottom + 16,
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        widget.doctorName,
                        style: const TextStyle(fontWeight: FontWeight.w600),
                      ),
                      Text(
                        widget.dayIso,
                        style: TextStyle(
                            fontSize: 12, color: Colors.grey.shade600),
                      ),
                    ],
                  ),
                ),
                IconButton(
                  onPressed: () => Navigator.of(context).pop(),
                  icon: const Icon(Icons.close),
                ),
              ],
            ),
            const SizedBox(height: 8),
            if (_loadingLocations)
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 24),
                child: Center(child: CircularProgressIndicator()),
              )
            else if (_locations.length > 1) ...[
              DropdownButtonFormField<int>(
                initialValue: _selectedLocationId,
                isExpanded: true,
                decoration: InputDecoration(
                  labelText: widget.translate(
                    'treatmentPlanWizardSlotLocation',
                    'Location',
                  ),
                  isDense: true,
                ),
                items: _locations
                    .map(
                      (loc) => DropdownMenuItem<int>(
                        value: loc.id,
                        child: Text(
                          loc.displayLabel,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                    )
                    .toList(),
                onChanged: _onLocationChanged,
              ),
              const SizedBox(height: 8),
            ] else if (_locations.length == 1)
              Padding(
                padding: const EdgeInsets.only(bottom: 8),
                child: Text(
                  _locations.first.displayLabel,
                  style: TextStyle(fontSize: 13, color: Colors.grey.shade700),
                ),
              ),
            if (_loadingSlots && !_loadingLocations)
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 16),
                child: Center(child: LinearProgressIndicator(minHeight: 2)),
              )
            else if (_error != null && !_loadingLocations)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 12),
                child: Text(
                  '${widget.translate('treatmentPlanWizardSlotsLoadError', 'Could not load slots')}: $_error',
                  style: TextStyle(color: Colors.red.shade700),
                ),
              )
            else if (!_loadingLocations &&
                !_loadingSlots &&
                _slots.isEmpty)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 12),
                child: Text(
                  widget.translate('treatmentPlanWizardNoFreeSlots',
                      'No free slots on this day.'),
                  style: TextStyle(color: Colors.grey.shade600),
                ),
              )
            else if (!_loadingLocations && !_loadingSlots)
              Flexible(
                child: SingleChildScrollView(
                  child: Wrap(
                    spacing: 6,
                    runSpacing: 6,
                    children: _slots.map((s) {
                      final selected = _selected.contains(s.startAt);
                      final locSuffix = s.locationLabel != null &&
                              s.locationLabel!.isNotEmpty &&
                              _locations.length > 1
                          ? ' · ${s.locationLabel}'
                          : '';
                      return FilterChip(
                        label: Text(
                          '${_formatTime(s.startAt)}–${_formatTime(s.endAt)}$locSuffix',
                        ),
                        selected: selected,
                        onSelected: (v) {
                          setState(() {
                            if (v) {
                              _selected.add(s.startAt);
                            } else {
                              _selected.remove(s.startAt);
                            }
                          });
                        },
                      );
                    }).toList(),
                  ),
                ),
              ),
            const SizedBox(height: 12),
            Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                TextButton(
                  onPressed: () => Navigator.of(context).pop(),
                  child: Text(widget.translate('cancel', 'Cancel')),
                ),
                const SizedBox(width: 8),
                FilledButton(
                  onPressed: (_loadingLocations ||
                          _loadingSlots ||
                          _selected.isEmpty)
                      ? null
                      : () {
                          PlanDoctorLocationDto? loc;
                          for (final l in _locations) {
                            if (l.id == _selectedLocationId) {
                              loc = l;
                              break;
                            }
                          }
                          final picked = _slots
                              .where((s) => _selected.contains(s.startAt))
                              .map(
                                (s) => _PickedSlot(
                                  tempId:
                                      '${widget.doctorId}|${s.startAt}|${DateTime.now().microsecondsSinceEpoch}',
                                  doctorId: widget.doctorId,
                                  doctorName: widget.doctorName,
                                  startAt: s.startAt,
                                  endAt: s.endAt,
                                  slotMinutes: s.slotMinutes,
                                  locationId: s.locationId ?? _apiLocationId,
                                  locationLabel: s.locationLabel ??
                                      loc?.displayLabel,
                                ),
                              )
                              .toList();
                          Navigator.of(context).pop(picked);
                        },
                  child: Text(
                    '${widget.translate('treatmentPlanWizardAddSlotsBtn', 'Add')} (${_selected.length})',
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
