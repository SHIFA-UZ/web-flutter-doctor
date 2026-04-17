import 'package:dio/dio.dart';
import 'session.dart';

class ApiClient {
  static final ApiClient _singleton = ApiClient._internal();
  factory ApiClient() => _singleton;
  ApiClient._internal();

  // NOTE: override via --dart-define=API_BASE=... at run time
  final Dio dio =
      Dio(
          BaseOptions(
            baseUrl: const String.fromEnvironment(
              'API_BASE',
              defaultValue: 'http://localhost:4000',
            ),
            connectTimeout: const Duration(seconds: 10),
            receiveTimeout: const Duration(seconds: 10),
          ),
        )
        ..interceptors.add(
          InterceptorsWrapper(
            onRequest: (options, handler) {
              final s = Session();
              if (s.token != null) {
                options.headers['Authorization'] = 'Bearer ${s.token}';
              }
              handler.next(options);
            },
          ),
        );
}
