import 'package:flutter/material.dart';

// Feature imports
import 'package:shifa_doc_app_v1/features/auth/presentation/splash_screen.dart';
import 'package:shifa_doc_app_v1/features/auth/presentation/verify_key_screen.dart';
import 'package:shifa_doc_app_v1/features/auth/presentation/login_screen.dart';
import 'package:shifa_doc_app_v1/features/auth/presentation/create_account/create_account_screen.dart';
import 'package:shifa_doc_app_v1/features/auth/presentation/receptionist_create_account_screen.dart';
import 'package:shifa_doc_app_v1/features/auth/presentation/create_account/account_information_screen.dart';
import 'package:shifa_doc_app_v1/features/schedule/presentation/setup_schedule_screen.dart';
import 'package:shifa_doc_app_v1/features/shell/presentation/main_shell.dart';
import 'package:shifa_doc_app_v1/features/appointments/presentation/in_person_appointment_screen.dart';
import 'package:shifa_doc_app_v1/features/appointments/presentation/waiting_room_screen.dart';
import 'package:shifa_doc_app_v1/features/appointments/presentation/video_call_screen.dart';
import 'package:shifa_doc_app_v1/features/appointments/domain/appointment_models.dart';
import 'package:shifa_doc_app_v1/features/patients/presentation/patient_form_screen.dart';
import 'package:shifa_doc_app_v1/features/patients/presentation/patients_screen.dart';
import 'package:shifa_doc_app_v1/features/patients/domain/patient_models.dart';
import 'package:shifa_doc_app_v1/features/patients/domain/patient_form_models.dart';
import 'package:shifa_doc_app_v1/features/admin/presentation/admin_login_screen.dart';
import 'package:shifa_doc_app_v1/features/admin/presentation/admin_forgot_password_screen.dart';
import 'package:shifa_doc_app_v1/features/admin/presentation/admin_shell.dart';
import 'package:shifa_doc_app_v1/features/tasks/presentation/tasks_screen.dart';
import 'package:shifa_doc_app_v1/features/tasks/presentation/create_task_screen.dart';
import 'package:shifa_doc_app_v1/features/tasks/presentation/task_details_screen.dart';
import 'package:shifa_doc_app_v1/features/tasks/presentation/select_template_screen.dart';
import 'package:shifa_doc_app_v1/features/tasks/domain/task_models.dart';
import 'package:shifa_doc_app_v1/features/notifications/presentation/notifications_screen.dart';

/// Central list of route names used across the app.
class AppRoutes {
  static const splash = '/';
  static const verify = '/verify';
  static const login = '/login';
  static const createAccount = '/create';
  static const receptionistCreateAccount = '/create/receptionist';
  static const accountInfo = '/create/account-info';
  static const setupSchedule = '/create/schedule';
  static const shell = '/app';
  static const inPerson = '/appointment/in-person';
  static const waitingRoom = '/appointment/waiting-room';
  static const videoCall = '/appointment/video-call';
  static const patientForm = '/patient/form';
  /// Open Patients screen with a specific patient selected (e.g. from chat header).
  static const patientsWithSelection = '/app/patients/selection';
  static const tasks = '/tasks';
  static const createTask = '/tasks/create';
  static const selectTemplate = '/tasks/templates';
  static const taskDetails = '/tasks/:id';
  static const notifications = '/app/notifications';
  static const adminLogin = '/admin/login';
  static const adminForgotPassword = '/admin/forgot-password';
  static const adminShell = '/admin';
}

