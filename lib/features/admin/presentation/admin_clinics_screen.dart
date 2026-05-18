import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/theme/app_colors.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';
import 'package:shifa_doc_app_v1/features/admin/domain/admin_models.dart';
import 'package:shifa_doc_app_v1/state/admin/admin_provider_params.dart';
import 'package:shifa_doc_app_v1/state/admin/admin_providers.dart';

class AdminClinicsScreen extends ConsumerStatefulWidget {
  const AdminClinicsScreen({super.key});

  @override
  ConsumerState<AdminClinicsScreen> createState() => _AdminClinicsScreenState();
}

class _AdminClinicsScreenState extends ConsumerState<AdminClinicsScreen> {
  int _listPage = 0;
  static const _pageSize = 50;
  int? _selectedClinicId;

  Future<void> _refreshLists() async {
    ref.invalidate(adminClinicsProvider(ClinicsListParams(page: _listPage, size: _pageSize)));
    final id = _selectedClinicId;
    if (id != null) {
      ref.invalidate(adminClinicDetailProvider(id));
    }
  }

  Future<void> _showCreateEditDialog({AdminClinicDetail? existing}) async {
    final l10n = AppLocalizations.of(context)!;
    final nameCtrl = TextEditingController(text: existing?.name ?? '');
    final phoneCtrl = TextEditingController(text: existing?.phone ?? '');
    final emailCtrl = TextEditingController(text: existing?.email ?? '');
    final addrCtrl = TextEditingController(text: existing?.address ?? '');
    final tzCtrl = TextEditingController(text: existing?.timeZone ?? 'Asia/Tashkent');

    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(
          existing == null
              ? l10n.translate('adminClinicCreateTitle')
              : l10n.translate('adminClinicEditTitle'),
        ),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: nameCtrl,
                decoration: InputDecoration(labelText: l10n.translate('adminClinicNameLabel')),
              ),
              TextField(
                controller: tzCtrl,
                decoration: InputDecoration(labelText: l10n.translate('adminClinicTimezoneLabel')),
              ),
              TextField(
                controller: phoneCtrl,
                decoration: InputDecoration(labelText: l10n.translate('adminClinicPhoneLabel')),
              ),
              TextField(
                controller: emailCtrl,
                decoration: InputDecoration(labelText: l10n.translate('adminClinicEmailLabel')),
              ),
              TextField(
                controller: addrCtrl,
                decoration: InputDecoration(labelText: l10n.translate('adminClinicAddressLabel')),
                maxLines: 2,
              ),
            ],
          ),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: Text(l10n.cancel)),
          TextButton(onPressed: () => Navigator.pop(ctx, true), child: Text(l10n.save)),
        ],
      ),
    );
    if (ok != true || !mounted) return;

    try {
      final actions = ref.read(adminActionsProvider);
      if (existing == null) {
        await actions.createClinic(
          name: nameCtrl.text.trim(),
          phone: phoneCtrl.text.trim().isEmpty ? null : phoneCtrl.text.trim(),
          email: emailCtrl.text.trim().isEmpty ? null : emailCtrl.text.trim(),
          address: addrCtrl.text.trim().isEmpty ? null : addrCtrl.text.trim(),
          timeZone: tzCtrl.text.trim().isEmpty ? null : tzCtrl.text.trim(),
        );
      } else {
        await actions.updateClinic(
          clinicId: existing.id,
          name: nameCtrl.text.trim(),
          phone: phoneCtrl.text.trim().isEmpty ? null : phoneCtrl.text.trim(),
          email: emailCtrl.text.trim().isEmpty ? null : emailCtrl.text.trim(),
          address: addrCtrl.text.trim().isEmpty ? null : addrCtrl.text.trim(),
          timeZone: tzCtrl.text.trim().isEmpty ? null : tzCtrl.text.trim(),
        );
      }
      if (!mounted) return;
      await _refreshLists();
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(l10n.translate('saved'))));
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('${AppLocalizations.of(context)!.error}: $e'), backgroundColor: AppColors.destructiveRed),
      );
    }
  }

  Future<void> _pickAndAssignDoctor(int clinicId) async {
    final l10n = AppLocalizations.of(context)!;
    final usersSnap = await ref.read(
      adminUsersProvider(UsersProviderParams(role: 'DOCTOR', enabled: null, search: null, page: 0, size: 100)).future,
    );
    final rawList = usersSnap['content'];
    final docUsers = <AdminUser>[
      ...(rawList is List ? rawList : const []).whereType<AdminUser>(),
    ];
    final withProfile = docUsers.where((u) => (u.doctorProfileId() ?? 0) > 0).toList();

    if (!mounted) return;
    if (withProfile.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(l10n.translate('adminClinicNoDoctorsDropdown'))),
      );
      return;
    }

    int? chosenPid;
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) {
        return StatefulBuilder(
          builder: (ctx, setLocal) => AlertDialog(
            title: Text(l10n.translate('adminClinicAssignDoctor')),
            content: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text(l10n.translate('doctor')),
                const SizedBox(height: 8),
                DropdownButton<int>(
                  isExpanded: true,
                  value: chosenPid,
                  items: withProfile
                      .map(
                        (u) => DropdownMenuItem<int>(
                          value: u.doctorProfileId()!,
                          child: Text('${u.displayName} (#${u.doctorProfileId()})'),
                        ),
                      )
                      .toList(),
                  onChanged: (v) => setLocal(() => chosenPid = v),
                ),
              ],
            ),
            actions: [
              TextButton(onPressed: () => Navigator.pop(ctx, false), child: Text(l10n.cancel)),
              TextButton(onPressed: () => Navigator.pop(ctx, true), child: Text(l10n.confirm)),
            ],
          ),
        );
      },
    );
    if (ok != true || chosenPid == null || !mounted) return;
    final pid = chosenPid!;
    try {
      await ref.read(adminActionsProvider).assignDoctorToClinic(clinicId: clinicId, doctorProfileId: pid);
      if (!mounted) return;
      await _refreshLists();
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(l10n.translate('saved'))));
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('${AppLocalizations.of(context)!.error}: $e'), backgroundColor: AppColors.destructiveRed),
      );
    }
  }

  Future<void> _confirmRemoveDoctor(int clinicId, int doctorProfileId) async {
    final l10n = AppLocalizations.of(context)!;
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(l10n.translate('adminClinicRemoveDoctor')),
        content: Text(l10n.translate('adminConfirmRemoveDoctor')),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: Text(l10n.cancel)),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: Text(l10n.delete, style: const TextStyle(color: AppColors.destructiveRed)),
          ),
        ],
      ),
    );
    if (ok != true || !mounted) return;
    try {
      await ref.read(adminActionsProvider).removeDoctorFromClinic(clinicId: clinicId, doctorProfileId: doctorProfileId);
      if (!mounted) return;
      await _refreshLists();
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('${AppLocalizations.of(context)!.error}: $e'), backgroundColor: AppColors.destructiveRed),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final listParams = ClinicsListParams(page: _listPage, size: _pageSize);
    final clinicsAsync = ref.watch(adminClinicsProvider(listParams));

    return Scaffold(
      backgroundColor: Colors.grey.shade50,
      appBar: AppBar(
        title: Text(l10n.translate('adminNavClinics')),
        backgroundColor: Colors.white,
        elevation: 0,
        foregroundColor: Colors.black87,
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: _refreshLists,
            tooltip: l10n.refresh,
          ),
          Padding(
            padding: const EdgeInsets.only(right: 12),
            child: ShifaPrimaryButton(
              onPressed: () => _showCreateEditDialog(),
              icon: Icons.add,
              label: l10n.translate('adminClinicCreateTitle'),
            ),
          ),
        ],
      ),
      body: clinicsAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(child: Text('${l10n.error}: $e')),
        data: (data) {
          final items = data['content'] as List<AdminClinicSummary>;
          final totalPages = (data['totalPages'] as int).clamp(1, 999999);
          final selId = _selectedClinicId;

          Widget detailPane = Center(
            child: Text(l10n.translate('adminClinicSelectPrompt'), style: TextStyle(color: Colors.grey.shade600)),
          );
          if (selId != null) {
            final det = ref.watch(adminClinicDetailProvider(selId));
            detailPane = det.when(
              loading: () => const Center(child: CircularProgressIndicator()),
              error: (e, _) => Center(child: Text('$e')),
              data: (c) => _ClinicDetailView(
                clinic: c,
                onEdit: () => _showCreateEditDialog(existing: c),
                onAssignDoctor: () => _pickAndAssignDoctor(c.id),
                onRemoveDoctor: (pid) => _confirmRemoveDoctor(c.id, pid),
              ),
            );
          }

          return Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              SizedBox(
                width: 360,
                child: Column(
                  children: [
                    Expanded(
                      child: ListView.separated(
                        itemCount: items.length,
                        separatorBuilder: (_, __) => const Divider(height: 1),
                        itemBuilder: (ctx, i) {
                          final c = items[i];
                          final sel = selId == c.id;
                          return Material(
                            color: sel ? Colors.blue.shade50 : Colors.white,
                            child: ListTile(
                              title: Text(c.name, overflow: TextOverflow.ellipsis),
                              subtitle: Text('${l10n.translate('adminClinicDoctorCount')}: ${c.doctorCount}'),
                              onTap: () => setState(() => _selectedClinicId = c.id),
                              selected: sel,
                            ),
                          );
                        },
                      ),
                    ),
                    if (totalPages > 1)
                      Padding(
                        padding: const EdgeInsets.all(8),
                        child: Row(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            IconButton(
                              icon: const Icon(Icons.chevron_left),
                              onPressed: _listPage <= 0
                                  ? null
                                  : () {
                                      setState(() => _listPage--);
                                    },
                            ),
                            Text('${_listPage + 1} / $totalPages'),
                            IconButton(
                              icon: const Icon(Icons.chevron_right),
                              onPressed: _listPage >= totalPages - 1
                                  ? null
                                  : () {
                                      setState(() => _listPage++);
                                    },
                            ),
                          ],
                        ),
                      ),
                  ],
                ),
              ),
              const VerticalDivider(width: 1),
              Expanded(child: detailPane),
            ],
          );
        },
      ),
    );
  }
}

