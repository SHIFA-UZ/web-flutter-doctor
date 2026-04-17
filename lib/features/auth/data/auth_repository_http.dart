import 'package:dio/dio.dart';
import 'package:shifa_doc_app_v1/core/services/api_client.dart';
import 'package:shifa_doc_app_v1/core/services/session.dart';
import 'package:shared_preferences/shared_preferences.dart';

class AuthRepositoryHttp {
  final Dio _dio = ApiClient().dio;

  Future<void> login(String email, String password) async {
    final res = await _dio.post(
      '/auth/login',
      data: {'email': email, 'password': password},
    );
    final token = res.data['token'] as String;
    final doctor = res.data['doctor'] as Map<String, dynamic>;

    final s = Session();
    s.token = token;
    s.doctorId = doctor['id'] as String;
    s.doctorName = doctor['name'] as String;

    // Persist token between app restarts
    final sp = await SharedPreferences.getInstance();
    await sp.setString('token', token);
    await sp.setString('doctorId', s.doctorId!);
    await sp.setString('doctorName', s.doctorName!);
  }

  Future<void> logout() async {
    final s = Session();
    s.clear();
    final sp = await SharedPreferences.getInstance();
    await sp.remove('token');
    await sp.remove('doctorId');
    await sp.remove('doctorName');
  }

  Future<bool> tryRestore() async {
    final sp = await SharedPreferences.getInstance();
    final token = sp.getString('token');
    final id = sp.getString('doctorId');
    final name = sp.getString('doctorName');
    if (token != null && id != null) {
      final s = Session();
      s.token = token;
      s.doctorId = id;
      s.doctorName = name;
      return true;
    }
    return false;
  }
}
