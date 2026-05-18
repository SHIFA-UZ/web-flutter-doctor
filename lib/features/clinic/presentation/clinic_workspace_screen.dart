// lib/features/clinic/presentation/clinic_workspace_screen.dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/theme/app_colors.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';
import 'package:shifa_doc_app_v1/state/calendar/calendar_controller.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_models.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_providers.dart';
import 'package:shifa_doc_app_v1/state/shell/shell_controller.dart';

class ClinicWorkspaceScreen extends ConsumerStatefulWidget {
  const ClinicWorkspaceScreen({super.key});

  @override
  ConsumerState<ClinicWorkspaceScreen> createState() => _ClinicWorkspaceScreenState();
}

class _ClinicWorkspaceScreenState extends ConsumerState<ClinicWorkspaceScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 8, vsync: this);
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  void _jumpToCalendarForDoctor(int? doctorProfileId) {
    ref.read(calendarProvider.notifier).setResourceDoctorId(doctorProfileId);
    ref.read(shellProvider.notifier).setTab(2);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final clinicsAsync = ref.watch(myClinicsProvider);
    final selectedId = ref.watch(selectedClinicIdProvider);
    final clinic = ref.watch(selectedClinicProvider);

    return clinicsAsync.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => Center(child: Text('${l10n.error}: $e')),
      data: (clinics) {
        if (clinics.isEmpty) {
          return Center(
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Text(
                l10n.translate('clinicWorkspaceNoClinics'),
                textAlign: TextAlign.center,
                style: TextStyle(color: Colors.grey.shade700),
              ),
            ),
          );
        }

        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            _ClinicIdentityBar(
              clinics: clinics,
              selectedId: selectedId ?? clinics.first.clinicId,
              clinic: clinic ?? clinics.first,
              onSelectClinic: (id) {
                ref.read(selectedClinicIdProvider.notifier).select(id);
              },
            ),
            Material(
              color: Colors.white,
              child: TabBar(
                controller: _tabController,
                isScrollable: true,
                labelColor: AppColors.primaryTeal,
                unselectedLabelColor: Colors.black54,
                tabs: [
                  Tab(text: l10n.translate('clinicWorkspaceOverview')),
                  Tab(text: l10n.translate('clinicWorkspaceDoctors')),
                  Tab(text: l10n.translate('clinicWorkspaceCalendar')),
                  Tab(text: l10n.translate('clinicWorkspacePatients')),
                  Tab(text: l10n.translate('clinicWorkspaceServices')),
                  Tab(text: l10n.translate('clinicWorkspaceDocuments')),
                  Tab(text: l10n.translate('clinicWorkspaceAnalytics')),
                  Tab(text: l10n.translate('clinicWorkspaceSettings')),
                ],
              ),
            ),
            Expanded(
              child: TabBarView(
                controller: _tabController,
                children: [
                  _OverviewTab(clinicId: clinic?.clinicId ?? clinics.first.clinicId),
                  _DoctorsTab(
                    clinicId: clinic?.clinicId ?? clinics.first.clinicId,
                    onOpenCalendar: _jumpToCalendarForDoctor,
                  ),
                  _CalendarTab(
                    clinicId: clinic?.clinicId ?? clinics.first.clinicId,
                    onOpenMainCalendar: _jumpToCalendarForDoctor,
                  ),
                  _PatientsTab(clinicId: clinic?.clinicId ?? clinics.first.clinicId),
                  _ServicesTab(clinicId: clinic?.clinicId ?? clinics.first.clinicId),
                  _PlaceholderTab(message: l10n.translate('clinicPlaceholderDocuments')),
                  _PlaceholderTab(message: l10n.translate('clinicPlaceholderAnalytics')),
                  _SettingsTab(clinic: clinic ?? clinics.first),
                ],
              ),
            ),
          ],
        );
      },
    );
  }
}

class _ClinicIdentityBar extends StatelessWidget {
  const _ClinicIdentityBar({
    required this.clinics,
    required this.selectedId,
    required this.clinic,
    required this.onSelectClinic,
  });

