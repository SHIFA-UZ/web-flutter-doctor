import 'dart:async';
import 'dart:io';

import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:path_provider/path_provider.dart';
import 'package:record/record.dart';

/// Lightweight mic session for inline voice UI (text fields, chat voice messages, AI scribe).
class DoctorVoiceRecorderSession {
  DoctorVoiceRecorderSession();

  final AudioRecorder _recorder = AudioRecorder();
  final Stopwatch _stopwatch = Stopwatch();
  StreamSubscription<Amplitude>? _amplitudeSub;
  String? _nativePath;
  int _lastDurationSeconds = 0;

  static const int waveBarCount = 40;

  /// Seconds for the last completed recording after [stop] (0 if none).
  int get lastRecordingDurationSeconds => _lastDurationSeconds;

  /// dBFS → ~0..1 bar height. Web mics often report quieter peaks.
  static double dbToBarHeight(double db) {
    if (db.isNaN || db.isInfinite) return 0.1;
    final floor = kIsWeb ? -70.0 : -55.0;
    final ceil = kIsWeb ? -12.0 : -8.0;
    if (db <= floor) return 0.08;
    if (db >= ceil) return 1.0;
    final t = (db - floor) / (ceil - floor);
    return (0.08 + t * 0.92).clamp(0.08, 1.0);
  }

  /// Start capture. [onLevel] receives normalized height for waveform bars.
  /// Returns null on success, or an error token if permission denied / start failed.
  Future<String?> start(void Function(double level) onLevel) async {
    _nativePath = null;
    if (!await _recorder.hasPermission()) {
      return 'no_permission';
    }
    try {
      // Ensure a clean start if a previous session left the recorder open.
      if (await _recorder.isRecording()) {
        try {
          await _recorder.stop();
        } catch (_) {}
      }

      if (kIsWeb) {
        // Prefer browser-native capture; wav path is more reliable for STT upload.
        await _recorder.start(
          const RecordConfig(
            encoder: AudioEncoder.wav,
            bitRate: 128000,
            sampleRate: 16000,
            numChannels: 1,
            autoGain: true,
            echoCancel: true,
            noiseSuppress: true,
          ),
          path: 'web_voice.wav',
        );
      } else {
        final directory = await getTemporaryDirectory();
        final timestamp = DateTime.now().millisecondsSinceEpoch;
        _nativePath = '${directory.path}/voice_$timestamp.m4a';
        await _recorder.start(
          const RecordConfig(
            encoder: AudioEncoder.aacLc,
            bitRate: 32000,
            sampleRate: 16000,
            numChannels: 1,
            autoGain: true,
            echoCancel: true,
            noiseSuppress: true,
          ),
          path: _nativePath!,
        );
      }

      if (!await _recorder.isRecording()) {
        return 'start_failed';
      }

      await _amplitudeSub?.cancel();
      _amplitudeSub =
          _recorder.onAmplitudeChanged(const Duration(milliseconds: 60)).listen(
        (amp) {
          // Prefer current; some web builds only update max.
          final db = amp.current.isFinite ? amp.current : amp.max;
          onLevel(dbToBarHeight(db));
        },
        onError: (_) {},
      );
      _stopwatch
        ..reset()
        ..start();
      return null;
    } catch (_) {
      return 'start_failed';
    }
  }

  /// Stop recorder. If [discard] is true, delete native file when applicable.
  /// Returns file path/buffer ref for upload, or null if cancelled / error.
  Future<String?> stop({required bool discard}) async {
    await _amplitudeSub?.cancel();
    _amplitudeSub = null;
    String? finalPath = _nativePath;
    try {
      if (kIsWeb) {
        finalPath = await _recorder.stop();
      } else {
        await _recorder.stop();
        finalPath = _nativePath;
      }
    } catch (_) {
      _stopwatch.stop();
      return null;
    }

    _stopwatch.stop();
    _lastDurationSeconds = _stopwatch.elapsed.inSeconds;

    if (discard) {
      if (finalPath != null && !kIsWeb) {
        try {
          final f = File(finalPath);
          if (await f.exists()) await f.delete();
        } catch (_) {}
      }
      return null;
    }
    return finalPath;
  }

  Future<void> dispose() async {
    await _amplitudeSub?.cancel();
    _amplitudeSub = null;
    await _recorder.dispose();
  }
}
