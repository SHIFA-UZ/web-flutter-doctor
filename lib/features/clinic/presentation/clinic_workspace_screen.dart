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
import 'package:shifa_doc_app_v1/state/auth/doctor_jwt_role_provider.dart';
import 'package:shifa_doc_app_v1/features/clinic/presentation/clinic_invitations_tab.dart';
import 'package:shifa_doc_app_v1/core/api/api_providers.dart';
import 'package:shifa_doc_app_v1/features/patients/domain/patient_models.dart';
import 'package:shifa_doc_app_v1/features/patients/presentation/patients_screen.dart'
    show
        PatientDetailPanel,
        showPatientDocumentUploadOptions,
        showPatientFormTemplateSheet;
import 'package:shifa_doc_app_v1/state/patients/patient_actions.dart'
    show fetchPatientWithClient;
import 'package:shifa_doc_app_v1/features/clinic/presentation/clinic_doctor_schedule_page.dart';
import 'package:shifa_doc_app_v1/features/clinic/presentation/clinic_finance_tab.dart';
import 'package:shifa_doc_app_v1/features/clinic/presentation/clinic_table_shell.dart';
import 'package:shifa_doc_app_v1/features/clinic/presentation/clinic_treatment_plans_tab.dart';

/// Full booking UI for another clinic doctor ([ClinicDoctorScheduleRoute]).
///
/// Prefer this over `[calendarProvider]` + shell Calendar for colleagues: entering
/// the shell Calendar tab clears `resourceDoctorId`, which would drop the intent.
void pushClinicDoctorScheduleForMember(
  BuildContext context,
  WidgetRef ref,
  ClinicMember member,
) {
  final c = ref.read(selectedClinicProvider);
  final tz = (c?.timeZone.trim().isNotEmpty == true)
      ? c!.timeZone.trim()
      : 'UTC';
  final street = (c?.address?.trim().isNotEmpty == true)
      ? c!.address!.trim()
      : null;
  Navigator.of(context, rootNavigator: true).push(
    MaterialPageRoute<void>(
      fullscreenDialog: true,
      builder: (ctx) => ClinicDoctorScheduleRoute(
        doctorProfileId: member.doctorProfileId,
        doctorDisplayName: member.displayName,
        clinicScheduleTimeZone: tz,
        clinicStreetAddress: street,
      ),
    ),
  );
}

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
    _tabController = TabController(length: 9, vsync: this);
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  /// Logged-in doctor only (shell Calendar clears `resourceDoctorId` on entry).
  void _openMyScheduleInShellCalendar() {
    if (ref.read(doctorAppJwtRoleProvider) == DoctorAppJwtRole.clinicStaff) {
      return;
    }
    ref.read(calendarProvider.notifier).setResourceDoctorId(null);
    ref.read(shellProvider.notifier).setTab(2);
  }

  /// [MainShell] clinic workspace index (`IndexedStack`; see `main_shell.dart`).
  int _clinicWorkspaceShellTabIndex() =>
      ref.read(doctorAppJwtRoleProvider) == DoctorAppJwtRole.clinicStaff ? 1 : 4;

  /// Opens the clinic workspace in the shell and selects one of its [TabBar] tabs.
  void _jumpToClinicWorkspaceSubTab(int clinicTabIndex) {
    ref.read(shellProvider.notifier).setTab(_clinicWorkspaceShellTabIndex());
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      if (clinicTabIndex >= 0 &&
          clinicTabIndex < _tabController.length) {
        _tabController.animateTo(clinicTabIndex);
      }
    });
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
                  Tab(text: l10n.translate('clinicWorkspaceTreatmentPlans')),
                  Tab(text: l10n.translate('clinicWorkspaceFinance')),
                  Tab(text: l10n.translate('clinicWorkspaceInvitations')),
                  Tab(text: l10n.translate('clinicWorkspaceSettings')),
                ],
              ),
            ),
            Expanded(
              child: TabBarView(
                controller: _tabController,
                children: [
                  _OverviewTab(
                    clinicId: clinic?.clinicId ?? clinics.first.clinicId,
                    onOpenClinicSubTab: _jumpToClinicWorkspaceSubTab,
                  ),
                  _DoctorsTab(
                    clinicId: clinic?.clinicId ?? clinics.first.clinicId,
                  ),
                  _CalendarTab(
                    clinicId: clinic?.clinicId ?? clinics.first.clinicId,
                    onOpenMySchedule: _openMyScheduleInShellCalendar,
                  ),
                  _PatientsTab(clinicId: clinic?.clinicId ?? clinics.first.clinicId),
                  _ServicesTab(clinicId: clinic?.clinicId ?? clinics.first.clinicId),
                  ClinicTreatmentPlansTab(clinicId: clinic?.clinicId ?? clinics.first.clinicId),
                  ClinicFinanceTab(clinicId: clinic?.clinicId ?? clinics.first.clinicId),
                  ClinicInvitationsTab(clinicId: clinic?.clinicId ?? clinics.first.clinicId),
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
  const _OverviewTab({
    required this.clinicId,
    required this.onOpenClinicSubTab,
  });
  final int clinicId;
  /// Clinic inner tab indices: Overview=0, Doctors=1, Calendar=2, Patients=3, …
  final void Function(int clinicTabIndex) onOpenClinicSubTab;

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
                  if (ref.read(doctorAppJwtRoleProvider) == DoctorAppJwtRole.clinicStaff) {
                    onOpenClinicSubTab(2);
                  } else {
                    ref.read(shellProvider.notifier).setTab(2);
                  }
                },
              ),
              ShifaSecondaryButton(
                label: l10n.calendar,
                icon: Icons.event_available_outlined,
                onPressed: () => onOpenClinicSubTab(2),
              ),
              ShifaSecondaryButton(
                label: l10n.patients,
                icon: Icons.people_outline,
                onPressed: () => onOpenClinicSubTab(3),
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

/// Sortable, filterable spreadsheet of clinic doctors and admins.
///
/// Columns: avatar + name (sortable), role (sortable + filterable via chips),
/// doctor profile id (numeric, sortable), user id (numeric, sortable), and an
/// action button to jump to that doctor's calendar.
class _DoctorsTab extends ConsumerStatefulWidget {
  const _DoctorsTab({
    required this.clinicId,
  });
  final int clinicId;

  @override
  ConsumerState<_DoctorsTab> createState() => _DoctorsTabState();
}

class _DoctorsTabState extends ConsumerState<_DoctorsTab> {
  final TextEditingController _searchCtrl = TextEditingController();
  String _search = '';
  String _roleFilter = 'ALL';
  int _sortIdx = 0;
  bool _sortAsc = true;

  @override
  void dispose() {
    _searchCtrl.dispose();
    super.dispose();
  }

  void _onSort(int i, bool asc) =>
      setState(() => (_sortIdx = i, _sortAsc = asc));

  List<ClinicMember> _apply(List<ClinicMember> members) {
    Iterable<ClinicMember> out = members;
    if (_search.trim().isNotEmpty) {
      final s = _search.trim().toLowerCase();
      out = out.where((m) =>
          m.displayName.toLowerCase().contains(s) ||
          m.membershipRole.toLowerCase().contains(s) ||
          m.doctorProfileId.toString().contains(s));
    }
    if (_roleFilter != 'ALL') {
      out = out.where((m) => m.membershipRole == _roleFilter);
    }
    final list = out.toList();
    int cmp(ClinicMember a, ClinicMember b) {
      int c;
      switch (_sortIdx) {
        case 0:
          c = a.displayName.toLowerCase().compareTo(b.displayName.toLowerCase());
          break;
        case 1:
          c = a.membershipRole.compareTo(b.membershipRole);
          break;
        case 2:
          c = a.doctorProfileId.compareTo(b.doctorProfileId);
          break;
        case 3:
          c = a.userId.compareTo(b.userId);
          break;
        default:
          c = 0;
      }
      return _sortAsc ? c : -c;
    }
    list.sort(cmp);
    return list;
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final async = ref.watch(clinicMembersProvider(widget.clinicId));
    return async.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => Center(child: Text('$e')),
      data: (members) {
        // Build the set of roles present in the dataset so the filter chips
        // only offer values that can actually match.
        final roles = {
          for (final m in members) m.membershipRole,
        }.toList()
          ..sort();
        final filtered = _apply(members);

        final toolbar = Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            ClinicTableSearchField(
              controller: _searchCtrl,
              hint: l10n.translate('clinicDoctorsSearchHint'),
              onChanged: (v) => setState(() => _search = v),
            ),
            const SizedBox(height: 8),
            ClinicFilterChips<String>(
              selected: _roleFilter,
              onSelected: (v) => setState(() => _roleFilter = v),
              options: [
                (
                  value: 'ALL',
                  label: l10n.translate('clinicTreatmentPlansAll'),
                ),
                for (final r in roles) (value: r, label: r),
              ],
            ),
          ],
        );

        final Widget body = members.isEmpty || filtered.isEmpty
            ? ClinicTableEmpty(l10n.translate('clinicWorkspaceNoDoctors'))
            : clinicDataTable(
                context: context,
                sortColumnIndex: _sortIdx,
                sortAscending: _sortAsc,
                columns: [
                  DataColumn(
                    label: Text(l10n.translate('clinicDoctorsColName')),
                    onSort: _onSort,
                  ),
                  DataColumn(
                    label: Text(l10n.translate('clinicDoctorsColRole')),
                    onSort: _onSort,
                  ),
                  DataColumn(
                    label: Text(l10n.translate('clinicDoctorsColProfileId')),
                    numeric: true,
                    onSort: _onSort,
                  ),
                  DataColumn(
                    label: Text(l10n.translate('clinicDoctorsColUserId')),
                    numeric: true,
                    onSort: _onSort,
                  ),
                  DataColumn(
                    label: Text(l10n.translate('clinicDoctorsColActions')),
                  ),
                ],
                rows: filtered.map((m) {
                  return DataRow(
                    cells: [
                      DataCell(
                        Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            CircleAvatar(
                              radius: 14,
                              backgroundColor: AppColors.primaryTeal
                                  .withValues(alpha: 0.15),
                              child: Text(
                                m.displayName.isNotEmpty
                                    ? m.displayName[0].toUpperCase()
                                    : '?',
                                style: const TextStyle(
                                  color: AppColors.primaryTeal,
                                  fontWeight: FontWeight.bold,
                                  fontSize: 12,
                                ),
                              ),
                            ),
                            const SizedBox(width: 8),
                            Text(
                              m.displayName,
                              style: const TextStyle(
                                fontWeight: FontWeight.w500,
                              ),
                            ),
                          ],
                        ),
                      ),
                      DataCell(Text(m.membershipRole)),
                      DataCell(Text('#${m.doctorProfileId}')),
                      DataCell(Text('#${m.userId}')),
                      DataCell(
                        IconButton(
                          tooltip: l10n.translate('clinicDoctorOpenSchedule'),
                          icon: const Icon(
                            Icons.calendar_month_outlined,
                            size: 20,
                          ),
                          onPressed: () => pushClinicDoctorScheduleForMember(
                            context,
                            ref,
                            m,
                          ),
                        ),
                      ),
                    ],
                  );
                }).toList(),
              );

        return ClinicTableShell(toolbar: toolbar, body: body);
      },
    );
  }
}

