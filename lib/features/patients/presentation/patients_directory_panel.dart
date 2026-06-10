import 'package:flutter/material.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/layout/platform_layout.dart';
import 'package:shifa_doc_app_v1/core/layout/responsive.dart';
import 'package:shifa_doc_app_v1/core/theme/app_colors.dart';
import 'package:shifa_doc_app_v1/core/theme/app_design_system.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';
import 'package:shifa_doc_app_v1/features/patients/domain/patient_models.dart';
import 'package:shifa_doc_app_v1/features/patients/presentation/patient_detail_helpers.dart';

class PatientsDirectoryPanel extends StatefulWidget {
  const PatientsDirectoryPanel({
    super.key,
    required this.patients,
    required this.selectedId,
    required this.favoriteIds,
    required this.onSelect,
    required this.onCreatePatient,
    this.onRefresh,
  });

  final List<Patient> patients;
  final String? selectedId;
  final Set<String> favoriteIds;
  final ValueChanged<String> onSelect;
  final VoidCallback onCreatePatient;
  final VoidCallback? onRefresh;

  @override
  State<PatientsDirectoryPanel> createState() => _PatientsDirectoryPanelState();
}

class _PatientsDirectoryPanelState extends State<PatientsDirectoryPanel> {
  final TextEditingController _searchCtrl = TextEditingController();
  PatientListTab _tab = PatientListTab.all;
  PatientSortOption _sort = PatientSortOption.nameAsc;
  PatientStatusKind? _statusFilter;
  int _page = 0;
  static const int _pageSize = 10;

  @override
  void initState() {
    super.initState();
    _searchCtrl.addListener(_onSearchQueryChanged);
  }

  @override
  void dispose() {
    _searchCtrl.removeListener(_onSearchQueryChanged);
    _searchCtrl.dispose();
    super.dispose();
  }

  void _onSearchQueryChanged() {
    if (!mounted) return;
    setState(() => _page = 0);
  }

  List<Patient> get _visiblePatients {
    final query = _searchCtrl.text.trim().toLowerCase();
    var list = filterPatientsByTab(
      widget.patients,
      _tab,
      widget.favoriteIds,
    );
    if (query.isNotEmpty) {
      list = list.where((p) {
        final phone = patientPhoneDisplay(p).toLowerCase();
        return p.name.toLowerCase().contains(query) ||
            p.id.contains(query) ||
            phone.contains(query) ||
            patientDisplayId(p).toLowerCase().contains(query);
      }).toList();
    }
    if (_statusFilter != null) {
      list = list
          .where((p) => patientStatusKind(p) == _statusFilter)
          .toList();
    }
    return sortPatients(list, _sort);
  }

  List<Patient> get _pagePatients {
    final start = _page * _pageSize;
    final list = _visiblePatients;
    if (start >= list.length) return const [];
    final end = (start + _pageSize).clamp(0, list.length);
    return list.sublist(start, end);
  }

  int get _totalPages {
    final count = _visiblePatients.length;
    if (count == 0) return 1;
    return (count / _pageSize).ceil();
  }

