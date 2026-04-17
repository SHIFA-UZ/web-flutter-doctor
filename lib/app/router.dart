import 'package:flutter/material.dart';

// Feature imports
import 'package:shifa_doc_app_v1/features/auth/presentation/splash_screen.dart';
import 'package:shifa_doc_app_v1/features/auth/presentation/verify_key_screen.dart';
import 'package:shifa_doc_app_v1/features/auth/presentation/login_screen.dart';
import 'package:shifa_doc_app_v1/features/auth/presentation/create_account/create_account_screen.dart';
import 'package:shifa_doc_app_v1/features/auth/presentation/create_account/account_information_screen.dart';
import 'package:shifa_doc_app_v1/features/schedule/presentation/setup_schedule_screen.dart';
import 'package:shifa_doc_app_v1/features/shell/presentation/main_shell.dart';
import 'package:shifa_doc_app_v1/features/appointments/presentation/in_person_appointment_screen.dart';
import 'package:shifa_doc_app_v1/features/appointments/presentation/waiting_room_screen.dart';
import 'package:shifa_doc_app_v1/features/appointments/presentation/video_call_screen.dart';
import 'package:shifa_doc_app_v1/features/appointments/domain/appointment_models.dart';

/// Central list of route names used across the app.
class AppRoutes {
  static const splash = '/';
  static const verify = '/verify';
  static const login = '/login';
  static const createAccount = '/create';
  static const accountInfo = '/create/account-info';
  static const setupSchedule = '/create/schedule';
  static const shell = '/app';
  static const inPerson = '/appointment/in-person';
  static const waitingRoom = '/appointment/waiting-room';
  static const videoCall = '/appointment/video-call';
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

      case AppRoutes.accountInfo:
        return MaterialPageRoute(
          builder: (_) => const AccountInformationScreen(),
        );

      case AppRoutes.setupSchedule:
        return MaterialPageRoute(builder: (_) => const SetupScheduleScreen());

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

      default:
        // Fallback: go to Splash
        return MaterialPageRoute(builder: (_) => const SplashScreen());
    }
  }
}
