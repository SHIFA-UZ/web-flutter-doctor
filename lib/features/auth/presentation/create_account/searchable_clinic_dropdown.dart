import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/models/clinic_option_model.dart';
import 'package:shifa_doc_app_v1/core/services/clinic_service.dart';

class SearchableClinicDropdown extends ConsumerStatefulWidget {
  final int? value;
  final ValueChanged<ClinicOption?>? onChanged;
  final String? hintText;
  final String? labelText;

  const SearchableClinicDropdown({
    super.key,
    this.value,
    this.onChanged,
    this.hintText,
    this.labelText,
  });

  @override
  ConsumerState<SearchableClinicDropdown> createState() =>
      _SearchableClinicDropdownState();
}

class _SearchableClinicDropdownState extends ConsumerState<SearchableClinicDropdown> {
  final TextEditingController _searchController = TextEditingController();
  List<ClinicOption> _clinics = [];
  int? _selectedId;
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _selectedId = widget.value;
    _loadClinics();
  }

  @override
  void didUpdateWidget(SearchableClinicDropdown oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.value != widget.value) {
      _selectedId = widget.value;
    }
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  Future<void> _loadClinics({String? search}) async {
    setState(() => _isLoading = true);
    try {
      final clinics = await ClinicService.getClinics(search: search, ref: ref);
      if (!mounted) return;
      setState(() {
        _clinics = clinics;
        _isLoading = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _clinics = const [];
        _isLoading = false;
      });
    }
  }

  ClinicOption? get _selectedClinic {
    if (_selectedId == null) return null;
    for (final clinic in _clinics) {
      if (clinic.id == _selectedId) return clinic;
    }
    return null;
  }

  Future<void> _showSearchDialog() async {
    _searchController.clear();
    var filtered = List<ClinicOption>.from(_clinics);

    final result = await showDialog<int>(
      context: context,
      builder: (dialogContext) {
        return StatefulBuilder(
          builder: (context, setDialogState) {
            Future<void> filter(String query) async {
              if (query.trim().isEmpty) {
                setDialogState(() => filtered = List<ClinicOption>.from(_clinics));
                return;
              }
              setDialogState(() {
                filtered = _clinics.where((c) => c.matches(query)).toList();
              });
              final remote = await ClinicService.getClinics(search: query, ref: ref);
              if (!context.mounted) return;
              setDialogState(() => filtered = remote);
            }

            return AlertDialog(
              title: Text(widget.labelText ?? AppLocalizations.of(context)!.clinic),
              content: SizedBox(
                width: double.maxFinite,
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    TextField(
                      controller: _searchController,
                      decoration: InputDecoration(
                        hintText: AppLocalizations.of(context)!.search,
                        prefixIcon: const Icon(Icons.search),
                      ),
                      onChanged: filter,
                    ),
                    const SizedBox(height: 12),
                    Flexible(
                      child: filtered.isEmpty
                          ? Center(
                              child: Text(
                                AppLocalizations.of(context)!.translate('notFound'),
                              ),
                            )
                          : ListView.builder(
                              shrinkWrap: true,
                              itemCount: filtered.length,
                              itemBuilder: (context, index) {
                                final clinic = filtered[index];
                                final selected = clinic.id == _selectedId;
                                return ListTile(
                                  title: Text(clinic.name),
                                  selected: selected,
                                  trailing: selected ? const Icon(Icons.check) : null,
                                  onTap: () => Navigator.pop(context, clinic.id),
                                );
                              },
                            ),
                    ),
                  ],
                ),
              ),
              actions: [
                TextButton(
                  onPressed: () => Navigator.pop(context),
                  child: Text(AppLocalizations.of(context)!.cancel),
                ),
              ],
            );
          },
        );
      },
    );

    if (result != null) {
      ClinicOption? selected;
      for (final clinic in _clinics) {
        if (clinic.id == result) {
          selected = clinic;
          break;
        }
      }
      if (selected == null) {
        final remote = await ClinicService.getClinics(ref: ref);
        for (final clinic in remote) {
          if (clinic.id == result) {
            selected = clinic;
            break;
          }
        }
      }
      if (selected != null && !_clinics.any((c) => c.id == selected!.id)) {
        setState(() => _clinics = [..._clinics, selected!]);
      }
      setState(() => _selectedId = result);
      widget.onChanged?.call(selected);
    }
  }

  @override
  Widget build(BuildContext context) {
    final selected = _selectedClinic;

    return InkWell(
      onTap: _isLoading ? null : _showSearchDialog,
      child: InputDecorator(
        decoration: InputDecoration(
          hintText: widget.hintText ?? AppLocalizations.of(context)!.clinic,
          labelText: widget.labelText ?? AppLocalizations.of(context)!.clinic,
          border: const OutlineInputBorder(),
          suffixIcon: _isLoading
              ? const SizedBox(
                  width: 20,
                  height: 20,
                  child: Padding(
                    padding: EdgeInsets.all(12),
                    child: CircularProgressIndicator(strokeWidth: 2),
                  ),
                )
              : const Icon(Icons.arrow_drop_down),
        ),
        child: Text(
          selected?.name ??
              (_isLoading
                  ? AppLocalizations.of(context)!.detecting
                  : AppLocalizations.of(context)!.clinic),
          style: TextStyle(
            color: selected == null ? Colors.grey.shade600 : Colors.black,
          ),
        ),
      ),
    );
  }
}
