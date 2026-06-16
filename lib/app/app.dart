import 'dart:async';
import 'dart:convert';

import 'package:firebase_core/firebase_core.dart';
import 'package:flutter/material.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/app/theme.dart';
import 'package:shifa_doc_app_v1/app/router.dart';
import 'package:shifa_doc_app_v1/core/api/api_providers.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/providers/language_provider.dart';
import 'package:shifa_doc_app_v1/core/services/push_notification_service.dart';
import 'package:shifa_doc_app_v1/core/util/admin_host.dart' show isAdminHost;
import 'package:shifa_doc_app_v1/core/util/set_web_title.dart';
import 'package:shifa_doc_app_v1/core/utils/timezone_utils.dart';
import 'package:shifa_doc_app_v1/core/layout/shifa_scroll_behavior.dart';
import 'package:shifa_doc_app_v1/core/widgets/app_lock_layer.dart';
import 'package:shifa_doc_app_v1/core/widgets/activity_tracker.dart';
export 'package:shifa_doc_app_v1/core/util/admin_host.dart';
import 'package:shifa_doc_app_v1/state/auth/auth_controller.dart';
import 'package:shifa_doc_app_v1/state/chat/chat_providers.dart';
import 'package:shifa_doc_app_v1/state/calendar/calendar_controller.dart';
import 'package:shifa_doc_app_v1/state/profile/profile_providers.dart';
import 'package:shifa_doc_app_v1/state/shell/shell_controller.dart';
import 'package:shifa_doc_app_v1/state/appointments/appointment_invalidation.dart';
import 'package:shifa_doc_app_v1/features/appointments/application/consultation_notes_provider.dart';
import 'package:shifa_doc_app_v1/features/shell/domain/doctor_shell_tab.dart';
import 'package:shifa_doc_app_v1/features/shell/presentation/shell_scope.dart';

// Global navigator key for navigation from anywhere
final navigatorKey = GlobalKey<NavigatorState>();

/// Fetches appointment by id and returns the appointment date in doctor's timezone.
Future<DateTime?> _fetchAppointmentDay(WidgetRef ref, int appointmentId) async {
  try {
    final client = ref.read(apiClientProvider);
    final tz = ref.read(profileAllProvider).valueOrNull?.profile['timeZone'] as String?;
    final effectiveTz = (tz != null && tz.trim().isNotEmpty) ? tz : 'UTC';
    final resp = await client.get('/api/appointments/$appointmentId');
    if (resp.statusCode != 200) return null;
    final map = json.decode(utf8.decode(resp.bodyBytes)) as Map<String, dynamic>;
    final startAtStr = map['startAt'] as String?;
    if (startAtStr == null) return null;
    final utc = DateTime.parse(startAtStr);
    final inTz = utcToTimezone(utc, effectiveTz);
    return DateTime(inTz.year, inTz.month, inTz.day);
  } catch (_) {
    return null;
  }
}

DateTime? _dayFromAppointmentStartAt(WidgetRef ref, String appointmentStartAt) {
  try {
    final tz = ref.read(profileAllProvider).valueOrNull?.profile['timeZone'] as String?;
    final effectiveTz = (tz != null && tz.trim().isNotEmpty) ? tz : 'UTC';
    final utc = DateTime.parse(appointmentStartAt);
    final inTz = utcToTimezone(utc, effectiveTz);
    return DateTime(inTz.year, inTz.month, inTz.day);
  } catch (_) {
    return null;
  }
}

/// Push the patients screen into the shell's nested navigator after the
/// shell has been mounted on the outer navigator. Retries on the next frame
/// if the shell's [Navigator] has not finished building yet, since
/// [pushNamedAndRemoveUntil] only schedules MainShell to be built — the
/// inner navigator is one frame behind.
void _pushIntoShell(Object arguments) {
  ShellScope.pushIntoShell(arguments);
}