class _CalendarTab extends ConsumerWidget {
  const _CalendarTab({
    required this.clinicId,
    required this.onOpenMySchedule,
  });
  final int clinicId;
  final VoidCallback onOpenMySchedule;

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
                      onPressed: onOpenMySchedule,
                    ),
                    const SizedBox(height: 12),
                    ...members.map(
                      (m) => Padding(
                        padding: const EdgeInsets.only(bottom: 8),
                        child: ShifaSecondaryButton(
                          label: '${l10n.calendarForDoctor}: ${m.displayName}',
                          icon: Icons.calendar_today_outlined,
                          width: ButtonWidth.fill,
                          onPressed: () =>
                              pushClinicDoctorScheduleForMember(context, ref, m),
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

class _PatientsTab extends ConsumerStatefulWidget {
  const _PatientsTab({required this.clinicId});
  final int clinicId;

  @override
  ConsumerState<_PatientsTab> createState() => _PatientsTabState();
}

class _PatientsTabState extends ConsumerState<_PatientsTab> {
  int? _selectedPatientId;
  Patient? _selectedPatient;
  bool _loadingPatient = false;
  String? _patientError;

  /// Client-side filter applied to the loaded patient page. The provider
  /// only loads the first page so we don't bother paginating, but search
  /// scans every loaded patient name / phone / email.
  final TextEditingController _searchCtrl = TextEditingController();
  String _search = '';
  int _sortIdx = 1; // Default: sort by Full name asc.
  bool _sortAsc = true;

  @override
  void dispose() {
    _searchCtrl.dispose();
    super.dispose();
  }

  void _onSort(int i, bool asc) =>
      setState(() => (_sortIdx = i, _sortAsc = asc));

  List<ClinicPatientRow> _apply(List<ClinicPatientRow> patients) {
    Iterable<ClinicPatientRow> out = patients;
    if (_search.trim().isNotEmpty) {
      final s = _search.trim().toLowerCase();
      out = out.where((p) =>
          p.fullName.toLowerCase().contains(s) ||
          (p.phone ?? '').toLowerCase().contains(s) ||
          (p.email ?? '').toLowerCase().contains(s) ||
          p.patientId.toString().contains(s));
    }
    final list = out.toList();
    int cmp(ClinicPatientRow a, ClinicPatientRow b) {
      int c;
      switch (_sortIdx) {
        case 0:
          c = a.patientId.compareTo(b.patientId);
          break;
        case 1:
          c = a.fullName.toLowerCase().compareTo(b.fullName.toLowerCase());
          break;
        case 2:
          c = (a.phone ?? '').compareTo(b.phone ?? '');
          break;
        case 3:
          c = (a.email ?? '').compareTo(b.email ?? '');
          break;
        default:
          c = 0;
      }
      return _sortAsc ? c : -c;
    }
    list.sort(cmp);
    return list;
  }

  Future<void> _loadPatient(int patientId) async {
    setState(() {
      _selectedPatientId = patientId;
      _loadingPatient = true;
      _patientError = null;
      _selectedPatient = null;
    });
    try {
      final client = ref.read(apiClientProvider);
      final p = await fetchPatientWithClient(
        client: client,
        patientId: patientId.toString(),
        clinicId: widget.clinicId,
      );
      if (!mounted) return;
      setState(() {
        _selectedPatient = p;
        _loadingPatient = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _patientError = '$e';
        _loadingPatient = false;
      });
    }
  }

  void _refreshSelectedPatient() {
    final id = _selectedPatientId;
    if (id != null) {
      _loadPatient(id);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final brand = Theme.of(context).colorScheme.primary;
    final async = ref.watch(clinicPatientsFirstPageProvider(widget.clinicId));
    return async.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => Center(child: Text('$e')),
      data: (page) {
        if (page.content.isEmpty) {
          return Center(child: Text(l10n.translate('clinicPatientsEmpty')));
        }

        void ensureSelection() {
          if (_selectedPatientId != null) return;
          final first = page.content.first;
          WidgetsBinding.instance.addPostFrameCallback((_) {
            if (mounted) _loadPatient(first.patientId);
          });
        }

        ensureSelection();

        Widget listPane() {
          final filtered = _apply(page.content);
          final toolbar = Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              ClinicTableSearchField(
                controller: _searchCtrl,
                hint: l10n.translate('clinicPatientsSearchHint'),
                onChanged: (v) => setState(() => _search = v),
              ),
              const SizedBox(height: 4),
              Text(
                l10n
                    .translate('clinicPatientsTotal')
                    .replaceAll('{{count}}', '${page.totalElements}'),
                style: Theme.of(context).textTheme.bodySmall,
              ),
            ],
          );

          final Widget body = filtered.isEmpty
              ? ClinicTableEmpty(l10n.translate('clinicPatientsEmpty'))
              : clinicDataTable(
                  context: context,
                  sortColumnIndex: _sortIdx,
                  sortAscending: _sortAsc,
                  columns: [
                    DataColumn(
                      label: Text(l10n.translate('clinicPatientsColId')),
                      numeric: true,
                      onSort: _onSort,
                    ),
                    DataColumn(
                      label: Text(l10n.translate('clinicPatientsColName')),
                      onSort: _onSort,
                    ),
                    DataColumn(
                      label: Text(l10n.translate('clinicPatientsColPhone')),
                      onSort: _onSort,
                    ),
                    DataColumn(
                      label: Text(l10n.translate('clinicPatientsColEmail')),
                      onSort: _onSort,
                    ),
                    DataColumn(
                      label: Text(l10n.translate('clinicPatientsColActions')),
                    ),
                  ],
                  rows: filtered.map((p) {
                    final selected = _selectedPatientId == p.patientId;
                    return DataRow(
                      selected: selected,
                      onSelectChanged: (_) => _loadPatient(p.patientId),
                      cells: [
                        DataCell(Text('#${p.patientId}')),
                        DataCell(Text(
                          p.fullName,
                          style: const TextStyle(
                            fontWeight: FontWeight.w500,
                          ),
                        )),
                        DataCell(Text(p.phone?.trim().isNotEmpty == true
                            ? p.phone!.trim()
                            : '—')),
                        DataCell(Text(p.email?.trim().isNotEmpty == true
                            ? p.email!.trim()
                            : '—')),
                        DataCell(
                          IconButton(
                            tooltip:
                                l10n.translate('clinicPatientsOpenTooltip'),
                            icon: const Icon(Icons.open_in_new, size: 20),
                            onPressed: () => _loadPatient(p.patientId),
                          ),
                        ),
                      ],
                    );
                  }).toList(),
                );

          return ClinicTableShell(toolbar: toolbar, body: body);
        }

        Widget detailPane() {
          if (_loadingPatient) {
            return const Center(child: CircularProgressIndicator());
          }
          if (_patientError != null) {
            return Center(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Text(_patientError!, textAlign: TextAlign.center),
              ),
            );
          }
          return PatientDetailPanel(
            patient: _selectedPatient,
            brand: brand,
            clinicWorkspaceId: widget.clinicId,
            onUploadOptions: (p) => showPatientDocumentUploadOptions(
              context,
              ref,
              p,
              clinicWorkspaceId: widget.clinicId,
              onAfterUpload: _refreshSelectedPatient,
            ),
            onCreateForm: (p) => showPatientFormTemplateSheet(context, p),
            formatDate: _formatClinicDate,
            onPatientDataRefresh: _refreshSelectedPatient,
          );
        }

        return LayoutBuilder(
          builder: (context, constraints) {
            final wide = constraints.maxWidth >= 900;
            if (wide) {
              return Row(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Expanded(flex: 2, child: listPane()),
                  const VerticalDivider(width: 1),
                  Expanded(flex: 3, child: detailPane()),
                ],
              );
            }
            return Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Expanded(flex: 2, child: listPane()),
                const Divider(height: 1),
                Expanded(flex: 3, child: detailPane()),
              ],
            );
          },
        );
      },
    );
  }
}

