// lib/core/services/daily_video_service.dart
import 'dart:convert';
import '../api/api_client.dart';

class DailyVideoService {
  final ApiClient _apiClient;

  DailyVideoService(this._apiClient);

  /// Get video token from backend. Uses postNoUnauthorizedCheck so 403 "call has ended"
  /// does not trigger global logout — user sees an error message instead.
  Future<VideoTokenResponse> getVideoToken({
    required int appointmentId,
    String? roomName,
  }) async {
    try {
      final response = await _apiClient.postNoUnauthorizedCheck(
        '/api/video/token',
        {
          'appointmentId': appointmentId,
          if (roomName != null) 'roomName': roomName,
        },
      );

      if (response.statusCode == 200) {
        final data = json.decode(utf8.decode(response.bodyBytes));
        return VideoTokenResponse.fromJson(data);
      } else {
        final errorBody = response.body;
        final message = _parseVideoTokenErrorMessage(response.statusCode, errorBody);
        throw Exception(message);
      }
    } catch (e) {
      if (e is Exception) rethrow;
      throw Exception('Failed to get video token: $e');
    }
  }

  /// Extract a user-friendly message from video token error response (e.g. 403 call ended).
  static String _parseVideoTokenErrorMessage(int statusCode, String body) {
    try {
      final decoded = json.decode(body) as Map<String, dynamic>?;
      final msg = decoded?['message'] as String?;
      if (msg != null && msg.isNotEmpty) return msg;
    } catch (_) {}
    if (statusCode == 403) {
      return "Video call is not available. It may have ended, or the join window has closed (usually 15 minutes after the appointment end).";
    }
    return 'Failed to get video token: $statusCode - $body';
  }
}

class VideoTokenResponse {
  final String token;
  final String roomUrl;
  final String roomName;

  VideoTokenResponse({
    required this.token,
    required this.roomUrl,
    required this.roomName,
  });

  factory VideoTokenResponse.fromJson(Map<String, dynamic> json) {
    return VideoTokenResponse(
      token: json['token'] as String,
      roomUrl: json['roomUrl'] as String,
      roomName: json['roomName'] as String,
    );
  }
}