/// Central route generator.
/// Use: MaterialApp(onGenerateRoute: AppRouter.onGenerateRoute, initialRoute: AppRoutes.splash)
class AppRouter {
  static Route<dynamic> onGenerateRoute(RouteSettings settings) {
    switch (settings.name) {
      case AppRoutes.splash:
        return MaterialPageRoute(builder: (_) => const SplashScreen());

      case AppRoutes.verify:
        return MaterialPageRoute(builder: (_) => const VerifyKeyScreen());

      case AppRoutes.login:
        return MaterialPageRoute(builder: (_) => const LoginScreen());

      case AppRoutes.createAccount:
        return MaterialPageRoute(builder: (_) => const CreateAccountScreen());

      case AppRoutes.receptionistCreateAccount:
        return MaterialPageRoute(
          builder: (_) => const ReceptionistCreateAccountScreen(),
        );

      case AppRoutes.accountInfo:
        return MaterialPageRoute(
          builder: (_) => const AccountInformationScreen(),
        );

      case AppRoutes.setupSchedule:
        return MaterialPageRoute(builder: (_) => const ScheduleScreen());

      case AppRoutes.shell:
        return MaterialPageRoute(builder: (_) => const MainShell());

      case AppRoutes.inPerson:
        // Expect an Appointment passed in settings.arguments
        return MaterialPageRoute(
          builder: (_) => InPersonAppointmentScreen(
            appointment: settings.arguments as Appointment,
          ),
        );

      case AppRoutes.waitingRoom:
        return MaterialPageRoute(
          builder: (_) =>
              WaitingRoomScreen(appointment: settings.arguments as Appointment),
        );

      case AppRoutes.videoCall:
        return MaterialPageRoute(
          builder: (_) =>
              VideoCallScreen(appointment: settings.arguments as Appointment),
        );

      case AppRoutes.patientForm:
        final args = settings.arguments as Map<String, dynamic>;
        return MaterialPageRoute(
          builder: (_) => PatientFormScreen(
            patient: args['patient'] as Patient,
            templateId: args['templateId'] as String,
            existingForm: args['existingForm'] as PatientForm?,
          ),
        );

      case AppRoutes.tasks:
        return MaterialPageRoute(builder: (_) => const TasksScreen());

      case AppRoutes.notifications:
        return MaterialPageRoute(builder: (_) => const NotificationsScreen());

      case AppRoutes.patientsWithSelection:
        final rootArgs = settings.arguments;
        String? rootPatientId;
        String? rootDocumentId;
        String? rootDocumentTitle;
        bool rootOpenDocumentViewer = false;
        int? rootClinicId;
        if (rootArgs is Map) {
          rootPatientId = rootArgs['patientId']?.toString();
          rootDocumentId = rootArgs['documentId']?.toString();
          rootDocumentTitle = rootArgs['documentTitle'] as String?;
          rootOpenDocumentViewer = rootArgs['openDocumentViewer'] == true;
          final c = rootArgs['clinicId'];
          if (c is int) {
            rootClinicId = c;
          } else {
            rootClinicId = int.tryParse(c?.toString() ?? '');
          }
        } else if (rootArgs is String) {
          rootPatientId = rootArgs;
        }
        return MaterialPageRoute(
          builder: (_) => PatientsScreen(
            initialSelectedId: rootPatientId,
            initialDocumentIdToSelect: rootDocumentId,
            initialDocumentTitle: rootDocumentTitle,
            initialOpenDocumentViewer: rootOpenDocumentViewer,
            clinicWorkspaceId: rootClinicId,
          ),
        );

      case AppRoutes.createTask:
        final args = settings.arguments as Map<String, dynamic>?;
        return MaterialPageRoute(
          builder: (_) => CreateTaskScreen(
            patientId: args?['patientId'] as int?,
            template: args?['template'] as TaskTemplate?,
          ),
        );
      case AppRoutes.selectTemplate:
        return MaterialPageRoute(builder: (_) => const SelectTemplateScreen());

      case AppRoutes.taskDetails:
        final taskId = int.tryParse(settings.arguments as String? ?? '');
        if (taskId == null) {
          return MaterialPageRoute(builder: (_) => const TasksScreen());
        }
        return MaterialPageRoute(
          builder: (_) => TaskDetailsScreen(taskId: taskId),
        );

      case AppRoutes.adminLogin:
        return MaterialPageRoute(builder: (_) => const AdminLoginScreen());

      case AppRoutes.adminForgotPassword:
        return MaterialPageRoute(builder: (_) => const AdminForgotPasswordScreen());

      case AppRoutes.adminShell:
        return MaterialPageRoute(builder: (_) => const AdminShell());

      default:
        // Fallback: go to Splash
        return MaterialPageRoute(builder: (_) => const SplashScreen());
    }
  }

  /// Generates routes for the shell's nested navigator (sidebar stays visible).
  /// Returns null for unknown routes so the shell can show its default tab content.
  static Route<dynamic>? shellOnGenerateRoute(RouteSettings settings) {
    switch (settings.name) {
      case AppRoutes.inPerson:
        return MaterialPageRoute(
          builder: (_) => InPersonAppointmentScreen(
            appointment: settings.arguments as Appointment,
          ),
        );
      case AppRoutes.waitingRoom:
        return MaterialPageRoute(
          builder: (_) =>
              WaitingRoomScreen(appointment: settings.arguments as Appointment),
        );
      case AppRoutes.videoCall:
        return MaterialPageRoute(
          builder: (_) =>
              VideoCallScreen(appointment: settings.arguments as Appointment),
        );
      case AppRoutes.patientForm:
        final args = settings.arguments as Map<String, dynamic>;
        return MaterialPageRoute(
          builder: (_) => PatientFormScreen(
            patient: args['patient'] as Patient,
            templateId: args['templateId'] as String,
            existingForm: args['existingForm'] as PatientForm?,
          ),
        );
      case AppRoutes.createTask:
        final args = settings.arguments as Map<String, dynamic>?;
        return MaterialPageRoute(
          builder: (_) => CreateTaskScreen(
            patientId: args?['patientId'] as int?,
            template: args?['template'] as TaskTemplate?,
          ),
        );
      case AppRoutes.selectTemplate:
        return MaterialPageRoute(builder: (_) => const SelectTemplateScreen());
      case AppRoutes.taskDetails:
        final arg = settings.arguments;
        final taskId = arg is int ? arg : int.tryParse(arg?.toString() ?? '');
        if (taskId == null || taskId == 0) return null;
        return MaterialPageRoute(
          builder: (_) => TaskDetailsScreen(taskId: taskId),
        );
      case AppRoutes.setupSchedule:
        return MaterialPageRoute(builder: (_) => const ScheduleScreen());
      case AppRoutes.patientsWithSelection:
        final args = settings.arguments;
        String? patientId;
        String? documentId;
        String? documentTitle;
        bool openDocumentViewer = false;
        int? clinicWorkspaceId;
        if (args is Map) {
          patientId = args['patientId']?.toString();
          documentId = args['documentId']?.toString();
          documentTitle = args['documentTitle'] as String?;
          openDocumentViewer = args['openDocumentViewer'] == true;
          final c = args['clinicId'];
          if (c is int) {
            clinicWorkspaceId = c;
          } else {
            clinicWorkspaceId = int.tryParse(c?.toString() ?? '');
          }
        } else if (args is String) {
          patientId = args;
        }
        return MaterialPageRoute(
          builder: (_) => PatientsScreen(
            initialSelectedId: patientId,
            initialDocumentIdToSelect: documentId,
            initialDocumentTitle: documentTitle,
            initialOpenDocumentViewer: openDocumentViewer,
            clinicWorkspaceId: clinicWorkspaceId,
          ),
        );
      default:
        return null;
    }
  }
}