/// Resolves appointment day, shows loading, then navigates to Calendar on that day (no "today" flash).
Future<void> _openCalendarToAppointment(
  WidgetRef ref,
  int id, {
  String? appointmentStartAt,
}) async {
  final context = navigatorKey.currentContext;
  if (context == null) return;

  final alreadyOnShell =
      ModalRoute.of(context)?.settings.name == AppRoutes.shell;
  var loadingDialogShown = false;

  void dismissLoading() {
    if (loadingDialogShown && context.mounted) {
      Navigator.of(context, rootNavigator: true).pop();
      loadingDialogShown = false;
    }
  }

  if (!alreadyOnShell) {
    loadingDialogShown = true;
    showDialog<void>(
      context: context,
      barrierDismissible: false,
      builder: (_) => const Center(
        child: Card(
          child: Padding(
            padding: EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                CircularProgressIndicator(),
                SizedBox(height: 16),
                Text('Opening appointment...'),
              ],
            ),
          ),
        ),
      ),
    );
  }

  try {
    final day = (appointmentStartAt != null && appointmentStartAt.isNotEmpty)
        ? _dayFromAppointmentStartAt(ref, appointmentStartAt)
        : null;
    final resolvedDay = day ?? await _fetchAppointmentDay(ref, id);
    if (resolvedDay == null) return;

    ref.read(calendarGoToAppointmentDayProvider.notifier).state = resolvedDay;
    ref.read(calendarGoToAppointmentIdProvider.notifier).state = id;
    await invalidateAppointmentRelatedProviders(ref);
    ref.read(shellProvider.notifier).setTab(2);

    if (alreadyOnShell) {
      // Already inside MainShell (e.g. notifications tab) — switch tab only.
      return;
    }

    dismissLoading();
    navigatorKey.currentState?.pushNamedAndRemoveUntil(
      AppRoutes.shell,
      (route) => false,
    );
  } finally {
    dismissLoading();
  }
}

class ShifaDoctorApp extends ConsumerStatefulWidget {
  const ShifaDoctorApp({super.key});

  @override
  ConsumerState<ShifaDoctorApp> createState() => _ShifaDoctorAppState();
}

class _ShifaDoctorAppState extends ConsumerState<ShifaDoctorApp> {
  bool _fcmTokenSetup = false;
  bool _pushTapSetup = false;

