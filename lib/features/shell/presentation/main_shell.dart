// lib/features/shell/presentation/main_shell.dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:shifa_doc_app_v1/state/shell/shell_controller.dart';
import 'package:shifa_doc_app_v1/features/chat/presentation/chat_screen.dart';
import 'package:shifa_doc_app_v1/features/home/presentation/home_screen.dart';
import 'package:shifa_doc_app_v1/features/calendar/presentation/calendar_screen.dart';
import 'package:shifa_doc_app_v1/features/patients/presentation/patients_screen.dart';
import 'package:shifa_doc_app_v1/features/profile/presentation/profile_screen.dart';
import 'package:shifa_doc_app_v1/features/tasks/presentation/tasks_screen.dart';
import 'package:shifa_doc_app_v1/features/notifications/presentation/notifications_screen.dart';
import 'package:shifa_doc_app_v1/features/shell/presentation/shell_scope.dart';

import 'package:shifa_doc_app_v1/state/profile/profile_providers.dart';
import 'package:shifa_doc_app_v1/state/auth/auth_controller.dart';
import 'package:shifa_doc_app_v1/state/chat/chat_providers.dart';
import 'package:shifa_doc_app_v1/state/notifications/doctor_notifications_provider.dart';
import 'package:shifa_doc_app_v1/state/appointments/appointment_invalidation.dart';
import 'package:shifa_doc_app_v1/state/patients/patients_provider.dart';
import 'package:shifa_doc_app_v1/app/router.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/widgets/language_mini_toggle.dart';
import 'package:shifa_doc_app_v1/core/widgets/patient_briefing_panel.dart';

class MainShell extends ConsumerStatefulWidget {
  const MainShell({Key? key}) : super(key: key);

  @override
  ConsumerState<MainShell> createState() => _MainShellState();
}

