// lib/features/admin/presentation/admin_shell.dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter/services.dart';
import 'package:shifa_doc_app_v1/app/router.dart';
import 'package:shifa_doc_app_v1/core/api/api_providers.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/state/auth/auth_controller.dart';
import 'package:shifa_doc_app_v1/core/theme/app_colors.dart';
import 'admin_dashboard_screen.dart';
import 'admin_payments_screen.dart';
import 'admin_tokens_screen.dart';
import 'admin_users_screen.dart';
import 'admin_create_admin_screen.dart';
import 'admin_audit_logs_screen.dart';
import 'admin_config_screen.dart';
import 'admin_deleted_patients_screen.dart';
import 'admin_clinics_screen.dart';
import 'admin_doctor_activity_screen.dart';

class AdminShell extends ConsumerStatefulWidget {
  const AdminShell({super.key});

  @override
  ConsumerState<AdminShell> createState() => _AdminShellState();
}

class _AdminShellState extends ConsumerState<AdminShell> {
  int _selectedIndex = 0;
  bool _checkedAdminSession = false;

  @override
  void initState() {
    super.initState();
    // Ensure API client uses only a valid admin token from admin storage.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _verifyAdminSessionOrRedirect();
    });
  }

  Future<void> _verifyAdminSessionOrRedirect() async {
    final restored = await ref.read(authProvider.notifier).restoreSession(forAdmin: true);
    if (!mounted) return;
    if (!restored) {
      Navigator.pushReplacementNamed(context, AppRoutes.adminLogin);
      return;
    }
    try {
      final api = ref.read(adminApiClientProvider);
      final res = await api.get('/api/admin/dashboard/stats');
      if (res.statusCode != 200) {
        ref.read(authProvider.notifier).logout(adminOnly: true);
        if (!mounted) return;
        Navigator.pushReplacementNamed(context, AppRoutes.adminLogin);
        return;
      }
    } catch (_) {
      ref.read(authProvider.notifier).logout(adminOnly: true);
      if (!mounted) return;
      Navigator.pushReplacementNamed(context, AppRoutes.adminLogin);
      return;
    }
    if (mounted) setState(() => _checkedAdminSession = true);
  }

  final _screens = const [
    AdminDashboardScreen(),
    AdminDoctorActivityScreen(),
    AdminPaymentsScreen(),
    AdminTokensScreen(),
    AdminUsersScreen(),
    AdminClinicsScreen(),
    AdminCreateAdminScreen(),
    AdminAuditLogsScreen(),
    AdminConfigScreen(),
    AdminDeletedPatientsScreen(),
  ];

  final List<_AdminNavItem> _navItems = const [
    _AdminNavItem(icon: Icons.dashboard, label: 'Dashboard', index: 0),
    _AdminNavItem(icon: Icons.monitor_heart_outlined, label: 'Doctor Activity', index: 1),
    _AdminNavItem(icon: Icons.payments_outlined, label: 'Payments Ops', index: 2),
    _AdminNavItem(icon: Icons.vpn_key, label: 'Tokens', index: 3),
    _AdminNavItem(icon: Icons.people, label: 'Users', index: 4),
    _AdminNavItem(icon: Icons.local_hospital_outlined, label: 'Clinics', index: 5),
    _AdminNavItem(icon: Icons.person_add, label: 'Create admin', index: 6),
    _AdminNavItem(icon: Icons.history, label: 'Audit Logs', index: 7),
    _AdminNavItem(icon: Icons.settings, label: 'Settings', index: 8),
    _AdminNavItem(icon: Icons.privacy_tip, label: 'Deleted Patients', index: 9),
  ];

  @override
  Widget build(BuildContext context) {
    if (!_checkedAdminSession) {
      return const Scaffold(
        body: Center(child: CircularProgressIndicator()),
      );
    }
    return Shortcuts(
      shortcuts: <ShortcutActivator, Intent>{
        const SingleActivator(LogicalKeyboardKey.keyK, control: true): const _OpenCommandPaletteIntent(),
        const SingleActivator(LogicalKeyboardKey.keyK, meta: true): const _OpenCommandPaletteIntent(),
      },
      child: Actions(
        actions: <Type, Action<Intent>>{
          _OpenCommandPaletteIntent: CallbackAction<_OpenCommandPaletteIntent>(
            onInvoke: (_) {
              _showCommandPalette(context);
              return null;
            },
          ),
        },
        child: Scaffold(
          body: Row(
            children: [
              // Sidebar
              Container(
                width: 240,
                decoration: BoxDecoration(
                  color: Colors.grey.shade900,
                  border: Border(
                    right: BorderSide(color: Colors.grey.shade800, width: 1),
                  ),
                ),
                child: Column(
                  children: [
                    // Header
                    Container(
                      padding: const EdgeInsets.all(20),
                      decoration: BoxDecoration(
                        border: Border(
                          bottom: BorderSide(color: Colors.grey.shade800, width: 1),
                        ),
                      ),
                      child: Row(
                        children: [
                          Image.asset(
                            'assets/branding/shifa_logo.png',
                            width: 40,
                            height: 40,
                          ),
                          const SizedBox(width: 12),
                          const Text(
                            'Admin Panel',
                            style: TextStyle(
                              color: Colors.white,
                              fontSize: 18,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ],
                      ),
                    ),
                    // Navigation
                    Expanded(
                      child: ListView(
                        padding: const EdgeInsets.symmetric(vertical: 8),
                        children: [
                          ..._navItems.map((item) => _buildNavItem(item.icon, item.label, item.index)),
                          const SizedBox(height: 8),
                          ListTile(
                            leading: const Icon(Icons.search, color: Colors.white70),
                            title: const Text(
                              'Command Palette',
                              style: TextStyle(color: Colors.white70),
                            ),
                            subtitle: const Text(
                              'Ctrl/Cmd + K',
                              style: TextStyle(color: Colors.white38, fontSize: 12),
                            ),
                            onTap: () => _showCommandPalette(context),
                          ),
                        ],
                      ),
                    ),
                    // Logout
                    Container(
                      padding: const EdgeInsets.all(16),
                      decoration: BoxDecoration(
                        border: Border(
                          top: BorderSide(color: Colors.grey.shade800, width: 1),
                        ),
                      ),
                      child: ListTile(
                        leading: const Icon(Icons.logout, color: AppColors.destructiveRed),
                        title: Text(AppLocalizations.of(context)!.translate('logout'), style: const TextStyle(color: AppColors.destructiveRed)),
                        onTap: () => _showLogoutConfirmation(context),
                      ),
                    ),
                  ],
                ),
              ),
              // Main content
              Expanded(
                child: _screens[_selectedIndex],
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildNavItem(IconData icon, String label, int index) {
    final isSelected = _selectedIndex == index;
    return InkWell(
      onTap: () => setState(() => _selectedIndex = index),
      child: Container(
        margin: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        decoration: BoxDecoration(
          color: isSelected ? Colors.grey.shade800 : Colors.transparent,
          borderRadius: BorderRadius.circular(8),
        ),
        child: Row(
          children: [
            Icon(icon, color: isSelected ? Colors.white : Colors.grey.shade400),
            const SizedBox(width: 12),
            Text(
              label,
              style: TextStyle(
                color: isSelected ? Colors.white : Colors.grey.shade400,
                fontWeight: isSelected ? FontWeight.w600 : FontWeight.normal,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _showLogoutConfirmation(BuildContext context) async {
    final l10n = AppLocalizations.of(context)!;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(l10n.translate('logout')),
        content: Text(l10n.translate('confirmLogout')),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(false),
            child: Text(l10n.translate('no')),
          ),
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(true),
            child: Text(l10n.translate('yes')),
          ),
        ],
      ),
    );
    if (confirmed == true && context.mounted) {
      ref.read(authProvider.notifier).logout(adminOnly: true);
      Navigator.pushReplacementNamed(context, AppRoutes.adminLogin);
    }
  }

  Future<void> _showCommandPalette(BuildContext context) async {
    final queryCtrl = TextEditingController();
    await showDialog<void>(
      context: context,
      builder: (ctx) {
        return StatefulBuilder(
          builder: (ctx, setLocalState) {
            final query = queryCtrl.text.trim().toLowerCase();
            final filtered = _navItems
                .where((it) => it.label.toLowerCase().contains(query))
                .toList(growable: false);
            return AlertDialog(
              title: const Text('Command Palette'),
              content: SizedBox(
                width: 520,
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    TextField(
                      controller: queryCtrl,
                      autofocus: true,
                      decoration: const InputDecoration(
                        hintText: 'Type a page name...',
                        prefixIcon: Icon(Icons.search),
                      ),
                      onChanged: (_) => setLocalState(() {}),
                    ),
                    const SizedBox(height: 12),
                    Flexible(
                      child: filtered.isEmpty
                          ? const Padding(
                              padding: EdgeInsets.symmetric(vertical: 16),
                              child: Text('No matching commands'),
                            )
                          : ListView.builder(
                              shrinkWrap: true,
                              itemCount: filtered.length,
                              itemBuilder: (_, i) {
                                final item = filtered[i];
                                return ListTile(
                                  dense: true,
                                  leading: Icon(item.icon),
                                  title: Text(item.label),
                                  trailing: Text('Alt+${item.index + 1}'),
                                  onTap: () {
                                    setState(() => _selectedIndex = item.index);
                                    Navigator.of(ctx).pop();
                                  },
                                );
                              },
                            ),
                    ),
                  ],
                ),
              ),
            );
          },
        );
      },
    );
    queryCtrl.dispose();
  }
}

class _OpenCommandPaletteIntent extends Intent {
  const _OpenCommandPaletteIntent();
}

class _AdminNavItem {
  final IconData icon;
  final String label;
  final int index;

  const _AdminNavItem({
    required this.icon,
    required this.label,
    required this.index,
  });
}
