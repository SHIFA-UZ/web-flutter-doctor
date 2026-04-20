import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/theme/app_colors.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';
import 'package:shifa_doc_app_v1/state/locations/doctor_location_actions.dart';
import 'package:shifa_doc_app_v1/state/locations/doctor_location_models.dart';

/// Screen that lets a doctor manage their practice locations (list / add /
/// edit / delete). Each location can be used as the target for a weekly
/// schedule and appointments.
class DoctorLocationsScreen extends ConsumerStatefulWidget {
  const DoctorLocationsScreen({Key? key}) : super(key: key);

  @override
  ConsumerState<DoctorLocationsScreen> createState() =>
      _DoctorLocationsScreenState();
}

class _DoctorLocationsScreenState extends ConsumerState<DoctorLocationsScreen> {
  bool _loading = true;
  List<DoctorLocationDto> _locations = const [];

  @override
  void initState() {
    super.initState();
    Future.microtask(_load);
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    try {
      final list = await fetchDoctorLocations(ref);
      if (!mounted) return;
      setState(() => _locations = list);
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(e.toString().replaceFirst('Exception: ', ''))),
      );
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _openEditor({DoctorLocationDto? existing}) async {
    final updated = await showDialog<DoctorLocationDto>(
      context: context,
      builder: (ctx) => _LocationEditorDialog(existing: existing),
    );
    if (updated == null) return;
    try {
      if (existing?.id == null) {
        await createDoctorLocation(ref, updated);
      } else {
        await updateDoctorLocation(ref, existing!.id!, updated);
      }
      if (!mounted) return;
      await _load();
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(e.toString().replaceFirst('Exception: ', ''))),
      );
    }
  }

  Future<void> _deleteLocation(DoctorLocationDto loc) async {
    final l10n = AppLocalizations.of(context)!;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(l10n.translate('deleteLocation') ?? 'Delete location'),
        content: Text(
          (l10n.translate('deleteLocationConfirm') ??
                  'Delete "{label}"? Schedule rules and appointments at this location must be removed first.')
              .replaceFirst('{label}', loc.label),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: Text(l10n.translate('cancel') ?? 'Cancel'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            style: TextButton.styleFrom(foregroundColor: AppColors.destructiveRed),
            child: Text(l10n.translate('delete') ?? 'Delete'),
          ),
        ],
      ),
    );
    if (confirmed != true) return;
    try {
      await deleteDoctorLocation(ref, loc.id!);
      if (!mounted) return;
      await _load();
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(e.toString().replaceFirst('Exception: ', ''))),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.translate('manageLocations') ?? 'Manage locations'),
        foregroundColor: Colors.black,
        backgroundColor: Colors.transparent,
        elevation: 0,
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _openEditor(),
        icon: const Icon(Icons.add),
        label: Text(l10n.translate('addLocation') ?? 'Add location'),
      ),
      body: SafeArea(
        child: _loading
            ? const Center(child: CircularProgressIndicator())
            : _locations.isEmpty
                ? Center(
                    child: Padding(
                      padding: const EdgeInsets.all(24),
                      child: Text(
                        l10n.translate('noLocationsYet') ??
                            'No locations yet. Tap “Add location” to create your first one.',
                        textAlign: TextAlign.center,
                        style: TextStyle(color: Colors.grey.shade700),
                      ),
                    ),
                  )
                : RefreshIndicator(
                    onRefresh: _load,
                    child: ListView.separated(
                      padding: const EdgeInsets.all(16),
                      itemCount: _locations.length,
                      separatorBuilder: (_, __) => const SizedBox(height: 12),
                      itemBuilder: (context, i) {
                        final l = _locations[i];
                        return _LocationCard(
                          location: l,
                          onEdit: () => _openEditor(existing: l),
                          onDelete: () => _deleteLocation(l),
                        );
                      },
                    ),
                  ),
      ),
    );
  }
}

class _LocationCard extends StatelessWidget {
  const _LocationCard({
    required this.location,
    required this.onEdit,
    required this.onDelete,
  });

  final DoctorLocationDto location;
  final VoidCallback onEdit;
  final VoidCallback onDelete;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final subtitleLines = <String>[];
    if (location.clinic != null && location.clinic!.isNotEmpty) {
      subtitleLines.add(location.clinic!);
    }
    if (location.address != null && location.address!.isNotEmpty) {
      subtitleLines.add(location.address!);
    }
    final cityParts = [
      location.locationCity,
      location.locationRegion,
      location.locationCountry,
    ].where((s) => s != null && s.isNotEmpty).cast<String>().toList();
    if (cityParts.isNotEmpty) subtitleLines.add(cityParts.join(', '));

