import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/core/api/api_providers.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';

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
  });

  final int id;
  final String title;
  final int amountMinor;
  final String currency;
  final bool isFreeConsultation;
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
  bool _loading = true;
  bool _saving = false;

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
      widget.registerSaveHandler?.call(requestSave);
      await Future.wait([_loadServices(), _loadSaved()]);
      if (mounted) setState(() => _loading = false);
    });
  }

  void _touch() => widget.onUnsavedChanged?.call(true);

  @override
  void dispose() {
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
          ),
        );
      }
      if (mounted) setState(() => _catalog = opts);
    } catch (_) {}
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

  bool get hasBillableContent {
    for (final list in _teeth.values) {
      if (list.isNotEmpty) return true;
    }
    return _notesCtrl.text.trim().isNotEmpty;
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

  /// Called when switching documentation mode with "Save".
  Future<bool> requestSave() async {
    setState(() => _saving = true);
    try {
      final api = ref.read(apiClientProvider);
      final res = await api.put(
        '/api/appointments/${widget.appointmentId}/dental-documentation',
        _toPayload(),
      );
      final ok = res.statusCode == 200;
      if (ok && mounted) {
        widget.onUnsavedChanged?.call(false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(AppLocalizations.of(context)!.translate('dentalDocSaved'))),
        );
      } else if (mounted) {
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

  /// Persist then return plain-text block for the appointment PDF.
  Future<String> persistForPdfAndSave() async {
    final api = ref.read(apiClientProvider);
    final res = await api.put(
      '/api/appointments/${widget.appointmentId}/dental-documentation',
      _toPayload(),
    );
    if (res.statusCode != 200 && mounted) {
      final msg = _extractApiMessage(utf8.decode(res.bodyBytes));
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(msg ?? AppLocalizations.of(context)!.translate('dentalDocSaveFailed')),
          backgroundColor: Colors.orange,
        ),
      );
    }
    if (!mounted) return '';
    return _buildPdfText(AppLocalizations.of(context)!);
  }

  String _buildPdfText(AppLocalizations l10n) {
    final buf = StringBuffer();
    buf.writeln(l10n.translate('dentalPdfHeader'));
    final (sub, ccy, _) = _computeSubtotal();
    for (final key in kFdiTeethOrder) {
      final c = toothKeyCompact(key);
      final list = _teeth[c] ?? const [];
      if (list.isEmpty) continue;
      buf.writeln('${toothKeyDisplay(c)}:');
      for (final line in list) {
        final title = line['title']?.toString() ?? '';
        final am = (line['amountMinor'] as num?)?.toInt() ?? 0;
        final cur = line['currency']?.toString() ?? ccy;
        buf.writeln(
          '  — $title: ${(am / 100).toStringAsFixed(2)} $cur',
        );
      }
    }
    final d = _discountValue();
    final total = _totalAfterDiscount(sub);
    buf.writeln();
    buf.writeln('${l10n.translate('dentalSubtotal')}: ${(sub / 100).toStringAsFixed(2)} $ccy');
    if (d > 0) {
      buf.writeln(
        '${l10n.translate('dentalDiscount')}: ${d.toStringAsFixed(1)}%',
      );
    }
    buf.writeln('${l10n.translate('dentalTotal')}: ${(total / 100).toStringAsFixed(2)} $ccy');
    final notes = _notesCtrl.text.trim();
    if (notes.isNotEmpty) {
      buf.writeln();
      buf.writeln('${l10n.translate('dentalClinicalNotes')}:');
      buf.writeln(notes);
    }
    return buf.toString().trim();
  }

  Future<void> _openToothEditor(String compactKey) async {
    final l10n = AppLocalizations.of(context)!;
    final display = toothKeyDisplay(compactKey);
    final list = List<Map<String, dynamic>>.from(_teeth[compactKey] ?? []);

    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (ctx) {
        return SafeArea(
          child: Padding(
            padding: EdgeInsets.only(
              bottom: MediaQuery.viewInsetsOf(ctx).bottom,
            ),
            child: StatefulBuilder(
              builder: (ctx, setLocal) {
                return Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
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
                      else
                        DropdownButtonFormField<int>(
                          decoration: InputDecoration(
                            labelText: l10n.translate('dentalAddService'),
                          ),
                          items: _catalog
                              .map(
                                (s) => DropdownMenuItem(
                                  value: s.id,
                                  child: Text(
                                    s.isFreeConsultation
                                        ? '${s.title} (0 ${s.currency})'
                                        : '${s.title} (${(s.amountMinor / 100).toStringAsFixed(2)} ${s.currency})',
                                    overflow: TextOverflow.ellipsis,
                                  ),
                                ),
                              )
                              .toList(),
                          onChanged: (id) {
                            if (id == null) return;
                            final s = _catalog.firstWhere((e) => e.id == id);
                            setLocal(() {
                              list.add({
                                'serviceId': s.id,
                                'title': s.title,
                                'amountMinor': s.isFreeConsultation ? 0 : s.amountMinor,
                                'currency': s.currency,
                              });
                              _touch();
                            });
                          },
                        ),
                      const SizedBox(height: 12),
                      Flexible(
                        child: ListView.builder(
                          shrinkWrap: true,
                          itemCount: list.length,
                          itemBuilder: (c, i) {
                            final line = list[i];
                            final title = line['title']?.toString() ?? '';
                            final am = (line['amountMinor'] as num?)?.toInt() ?? 0;
                            final cur = line['currency']?.toString() ?? '';
                            return ListTile(
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
                      ),
                      const SizedBox(height: 8),
                      FilledButton(
                        onPressed: () {
                          setState(() {
                            _teeth[compactKey] = List<Map<String, dynamic>>.from(list);
                          });
                          Navigator.pop(ctx);
                        },
                        child: Text(l10n.save),
                      ),
                    ],
                  ),
                );
              },
            ),
          ),
        );
      },
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
              Text(
                l10n.translate('dentalUpperJaw'),
                style: const TextStyle(fontWeight: FontWeight.w700),
              ),
              const SizedBox(height: 6),
              _toothRow(kFdiTeethOrder.sublist(0, 16)),
              const SizedBox(height: 12),
              Text(
                l10n.translate('dentalLowerJaw'),
                style: const TextStyle(fontWeight: FontWeight.w700),
              ),
              const SizedBox(height: 6),
              _toothRow(kFdiTeethOrder.sublist(16, 32)),
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
              TextField(
                controller: _notesCtrl,
                minLines: 2,
                maxLines: 5,
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

  Widget _toothRow(List<String> order) {
    return Wrap(
      spacing: 4,
      runSpacing: 4,
      children: order.map((spaced) {
        final c = toothKeyCompact(spaced);
        final n = (_teeth[c] ?? const []).length;
        final has = n > 0;
        return Material(
          color: has ? widget.brand.withValues(alpha: 0.15) : Colors.grey.shade100,
          borderRadius: BorderRadius.circular(8),
          child: InkWell(
            borderRadius: BorderRadius.circular(8),
            onTap: () => _openToothEditor(c),
            child: SizedBox(
              width: 40,
              height: 44,
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Text(
                    c,
                    style: TextStyle(
                      fontSize: 10,
                      fontWeight: FontWeight.w700,
                      color: has ? widget.brand : Colors.black87,
                    ),
                  ),
                  if (has)
                    Text(
                      '$n',
                      style: TextStyle(fontSize: 10, color: widget.brand),
                    ),
                ],
              ),
            ),
          ),
        );
      }).toList(),
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
