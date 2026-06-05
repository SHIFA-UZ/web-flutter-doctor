// lib/features/shell/presentation/main_shell.dart
import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:shifa_doc_app_v1/state/shell/shell_controller.dart';
import 'package:shifa_doc_app_v1/features/home/presentation/widgets/ask_shifa_ai_overlay.dart';
import 'package:shifa_doc_app_v1/features/home/presentation/widgets/home_search_overlay.dart';
import 'package:shifa_doc_app_v1/features/chat/presentation/chat_screen.dart';
import 'package:shifa_doc_app_v1/state/tasks/tasks_provider.dart';
import 'package:shifa_doc_app_v1/features/tasks/domain/task_models.dart';
import 'package:shifa_doc_app_v1/features/home/presentation/home_screen.dart';
import 'package:shifa_doc_app_v1/features/calendar/presentation/calendar_screen.dart';
import 'package:shifa_doc_app_v1/features/patients/presentation/patients_screen.dart';
import 'package:shifa_doc_app_v1/features/profile/presentation/profile_screen.dart';
import 'package:shifa_doc_app_v1/features/tasks/presentation/tasks_screen.dart';
import 'package:shifa_doc_app_v1/features/notifications/presentation/notifications_screen.dart';
import 'package:shifa_doc_app_v1/features/reports/presentation/reports_screen.dart';
import 'package:shifa_doc_app_v1/features/clinic/presentation/clinic_workspace_screen.dart';
import 'package:shifa_doc_app_v1/features/shell/domain/doctor_shell_tab.dart';
import 'package:shifa_doc_app_v1/features/shell/presentation/shell_scope.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_providers.dart';

import 'package:shifa_doc_app_v1/state/profile/profile_providers.dart';
import 'package:shifa_doc_app_v1/state/auth/doctor_jwt_role_provider.dart';
import 'package:shifa_doc_app_v1/state/auth/auth_controller.dart';
import 'package:shifa_doc_app_v1/state/chat/chat_providers.dart';
import 'package:shifa_doc_app_v1/state/notifications/doctor_notifications_provider.dart';
import 'package:shifa_doc_app_v1/state/appointments/appointment_invalidation.dart';
import 'package:shifa_doc_app_v1/core/subscription/doctor_subscription.dart';
import 'package:shifa_doc_app_v1/state/subscription/doctor_subscription_provider.dart';
import 'package:shifa_doc_app_v1/state/locations/doctor_location_actions.dart';
import 'package:shifa_doc_app_v1/state/locations/doctor_location_models.dart';
import 'package:shifa_doc_app_v1/state/patients/patients_provider.dart';
import 'package:shifa_doc_app_v1/app/router.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/utils/timezone_utils.dart';
import 'package:shifa_doc_app_v1/core/widgets/language_mini_toggle.dart';
import 'package:shifa_doc_app_v1/core/widgets/patient_briefing_panel.dart';
import 'package:shifa_doc_app_v1/state/calendar/calendar_controller.dart';
import 'package:shifa_doc_app_v1/features/calendar/domain/calendar_models.dart';
import 'package:shifa_doc_app_v1/features/auth/presentation/clinic_staff_web_only_screen.dart';
import 'package:shifa_doc_app_v1/core/layout/platform_layout.dart';
import 'package:shifa_doc_app_v1/core/layout/responsive.dart';

class MainShell extends ConsumerStatefulWidget {
  const MainShell({Key? key}) : super(key: key);

  @override
  ConsumerState<MainShell> createState() => _MainShellState();
}

class _MainShellState extends ConsumerState<MainShell> with WidgetsBindingObserver {
  // Use the top-level [shellNavigatorKey] from shell_scope.dart so deep-link
  // handlers in app.dart (notification taps, FCM, ...) can push routes
  // straight into the shell's nested navigator and keep the sidebar visible.
  GlobalKey<NavigatorState> get _shellNavKey => shellNavigatorKey;
  String? _activeLocationLabel;
  Timer? _sidebarLocationTicker;

  String _activeLocationPrefKey() {
    final profile = ref.read(profileAllProvider).valueOrNull?.profile;
    final doctorId = (profile?['id'] ?? profile?['doctorId'] ?? 'unknown')
        .toString();
    return 'active_location_id:$doctorId';
  }

  String? _deriveCurrentLocationLabel({
    required List<CalendarEntry> entries,
    required DateTime nowInDoctorZone,
    required AppLocalizations? l10n,
  }) {
    int toMinutes(TimeOfDay t) => t.hour * 60 + t.minute;
    final nowMinutes = nowInDoctorZone.hour * 60 + nowInDoctorZone.minute;
    final dated = entries.where((e) => e.location.trim().isNotEmpty).toList()
      ..sort((a, b) => toMinutes(a.start).compareTo(toMinutes(b.start)));

    for (final entry in dated) {
      final start = toMinutes(entry.start);
      final end = toMinutes(entry.end);
      if (nowMinutes >= start && nowMinutes < end) {
        return _normalizeLocationLabel(entry.location, l10n);
      }
    }

    for (final entry in dated) {
      if (toMinutes(entry.start) >= nowMinutes) {
        return _normalizeLocationLabel(entry.location, l10n);
      }
    }

    if (dated.isNotEmpty) {
      return _normalizeLocationLabel(dated.last.location, l10n);
    }
    return null;
  }

  String _normalizeLocationLabel(String raw, AppLocalizations? l10n) {
    final trimmed = raw.trim();
    if (trimmed.isEmpty) return trimmed;
    if (trimmed.toLowerCase().contains('video')) {
      return l10n?.videoCall ?? 'Video call';
    }
    return trimmed;
  }

  /// Sidebar shows practice location OR today's contextual slot (may be video).
  bool _sidebarLocationChipIsVideo(BuildContext context) {
    final label = _activeLocationLabel;
    if (label == null || label.trim().isEmpty) return false;
    if (label.toLowerCase().contains('video')) return true;
    final l10n = AppLocalizations.of(context);
    return l10n != null && label == l10n.videoCall;
  }

