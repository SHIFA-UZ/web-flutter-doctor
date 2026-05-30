import 'package:flutter/material.dart';
import 'package:shifa_doc_app_v1/core/models/profession_model.dart';
import 'package:shifa_doc_app_v1/core/providers/language_provider.dart';
import 'package:shifa_doc_app_v1/core/services/profession_service.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class SearchableProfessionDropdown extends ConsumerStatefulWidget {
  final String? value;
  final List<String>? values;
  final ValueChanged<String?>? onChanged;
  final ValueChanged<List<String>>? onChangedMultiple;
  final String? hintText;
  final String? labelText;
  final bool useBackend;
  final bool allowMultiple;

  const SearchableProfessionDropdown({
    Key? key,
    this.value,
    this.values,
    this.onChanged,
    this.onChangedMultiple,
    this.hintText,
    this.labelText,
    this.useBackend = true,
    this.allowMultiple = false,
  }) : super(key: key);

  @override
  ConsumerState<SearchableProfessionDropdown> createState() =>
      _SearchableProfessionDropdownState();
}

class _SearchableProfessionDropdownState
    extends ConsumerState<SearchableProfessionDropdown> {
  final TextEditingController _searchController = TextEditingController();
  List<ProfessionModel> _filteredProfessions = [];
  String? _selectedValue;
  List<String> _selectedValues = [];
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _selectedValue = widget.value;
    _selectedValues = List<String>.from(widget.values ?? const []);
    _loadProfessions();
  }

  Future<void> _loadProfessions() async {
    setState(() => _isLoading = true);
    try {
      final locale = ref.read(languageProvider).locale;
      final professions = await ProfessionService.getProfessions(
        language: locale.backendLanguageCode,
        useBackend: widget.useBackend,
        ref: ref,
      );
      if (mounted) {
        setState(() {
          _filteredProfessions = professions;
          _isLoading = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _filteredProfessions = ProfessionData.allProfessions;
          _isLoading = false;
        });
      }
    }
  }

  @override
  void didUpdateWidget(SearchableProfessionDropdown oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.value != widget.value) {
      _selectedValue = widget.value;
    }
    if (oldWidget.values != widget.values) {
      _selectedValues = List<String>.from(widget.values ?? const []);
    }
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  Future<void> _updateFilteredList(String query) async {
    if (query.isEmpty) {
      await _loadProfessions();
      return;
    }

    setState(() {
      _filteredProfessions =
          _filteredProfessions.where((p) => p.matches(query)).toList();
    });

    if (widget.useBackend && query.isNotEmpty) {
      try {
        final locale = ref.read(languageProvider).locale;
        final backendResults = await ProfessionService.fetchFromBackend(
          language: locale.backendLanguageCode,
          search: query,
          ref: ref,
        );
        if (backendResults != null && mounted) {
          setState(() {
            _filteredProfessions = backendResults;
          });
        }
      } catch (e) {
        // Fallback to local search (already done above)
      }
    }
  }

  ProfessionModel? _findProfession(String english) {
    try {
      return _filteredProfessions.firstWhere((p) => p.english == english);
    } catch (_) {
      return ProfessionData.findByEnglish(english);
    }
  }

  Future<void> _showSearchDialog() async {
    _searchController.clear();
    _updateFilteredList('');

    if (widget.allowMultiple) {
      final result = await showDialog<List<String>>(
        context: context,
        builder: (context) => _MultiSearchDialog(
          searchController: _searchController,
          initialFilteredProfessions: _filteredProfessions,
          selectedValues: _selectedValues,
          locale: ref.read(languageProvider).locale,
        ),
      );

      if (result != null && widget.onChangedMultiple != null) {
        widget.onChangedMultiple!(result);
        setState(() => _selectedValues = result);
      }
      return;
    }

    final result = await showDialog<String>(
      context: context,
      builder: (context) => _SearchDialog(
        searchController: _searchController,
        initialFilteredProfessions: _filteredProfessions,
        selectedValue: _selectedValue,
        locale: ref.read(languageProvider).locale,
        useBackend: widget.useBackend,
      ),
    );

    if (result != null && widget.onChanged != null) {
      widget.onChanged!(result);
      setState(() {
        _selectedValue = result;
      });
    }
  }

  String _displayText(Locale locale) {
    if (widget.allowMultiple) {
      if (_selectedValues.isEmpty) {
        return widget.hintText ??
            AppLocalizations.of(context)!.selectProfession;
      }
      final labels = _selectedValues
          .map((v) => _findProfession(v)?.getDisplayText(locale) ?? v)
          .toList();
      return labels.join(', ');
    }

    final selectedProfession =
        _selectedValue != null ? _findProfession(_selectedValue!) : null;
    if (selectedProfession != null) {
      return selectedProfession.getDisplayText(locale);
    }
    return widget.hintText ?? AppLocalizations.of(context)!.selectProfession;
  }

  @override
  Widget build(BuildContext context) {
    final locale = ref.watch(languageProvider).locale;
    final hasSelection = widget.allowMultiple
        ? _selectedValues.isNotEmpty
        : _selectedValue != null;

    return InkWell(
      onTap: _isLoading ? null : _showSearchDialog,
      child: InputDecorator(
        decoration: InputDecoration(
          hintText: widget.hintText ??
              AppLocalizations.of(context)!.selectProfession,
          labelText:
              widget.labelText ?? AppLocalizations.of(context)!.profession,
          border: const OutlineInputBorder(),
          suffixIcon: _isLoading
              ? const SizedBox(
                  width: 20,
                  height: 20,
                  child: Padding(
                    padding: EdgeInsets.all(12.0),
                    child: CircularProgressIndicator(strokeWidth: 2),
                  ),
                )
              : const Icon(Icons.arrow_drop_down),
        ),
        child: widget.allowMultiple && _selectedValues.length > 1
            ? Wrap(
                spacing: 6,
                runSpacing: 4,
                children: _selectedValues.map((value) {
                  final profession = _findProfession(value);
                  return Chip(
                    label: Text(
                      profession?.getDisplayText(locale) ?? value,
                      style: const TextStyle(fontSize: 12),
                    ),
                    visualDensity: VisualDensity.compact,
                    materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
                  );
                }).toList(),
              )
            : Text(
                _displayText(locale),
                style: TextStyle(
                  color: hasSelection
                      ? Theme.of(context).textTheme.bodyLarge?.color
                      : Theme.of(context).hintColor,
                ),
              ),
      ),
    );
  }
}

