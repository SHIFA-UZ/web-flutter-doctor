// lib/features/profile/presentation/searchable_timezone_dropdown.dart
// Practice timezone selector for onboarding (Shifa Global Time Architecture v2).

import 'package:flutter/material.dart';
import 'package:shifa_doc_app_v1/core/services/timezone_service.dart';

class SearchableTimezoneDropdown extends StatefulWidget {
  final String? value;
  final ValueChanged<String?>? onChanged;
  final String? hintText;
  final String? labelText;

  const SearchableTimezoneDropdown({
    super.key,
    this.value,
    this.onChanged,
    this.hintText,
    this.labelText,
  });

  @override
  State<SearchableTimezoneDropdown> createState() => _SearchableTimezoneDropdownState();
}

class _SearchableTimezoneDropdownState extends State<SearchableTimezoneDropdown> {
  final TextEditingController _searchController = TextEditingController();
  List<String> _allZones = [];

  @override
  void initState() {
    super.initState();
    _allZones = List<String>.from(commonIanaTimeZones);
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  void _ensureZoneInList(String? zone) {
    if (zone == null || zone.isEmpty) return;
    if (!_allZones.contains(zone)) {
      _allZones = [zone, ...commonIanaTimeZones];
    }
  }

  Future<void> _showSearchDialog() async {
    _searchController.clear();
    _ensureZoneInList(widget.value);

    final result = await showDialog<String>(
      context: context,
      builder: (ctx) => _TimezoneSearchDialog(
        searchController: _searchController,
        zones: _allZones,
        selectedValue: widget.value,
        hintText: widget.hintText ?? 'Search timezone',
      ),
    );

    if (result != null && widget.onChanged != null) {
      widget.onChanged!(result);
    }
  }

  @override
  Widget build(BuildContext context) {
    _ensureZoneInList(widget.value);
    return InkWell(
      onTap: _showSearchDialog,
      child: InputDecorator(
        decoration: InputDecoration(
          hintText: widget.hintText ?? 'Practice timezone',
          labelText: widget.labelText,
          border: const OutlineInputBorder(),
          suffixIcon: const Icon(Icons.arrow_drop_down),
        ),
        child: Text(
          widget.value ?? (widget.hintText ?? 'Select timezone'),
          style: TextStyle(
            color: widget.value != null
                ? Theme.of(context).textTheme.bodyLarge?.color
                : Theme.of(context).hintColor,
          ),
        ),
      ),
    );
  }
}

class _TimezoneSearchDialog extends StatefulWidget {
  final TextEditingController searchController;
  final List<String> zones;
  final String? selectedValue;
  final String hintText;

  const _TimezoneSearchDialog({
    required this.searchController,
    required this.zones,
    this.selectedValue,
    required this.hintText,
  });

  @override
  State<_TimezoneSearchDialog> createState() => _TimezoneSearchDialogState();
}

class _TimezoneSearchDialogState extends State<_TimezoneSearchDialog> {
  late List<String> _filtered;

  @override
  void initState() {
    super.initState();
    _filtered = List.from(widget.zones);
    widget.searchController.addListener(_onSearchChanged);
  }

  @override
  void dispose() {
    widget.searchController.removeListener(_onSearchChanged);
    super.dispose();
  }

  void _onSearchChanged() {
    setState(() {
      final q = widget.searchController.text.trim().toLowerCase();
      _filtered = q.isEmpty
          ? List.from(widget.zones)
          : widget.zones.where((z) => z.toLowerCase().contains(q)).toList();
    });
  }

  @override
  Widget build(BuildContext context) {
    return Dialog(
      child: Container(
        width: double.maxFinite,
        constraints: const BoxConstraints(maxHeight: 500),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Padding(
              padding: const EdgeInsets.all(16.0),
              child: TextField(
                controller: widget.searchController,
                autofocus: true,
                decoration: InputDecoration(
                  hintText: widget.hintText,
                  prefixIcon: const Icon(Icons.search),
                  border: const OutlineInputBorder(),
                ),
              ),
            ),
            const Divider(height: 1),
            Expanded(
              child: _filtered.isEmpty
                  ? Center(
                      child: Text(
                        'No timezone found',
                        style: TextStyle(color: Colors.grey.shade600),
                      ),
                    )
                  : ListView.builder(
                      shrinkWrap: true,
                      itemCount: _filtered.length,
                      itemBuilder: (context, index) {
                        final zone = _filtered[index];
                        final isSelected = zone == widget.selectedValue;
                        return ListTile(
                          title: Text(zone),
                          selected: isSelected,
                          selectedTileColor: Theme.of(context).colorScheme.primary.withOpacity(0.1),
                          onTap: () => Navigator.of(context).pop(zone),
                          trailing: isSelected
                              ? Icon(Icons.check, color: Theme.of(context).colorScheme.primary)
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