String _formatClinicDate(DateTime d) {
  String two(int n) => n.toString().padLeft(2, '0');
  return '${two(d.day)}.${two(d.month)}.${d.year}';
}

class _ServicesTab extends ConsumerStatefulWidget {
  const _ServicesTab({required this.clinicId});
  final int clinicId;

  static const _currencies = ['UZS', 'USD', 'EUR', 'RUB'];

  @override
  ConsumerState<_ServicesTab> createState() => _ServicesTabState();
}

class _ServicesTabState extends ConsumerState<_ServicesTab> {
  final TextEditingController _searchCtrl = TextEditingController();
  String _search = '';
  String _statusFilter = 'ALL'; // ALL, ACTIVE, INACTIVE
  int _sortIdx = 1; // Default: sort by Title asc.
  bool _sortAsc = true;

  @override
  void dispose() {
    _searchCtrl.dispose();
    super.dispose();
  }

  void _onSort(int i, bool asc) =>
      setState(() => (_sortIdx = i, _sortAsc = asc));

  List<ClinicCatalogItem> _apply(List<ClinicCatalogItem> items) {
    Iterable<ClinicCatalogItem> out = items;
    if (_search.trim().isNotEmpty) {
      final s = _search.trim().toLowerCase();
      out = out.where((it) =>
          it.title.toLowerCase().contains(s) ||
          (it.code ?? '').toLowerCase().contains(s) ||
          it.id.toString().contains(s));
    }
    if (_statusFilter == 'ACTIVE') {
      out = out.where((it) => it.active);
    } else if (_statusFilter == 'INACTIVE') {
      out = out.where((it) => !it.active);
    }
    final list = out.toList();
    int cmp(ClinicCatalogItem a, ClinicCatalogItem b) {
      int c;
      switch (_sortIdx) {
        case 0:
          c = a.id.compareTo(b.id);
          break;
        case 1:
          c = a.title.toLowerCase().compareTo(b.title.toLowerCase());
          break;
        case 2:
          c = (a.code ?? '').compareTo(b.code ?? '');
          break;
        case 3:
          c = a.defaultPriceMinor.compareTo(b.defaultPriceMinor);
          break;
        case 4:
          c = a.currency.compareTo(b.currency);
          break;
        case 5:
          // Active first when asc.
          c = (a.active ? 0 : 1).compareTo(b.active ? 0 : 1);
          break;
        default:
          c = 0;
      }
      return _sortAsc ? c : -c;
    }
    list.sort(cmp);
    return list;
  }

