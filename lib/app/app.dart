import 'package:flutter/material.dart';
import 'package:shifa_doc_app_v1/app/theme.dart';
import 'package:shifa_doc_app_v1/app/router.dart';
import 'package:shifa_doc_app_v1/features/auth/data/auth_repository_http.dart';
import 'package:shifa_doc_app_v1/core/services/session.dart';

class ShifaDoctorApp extends StatefulWidget {
  const ShifaDoctorApp({super.key});

  @override
  State<ShifaDoctorApp> createState() => _ShifaDoctorAppState();
}

class _ShifaDoctorAppState extends State<ShifaDoctorApp> {
  String _initialRoute = AppRoutes.splash;

  @override
  void initState() {
    super.initState();
    _bootstrap();
  }

  Future<void> _bootstrap() async {
    final restored = await AuthRepositoryHttp().tryRestore();
    setState(() {
      _initialRoute = restored ? AppRoutes.shell : AppRoutes.splash;
    });
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Shifa Doctor',
      debugShowCheckedModeBanner: false,
      theme: buildTheme(),
      onGenerateRoute: AppRouter.onGenerateRoute,
      initialRoute: _initialRoute,
    );
  }
}