  void _setTab(PatientListTab tab) {
    setState(() {
      _tab = tab;
      _page = 0;
    });
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final brand = Theme.of(context).colorScheme.primary;
    final total = widget.patients.length;
    final visible = _visiblePatients.length;
    final pageItems = _pagePatients;
    final start = visible == 0 ? 0 : (_page * _pageSize) + 1;
    final end = visible == 0
        ? 0
        : ((_page + 1) * _pageSize).clamp(0, visible);

    if (PlatformLayout.useCompactToolbar(context)) {
      return Container(
        decoration: AppDesignSystem.cardDecoration(
          borderOverride:
              Border.all(color: AppDesignSystem.border.withValues(alpha: 0.6)),
        ),
        clipBehavior: Clip.antiAlias,
        child: CustomScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          slivers: [
            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(12, 16, 12, 0),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Row(
                      children: [
                        Expanded(
                          child: Text(
                            l10n.patients,
                            style: AppDesignSystem.display(context).copyWith(
                              fontSize: 22,
                            ),
                          ),
                        ),
                        _CountBadge(count: total),
                      ],
                    ),
                    const SizedBox(height: 6),
                    Text(
                      l10n.translate('patientsPageSubtitle') ??
                          'Manage patient records, communications and clinical data.',
                      style: AppDesignSystem.body2(context),
                    ),
                    const SizedBox(height: 12),
                    TextField(
                      controller: _searchCtrl,
                      decoration: InputDecoration(
                        hintText: l10n.translate('searchPatientsGlobalHint') ??
                            'Search patients, phone, ID...',
                        prefixIcon: const Icon(Icons.search, size: 20),
                        filled: true,
                        fillColor: AppDesignSystem.backgroundSecondary,
                        isDense: true,
                        contentPadding: const EdgeInsets.symmetric(
                          horizontal: 12,
                          vertical: 10,
                        ),
                        border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(14),
                          borderSide:
                              BorderSide(color: AppDesignSystem.border),
                        ),
                        enabledBorder: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(14),
                          borderSide:
                              BorderSide(color: AppDesignSystem.border),
                        ),
                        focusedBorder: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(14),
                          borderSide: BorderSide(color: brand, width: 1.5),
                        ),
                      ),
                    ),
                    const SizedBox(height: 10),
                    Row(
                      children: [
                        _FilterButton(
                          activeFilter: _statusFilter,
                          brand: brand,
                          onChanged: (value) => setState(() {
                            _statusFilter = value;
                            _page = 0;
                          }),
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          child: ShifaPrimaryButton(
                            onPressed: widget.onCreatePatient,
                            icon: Icons.person_add_outlined,
                            label: l10n.translate('newPatient') ?? 'New Patient',
                            width: ButtonWidth.fill,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 16),
                    _DirectoryTabs(
                      selected: _tab,
                      onChanged: _setTab,
                      brand: brand,
                    ),
                    const SizedBox(height: 12),
                    Row(
                      children: [
                        if (widget.onRefresh != null)
                          IconButton(
                            onPressed: widget.onRefresh,
                            icon: Icon(Icons.refresh, color: brand, size: 20),
                            tooltip: l10n.refresh,
                          ),
                        Expanded(
                          child: _SortButton(
                            sort: _sort,
                            brand: brand,
                            onChanged: (value) =>
                                setState(() => _sort = value),
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
            if (pageItems.isEmpty)
              SliverFillRemaining(
                hasScrollBody: false,
                child: Center(
                  child: Text(
                    l10n.noPatientsFound,
                    style: AppDesignSystem.body2(context),
                  ),
                ),
              )
            else
              SliverPadding(
                padding: const EdgeInsets.fromLTRB(12, 12, 12, 8),
                sliver: SliverList(
                  delegate: SliverChildBuilderDelegate(
                    (context, index) {
                      if (index.isOdd) return const SizedBox(height: 8);
                      final patient = pageItems[index ~/ 2];
                      return _PatientListRow(
                        patient: patient,
                        isSelected: widget.selectedId == patient.id,
                        brand: brand,
                        onTap: () => widget.onSelect(patient.id),
                      );
                    },
                    childCount: pageItems.length * 2 - 1,
                  ),
                ),
              ),
            SliverToBoxAdapter(
              child: _DirectoryPagination(
                start: start,
                end: end,
                total: visible,
                page: _page,
                totalPages: _totalPages,
                brand: brand,
                onPageChanged: (page) => setState(() => _page = page),
              ),
            ),
            SliverPadding(
              padding: EdgeInsets.only(
                bottom: Responsive.bottomNavClearance(context),
              ),
              sliver: const SliverToBoxAdapter(child: SizedBox.shrink()),
            ),
          ],
        ),
      );
    }

    return Container(
      decoration: AppDesignSystem.cardDecoration(
        borderOverride: Border.all(color: AppDesignSystem.border.withValues(alpha: 0.6)),
      ),
      clipBehavior: Clip.antiAlias,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 20, 20, 0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Expanded(
                      flex: 2,
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            children: [
                              Flexible(
                                child: Text(
                                  l10n.patients,
                                  style: AppDesignSystem.display(context).copyWith(
                                    fontSize: 24,
                                  ),
                                  overflow: TextOverflow.ellipsis,
                                ),
                              ),
                              const SizedBox(width: 10),
                              _CountBadge(count: total),
                            ],
                          ),
                          const SizedBox(height: 6),
                          Text(
                            l10n.translate('patientsPageSubtitle') ??
                                'Manage patient records, communications and clinical data.',
                            style: AppDesignSystem.body2(context),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(width: 16),
                    Expanded(
                      flex: 3,
                      child: Row(
                        children: [
                          Expanded(
                            child: TextField(
                              controller: _searchCtrl,
                              decoration: InputDecoration(
                                hintText: l10n.translate('searchPatientsGlobalHint') ??
                                    'Search patients, phone, ID...',
                                prefixIcon: const Icon(Icons.search, size: 20),
                                filled: true,
                                fillColor: AppDesignSystem.backgroundSecondary,
                                isDense: true,
                                contentPadding: const EdgeInsets.symmetric(
                                  horizontal: 12,
                                  vertical: 10,
                                ),
                                border: OutlineInputBorder(
                                  borderRadius: BorderRadius.circular(14),
                                  borderSide:
                                      BorderSide(color: AppDesignSystem.border),
                                ),
                                enabledBorder: OutlineInputBorder(
                                  borderRadius: BorderRadius.circular(14),
                                  borderSide:
                                      BorderSide(color: AppDesignSystem.border),
                                ),
                                focusedBorder: OutlineInputBorder(
                                  borderRadius: BorderRadius.circular(14),
                                  borderSide: BorderSide(color: brand, width: 1.5),
                                ),
                              ),
                            ),
                          ),
                          const SizedBox(width: 8),
                          _FilterButton(
                            activeFilter: _statusFilter,
                            brand: brand,
                            onChanged: (value) =>
                                setState(() {
                                  _statusFilter = value;
                                  _page = 0;
                                }),
                          ),
                          const SizedBox(width: 8),
                          ShifaPrimaryButton(
                            onPressed: widget.onCreatePatient,
                            icon: Icons.person_add_outlined,
                            label: l10n.translate('newPatient') ?? 'New Patient',
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 20),
                _DirectoryTabs(
                  selected: _tab,
                  onChanged: _setTab,
                  brand: brand,
                ),
                const SizedBox(height: 16),
                Row(
                  children: [
                    Expanded(
                      child: TextField(
                        controller: _searchCtrl,
                        decoration: InputDecoration(
                          hintText: l10n.translate('searchPatientsHint') ??
                              'Search patients...',
                          prefixIcon: const Icon(Icons.search, size: 20),
                          filled: true,
                          fillColor: AppDesignSystem.backgroundSecondary,
                          isDense: true,
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 12,
                            vertical: 10,
                          ),
                          border: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(14),
                            borderSide: BorderSide(color: AppDesignSystem.border),
                          ),
                          enabledBorder: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(14),
                            borderSide: BorderSide(color: AppDesignSystem.border),
                          ),
                          focusedBorder: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(14),
                            borderSide: BorderSide(color: brand, width: 1.5),
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(width: 10),
                    if (widget.onRefresh != null)
                      IconButton(
                        onPressed: widget.onRefresh,
                        icon: Icon(Icons.refresh, color: brand, size: 20),
                        tooltip: l10n.refresh,
                      ),
                    const SizedBox(width: 10),
                    _SortButton(
                      sort: _sort,
                      brand: brand,
                      onChanged: (value) => setState(() => _sort = value),
                    ),
                  ],
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),
          Expanded(
            child: pageItems.isEmpty
                ? Center(
                    child: Text(
                      l10n.noPatientsFound,
                      style: AppDesignSystem.body2(context),
                    ),
                  )
                : ListView.separated(
                    padding: const EdgeInsets.fromLTRB(12, 0, 12, 8),
                    itemCount: pageItems.length,
                    separatorBuilder: (_, __) => const SizedBox(height: 8),
                    itemBuilder: (context, index) {
                      final patient = pageItems[index];
                      return _PatientListRow(
                        patient: patient,
                        isSelected: widget.selectedId == patient.id,
                        brand: brand,
                        onTap: () => widget.onSelect(patient.id),
                      );
                    },
                  ),
          ),
          _DirectoryPagination(
            start: start,
            end: end,
            total: visible,
            page: _page,
            totalPages: _totalPages,
            brand: brand,
            onPageChanged: (page) => setState(() => _page = page),
          ),
        ],
      ),
    );
  }
}

class _CountBadge extends StatelessWidget {
  const _CountBadge({required this.count});

  final int count;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: AppDesignSystem.backgroundTertiary,
        borderRadius: BorderRadius.circular(20),
      ),
      child: Text(
        '$count ${l10n.translate('patientsCountLabel') ?? 'patients'}',
        style: AppDesignSystem.caption(context).copyWith(
          color: AppDesignSystem.textSecondary,
          fontWeight: FontWeight.w600,
        ),
      ),
    );
  }
}

class _DirectoryTabs extends StatelessWidget {
  const _DirectoryTabs({
    required this.selected,
    required this.onChanged,
    required this.brand,
  });

  final PatientListTab selected;
  final ValueChanged<PatientListTab> onChanged;
  final Color brand;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final tabs = <(PatientListTab, String)>[
      (PatientListTab.all, l10n.translate('allPatients') ?? 'All Patients'),
      (PatientListTab.recent, l10n.translate('recent') ?? 'Recent'),
      (PatientListTab.favorites, l10n.translate('favorites') ?? 'Favorites'),
      (PatientListTab.followUps, l10n.translate('followUps') ?? 'Follow-ups'),
    ];

    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      child: Row(
        children: tabs.map((entry) {
          final isActive = selected == entry.$1;
          return Padding(
            padding: const EdgeInsets.only(right: 20),
            child: InkWell(
              onTap: () => onChanged(entry.$1),
              borderRadius: BorderRadius.circular(8),
              child: Padding(
                padding: const EdgeInsets.symmetric(vertical: 6),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      entry.$2,
                      style: TextStyle(
                        fontSize: 14,
                        fontWeight: isActive ? FontWeight.w600 : FontWeight.w500,
                        color: isActive ? brand : AppDesignSystem.textSecondary,
                      ),
                    ),
                    const SizedBox(height: 6),
                    AnimatedContainer(
                      duration: const Duration(milliseconds: 180),
                      height: 2,
                      width: isActive ? 28 : 0,
                      decoration: BoxDecoration(
                        color: brand,
                        borderRadius: BorderRadius.circular(2),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          );
        }).toList(),
      ),
    );
  }
}

class _FilterButton extends StatelessWidget {
  const _FilterButton({
    required this.activeFilter,
    required this.brand,
    required this.onChanged,
  });

  final PatientStatusKind? activeFilter;
  final Color brand;
  final ValueChanged<PatientStatusKind?> onChanged;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return PopupMenuButton<PatientStatusKind?>(
      onSelected: onChanged,
      offset: const Offset(0, 40),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
        decoration: BoxDecoration(
          color: activeFilter != null
              ? brand.withValues(alpha: 0.1)
              : AppDesignSystem.backgroundSecondary,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(
            color: activeFilter != null ? brand : AppDesignSystem.border,
          ),
        ),
        child: Icon(
          Icons.filter_list,
          size: 20,
          color: activeFilter != null ? brand : AppDesignSystem.textSecondary,
        ),
      ),
      itemBuilder: (context) => [
        PopupMenuItem<PatientStatusKind?>(
          value: null,
          child: Text(l10n.translate('filterAllStatuses') ?? 'All statuses'),
        ),
        PopupMenuItem(
          value: PatientStatusKind.active,
          child: Text(l10n.translate('patientStatusActive') ?? 'Active'),
        ),
        PopupMenuItem(
          value: PatientStatusKind.atRisk,
          child: Text(l10n.translate('patientStatusAtRisk') ?? 'At Risk'),
        ),
        PopupMenuItem(
          value: PatientStatusKind.followUp,
          child: Text(l10n.translate('patientStatusFollowUp') ?? 'Follow-up'),
        ),
      ],
    );
  }
}

class _SortButton extends StatelessWidget {
  const _SortButton({
    required this.sort,
    required this.brand,
    required this.onChanged,
  });

