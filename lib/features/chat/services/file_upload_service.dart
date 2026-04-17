// lib/features/chat/services/file_upload_service.dart
import 'dart:io';
import 'dart:typed_data';
import 'package:shifa_doc_app_v1/core/api/api_client.dart';
import 'package:http/http.dart' as http;
import 'dart:convert';

/// Upload file to backend and return public URL
/// This service handles image, voice, and document uploads
class FileUploadService {
  static Future<String?> uploadFile({
    required ApiClient apiClient,
    required File file,
    required String fileName,
    String? thumbnailPath, // For images: compressed thumbnail path
  }) async {
    try {
      // Read file bytes
      final fileBytes = await file.readAsBytes();
      
      // Create multipart request
      final uri = Uri.parse('${apiClient.baseUrl}/api/messages/upload-attachment');
      final request = http.MultipartRequest('POST', uri);
      
      // Add file
      request.files.add(
        http.MultipartFile.fromBytes(
          'file',
          fileBytes,
          filename: fileName,
        ),
      );
      
      // Add thumbnail if provided
      if (thumbnailPath != null) {
        final thumbnailFile = File(thumbnailPath);
        if (await thumbnailFile.exists()) {
          final thumbnailBytes = await thumbnailFile.readAsBytes();
          request.files.add(
            http.MultipartFile.fromBytes(
              'thumbnail',
              thumbnailBytes,
              filename: 'thumb_$fileName',
            ),
          );
        }
      }
      
      // Add auth headers
      final token = apiClient.getAuthToken();
      if (token != null) {
        request.headers['Authorization'] = 'Bearer $token';
      }
      
      // Send request
      final streamedResponse = await request.send();
      final response = await http.Response.fromStream(streamedResponse);
      
      if (response.statusCode >= 200 && response.statusCode < 300) {
        final json = jsonDecode(response.body) as Map<String, dynamic>;
        return json['url'] as String?;
      }
      
      throw Exception('Upload failed: ${response.statusCode} ${response.body}');
    } catch (e) {
      throw Exception('Error uploading file: $e');
    }
  }
  
  static Future<String?> uploadFileBytes({
    required ApiClient apiClient,
    required Uint8List fileBytes,
    required String fileName,
    Uint8List? thumbnailBytes,
  }) async {
    try {
      // Create multipart request
      final uri = Uri.parse('${apiClient.baseUrl}/api/messages/upload-attachment');
      final request = http.MultipartRequest('POST', uri);
      
      // Add file
      request.files.add(
        http.MultipartFile.fromBytes(
          'file',
          fileBytes,
          filename: fileName,
        ),
      );
      
      // Add thumbnail if provided
      if (thumbnailBytes != null) {
        request.files.add(
          http.MultipartFile.fromBytes(
            'thumbnail',
            thumbnailBytes,
            filename: 'thumb_$fileName',
          ),
        );
      }
      
      // Add auth headers
      final token = apiClient.getAuthToken();
      if (token != null) {
        request.headers['Authorization'] = 'Bearer $token';
      }
      
      // Send request
      final streamedResponse = await request.send();
      final response = await http.Response.fromStream(streamedResponse);
      
      if (response.statusCode >= 200 && response.statusCode < 300) {
        final json = jsonDecode(response.body) as Map<String, dynamic>;
        return json['url'] as String?;
      }
      
      throw Exception('Upload failed: ${response.statusCode} ${response.body}');
    } catch (e) {
      throw Exception('Error uploading file: $e');
    }
  }
}