    return Card(
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: BorderSide(color: Colors.grey.shade300),
      ),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Row(
                    children: [
                      Flexible(
                        child: Text(
                          location.label,
                          style: const TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.w600,
                          ),
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      if (location.isPrimary) ...[
                        const SizedBox(width: 8),
                        Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 8,
                            vertical: 2,
                          ),
                          decoration: BoxDecoration(
                            color: Colors.amber.shade100,
                            borderRadius: BorderRadius.circular(12),
                          ),
                          child: Text(
                            l10n.translate('primary') ?? 'Primary',
                            style: const TextStyle(fontSize: 11),
                          ),
                        ),
                      ],
                    ],
                  ),
                ),
                IconButton(
                  icon: const Icon(Icons.edit_outlined),
                  onPressed: onEdit,
                ),
                IconButton(
                  icon: const Icon(
                    Icons.delete_outline,
                    color: AppColors.destructiveRed,
                  ),
                  onPressed: onDelete,
                ),
              ],
            ),
            if (subtitleLines.isNotEmpty) ...[
              const SizedBox(height: 4),
              ...subtitleLines.map(
                (line) => Padding(
                  padding: const EdgeInsets.only(top: 2),
                  child: Text(
                    line,
                    style: TextStyle(
                      fontSize: 13,
                      color: Colors.grey.shade700,
                    ),
                  ),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class _LocationEditorDialog extends StatefulWidget {
  const _LocationEditorDialog({this.existing});
  final DoctorLocationDto? existing;

  @override
  State<_LocationEditorDialog> createState() => _LocationEditorDialogState();
}

class _LocationEditorDialogState extends State<_LocationEditorDialog> {
  late final TextEditingController _label;
  late final TextEditingController _clinic;
  late final TextEditingController _address;
  late final TextEditingController _city;
  late final TextEditingController _region;
  late final TextEditingController _country;
  late bool _isPrimary;

  @override
  void initState() {
    super.initState();
    final e = widget.existing;
    _label = TextEditingController(text: e?.label ?? '');
    _clinic = TextEditingController(text: e?.clinic ?? '');
    _address = TextEditingController(text: e?.address ?? '');
    _city = TextEditingController(text: e?.locationCity ?? '');
    _region = TextEditingController(text: e?.locationRegion ?? '');
    _country = TextEditingController(text: e?.locationCountry ?? '');
    _isPrimary = e?.isPrimary ?? false;
  }

  @override
  void dispose() {
    _label.dispose();
    _clinic.dispose();
    _address.dispose();
    _city.dispose();
    _region.dispose();
    _country.dispose();
    super.dispose();
  }

  void _submit() {
    final label = _label.text.trim();
    if (label.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(
          AppLocalizations.of(context)!.translate('labelRequired') ??
              'Label is required',
        )),
      );
      return;
    }
    final dto = DoctorLocationDto(
      id: widget.existing?.id,
      label: label,
      clinic: _clinic.text.trim().isEmpty ? null : _clinic.text.trim(),
      address: _address.text.trim().isEmpty ? null : _address.text.trim(),
      locationCity: _city.text.trim().isEmpty ? null : _city.text.trim(),
      locationRegion: _region.text.trim().isEmpty ? null : _region.text.trim(),
      locationCountry:
          _country.text.trim().isEmpty ? null : _country.text.trim(),
      isPrimary: _isPrimary,
    );
    Navigator.pop(context, dto);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final isNew = widget.existing == null;
    return AlertDialog(
      title: Text(isNew
          ? (l10n.translate('addLocation') ?? 'Add location')
          : (l10n.translate('editLocation') ?? 'Edit location')),
      content: SingleChildScrollView(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 420),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: _label,
                decoration: InputDecoration(
                  labelText: l10n.translate('label') ?? 'Label',
                  hintText: 'e.g. Main Clinic',
                ),
              ),
              const SizedBox(height: 8),
              TextField(
                controller: _clinic,
                decoration: InputDecoration(
                  labelText: l10n.translate('clinic') ?? 'Clinic name',
                ),
              ),
              const SizedBox(height: 8),
              TextField(
                controller: _address,
                maxLines: 2,
                decoration: InputDecoration(
                  labelText: l10n.translate('address') ?? 'Address',
                ),
              ),
              const SizedBox(height: 8),
              Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: _city,
                      decoration: InputDecoration(
                        labelText: l10n.translate('city') ?? 'City',
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: TextField(
                      controller: _region,
                      decoration: InputDecoration(
                        labelText: l10n.translate('region') ?? 'Region',
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              TextField(
                controller: _country,
                decoration: InputDecoration(
                  labelText: l10n.translate('country') ?? 'Country',
                ),
              ),
              const SizedBox(height: 8),
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                title: Text(l10n.translate('setAsPrimary') ?? 'Set as primary'),
                value: _isPrimary,
                onChanged: (v) => setState(() => _isPrimary = v),
              ),
            ],
          ),
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: Text(l10n.translate('cancel') ?? 'Cancel'),
        ),
        ShifaPrimaryButton(
          label: l10n.translate('save') ?? 'Save',
          onPressed: _submit,
        ),
      ],
    );
  }
}
