import 'package:flutter/material.dart';
import 'package:shifa_doc_app_v1/core/models/profession_model.dart';
import 'package:shifa_doc_app_v1/core/providers/language_provider.dart';
import 'package:shifa_doc_app_v1/core/services/profession_service.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class SearchableProfessionDropdown extends ConsumerStatefulWidget {
  final String? value;
  final ValueChanged<String?>? onChanged;
  final String? hintText;
  final String? labelText;
  final bool useBackend;

  const SearchableProfessionDropdown({
    Key? key,
    this.value,
    this.onChanged,
    this.hintText,
    this.labelText,
    this.useBackend = true,
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
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _selectedValue = widget.value;
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
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  Future<void> _updateFilteredList(String query) async {
    if (query.isEmpty) {
      // Reload all professions
      await _loadProfessions();
      return;
    }
    
    setState(() {
      // Search in current list
      _filteredProfessions = _filteredProfessions.where((p) => p.matches(query)).toList();
    });
    
    // Also try backend search if enabled
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

  Future<void> _showSearchDialog() async {
    _searchController.clear();
    _updateFilteredList('');

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

  @override
  Widget build(BuildContext context) {
    final locale = ref.watch(languageProvider).locale;
    
    // Find selected profession in current list
    ProfessionModel? selectedProfession;
    if (_selectedValue != null) {
      try {
        selectedProfession = _filteredProfessions.firstWhere(
          (p) => p.english == _selectedValue,
        );
      } catch (e) {
        // Try to find in all professions as fallback
        selectedProfession = ProfessionData.findByEnglish(_selectedValue!);
      }
    }

    return InkWell(
      onTap: _isLoading ? null : _showSearchDialog,
      child: InputDecorator(
        decoration: InputDecoration(
          hintText: widget.hintText ?? AppLocalizations.of(context)!.selectProfession,
          labelText: widget.labelText ?? AppLocalizations.of(context)!.profession,
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
        child: Text(
          selectedProfession != null
              ? selectedProfession.getDisplayText(locale)
              : widget.hintText ?? 'Select Profession',
          style: TextStyle(
            color: selectedProfession != null
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
    
    // Filter locally from initial list
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
            // Search bar
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
            // List of professions
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
                          selectedTileColor:
                              Theme.of(context).colorScheme.primary.withOpacity(0.1),
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