  final PatientSortOption sort;
  final Color brand;
  final ValueChanged<PatientSortOption> onChanged;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return PopupMenuButton<PatientSortOption>(
      initialValue: sort,
      onSelected: onChanged,
      offset: const Offset(0, 40),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
        decoration: BoxDecoration(
          color: AppDesignSystem.backgroundSecondary,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: AppDesignSystem.border),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.sort, size: 18, color: brand),
            const SizedBox(width: 6),
            Text(
              l10n.translate('sort') ?? 'Sort',
              style: const TextStyle(
                fontSize: 13,
                fontWeight: FontWeight.w500,
              ),
            ),
            Icon(Icons.expand_more, size: 18, color: Colors.grey.shade600),
          ],
        ),
      ),
      itemBuilder: (context) => [
        PopupMenuItem(
          value: PatientSortOption.nameAsc,
          child: Text(l10n.translate('sortNameAsc') ?? 'Name (A–Z)'),
        ),
        PopupMenuItem(
          value: PatientSortOption.nameDesc,
          child: Text(l10n.translate('sortNameDesc') ?? 'Name (Z–A)'),
        ),
        PopupMenuItem(
          value: PatientSortOption.recent,
          child: Text(l10n.translate('sortRecent') ?? 'Recent activity'),
        ),
      ],
    );
  }
}

