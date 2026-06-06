import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/core/api/api_client.dart';
import 'package:shifa_doc_app_v1/core/api/api_providers.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/widgets/doctor_speech_text_field.dart';
import 'package:shifa_doc_app_v1/core/widgets/scrollable_sheet_dialog.dart';
import 'package:shifa_doc_app_v1/features/appointments/dental/dental_fdi_chart.dart';
import 'package:shifa_doc_app_v1/features/appointments/services/appointment_pdf_data.dart';

/// FDI-style quadrant codes in the same order as form 025-2 (patient-facing chart).
const List<String> kFdiTeethOrder = [
  'UR 8',
  'UR 7',
  'UR 6',
  'UR 5',
  'UR 4',
  'UR 3',
  'UR 2',
  'UR 1',
  'UL 1',
  'UL 2',
  'UL 3',
  'UL 4',
  'UL 5',
  'UL 6',
  'UL 7',
  'UL 8',
  'LR 8',
  'LR 7',
  'LR 6',
  'LR 5',
  'LR 4',
  'LR 3',
  'LR 2',
  'LR 1',
  'LL 1',
  'LL 2',
  'LL 3',
  'LL 4',
  'LL 5',
  'LL 6',
  'LL 7',
  'LL 8',
];

/// Normalizes tooth key for API (no spaces): UR8, UL1, …
String toothKeyCompact(String spaced) => spaced.replaceAll(' ', '');

/// Display label with space for readability in UI.
String toothKeyDisplay(String compactOrSpaced) {
  final s = compactOrSpaced.replaceAll(' ', '');
  if (s.length < 3) return s;
  return '${s.substring(0, 2)} ${s.substring(2)}';
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
  });

  final String appointmentId;
  final Color brand;
  final void Function(Future<bool> Function() fn)? registerSaveHandler;
  final ValueChanged<bool>? onUnsavedChanged;

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

  static const _autosaveDelay = Duration(milliseconds: 1200);

  int get _version => 1;

  @override
  void initState() {
    super.initState();
    for (final t in kFdiTeethOrder) {
      _teeth[toothKeyCompact(t)] = [];
    }
    _discountCtrl.addListener(_touch);
    _notesCtrl.addListener(_touch);
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      _apiClient = ref.read(apiClientProvider);
      widget.registerSaveHandler?.call(requestSave);
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
      final teethRaw = map['teeth'];
      if (teethRaw is Map) {
        for (final e in _teeth.keys) {
          _teeth[e] = [];
        }
        teethRaw.forEach((k, v) {
          final key = k.toString().replaceAll(' ', '');
          if (!_teeth.containsKey(key)) return;
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

  bool get hasBillableContent =>
      hasDentalChartContent || _notesCtrl.text.trim().isNotEmpty;

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
    for (final key in kFdiTeethOrder) {
      final c = toothKeyCompact(key);
      final list = _teeth[c] ?? const [];
      if (list.isEmpty) continue;
      final toothLabel = toothKeyDisplay(c);
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

  Future<void> _openToothEditor(String compactKey) async {
    final l10n = AppLocalizations.of(context)!;
    final display = toothKeyDisplay(compactKey);
    final list = List<Map<String, dynamic>>.from(_teeth[compactKey] ?? []);

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
                  '${l10n.translate('dentalToothServices')} $display',
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
                    l10n.translate('dentalSelectedServices') ??
                        'Selected services',
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
              _teeth[compactKey] = List<Map<String, dynamic>>.from(list);
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

    final (sub, ccy, lineCount) = _computeSubtotal();
    final total = _totalAfterDiscount(sub);
    final disc = _discountValue();

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
                toothServiceCounts: {
                  for (final e in _teeth.entries)
                    if (e.value.isNotEmpty) e.key: e.value.length,
                },
                onToothTap: _openToothEditor,
                showTitle: false,
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
