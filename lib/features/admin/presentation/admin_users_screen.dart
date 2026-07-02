// lib/features/admin/presentation/admin_users_screen.dart
import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/state/admin/admin_providers.dart';
import 'package:shifa_doc_app_v1/state/admin/admin_provider_params.dart';
import 'package:shifa_doc_app_v1/features/admin/domain/admin_models.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';
import 'package:shifa_doc_app_v1/features/admin/presentation/admin_user_stats_panel.dart';
import 'package:shifa_doc_app_v1/core/theme/app_colors.dart';

class AdminUsersScreen extends ConsumerStatefulWidget {
  const AdminUsersScreen({super.key});

  @override
  ConsumerState<AdminUsersScreen> createState() => _AdminUsersScreenState();
}

class _AdminUsersScreenState extends ConsumerState<AdminUsersScreen> {
  int _currentPage = 0;
  String? _filterRole;
  bool? _filterEnabled;
  bool? _filterDeviceRegistered;
  String _searchQuery = '';
  final _searchController = TextEditingController();
  final _searchFocusNode = FocusNode();
  Timer? _debounceTimer;

  UsersProviderParams get _usersParams => UsersProviderParams(
        role: _filterRole,
        enabled: _filterEnabled,
        search: _searchQuery.isEmpty ? null : _searchQuery,
        deviceRegistered: _filterDeviceRegistered,
        page: _currentPage,
        size: 20,
      );

  void _applyQuickFilter({String? role, bool? deviceRegistered}) {
    setState(() {
      _filterRole = role;
      _filterDeviceRegistered = deviceRegistered;
      _currentPage = 0;
    });
  }

  void _refreshUsers() {
    ref.invalidate(adminUsersProvider(_usersParams));
    ref.invalidate(userManagementStatsProvider);
  }

  @override
  void initState() {
    super.initState();
    _searchController.addListener(_onSearchChanged);
  }

  void _onSearchChanged() {
    _debounceTimer?.cancel();
    _debounceTimer = Timer(const Duration(milliseconds: 400), () {
      if (mounted) _applySearch();
    });
  }

  @override
  void dispose() {
    _debounceTimer?.cancel();
    _searchController.removeListener(_onSearchChanged);
    _searchController.dispose();
    _searchFocusNode.dispose();
    super.dispose();
  }

