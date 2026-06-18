import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/core/api/api_client.dart';
import 'package:shifa_doc_app_v1/core/api/api_providers.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/widgets/doctor_speech_text_field.dart';
import 'package:shifa_doc_app_v1/core/widgets/scrollable_sheet_dialog.dart';
import 'package:shifa_doc_app_v1/features/appointments/dental/dental_chart_codec.dart';
import 'package:shifa_doc_app_v1/features/appointments/dental/dental_fdi_chart.dart';
import 'package:shifa_doc_app_v1/features/appointments/dental/dental_plan_readonly_view.dart';
import 'package:shifa_doc_app_v1/features/appointments/services/appointment_pdf_data.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_treatment_plan_actions.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_treatment_plan_models.dart';

/// Display label for a tooth or general-services key in UI/PDF.
String toothKeyDisplay(String key, {DentalDentition dentition = DentalDentition.permanent}) {
  if (key == DentalChartCodec.generalServicesKey) return key;
  return DentalChartCodec.toFdiDisplay(key, dentitionHint: dentition);
}

class _ServiceOption {
  const _ServiceOption({
    required this.id,
    required this.title,
    required this.amountMinor,
    required this.currency,
    required this.isFreeConsultation,
    required this.groupId,
    required this.groupName,
    required this.groupSortOrder,
  });

  final int id;
  final String title;
  final int amountMinor;
  final String currency;
  final bool isFreeConsultation;
  final int? groupId;
  final String? groupName;
  final int groupSortOrder;
}

class _AggregatedServiceGroup {
  const _AggregatedServiceGroup({
    required this.groupId,
    required this.sortOrder,
    required this.groupName,
    required this.services,
  });

  final int? groupId;
  final int sortOrder;
  final String? groupName;
  final List<_ServiceOption> services;

  String label(AppLocalizations l10n) {
    if (groupId == null) {
      return l10n.translate('serviceGroupNone');
    }
    final n = groupName?.trim();
    if (n == null || n.isEmpty) return l10n.translate('serviceGroupNone');
    return n;
  }
}

class DentalVisitDocumentationPanel extends ConsumerStatefulWidget {
  const DentalVisitDocumentationPanel({
    super.key,
    required this.appointmentId,
    required this.brand,
    this.registerSaveHandler,
    this.onUnsavedChanged,
    this.activePlanId,
    this.planTitle,
    this.dentalPlanDocumentation,
    this.planLines = const [],
    this.fulfillmentCandidates = const [],
    this.fulfilledLineIds = const [],
    this.linesTotalCount = 0,
    this.linesCompletedCount = 0,
    this.loadingPlanContext = false,
    this.planSummary,
    this.onFulfillmentChanged,
    this.onRetryLoadPlan,
  });

  final String appointmentId;
  final Color brand;
  final void Function(Future<bool> Function() fn)? registerSaveHandler;
  final ValueChanged<bool>? onUnsavedChanged;

  /// When set, chart shows plan lines to fulfill (plan-only mode).
  final int? activePlanId;
  final String? planTitle;
  final String? dentalPlanDocumentation;
  final List<LineDetailDto> planLines;
  final List<FulfillmentCandidateDto> fulfillmentCandidates;
  final List<int> fulfilledLineIds;
  final int linesTotalCount;
  final int linesCompletedCount;
  final bool loadingPlanContext;
  final TreatmentPlanSummaryDto? planSummary;
  final VoidCallback? onFulfillmentChanged;
  final VoidCallback? onRetryLoadPlan;

  @override
  ConsumerState<DentalVisitDocumentationPanel> createState() =>
      DentalVisitDocumentationPanelState();
}

class DentalVisitDocumentationPanelState extends ConsumerState<DentalVisitDocumentationPanel> {
  final Map<String, List<Map<String, dynamic>>> _teeth = {};
  final TextEditingController _discountCtrl = TextEditingController(text: '0');
  final TextEditingController _notesCtrl = TextEditingController();
  List<_ServiceOption> _catalog = [];
  List<_AggregatedServiceGroup> _serviceGroups = [];
  bool _loading = true;
  bool _saving = false;
  bool _hydrating = true;
  bool _loaded = false;
  bool _dirty = false;
  Timer? _autosaveTimer;
  ApiClient? _apiClient;
  DentalDentition _dentition = DentalDentition.permanent;
  final Set<int> _selectedLineIds = {};

  bool get _isPlanMode => widget.activePlanId != null;

  int get _appointmentIdInt => int.tryParse(widget.appointmentId) ?? 0;

  Set<int> get _pendingFulfillLineIds => _selectedLineIds
      .where((id) => !widget.fulfilledLineIds.contains(id))
      .toSet();

  bool get hasPendingFulfillment => _isPlanMode && _pendingFulfillLineIds.isNotEmpty;

  static const _autosaveDelay = Duration(milliseconds: 1200);

