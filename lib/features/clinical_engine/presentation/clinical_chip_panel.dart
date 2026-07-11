import 'package:flutter/material.dart';
import 'package:shifa_doc_app_v1/features/clinical_engine/domain/clinical_engine_models.dart';

typedef ClinicalChipToggle = void Function(ClinicalChip chip, bool selected);

class ClinicalChipPanel extends StatelessWidget {
  const ClinicalChipPanel({
    super.key,
    required this.title,
    required this.field,
    required this.chips,
    required this.locale,
    required this.selectedChipIds,
    required this.chipVariables,
    required this.onChipToggled,
    required this.onVariableChanged,
  });

  final String title;
  final String field;
  final List<ClinicalChip> chips;
  final String locale;
  final Set<String> selectedChipIds;
  final Map<String, Map<String, String>> chipVariables;
  final ClinicalChipToggle onChipToggled;
  final void Function(String chipId, String variable, String value) onVariableChanged;

  @override
  Widget build(BuildContext context) {
    final fieldChips = chips.where((c) => c.field == field).toList()
      ..sort((a, b) => a.priority.compareTo(b.priority));
    if (fieldChips.isEmpty) return const SizedBox.shrink();

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          title,
          style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600),
        ),
        const SizedBox(height: 8),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            for (final chip in fieldChips)
              FilterChip(
                label: Text(chip.labels.forLocale(locale)),
                selected: selectedChipIds.contains(chip.chipId),
                onSelected: (selected) => onChipToggled(chip, selected),
              ),
          ],
        ),
        for (final chip in fieldChips)
          if (selectedChipIds.contains(chip.chipId) && chip.variables.isNotEmpty)
            Padding(
              padding: const EdgeInsets.only(top: 8),
              child: _VariableInputs(
                chip: chip,
                locale: locale,
                values: chipVariables[chip.chipId] ?? {},
                onChanged: (variable, value) => onVariableChanged(chip.chipId, variable, value),
              ),
            ),
        const SizedBox(height: 12),
      ],
    );
  }
}

class _VariableInputs extends StatefulWidget {
  const _VariableInputs({
    required this.chip,
    required this.locale,
    required this.values,
    required this.onChanged,
  });

  final ClinicalChip chip;
  final String locale;
  final Map<String, String> values;
  final void Function(String variable, String value) onChanged;

  @override
  State<_VariableInputs> createState() => _VariableInputsState();
}

class _VariableInputsState extends State<_VariableInputs> {
  late final Map<String, TextEditingController> _controllers;

  @override
  void initState() {
    super.initState();
    _controllers = {
      for (final variable in widget.chip.variables)
        variable: TextEditingController(text: widget.values[variable] ?? ''),
    };
  }

  @override
  void dispose() {
    for (final c in _controllers.values) {
      c.dispose();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        for (final variable in widget.chip.variables)
          Padding(
            padding: const EdgeInsets.only(bottom: 6),
            child: TextField(
              decoration: InputDecoration(
                labelText: '$variable (${widget.chip.labels.forLocale(widget.locale)})',
                border: const OutlineInputBorder(),
                isDense: true,
              ),
              controller: _controllers[variable],
              onChanged: (v) => widget.onChanged(variable, v),
            ),
          ),
      ],
    );
  }
}

class ClinicalSharedChipPanel extends StatelessWidget {
  const ClinicalSharedChipPanel({
    super.key,
    required this.title,
    required this.field,
    required this.templates,
    required this.locale,
    required this.selectedChipIds,
    required this.onTemplateToggled,
  });

  final String title;
  final String field;
  final List<ClinicalSharedTemplate> templates;
  final String locale;
  final Set<String> selectedChipIds;
  final void Function(ClinicalSharedTemplate template, bool selected) onTemplateToggled;

  @override
  Widget build(BuildContext context) {
    final fieldTemplates = templates.where((t) => t.field == field).toList()
      ..sort((a, b) => a.priority.compareTo(b.priority));
    if (fieldTemplates.isEmpty) return const SizedBox.shrink();

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          title,
          style: TextStyle(fontSize: 12, fontWeight: FontWeight.w500, color: Colors.grey.shade700),
        ),
        const SizedBox(height: 6),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            for (final template in fieldTemplates)
              FilterChip(
                label: Text(template.labels.forLocale(locale)),
                selected: selectedChipIds.contains(template.chipId),
                onSelected: (selected) => onTemplateToggled(template, selected),
              ),
          ],
        ),
        const SizedBox(height: 8),
      ],
    );
  }
}

class ClinicalOcclusionChipPanel extends StatelessWidget {
  const ClinicalOcclusionChipPanel({
    super.key,
    required this.chips,
    required this.locale,
    required this.selectedChipIds,
    required this.chipVariables,
    required this.onChipToggled,
    required this.onVariableChanged,
  });

  final List<ClinicalOcclusionChip> chips;
  final String locale;
  final Set<String> selectedChipIds;
  final Map<String, Map<String, String>> chipVariables;
  final void Function(ClinicalOcclusionChip chip, bool selected) onChipToggled;
  final void Function(String chipId, String variable, String value) onVariableChanged;

  @override
  Widget build(BuildContext context) {
    if (chips.isEmpty) return const SizedBox.shrink();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          'Clinical Engine — Occlusion',
          style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600),
        ),
        const SizedBox(height: 8),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            for (final chip in chips)
              FilterChip(
                label: Text(chip.labels.forLocale(locale)),
                selected: selectedChipIds.contains(chip.chipId),
                onSelected: (selected) => onChipToggled(chip, selected),
              ),
          ],
        ),
        for (final chip in chips)
          if (selectedChipIds.contains(chip.chipId) && chip.variables.isNotEmpty)
            Padding(
              padding: const EdgeInsets.only(top: 8),
              child: Column(
                children: [
                  for (final variable in chip.variables)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 6),
                      child: TextField(
                        decoration: InputDecoration(
                          labelText: variable,
                          border: const OutlineInputBorder(),
                          isDense: true,
                        ),
                        onChanged: (v) => onVariableChanged(chip.chipId, variable, v),
                      ),
                    ),
                ],
              ),
            ),
        const SizedBox(height: 12),
      ],
    );
  }
}