  void _applySearch() {
    setState(() {
      _searchQuery = _searchController.text.trim();
      _currentPage = 0;
    });
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final usersAsync = ref.watch(adminUsersProvider(_usersParams));
    final statsAsync = ref.watch(userManagementStatsProvider);

    return Scaffold(
      backgroundColor: Colors.grey.shade50,
      appBar: AppBar(
        title: Text(l10n.translate('userManagement')),
        backgroundColor: Colors.white,
        elevation: 0,
        foregroundColor: Colors.black87,
      ),
      body: Column(
        children: [
          // Metrics overview
          Container(
            width: double.infinity,
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 0),
            color: Colors.grey.shade50,
            child: statsAsync.when(
              loading: () => const Padding(
                padding: EdgeInsets.symmetric(vertical: 24),
                child: Center(child: CircularProgressIndicator()),
              ),
              error: (_, __) => const SizedBox.shrink(),
              data: (stats) => AdminUserStatsPanel(
                stats: stats,
                onFilterPatients: () => _applyQuickFilter(role: 'PATIENT', deviceRegistered: null),
                onFilterPatientsWithDevice: () =>
                    _applyQuickFilter(role: 'PATIENT', deviceRegistered: true),
                onFilterPatientsWithoutDevice: () =>
                    _applyQuickFilter(role: 'PATIENT', deviceRegistered: false),
                onFilterDoctors: () => _applyQuickFilter(role: 'DOCTOR', deviceRegistered: null),
              ),
            ),
          ),
          // Search and filters
          Container(
            padding: const EdgeInsets.all(16),
            color: Colors.white,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Search bar
                Row(
                  children: [
                    Expanded(
                      child: TextField(
                        controller: _searchController,
                        focusNode: _searchFocusNode,
                        decoration: InputDecoration(
                          hintText: l10n.translate('searchUsersPlaceholder'),
                          prefixIcon: const Icon(Icons.search),
                          suffixIcon: _searchController.text.isNotEmpty
                              ? IconButton(
                                  icon: const Icon(Icons.clear),
                                  onPressed: () {
                                    _searchController.clear();
                                    _applySearch();
                                  },
                                )
                              : null,
                          border: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(8),
                          ),
                          contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                        ),
                        onChanged: (_) => setState(() {}),
                        onSubmitted: (_) => _applySearch(),
                      ),
                    ),
                    const SizedBox(width: 8),
                    ShifaPrimaryButton(
                      onPressed: _applySearch,
                      icon: Icons.search,
                      label: l10n.translate('search'),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                Row(
                  children: [
                    DropdownButton<String?>(
                      value: _filterRole,
                      hint: Text(l10n.translate('filterByRole')),
                      items: [
                        DropdownMenuItem(value: null, child: Text(l10n.translate('allRoles'))),
                        DropdownMenuItem(value: 'DOCTOR', child: Text(l10n.translate('doctors'))),
                        DropdownMenuItem(value: 'PATIENT', child: Text(l10n.patients)),
                        DropdownMenuItem(value: 'ADMIN', child: Text(l10n.translate('admins'))),
                      ],
                      onChanged: (v) => setState(() {
                        _filterRole = v;
                        _currentPage = 0;
                      }),
                    ),
                    const SizedBox(width: 16),
                    DropdownButton<bool?>(
                      value: _filterEnabled,
                      hint: Text(l10n.translate('filterByStatus')),
                      items: [
                        DropdownMenuItem(value: null, child: Text(l10n.all)),
                        DropdownMenuItem(value: true, child: Text(l10n.translate('enabled'))),
                        DropdownMenuItem(value: false, child: Text(l10n.translate('disabled'))),
                      ],
                      onChanged: (v) => setState(() {
                        _filterEnabled = v;
                        _currentPage = 0;
                      }),
                    ),
                    const SizedBox(width: 16),
                    DropdownButton<bool?>(
                      value: _filterDeviceRegistered,
                      hint: Text(l10n.translate('filterByDevice')),
                      items: [
                        DropdownMenuItem(value: null, child: Text(l10n.all)),
                        DropdownMenuItem(value: true, child: Text(l10n.translate('withDevice'))),
                        DropdownMenuItem(value: false, child: Text(l10n.translate('withoutDevice'))),
                      ],
                      onChanged: (v) => setState(() {
                        _filterDeviceRegistered = v;
                        _currentPage = 0;
                      }),
                    ),
                    const Spacer(),
                    ShifaPrimaryButton(
                      onPressed: _refreshUsers,
                      icon: Icons.refresh,
                      label: l10n.refresh,
                    ),
                  ],
                ),
              ],
            ),
          ),
          // Users List
          Expanded(
            child: usersAsync.when(
              loading: () => const Center(child: CircularProgressIndicator()),
              error: (e, _) => Center(child: Text('${l10n.error}: $e')),
              data: (data) {
                final users = data['content'] as List<AdminUser>;
                final totalPages = data['totalPages'] as int;
                final totalElements = data['totalElements'] as int;
                return Column(
                  children: [
                    if (totalElements > 0)
                      Padding(
                        padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
                        child: Align(
                          alignment: Alignment.centerLeft,
                          child: Text(
                            l10n.translate('showingUsersCount')
                                .replaceAll('{count}', users.length.toString())
                                .replaceAll('{total}', totalElements.toString()),
                            style: TextStyle(fontSize: 13, color: Colors.grey.shade600),
                          ),
                        ),
                      ),
                    Expanded(
                      child: users.isEmpty
                          ? Center(child: Text(l10n.translate('noUsersFound')))
                          : ListView.builder(
                              padding: const EdgeInsets.all(16),
                              itemCount: users.length,
                              itemBuilder: (context, i) => _UserCard(
                                user: users[i],
                                ref: ref,
                                usersParams: _usersParams,
                              ),
                            ),
                    ),
                    if (totalPages > 1)
                      Container(
                        padding: const EdgeInsets.all(16),
                        child: Row(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            IconButton(
                              icon: const Icon(Icons.chevron_left),
                              onPressed: _currentPage > 0
                                  ? () => setState(() => _currentPage--)
                                  : null,
                            ),
                            Text('${l10n.translate('page')} ${_currentPage + 1} ${l10n.translate('of')} $totalPages'),
                            IconButton(
                              icon: const Icon(Icons.chevron_right),
                              onPressed: _currentPage < totalPages - 1
                                  ? () => setState(() => _currentPage++)
                                  : null,
                            ),
                          ],
                        ),
                      ),
                  ],
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}

class _UserCard extends ConsumerWidget {
  final AdminUser user;
  final WidgetRef ref;
  final UsersProviderParams usersParams;

  const _UserCard({required this.user, required this.ref, required this.usersParams});

  // Format backend role enum to human-readable text
  String _formatRole(String role, AppLocalizations l10n) {
    switch (role.toUpperCase()) {
      case 'DOCTOR':
        return l10n.translate('doctor');
      case 'PATIENT':
        return l10n.translate('patient');
      case 'ADMIN':
        return l10n.translate('admin');
      default:
        // Fallback: capitalize first letter
        return role.isEmpty ? 'Unknown' : role[0].toUpperCase() + role.substring(1).toLowerCase();
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context)!;
    final name = user.displayName;
    final initial = name.isNotEmpty && name != 'User ${user.id}'
        ? name[0].toUpperCase()
        : (user.email?.isNotEmpty == true
            ? user.email![0].toUpperCase()
            : user.phone?.isNotEmpty == true
                ? '+'
                : '?');
    final secondaryStyle = Theme.of(context).textTheme.bodySmall?.copyWith(
          color: Colors.grey.shade700,
          fontSize: 13,
        );
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            CircleAvatar(
              child: Text(initial),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  // Name always on top of role
                  Padding(
                    padding: const EdgeInsets.only(bottom: 4),
                    child: Text(
                      name,
                      style: Theme.of(context).textTheme.titleMedium,
                      overflow: TextOverflow.ellipsis,
                      maxLines: 1,
                    ),
                  ),
                  Text(
                    _formatRole(user.role, l10n),
                    style: secondaryStyle,
                    overflow: TextOverflow.ellipsis,
                    maxLines: 1,
                  ),
                  const SizedBox(height: 2),
                  Text(
                    user.email?.isNotEmpty == true
                        ? user.email!
                        : l10n.translate('noEmail'),
                    style: secondaryStyle,
                    overflow: TextOverflow.ellipsis,
                    maxLines: 1,
                  ),
                  const SizedBox(height: 2),
                  Text(
                    user.phone?.isNotEmpty == true
                        ? user.phone!
                        : l10n.translate('noPhone'),
                    style: secondaryStyle,
                    overflow: TextOverflow.ellipsis,
                    maxLines: 1,
                  ),
                  if (user.lastLoginAt != null) ...[
                    const SizedBox(height: 4),
                    Text(
                      '${l10n.translate('lastLogin')}: ${_formatDateTime(user.lastLoginAt!)}',
                      style: TextStyle(fontSize: 12, color: Colors.grey.shade600),
                      overflow: TextOverflow.ellipsis,
                      maxLines: 1,
                    ),
                  ] else if (user.role == 'PATIENT' || user.role == 'DOCTOR') ...[
                    const SizedBox(height: 4),
                    Text(
                      l10n.translate('neverLoggedIn'),
                      style: TextStyle(fontSize: 12, color: Colors.orange.shade700),
                    ),
                  ],
                  if (user.createdAt != null) ...[
                    const SizedBox(height: 2),
                    Text(
                      '${l10n.translate('joined')}: ${_formatDateTime(user.createdAt!)}',
                      style: TextStyle(fontSize: 12, color: Colors.grey.shade600),
                    ),
                  ],
                ],
              ),
            ),
            const SizedBox(width: 8),
            Column(
              crossAxisAlignment: CrossAxisAlignment.end,
              mainAxisSize: MainAxisSize.min,
              children: [
                Wrap(
                  spacing: 6,
                  runSpacing: 6,
                  alignment: WrapAlignment.end,
                  children: [
                    if (user.role == 'PATIENT' || user.role == 'DOCTOR')
                      Chip(
                        avatar: Icon(
                          user.deviceRegistered ? Icons.phone_android : Icons.phone_disabled,
                          size: 14,
                          color: Colors.white,
                        ),
                        label: Text(
                          user.deviceRegistered
                              ? l10n.translate('withDevice')
                              : l10n.translate('withoutDevice'),
                          style: const TextStyle(color: Colors.white, fontSize: 11),
                        ),
                        backgroundColor: user.deviceRegistered ? Colors.teal : Colors.orange.shade700,
                        visualDensity: VisualDensity.compact,
                        materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
                      ),
                    if (user.isLocked)
                      Chip(
                        label: Text(l10n.translate('locked')),
                        backgroundColor: AppColors.destructiveRed,
                        labelStyle: const TextStyle(color: Colors.white, fontSize: 11),
                        visualDensity: VisualDensity.compact,
                        materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
                      ),
                    if (user.role != 'ADMIN') ...[
                      Chip(
                        label: Text(_tierLabel(user.subscriptionTier, l10n)),
                        backgroundColor: _tierColor(user.subscriptionTier),
                        labelStyle: const TextStyle(color: Colors.white, fontSize: 11),
                        visualDensity: VisualDensity.compact,
                        materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
                      ),
                    ],
                    Chip(
                      label: Text(user.enabled ? l10n.translate('enabled') : l10n.translate('disabled')),
                      backgroundColor: user.enabled ? Colors.green : Colors.red,
                      labelStyle: const TextStyle(color: Colors.white, fontSize: 11),
                      visualDensity: VisualDensity.compact,
                      materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
                    ),
                  ],
                ),
                PopupMenuButton(
              itemBuilder: (context) {
                // Build menu items list
                final items = <PopupMenuEntry>[
                  PopupMenuItem(
                    child: Row(
                      children: [
                        Icon(user.enabled ? Icons.block : Icons.check, size: 18),
                        const SizedBox(width: 8),
                        Text(user.enabled ? l10n.translate('disable') : l10n.translate('enable')),
                      ],
                    ),
                    onTap: () async {
                      try {
                        final actions = ref.read(adminActionsProvider);
                        await actions.setUserEnabled(user.id, !user.enabled);
                        ref.invalidate(adminUsersProvider(usersParams));
                      } catch (e) {
                        if (context.mounted) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(content: Text('${l10n.error}: $e')),
                          );
                        }
                      }
                    },
                  ),
                  if (user.isLocked)
                    PopupMenuItem(
                      child: Row(
                        children: [
                          const Icon(Icons.lock_open, size: 18),
                          const SizedBox(width: 8),
                          Text(l10n.translate('unlock')),
                        ],
                      ),
                      onTap: () async {
                        try {
                          final actions = ref.read(adminActionsProvider);
                          await actions.unlockUser(user.id);
                          ref.invalidate(adminUsersProvider(usersParams));
                        } catch (e) {
                          if (context.mounted) {
                            ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(content: Text('${l10n.error}: $e')),
                          );
                        }
                      }
                    },
                  ),
                  PopupMenuItem(
                    child: Row(
                      children: [
                        const Icon(Icons.lock_reset, size: 18),
                        const SizedBox(width: 8),
                        Text(l10n.translate('resetPassword')),
                      ],
                    ),
                    onTap: () async {
                      try {
                        final actions = ref.read(adminActionsProvider);
                        final tempPassword = await actions.resetUserPassword(user.id);
                        if (context.mounted) {
                          showDialog(
                            context: context,
                            builder: (context) => AlertDialog(
                              title: Text(l10n.translate('passwordReset')),
                              content: Column(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  Text(l10n.translate('temporaryPassword')),
                                  SelectableText(
                                    tempPassword,
                                    style: const TextStyle(
                                      fontFamily: 'monospace',
                                      fontSize: 18,
                                      fontWeight: FontWeight.bold,
                                    ),
                                  ),
                                  const SizedBox(height: 8),
                                  Text(
                                    l10n.translate('sharePasswordSecurely'),
                                    style: const TextStyle(fontSize: 12, color: Colors.grey),
                                  ),
                                ],
                              ),
                              actions: [
                                TextButton(
                                  onPressed: () => Navigator.pop(context),
                                  child: Text(l10n.close),
                                ),
                              ],
                            ),
                          );
                        }
                      } catch (e) {
                        if (context.mounted) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(content: Text('${l10n.error}: $e')),
                          );
                        }
                      }
                    },
                  ),
                  if (user.role != 'ADMIN')
                    PopupMenuItem(
                      child: Row(
                        children: [
                          const Icon(Icons.workspace_premium, size: 18, color: Colors.deepPurple),
                          const SizedBox(width: 8),
                          Text(l10n.translate('changeSubscriptionTier'),
                              style: const TextStyle(color: Colors.deepPurple)),
                        ],
                      ),
                      onTap: () => _showChangeTierDialog(context, ref, user, usersParams),
                    ),
                  PopupMenuItem(
                    child: Row(
                      children: [
                        Icon(Icons.logout, size: 18, color: Colors.orange),
                        const SizedBox(width: 8),
                        Text(l10n.translate('forceLogout'), style: const TextStyle(color: Colors.orange)),
                      ],
                    ),
                    onTap: () async {
                      try {
                        final actions = ref.read(adminActionsProvider);
                        await actions.forceLogout(user.id);
                        if (context.mounted) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(content: Text(l10n.translate('userLoggedOut'))),
                          );
                        }
                      } catch (e) {
                        if (context.mounted) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(content: Text('${l10n.error}: $e')),
                          );
                        }
                      }
                    },
                  ),
                  if (user.role != 'ADMIN')
                    PopupMenuItem(
                      child: Row(
                        children: [
                          const Icon(Icons.delete_forever, size: 18, color: Colors.red),
                          const SizedBox(width: 8),
                          Text(l10n.translate('deleteUser'), style: const TextStyle(color: Colors.red)),
                        ],
                      ),
                      onTap: () => _showDeleteUserConfirmation(context, ref, user, usersParams),
                    ),
                ];

                // Add "Reset Doctor Calendar" for doctors
                if (user.role == 'DOCTOR') {
                  debugPrint('AdminUsersScreen: Adding Reset Calendar menu for doctor user ${user.id}');
                  final doctorId = _doctorId(user);
                  items.add(
                    PopupMenuItem(
                      onTap: doctorId != null
                          ? () => _showResetCalendarConfirmation(context, ref, user, usersParams)
                          : () {
                              ScaffoldMessenger.of(context).showSnackBar(
                                SnackBar(
                                  content: Text(l10n.translate('doctorProfileIdNotFound')),
                                  backgroundColor: AppColors.destructiveRed,
                                ),
                              );
                            },
                      child: Row(
                        children: [
                          const Icon(Icons.calendar_today, size: 18),
                          const SizedBox(width: 8),
                          Text(l10n.translate('resetDoctorCalendar')),
                          if (doctorId == null)
                            const Padding(
                              padding: EdgeInsets.only(left: 8),
                              child: Icon(
                                Icons.warning,
                                size: 14,
                                color: Colors.orange,
                              ),
                            ),
                        ],
                      ),
                    ),
                  );
                } else {
                  debugPrint('AdminUsersScreen: User ${user.id} role is "${user.role}", not adding Reset Calendar menu');
                }

                return items;
              },
            ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  static String _formatDateTime(String iso) {
    final dt = DateTime.parse(iso).toLocal();
    return '${dt.year}-${dt.month.toString().padLeft(2, '0')}-${dt.day.toString().padLeft(2, '0')} '
        '${dt.hour.toString().padLeft(2, '0')}:${dt.minute.toString().padLeft(2, '0')}';
  }

  static String _tierLabel(String tier, AppLocalizations l10n) {
    switch (tier.toUpperCase()) {
      case 'BASIC':
        return l10n.translate('subscriptionTierBasic');
      case 'PRO':
        return l10n.translate('subscriptionTierPro');
      case 'PREMIUM':
        return l10n.translate('subscriptionTierPremium');
      default:
        return tier;
    }
  }

  static Color _tierColor(String tier) {
    switch (tier.toUpperCase()) {
      case 'BASIC':
        return Colors.blueGrey;
      case 'PRO':
        return Colors.indigo;
      case 'PREMIUM':
        return Colors.deepPurple;
      default:
        return Colors.grey;
    }
  }

  /// Shows a tier picker scoped by the user's role: doctors can pick any of
  /// BASIC/PRO/PREMIUM, patients are restricted to PRO/PREMIUM (the backend
  /// enforces the same rule and will return 400 if violated).
  static Future<void> _showChangeTierDialog(
    BuildContext context,
    WidgetRef ref,
    AdminUser user,
    UsersProviderParams usersParams,
  ) async {
    final l10n = AppLocalizations.of(context)!;
    final allowedTiers = user.role == 'PATIENT'
        ? const ['PRO', 'PREMIUM']
        : const ['BASIC', 'PRO', 'PREMIUM'];
    String selected = allowedTiers.contains(user.subscriptionTier.toUpperCase())
        ? user.subscriptionTier.toUpperCase()
        : allowedTiers.first;

    final picked = await showDialog<String>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setLocalState) => AlertDialog(
          title: Text(l10n.translate('changeSubscriptionTier')),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                l10n.translate('subscriptionTierDialogHint'),
                style: const TextStyle(fontSize: 13, color: Colors.black54),
              ),
              const SizedBox(height: 8),
              for (final tier in allowedTiers)
                RadioListTile<String>(
                  value: tier,
                  groupValue: selected,
                  onChanged: (v) => setLocalState(() => selected = v ?? selected),
                  title: Text(_tierLabel(tier, l10n)),
                  dense: true,
                ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(ctx).pop(),
              child: Text(l10n.cancel),
            ),
            ShifaPrimaryButton(
              onPressed: () => Navigator.of(ctx).pop(selected),
              label: l10n.save,
            ),
          ],
        ),
      ),
    );

    if (!context.mounted || picked == null) return;
    if (picked.toUpperCase() == user.subscriptionTier.toUpperCase()) return;

    try {
      final actions = ref.read(adminActionsProvider);
      await actions.setUserSubscriptionTier(user.id, picked);
      ref.invalidate(adminUsersProvider(usersParams));
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              '${l10n.translate('subscriptionTierUpdated')}: ${_tierLabel(picked, l10n)}',
            ),
          ),
        );
      }
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('${l10n.error}: $e')),
        );
      }
    }
  }

  static int? _doctorId(AdminUser user) {
    final p = user.profile;
    if (p == null) {
      debugPrint('AdminUsersScreen: User ${user.id} (${user.role}) has null profile');
      return null;
    }
    final id = p['doctorId'];
    debugPrint('AdminUsersScreen: User ${user.id} (${user.role}) profile keys: ${p.keys}, doctorId: $id (type: ${id.runtimeType})');
    if (id is int) return id;
    if (id is num) return id.toInt();
    return null;
  }

  static Future<void> _showResetCalendarConfirmation(
    BuildContext context,
    WidgetRef ref,
    AdminUser user,
    UsersProviderParams usersParams,
  ) async {
    final doctorId = _doctorId(user);
    if (doctorId == null) return;

    final l10n = AppLocalizations.of(context)!;
    final reason = await _collectActionReason(
      context: context,
      title: l10n.translate('resetDoctorCalendar'),
      message: l10n.translate('doctorCalendarResetConfirm'),
      confirmLabel: l10n.translate('confirmReset'),
    );

    if (!context.mounted || reason == null) return;

    try {
      final actions = ref.read(adminActionsProvider);
      await actions.resetDoctorCalendar(doctorId);
      ref.invalidate(adminUsersProvider(usersParams));
      if (context.mounted) {
        final l10n = AppLocalizations.of(context)!;
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('${l10n.translate('doctorCalendarResetSuccessfully')} (Reason: $reason)')),
        );
      }
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('${AppLocalizations.of(context)!.error}: $e')),
        );
      }
    }
  }

  static Future<void> _showDeleteUserConfirmation(
    BuildContext context,
    WidgetRef ref,
    AdminUser user,
    UsersProviderParams usersParams,
  ) async {
    final l10n = AppLocalizations.of(context)!;
    final reason = await _collectActionReason(
      context: context,
      title: l10n.translate('deleteUser'),
      message: l10n.translate('deleteUserConfirm'),
      confirmLabel: l10n.translate('deleteUser'),
    );

    if (!context.mounted || reason == null) return;

    try {
      final actions = ref.read(adminActionsProvider);
      await actions.deleteUser(user.id);
      ref.invalidate(adminUsersProvider(usersParams));
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('${l10n.translate('userDeleted')} (Reason: $reason)')),
        );
      }
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('${AppLocalizations.of(context)!.error}: $e')),
        );
      }
    }
  }

  static Future<String?> _collectActionReason({
    required BuildContext context,
    required String title,
    required String message,
    required String confirmLabel,
  }) async {
    final l10n = AppLocalizations.of(context)!;
    final ctrl = TextEditingController();
    final result = await showDialog<String>(
      context: context,
      barrierDismissible: false,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setLocalState) => AlertDialog(
          title: Text(title),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(message),
              const SizedBox(height: 12),
              TextField(
                controller: ctrl,
                minLines: 2,
                maxLines: 4,
                decoration: const InputDecoration(
                  labelText: 'Reason (required)',
                  border: OutlineInputBorder(),
                ),
                onChanged: (_) => setLocalState(() {}),
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(ctx).pop(),
              child: Text(l10n.cancel),
            ),
            ShifaPrimaryButton(
              onPressed: ctrl.text.trim().isEmpty ? null : () => Navigator.of(ctx).pop(ctrl.text.trim()),
              variant: ButtonVariant.destructive,
              label: confirmLabel,
            ),
          ],
        ),
      ),
    );
    ctrl.dispose();
    return result;
  }
}