class _PatientListRow extends StatelessWidget {
  const _PatientListRow({
    required this.patient,
    required this.isSelected,
    required this.brand,
    required this.onTap,
  });

  final Patient patient;
  final bool isSelected;
  final Color brand;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final age = patientAge(patient);
    final gender = patientGenderLabel(patient, l10n);
    final phone = patientPhoneDisplay(patient);
    final status = patientStatus(patient, l10n);
    final subtitleParts = <String>[];
    if (age != null) subtitleParts.add('$age');
    if (gender != '—') subtitleParts.add(gender);
    final subtitle = subtitleParts.isEmpty
        ? phone
        : '${subtitleParts.join(', ')} · $phone';

    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(AppDesignSystem.cardRadiusSm),
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 180),
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
          decoration: BoxDecoration(
            color: isSelected
                ? AppColors.primaryTeal.withValues(alpha: 0.06)
                : Colors.white,
            borderRadius: BorderRadius.circular(AppDesignSystem.cardRadiusSm),
            border: Border.all(
              color: isSelected ? brand : AppDesignSystem.border.withValues(alpha: 0.7),
              width: isSelected ? 1.5 : 1,
            ),
            boxShadow: isSelected ? AppDesignSystem.cardShadow : null,
          ),
          child: Row(
            children: [
              _PatientAvatar(name: patient.name, photoUrl: patient.photoUrl, size: 22),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      patient.name,
                      style: const TextStyle(
                        fontSize: 15,
                        fontWeight: FontWeight.w600,
                        color: AppDesignSystem.textPrimary,
                      ),
                      overflow: TextOverflow.ellipsis,
                    ),
                    const SizedBox(height: 2),
                    Text(
                      subtitle,
                      style: AppDesignSystem.body2(context).copyWith(fontSize: 12),
                      overflow: TextOverflow.ellipsis,
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                decoration: BoxDecoration(
                  color: status.backgroundColor,
                  borderRadius: BorderRadius.circular(20),
                ),
                child: Text(
                  status.label,
                  style: TextStyle(
                    fontSize: 11,
                    fontWeight: FontWeight.w600,
                    color: status.textColor,
                  ),
                ),
              ),
              const SizedBox(width: 4),
              Icon(Icons.chevron_right, color: Colors.grey.shade500, size: 20),
            ],
          ),
        ),
      ),
    );
  }
}