  String _assignmentSubtitle(AppLocalizations l10n, List<ClinicMember> members, ClinicCatalogItem it) {
    if (it.appliesToAllDoctors) return l10n.translate('clinicServicesAssignmentAll');
    if (it.assignedDoctorProfileIds.isEmpty) return l10n.translate('clinicServicesAssignmentNone');
    final names = <String>[];
    for (final id in it.assignedDoctorProfileIds) {
      String? label;
      for (final m in members) {
        if (m.doctorProfileId == id) {
          label = m.displayName;
          break;
        }
      }
      names.add(label ?? '#$id');
    }
    return names.join(', ');
  }

  Future<void> _openEditor(BuildContext context, WidgetRef ref, ClinicCatalogItem? existing) async {
    final l10n = AppLocalizations.of(context)!;
    final clinicId = widget.clinicId;
    List<ClinicMember> members = <ClinicMember>[];
    try {
      members = await ref.read(clinicMembersProvider(clinicId).future);
    } catch (_) {
      members = ref.read(clinicMembersProvider(clinicId)).valueOrNull ?? <ClinicMember>[];
    }

    if (!context.mounted) return;

    final titleCtrl = TextEditingController(text: existing?.title ?? '');
    final codeCtrl = TextEditingController(text: existing?.code ?? '');
    final priceCtrl = TextEditingController(
      text: existing != null ? (existing.defaultPriceMinor / 100).toStringAsFixed(2) : '',
    );
    var currency = existing?.currency ?? 'UZS';
    if (!_ServicesTab._currencies.contains(currency)) currency = 'UZS';
    var appliesToAll = existing?.appliesToAllDoctors ?? true;
    final selected = <int>{if (existing != null) ...existing.assignedDoctorProfileIds};

    Future<void> submit(void Function(void Function()) setLocal, NavigatorState nav) async {
      final title = titleCtrl.text.trim();
      if (title.isEmpty) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(l10n.translate('clinicServiceTitleLabel'))),
        );
        return;
      }
      final priceMinor = ((double.tryParse(priceCtrl.text.trim()) ?? 0) * 100).round();
      if (priceMinor < 0) return;
      if (!appliesToAll && selected.isEmpty) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(l10n.translate('clinicServicePickDoctors'))),
        );
        return;
      }
      try {
        if (existing == null) {
          await createClinicCatalogItem(
            ref,
            clinicId: clinicId,
            code: codeCtrl.text.trim().isEmpty ? null : codeCtrl.text.trim(),
            title: title,
            defaultPriceMinor: priceMinor,
            currency: currency,
            active: true,
            appliesToAllDoctors: appliesToAll,
            assignedDoctorProfileIds: appliesToAll ? <int>[] : selected.toList(),
          );
        } else {
          await patchClinicCatalogItem(
            ref,
            clinicId: clinicId,
            catalogItemId: existing.id,
            code: codeCtrl.text.trim().isEmpty ? '' : codeCtrl.text.trim(),
            title: title,
            defaultPriceMinor: priceMinor,
            currency: currency,
            appliesToAllDoctors: appliesToAll,
            assignedDoctorProfileIds: appliesToAll ? null : selected.toList(),
          );
        }
        if (context.mounted) nav.pop(true);
      } catch (e) {
        if (context.mounted) {
          ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('$e')));
        }
      }
    }

    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) {
        return StatefulBuilder(
          builder: (ctx, setLocal) {
            return AlertDialog(
              title: Text(
                existing == null ? l10n.translate('clinicServiceAddTitle') : l10n.translate('clinicServiceEditTitle'),
              ),
              content: SizedBox(
                width: 420,
                child: SingleChildScrollView(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      TextField(
                        controller: titleCtrl,
                        decoration: InputDecoration(labelText: l10n.translate('clinicServiceTitleLabel')),
                      ),
                      const SizedBox(height: 12),
                      TextField(
                        controller: codeCtrl,
                        decoration: InputDecoration(labelText: l10n.translate('clinicServiceCodeLabel')),
                      ),
                      const SizedBox(height: 12),
                      Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Expanded(
                            child: TextField(
                              controller: priceCtrl,
                              keyboardType: const TextInputType.numberWithOptions(decimal: true),
                              decoration: InputDecoration(labelText: l10n.translate('clinicServicePriceLabel')),
                            ),
                          ),
                          const SizedBox(width: 12),
                          SizedBox(
                            width: 100,
                            child: DropdownButtonFormField<String>(
                              initialValue: currency,
                              decoration: InputDecoration(labelText: l10n.translate('clinicServiceCurrencyLabel')),
                              items: _ServicesTab._currencies
                                  .map((c) => DropdownMenuItem(value: c, child: Text(c)))
                                  .toList(),
                              onChanged: (v) {
                                if (v != null) setLocal(() => currency = v);
                              },
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 12),
                      SwitchListTile(
                        contentPadding: EdgeInsets.zero,
                        title: Text(l10n.translate('clinicServiceAllDoctorsToggle')),
                        value: appliesToAll,
                        onChanged: (v) => setLocal(() => appliesToAll = v),
                      ),
                      if (!appliesToAll) ...[
                        Text(l10n.translate('clinicServicePickDoctors'), style: Theme.of(ctx).textTheme.titleSmall),
                        const SizedBox(height: 8),
                        if (members.isEmpty)
                          Text(l10n.translate('clinicWorkspaceNoDoctors'), style: Theme.of(ctx).textTheme.bodySmall)
                        else
                          ...members.map((m) {
                            return CheckboxListTile(
                              dense: true,
                              contentPadding: EdgeInsets.zero,
                              title: Text(m.displayName),
                              subtitle: Text(m.membershipRole),
                              value: selected.contains(m.doctorProfileId),
                              onChanged: (on) {
                                setLocal(() {
                                  if (on == true) {
                                    selected.add(m.doctorProfileId);
                                  } else {
                                    selected.remove(m.doctorProfileId);
                                  }
                                });
                              },
                            );
                          }),
                      ],
                    ],
                  ),
                ),
              ),
              actions: [
                TextButton(onPressed: () => Navigator.pop(ctx, false), child: Text(l10n.translate('cancel'))),
                TextButton(
                  onPressed: () => submit(setLocal, Navigator.of(ctx)),
                  child: Text(l10n.translate('clinicServiceSave')),
                ),
              ],
            );
          },
        );
      },
    );

    titleCtrl.dispose();
    codeCtrl.dispose();
    priceCtrl.dispose();
    if (ok == true && context.mounted) {
      ref.invalidate(clinicCatalogProvider(clinicId));
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final async = ref.watch(clinicCatalogProvider(widget.clinicId));
    final membersAsync = ref.watch(clinicMembersProvider(widget.clinicId));
    final members = membersAsync.valueOrNull ?? <ClinicMember>[];

    return async.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => Center(child: Text('$e')),
      data: (items) {
        final filtered = _apply(items);

        final toolbar = Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: ClinicTableSearchField(
                    controller: _searchCtrl,
                    hint: l10n.translate('clinicServicesSearchHint'),
                    onChanged: (v) => setState(() => _search = v),
                  ),
                ),
                const SizedBox(width: 8),
                FilledButton.icon(
                  onPressed: () => _openEditor(context, ref, null),
                  icon: const Icon(Icons.add, size: 18),
                  label: Text(l10n.translate('clinicServiceAddTitle')),
                ),
              ],
            ),
            const SizedBox(height: 8),
            ClinicFilterChips<String>(
              selected: _statusFilter,
              onSelected: (v) => setState(() => _statusFilter = v),
              options: [
                (
                  value: 'ALL',
                  label: l10n.translate('clinicTreatmentPlansAll'),
                ),
                (
                  value: 'ACTIVE',
                  label: l10n.translate('clinicServiceActive'),
                ),
                (
                  value: 'INACTIVE',
                  label: l10n.translate('clinicServiceInactiveBadge'),
                ),
              ],
            ),
          ],
        );

        final Widget body = items.isEmpty || filtered.isEmpty
            ? ClinicTableEmpty(l10n.translate('clinicServicesEmpty'))
            : clinicDataTable(
                context: context,
                sortColumnIndex: _sortIdx,
                sortAscending: _sortAsc,
                columns: [
                  DataColumn(
                    label: Text(l10n.translate('clinicServicesColId')),
                    numeric: true,
                    onSort: _onSort,
                  ),
                  DataColumn(
                    label: Text(l10n.translate('clinicServicesColTitle')),
                    onSort: _onSort,
                  ),
                  DataColumn(
                    label: Text(l10n.translate('clinicServicesColCode')),
                    onSort: _onSort,
                  ),
                  DataColumn(
                    label: Text(l10n.translate('clinicServicesColPrice')),
                    numeric: true,
                    onSort: _onSort,
                  ),
                  DataColumn(
                    label: Text(l10n.translate('clinicServicesColCurrency')),
                    onSort: _onSort,
                  ),
                  DataColumn(
                    label: Text(l10n.translate('clinicServicesColStatus')),
                    onSort: _onSort,
                  ),
                  DataColumn(
                    label: Text(l10n.translate('clinicServicesColDoctors')),
                  ),
                  DataColumn(
                    label: Text(l10n.translate('clinicServicesColActions')),
                  ),
                ],
                rows: filtered.map((it) {
                  final price =
                      (it.defaultPriceMinor / 100).toStringAsFixed(2);
                  return DataRow(
                    cells: [
                      DataCell(Text('#${it.id}')),
                      DataCell(Text(
                        it.title,
                        style: TextStyle(
                          color: it.active ? null : Colors.grey,
                          fontStyle:
                              it.active ? null : FontStyle.italic,
                          fontWeight: FontWeight.w500,
                        ),
                      )),
                      DataCell(Text(it.code ?? '—')),
                      DataCell(Text(
                        price,
                        style: const TextStyle(
                          fontWeight: FontWeight.w600,
                        ),
                      )),
                      DataCell(Text(it.currency)),
                      DataCell(
                        Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 8,
                            vertical: 3,
                          ),
                          decoration: BoxDecoration(
                            color: (it.active
                                    ? Colors.green
                                    : Colors.grey)
                                .withValues(alpha: 0.12),
                            borderRadius: BorderRadius.circular(999),
                          ),
                          child: Text(
                            it.active
                                ? l10n.translate('clinicServiceActive')
                                : l10n
                                    .translate('clinicServiceInactiveBadge'),
                            style: TextStyle(
                              color: it.active
                                  ? Colors.green.shade800
                                  : Colors.grey.shade700,
                              fontSize: 11,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ),
                      ),
                      DataCell(
                        ConstrainedBox(
                          constraints:
                              const BoxConstraints(maxWidth: 240),
                          child: Text(
                            _assignmentSubtitle(l10n, members, it),
                            overflow: TextOverflow.ellipsis,
                            maxLines: 2,
                          ),
                        ),
                      ),
                      DataCell(
                        PopupMenuButton<String>(
                          icon: const Icon(Icons.more_vert, size: 20),
                          onSelected: (v) async {
                            if (v == 'edit') {
                              await _openEditor(context, ref, it);
                            } else if (v == 'toggle') {
                              try {
                                await patchClinicCatalogItem(
                                  ref,
                                  clinicId: widget.clinicId,
                                  catalogItemId: it.id,
                                  active: !it.active,
                                );
                                if (context.mounted) {
                                  ref.invalidate(clinicCatalogProvider(
                                      widget.clinicId));
                                }
                              } catch (e) {
                                if (context.mounted) {
                                  ScaffoldMessenger.of(context)
                                      .showSnackBar(SnackBar(
                                          content: Text('$e')));
                                }
                              }
                            }
                          },
                          itemBuilder: (ctx) => [
                            PopupMenuItem(
                              value: 'edit',
                              child: Text(l10n.translate('edit')),
                            ),
                            PopupMenuItem(
                              value: 'toggle',
                              child: Text(
                                it.active
                                    ? l10n.translate(
                                        'clinicServiceDeactivate')
                                    : l10n.translate(
                                        'clinicServiceActivate'),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  );
                }).toList(),
              );

        return ClinicTableShell(toolbar: toolbar, body: body);
      },
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