  @override
  Widget build(BuildContext context) {
    final languageState = ref.watch(languageProvider);
    final authState = ref.watch(authProvider);
    
    // Listen to auth state changes and navigate to login on logout
    ref.listen<AuthState>(authProvider, (previous, next) {
      if (previous?.isAuthenticated == true && !next.isAuthenticated) {
        // User was logged in but now logged out - navigate to correct login
        final route = isAdminHost ? AppRoutes.adminLogin : AppRoutes.login;
        WidgetsBinding.instance.addPostFrameCallback((_) {
          navigatorKey.currentState?.pushNamedAndRemoveUntil(
            route,
            (route) => false,
          );
        });
      }
    });

    // Set up FCM token upload when authenticated (and Firebase is available).
    // Admin app should NOT call doctor-only endpoints.
    if (!isAdminHost && authState.isAuthenticated && !_fcmTokenSetup && Firebase.apps.isNotEmpty) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted) return;
        _fcmTokenSetup = true;
        final pushService = PushNotificationService();
        final api = ref.read(doctorApiClientProvider);
        pushService.setOnFcmTokenReady((token) {
          if (token.isEmpty) return;
          api
              .put('/api/doctors/me/fcm-token', <String, dynamic>{'fcmToken': token})
              .then((_) {
            if (kDebugMode) {
              debugPrint('Doctor FCM token uploaded to backend');
            }
          }).catchError((e) {
            if (kDebugMode) {
              debugPrint('Doctor FCM token upload failed: $e');
            }
          });
        });
        final existing = pushService.getFcmToken();
        if (existing != null && existing.isNotEmpty) {
          api
              .put('/api/doctors/me/fcm-token', <String, dynamic>{'fcmToken': existing})
              .then((_) {
            if (kDebugMode) {
              debugPrint('Doctor FCM token uploaded to backend (existing)');
            }
          }).catchError((e) {
            if (kDebugMode) {
              debugPrint('Doctor FCM token upload failed (existing): $e');
            }
          });
        }
      });
    } else if (!authState.isAuthenticated) {
      _fcmTokenSetup = false;
    }

    // Set up push notification tap handler once.
    if (!_pushTapSetup && Firebase.apps.isNotEmpty) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted) return;
        _pushTapSetup = true;
        final pushService = PushNotificationService();
        pushService.setOnForegroundDataRefresh((data) {
          if (!ref.read(authProvider).isAuthenticated) return;
          unawaited(refreshCalendarFromPushPayload(ref, data));
        });
        pushService.setOnNotificationTap((data) {
          debugPrint('═══ NOTIFICATION TAP HANDLER (FCM) ═══');
          debugPrint('Notification tapped - payload: $data');

          // Extract notification details
          final idRaw = data['notificationId'] ?? data['id'];
          final type = data['type'] as String?;
          final appointmentId = data['appointmentId'];
          final patientId = data['patientId'];
          final documentId = data['documentId'];
          final documentTitle = data['documentTitle'] as String?;
          final documentAccessRequestId = data['documentAccessRequestId'];
          final taskId = data['taskId'];

          debugPrint('Type: $type');
          debugPrint('AppointmentId: $appointmentId');
          debugPrint('PatientId: $patientId');
          debugPrint('DocumentId: $documentId');
          debugPrint('DocumentTitle: $documentTitle');
          debugPrint('DocumentAccessRequestId: $documentAccessRequestId');
          debugPrint('TaskId: $taskId');

          // Best-effort mark-as-read
          if (idRaw != null) {
            final id = int.tryParse(idRaw.toString());
            if (id != null && id > 0) {
              debugPrint('Marking notification $id as read...');
              Future.microtask(() async {
                try {
                  final api = ref.read(apiClientProvider);
                  await api.put('/api/notifications/$id/read', <String, dynamic>{});
                  debugPrint('✓ Notification $id marked as read');
                } catch (e) {
                  debugPrint('✗ Failed to mark notification as read: $e');
                }
              });
            }
          }

          // AI Scribe ready: refresh notes for that appointment so open screen shows new draft; then navigate to appointment
          if (type == 'AI_SCRIBE_READY' && appointmentId != null) {
            final aid = appointmentId.toString();
            ref.invalidate(draftNotesForAppointmentProvider(aid));
            ref.invalidate(consultationNotesForAppointmentProvider(aid));
          }

          // Navigate based on notification type and available IDs
          debugPrint('Determining navigation (priority: document access > appointment > task > patient)...');

          // Priority 1: Document access (REQUEST / APPROVED / REJECTED) → Patients screen with patient + document (highlight or open PDF)
          final isDocAccess = type == 'DOCUMENT_ACCESS_REQUEST' ||
              type == 'DOCUMENT_ACCESS_APPROVED' ||
              type == 'DOCUMENT_ACCESS_REJECTED';
          if (isDocAccess && patientId != null && documentId != null) {
            debugPrint('→ Document access: patient $patientId, document $documentId, openViewer=${type == 'DOCUMENT_ACCESS_APPROVED'}');
            ref.read(shellProvider.notifier).setTab(3);
            navigatorKey.currentState?.pushNamedAndRemoveUntil(
              AppRoutes.shell,
              (route) => false,
            );
            // Push patientsWithSelection into the shell's nested navigator
            // (NOT the outer one) so the sidebar/scaffold remains visible.
            // Pushing on `navigatorKey` would render PatientsScreen via the
            // outer router which renders it standalone, and any subsequent
            // pop (e.g. closing the document viewer) would land on a
            // sidebar-less Patients screen.
            _pushIntoShell(<String, dynamic>{
              'patientId': patientId.toString(),
              'documentId': documentId.toString(),
              'documentTitle': documentTitle ?? 'Document',
              'openDocumentViewer': type == 'DOCUMENT_ACCESS_APPROVED',
            });
            return;
          }

          // Priority 2b: Chat message → open conversation
          if (type == 'CHAT_MESSAGE' || type == 'NEW_MESSAGE') {
            final chatRaw = data['chatId'] ?? data['conversationId'] ?? data['entityId'];
            final cid = int.tryParse(chatRaw?.toString() ?? '');
            if (cid != null && cid > 0) {
              ref.read(notificationPendingConversationIdProvider.notifier).state = cid;
              ref.read(shellProvider.notifier).setTab(DoctorShellTab.chat);
              navigatorKey.currentState?.pushNamedAndRemoveUntil(
                AppRoutes.shell,
                (route) => false,
              );
              return;
            }
          }

          // Priority 3: Appointment (fetch day first, then navigate so calendar opens on correct date)
          if (appointmentId != null) {
            final id = int.tryParse(appointmentId.toString());
            if (id != null && id > 0) {
              final startAtRaw = data['appointmentStartAt'];
              _openCalendarToAppointment(
                ref,
                id,
                appointmentStartAt: startAtRaw?.toString(),
              );
              return;
            }
          }

          // Priority 3: Task completed → Remote care task details
          if (type == 'TASK_COMPLETED' && taskId != null) {
            final tid = int.tryParse(taskId.toString());
            if (tid != null && tid > 0) {
              debugPrint('→ Opening task $tid');
              ref.read(notificationPendingTaskIdProvider.notifier).state = tid;
              ref.read(shellProvider.notifier).setTab(5);
              navigatorKey.currentState?.pushNamedAndRemoveUntil(
                AppRoutes.shell,
                (route) => false,
              );
              return;
            }
          }

          // Priority 4: Patient only (navigate to patients screen)
          if (patientId != null) {
            debugPrint('→ Has patientId: $patientId');
            ref.read(shellProvider.notifier).setTab(3);
            navigatorKey.currentState?.pushNamedAndRemoveUntil(
              AppRoutes.shell,
              (route) => false,
            );
            _pushIntoShell(patientId.toString());
            return;
          }

          // Default: open notifications screen
          debugPrint('→ No navigation data (documentId, appointmentId, patientId all null)');
          debugPrint('→ Navigating to Notifications screen (default)');
          navigatorKey.currentState?.pushNamed(AppRoutes.notifications);
        });
        // Deliver any pending notification that opened the app.
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (!mounted) return;
          pushService.processPendingInitialMessage();
        });
      });
    }

    // Browser tab title: show unread chat count like a messenger (e.g. "(1) Shifa Doctor").
    // Admin app should not poll chat unread count (doctor-only endpoint).
    if (!isAdminHost) {
      ref.listen<AsyncValue<int>>(unreadCountProvider, (prev, next) {
        final count = next.valueOrNull ?? 0;
        setWebTitle(count > 0 ? '($count) Shifa Doctor' : 'Shifa Doctor');
      });
    }
    
    return AppLockLifecycleLayer(
      child: ActivityTracker(
      child: ScrollConfiguration(
        behavior: const ShifaScrollBehavior(),
        child: MaterialApp(
        navigatorKey: navigatorKey,
        title: 'Shifa Doctor',
        debugShowCheckedModeBanner: false,
        theme: buildTheme(),
        locale: languageState.locale,
        supportedLocales: const [
          Locale('en'), // English
          Locale('uz'), // Uzbek (Latin)
          Locale.fromSubtags(languageCode: 'uz', scriptCode: 'Cyrl'), // Uzbek (Cyrillic)
          Locale('ru'), // Russian
        ],
        localizationsDelegates: const [
          AppLocalizations.delegate,
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        onGenerateRoute: AppRouter.onGenerateRoute,
        // Admin URL: start at admin login. Doctor URL: start at splash -> login
        initialRoute: isAdminHost ? AppRoutes.adminLogin : AppRoutes.splash,
      ),
      ),
    ),
    );
  }
}
