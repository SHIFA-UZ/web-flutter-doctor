// lib/features/chat/services/image_compression_service.dart
import 'dart:io';
import 'package:flutter_image_compress/flutter_image_compress.dart';
import 'package:image_picker/image_picker.dart';

class ImageCompressionService {
  /// Compress image before upload
  /// Returns compressed file path
  static Future<XFile?> compressImage(XFile imageFile, {int quality = 85}) async {
    try {
      final file = File(imageFile.path);
      final fileSize = await file.length();
      
      // If file is already small (< 500KB), return as-is
      if (fileSize < 500 * 1024) {
        return imageFile;
      }

      // Get target path
      final targetPath = '${imageFile.path}_compressed.jpg';
      
      // Compress image
      final compressedFile = await FlutterImageCompress.compressAndGetFile(
        imageFile.path,
        targetPath,
        quality: quality,
        minWidth: 1920,
        minHeight: 1920,
      );

      if (compressedFile != null) {
        return XFile(compressedFile.path);
      }
      
      return imageFile;
    } catch (e) {
      // If compression fails, return original
      return imageFile;
    }
  }
}
