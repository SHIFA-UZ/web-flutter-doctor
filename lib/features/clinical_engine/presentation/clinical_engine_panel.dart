import 'dart:async';

import 'package:flutter/material.dart';
import 'package:shifa_doc_app_v1/features/clinical_engine/data/clinical_engine_api.dart';
import 'package:shifa_doc_app_v1/features/clinical_engine/domain/clinical_engine_models.dart';
import 'package:shifa_doc_app_v1/features/clinical_engine/presentation/clinical_chip_panel.dart';

typedef ClinicalEngineApplyCallback = void Function({
  required ClinicalDiseaseDetail disease,
  required Map<String, String> synthesizedFields,
  required List<ClinicalChipSelection> allSelections,
});

class ClinicalEnginePanel extends StatefulWidget {
  const ClinicalEnginePanel({
    super.key,
    required this.api,
    required this.locale,
    required this.onApplied,
    this.initialDiseaseId,
    this.initialSelections = const [],
    this.onSelectionsChanged,
  });

  final ClinicalEngineApi api;
  final String locale;
  final ClinicalEngineApplyCallback onApplied;
  final String? initialDiseaseId;
  final List<ClinicalChipSelection> initialSelections;
  final ValueChanged<List<ClinicalChipSelection>>? onSelectionsChanged;

  @override
  State<ClinicalEnginePanel> createState() => _ClinicalEnginePanelState();
}

class _ClinicalEnginePanelState extends State<ClinicalEnginePanel> {
  static const _debounceMs = 350;

  bool _loading = true;
  String? _error;
  List<ClinicalGroup> _groups = const [];
  List<ClinicalTopDiagnosis> _top5 = const [];
  List<ClinicalOcclusionChip> _occlusionChips = const [];
  List<ClinicalSharedTemplate> _mucosaTemplates = const [];
  List<ClinicalSharedTemplate> _xrayTemplates = const [];
  ClinicalDiseaseDetail? _selectedDisease;

  final Set<String> _selectedChipIds = {};
  final Map<String, Map<String, String>> _chipVariables = {};
  final Set<String> _selectedOcclusionChipIds = {};
  final Map<String, Map<String, String>> _occlusionVariables = {};
  final Set<String> _selectedSharedChipIds = {};

  final _searchCtrl = TextEditingController();
  Timer? _searchDebounce;
  Timer? _synthDebounce;
  List<ClinicalChip> _searchResults = const [];
  bool _searching = false;

  @override
  void initState() {
    super.initState();
    _bootstrap();
  }

  @override
  void dispose() {
    _searchDebounce?.cancel();
    _synthDebounce?.cancel();
    _searchCtrl.dispose();
    super.dispose();
  }