class _MainShellState extends ConsumerState<MainShell> with WidgetsBindingObserver {
  final GlobalKey<NavigatorState> _shellNavKey = GlobalKey<NavigatorState>();

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      debugPrint('MainShell: App resumed');
      // Refresh notification lists/counts when app is resumed so sidebar badge
      // and notifications screen stay in sync with backend state.
      ref.invalidate(doctorNotificationsProvider);
      ref.invalidate(doctorNotificationsUnreadCountProvider);
      invalidateAppointmentRelatedProviders(ref);
    }
  }

  /// Pops the shell navigator back to tab content (if on a sub-page), then switches to the given tab.
  void _goToTab(int index) {
    _shellNavKey.currentState?.popUntil((route) => route.isFirst);
    ref.read(shellProvider.notifier).setTab(index);
  }

  @override
  Widget build(BuildContext context) {
    ref.listen<int?>(notificationPendingTaskIdProvider, (prev, next) {
      if (next == null || next <= 0) return;
      final taskId = next;
      ref.read(notificationPendingTaskIdProvider.notifier).state = null;
      WidgetsBinding.instance.addPostFrameCallback((_) {
        _shellNavKey.currentState?.pushNamed(
          AppRoutes.taskDetails,
          arguments: taskId,
        );
      });
    });

    // When shell is built with a pending task id already set (e.g. from notification tap),
    // ref.listen may not fire for that initial value — push task details once.
    final pendingTaskId = ref.watch(notificationPendingTaskIdProvider);
    if (pendingTaskId != null && pendingTaskId > 0) {
      ref.read(notificationPendingTaskIdProvider.notifier).state = null;
      WidgetsBinding.instance.addPostFrameCallback((_) {
        _shellNavKey.currentState?.pushNamed(
          AppRoutes.taskDetails,
          arguments: pendingTaskId,
        );
      });
    }

    final brand = Theme.of(context).colorScheme.primary;
    final selectedIndex = ref.watch(shellProvider);

    final screens = const [
      _KeepAlive(child: ChatScreen()),
      _KeepAlive(child: HomeScreen()),
      _KeepAlive(child: CalendarScreen()),
      _KeepAlive(child: PatientsScreen()),
      _KeepAlive(child: TasksScreen()),
      _KeepAlive(child: NotificationsScreen()),
      _KeepAlive(child: ProfileScreen()),
    ];

    final allAsync = ref.watch(profileAllProvider);
    final photoCacheBuster = ref.watch(photoCacheBusterProvider);
    String? avatarUrl;
    allAsync.when(
      data: (all) {
        final raw = all.profile['photoUrl'] as String?;
        avatarUrl = (raw != null && raw.isNotEmpty)
            ? '$raw${raw.contains('?') ? '&' : '?'}t=$photoCacheBuster'
            : null;
      },
      loading: () {},
      error: (_, __) {},
    );

    return Scaffold(
      body: Row(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // ───────────────── Sidebar ─────────────────
          Container(
            width: 80,
            decoration: BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
                colors: [brand, brand.withOpacity(0.85)],
              ),
            ),
            child: SafeArea(
              child: SingleChildScrollView(
                padding: const EdgeInsets.only(bottom: 16),
                child: Column(
                  children: [
                    const SizedBox(height: 16),

                // ───── Logo + Divider (Option 2) ─────
                InkWell(
                  onTap: () => _goToTab(1),
                  borderRadius: BorderRadius.circular(12),
                  child: Padding(
                    padding: const EdgeInsets.all(8),
                    child: Image.asset(
                      'assets/branding/shifa_logo.png',
                      width: 60,
                      height: 60,
                      fit: BoxFit.contain,
                    ),
                  ),
                ),

                const SizedBox(height: 12),

                Container(
                  width: 32,
                  height: 1,
                  color: Colors.white.withOpacity(0.35),
                ),

                    const SizedBox(height: 32),

                // ───── Tabs ─────
                    _buildChatNavItem(
                  context,
                  ref,
                  0,
                  brand,
                  selectedIndex,
                ),
                    const SizedBox(height: 20),

                    _buildNavItem(
                  context,
                  ref,
                  Icons.home_outlined,
                  1,
                  brand,
                  selectedIndex,
                ),
                    const SizedBox(height: 20),

                    _buildNavItem(
                  context,
                  ref,
                  Icons.calendar_today_outlined,
                  2,
                  brand,
                  selectedIndex,
                ),
                    const SizedBox(height: 20),

                    _buildNavItem(
                  context,
                  ref,
                  Icons.people_outline,
                  3,
                  brand,
                  selectedIndex,
                ),
                    const SizedBox(height: 20),

                _buildNavItem(
                  context,
                  ref,
                  Icons.task_alt,
                  4,
                  brand,
                  selectedIndex,
                ),
                const SizedBox(height: 20),

                    _buildNotificationsNavItem(
                  context,
                  ref,
                  5,
                  brand,
                  selectedIndex,
                ),

                    const SizedBox(height: 24),

                    // ───── Language Toggle ─────
                    const LanguageMiniToggle(),

                    const SizedBox(height: 16),

                    // ───── Profile Avatar ─────
                    _buildNavAvatarItem(
                      context,
                      ref,
                      avatarUrl,
                      6,
                      brand,
                      selectedIndex,
                    ),

                    const SizedBox(height: 12),

                    // ───── Logout ─────
                    _LogoutButton(brand: brand),
                  ],
                ),
              ),
            ),
          ),

          // ───────────────── Content (nested Navigator so sidebar stays visible) ─────────────────
          Expanded(
            child: Stack(
              children: [
                ShellScope(
                  navigatorKey: _shellNavKey,
                  child: Navigator(
                key: _shellNavKey,
                initialRoute: '/',
                onGenerateRoute: (RouteSettings settings) {
                  final name = settings.name;
                  if (name == null || name.isEmpty || name == '/') {
                    return MaterialPageRoute<void>(
                      builder: (_) => _ShellTabContent(screens: screens),
                    );
                  }
                  final route = AppRouter.shellOnGenerateRoute(settings);
                  if (route != null) return route;
                  return MaterialPageRoute<void>(
                    builder: (_) => _ShellTabContent(screens: screens),
                  );
                },
              ),
            ),
                const Positioned(
                  right: 0,
                  bottom: 0,
                  child: PatientBriefingPanel(),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildNavItem(
    BuildContext context,
    WidgetRef ref,
    IconData icon,
    int index,
    Color brand,
    int selectedIndex,
  ) {
    final isSelected = selectedIndex == index;
    return InkWell(
      onTap: () => _goToTab(index),
      borderRadius: BorderRadius.circular(12),
      child: Container(
        width: 48,
        height: 48,
        decoration: BoxDecoration(
          color: isSelected ? Colors.white : Colors.transparent,
          borderRadius: BorderRadius.circular(12),
        ),
        alignment: Alignment.center,
        child: Icon(icon, color: isSelected ? brand : Colors.white, size: 28),
      ),
    );
  }

  Widget _buildChatNavItem(
    BuildContext context,
    WidgetRef ref,
    int index,
    Color brand,
    int selectedIndex,
  ) {
    final isSelected = selectedIndex == index;
    final unreadAsync = ref.watch(unreadCountProvider);
    
    return InkWell(
      onTap: () => _goToTab(index),
      borderRadius: BorderRadius.circular(12),
      child: Container(
        width: 48,
        height: 48,
        decoration: BoxDecoration(
          color: isSelected ? Colors.white : Colors.transparent,
          borderRadius: BorderRadius.circular(12),
        ),
        alignment: Alignment.center,
        child: Stack(
          clipBehavior: Clip.none,
          children: [
            Icon(
              Icons.chat_bubble_outline,
              color: isSelected ? brand : Colors.white,
              size: 28,
            ),
            unreadAsync.when(
              data: (count) {
                if (count > 0) {
                  return Positioned(
                    right: -4,
                    top: -4,
                    child: Container(
                      padding: const EdgeInsets.all(4),
                      decoration: BoxDecoration(
                        color: Colors.red,
                        shape: BoxShape.circle,
                        border: Border.all(color: Colors.white, width: 2),
                      ),
                      constraints: const BoxConstraints(
                        minWidth: 16,
                        minHeight: 16,
                      ),
                      child: Center(
                        child: Text(
                          count > 99 ? '99+' : count.toString(),
                          style: const TextStyle(
                            color: Colors.white,
                            fontSize: 10,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ),
                    ),
                  );
                }
                return const SizedBox.shrink();
              },
              loading: () => const SizedBox.shrink(),
              error: (_, __) => const SizedBox.shrink(),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildNotificationsNavItem(
    BuildContext context,
    WidgetRef ref,
    int index,
    Color brand,
    int selectedIndex,
  ) {
    final isSelected = selectedIndex == index;
    final unreadAsync = ref.watch(doctorNotificationsUnreadCountProvider);

    return InkWell(
      onTap: () => _goToTab(index),
      borderRadius: BorderRadius.circular(12),
      child: Container(
        width: 48,
        height: 48,
        decoration: BoxDecoration(
          color: isSelected ? Colors.white : Colors.transparent,
          borderRadius: BorderRadius.circular(12),
        ),
        alignment: Alignment.center,
        child: Stack(
          clipBehavior: Clip.none,
          children: [
            Icon(
              Icons.notifications_outlined,
              color: isSelected ? brand : Colors.white,
              size: 28,
            ),
            unreadAsync.when(
              data: (count) {
                if (count > 0) {
                  return Positioned(
                    right: -4,
                    top: -4,
                    child: Container(
                      padding: const EdgeInsets.all(4),
                      decoration: BoxDecoration(
                        color: Colors.red,
                        shape: BoxShape.circle,
                        border: Border.all(color: Colors.white, width: 2),
                      ),
                      constraints: const BoxConstraints(
                        minWidth: 16,
                        minHeight: 16,
                      ),
                      child: Center(
                        child: Text(
                          count > 99 ? '99+' : count.toString(),
                          style: const TextStyle(
                            color: Colors.white,
                            fontSize: 10,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ),
                    ),
                  );
                }
                return const SizedBox.shrink();
              },
              loading: () => const SizedBox.shrink(),
              error: (_, __) => const SizedBox.shrink(),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildNavAvatarItem(
    BuildContext context,
    WidgetRef ref,
    String? photoUrl,
    int index,
    Color brand,
    int selectedIndex,
  ) {
    final isSelected = selectedIndex == index;
    final hasPhoto = photoUrl != null && photoUrl.isNotEmpty;
    final photoUrlNonNull = photoUrl;

    return InkWell(
      onTap: () => _goToTab(index),
      borderRadius: BorderRadius.circular(12),
      child: Container(
        width: 48,
        height: 48,
        decoration: BoxDecoration(
          color: isSelected ? Colors.white : Colors.transparent,
          borderRadius: BorderRadius.circular(12),
        ),
        alignment: Alignment.center,
        child: CircleAvatar(
          radius: 16,
          backgroundColor: isSelected
              ? brand.withOpacity(0.12)
              : Colors.white.withOpacity(0.20),
          backgroundImage: hasPhoto && photoUrlNonNull != null ? NetworkImage(photoUrlNonNull) : null,
          child: hasPhoto
              ? null
              : Icon(
                  Icons.person,
                  color: isSelected ? brand : Colors.white,
                  size: 22,
                ),
        ),
      ),
    );
  }
}

class _LogoutButton extends ConsumerWidget {
  final Color brand;
  const _LogoutButton({required this.brand});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context)!;
    return Tooltip(
      message: l10n.signOut,
      waitDuration: const Duration(milliseconds: 250),
      child: InkWell(
        onTap: () {
          showDialog(
            context: context,
            builder: (BuildContext dialogContext) {
              return AlertDialog(
                title: Text(l10n.signOut),
                content: Text(l10n.signOutConfirm),
                actions: [
                  TextButton(
                    onPressed: () => Navigator.of(dialogContext).pop(),
                    child: Text(l10n.cancel),
                  ),
                  TextButton(
                    onPressed: () {
                      Navigator.of(dialogContext).pop();
                      ref.read(authProvider.notifier).logout();
                      ref.invalidate(profileAllProvider);
                      ref.invalidate(shellProvider);
                      // Clear doctor-scoped data so next login never sees stale appointments/patients
                      invalidateAppointmentRelatedProviders(ref);
                      ref.invalidate(patientsProvider);
                      Navigator.pushNamedAndRemoveUntil(
                        context,
                        AppRoutes.login,
                        (_) => false,
                      );
                    },
                    style: TextButton.styleFrom(
                      foregroundColor: Colors.red,
                    ),
                    child: Text(l10n.signOut),
                  ),
                ],
              );
            },
          );
        },
        borderRadius: BorderRadius.circular(12),
        child: Container(
          width: 48,
          height: 48,
          decoration: BoxDecoration(
            color: Colors.white.withOpacity(0.15),
            borderRadius: BorderRadius.circular(12),
          ),
          alignment: Alignment.center,
          child: const Icon(Icons.logout, color: Colors.white, size: 24),
        ),
      ),
    );
  }
}

/// Default shell content: tab body. Watches shellProvider so tab switches update the visible screen.
class _ShellTabContent extends ConsumerWidget {
  const _ShellTabContent({required this.screens});

  final List<Widget> screens;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final selectedIndex = ref.watch(shellProvider);
    return IndexedStack(index: selectedIndex, children: screens);
  }
}

class _KeepAlive extends StatefulWidget {
  final Widget child;
  const _KeepAlive({required this.child});

  @override
  State<_KeepAlive> createState() => _KeepAliveState();
}

class _KeepAliveState extends State<_KeepAlive>
    with AutomaticKeepAliveClientMixin {
  @override
  bool get wantKeepAlive => true;

  @override
  Widget build(BuildContext context) {
    super.build(context);
    return widget.child;
  }
}