class _ClinicDetailView extends StatelessWidget {
  final AdminClinicDetail clinic;
  final VoidCallback onEdit;
  final VoidCallback onAssignDoctor;
  final void Function(int doctorProfileId) onRemoveDoctor;

  const _ClinicDetailView({
    required this.clinic,
    required this.onEdit,
    required this.onAssignDoctor,
    required this.onRemoveDoctor,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return SingleChildScrollView(
      padding: const EdgeInsets.all(24),
      child: Align(
        alignment: Alignment.topLeft,
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 640),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(child: Text(clinic.name, style: Theme.of(context).textTheme.headlineSmall)),
                  ShifaSecondaryButton(onPressed: onEdit, icon: Icons.edit_outlined, label: l10n.edit),
                  const SizedBox(width: 8),
                  ShifaPrimaryButton(onPressed: onAssignDoctor, icon: Icons.person_add_alt_1, label: l10n.translate('adminClinicAssignDoctor')),
                ],
              ),
              const SizedBox(height: 8),
              Text('${l10n.translate('adminClinicTimezoneLabel')}: ${clinic.timeZone}'),
              if (clinic.phone != null && clinic.phone!.isNotEmpty)
                Text('${l10n.translate('adminClinicPhoneLabel')}: ${clinic.phone}'),
              if (clinic.email != null && clinic.email!.isNotEmpty)
                Text('${l10n.translate('adminClinicEmailLabel')}: ${clinic.email}'),
              if (clinic.address != null && clinic.address!.isNotEmpty)
                Padding(
                  padding: const EdgeInsets.only(top: 4),
                  child: Text('${l10n.translate('adminClinicAddressLabel')}: ${clinic.address}'),
                ),
              const SizedBox(height: 24),
              Text(l10n.translate('adminClinicDoctorsHeading'), style: Theme.of(context).textTheme.titleMedium),
              const SizedBox(height: 8),
              if (clinic.doctors.isEmpty)
                Text(l10n.translate('adminClinicNoDoctors'))
              else
                ...clinic.doctors.map(
                  (d) => Card(
                    child: ListTile(
                      title: Text(d.displayName),
                      subtitle: Text('doctorProfileId=${d.doctorProfileId} · userId=${d.userId} · ${d.membershipRole}'),
                      trailing: IconButton(
                        icon: const Icon(Icons.remove_circle_outline, color: AppColors.destructiveRed),
                        tooltip: l10n.translate('adminClinicRemoveDoctor'),
                        onPressed: () => onRemoveDoctor(d.doctorProfileId),
                      ),
                    ),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}