  Future<void> _bootstrap() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final results = await Future.wait([
        widget.api.fetchGroups(),
        widget.api.fetchTopDiagnoses(),
        widget.api.fetchOcclusionChips(),
        widget.api.fetchSharedTemplates('mucosa'),
        widget.api.fetchSharedTemplates('xray'),
      ]);
      _groups = results[0] as List<ClinicalGroup>;
      _top5 = results[1] as List<ClinicalTopDiagnosis>;
      _occlusionChips = results[2] as List<ClinicalOcclusionChip>;
      _mucosaTemplates = results[3] as List<ClinicalSharedTemplate>;
      _xrayTemplates = results[4] as List<ClinicalSharedTemplate>;
      if (widget.initialDiseaseId != null) {
        await _selectDisease(
          widget.initialDiseaseId!,
          userInitiated: false,
          restoreSelections: widget.initialSelections,
        );
      }
    } catch (e) {
      _error = e.toString();
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _selectDisease(
    String diseaseId, {
    required bool userInitiated,
    List<ClinicalChipSelection> restoreSelections = const [],
  }) async {
    setState(() => _loading = true);
    final detail = await widget.api.fetchDisease(diseaseId);
    if (!mounted) return;

    _selectedChipIds.clear();
    _chipVariables.clear();
    _selectedOcclusionChipIds.clear();
    _occlusionVariables.clear();
    _selectedSharedChipIds.clear();

    if (restoreSelections.isNotEmpty) {
      for (final sel in restoreSelections) {
        if (sel.chipId.startsWith('shared.')) {
          _selectedSharedChipIds.add(sel.chipId);
        } else if (sel.chipId.startsWith('occ.')) {
          _selectedOcclusionChipIds.add(sel.chipId);
          if (sel.variables.isNotEmpty) {
            _occlusionVariables[sel.chipId] = Map.from(sel.variables);
          }
        } else {
          _selectedChipIds.add(sel.chipId);
          if (sel.variables.isNotEmpty) {
            _chipVariables[sel.chipId] = Map.from(sel.variables);
          }
        }
      }
    } else if (userInitiated) {
      _selectedChipIds.addAll(detail?.chips.map((c) => c.chipId) ?? const []);
    }

    setState(() {
      _selectedDisease = detail;
      _loading = false;
    });

    if (detail == null) return;
    _notifySelectionsChanged();
    if (userInitiated) {
      await _applySynthesis(immediate: true);
    } else {
      widget.onApplied(
        disease: detail,
        synthesizedFields: const {},
        allSelections: _buildAllSelections(),
      );
    }
  }

  List<ClinicalChipSelection> _buildAllSelections() {
    return [
      for (final chipId in _selectedChipIds)
        ClinicalChipSelection(chipId: chipId, variables: _chipVariables[chipId] ?? const {}),
      for (final chipId in _selectedOcclusionChipIds)
        ClinicalChipSelection(chipId: chipId, variables: _occlusionVariables[chipId] ?? const {}),
      for (final chipId in _selectedSharedChipIds)
        ClinicalChipSelection(chipId: chipId),
    ];
  }

  void _notifySelectionsChanged() {
    widget.onSelectionsChanged?.call(_buildAllSelections());
  }

  Future<void> _applySynthesis({bool immediate = false}) async {
    final disease = _selectedDisease;
    if (disease == null) return;

    if (!immediate) {
      _synthDebounce?.cancel();
      _synthDebounce = Timer(const Duration(milliseconds: _debounceMs), () {
        _runSynthesis(disease);
      });
      return;
    }
    await _runSynthesis(disease);
  }

  Future<void> _runSynthesis(ClinicalDiseaseDetail disease) async {
    final selections = _buildAllSelections();
    final fields = await widget.api.synthesize(
      locale: widget.locale,
      diseaseId: disease.diseaseId,
      selections: selections,
    );
    if (!mounted) return;
    widget.onApplied(
      disease: disease,
      synthesizedFields: fields,
      allSelections: selections,
    );
  }

  void _onSearchChanged(String value) {
    _searchDebounce?.cancel();
    if (value.trim().length < 2) {
      setState(() => _searchResults = const []);
      return;
    }
    _searchDebounce = Timer(const Duration(milliseconds: _debounceMs), () async {
      setState(() => _searching = true);
      final results = await widget.api.searchChips(
        query: value.trim(),
        locale: widget.locale,
      );
      if (mounted) {
        setState(() {
          _searchResults = results;
          _searching = false;
        });
      }
    });
  }

  Future<void> _addChipFromSearch(ClinicalChip chip) async {
    setState(() {
      _selectedChipIds.add(chip.chipId);
      _searchCtrl.clear();
      _searchResults = const [];
    });
    _notifySelectionsChanged();
    final disease = _selectedDisease;
    if (disease != null) await _applySynthesis(immediate: true);
  }

  Future<void> _onChipToggled(ClinicalChip chip, bool selected) async {
    setState(() {
      if (selected) {
        _selectedChipIds.add(chip.chipId);
      } else {
        _selectedChipIds.remove(chip.chipId);
        _chipVariables.remove(chip.chipId);
      }
    });
    _notifySelectionsChanged();
    final disease = _selectedDisease;
    if (disease != null) await _applySynthesis();
  }

  Future<void> _onSharedToggled(ClinicalSharedTemplate template, bool selected) async {
    setState(() {
      if (selected) {
        _selectedSharedChipIds.add(template.chipId);
      } else {
        _selectedSharedChipIds.remove(template.chipId);
      }
    });
    _notifySelectionsChanged();
    final disease = _selectedDisease;
    if (disease != null) await _applySynthesis();
  }

  Future<void> _onOcclusionToggled(ClinicalOcclusionChip chip, bool selected) async {
    setState(() {
      if (selected) {
        _selectedOcclusionChipIds.add(chip.chipId);
      } else {
        _selectedOcclusionChipIds.remove(chip.chipId);
        _occlusionVariables.remove(chip.chipId);
      }
    });
    _notifySelectionsChanged();
    final disease = _selectedDisease;
    if (disease != null) await _applySynthesis();
  }

  Future<void> _showGroupPicker() async {
    final group = await showModalBottomSheet<ClinicalGroup>(
      context: context,
      builder: (ctx) => ListView(
        children: [
          for (final g in _groups)
            ListTile(
              title: Text(g.names.forLocale(widget.locale)),
              onTap: () => Navigator.pop(ctx, g),
            ),
        ],
      ),
    );
    if (group == null) return;
    final diseases = await widget.api.fetchDiseases(group.groupId);
    if (!mounted) return;
    final disease = await showModalBottomSheet<ClinicalDiseaseSummary>(
      context: context,
      isScrollControlled: true,
      builder: (ctx) => DraggableScrollableSheet(
        expand: false,
        initialChildSize: 0.6,
        builder: (_, controller) => ListView(
          controller: controller,
          children: [
            for (final d in diseases)
              ListTile(
                title: Text(d.names.forLocale(widget.locale)),
                subtitle: Text(d.icdCodes.join(', ')),
                onTap: () => Navigator.pop(ctx, d),
              ),
          ],
        ),
      ),
    );
    if (disease != null) {
      await _selectDisease(disease.diseaseId, userInitiated: true);
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_loading && _selectedDisease == null) {
      return const Padding(
        padding: EdgeInsets.symmetric(vertical: 12),
        child: Center(child: CircularProgressIndicator()),
      );
    }
    if (_error != null) {
      return Text(_error!, style: const TextStyle(color: Colors.red));
    }

    final disease = _selectedDisease;
    return Card(
      margin: const EdgeInsets.only(bottom: 16),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Expanded(
                  child: Text(
                    'Clinical Engine',
                    style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700),
                  ),
                ),
                TextButton.icon(
                  onPressed: _showGroupPicker,
                  icon: const Icon(Icons.medical_information_outlined, size: 18),
                  label: const Text('Diagnosis'),
                ),
              ],
            ),
            if (_top5.isNotEmpty && disease == null) ...[
              const Text('Top diagnoses', style: TextStyle(fontWeight: FontWeight.w600)),
              const SizedBox(height: 8),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  for (final item in _top5)
                    ActionChip(
                      label: Text(item.names.forLocale(widget.locale)),
                      onPressed: () => _selectDisease(item.diseaseId, userInitiated: true),
                    ),
                ],
              ),
              const SizedBox(height: 12),
            ],
            if (disease != null) ...[
              Text(
                disease.names.forLocale(widget.locale),
                style: const TextStyle(fontWeight: FontWeight.w600),
              ),
              const SizedBox(height: 8),
              TextField(
                controller: _searchCtrl,
                decoration: InputDecoration(
                  labelText: 'Search chips',
                  hintText: 'Type at least 2 characters',
                  border: const OutlineInputBorder(),
                  isDense: true,
                  prefixIcon: const Icon(Icons.search, size: 20),
                  suffixIcon: _searching
                      ? const Padding(
                          padding: EdgeInsets.all(10),
                          child: SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2)),
                        )
                      : null,
                ),
                onChanged: _onSearchChanged,
              ),
              if (_searchResults.isNotEmpty) ...[
                const SizedBox(height: 8),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    for (final chip in _searchResults)
                      ActionChip(
                        label: Text(chip.labels.forLocale(widget.locale)),
                        onPressed: () => _addChipFromSearch(chip),
                      ),
                  ],
                ),
                const SizedBox(height: 8),
              ],
              ClinicalChipPanel(
                title: 'Complaints',
                field: ClinicalFieldMapping.complaints,
                chips: disease.chips,
                locale: widget.locale,
                selectedChipIds: _selectedChipIds,
                chipVariables: _chipVariables,
                onChipToggled: _onChipToggled,
                onVariableChanged: (chipId, variable, value) async {
                  _chipVariables.putIfAbsent(chipId, () => {})[variable] = value;
                  _notifySelectionsChanged();
                  await _applySynthesis();
                },
              ),
              ClinicalChipPanel(
                title: 'KASALLIK RIVOJLANISHI',
                field: ClinicalFieldMapping.morbi,
                chips: disease.chips,
                locale: widget.locale,
                selectedChipIds: _selectedChipIds,
                chipVariables: _chipVariables,
                onChipToggled: _onChipToggled,
                onVariableChanged: (chipId, variable, value) async {
                  _chipVariables.putIfAbsent(chipId, () => {})[variable] = value;
                  _notifySelectionsChanged();
                  await _applySynthesis();
                },
              ),
              ClinicalChipPanel(
                title: 'Objective',
                field: ClinicalFieldMapping.objective,
                chips: disease.chips,
                locale: widget.locale,
                selectedChipIds: _selectedChipIds,
                chipVariables: _chipVariables,
                onChipToggled: _onChipToggled,
                onVariableChanged: (chipId, variable, value) async {
                  _chipVariables.putIfAbsent(chipId, () => {})[variable] = value;
                  _notifySelectionsChanged();
                  await _applySynthesis();
                },
              ),
              ClinicalOcclusionChipPanel(
                chips: _occlusionChips,
                locale: widget.locale,
                selectedChipIds: _selectedOcclusionChipIds,
                chipVariables: _occlusionVariables,
                onChipToggled: _onOcclusionToggled,
                onVariableChanged: (chipId, variable, value) async {
                  _occlusionVariables.putIfAbsent(chipId, () => {})[variable] = value;
                  _notifySelectionsChanged();
                  await _applySynthesis();
                },
              ),
              ClinicalChipPanel(
                title: 'Oral cavity',
                field: ClinicalFieldMapping.oralCavity,
                chips: disease.chips,
                locale: widget.locale,
                selectedChipIds: _selectedChipIds,
                chipVariables: _chipVariables,
                onChipToggled: _onChipToggled,
                onVariableChanged: (chipId, variable, value) async {
                  _chipVariables.putIfAbsent(chipId, () => {})[variable] = value;
                  _notifySelectionsChanged();
                  await _applySynthesis();
                },
              ),
              ClinicalSharedChipPanel(
                title: 'Shared mucosa variants',
                field: ClinicalFieldMapping.oralCavity,
                templates: _mucosaTemplates,
                locale: widget.locale,
                selectedChipIds: _selectedSharedChipIds,
                onTemplateToggled: _onSharedToggled,
              ),
              ClinicalChipPanel(
                title: 'X-ray',
                field: ClinicalFieldMapping.xray,
                chips: disease.chips,
                locale: widget.locale,
                selectedChipIds: _selectedChipIds,
                chipVariables: _chipVariables,
                onChipToggled: _onChipToggled,
                onVariableChanged: (chipId, variable, value) async {
                  _chipVariables.putIfAbsent(chipId, () => {})[variable] = value;
                  _notifySelectionsChanged();
                  await _applySynthesis();
                },
              ),
              ClinicalSharedChipPanel(
                title: 'Shared x-ray templates',
                field: ClinicalFieldMapping.xray,
                templates: _xrayTemplates,
                locale: widget.locale,
                selectedChipIds: _selectedSharedChipIds,
                onTemplateToggled: _onSharedToggled,
              ),
              ClinicalChipPanel(
                title: 'Treatment (stage 1)',
                field: ClinicalFieldMapping.treatment1,
                chips: disease.chips,
                locale: widget.locale,
                selectedChipIds: _selectedChipIds,
                chipVariables: _chipVariables,
                onChipToggled: _onChipToggled,
                onVariableChanged: (chipId, variable, value) async {
                  _chipVariables.putIfAbsent(chipId, () => {})[variable] = value;
                  _notifySelectionsChanged();
                  await _applySynthesis();
                },
              ),
              ClinicalChipPanel(
                title: 'Treatment (stage 2 / epicrisis)',
                field: ClinicalFieldMapping.treatment2,
                chips: disease.chips,
                locale: widget.locale,
                selectedChipIds: _selectedChipIds,
                chipVariables: _chipVariables,
                onChipToggled: _onChipToggled,
                onVariableChanged: (chipId, variable, value) async {
                  _chipVariables.putIfAbsent(chipId, () => {})[variable] = value;
                  _notifySelectionsChanged();
                  await _applySynthesis();
                },
              ),
              ClinicalChipPanel(
                title: 'Recommendations',
                field: ClinicalFieldMapping.recommendations,
                chips: disease.chips,
                locale: widget.locale,
                selectedChipIds: _selectedChipIds,
                chipVariables: _chipVariables,
                onChipToggled: _onChipToggled,
                onVariableChanged: (chipId, variable, value) async {
                  _chipVariables.putIfAbsent(chipId, () => {})[variable] = value;
                  _notifySelectionsChanged();
                  await _applySynthesis();
                },
              ),
            ],
          ],
        ),
      ),
    );
  }
}