class _SearchDialog extends StatefulWidget {
  final TextEditingController searchController;
  final List<ProfessionModel> initialFilteredProfessions;
  final String? selectedValue;
  final Locale locale;
  final bool useBackend;

  const _SearchDialog({
    Key? key,
    required this.searchController,
    required this.initialFilteredProfessions,
    required this.selectedValue,
    required this.locale,
    required this.useBackend,
  }) : super(key: key);

  @override
  State<_SearchDialog> createState() => _SearchDialogState();
}

class _SearchDialogState extends State<_SearchDialog> {
  late List<ProfessionModel> _filteredProfessions;

  @override
  void initState() {
    super.initState();
    _filteredProfessions = widget.initialFilteredProfessions;
    widget.searchController.addListener(_onSearchChanged);
  }

  @override
  void dispose() {
    widget.searchController.removeListener(_onSearchChanged);
    super.dispose();
  }

  void _onSearchChanged() {
    final query = widget.searchController.text;
    if (query.isEmpty) {
      setState(() {
        _filteredProfessions = widget.initialFilteredProfessions;
      });
      return;
    }

    setState(() {
      _filteredProfessions = widget.initialFilteredProfessions
          .where((p) => p.matches(query))
          .toList();
    });
  }

  @override
  Widget build(BuildContext context) {
    return Dialog(
      child: Container(
        width: double.maxFinite,
        constraints: const BoxConstraints(maxHeight: 600),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Padding(
              padding: const EdgeInsets.all(16.0),
              child: TextField(
                controller: widget.searchController,
                autofocus: true,
                decoration: InputDecoration(
                  hintText: AppLocalizations.of(context)!.searchProfession,
                  prefixIcon: const Icon(Icons.search),
                  suffixIcon: ValueListenableBuilder<TextEditingValue>(
                    valueListenable: widget.searchController,
                    builder: (context, value, child) {
                      return value.text.isNotEmpty
                          ? IconButton(
                              icon: const Icon(Icons.clear),
                              onPressed: () {
                                widget.searchController.clear();
                              },
                            )
                          : const SizedBox.shrink();
                    },
                  ),
                  border: const OutlineInputBorder(),
                ),
              ),
            ),
            const Divider(height: 1),
            Expanded(
              child: _filteredProfessions.isEmpty
                  ? Center(
                      child: Padding(
                        padding: const EdgeInsets.all(16.0),
                        child: Text(
                          AppLocalizations.of(context)!.noProfessionsFound,
                          style: TextStyle(
                            color: Colors.grey.shade600,
                          ),
                        ),
                      ),
                    )
                  : ListView.builder(
                      shrinkWrap: true,
                      itemCount: _filteredProfessions.length,
                      itemBuilder: (context, index) {
                        final profession = _filteredProfessions[index];
                        final isSelected =
                            profession.english == widget.selectedValue;

                        return ListTile(
                          title: Text(
                            profession.getDisplayText(widget.locale),
                            style: TextStyle(
                              fontWeight: isSelected
                                  ? FontWeight.bold
                                  : FontWeight.normal,
                            ),
                          ),
                          selected: isSelected,
                          selectedTileColor: Theme.of(context)
                              .colorScheme
                              .primary
                              .withOpacity(0.1),
                          onTap: () {
                            Navigator.pop(context, profession.english);
                          },
                          trailing: isSelected
                              ? Icon(
                                  Icons.check,
                                  color: Theme.of(context).colorScheme.primary,
                                )
                              : null,
                        );
                      },
                    ),
            ),
          ],
        ),
      ),
    );
  }
}

