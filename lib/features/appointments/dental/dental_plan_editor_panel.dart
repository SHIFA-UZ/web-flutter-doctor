import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/widgets/scrollable_sheet_dialog.dart';
import 'package:shifa_doc_app_v1/features/appointments/dental/dental_chart_codec.dart';
import 'package:shifa_doc_app_v1/features/appointments/dental/dental_fdi_chart.dart';
import 'package:shifa_doc_app_v1/features/appointments/dental/dental_visit_documentation_panel.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_models.dart';

class _PlanServiceGroup {
  const _PlanServiceGroup({
    required this.groupId,
    required this.sortOrder,
    required this.groupName,
    required this.services,
  });

  final int? groupId;
  final int sortOrder;
  final String? groupName;
  final List<PlanServiceOption> services;

  String label(AppLocalizations l10n) {
    if (groupId == null) {
      return l10n.translate('serviceGroupNone');
    }
    final n = groupName?.trim();
    if (n == null || n.isEmpty) return l10n.translate('serviceGroupNone');
    return n;
  }
}

List<_PlanServiceGroup> _aggregatePlanServiceGroups(
  List<PlanServiceOption> opts,
) {
  final buckets = <Object, List<PlanServiceOption>>{};
  for (final o in opts) {
    final key = o.groupId ?? o.groupName?.trim().toLowerCase() ?? _ungroupedBucket;
    buckets.putIfAbsent(key, () => []).add(o);
  }
  final out = <_PlanServiceGroup>[];
  buckets.forEach((key, services) {
    if (services.isEmpty) return;
    final sort = services
        .map((s) => s.groupSortOrder)
        .reduce((a, b) => a < b ? a : b);
    final int? gid = key is int ? key : services.first.groupId;
    final String? name = key == _ungroupedBucket
        ? null
        : (key is String ? services.first.groupName : services.first.groupName);
    out.add(
      _PlanServiceGroup(
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

const Object _ungroupedBucket = Object();

bool _planServiceGroupsAreNamed(List<_PlanServiceGroup> groups) {
  if (groups.length > 1) return true;
  if (groups.length == 1) {
    final g = groups.first;
    return g.groupId != null || (g.groupName?.trim().isNotEmpty ?? false);
  }
  return false;
}

/// FDI teeth-chart editor for comprehensive treatment plans (planning mode).
/// Uses clinic [PlanServiceOption] catalog instead of doctor-only services.
class DentalPlanEditorPanel extends StatefulWidget {
  const DentalPlanEditorPanel({
    super.key,
    required this.brand,
    required this.catalog,
    this.initialDocumentationJson,
    this.showFullClinicCatalogHint = false,
    this.onChanged,
  });

  final Color brand;
  final List<PlanServiceOption> catalog;
  final String? initialDocumentationJson;
  final bool showFullClinicCatalogHint;
  final VoidCallback? onChanged;

  @override
  State<DentalPlanEditorPanel> createState() => DentalPlanEditorPanelState();
}

class DentalPlanEditorPanelState extends State<DentalPlanEditorPanel> {
  final Map<String, List<Map<String, dynamic>>> _teeth = {};
  final TextEditingController _discountCtrl = TextEditingController(text: '0');
  final TextEditingController _notesCtrl = TextEditingController();
  DentalDentition _dentition = DentalDentition.permanent;

  @override
  void initState() {
    super.initState();
    _initTeethKeys();
    _hydrateFromJson(widget.initialDocumentationJson);
    _discountCtrl.addListener(_touch);
    _notesCtrl.addListener(_touch);
  }

  @override
  void dispose() {
    _discountCtrl.dispose();
    _notesCtrl.dispose();
    super.dispose();
  }

  void _touch() => widget.onChanged?.call();

  double _discountValue() {
    final v = double.tryParse(_discountCtrl.text.trim().replaceAll(',', '.'));
    if (v == null || v < 0) return 0;
    if (v > 100) return 100;
    return v;
  }

  int _computeSubtotalMinor() {
    var total = 0;
    for (final list in _teeth.values) {
      for (final line in list) {
        total += (line['amountMinor'] as num?)?.toInt() ?? 0;
      }
    }
    return total;
  }

  void _initTeethKeys() {
    for (final d in DentalDentition.values) {
      for (final fdi in DentalChartCodec.visitDocTeethOrder(d)) {
        _teeth.putIfAbsent(fdi, () => []);
      }
    }
    _teeth.putIfAbsent(DentalChartCodec.generalServicesKey, () => []);
  }

  String _resolveKey(String raw, {DentalDentition? dentitionHint}) {
    final s = raw.replaceAll(' ', '');
    if (s == DentalChartCodec.generalServicesKey) return s;
    return DentalChartCodec.normalizeToothKey(
      s,
      dentition: dentitionHint ?? _dentition,
    );
  }

  void _hydrateFromJson(String? raw) {
    if (raw == null || raw.trim().isEmpty) return;
    try {
      final map = jsonDecode(raw);
      if (map is! Map) return;
      final dentRaw = map['dentition']?.toString().trim().toLowerCase();
      if (dentRaw == 'primary') {
        _dentition = DentalDentition.primary;
      }
      for (final e in _teeth.keys) {
        _teeth[e] = [];
      }
      final teethRaw = map['teeth'];
      if (teethRaw is Map) {
        teethRaw.forEach((k, v) {
          final key = _resolveKey(k.toString());
          if (!_teeth.containsKey(key) || v is! List) return;
          _teeth[key] = v
              .whereType<Map>()
              .map((e) => Map<String, dynamic>.from(e))
              .toList();
        });
      }
      final disc = map['discountPercent'];
      if (disc is num) {
        _discountCtrl.text = disc.toString();
      }
      final notes = map['notes']?.toString();
      if (notes != null && notes.isNotEmpty) {
        _notesCtrl.text = notes;
      }
    } catch (_) {}
  }

  void _touchEditor() => _touch();

  bool get hasContent {
    for (final list in _teeth.values) {
      if (list.isNotEmpty) return true;
    }
    return false;
  }

  int get totalMinor {
    final sub = _computeSubtotalMinor();
    final disc = _discountValue();
    return (sub * (1 - disc / 100)).round();
  }

  String get currency {
    for (final list in _teeth.values) {
      for (final line in list) {
        final c = line['currency']?.toString();
        if (c != null && c.isNotEmpty) return c;
      }
    }
    return 'UZS';
  }

  String buildDocumentationJson() => jsonEncode(_toPayload());

  Map<String, dynamic> _toPayload() {
    final teethOut = <String, dynamic>{};
    _teeth.forEach((k, v) {
      if (v.isNotEmpty) teethOut[k] = v;
    });
    return {
      'version': 2,
      'dentition': _dentition == DentalDentition.primary ? 'primary' : 'permanent',
      'teeth': teethOut,
      'discountPercent': _discountValue(),
      'notes': _notesCtrl.text.trim(),
    };
  }

  PlanServiceOption? _findCatalog(String? serviceKey, int? catalogItemId) {
    if (serviceKey != null) {
      for (final c in widget.catalog) {
        if (c.key == serviceKey) return c;
      }
    }
    if (catalogItemId != null) {
      for (final c in widget.catalog) {
        if (c.catalogItemId == catalogItemId) return c;
      }
    }
    return null;
  }

  /// Flatten teeth map into treatment-plan line payloads with specialtyMetadata.
  List<Map<String, dynamic>> buildLineRequests({String source = 'PLAN_WIZARD'}) {
    final lines = <Map<String, dynamic>>[];
    var order = 0;
    final dentitionStr =
        _dentition == DentalDentition.primary ? 'primary' : 'permanent';
    final keys = [
      ...DentalChartCodec.visitDocTeethOrder(_dentition),
      DentalChartCodec.generalServicesKey,
    ];
    final rawLines = <({String toothKey, Map<String, dynamic> row, int amount})>[];
    for (final toothKey in keys) {
      for (final row in _teeth[toothKey] ?? const []) {
        final amount = (row['amountMinor'] as num?)?.toInt() ?? 0;
        if (amount < 0) continue;
        rawLines.add((toothKey: toothKey, row: row, amount: amount));
      }
    }
    final subtotal = rawLines.fold<int>(0, (s, e) => s + e.amount);
    final discountPct = _discountValue();
    var discountLeft = subtotal > 0
        ? (subtotal * discountPct / 100).round()
        : 0;
    for (var i = 0; i < rawLines.length; i++) {
      final entry = rawLines[i];
      final toothKey = entry.toothKey;
      final row = entry.row;
      final amount = entry.amount;
      final serviceKey = row['serviceKey']?.toString();
      final catalogItemId = (row['catalogItemId'] as num?)?.toInt();
      final catalog = _findCatalog(serviceKey, catalogItemId);
      final title = row['title']?.toString() ?? catalog?.title ?? '';
      final ccy = row['currency']?.toString() ?? catalog?.currency ?? 'UZS';
      if (title.isEmpty) continue;
      var lineDiscount = 0;
      if (discountLeft > 0 && subtotal > 0) {
        lineDiscount = i == rawLines.length - 1
            ? discountLeft
            : (amount * discountPct / 100).round();
        discountLeft -= lineDiscount;
      }
      final isGeneral = toothKey == DentalChartCodec.generalServicesKey;
      final meta = <String, dynamic>{
        if (!isGeneral) 'fdi': toothKey,
        'dentition': dentitionStr,
        'source': source,
      };
      lines.add({
        if (catalog?.catalogItemId != null)
          'catalogItemId': catalog!.catalogItemId,
        'title': title,
        'quantity': 1,
        'unitPriceMinor': amount,
        'discountMinor': lineDiscount,
        'currency': ccy,
        'sortOrder': order,
        'status': 'PLANNED',
        'specialtyMetadata': jsonEncode(meta),
      });
      order += 1;
    }
    return lines;
  }

  List<PlanServiceOption> get _activeCatalog =>
      widget.catalog.where((c) => c.active).toList();

  Widget _planServiceListTile(
    PlanServiceOption s,
    void Function(void Function()) setLocal,
    List<Map<String, dynamic>> list,
  ) {
    final priceLine =
        '${(s.defaultPriceMinor / 100).toStringAsFixed(2)} ${s.currency}';
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
            'serviceKey': s.key,
            if (s.catalogItemId != null) 'catalogItemId': s.catalogItemId,
            'title': s.title,
            'amountMinor': s.defaultPriceMinor,
            'currency': s.currency,
          });
        });
        _touch();
      },
    );
  }

  Future<void> _openServicesEditor(
    String toothKey, {
    required String titlePrefix,
  }) async {
    final l10n = AppLocalizations.of(context)!;
    final resolved = _resolveKey(toothKey);
    final list = List<Map<String, dynamic>>.from(_teeth[resolved] ?? const []);
    final display = toothKey == DentalChartCodec.generalServicesKey
        ? l10n.translate('dentalGeneralServicesShort')
        : toothKeyDisplay(resolved, dentition: _dentition);
    final catalog = _activeCatalog;
    final searchCtrl = TextEditingController();

    try {
      await showScrollableFormBottomSheetWithFooter<void>(
      context: context,
      includeBottomNavClearance: false,
      bodyBuilder: (ctx) {
        var activeGroupIndex = 0;
        return StatefulBuilder(
          builder: (ctx, setLocal) {
            final query = searchCtrl.text.trim().toLowerCase();
            final filtered = query.isEmpty
                ? catalog
                : catalog
                    .where(
                      (s) =>
                          s.title.toLowerCase().contains(query) ||
                          (s.code?.toLowerCase().contains(query) ?? false),
                    )
                    .toList();
            final groups = _aggregatePlanServiceGroups(filtered);
            final listHeight = (MediaQuery.sizeOf(ctx).height * 0.45)
                .clamp(260.0, 520.0);
            final maxH = (MediaQuery.sizeOf(ctx).height * 0.38).clamp(220.0, 360.0);

            return Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text(
                  '$titlePrefix — $display',
                  style: Theme.of(ctx).textTheme.titleMedium,
                ),
                const SizedBox(height: 8),
                if (catalog.length > 8)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 8),
                    child: TextField(
                      controller: searchCtrl,
                      decoration: InputDecoration(
                        hintText: l10n.translate('search'),
                        prefixIcon: const Icon(Icons.search, size: 20),
                        isDense: true,
                      ),
                      onChanged: (_) => setLocal(() => activeGroupIndex = 0),
                    ),
                  ),
                if (catalog.isEmpty)
                  Text(
                    l10n.translate('treatmentPlanWizardNoCatalog'),
                    style: TextStyle(color: Colors.grey.shade600),
                  )
                else if (filtered.isEmpty)
                  Text(
                    l10n.translate('dentalPlanEditorNoSearchMatches'),
                    style: TextStyle(color: Colors.grey.shade600),
                  )
                else ...[
                  Text(
                    l10n.translate('dentalAddService'),
                    style: Theme.of(ctx).textTheme.labelLarge,
                  ),
                  const SizedBox(height: 8),
                  if (!_planServiceGroupsAreNamed(groups))
                    SizedBox(
                      height: listHeight,
                      child: ListView.builder(
                        itemCount: filtered.length,
                        itemBuilder: (c, i) => _planServiceListTile(
                          filtered[i],
                          setLocal,
                          list,
                        ),
                      ),
                    )
                  else if (MediaQuery.sizeOf(ctx).width >= 640)
                    SizedBox(
                      height: maxH,
                      child: Builder(
                        builder: (context) {
                          final n = groups.length;
                          activeGroupIndex = activeGroupIndex.clamp(0, n - 1);
                          final g = groups[activeGroupIndex];
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
                                        final grp = groups[i];
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
                                    itemBuilder: (c, j) => _planServiceListTile(
                                      g.services[j],
                                      setLocal,
                                      list,
                                    ),
                                  ),
                                ),
                              ),
                            ],
                          );
                        },
                      ),
                    )
                  else
                    SizedBox(
                      height: listHeight,
                      child: CustomScrollView(
                        slivers: [
                          for (final g in groups) ...[
                            SliverToBoxAdapter(
                              child: Padding(
                                padding: const EdgeInsets.only(
                                  top: 10,
                                  bottom: 4,
                                ),
                                child: Text(
                                  g.label(l10n),
                                  style: Theme.of(ctx)
                                      .textTheme
                                      .titleSmall
                                      ?.copyWith(fontWeight: FontWeight.w700),
                                ),
                              ),
                            ),
                            SliverList(
                              delegate: SliverChildBuilderDelegate(
                                (context, j) => _planServiceListTile(
                                  g.services[j],
                                  setLocal,
                                  list,
                                ),
                                childCount: g.services.length,
                              ),
                            ),
                          ],
                        ],
                      ),
                    ),
                ],
                if (list.isNotEmpty) ...[
                  const SizedBox(height: 12),
                  Text(
                    l10n.translate('dentalSelectedServices'),
                    style: Theme.of(ctx).textTheme.labelLarge,
                  ),
                  for (var i = 0; i < list.length; i++)
                    ListTile(
                      contentPadding: EdgeInsets.zero,
                      title: Text(list[i]['title']?.toString() ?? ''),
                      trailing: IconButton(
                        icon: const Icon(Icons.delete_outline),
                        onPressed: () {
                          setLocal(() => list.removeAt(i));
                          _touch();
                        },
                      ),
                    ),
                ],
              ],
            );
          },
        );
      },
      footer: Builder(
        builder: (sheetCtx) => FilledButton(
          onPressed: () {
            setState(() => _teeth[resolved] = List.from(list));
            _touch();
            Navigator.pop(sheetCtx);
          },
          child: Text(l10n.save),
        ),
      ),
    );
    } finally {
      searchCtrl.dispose();
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final generalCount =
        (_teeth[DentalChartCodec.generalServicesKey] ?? const []).length;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(
          l10n.translate('dentalPlanEditorIntro'),
          style: Theme.of(context).textTheme.bodySmall,
        ),
        if (widget.showFullClinicCatalogHint) ...[
          const SizedBox(height: 4),
          Text(
            l10n.translate('dentalPlanEditorCatalogHint'),
            style: Theme.of(context).textTheme.bodySmall?.copyWith(
                  color: Colors.grey.shade700,
                  fontStyle: FontStyle.italic,
                ),
          ),
        ],
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
        ),
        const SizedBox(height: 8),
        OutlinedButton.icon(
          onPressed: () => _openServicesEditor(
            DentalChartCodec.generalServicesKey,
            titlePrefix: l10n.translate('dentalGeneralServices'),
          ),
          icon: const Icon(Icons.medical_services_outlined, size: 18),
          label: Text(
            '${l10n.translate('dentalGeneralServices')}'
            '${generalCount > 0 ? ' ($generalCount)' : ''}',
          ),
        ),
        const SizedBox(height: 8),
        Row(
          children: [
            Expanded(
              flex: 2,
              child: TextField(
                controller: _discountCtrl,
                keyboardType:
                    const TextInputType.numberWithOptions(decimal: true),
                decoration: InputDecoration(
                  labelText: l10n.translate('dentalDiscountPercent'),
                  suffixText: '%',
                ),
              ),
            ),
          ],
        ),
        const SizedBox(height: 8),
        TextField(
          controller: _notesCtrl,
          decoration: InputDecoration(
            labelText: l10n.translate('dentalPlanEditorNotes'),
          ),
          maxLines: 2,
        ),
        if (hasContent) ...[
          const SizedBox(height: 12),
          Text(
            '${l10n.translate('dentalPlanEditorTotal')}: '
            '${(totalMinor / 100).toStringAsFixed(2)} $currency',
            style: Theme.of(context).textTheme.titleSmall,
          ),
        ],
      ],
    );
  }
}
