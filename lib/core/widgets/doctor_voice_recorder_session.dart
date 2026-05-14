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

  /// dBFS → ~0..1 bar height.
  static double dbToBarHeight(double db) {
    if (db.isNaN || db.isInfinite) return 0.1;
    const floor = -55.0;
    const ceil = -8.0;
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
      if (kIsWeb) {
        await _recorder.start(
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
        _nativePath = '${directory.path}/voice_$timestamp.m4a';
        await _recorder.start(
          const RecordConfig(
            encoder: AudioEncoder.aacLc,
            bitRate: 128000,
            sampleRate: 44100,
          ),
          path: _nativePath!,
        );
      }

      await _amplitudeSub?.cancel();
      _amplitudeSub = _recorder.onAmplitudeChanged(const Duration(milliseconds: 82)).listen((amp) {
        onLevel(dbToBarHeight(amp.current));
      });
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
