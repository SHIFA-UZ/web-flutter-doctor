// lib/features/chat/presentation/widgets/voice_recording_dialog.dart
import 'dart:async';
import 'dart:io';

import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';
import 'package:path_provider/path_provider.dart';
import 'package:record/record.dart';

import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';

class _VoiceRecordingDialog extends StatefulWidget {
  final Function(String filePath, int durationSeconds) onRecordingComplete;
  final VoidCallback onCancel;
  final String? titleLabel;
  final String? sendButtonLabel;

  const _VoiceRecordingDialog({
    super.key,
    required this.onRecordingComplete,
    required this.onCancel,
    this.titleLabel,
    this.sendButtonLabel,
  });

  @override
  State<_VoiceRecordingDialog> createState() => _VoiceRecordingDialogState();
}

class _VoiceRecordingDialogState extends State<_VoiceRecordingDialog> {
  final AudioRecorder _audioRecorder = AudioRecorder();
  StreamSubscription<Amplitude>? _amplitudeSubscription;

  bool _isRecording = false;
  String? _recordingPath;
  final Stopwatch _stopwatch = Stopwatch();

  static const int _waveBarCount = 44;
  late List<double> _waveLevels;

  @override
  void initState() {
    super.initState();
    _waveLevels = List<double>.filled(_waveBarCount, 0.12);
    _startRecording();
  }

  @override
  void dispose() {
    _amplitudeSubscription?.cancel();
    _audioRecorder.dispose();
    super.dispose();
  }

  void _subscribeAmplitude() {
    _amplitudeSubscription?.cancel();
    _amplitudeSubscription =
        _audioRecorder.onAmplitudeChanged(const Duration(milliseconds: 85)).listen((amp) {
      if (!mounted || !_isRecording) return;
      final level = _dbToBarHeight(amp.current);
      setState(() {
        _waveLevels = List<double>.from(_waveLevels.skip(1))..add(level);
      });
    });
  }

  /// Map dBFS from [AudioRecorder.getAmplitude] to bar fill height in ~0..1.
  double _dbToBarHeight(double db) {
    if (db.isNaN || db.isInfinite) return 0.1;
    const floor = -55.0;
    const ceil = -8.0;
    if (db <= floor) return 0.08;
    if (db >= ceil) return 1.0;
    final t = (db - floor) / (ceil - floor);
    return (0.08 + t * 0.92).clamp(0.08, 1.0);
  }

  Future<void> _startRecording() async {
    try {
      if (await _audioRecorder.hasPermission()) {
        if (kIsWeb) {
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

        if (!mounted) return;
        setState(() => _isRecording = true);
        _stopwatch
          ..reset()
          ..start();
        _subscribeAmplitude();
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
    await _amplitudeSubscription?.cancel();
    _amplitudeSubscription = null;
    _stopwatch.stop();
    String? finalPath = _recordingPath;
    final durationSeconds = _stopwatch.elapsed.inSeconds;

    try {
      if (kIsWeb) {
        finalPath = await _audioRecorder.stop();
        if (finalPath == null || finalPath.isEmpty) {
          throw Exception('Failed to get recording path from web recorder');
        }
      } else {
        await _audioRecorder.stop();
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

    if (cancel) {
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
              Container(
                width: 72,
                height: 72,
                decoration: BoxDecoration(
                  color: Colors.red.shade50,
                  shape: BoxShape.circle,
                ),
                child: Icon(
                  Icons.mic_rounded,
                  size: 36,
                  color: Colors.red.shade700,
                ),
              ),
              const SizedBox(height: 14),
              Text(
                widget.titleLabel ?? l10n.voiceMessage,
                style: const TextStyle(
                  fontSize: 18,
                  fontWeight: FontWeight.w600,
                ),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 16),
              // Live input level — ChatGPT-style scrolling bars (not a stopwatch)
              SizedBox(
                height: 64,
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    for (var i = 0; i < _waveLevels.length; i++)
                      Expanded(
                        child: Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 0.5),
                          child: AnimatedContainer(
                            duration: const Duration(milliseconds: 70),
                            curve: Curves.easeOut,
                            height: 6 + _waveLevels[i] * 54,
                            decoration: BoxDecoration(
                              color: Color.lerp(
                                brand.withValues(alpha: 0.35),
                                brand,
                                _waveLevels[i],
                              ),
                              borderRadius: BorderRadius.circular(2),
                            ),
                          ),
                        ),
                      ),
                  ],
                ),
              ),
              const SizedBox(height: 8),
              Text(
                _formatDuration(_stopwatch.elapsed),
                style: TextStyle(
                  fontSize: 12,
                  color: Colors.grey.shade600,
                  fontFeatures: const [FontFeature.tabularFigures()],
                ),
              ),
              const SizedBox(height: 16),
              Text(
                l10n.translate('voiceRecordingFinishHint'),
                textAlign: TextAlign.center,
                style: TextStyle(
                  fontSize: 12,
                  color: Colors.grey.shade600,
                  height: 1.35,
                ),
              ),
              const SizedBox(height: 12),
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  TextButton(
                    onPressed: () => _stopRecording(cancel: true),
                    child: Text(l10n.cancel),
                  ),
                  const SizedBox(width: 16),
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

class VoiceRecordingDialog extends _VoiceRecordingDialog {
  const VoiceRecordingDialog({
    super.key,
    required super.onRecordingComplete,
    required super.onCancel,
    super.titleLabel,
    super.sendButtonLabel,
  });
}