class _MultiSearchDialog extends StatefulWidget {
  final TextEditingController searchController;
  final List<ProfessionModel> initialFilteredProfessions;
  final List<String> selectedValues;
  final Locale locale;

  const _MultiSearchDialog({
    Key? key,
    required this.searchController,
    required this.initialFilteredProfessions,
    required this.selectedValues,
    required this.locale,
  }) : super(key: key);

  @override
  State<_MultiSearchDialog> createState() => _MultiSearchDialogState();
}

class _MultiSearchDialogState extends State<_MultiSearchDialog> {
  late List<ProfessionModel> _filteredProfessions;
  late Set<String> _selected;

  @override
  void initState() {
    super.initState();
    _filteredProfessions = widget.initialFilteredProfessions;
    _selected = Set<String>.from(widget.selectedValues);
    widget.searchController.addListener(_onSearchChanged);
  }

  @override
  void dispose() {
    widget.searchController.removeListener(_onSearchChanged);
    super.dispose();
  }

  void _onSearchChanged() {
    final query = widget.searchController.text;
    if (query.isEmpty) {
      setState(() {
        _filteredProfessions = widget.initialFilteredProfessions;
      });
      return;
    }

    setState(() {
      _filteredProfessions = widget.initialFilteredProfessions
          .where((p) => p.matches(query))
          .toList();
    });
  }

  void _toggle(String english) {
    setState(() {
      if (_selected.contains(english)) {
        _selected.remove(english);
      } else {
        _selected.add(english);
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;

    return Dialog(
      child: Container(
        width: double.maxFinite,
        constraints: const BoxConstraints(maxHeight: 600),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Padding(
              padding: const EdgeInsets.all(16.0),
              child: TextField(
                controller: widget.searchController,
                autofocus: true,
                decoration: InputDecoration(
                  hintText: l10n.searchProfession,
                  prefixIcon: const Icon(Icons.search),
                  suffixIcon: ValueListenableBuilder<TextEditingValue>(
                    valueListenable: widget.searchController,
                    builder: (context, value, child) {
                      return value.text.isNotEmpty
                          ? IconButton(
                              icon: const Icon(Icons.clear),
                              onPressed: widget.searchController.clear,
                            )
                          : const SizedBox.shrink();
                    },
                  ),
                  border: const OutlineInputBorder(),
                ),
              ),
            ),
            const Divider(height: 1),
            Expanded(
              child: _filteredProfessions.isEmpty
                  ? Center(
                      child: Padding(
                        padding: const EdgeInsets.all(16.0),
                        child: Text(
                          l10n.noProfessionsFound,
                          style: TextStyle(color: Colors.grey.shade600),
                        ),
                      ),
                    )
                  : ListView.builder(
                      shrinkWrap: true,
                      itemCount: _filteredProfessions.length,
                      itemBuilder: (context, index) {
                        final profession = _filteredProfessions[index];
                        final isSelected =
                            _selected.contains(profession.english);

                        return CheckboxListTile(
                          title: Text(
                            profession.getDisplayText(widget.locale),
                          ),
                          value: isSelected,
                          onChanged: (_) => _toggle(profession.english),
                        );
                      },
                    ),
            ),
            const Divider(height: 1),
            Padding(
              padding: const EdgeInsets.all(12.0),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.end,
                children: [
                  TextButton(
                    onPressed: () => Navigator.pop(context),
                    child: Text(l10n.cancel),
                  ),
                  const SizedBox(width: 8),
                  FilledButton(
                    onPressed: () =>
                        Navigator.pop(context, _selected.toList()),
                    child: Text(l10n.ok),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
