import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:http/http.dart' as http;
import 'package:shifa_doc_app_v1/core/api/api_providers.dart';
import 'package:shifa_doc_app_v1/core/providers/language_provider.dart';

const String kNoSpeechTranscriptPlaceholder = '(No speech detected)';

Future<Uint8List> readVoiceRecordingFileBytes(String filePath) async {
  if (kIsWeb) {
    if (filePath.startsWith('blob:')) {
      final response = await http.get(Uri.parse(filePath));
      if (response.statusCode != 200) {
        throw Exception('Failed to fetch blob: ${response.statusCode}');
      }
      return response.bodyBytes;
    }
    if (filePath.startsWith('data:')) {
      final base64String = filePath.split(',')[1];
      return base64Decode(base64String);
    }
    throw Exception('Invalid file path: $filePath');
  }
  final file = File(filePath);
  if (!await file.exists()) {
    throw Exception('Recording file not found');
  }
  return file.readAsBytes();
}

String voiceRecordingUploadFileName(String filePath) {
  if (kIsWeb) {
    return 'speech_${DateTime.now().millisecondsSinceEpoch}.wav';
  }
  return 'speech_${DateTime.now().millisecondsSinceEpoch}.m4a';
}

String normalizedTranscriptionLanguageHint(WidgetRef ref) {
  final uiLang = ref.read(languageProvider).locale.languageCode.toLowerCase();
  if (uiLang == 'uz' || uiLang == 'ru' || uiLang == 'en') return uiLang;
  return 'uz';
}

Future<String> postDoctorTranscription({
  required WidgetRef ref,
  required Uint8List fileBytes,
  required String fileName,
}) async {
  final client = ref.read(apiClientProvider);
  final multipartFile = http.MultipartFile.fromBytes(
    'file',
    fileBytes,
    filename: fileName,
  );
  final streamed = await client.postMultipart(
    '/api/ai/transcribe',
    files: [multipartFile],
    fields: {'languageHint': normalizedTranscriptionLanguageHint(ref)},
  );
  final response = await http.Response.fromStream(streamed);
  if (response.statusCode == 403) {
    throw Exception('speechToTextRequiresPro');
  }
  if (response.statusCode < 200 || response.statusCode >= 300) {
    throw Exception(response.body.isNotEmpty ? response.body : 'HTTP ${response.statusCode}');
  }
  final json = jsonDecode(response.body) as Map<String, dynamic>;
  return ((json['text'] as String?) ?? '').trim();
}

void appendTranscribedText(TextEditingController c, String text) {
  final t = text.trim();
  if (t.isEmpty || t == kNoSpeechTranscriptPlaceholder) return;
  final v = c.text;
  final sep = v.isEmpty
      ? ''
      : (v.endsWith('\n') || v.endsWith(' '))
      ? ''
      : ' ';
  c.text = '$v$sep$t';
  c.selection = TextSelection.collapsed(offset: c.text.length);
}
