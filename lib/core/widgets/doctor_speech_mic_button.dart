import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/core/api/doctor_transcription_api.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/subscription/doctor_subscription.dart';
import 'package:shifa_doc_app_v1/features/chat/presentation/widgets/voice_recording_dialog.dart';
import 'package:shifa_doc_app_v1/state/subscription/doctor_subscription_provider.dart';

/// Mic control for doctors: records audio, transcribes via [/api/ai/transcribe], appends to [controller].
class DoctorSpeechMicButton extends ConsumerWidget {
  final TextEditingController controller;
  final VoidCallback? onTranscriptAppended;

  const DoctorSpeechMicButton({
    super.key,
    required this.controller,
    this.onTranscriptAppended,
  });

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final allowed = ref.watch(doctorFeatureProvider(DoctorFeature.speechToText));
    if (!allowed) {
      return const SizedBox.shrink();
    }
    final l10n = AppLocalizations.of(context)!;
    return IconButton(
      icon: const Icon(
        Icons.mic_none_rounded,
        size: 22,
      ),
      tooltip: l10n.translate('speakToType'),
      padding: EdgeInsets.zero,
      constraints: const BoxConstraints(minWidth: 36, minHeight: 36),
      onPressed: () {
        startDoctorSpeechToTextOverlay(
          context: context,
          ref: ref,
          controller: controller,
          onTranscriptAppended: onTranscriptAppended,
        );
      },
    );
  }
}

Future<void> startDoctorSpeechToTextOverlay({
  required BuildContext context,
  required WidgetRef ref,
  required TextEditingController controller,
  VoidCallback? onTranscriptAppended,
}) async {
  final l10n = AppLocalizations.of(context)!;
  late final OverlayEntry overlay;
  overlay = OverlayEntry(
    builder: (ctx) => Positioned(
      right: 24,
      bottom: 24,
      child: VoiceRecordingDialog(
        titleLabel: l10n.translate('speakToType'),
        sendButtonLabel: l10n.processRecording,
        onRecordingComplete: (filePath, _) async {
          overlay.remove();
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
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(
                content: Text(l10n.translate('transcriptionAdded')),
                backgroundColor: Colors.green,
              ),
            );
          } catch (e) {
            if (!context.mounted) return;
            ScaffoldMessenger.of(context).hideCurrentSnackBar();
            final msg = e.toString();
            final clean = msg.startsWith('Exception: ') ? msg.substring(11) : msg;
            final localized = clean == 'speechToTextRequiresPro'
                ? l10n.translate('speechToTextRequiresPro')
                : '${l10n.error}: $clean';
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(content: Text(localized), backgroundColor: Colors.red),
            );
          }
        },
        onCancel: () => overlay.remove(),
      ),
    ),
  );
  Overlay.of(context, rootOverlay: true).insert(overlay);
}
