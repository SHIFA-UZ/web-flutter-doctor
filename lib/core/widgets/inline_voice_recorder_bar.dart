// Inline voice capture (chat voice messages, AI scribe, etc.) — same visuals as doctor field waveform.
import 'package:flutter/material.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/widgets/doctor_voice_recorder_session.dart';

/// Self-contained recorder strip: live waveform, cancel, confirm. Embed in chat composer or notes UI.
class InlineVoiceRecorderBar extends StatefulWidget {
  final Future<void> Function(String filePath, int durationSeconds) onRecordingComplete;
  final VoidCallback onCancel;
  final String? titleLabel;
  final String? confirmButtonLabel;

  const InlineVoiceRecorderBar({
    super.key,
    required this.onRecordingComplete,
    required this.onCancel,
    this.titleLabel,
    this.confirmButtonLabel,
  });

  @override
  State<InlineVoiceRecorderBar> createState() => _InlineVoiceRecorderBarState();
}

class _InlineVoiceRecorderBarState extends State<InlineVoiceRecorderBar> {
  final DoctorVoiceRecorderSession _session = DoctorVoiceRecorderSession();
  bool _recording = false;
  bool _finishing = false;
  late List<double> _waveLevels;

  @override
  void initState() {
    super.initState();
    _waveLevels = List<double>.filled(DoctorVoiceRecorderSession.waveBarCount, 0.12);
    WidgetsBinding.instance.addPostFrameCallback((_) => _begin());
  }

  @override
  void dispose() {
    _session.dispose();
    super.dispose();
  }

  Future<void> _begin() async {
    if (!mounted) return;
    setState(() => _recording = true);
    final err = await _session.start((level) {
      if (!mounted) return;
      setState(() {
        _waveLevels = List<double>.from(_waveLevels.skip(1))..add(level);
      });
    });
    if (!mounted) return;
    if (err != null) {
      setState(() => _recording = false);
      final l10n = AppLocalizations.of(context)!;
      if (err == 'no_permission') {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(l10n.microphonePermissionDenied),
            backgroundColor: Colors.red,
          ),
        );
      } else {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(l10n.errorRecordingVoice),
            backgroundColor: Colors.red,
          ),
        );
      }
      widget.onCancel();
    }
  }

  Future<void> _cancel() async {
    await _session.stop(discard: true);
    if (mounted) widget.onCancel();
  }

  Future<void> _submit() async {
    setState(() => _finishing = true);
    final path = await _session.stop(discard: false);
    final seconds = _session.lastRecordingDurationSeconds;
    if (!mounted) return;
    setState(() {
      _recording = false;
      _finishing = false;
    });
    if (path != null) {
      await widget.onRecordingComplete(path, seconds);
    } else {
      widget.onCancel();
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (widget.titleLabel != null && widget.titleLabel!.isNotEmpty)
          Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: Text(
              widget.titleLabel!,
              style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600),
            ),
          ),
        Container(
          padding: const EdgeInsets.only(left: 12, right: 4, top: 6, bottom: 6),
          decoration: BoxDecoration(
            color: const Color(0xFF3A3A3C).withValues(alpha: 0.94),
            borderRadius: BorderRadius.circular(999),
          ),
          child: Row(
            children: [
              Expanded(
                child: SizedBox(
                  height: 28,
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.end,
                    children: [
                      for (var i = 0; i < _waveLevels.length; i++)
                        Expanded(
                          child: Padding(
                            padding: const EdgeInsets.symmetric(horizontal: 0.5),
                            child: AnimatedContainer(
                              duration: const Duration(milliseconds: 72),
                              curve: Curves.easeOut,
                              height: 3 + _waveLevels[i] * 24,
                              decoration: BoxDecoration(
                                color: Color.lerp(
                                  Colors.white.withValues(alpha: 0.22),
                                  Colors.white.withValues(alpha: 0.78),
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
              ),
              IconButton(
                icon: const Icon(Icons.close, size: 20, color: Colors.white70),
                tooltip: l10n.cancel,
                onPressed: _finishing ? null : _cancel,
                padding: EdgeInsets.zero,
                constraints: const BoxConstraints(minWidth: 32, minHeight: 32),
              ),
              IconButton(
                icon: _finishing
                    ? const SizedBox(
                        width: 20,
                        height: 20,
                        child: CircularProgressIndicator(
                          strokeWidth: 2,
                          color: Colors.white70,
                        ),
                      )
                    : const Icon(Icons.check_rounded, size: 22, color: Colors.white70),
                tooltip: widget.confirmButtonLabel ?? l10n.processRecording,
                onPressed: _finishing || !_recording ? null : _submit,
                padding: EdgeInsets.zero,
                constraints: const BoxConstraints(minWidth: 32, minHeight: 32),
              ),
            ],
          ),
        ),
        const SizedBox(height: 6),
        Text(
          l10n.translate('voiceRecordingFinishHint'),
          style: TextStyle(
            fontSize: 11,
            color: Colors.grey.shade600,
            height: 1.3,
          ),
        ),
      ],
    );
  }
}
