import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:http/http.dart' as http;
import 'package:shifa_doc_app_v1/core/api/ai_api.dart';
import 'package:shifa_doc_app_v1/core/api/api_providers.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/network/public_backend_config.dart';
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
  if (response.statusCode == 429) {
    var message = 'HTTP 429';
    try {
      final json = jsonDecode(response.body) as Map<String, dynamic>?;
      message = json?['message'] as String? ?? message;
    } catch (_) {}
    throw AiStreamException('RATE_LIMIT', message);
  }
  if (response.statusCode < 200 || response.statusCode >= 300) {
    throw Exception(response.body.isNotEmpty ? response.body : 'HTTP ${response.statusCode}');
  }
  final json = jsonDecode(response.body) as Map<String, dynamic>;
  return ((json['text'] as String?) ?? '').trim();
}

Future<void> postDoctorTranscriptionFeedback({
  required WidgetRef ref,
  required String transcript,
  required String localeHint,
  Uint8List? audioBytes,
  String? audioFileName,
}) async {
  final client = ref.read(apiClientProvider);
  final files = <http.MultipartFile>[];
  if (audioBytes != null && audioBytes.isNotEmpty) {
    final name = audioFileName?.trim().isNotEmpty == true ? audioFileName!.trim() : 'speech.m4a';
    files.add(http.MultipartFile.fromBytes('file', audioBytes, filename: name));
  }
  final streamed = await client.postMultipart(
    '/api/ai/transcription-feedback',
    files: files,
    fields: {
      'transcript': transcript,
      'locale': localeHint,
    },
  );
  final response = await http.Response.fromStream(streamed);
  if (response.statusCode == 404) {
    throw Exception('transcription_feedback_disabled');
  }
  if (response.statusCode == 429) {
    var message = 'HTTP 429';
    try {
      final json = jsonDecode(response.body) as Map<String, dynamic>?;
      message = json?['message'] as String? ?? message;
    } catch (_) {}
    throw AiStreamException('RATE_LIMIT', message);
  }
  if (response.statusCode < 200 || response.statusCode >= 300) {
    throw Exception(response.body.isNotEmpty ? response.body : 'HTTP ${response.statusCode}');
  }
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

/// Upload + append flow for doctor STT ([/api/ai/transcribe]); used by inline speech fields.
Future<void> completeDoctorTranscriptionFromRecording({
  required BuildContext context,
  required WidgetRef ref,
  required String filePath,
  required TextEditingController controller,
  VoidCallback? onTranscriptAppended,
}) async {
  final l10n = AppLocalizations.of(context)!;
  try {
    if (!context.mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(l10n.translate('transcribing'))),
    );
    final bytes = await readVoiceRecordingFileBytes(filePath);
    final name = voiceRecordingUploadFileName(filePath);
    final text = await postDoctorTranscription(
      ref: ref,
      fileBytes: bytes,
      fileName: name,
    );
    if (!context.mounted) return;
    ScaffoldMessenger.of(context).hideCurrentSnackBar();
    if (text.isEmpty || text == kNoSpeechTranscriptPlaceholder) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(l10n.translate('noSpeechDetected'))),
      );
      return;
    }
    appendTranscribedText(controller, text);
    onTranscriptAppended?.call();

    var feedbackEnabled = false;
    try {
      feedbackEnabled = await PublicBackendConfig.transcriptionFeedbackEnabled();
    } catch (_) {}
    if (!context.mounted) return;

    final hintLang = normalizedTranscriptionLanguageHint(ref);
    final messenger = ScaffoldMessenger.of(context);

    final addedLine = l10n.translate('transcriptionAdded');

    if (feedbackEnabled && text.trim().isNotEmpty) {
      messenger.showSnackBar(
        SnackBar(
          content: Text(
            '$addedLine\n${l10n.translate('transcriptionReportHint')}',
          ),
          backgroundColor: Colors.green,
          duration: const Duration(seconds: 10),
          action: SnackBarAction(
            label: l10n.translate('transcriptionReportAction'),
            onPressed: () async {
              try {
                await postDoctorTranscriptionFeedback(
                  ref: ref,
                  transcript: text,
                  localeHint: hintLang,
                  audioBytes: bytes,
                  audioFileName: name,
                );
                if (!context.mounted) return;
                ScaffoldMessenger.of(context).showSnackBar(
                  SnackBar(content: Text(l10n.translate('transcriptionReportThanks'))),
                );
              } catch (e) {
                if (!context.mounted) return;
                ScaffoldMessenger.of(context).showSnackBar(
                  SnackBar(
                    content: Text('${l10n.error}: $e'),
                    backgroundColor: Colors.red,
                  ),
                );
              }
            },
          ),
        ),
      );
    } else {
      messenger.showSnackBar(
        SnackBar(
          content: Text(addedLine),
          backgroundColor: Colors.green,
        ),
      );
    }
  } catch (e) {
    if (!context.mounted) return;
    ScaffoldMessenger.of(context).hideCurrentSnackBar();
    if (e is AiStreamException && e.code == 'RATE_LIMIT') {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(e.userFacingMessage), backgroundColor: Colors.red),
      );
      return;
    }
    final msg = e.toString();
    final clean = msg.startsWith('Exception: ') ? msg.substring(11) : msg;
    final localized = clean == 'speechToTextRequiresPro'
        ? l10n.translate('speechToTextRequiresPro')
        : '${l10n.error}: $clean';
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(localized), backgroundColor: Colors.red),
    );
  }
}
