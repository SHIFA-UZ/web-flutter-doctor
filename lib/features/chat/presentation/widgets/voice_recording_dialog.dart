// lib/features/chat/presentation/widgets/voice_recording_dialog.dart
import 'dart:async';
import 'dart:typed_data';
import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';
import 'package:flutter/scheduler.dart';
import 'package:record/record.dart';
import 'package:path_provider/path_provider.dart';
import 'dart:io';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';

class _VoiceRecordingDialog extends StatefulWidget {
  final Function(String filePath, int durationSeconds) onRecordingComplete;
  final VoidCallback onCancel;
  final String? titleLabel;
  final String? sendButtonLabel;

  const _VoiceRecordingDialog({
    required this.onRecordingComplete,
    required this.onCancel,
    this.titleLabel,
    this.sendButtonLabel,
  });

  @override
  State<_VoiceRecordingDialog> createState() => _VoiceRecordingDialogState();
}

class _VoiceRecordingDialogState extends State<_VoiceRecordingDialog>
    with SingleTickerProviderStateMixin {
  final AudioRecorder _audioRecorder = AudioRecorder();
  bool _isRecording = false;
  String? _recordingPath;
  Uint8List? _recordingBytes; // For web: store audio bytes
  final Stopwatch _stopwatch = Stopwatch();
  late final Ticker _ticker;
  double _slideOffset = 0.0;
  bool _shouldCancel = false;

  @override
  void initState() {
    super.initState();
    _ticker = createTicker((_) {
      if (_isRecording) {
        setState(() {});
      }
    });
    _startRecording();
  }

  @override
  void dispose() {
    _ticker.dispose();
    _audioRecorder.dispose();
    super.dispose();
  }

  Future<void> _startRecording() async {
    try {
      if (await _audioRecorder.hasPermission()) {
        if (kIsWeb) {
          // On web: lower quality reduces CPU and helps prevent browser stopping the stream early.
          // Path is required by API; stop() returns the blob URL.
          await _audioRecorder.start(
            const RecordConfig(
              encoder: AudioEncoder.wav,
              bitRate: 64000,
              sampleRate: 16000,
              numChannels: 1,
            ),
            path: 'web_voice.wav',
          );
        } else {
          // On mobile, use file path
          final directory = await getTemporaryDirectory();
          final timestamp = DateTime.now().millisecondsSinceEpoch;
          _recordingPath = '${directory.path}/voice_$timestamp.m4a';
          
          await _audioRecorder.start(
            const RecordConfig(
              encoder: AudioEncoder.aacLc,
              bitRate: 128000,
              sampleRate: 44100,
            ),
            path: _recordingPath!,
          );
        }

        setState(() => _isRecording = true);
        _stopwatch.reset();
        _stopwatch.start();
        _ticker.start();
      } else {
        if (mounted) {
          final l10n = AppLocalizations.of(context)!;
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(l10n.microphonePermissionDenied),
              backgroundColor: Colors.red,
            ),
          );
        }
        widget.onCancel();
      }
    } catch (e) {
      if (mounted) {
        final l10n = AppLocalizations.of(context)!;
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('${l10n.errorRecordingVoice}: $e'),
            backgroundColor: Colors.red,
          ),
        );
      }
      widget.onCancel();
    }
  }

  String _formatDuration(Duration duration) {
    final minutes = duration.inMinutes;
    final seconds = duration.inSeconds % 60;
    return '${minutes.toString().padLeft(2, '0')}:${seconds.toString().padLeft(2, '0')}';
  }

  Future<void> _stopRecording({bool cancel = false}) async {
    _ticker.stop();
    _stopwatch.stop();
    String? finalPath = _recordingPath;
    final durationSeconds = _stopwatch.elapsed.inSeconds;
    
    try {
      if (kIsWeb) {
        // On web, stop() returns the blob URL as String?
        finalPath = await _audioRecorder.stop();
        if (finalPath == null || finalPath.isEmpty) {
          throw Exception('Failed to get recording path from web recorder');
        }
      } else {
        await _audioRecorder.stop();
        // On mobile, we already have _recordingPath set
        finalPath = _recordingPath;
      }
    } catch (e) {
      if (mounted) {
        final l10n = AppLocalizations.of(context)!;
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('${l10n.errorRecordingVoice}: $e'),
            backgroundColor: Colors.red,
          ),
        );
      }
      widget.onCancel();
      return;
    }
    
    if (cancel || _shouldCancel) {
      // Delete recording file (only on mobile)
      if (finalPath != null && !kIsWeb) {
        try {
          final file = File(finalPath);
          if (await file.exists()) {
            await file.delete();
          }
        } catch (e) {
          // Ignore file deletion errors
        }
      }
      widget.onCancel();
    } else if (finalPath != null) {
      widget.onRecordingComplete(finalPath, durationSeconds);
    } else {
      // If no path was obtained, cancel
      widget.onCancel();
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final brand = Theme.of(context).colorScheme.primary;

    return Material(
      elevation: 12,
      borderRadius: BorderRadius.circular(16),
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 360),
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              // Recording indicator
              Container(
                width: 80,
                height: 80,
                decoration: BoxDecoration(
                  color: Colors.red.shade50,
                  shape: BoxShape.circle,
                ),
                child: Icon(
                  Icons.mic,
                  size: 40,
                  color: Colors.red.shade700,
                ),
              ),
              const SizedBox(height: 16),
              Text(
                widget.titleLabel ?? l10n.voiceMessage,
                style: const TextStyle(
                  fontSize: 18,
                  fontWeight: FontWeight.w600,
                ),
              ),
              const SizedBox(height: 8),
              Text(
                _formatDuration(_stopwatch.elapsed),
                style: TextStyle(
                  fontSize: 24,
                  fontWeight: FontWeight.bold,
                  color: brand,
                ),
              ),
              const SizedBox(height: 24),
              // Instructions
              Text(
                _shouldCancel ? 'Slide up to cancel' : 'Slide left to cancel',
                style: TextStyle(
                  fontSize: 14,
                  color: Colors.grey.shade600,
                ),
              ),
              const SizedBox(height: 16),
              // Action buttons
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  // Cancel button
                  TextButton(
                    onPressed: () => _stopRecording(cancel: true),
                    child: Text(l10n.cancel),
                  ),
                  const SizedBox(width: 16),
                  // Stop and send button
                  ShifaPrimaryButton(
                    onPressed: () => _stopRecording(),
                    icon: Icons.send,
                    label: widget.sendButtonLabel ?? l10n.sendVoice,
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

// Export as VoiceRecordingDialog
class VoiceRecordingDialog extends _VoiceRecordingDialog {
  const VoiceRecordingDialog({
    required super.onRecordingComplete,
    required super.onCancel,
    super.titleLabel,
    super.sendButtonLabel,
  });
}