  final List<MyClinicSummary> clinics;
  final int selectedId;
  final MyClinicSummary clinic;
  final void Function(int id) onSelectClinic;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return Container(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 12),
      decoration: BoxDecoration(
        color: AppColors.primaryTeal.withValues(alpha: 0.08),
        border: Border(bottom: BorderSide(color: Colors.grey.shade300)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(Icons.local_hospital, size: 40, color: AppColors.primaryTeal),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        clinic.name,
                        style: Theme.of(context).textTheme.titleLarge?.copyWith(
                              fontWeight: FontWeight.bold,
                            ),
                      ),
                    ),
                    if (clinics.length > 1)
                      DropdownButton<int>(
                        value: selectedId,
                        items: clinics
                            .map(
                              (c) => DropdownMenuItem(
                                value: c.clinicId,
                                child: Text(c.name, overflow: TextOverflow.ellipsis),
                              ),
                            )
                            .toList(),
                        onChanged: (v) {
                          if (v != null) onSelectClinic(v);
                        },
                      ),
                  ],
                ),
                const SizedBox(height: 4),
                Text(
                  '${clinic.timeZone}'
                  '${clinic.address != null && clinic.address!.isNotEmpty ? ' · ${clinic.address}' : ''}',
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(color: Colors.black54),
                ),
                const SizedBox(height: 4),
                Text(
                  '${l10n.translate('clinicWorkspaceYourRole')}: ${clinic.membershipRole}'
                  '${clinic.isPracticeClinic ? ' (${l10n.translate('clinicWorkspacePrimaryPractice')})' : ''}',
                  style: Theme.of(context).textTheme.bodySmall,
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _OverviewTab extends ConsumerWidget {
  const _OverviewTab({required this.clinicId});
  final int clinicId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context)!;
    final async = ref.watch(clinicOverviewStatsProvider(clinicId));
    final clinic = ref.watch(selectedClinicProvider);

    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (clinic != null) ...[
            if (clinic.phone != null && clinic.phone!.isNotEmpty)
              ListTile(
                dense: true,
                leading: const Icon(Icons.phone_outlined),
                title: Text(clinic.phone!),
              ),
            if (clinic.email != null && clinic.email!.isNotEmpty)
              ListTile(
                dense: true,
                leading: const Icon(Icons.email_outlined),
                title: Text(clinic.email!),
              ),
            const Divider(),
          ],
          async.when(
            loading: () => const Center(child: Padding(
              padding: EdgeInsets.all(24),
              child: CircularProgressIndicator(),
            )),
            error: (e, _) => Text('${l10n.error}: $e'),
            data: (s) {
              return Wrap(
                spacing: 12,
                runSpacing: 12,
                children: [
                  _MetricCard(
                    label: l10n.translate('clinicMetricAppointmentsToday'),
                    value: '${s.appointmentsToday}',
                  ),
                  _MetricCard(
                    label: l10n.translate('clinicMetricActiveDoctors'),
                    value: '${s.activeDoctors}',
                  ),
                  _MetricCard(
                    label: l10n.translate('clinicMetricPatientsThisMonth'),
                    value: '${s.patientsThisMonth}',
                  ),
                ],
              );
            },
          ),
          const SizedBox(height: 24),
          Text(l10n.translate('clinicWorkspaceQuickActions'), style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              ShifaSecondaryButton(
                label: l10n.translate('clinicOpenCalendarTab'),
                icon: Icons.calendar_today_outlined,
                onPressed: () {
                  ref.read(calendarProvider.notifier).setResourceDoctorId(null);
                  ref.read(shellProvider.notifier).setTab(2);
                },
              ),
              ShifaSecondaryButton(
                label: l10n.calendar,
                icon: Icons.event_available_outlined,
                onPressed: () {
                  ref.read(shellProvider.notifier).setTab(2);
                },
              ),
              ShifaSecondaryButton(
                label: l10n.patients,
                icon: Icons.people_outline,
                onPressed: () {
                  ref.read(shellProvider.notifier).setTab(3);
                },
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _MetricCard extends StatelessWidget {
  const _MetricCard({required this.label, required this.value});
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: SizedBox(
          width: 160,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(label, style: Theme.of(context).textTheme.bodySmall),
              const SizedBox(height: 4),
              Text(value, style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                    fontWeight: FontWeight.bold,
                    color: AppColors.primaryTeal,
                  )),
            ],
          ),
        ),
      ),
    );
  }
}

class _DoctorsTab extends ConsumerWidget {
  const _DoctorsTab({
    required this.clinicId,
    required this.onOpenCalendar,
  });
  final int clinicId;
  final void Function(int?) onOpenCalendar;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context)!;
    final async = ref.watch(clinicMembersProvider(clinicId));
    return async.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => Center(child: Text('$e')),
      data: (members) {
        if (members.isEmpty) {
          return Center(child: Text(l10n.translate('clinicWorkspaceNoDoctors')));
        }
        return ListView.separated(
          padding: const EdgeInsets.all(16),
          itemCount: members.length,
          separatorBuilder: (_, __) => const Divider(height: 1),
          itemBuilder: (_, i) {
            final m = members[i];
            return ListTile(
              leading: CircleAvatar(
                backgroundColor: AppColors.primaryTeal.withValues(alpha: 0.15),
                child: Text(
                  m.displayName.isNotEmpty ? m.displayName[0].toUpperCase() : '?',
                  style: const TextStyle(color: AppColors.primaryTeal, fontWeight: FontWeight.bold),
                ),
              ),
              title: Text(m.displayName),
              subtitle: Text('${m.membershipRole} · id ${m.doctorProfileId}'),
              trailing: IconButton(
                tooltip: l10n.translate('clinicDoctorOpenSchedule'),
                icon: const Icon(Icons.calendar_month_outlined),
                onPressed: () => onOpenCalendar(m.doctorProfileId),
              ),
            );
          },
        );
      },
    );
  }
}