  Future<void> _loadActiveLocationLabel() async {
    if (!mounted) return;
    if (ref.read(doctorAppJwtRoleProvider) == DoctorAppJwtRole.clinicStaff) {
      if (mounted) {
        setState(() => _activeLocationLabel = null);
      }
      return;
    }
    try {
      final profile = ref.read(profileAllProvider).valueOrNull?.profile;
      final doctorTimeZone = profile?['timeZone'] as String?;
      final l10n = mounted ? AppLocalizations.of(context) : null;
      if (doctorTimeZone != null && doctorTimeZone.trim().isNotEmpty) {
        final nowInDoctorZone = getNowInTimezone(doctorTimeZone);
        final todayInDoctorZone = DateTime(
          nowInDoctorZone.year,
          nowInDoctorZone.month,
          nowInDoctorZone.day,
        );

        await ref.read(calendarProvider.notifier).loadDay(
              day: todayInDoctorZone,
              doctorTimeZone: doctorTimeZone,
            );
        final todayEntries = ref.read(calendarProvider)[todayInDoctorZone] ?? const <CalendarEntry>[];
        final currentLocation = _deriveCurrentLocationLabel(
          entries: todayEntries,
          nowInDoctorZone: nowInDoctorZone,
          l10n: l10n,
        );
        if (currentLocation != null && currentLocation.isNotEmpty) {
          if (!mounted) return;
          setState(() {
            _activeLocationLabel = currentLocation;
          });
          return;
        }
      }

      final locs = await fetchDoctorLocations(ref);
      final prefs = await SharedPreferences.getInstance();
      final savedId = prefs.getInt(_activeLocationPrefKey());

      DoctorLocationDto? active;
      if (savedId != null) {
        for (final l in locs) {
          if (l.id == savedId) {
            active = l;
            break;
          }
        }
      }
      active ??= locs.cast<DoctorLocationDto?>().firstWhere(
            (l) => l?.isPrimary == true,
            orElse: () => locs.isNotEmpty ? locs.first : null,
          );

      if (!mounted) return;
      setState(() {
        _activeLocationLabel = active?.label;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _activeLocationLabel = null;
      });
    }
  }

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    Future.microtask(() {
      if (!mounted) return;
      if (ref.read(doctorAppJwtRoleProvider) == DoctorAppJwtRole.clinicStaff) {
        return;
      }
      _loadActiveLocationLabel();
      ref.read(tasksProvider.notifier).loadTasks();
    });
    _sidebarLocationTicker = Timer.periodic(const Duration(minutes: 1), (_) {
      if (ref.read(doctorAppJwtRoleProvider) == DoctorAppJwtRole.clinicStaff) {
        return;
      }
      _loadActiveLocationLabel();
    });
  }

  @override
  void dispose() {
    _sidebarLocationTicker?.cancel();
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
      unawaited(invalidateAppointmentRelatedProviders(ref));
      if (ref.read(doctorAppJwtRoleProvider) != DoctorAppJwtRole.clinicStaff) {
        _loadActiveLocationLabel();
      }
    }
  }

  /// Pops the shell navigator back to tab content (if on a sub-page), then switches to the given tab.
  void _goToTab(int index) {
    _shellNavKey.currentState?.popUntil((route) => route.isFirst);
    ref.read(shellProvider.notifier).setTab(index);
    if (ref.read(doctorAppJwtRoleProvider) != DoctorAppJwtRole.clinicStaff &&
        (index == 1 || index == 2)) {
      _loadActiveLocationLabel();
    }
  }

  @override
  Widget build(BuildContext context) {
    if (ClinicStaffWebOnlyScreen.shouldShow(ref)) {
      return const ClinicStaffWebOnlyScreen();
    }

    final isClinicStaff =
        ref.watch(doctorAppJwtRoleProvider) == DoctorAppJwtRole.clinicStaff;

    ref.listen<int?>(notificationPendingTaskIdProvider, (prev, next) {
      if (next == null || next <= 0) return;
      if (ref.read(doctorAppJwtRoleProvider) == DoctorAppJwtRole.clinicStaff) {
        ref.read(notificationPendingTaskIdProvider.notifier).state = null;
        return;
      }
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
    if (!isClinicStaff &&
        pendingTaskId != null &&
        pendingTaskId > 0) {
      ref.read(notificationPendingTaskIdProvider.notifier).state = null;
      WidgetsBinding.instance.addPostFrameCallback((_) {
        _shellNavKey.currentState?.pushNamed(
          AppRoutes.taskDetails,
          arguments: pendingTaskId,
        );
      });
    }

    final brand = Theme.of(context).colorScheme.primary;
    var selectedIndex = ref.watch(shellProvider);
    final canUseTasks = isClinicStaff
        ? false
        : ref.watch(doctorFeatureProvider(DoctorFeature.remoteCareTasks));

    if (isClinicStaff) {
      if (selectedIndex < 0 || selectedIndex > 3) {
        Future.microtask(() {
          if (mounted) ref.read(shellProvider.notifier).setTab(1);
        });
        selectedIndex = 1;
      }
    } else if (selectedIndex == DoctorShellTab.tasks && !canUseTasks) {
      // Doctor was on Tasks but tier no longer permits it — bounce to Home.
      Future.microtask(() {
        if (mounted) ref.read(shellProvider.notifier).setTab(DoctorShellTab.home);
      });
      selectedIndex = DoctorShellTab.home;
    }

    final hasClinicWorkspace = ref.watch(hasClinicWorkspaceProvider);

    final screens = isClinicStaff
        ? const [
            _KeepAlive(child: ChatScreen()),
            _KeepAlive(child: ClinicWorkspaceScreen()),
            _KeepAlive(child: NotificationsScreen()),
            _KeepAlive(child: ProfileScreen()),
          ]
        : const [
            _KeepAlive(child: ChatScreen()),
            _KeepAlive(child: HomeScreen()),
            _KeepAlive(child: CalendarScreen()),
            _KeepAlive(child: PatientsScreen()),
            _KeepAlive(child: ClinicWorkspaceScreen()),
            _KeepAlive(child: TasksScreen()),
            _KeepAlive(child: ReportsScreen()),
            _KeepAlive(child: NotificationsScreen()),
            _KeepAlive(child: ProfileScreen()),
          ];

    final photoCacheBuster = ref.watch(photoCacheBusterProvider);
    String? avatarUrl;
    if (isClinicStaff) {
      final meAsync = ref.watch(meProfileProvider);
      meAsync.when(
        data: (me) {
          final raw = me.photoUrl;
          avatarUrl = (raw != null && raw.isNotEmpty)
              ? '$raw${raw.contains('?') ? '&' : '?'}t=$photoCacheBuster'
              : null;
        },
        loading: () {},
        error: (_, __) {},
      );
    } else {
      final allAsync = ref.watch(profileAllProvider);
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
    }

    final isMobile = PlatformLayout.useMobileShell(context);
    final l10n = AppLocalizations.of(context)!;

    return Scaffold(
      body: Row(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          if (!isMobile)
          // ───────────────── Sidebar (screenshot-style labeled nav) ─────────────────
          Container(
            width: 260,
            decoration: BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
                colors: [brand, brand.withValues(alpha: 0.88)],
              ),
            ),
            child: SafeArea(
              child: Column(
                children: [
                  const SizedBox(height: 20),
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 20),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Image.asset(
                              'assets/branding/shifa_logo.png',
                              width: 40,
                              height: 40,
                              fit: BoxFit.contain,
                            ),
                            const SizedBox(width: 10),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  const Text(
                                    'SHIFA',
                                    style: TextStyle(
                                      color: Colors.white,
                                      fontWeight: FontWeight.w800,
                                      fontSize: 16,
                                      letterSpacing: 0.5,
                                    ),
                                  ),
                                  Text(
                                    l10n.translate('clinicIntelligence'),
                                    style: TextStyle(
                                      color: Colors.white.withValues(alpha: 0.85),
                                      fontSize: 11,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 14),
                        _SidebarSearchButtonDark(
                          onTap: () => HomeSearchOverlay.show(context),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 12),
                  Expanded(
                    child: SingleChildScrollView(
                      padding: const EdgeInsets.symmetric(horizontal: 12),
                      child: Column(
                        children: [
                          if (isClinicStaff) ...[
                            _buildDarkNavItem(context, ref, Icons.chat_bubble_outline, l10n.chat, 0, brand, selectedIndex),
                            _buildDarkNavItem(context, ref, Icons.local_hospital_outlined, l10n.translate('clinicNavClinic'), 1, brand, selectedIndex),
                            _buildDarkNotificationsNavItem(context, ref, 2, brand, selectedIndex),
                          ] else ...[
                            _buildDarkNavItem(context, ref, Icons.home_outlined, l10n.home, DoctorShellTab.home, brand, selectedIndex),
                            _buildDarkNavItem(context, ref, Icons.calendar_today_outlined, l10n.translate('navAppointments'), DoctorShellTab.calendar, brand, selectedIndex),
                            _buildDarkNavItem(context, ref, Icons.people_outline, l10n.patients, DoctorShellTab.patients, brand, selectedIndex),
                            if (hasClinicWorkspace)
                              _buildDarkNavItem(context, ref, Icons.medical_services_outlined, l10n.translate('clinicNavClinic'), DoctorShellTab.clinic, brand, selectedIndex),
                            if (canUseTasks)
                              _buildDarkTasksNavItem(context, ref, DoctorShellTab.tasks, brand, selectedIndex),
                            _buildDarkNavItem(context, ref, Icons.analytics_outlined, l10n.translate('navReports'), DoctorShellTab.reports, brand, selectedIndex),
                            if (hasClinicWorkspace)
                              _buildDarkNavItem(context, ref, Icons.account_balance_wallet_outlined, l10n.translate('navFinance'), DoctorShellTab.clinic, brand, selectedIndex),
                            _buildDarkNavItem(context, ref, Icons.settings_outlined, l10n.translate('navSettings'), DoctorShellTab.profile, brand, selectedIndex),
                          ],
                        ],
                      ),
                    ),
                  ),
                  Padding(
                    padding: const EdgeInsets.all(12),
                    child: Column(
                      children: [
                        if (!isClinicStaff)
                          _SidebarAiCard(
                            brand: brand,
                            onTap: () {
                              if (!ref.read(
                                doctorFeatureProvider(DoctorFeature.askShifaAi),
                              )) {
                                ScaffoldMessenger.of(context).showSnackBar(
                                  SnackBar(content: Text(l10n.error)),
                                );
                                return;
                              }
                              AskShifaAiOverlay.show(context);
                            },
                          ),
                        if (!isClinicStaff) const SizedBox(height: 12),
                        _SidebarDoctorProfile(
                          brand: brand,
                          photoUrl: avatarUrl,
                          onTap: () => _goToTab(isClinicStaff ? 3 : DoctorShellTab.profile),
                        ),
                        const SizedBox(height: 8),
                        const LanguageMiniToggle(),
                        const SizedBox(height: 8),
                        _LogoutButtonDark(brand: brand),
                      ],
                    ),
                  ),
                ],
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
                      builder: (_) => _ShellTabContent(
                        screens: screens,
                        isClinicStaff: isClinicStaff,
                      ),
                    );
                  }
                  final route = AppRouter.shellOnGenerateRoute(settings);
                  if (route != null) return route;
                  return MaterialPageRoute<void>(
                    builder: (_) => _ShellTabContent(
                      screens: screens,
                      isClinicStaff: isClinicStaff,
                    ),
                  );
                },
              ),
                ),
                if (!isClinicStaff && !PlatformLayout.isNativeMobile)
                  Positioned(
                    right: 0,
                    bottom: 0,
                    child: PatientBriefingPanel(
                      bottomInset: Responsive.bottomNavClearance(context),
                    ),
                  ),
                if (isMobile)
                  Positioned(
                    top: 0,
                    right: 0,
                    child: SafeArea(
                      child: Padding(
                        padding: const EdgeInsets.only(top: 4, right: 8),
                        child: const LanguageMiniToggle(),
                      ),
                    ),
                  ),
              ],
            ),
          ),
        ],
      ),
      bottomNavigationBar: isMobile
          ? _MobileBottomNav(
              isClinicStaff: isClinicStaff,
              selectedIndex: selectedIndex,
              brand: brand,
              avatarUrl: avatarUrl,
              hasClinicWorkspace: hasClinicWorkspace,
              canUseTasks: canUseTasks,
              activeLocationLabel: _activeLocationLabel,
              locationIsVideo: _sidebarLocationChipIsVideo(context),
              onSelectTab: _goToTab,
              onShowMore: () => _showMobileMoreSheet(
                context,
                brand: brand,
                selectedIndex: selectedIndex,
                isClinicStaff: isClinicStaff,
                hasClinicWorkspace: hasClinicWorkspace,
                canUseTasks: canUseTasks,
                activeLocationLabel: _activeLocationLabel,
                locationIsVideo: _sidebarLocationChipIsVideo(context),
              ),
            )
          : null,
    );
  }

  void _showMobileMoreSheet(
    BuildContext context, {
    required Color brand,
    required int selectedIndex,
    required bool isClinicStaff,
    required bool hasClinicWorkspace,
    required bool canUseTasks,
    required String? activeLocationLabel,
    required bool locationIsVideo,
  }) {
    final l10n = AppLocalizations.of(context)!;
    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      useSafeArea: true,
      builder: (ctx) {
        final bottomPad = Responsive.mobileBottomSheetPadding(ctx);
        return Padding(
          padding: EdgeInsets.only(bottom: bottomPad),
          child: SingleChildScrollView(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  if (!isClinicStaff &&
                      (activeLocationLabel ?? '').trim().isNotEmpty) ...[
                    _MobileMoreSheetTile(
                      leading: Icon(
                        locationIsVideo
                            ? Icons.videocam_outlined
                            : Icons.location_on_outlined,
                        color: brand,
                      ),
                      title: Text(activeLocationLabel!),
                      subtitle: Text(
                        l10n.translate('currentLocation') ?? 'Current location',
                      ),
                    ),
                    const Divider(),
                  ],
                  if (!isClinicStaff && hasClinicWorkspace)
                    _MobileMoreSheetTile(
                      leading: Icon(Icons.local_hospital_outlined, color: brand),
                      title: Text(l10n.translate('clinicNavClinic') ?? 'Clinic'),
                      selected: selectedIndex == 4,
                      onTap: () {
                        Navigator.pop(ctx);
                        _goToTab(4);
                      },
                    ),
                  if (!isClinicStaff && canUseTasks)
                    _MobileMoreSheetTile(
                      leading: Icon(Icons.task_alt, color: brand),
                      title: Text(l10n.translate('tasks') ?? 'Tasks'),
                      selected: selectedIndex == DoctorShellTab.tasks,
                      onTap: () {
                        Navigator.pop(ctx);
                        _goToTab(DoctorShellTab.tasks);
                      },
                    ),
                  if (!isClinicStaff)
                    _MobileMoreSheetTile(
                      leading: Icon(Icons.analytics_outlined, color: brand),
                      title: Text(l10n.translate('navReports')),
                      selected: selectedIndex == DoctorShellTab.reports,
                      onTap: () {
                        Navigator.pop(ctx);
                        _goToTab(DoctorShellTab.reports);
                      },
                    ),
                  _MobileMoreSheetTile(
                    leading: Icon(Icons.notifications_outlined, color: brand),
                    title: Text(l10n.notifications),
                    selected: isClinicStaff
                        ? selectedIndex == 2
                        : selectedIndex == DoctorShellTab.notifications,
                    onTap: () {
                      Navigator.pop(ctx);
                      _goToTab(
                        isClinicStaff ? 2 : DoctorShellTab.notifications,
                      );
                    },
                  ),
                  _MobileMoreSheetTile(
                    leading: Icon(Icons.person_outline, color: brand),
                    title: Text(l10n.profile),
                    selected: isClinicStaff
                        ? selectedIndex == 3
                        : selectedIndex == DoctorShellTab.profile,
                    onTap: () {
                      Navigator.pop(ctx);
                      _goToTab(isClinicStaff ? 3 : DoctorShellTab.profile);
                    },
                  ),
                  const Divider(),
                  _MobileMoreSheetTile(
                    leading: Icon(Icons.language, color: brand),
                    title: Text(l10n.language),
                    trailing: const LanguageMiniToggle(),
                  ),
                  const SizedBox(height: 8),
                  _MobileMoreLogoutButton(brand: brand),
                  const SizedBox(height: 8),
                ],
              ),
            ),
          ),
        );
      },
    );
  }

  Widget _buildDarkNavItem(
    BuildContext context,
    WidgetRef ref,
    IconData icon,
    String label,
    int index,
    Color brand,
    int selectedIndex,
  ) {
    final isSelected = selectedIndex == index;
    return Padding(
      padding: const EdgeInsets.only(bottom: 4),
      child: Material(
        color: isSelected ? Colors.white : Colors.transparent,
        borderRadius: BorderRadius.circular(12),
        child: InkWell(
          onTap: () => _goToTab(index),
          borderRadius: BorderRadius.circular(12),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 11),
            child: Row(
              children: [
                Icon(icon, color: isSelected ? brand : Colors.white, size: 22),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    label,
                    style: TextStyle(
                      fontSize: 14,
                      fontWeight: isSelected ? FontWeight.w700 : FontWeight.w500,
                      color: isSelected ? brand : Colors.white,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildDarkTasksNavItem(
    BuildContext context,
    WidgetRef ref,
    int index,
    Color brand,
    int selectedIndex,
  ) {
    final l10n = AppLocalizations.of(context)!;
    final isSelected = selectedIndex == index;
    final count = ref.watch(tasksProvider).where((t) => t.status == TaskStatus.active).length;

    return Padding(
      padding: const EdgeInsets.only(bottom: 4),
      child: Material(
        color: isSelected ? Colors.white : Colors.transparent,
        borderRadius: BorderRadius.circular(12),
        child: InkWell(
          onTap: () => _goToTab(index),
          borderRadius: BorderRadius.circular(12),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 11),
            child: Row(
              children: [
                Icon(Icons.task_alt_outlined,
                    color: isSelected ? brand : Colors.white, size: 22),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    l10n.tasks,
                    style: TextStyle(
                      fontSize: 14,
                      fontWeight: isSelected ? FontWeight.w700 : FontWeight.w500,
                      color: isSelected ? brand : Colors.white,
                    ),
                  ),
                ),
                if (count > 0)
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
                    decoration: BoxDecoration(
                      color: isSelected ? brand : Colors.white,
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: Text(
                      count > 99 ? '99+' : '$count',
                      style: TextStyle(
                        fontSize: 11,
                        fontWeight: FontWeight.w700,
                        color: isSelected ? Colors.white : brand,
                      ),
                    ),
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildDarkNotificationsNavItem(
    BuildContext context,
    WidgetRef ref,
    int index,
    Color brand,
    int selectedIndex,
  ) {
    final l10n = AppLocalizations.of(context)!;
    final isSelected = selectedIndex == index;
    final unread = ref.watch(doctorNotificationsUnreadCountProvider).valueOrNull ?? 0;

    return Padding(
      padding: const EdgeInsets.only(bottom: 4),
      child: Material(
        color: isSelected ? Colors.white : Colors.transparent,
        borderRadius: BorderRadius.circular(12),
        child: InkWell(
          onTap: () => _goToTab(index),
          borderRadius: BorderRadius.circular(12),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 11),
            child: Row(
              children: [
                Icon(Icons.notifications_outlined,
                    color: isSelected ? brand : Colors.white, size: 22),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    l10n.notifications,
                    style: TextStyle(
                      fontSize: 14,
                      fontWeight: isSelected ? FontWeight.w700 : FontWeight.w500,
                      color: isSelected ? brand : Colors.white,
                    ),
                  ),
                ),
                if (unread > 0)
                  Container(
                    width: 8,
                    height: 8,
                    decoration: const BoxDecoration(
                      color: Colors.red,
                      shape: BoxShape.circle,
                    ),
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildLabeledNavItem(
    BuildContext context,
    WidgetRef ref,
    IconData icon,
    String label,
    int index,
    Color brand,
    int selectedIndex,
  ) {
    final isSelected = selectedIndex == index;
    return Padding(
      padding: const EdgeInsets.only(bottom: 4),
      child: Material(
        color: isSelected ? brand.withValues(alpha: 0.1) : Colors.transparent,
        borderRadius: BorderRadius.circular(12),
        child: InkWell(
          onTap: () => _goToTab(index),
          borderRadius: BorderRadius.circular(12),
          hoverColor: brand.withValues(alpha: 0.06),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
            child: Row(
              children: [
                Icon(icon, color: isSelected ? brand : Colors.grey.shade600, size: 22),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    label,
                    style: TextStyle(
                      fontSize: 14,
                      fontWeight: isSelected ? FontWeight.w600 : FontWeight.w500,
                      color: isSelected ? brand : Colors.grey.shade800,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildLabeledChatNavItem(
    BuildContext context,
    WidgetRef ref,
    int index,
    Color brand,
    int selectedIndex,
  ) {
    final l10n = AppLocalizations.of(context)!;
    final isSelected = selectedIndex == index;
    final unreadAsync = ref.watch(unreadCountProvider);

    return Padding(
      padding: const EdgeInsets.only(bottom: 4),
      child: Material(
        color: isSelected ? brand.withValues(alpha: 0.1) : Colors.transparent,
        borderRadius: BorderRadius.circular(12),
        child: InkWell(
          onTap: () => _goToTab(index),
          borderRadius: BorderRadius.circular(12),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
            child: Row(
              children: [
                Stack(
                  clipBehavior: Clip.none,
                  children: [
                    Icon(Icons.chat_bubble_outline,
                        color: isSelected ? brand : Colors.grey.shade600, size: 22),
                    unreadAsync.when(
                      data: (count) => count > 0
                          ? Positioned(
                              right: -6,
                              top: -4,
                              child: Container(
                                width: 8,
                                height: 8,
                                decoration: const BoxDecoration(
                                  color: Colors.red,
                                  shape: BoxShape.circle,
                                ),
                              ),
                            )
                          : const SizedBox.shrink(),
                      loading: () => const SizedBox.shrink(),
                      error: (_, __) => const SizedBox.shrink(),
                    ),
                  ],
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    l10n.chat,
                    style: TextStyle(
                      fontSize: 14,
                      fontWeight: isSelected ? FontWeight.w600 : FontWeight.w500,
                      color: isSelected ? brand : Colors.grey.shade800,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildLabeledNotificationsNavItem(
    BuildContext context,
    WidgetRef ref,
    int index,
    Color brand,
    int selectedIndex,
  ) {
    final l10n = AppLocalizations.of(context)!;
    final isSelected = selectedIndex == index;
    final unreadAsync = ref.watch(doctorNotificationsUnreadCountProvider);

    return Padding(
      padding: const EdgeInsets.only(bottom: 4),
      child: Material(
        color: isSelected ? brand.withValues(alpha: 0.1) : Colors.transparent,
        borderRadius: BorderRadius.circular(12),
        child: InkWell(
          onTap: () => _goToTab(index),
          borderRadius: BorderRadius.circular(12),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
            child: Row(
              children: [
                Stack(
                  clipBehavior: Clip.none,
                  children: [
                    Icon(Icons.notifications_outlined,
                        color: isSelected ? brand : Colors.grey.shade600, size: 22),
                    unreadAsync.when(
                      data: (count) => count > 0
                          ? Positioned(
                              right: -6,
                              top: -4,
                              child: Container(
                                width: 8,
                                height: 8,
                                decoration: const BoxDecoration(
                                  color: Colors.red,
                                  shape: BoxShape.circle,
                                ),
                              ),
                            )
                          : const SizedBox.shrink(),
                      loading: () => const SizedBox.shrink(),
                      error: (_, __) => const SizedBox.shrink(),
                    ),
                  ],
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    l10n.notifications,
                    style: TextStyle(
                      fontSize: 14,
                      fontWeight: isSelected ? FontWeight.w600 : FontWeight.w500,
                      color: isSelected ? brand : Colors.grey.shade800,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildLabeledNavAvatarItem(
    BuildContext context,
    WidgetRef ref,
    String? photoUrl,
    int index,
    Color brand,
    int selectedIndex,
  ) {
    final l10n = AppLocalizations.of(context)!;
    final isSelected = selectedIndex == index;
    final hasPhoto = photoUrl != null && photoUrl.isNotEmpty;

    return Material(
      color: isSelected ? brand.withValues(alpha: 0.1) : Colors.grey.shade50,
      borderRadius: BorderRadius.circular(12),
      child: InkWell(
        onTap: () => _goToTab(index),
        borderRadius: BorderRadius.circular(12),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
          child: Row(
            children: [
              CircleAvatar(
                radius: 16,
                backgroundColor: brand.withValues(alpha: 0.12),
                backgroundImage: hasPhoto ? NetworkImage(photoUrl!) : null,
                child: hasPhoto
                    ? null
                    : Icon(Icons.person, color: brand, size: 18),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Text(
                  l10n.profile,
                  style: TextStyle(
                    fontSize: 14,
                    fontWeight: isSelected ? FontWeight.w600 : FontWeight.w500,
                    color: isSelected ? brand : Colors.grey.shade800,
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildNavItem(
    BuildContext context,
    WidgetRef ref,
    IconData icon,
    int index,
    Color brand,
    int selectedIndex, {
    String? tooltip,
  }) {
    final isSelected = selectedIndex == index;
    final child = InkWell(
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
    if (tooltip != null && tooltip.isNotEmpty) {
      return Tooltip(message: tooltip, child: child);
    }
    return child;
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

class _SidebarSearchButtonDark extends StatelessWidget {
  const _SidebarSearchButtonDark({required this.onTap});

  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return Material(
      color: Colors.white.withValues(alpha: 0.14),
      borderRadius: BorderRadius.circular(12),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(12),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
          child: Row(
            children: [
              Icon(Icons.search, size: 18, color: Colors.white.withValues(alpha: 0.9)),
              const SizedBox(width: 10),
              Expanded(
                child: Text(
                  l10n.translate('search'),
                  style: TextStyle(color: Colors.white.withValues(alpha: 0.85), fontSize: 13),
                ),
              ),
              Text(
                '⌘K',
                style: TextStyle(
                  fontSize: 11,
                  color: Colors.white.withValues(alpha: 0.65),
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _SidebarAiCard extends StatelessWidget {
  const _SidebarAiCard({required this.brand, required this.onTap});

  final Color brand;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: Colors.white.withValues(alpha: 0.2)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(Icons.auto_awesome, color: Colors.white.withValues(alpha: 0.95), size: 18),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  l10n.translate('sidebarAiTitle'),
                  style: const TextStyle(
                    color: Colors.white,
                    fontWeight: FontWeight.w600,
                    fontSize: 12,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          SizedBox(
            width: double.infinity,
            child: TextButton(
              onPressed: onTap,
              style: TextButton.styleFrom(
                backgroundColor: Colors.white,
                foregroundColor: brand,
                padding: const EdgeInsets.symmetric(vertical: 10),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
              ),
              child: Text(
                l10n.translate('sidebarAiCta'),
                style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 12),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _SidebarDoctorProfile extends ConsumerWidget {
  const _SidebarDoctorProfile({
    required this.brand,
    required this.photoUrl,
    required this.onTap,
  });

  final Color brand;
  final String? photoUrl;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context)!;
    final isClinicStaff =
        ref.watch(doctorAppJwtRoleProvider) == DoctorAppJwtRole.clinicStaff;

    String name = l10n.doctor;
    if (isClinicStaff) {
      final me = ref.watch(meProfileProvider).valueOrNull;
      if (me != null) {
        name = '${me.firstName} ${me.lastName.isNotEmpty ? '${me.lastName[0]}.' : ''}'.trim();
      }
    } else {
      final profile = ref.watch(profileAllProvider).valueOrNull?.profile;
      final first = profile?['firstName'] as String? ?? '';
      final last = profile?['lastName'] as String? ?? '';
      final title = profile?['title'] as String? ?? 'Dr.';
      if (first.isNotEmpty || last.isNotEmpty) {
        name = '$title $first ${last.isNotEmpty ? '${last[0]}.' : ''}'.trim();
      }
    }
    final hasPhoto = photoUrl != null && photoUrl!.isNotEmpty;

    return Material(
      color: Colors.white.withValues(alpha: 0.1),
      borderRadius: BorderRadius.circular(12),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(12),
        child: Padding(
          padding: const EdgeInsets.all(10),
          child: Row(
            children: [
              CircleAvatar(
                radius: 18,
                backgroundColor: Colors.white.withValues(alpha: 0.2),
                backgroundImage: hasPhoto ? NetworkImage(photoUrl!) : null,
                child: hasPhoto ? null : const Icon(Icons.person, color: Colors.white, size: 18),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      name,
                      style: const TextStyle(
                        color: Colors.white,
                        fontWeight: FontWeight.w600,
                        fontSize: 13,
                      ),
                      overflow: TextOverflow.ellipsis,
                    ),
                    Text(
                      l10n.translate('administrator'),
                      style: TextStyle(
                        color: Colors.white.withValues(alpha: 0.75),
                        fontSize: 11,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _LogoutButtonDark extends ConsumerWidget {
  const _LogoutButtonDark({required this.brand});

  final Color brand;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context)!;
    return TextButton.icon(
      onPressed: () {
        showDialog(
          context: context,
          builder: (dialogContext) => AlertDialog(
            title: Text(l10n.signOut),
            content: Text(l10n.signOutConfirm),
            actions: [
              TextButton(onPressed: () => Navigator.pop(dialogContext), child: Text(l10n.cancel)),
              TextButton(
                onPressed: () {
                  Navigator.pop(dialogContext);
                  ref.read(authProvider.notifier).logout();
                  ref.invalidate(profileAllProvider);
                  ref.invalidate(meProfileProvider);
                  ref.invalidate(shellProvider);
                  unawaited(invalidateAppointmentRelatedProviders(ref));
                  ref.invalidate(patientsProvider);
                  Navigator.pushNamedAndRemoveUntil(context, AppRoutes.login, (_) => false);
                },
                style: TextButton.styleFrom(foregroundColor: Colors.red),
                child: Text(l10n.signOut),
              ),
            ],
          ),
        );
      },
      icon: Icon(Icons.logout, size: 18, color: Colors.white.withValues(alpha: 0.85)),
      label: Text(l10n.signOut, style: TextStyle(color: Colors.white.withValues(alpha: 0.85))),
    );
  }
}

class _SidebarSearchButton extends StatelessWidget {
  const _SidebarSearchButton({required this.brand, required this.onTap});

  final Color brand;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return Material(
      color: Colors.grey.shade50,
      borderRadius: BorderRadius.circular(12),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(12),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
          child: Row(
            children: [
              Icon(Icons.search, size: 18, color: Colors.grey.shade500),
              const SizedBox(width: 10),
              Expanded(
                child: Text(
                  l10n.translate('search') ?? 'Search',
                  style: TextStyle(color: Colors.grey.shade500, fontSize: 14),
                ),
              ),
              Text(
                '⌘K',
                style: TextStyle(
                  fontSize: 11,
                  color: Colors.grey.shade400,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _LogoutButtonLabeled extends ConsumerWidget {
  const _LogoutButtonLabeled({required this.brand});

  final Color brand;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context)!;
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: () {
          showDialog(
            context: context,
            builder: (dialogContext) => AlertDialog(
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
                    ref.invalidate(meProfileProvider);
                    ref.invalidate(shellProvider);
                    unawaited(invalidateAppointmentRelatedProviders(ref));
                    ref.invalidate(patientsProvider);
                    Navigator.pushNamedAndRemoveUntil(
                      context,
                      AppRoutes.login,
                      (_) => false,
                    );
                  },
                  style: TextButton.styleFrom(foregroundColor: Colors.red),
                  child: Text(l10n.signOut),
                ),
              ],
            ),
          );
        },
        borderRadius: BorderRadius.circular(12),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
          child: Row(
            children: [
              Icon(Icons.logout, color: Colors.grey.shade600, size: 20),
              const SizedBox(width: 12),
              Text(
                l10n.signOut,
                style: TextStyle(
                  fontSize: 14,
                  color: Colors.grey.shade700,
                  fontWeight: FontWeight.w500,
                ),
              ),
            ],
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
                      ref.invalidate(meProfileProvider);
                      ref.invalidate(shellProvider);
                      // Clear doctor-scoped data so next login never sees stale appointments/patients
                      unawaited(invalidateAppointmentRelatedProviders(ref));
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

/// Default shell content: tab body. Reads [shellProvider] directly so tab changes
/// are not lost when this widget is built inside a nested Navigator route.
class _ShellTabContent extends ConsumerWidget {
  const _ShellTabContent({
    required this.screens,
    required this.isClinicStaff,
  });

  final List<Widget> screens;
  final bool isClinicStaff;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    var selectedIndex = ref.watch(shellProvider);
    if (isClinicStaff) {
      if (selectedIndex < 0 || selectedIndex > 3) {
        selectedIndex = 1;
      }
    } else {
      final canUseTasks =
          ref.watch(doctorFeatureProvider(DoctorFeature.remoteCareTasks));
      if (selectedIndex == DoctorShellTab.tasks && !canUseTasks) {
        selectedIndex = DoctorShellTab.home;
      } else if (selectedIndex > DoctorShellTab.profile) {
        selectedIndex = DoctorShellTab.home;
      }
    }
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

class _MobileMoreSheetTile extends StatelessWidget {
  const _MobileMoreSheetTile({
    required this.leading,
    required this.title,
    this.subtitle,
    this.trailing,
    this.selected = false,
    this.onTap,
  });

  final Widget leading;
  final Widget title;
  final Widget? subtitle;
  final Widget? trailing;
  final bool selected;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: selected ? Theme.of(context).colorScheme.primary.withValues(alpha: 0.08) : Colors.transparent,
      borderRadius: BorderRadius.circular(12),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(12),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 10),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              leading,
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    DefaultTextStyle.merge(
                      style: const TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w500,
                      ),
                      child: title,
                    ),
                    if (subtitle != null) ...[
                      const SizedBox(height: 2),
                      DefaultTextStyle.merge(
                        style: TextStyle(
                          fontSize: 13,
                          color: Colors.grey.shade600,
                        ),
                        child: subtitle!,
                      ),
                    ],
                  ],
                ),
              ),
              if (trailing != null) trailing!,
            ],
          ),
        ),
      ),
    );
  }
}

class _MobileMoreLogoutButton extends ConsumerWidget {
  const _MobileMoreLogoutButton({required this.brand});

  final Color brand;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context)!;
    return Material(
      color: Colors.transparent,
      borderRadius: BorderRadius.circular(12),
      child: InkWell(
        onTap: () {
          showDialog(
            context: context,
            builder: (dialogContext) => AlertDialog(
              title: Text(l10n.signOut),
              content: Text(l10n.signOutConfirm),
              actions: [
                TextButton(
                  onPressed: () => Navigator.pop(dialogContext),
                  child: Text(l10n.cancel),
                ),
                TextButton(
                  onPressed: () {
                    Navigator.pop(dialogContext);
                    ref.read(authProvider.notifier).logout();
                    ref.invalidate(profileAllProvider);
                    ref.invalidate(meProfileProvider);
                    ref.invalidate(shellProvider);
                    unawaited(invalidateAppointmentRelatedProviders(ref));
                    ref.invalidate(patientsProvider);
                    Navigator.pushNamedAndRemoveUntil(
                      context,
                      AppRoutes.login,
                      (_) => false,
                    );
                  },
                  style: TextButton.styleFrom(foregroundColor: Colors.red),
                  child: Text(l10n.signOut),
                ),
              ],
            ),
          );
        },
        borderRadius: BorderRadius.circular(12),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 14),
          child: Row(
            children: [
              Icon(Icons.logout, color: brand, size: 22),
              const SizedBox(width: 16),
              Text(
                l10n.signOut,
                style: TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.w500,
                  color: brand,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _MobileBottomNav extends ConsumerWidget {
  const _MobileBottomNav({
    required this.isClinicStaff,
    required this.selectedIndex,
    required this.brand,
    required this.avatarUrl,
    required this.hasClinicWorkspace,
    required this.canUseTasks,
    required this.activeLocationLabel,
    required this.locationIsVideo,
    required this.onSelectTab,
    required this.onShowMore,
  });

  final bool isClinicStaff;
  final int selectedIndex;
  final Color brand;
  final String? avatarUrl;
  final bool hasClinicWorkspace;
  final bool canUseTasks;
  final String? activeLocationLabel;
  final bool locationIsVideo;
  final ValueChanged<int> onSelectTab;
  final VoidCallback onShowMore;

  int _doctorNavHighlight(int tabIndex) {
    // Mobile bottom bar order: Home, Calendar, Patients, Chat, More
    const mobilePrimaryTabs = [
      DoctorShellTab.home,
      DoctorShellTab.calendar,
      DoctorShellTab.patients,
      DoctorShellTab.chat,
    ];
    final idx = mobilePrimaryTabs.indexOf(tabIndex);
    if (idx >= 0) return idx;
    return 4;
  }

  int _mobileTabFromNavIndex(int navIndex) {
    switch (navIndex) {
      case 0:
        return DoctorShellTab.home;
      case 1:
        return DoctorShellTab.calendar;
      case 2:
        return DoctorShellTab.patients;
      case 3:
        return DoctorShellTab.chat;
      default:
        return -1;
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context)!;
    final unreadChat = ref.watch(unreadCountProvider).valueOrNull ?? 0;
    final unreadNotif =
        ref.watch(doctorNotificationsUnreadCountProvider).valueOrNull ?? 0;

    if (isClinicStaff) {
      return NavigationBar(
        selectedIndex: selectedIndex.clamp(0, 3),
        onDestinationSelected: onSelectTab,
        destinations: [
          NavigationDestination(
            icon: Badge(
              isLabelVisible: unreadChat > 0,
              label: Text('$unreadChat'),
              child: const Icon(Icons.chat_bubble_outline),
            ),
            label: l10n.chat,
          ),
          NavigationDestination(
            icon: const Icon(Icons.local_hospital_outlined),
            label: l10n.translate('clinicNavClinic') ?? 'Clinic',
          ),
          NavigationDestination(
            icon: Badge(
              isLabelVisible: unreadNotif > 0,
              label: Text('$unreadNotif'),
              child: const Icon(Icons.notifications_outlined),
            ),
            label: l10n.notifications,
          ),
          NavigationDestination(
            icon: CircleAvatar(
              radius: 12,
              backgroundImage: avatarUrl != null && avatarUrl!.isNotEmpty
                  ? NetworkImage(avatarUrl!)
                  : null,
              child: avatarUrl == null || avatarUrl!.isEmpty
                  ? const Icon(Icons.person, size: 16)
                  : null,
            ),
            label: l10n.profile,
          ),
        ],
      );
    }

    final navIndex = _doctorNavHighlight(selectedIndex);
    return NavigationBar(
      selectedIndex: navIndex,
      onDestinationSelected: (index) {
        if (index == 4) {
          onShowMore();
        } else {
          final tab = _mobileTabFromNavIndex(index);
          if (tab >= 0) onSelectTab(tab);
        }
      },
      destinations: [
        NavigationDestination(
          icon: const Icon(Icons.home_outlined),
          label: l10n.home,
        ),
        NavigationDestination(
          icon: const Icon(Icons.calendar_today_outlined),
          label: l10n.calendar,
        ),
        NavigationDestination(
          icon: const Icon(Icons.people_outline),
          label: l10n.patients,
        ),
        NavigationDestination(
          icon: Badge(
            isLabelVisible: unreadChat > 0,
            label: Text('$unreadChat'),
            child: const Icon(Icons.chat_bubble_outline),
          ),
          label: l10n.chat,
        ),
        NavigationDestination(
          icon: const Icon(Icons.more_horiz),
          label: l10n.translate('more') ?? 'More',
        ),
      ],
    );
  }
}