  int get _version => 2;

  void _initTeethKeys() {
    for (final d in DentalDentition.values) {
      for (final fdi in DentalChartCodec.visitDocTeethOrder(d)) {
        _teeth.putIfAbsent(fdi, () => []);
      }
    }
    _teeth.putIfAbsent(DentalChartCodec.generalServicesKey, () => []);
  }

  /// Resolves stored/API key to canonical map key (FDI or general).
  String _resolveKey(String raw, {DentalDentition? dentitionHint}) {
    final s = raw.replaceAll(' ', '');
    if (s == DentalChartCodec.generalServicesKey) return s;
    return DentalChartCodec.normalizeToothKey(
      s,
      dentition: dentitionHint ?? _dentition,
    );
  }

  @override
  void didUpdateWidget(covariant DentalVisitDocumentationPanel oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.fulfilledLineIds != widget.fulfilledLineIds) {
      _selectedLineIds.removeWhere(
        (id) => widget.fulfilledLineIds.contains(id),
      );
    }
    if (_isPlanMode) {
      _applyPlanDentitionFromDoc();
    }
  }

  void _applyPlanDentitionFromDoc() {
    final raw = widget.dentalPlanDocumentation;
    if (raw == null || raw.trim().isEmpty) return;
    try {
      final doc = jsonDecode(raw) as Map<String, dynamic>?;
      final dentRaw = doc?['dentition']?.toString().trim().toLowerCase();
      _dentition = dentRaw == 'primary'
          ? DentalDentition.primary
          : DentalDentition.permanent;
    } catch (_) {}
  }

  @override
  void initState() {
    super.initState();
    _initTeethKeys();
    _discountCtrl.addListener(_touch);
    _notesCtrl.addListener(_touch);
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      widget.registerSaveHandler?.call(requestSave);
      if (_isPlanMode) {
        _applyPlanDentitionFromDoc();
        _hydrating = false;
        _loaded = true;
        _dirty = false;
        if (mounted) setState(() => _loading = false);
        return;
      }
      _apiClient = ref.read(apiClientProvider);
      await Future.wait([_loadServices(), _loadSaved()]);
      _hydrating = false;
      _loaded = true;
      _dirty = false;
      if (mounted) setState(() => _loading = false);
    });
  }

  void _touch() {
    if (_hydrating || !_loaded) return;
    widget.onUnsavedChanged?.call(true);
    _dirty = true;
    _scheduleAutosave();
  }

  void _scheduleAutosave() {
    _autosaveTimer?.cancel();
    _autosaveTimer = Timer(_autosaveDelay, () {
      unawaited(_persistDraft(silent: true));
    });
  }

  @override
  void dispose() {
    _autosaveTimer?.cancel();
    if (_dirty && _apiClient != null) {
      unawaited(_persistDraft(silent: true, showErrors: false));
    }
    _discountCtrl.dispose();
    _notesCtrl.dispose();
    super.dispose();
  }

  Future<void> _loadServices() async {
    final api = ref.read(apiClientProvider);
    final res = await api.get('/api/doctors/me/services');
    if (res.statusCode != 200) return;
    try {
      final list = jsonDecode(utf8.decode(res.bodyBytes)) as List<dynamic>;
      final opts = <_ServiceOption>[];
      for (final raw in list) {
        if (raw is! Map<String, dynamic>) continue;
        if (raw['isActive'] == false) continue;
        final id = (raw['id'] as num?)?.toInt();
        final title = raw['title']?.toString() ?? '';
        if (id == null || title.isEmpty) continue;
        final isFree = raw['isFreeConsultation'] == true;
        final gid = (raw['groupId'] as num?)?.toInt();
        final gname = raw['groupName']?.toString();
        final gsort =
            (raw['groupSortOrder'] as num?)?.toInt() ?? 2147483647;
        final prices = (raw['prices'] as List?) ?? const [];
        var amount = 0;
        var ccy = 'UZS';
        for (final p in prices) {
          if (p is! Map) continue;
          final am = (p['amountMinor'] as num?)?.toInt() ?? 0;
          final cur = p['currency']?.toString().toUpperCase() ?? '';
          if (am > 0 && cur.isNotEmpty) {
            amount = am;
            ccy = cur;
            break;
          }
        }
        if (amount == 0 && prices.isNotEmpty) {
          final p = prices.first;
          if (p is Map) {
            amount = (p['amountMinor'] as num?)?.toInt() ?? 0;
            ccy = p['currency']?.toString().toUpperCase() ?? 'UZS';
          }
        }
        opts.add(
          _ServiceOption(
            id: id,
            title: title,
            amountMinor: amount,
            currency: ccy.isEmpty ? 'UZS' : ccy,
            isFreeConsultation: isFree,
            groupId: gid,
            groupName: gname,
            groupSortOrder: gsort,
          ),
        );
      }
      final grouped = _aggregateServiceGroups(opts);
      if (mounted) {
        setState(() {
          _catalog = opts;
          _serviceGroups = grouped;
        });
      }
    } catch (_) {}
  }

  List<_AggregatedServiceGroup> _aggregateServiceGroups(
    List<_ServiceOption> opts,
  ) {
    final buckets = <int?, List<_ServiceOption>>{};
    for (final o in opts) {
      buckets.putIfAbsent(o.groupId, () => []).add(o);
    }
    final out = <_AggregatedServiceGroup>[];
    buckets.forEach((gid, services) {
      if (services.isEmpty) return;
      final sort = services
          .map((s) => s.groupSortOrder)
          .reduce((a, b) => a < b ? a : b);
      final name = gid == null ? null : services.first.groupName;
      out.add(
        _AggregatedServiceGroup(
          groupId: gid,
          sortOrder: sort,
          groupName: name,
          services: services,
        ),
      );
    });
    out.sort((a, b) {
      final c = a.sortOrder.compareTo(b.sortOrder);
      if (c != 0) return c;
      return a
          .groupName
          .toString()
          .toLowerCase()
          .compareTo(b.groupName.toString().toLowerCase());
    });
    return out;
  }

  Future<void> _loadSaved() async {
    final api = ref.read(apiClientProvider);
    final res = await api.get('/api/appointments/${widget.appointmentId}/dental-documentation');
    if (res.statusCode != 200) return;
    try {
      final map = jsonDecode(utf8.decode(res.bodyBytes));
      if (map is! Map || map.isEmpty) return;

      final dentRaw = map['dentition']?.toString().trim().toLowerCase();
      if (dentRaw == 'primary') {
        _dentition = DentalDentition.primary;
      } else {
        _dentition = DentalDentition.permanent;
      }

      for (final e in _teeth.keys) {
        _teeth[e] = [];
      }

      final teethRaw = map['teeth'];
      if (teethRaw is Map) {
        teethRaw.forEach((k, v) {
          final key = _resolveKey(k.toString());
          if (!_teeth.containsKey(key)) {
            // Legacy key from another dentition — migrate if possible.
            final migrated = _resolveKey(k.toString(), dentitionHint: DentalDentition.permanent);
            if (!_teeth.containsKey(migrated)) return;
            if (v is! List) return;
            final lines = <Map<String, dynamic>>[];
            for (final item in v) {
              if (item is! Map) continue;
              lines.add(Map<String, dynamic>.from(item));
            }
            _teeth[migrated] = lines;
            return;
          }
          if (v is! List) return;
          final lines = <Map<String, dynamic>>[];
          for (final item in v) {
            if (item is! Map) continue;
            lines.add(Map<String, dynamic>.from(item));
          }
          _teeth[key] = lines;
        });
      }
      final disc = map['discountPercent'];
      if (disc is num) {
        _discountCtrl.text = disc.toString();
      } else if (disc is String && disc.trim().isNotEmpty) {
        _discountCtrl.text = disc.trim();
      }
      final notes = map['notes']?.toString();
      if (notes != null && notes.isNotEmpty) {
        _notesCtrl.text = notes;
      }
      if (mounted) setState(() {});
    } catch (_) {}
  }

  bool get hasDentalChartContent {
    for (final list in _teeth.values) {
      if (list.isNotEmpty) return true;
    }
    return false;
  }

  bool get hasBillableContent {
    if (_isPlanMode) {
      return hasPendingFulfillment ||
          widget.fulfilledLineIds.isNotEmpty ||
          _notesCtrl.text.trim().isNotEmpty;
    }
    return hasDentalChartContent || _notesCtrl.text.trim().isNotEmpty;
  }

  /// Apply checked plan lines before completing the visit.
  Future<bool> applyPendingIfNeeded({bool silent = false}) async {
    if (!_isPlanMode || widget.activePlanId == null) return true;
    final pending = _pendingFulfillLineIds.toList();
    if (pending.isEmpty) return true;

    setState(() => _saving = true);
    try {
      final result = await fulfillTreatmentPlanLines(
        ref,
        planId: widget.activePlanId!,
        appointmentId: _appointmentIdInt,
        lineIds: pending,
      );
      if (result == null) return false;
      if (!silent && mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              AppLocalizations.of(context)!.translate('appointmentPlanApplied'),
            ),
          ),
        );
      }
      widget.onFulfillmentChanged?.call();
      return true;
    } catch (_) {
      return false;
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  AppointmentPdfTreatmentPlanSection? buildPlanPdfSection({
    int? sessionPaymentMinor,
    String? sessionPaymentMethod,
  }) {
    if (!_isPlanMode || widget.activePlanId == null) return null;
    final summary = widget.planSummary;
    if (summary == null) return null;

    final fulfilledLines = <AppointmentPdfDentalLine>[];
    for (final lineId in widget.fulfilledLineIds) {
      LineDetailDto? line;
      for (final l in widget.planLines) {
        if (l.id == lineId) {
          line = l;
          break;
        }
      }
      if (line == null) continue;
      var tooth = '';
      final meta = line.specialtyMetadata;
      if (meta != null && meta.isNotEmpty) {
        try {
          final m = jsonDecode(meta) as Map<String, dynamic>?;
          tooth = m?['fdi']?.toString() ?? '';
        } catch (_) {}
      }
      fulfilledLines.add(
        AppointmentPdfDentalLine(
          tooth: tooth,
          serviceTitle: line.title,
          amountMinor: line.lineTotalMinor,
          currency: line.currency,
        ),
      );
    }

    return AppointmentPdfTreatmentPlanSection(
      planId: '${widget.activePlanId}',
      planTitle: widget.planTitle ?? summary.title,
      planTotalMinor: summary.totalMinor,
      planPaidMinor: summary.paidMinor,
      planOwedMinor: summary.owedMinor,
      currency: summary.currency,
      fulfilledThisVisit: fulfilledLines,
      sessionPaymentMinor: sessionPaymentMinor,
      sessionPaymentMethod: sessionPaymentMethod,
    );
  }

  Map<String, DentalToothPlanState> _planToothStates() {
    final states = DentalPlanReadonlyView.toothStatesFromLines(widget.planLines);
    for (final c in widget.fulfillmentCandidates) {
      if (!_selectedLineIds.contains(c.lineId)) continue;
      final fdi = c.fdi;
      if (fdi == null || fdi.isEmpty) continue;
      final current = states[fdi];
      if (current == DentalToothPlanState.planned ||
          current == DentalToothPlanState.partial) {
        states[fdi] = DentalToothPlanState.partial;
      }
    }
    return states;
  }

  Map<String, int> _planServiceCounts() {
    final counts = <String, int>{};
    final raw = widget.dentalPlanDocumentation;
    if (raw == null || raw.trim().isEmpty) return counts;
    try {
      final doc = jsonDecode(raw) as Map<String, dynamic>?;
      final teethRaw = doc?['teeth'];
      if (teethRaw is Map) {
        teethRaw.forEach((k, v) {
          if (k.toString() == DentalChartCodec.generalServicesKey) return;
          if (v is List && v.isNotEmpty) {
            counts[k.toString()] = v.length;
          }
        });
      }
    } catch (_) {}
    return counts;
  }

  List<FulfillmentCandidateDto> _candidatesForTooth(String fdi) {
    return widget.fulfillmentCandidates.where((c) => c.fdi == fdi).toList();
  }

  List<FulfillmentCandidateDto> get _generalCandidates {
    return widget.fulfillmentCandidates
        .where((c) => c.fdi == null || c.fdi!.isEmpty)
        .toList();
  }

  Future<void> _openPlanFulfillSheet(
    String toothKey, {
    required String titlePrefix,
  }) async {
    if (widget.activePlanId == null) return;
    final l10n = AppLocalizations.of(context)!;
    final isGeneral = toothKey == DentalChartCodec.generalServicesKey;
    final candidates = isGeneral
        ? _generalCandidates
        : _candidatesForTooth(toothKey);
    if (candidates.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(l10n.translate('appointmentPlanNoLinesOnTooth'))),
      );
      return;
    }

    final display = isGeneral
        ? l10n.translate('dentalGeneralServices')
        : toothKeyDisplay(toothKey, dentition: _dentition);

    await showScrollableFormBottomSheetWithFooter<void>(
      context: context,
      includeBottomNavClearance: false,
      bodyBuilder: (ctx) {
        return StatefulBuilder(
          builder: (ctx, setLocal) {
            return Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text(
                  isGeneral ? display : '$titlePrefix $display',
                  style: Theme.of(ctx).textTheme.titleMedium,
                ),
                const SizedBox(height: 12),
                Text(
                  l10n.translate('appointmentPlanFulfillSheetHint'),
                  style: Theme.of(ctx).textTheme.bodySmall,
                ),
                const SizedBox(height: 8),
                for (final c in candidates) ...[
                  CheckboxListTile(
                    value: _selectedLineIds.contains(c.lineId) ||
                        widget.fulfilledLineIds.contains(c.lineId),
                    onChanged: widget.fulfilledLineIds.contains(c.lineId)
                        ? null
                        : (v) {
                            setLocal(() {
                              if (v == true) {
                                _selectedLineIds.add(c.lineId);
                              } else {
                                _selectedLineIds.remove(c.lineId);
                              }
                            });
                            setState(() {});
                            widget.onFulfillmentChanged?.call();
                          },
                    title: Text(c.title),
                    subtitle: Text(
                      '${(c.lineTotalMinor / 100).toStringAsFixed(2)} ${c.currency}',
                    ),
                    controlAffinity: ListTileControlAffinity.leading,
                    dense: true,
                  ),
                ],
              ],
            );
          },
        );
      },
      footer: Builder(
        builder: (sheetCtx) => FilledButton(
          onPressed: () => Navigator.pop(sheetCtx),
          child: Text(l10n.save),
        ),
      ),
    );
  }

  Widget _buildPlanEmptyBanner(AppLocalizations l10n) {
    if (widget.loadingPlanContext) {
      return const LinearProgressIndicator(minHeight: 2);
    }
    final total = widget.linesTotalCount;
    final done = widget.linesCompletedCount;
    if (total > 0 && done >= total) {
      return Card(
        color: Colors.green.shade50,
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Text(l10n.translate('appointmentPlanAllDone')),
        ),
      );
    }
    if (total > done && widget.fulfillmentCandidates.isEmpty) {
      return Card(
        color: Colors.orange.shade50,
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Row(
            children: [
              Expanded(
                child: Text(l10n.translate('appointmentPlanLoadFailed')),
              ),
              TextButton(
                onPressed: widget.onRetryLoadPlan,
                child: Text(l10n.retry),
              ),
            ],
          ),
        ),
      );
    }
    if (widget.fulfillmentCandidates.isEmpty) {
      return Card(
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Text(l10n.translate('appointmentPlanNoOpenLines')),
        ),
      );
    }
    return const SizedBox.shrink();
  }

  Widget _buildPlanModeBody(AppLocalizations l10n) {
    final toothStates = _planToothStates();
    final serviceCounts = _planServiceCounts();
    final generalCount = _generalCandidates.length;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(
          l10n.translate('appointmentPlanChartIntro'),
          style: Theme.of(context).textTheme.bodySmall,
        ),
        const SizedBox(height: 8),
        _buildPlanEmptyBanner(l10n),
        const SizedBox(height: 8),
        DentalFdiChart(
          brand: widget.brand,
          dentition: _dentition,
          showDentitionToggle: false,
          toothServiceCounts: serviceCounts,
          toothPlanStates: toothStates,
          onDentitionChanged: null,
          onToothTap: (fdi) => _openPlanFulfillSheet(
            fdi,
            titlePrefix: l10n.translate('dentalToothServices'),
          ),
          showTitle: false,
        ),
        if (generalCount > 0) ...[
          const SizedBox(height: 12),
          OutlinedButton.icon(
            onPressed: () => _openPlanFulfillSheet(
              DentalChartCodec.generalServicesKey,
              titlePrefix: l10n.translate('dentalGeneralServices'),
            ),
            icon: const Icon(Icons.medical_services_outlined),
            label: Text(
              '${l10n.translate('dentalGeneralServices')} ($generalCount)',
            ),
          ),
        ],
        const SizedBox(height: 16),
        DoctorSpeechTextField(
          controller: _notesCtrl,
          minLines: 2,
          maxLines: 5,
          onTranscriptAppended: _touch,
          decoration: InputDecoration(
            labelText: l10n.translate('dentalClinicalNotes'),
            alignLabelWithHint: true,
          ),
        ),
      ],
    );
  }

  /// Appends text to clinical notes (e.g. from last 025-2 form or Shifa AI).
  void appendClinicalNotes(String text) {
    final toAdd = text.trim();
    if (toAdd.isEmpty) return;
    if (_notesCtrl.text.trim().isNotEmpty) {
      _notesCtrl.text += '\n\n';
    }
    _notesCtrl.text += toAdd;
    _touch();
    if (mounted) setState(() {});
  }

  double _discountValue() {
    final v = double.tryParse(_discountCtrl.text.trim().replaceAll(',', '.'));
    if (v == null || v < 0) return 0;
    if (v > 100) return 100;
    return v;
  }

  /// Only sums lines whose currency matches the first priced currency (avoids silent miscount).
  (int subtotal, String currency, int lineCount) _computeSubtotal() {
    var total = 0;
    var ccy = '';
    var count = 0;
    for (final list in _teeth.values) {
      for (final line in list) {
        final am = (line['amountMinor'] as num?)?.toInt() ?? 0;
        final c = line['currency']?.toString().toUpperCase() ?? '';
        if (am <= 0) continue;
        if (ccy.isEmpty && c.isNotEmpty) ccy = c;
        if (c.isNotEmpty && ccy.isNotEmpty && c != ccy) continue;
        total += am;
        count++;
      }
    }
    if (ccy.isEmpty) ccy = 'UZS';
    return (total, ccy, count);
  }

  int _totalAfterDiscount(int subtotalMinor) {
    final d = _discountValue();
    return (subtotalMinor * (100 - d) / 100.0).round();
  }

  Map<String, dynamic> _toPayload() {
    final teethOut = <String, dynamic>{};
    _teeth.forEach((k, v) {
      if (v.isNotEmpty) teethOut[k] = v;
    });
    return {
      'version': _version,
      'dentition': _dentition == DentalDentition.primary ? 'primary' : 'permanent',
      'teeth': teethOut,
      'discountPercent': _discountValue(),
      'notes': _notesCtrl.text.trim(),
    };
  }

  /// Persists the current draft immediately (e.g. before leaving the screen).
  Future<void> flushSave() async {
    _autosaveTimer?.cancel();
    await _persistDraft(silent: true);
  }

  /// Called when switching documentation mode with "Save".
  Future<bool> requestSave() async {
    _autosaveTimer?.cancel();
    return _persistDraft(showSuccessSnackBar: true);
  }

  Future<bool> _persistDraft({
    bool silent = false,
    bool showSuccessSnackBar = false,
    bool showErrors = true,
  }) async {
    if (!_loaded || (!_dirty && silent)) return true;
    if (!mounted) return false;

    setState(() => _saving = true);
    try {
      _apiClient ??= ref.read(apiClientProvider);
      final res = await _apiClient!.put(
        '/api/appointments/${widget.appointmentId}/dental-documentation',
        _toPayload(),
      );
      final ok = res.statusCode == 200;
      if (ok && mounted) {
        _dirty = false;
        widget.onUnsavedChanged?.call(false);
        if (showSuccessSnackBar) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(
                AppLocalizations.of(context)!.translate('dentalDocSaved'),
              ),
            ),
          );
        }
      } else if (showErrors && mounted) {
        final msg = _extractApiMessage(utf8.decode(res.bodyBytes));
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              msg ?? AppLocalizations.of(context)!.translate('dentalDocSaveFailed'),
            ),
            backgroundColor: Colors.red,
          ),
        );
      }
      return ok;
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  /// Persist dental chart to backend before generating the PDF (same payload as Save).
  Future<void> persistForPdf() async {
    _autosaveTimer?.cancel();
    await _persistDraft(silent: true, showErrors: true);
  }

  /// Free-text notes for PDF only (rendered under [Clinical Notes]; no billing).
  String get dentalClinicalNotesPdfText => _notesCtrl.text.trim();

  /// Builds structured billing for the PDF; `null` when no per-tooth rows.
  AppointmentPdfDentalBilling? buildDentalPdfBilling(AppLocalizations l10n) {
    final (sub, ccy, _) = _computeSubtotal();
    final lines = <AppointmentPdfDentalLine>[];
    final order = [
      ...DentalChartCodec.visitDocTeethOrder(_dentition),
      DentalChartCodec.generalServicesKey,
    ];
    for (final key in order) {
      final list = _teeth[key] ?? const [];
      if (list.isEmpty) continue;
      final toothLabel = key == DentalChartCodec.generalServicesKey
          ? l10n.translate('dentalGeneralServicesShort')
          : toothKeyDisplay(key, dentition: _dentition);
      for (final line in list) {
        final title = line['title']?.toString() ?? '';
        final am = (line['amountMinor'] as num?)?.toInt() ?? 0;
        final rawCur = line['currency']?.toString().toUpperCase();
        lines.add(
          AppointmentPdfDentalLine(
            tooth: toothLabel,
            serviceTitle: title,
            amountMinor: am,
            currency: rawCur != null && rawCur.isNotEmpty ? rawCur : ccy,
          ),
        );
      }
    }
    if (lines.isEmpty) return null;

    final dVal = _discountValue();
    final total = _totalAfterDiscount(sub);
    return AppointmentPdfDentalBilling(
      header: l10n.translate('dentalPdfHeader'),
      lines: lines,
      subtotalMinor: sub,
      discountPercent: dVal > 0 ? dVal : null,
      totalMinor: total,
      currency: ccy,
    );
  }

  Future<void> _openServicesEditor(String toothKey, {required String titlePrefix}) async {
    final l10n = AppLocalizations.of(context)!;
    final resolved = _resolveKey(toothKey);
    final isGeneral = resolved == DentalChartCodec.generalServicesKey;
    final display = isGeneral
        ? ''
        : toothKeyDisplay(resolved, dentition: _dentition);
    final list = List<Map<String, dynamic>>.from(_teeth[resolved] ?? []);

    await showScrollableFormBottomSheetWithFooter<void>(
      context: context,
      includeBottomNavClearance: false,
      bodyBuilder: (ctx) {
        var activeGroupIndex = 0;
        final maxH = (MediaQuery.sizeOf(ctx).height * 0.38).clamp(220.0, 360.0);
        return StatefulBuilder(
          builder: (ctx, setLocal) {
            return Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text(
                  isGeneral ? titlePrefix : '$titlePrefix $display',
                  style: Theme.of(ctx).textTheme.titleMedium,
                ),
                const SizedBox(height: 12),
                if (_catalog.isEmpty)
                  Text(
                    l10n.translate('dentalNoServices'),
                    style: TextStyle(color: Colors.grey.shade700),
                  )
                else ...[
                  Text(
                    l10n.translate('dentalAddService'),
                    style: Theme.of(ctx).textTheme.labelLarge,
                  ),
                  const SizedBox(height: 8),
                  if (_serviceGroups.isEmpty)
                    const SizedBox.shrink()
                  else
                    SizedBox(
                      height: maxH,
                      child: Builder(
                        builder: (context) {
                          final n = _serviceGroups.length;
                          activeGroupIndex = activeGroupIndex.clamp(0, n - 1);
                          final g = _serviceGroups[activeGroupIndex];
                          return Row(
                            crossAxisAlignment: CrossAxisAlignment.stretch,
                            children: [
                              Expanded(
                                flex: 2,
                                child: DecoratedBox(
                                  decoration: BoxDecoration(
                                    color: Colors.grey.shade50,
                                    borderRadius: const BorderRadius.only(
                                      topLeft: Radius.circular(8),
                                      bottomLeft: Radius.circular(8),
                                    ),
                                  ),
                                  child: ClipRRect(
                                    borderRadius: const BorderRadius.only(
                                      topLeft: Radius.circular(8),
                                      bottomLeft: Radius.circular(8),
                                    ),
                                    child: ListView.builder(
                                      itemCount: n,
                                      itemBuilder: (c, i) {
                                        final grp = _serviceGroups[i];
                                        final selected = i == activeGroupIndex;
                                        return InkWell(
                                          onTap: () => setLocal(
                                            () => activeGroupIndex = i,
                                          ),
                                          child: MouseRegion(
                                            onEnter: (_) => setLocal(
                                              () => activeGroupIndex = i,
                                            ),
                                            child: Container(
                                              width: double.infinity,
                                              padding:
                                                  const EdgeInsets.symmetric(
                                                horizontal: 12,
                                                vertical: 14,
                                              ),
                                              color: selected
                                                  ? widget.brand.withValues(
                                                      alpha: 0.14,
                                                    )
                                                  : null,
                                              child: Text(
                                                grp.label(l10n),
                                                style: TextStyle(
                                                  fontWeight: selected
                                                      ? FontWeight.w700
                                                      : FontWeight.w500,
                                                  fontSize: 13,
                                                ),
                                              ),
                                            ),
                                          ),
                                        );
                                      },
                                    ),
                                  ),
                                ),
                              ),
                              VerticalDivider(
                                width: 1,
                                thickness: 1,
                                color: Colors.grey.shade300,
                              ),
                              Expanded(
                                flex: 3,
                                child: ClipRRect(
                                  borderRadius: const BorderRadius.only(
                                    topRight: Radius.circular(8),
                                    bottomRight: Radius.circular(8),
                                  ),
                                  child: ListView.builder(
                                    itemCount: g.services.length,
                                    itemBuilder: (c, j) {
                                      final s = g.services[j];
                                      final priceLine = s.isFreeConsultation
                                          ? '0 ${s.currency}'
                                          : '${(s.amountMinor / 100).toStringAsFixed(2)} ${s.currency}';
                                      return ListTile(
                                        dense: true,
                                        title: Text(
                                          s.title,
                                          maxLines: 2,
                                          overflow: TextOverflow.ellipsis,
                                        ),
                                        subtitle: Text(priceLine),
                                        onTap: () {
                                          setLocal(() {
                                            list.add({
                                              'serviceId': s.id,
                                              'title': s.title,
                                              'amountMinor':
                                                  s.isFreeConsultation
                                                      ? 0
                                                      : s.amountMinor,
                                              'currency': s.currency,
                                            });
                                            _touch();
                                          });
                                        },
                                      );
                                    },
                                  ),
                                ),
                              ),
                            ],
                          );
                        },
                      ),
                    ),
                ],
                if (list.isNotEmpty) ...[
                  const SizedBox(height: 12),
                  Text(
                    l10n.translate('dentalSelectedServices'),
                    style: Theme.of(ctx).textTheme.labelLarge,
                  ),
                  const SizedBox(height: 4),
                  for (var i = 0; i < list.length; i++) ...[
                    if (i > 0) const Divider(height: 1),
                    Builder(
                      builder: (context) {
                        final line = list[i];
                        final title = line['title']?.toString() ?? '';
                        final am =
                            (line['amountMinor'] as num?)?.toInt() ?? 0;
                        final cur = line['currency']?.toString() ?? '';
                        return ListTile(
                          contentPadding: EdgeInsets.zero,
                          title: Text(title),
                          subtitle: Text('${(am / 100).toStringAsFixed(2)} $cur'),
                          trailing: IconButton(
                            icon: const Icon(Icons.delete_outline),
                            onPressed: () {
                              setLocal(() {
                                list.removeAt(i);
                                _touch();
                              });
                            },
                          ),
                        );
                      },
                    ),
                  ],
                ],
              ],
            );
          },
        );
      },
      footer: Builder(
        builder: (sheetCtx) => FilledButton(
          onPressed: () {
            setState(() {
              _teeth[resolved] = List<Map<String, dynamic>>.from(list);
            });
            Navigator.pop(sheetCtx);
          },
          child: Text(l10n.save),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    if (_loading) {
      return const Padding(
        padding: EdgeInsets.all(24),
        child: Center(child: CircularProgressIndicator()),
      );
    }

    if (_isPlanMode) {
      return Stack(
        children: [
          SingleChildScrollView(
            padding: const EdgeInsets.only(bottom: 24),
            child: _buildPlanModeBody(l10n),
          ),
          if (_saving)
            const Positioned.fill(
              child: IgnorePointer(
                child: Center(
                  child: SizedBox(
                    width: 36,
                    height: 36,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  ),
                ),
              ),
            ),
        ],
      );
    }

    final (sub, ccy, lineCount) = _computeSubtotal();
    final total = _totalAfterDiscount(sub);
    final disc = _discountValue();
    final generalCount =
        (_teeth[DentalChartCodec.generalServicesKey] ?? const []).length;

    return Stack(
      children: [
        SingleChildScrollView(
          padding: const EdgeInsets.only(bottom: 24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(
                l10n.translate('dentalDocIntro'),
                style: Theme.of(context).textTheme.bodySmall,
              ),
              const SizedBox(height: 12),
              DentalFdiChart(
                brand: widget.brand,
                dentition: _dentition,
                onDentitionChanged: (d) {
                  setState(() => _dentition = d);
                  _touch();
                },
                toothServiceCounts: {
                  for (final fdi in DentalChartCodec.visitDocTeethOrder(_dentition))
                    if ((_teeth[fdi] ?? const []).isNotEmpty)
                      fdi: (_teeth[fdi] ?? const []).length,
                },
                onToothTap: (fdi) => _openServicesEditor(
                  fdi,
                  titlePrefix: l10n.translate('dentalToothServices'),
                ),
                showTitle: false,
              ),
              const SizedBox(height: 12),
              OutlinedButton.icon(
                onPressed: () => _openServicesEditor(
                  DentalChartCodec.generalServicesKey,
                  titlePrefix: l10n.translate('dentalGeneralServices'),
                ),
                icon: const Icon(Icons.medical_services_outlined),
                label: Text(
                  generalCount > 0
                      ? '${l10n.translate('dentalGeneralServices')} ($generalCount)'
                      : l10n.translate('dentalGeneralServices'),
                ),
              ),
              const SizedBox(height: 4),
              Text(
                l10n.translate('dentalGeneralServicesHint'),
                style: Theme.of(context).textTheme.bodySmall?.copyWith(
                      color: Colors.grey.shade700,
                    ),
              ),
              const SizedBox(height: 16),
              TextField(
                controller: _discountCtrl,
                keyboardType: const TextInputType.numberWithOptions(decimal: true),
                decoration: InputDecoration(
                  labelText: l10n.translate('dentalDiscountPercent'),
                  suffixText: '%',
                ),
                onChanged: (_) => setState(() {}),
              ),
              const SizedBox(height: 12),
              DoctorSpeechTextField(
                controller: _notesCtrl,
                minLines: 2,
                maxLines: 5,
                onTranscriptAppended: _touch,
                decoration: InputDecoration(
                  labelText: l10n.translate('dentalClinicalNotes'),
                  alignLabelWithHint: true,
                ),
              ),
              const SizedBox(height: 16),
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(12),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      Text(
                        '${l10n.translate('dentalSubtotal')}: ${(sub / 100).toStringAsFixed(2)} $ccy · $lineCount ${l10n.translate('dentalLineItems')}',
                      ),
                      if (disc > 0)
                        Text(
                          '${l10n.translate('dentalDiscount')}: ${disc.toStringAsFixed(1)}%',
                        ),
                      const SizedBox(height: 6),
                      Text(
                        '${l10n.translate('dentalTotal')}: ${(total / 100).toStringAsFixed(2)} $ccy',
                        style: TextStyle(
                          fontWeight: FontWeight.w800,
                          color: widget.brand,
                          fontSize: 16,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
        if (_saving)
          const Positioned.fill(
            child: IgnorePointer(
              child: Center(
                child: SizedBox(
                  width: 36,
                  height: 36,
                  child: CircularProgressIndicator(strokeWidth: 2),
                ),
              ),
            ),
          ),
      ],
    );
  }

}

String? _extractApiMessage(String body) {
  try {
    final decoded = jsonDecode(body);
    if (decoded is Map) {
      final m = decoded['message'] ?? decoded['error'];
      if (m is String && m.isNotEmpty) return m;
    }
  } catch (_) {}
  return null;
}