class _DirectoryPagination extends StatelessWidget {
  const _DirectoryPagination({
    required this.start,
    required this.end,
    required this.total,
    required this.page,
    required this.totalPages,
    required this.brand,
    required this.onPageChanged,
  });

  final int start;
  final int end;
  final int total;
  final int page;
  final int totalPages;
  final Color brand;
  final ValueChanged<int> onPageChanged;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final label = (l10n.translate('patientsPagination') ??
            'Showing {{start}} to {{end}} of {{total}} patients')
        .replaceAll('{{start}}', '$start')
        .replaceAll('{{end}}', '$end')
        .replaceAll('{{total}}', '$total');

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      decoration: BoxDecoration(
        color: AppDesignSystem.backgroundSecondary.withValues(alpha: 0.5),
        border: Border(
          top: BorderSide(color: AppDesignSystem.border.withValues(alpha: 0.7)),
        ),
      ),
      child: Row(
        children: [
          Expanded(
            child: Text(
              label,
              style: AppDesignSystem.caption(context).copyWith(
                color: AppDesignSystem.textSecondary,
              ),
            ),
          ),
          IconButton(
            onPressed: page > 0 ? () => onPageChanged(page - 1) : null,
            icon: const Icon(Icons.chevron_left),
            iconSize: 20,
            color: brand,
          ),
          ...List.generate(totalPages.clamp(0, 5), (i) {
            final pageNum = _visiblePageNumber(page, totalPages, i);
            final isActive = pageNum == page;
            return Padding(
              padding: const EdgeInsets.symmetric(horizontal: 2),
              child: InkWell(
                onTap: () => onPageChanged(pageNum),
                borderRadius: BorderRadius.circular(8),
                child: Container(
                  width: 28,
                  height: 28,
                  alignment: Alignment.center,
                  decoration: BoxDecoration(
                    color: isActive ? brand : Colors.transparent,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Text(
                    '${pageNum + 1}',
                    style: TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.w600,
                      color: isActive ? Colors.white : AppDesignSystem.textSecondary,
                    ),
                  ),
                ),
              ),
            );
          }),
          if (totalPages > 5)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 4),
              child: Text('…', style: AppDesignSystem.caption(context)),
            ),
          IconButton(
            onPressed: page < totalPages - 1 ? () => onPageChanged(page + 1) : null,
            icon: const Icon(Icons.chevron_right),
            iconSize: 20,
            color: brand,
          ),
        ],
      ),
    );
  }

  int _visiblePageNumber(int current, int total, int slot) {
    if (total <= 5) return slot;
    if (current <= 2) return slot;
    if (current >= total - 3) return total - 5 + slot;
    return current - 2 + slot;
  }
}

class _PatientAvatar extends StatelessWidget {
  const _PatientAvatar({
    required this.name,
    this.photoUrl,
    this.size = 24,
  });

  final String name;
  final String? photoUrl;
  final double size;

  String get initials {
    final parts = name.trim().split(RegExp(r'\s+'));
    if (parts.isEmpty) return '?';
    if (parts.length == 1) {
      return parts.first.isNotEmpty
          ? parts.first.substring(0, 1).toUpperCase()
          : '?';
    }
    final first = parts.first.isNotEmpty ? parts.first.substring(0, 1) : '?';
    final last = parts.last.isNotEmpty ? parts.last.substring(0, 1) : '?';
    return (first + last).toUpperCase();
  }

  @override
  Widget build(BuildContext context) {
    final hasUrl = photoUrl != null && photoUrl!.isNotEmpty;
    return CircleAvatar(
      radius: size,
      backgroundColor: AppColors.secondaryLight,
      backgroundImage: hasUrl ? NetworkImage(photoUrl!) : null,
      child: hasUrl
          ? null
          : Text(
              initials,
              style: TextStyle(
                fontWeight: FontWeight.w700,
                color: AppColors.primaryTeal,
                fontSize: size * 0.72,
              ),
            ),
    );
  }
}