class _CalendarTab extends ConsumerWidget {
  const _CalendarTab({
    required this.clinicId,
    required this.onOpenMainCalendar,
  });
  final int clinicId;
  final void Function(int?) onOpenMainCalendar;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context)!;
    final membersAsync = ref.watch(clinicMembersProvider(clinicId));
    return membersAsync.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => Center(child: Text('$e')),
      data: (members) {
        return Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(
                l10n.translate('clinicCalendarMvpHint'),
                style: Theme.of(context).textTheme.bodyMedium,
              ),
              const SizedBox(height: 16),
              Expanded(
                child: ListView(
                  children: [
                    ShifaPrimaryButton(
                      label: l10n.mySchedule,
                      icon: Icons.person_outline,
                      width: ButtonWidth.fill,
                      onPressed: () => onOpenMainCalendar(null),
                    ),
                    const SizedBox(height: 12),
                    ...members.map(
                      (m) => Padding(
                        padding: const EdgeInsets.only(bottom: 8),
                        child: ShifaSecondaryButton(
                          label: '${l10n.calendarForDoctor}: ${m.displayName}',
                          icon: Icons.calendar_today_outlined,
                          width: ButtonWidth.fill,
                          onPressed: () => onOpenMainCalendar(m.doctorProfileId),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}

class _PatientsTab extends ConsumerWidget {
  const _PatientsTab({required this.clinicId});
  final int clinicId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context)!;
    final async = ref.watch(clinicPatientsFirstPageProvider(clinicId));
    return async.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => Center(child: Text('$e')),
      data: (page) {
        if (page.content.isEmpty) {
          return Center(child: Text(l10n.translate('clinicPatientsEmpty')));
        }
        return Column(
          children: [
            Padding(
              padding: const EdgeInsets.all(12),
              child: Align(
                alignment: Alignment.centerLeft,
                child: Text(
                  l10n.translate('clinicPatientsTotal').replaceAll('{{count}}', '${page.totalElements}'),
                  style: Theme.of(context).textTheme.bodySmall,
                ),
              ),
            ),
            Expanded(
              child: ListView.separated(
                itemCount: page.content.length,
                separatorBuilder: (_, __) => const Divider(height: 1),
                itemBuilder: (_, i) {
                  final p = page.content[i];
                  final parts = <String>[
                    if (p.phone != null && p.phone!.trim().isNotEmpty) p.phone!.trim(),
                    if (p.email != null && p.email!.trim().isNotEmpty) p.email!.trim(),
                  ];
                  return ListTile(
                    title: Text(p.fullName),
                    subtitle: Text(parts.isEmpty ? '—' : parts.join(' · ')),
                  );
                },
              ),
            ),
          ],
        );
      },
    );
  }
}

class _ServicesTab extends ConsumerWidget {
  const _ServicesTab({required this.clinicId});
  final int clinicId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context)!;
    final async = ref.watch(clinicCatalogProvider(clinicId));
    return async.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => Center(child: Text('$e')),
      data: (items) {
        if (items.isEmpty) {
          return Center(child: Text(l10n.translate('clinicServicesEmpty')));
        }
        return ListView.separated(
          padding: const EdgeInsets.all(16),
          itemCount: items.length,
          separatorBuilder: (_, __) => const Divider(height: 1),
          itemBuilder: (_, i) {
            final it = items[i];
            final price = (it.defaultPriceMinor / 100).toStringAsFixed(2);
            return ListTile(
              title: Text(it.title),
              subtitle: Text('${it.currency} $price · ${it.code ?? '—'}'),
            );
          },
        );
      },
    );
  }
}

class _PlaceholderTab extends StatelessWidget {
  const _PlaceholderTab({required this.message});
  final String message;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Text(message, textAlign: TextAlign.center),
      ),
    );
  }
}

class _SettingsTab extends StatelessWidget {
  const _SettingsTab({required this.clinic});
  final MyClinicSummary clinic;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Text(l10n.translate('clinicSettingsReadOnly'), style: Theme.of(context).textTheme.bodySmall),
        const SizedBox(height: 12),
        ListTile(
          title: Text(l10n.translate('adminClinicNameLabel')),
          subtitle: Text(clinic.name),
        ),
        ListTile(
          title: Text(l10n.translate('adminClinicTimezoneLabel')),
          subtitle: Text(clinic.timeZone),
        ),
        if (clinic.phone != null)
          ListTile(
            title: Text(l10n.translate('adminClinicPhoneLabel')),
            subtitle: Text(clinic.phone!),
          ),
        if (clinic.email != null)
          ListTile(
            title: Text(l10n.translate('adminClinicEmailLabel')),
            subtitle: Text(clinic.email!),
          ),
        if (clinic.address != null)
          ListTile(
            title: Text(l10n.translate('adminClinicAddressLabel')),
            subtitle: Text(clinic.address!),
          ),
      ],
    );
  }
}
