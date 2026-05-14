import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/subscription/doctor_subscription.dart';
import 'package:shifa_doc_app_v1/core/api/doctor_transcription_api.dart';
import 'package:shifa_doc_app_v1/core/widgets/doctor_voice_recorder_session.dart';
import 'package:shifa_doc_app_v1/state/subscription/doctor_subscription_provider.dart';

/// Standard outline/decoration vs. borderless expanding notes block.
enum DoctorSpeechInputStyle {
  standard,
  borderlessExpanding,
}

/// Text field with optional doctor STT: records in-place and shows a bottom waveform strip
/// (see product reference) plus cancel / confirm.
class DoctorSpeechTextField extends ConsumerStatefulWidget {
  const DoctorSpeechTextField({
    super.key,
    required this.controller,
    required this.decoration,
    this.style = DoctorSpeechInputStyle.standard,
    this.onTranscriptAppended,
    this.suffixBeforeMic,
    this.suffixBeforeMicBuilder,
    this.maxLines = 1,
    this.minLines,
    this.expands = false,
    this.textAlignVertical,
    this.keyboardType,
    this.textCapitalization = TextCapitalization.sentences,
    this.textStyle,
    this.validator,
    this.onChanged,
    this.focusNode,
    this.readOnly = false,
  });

  final TextEditingController controller;
  final InputDecoration decoration;
  final DoctorSpeechInputStyle style;
  final VoidCallback? onTranscriptAppended;

  /// Shown before the mic in the suffix row. Prefer [suffixBeforeMicBuilder] if widgets depend on controller text.
  final List<Widget>? suffixBeforeMic;

  /// Rebuilt when [controller] changes — use for dynamic trailing actions (e.g. ICD clear).
  final List<Widget> Function(BuildContext context)? suffixBeforeMicBuilder;

  final int? maxLines;
  final int? minLines;
  final bool expands;
  final TextAlignVertical? textAlignVertical;
  final TextInputType? keyboardType;
  final TextCapitalization textCapitalization;
  final TextStyle? textStyle;
  final String? Function(String?)? validator;
  final void Function(String)? onChanged;
  final FocusNode? focusNode;
  final bool readOnly;

  @override
  ConsumerState<DoctorSpeechTextField> createState() => _DoctorSpeechTextFieldState();
}

class _DoctorSpeechTextFieldState extends ConsumerState<DoctorSpeechTextField> {
  final DoctorVoiceRecorderSession _session = DoctorVoiceRecorderSession();
  bool _recording = false;
  bool _transcribing = false;
  late List<double> _waveLevels;

  @override
  void initState() {
    super.initState();
    _waveLevels = List<double>.filled(DoctorVoiceRecorderSession.waveBarCount, 0.12);
  }

  @override
  void dispose() {
    _session.dispose();
    super.dispose();
  }

  BorderRadius _borderRadiusFromDecoration(InputDecoration d) {
    final b = d.enabledBorder ?? d.border;
    if (b is OutlineInputBorder) {
      return b.borderRadius.resolve(TextDirection.ltr);
    }
    return const BorderRadius.all(Radius.circular(4));
  }

  Future<void> _beginRecording() async {
    if (_transcribing || !ref.read(doctorFeatureProvider(DoctorFeature.speechToText))) {
      return;
    }
    final l10n = AppLocalizations.of(context)!;
    setState(() {
      _recording = true;
      _waveLevels = List<double>.filled(DoctorVoiceRecorderSession.waveBarCount, 0.12);
    });

    final err = await _session.start((level) {
      if (!mounted) return;
      setState(() {
        _waveLevels = List<double>.from(_waveLevels.skip(1))..add(level);
      });
    });

    if (!mounted) return;
    if (err != null) {
      setState(() => _recording = false);
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
    }
  }

  Future<void> _cancelRecording() async {
    await _session.stop(discard: true);
    if (mounted) setState(() => _recording = false);
  }

  Future<void> _confirmRecording() async {
    setState(() => _transcribing = true);
    final path = await _session.stop(discard: false);
    if (!mounted) return;
    setState(() {
      _recording = false;
      _transcribing = false;
    });
    if (path != null) {
      await completeDoctorTranscriptionFromRecording(
        context: context,
        ref: ref,
        filePath: path,
        controller: widget.controller,
        onTranscriptAppended: widget.onTranscriptAppended,
      );
    }
  }

  Widget _micSuffixButton() {
    final l10n = AppLocalizations.of(context)!;
    return IconButton(
      icon: const Icon(Icons.mic_none_rounded, size: 22),
      tooltip: l10n.translate('speakToType'),
      padding: EdgeInsets.zero,
      constraints: const BoxConstraints(minWidth: 36, minHeight: 36),
      onPressed: _transcribing ? null : _beginRecording,
    );
  }

  Widget _suffixRowStandard() {
    return ListenableBuilder(
      listenable: widget.controller,
      builder: (context, _) {
        final extras =
            widget.suffixBeforeMicBuilder?.call(context) ?? widget.suffixBeforeMic ?? const <Widget>[];
        final mic = _micSuffixButton();
        if (extras.isEmpty) return mic;
        return Row(
          mainAxisSize: MainAxisSize.min,
          children: [...extras, mic],
        );
      },
    );
  }

  Widget? _standardSuffixIcon(bool allowed) {
    final deco = widget.decoration;
    if (!allowed) return deco.suffixIcon;
    if (_recording) return null;
    if (deco.suffixIcon != null) return deco.suffixIcon;
    return _suffixRowStandard();
  }

  Widget _wavePill(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return Container(
      padding: const EdgeInsets.only(left: 10, right: 4, top: 4, bottom: 4),
      decoration: BoxDecoration(
        color: const Color(0xFF3A3A3C).withValues(alpha: 0.94),
        borderRadius: BorderRadius.circular(999),
      ),
      child: Row(
        children: [
          Expanded(
            child: SizedBox(
              height: 26,
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
                          height: 3 + _waveLevels[i] * 23,
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
            onPressed: _transcribing ? null : _cancelRecording,
            padding: EdgeInsets.zero,
            constraints: const BoxConstraints(minWidth: 32, minHeight: 32),
            tooltip: l10n.cancel,
          ),
          IconButton(
            icon: _transcribing
                ? const SizedBox(
                    width: 20,
                    height: 20,
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      color: Colors.white70,
                    ),
                  )
                : const Icon(Icons.check_rounded, size: 22, color: Colors.white70),
            onPressed: (_transcribing || !_recording) ? null : _confirmRecording,
            padding: EdgeInsets.zero,
            constraints: const BoxConstraints(minWidth: 32, minHeight: 32),
            tooltip: l10n.processRecording,
          ),
        ],
      ),
    );
  }

  Widget _buildStandard(bool allowed) {
    final deco = widget.decoration;
    final basePadding = deco.contentPadding?.resolve(Directionality.of(context)) ??
        const EdgeInsets.symmetric(horizontal: 12, vertical: 16);
    final extraBottom = _recording ? 40.0 : 0.0;
    final mergedDecoration = deco.copyWith(
      contentPadding: EdgeInsets.fromLTRB(
        basePadding.left,
        basePadding.top,
        basePadding.right,
        basePadding.bottom + extraBottom,
      ),
      suffixIcon: _standardSuffixIcon(allowed),
    );

    final field = TextFormField(
      controller: widget.controller,
      focusNode: widget.focusNode,
      maxLines: widget.maxLines,
      minLines: widget.minLines,
      expands: widget.expands,
      textAlignVertical: widget.textAlignVertical,
      keyboardType: widget.keyboardType,
      textCapitalization: widget.textCapitalization,
      style: widget.textStyle,
      validator: widget.validator,
      onChanged: widget.onChanged,
      readOnly: widget.readOnly,
      decoration: mergedDecoration,
    );

    if (!allowed || !_recording) {
      return field;
    }

    return ClipRRect(
      borderRadius: _borderRadiusFromDecoration(deco),
      clipBehavior: Clip.antiAlias,
      child: Stack(
        clipBehavior: Clip.hardEdge,
        fit: StackFit.passthrough,
        children: [
          field,
          Positioned(
            left: 14,
            right: 14,
            bottom: 10,
            child: _wavePill(context),
          ),
        ],
      ),
    );
  }

  Widget _buildBorderlessExpanding(bool allowed) {
    final deco = widget.decoration;
    final basePadding = deco.contentPadding?.resolve(Directionality.of(context)) ??
        const EdgeInsets.fromLTRB(4, 8, 44, 8);
    final extraBottom = _recording ? 48.0 : 0.0;
    final mergedDecoration = deco.copyWith(
      contentPadding: EdgeInsets.fromLTRB(
        basePadding.left,
        basePadding.top,
        basePadding.right,
        basePadding.bottom + extraBottom,
      ),
    );

    final field = TextFormField(
      controller: widget.controller,
      focusNode: widget.focusNode,
      maxLines: widget.maxLines,
      minLines: widget.minLines,
      expands: widget.expands,
      textAlignVertical: widget.textAlignVertical,
      keyboardType: widget.keyboardType,
      textCapitalization: widget.textCapitalization,
      style: widget.textStyle,
      validator: widget.validator,
      onChanged: widget.onChanged,
      readOnly: widget.readOnly,
      decoration: mergedDecoration,
    );

    if (!allowed) return field;

    return Stack(
      clipBehavior: Clip.hardEdge,
      children: [
        field,
        if (!_recording)
          Positioned(
            top: 2,
            right: 0,
            child: IconButton(
              icon: const Icon(Icons.mic_none_rounded, size: 22),
              tooltip: AppLocalizations.of(context)!.translate('speakToType'),
              padding: EdgeInsets.zero,
              constraints: const BoxConstraints(minWidth: 36, minHeight: 36),
              onPressed: _transcribing ? null : _beginRecording,
            ),
          ),
        if (_recording)
          Positioned(
            left: 6,
            right: 6,
            bottom: 8,
            child: _wavePill(context),
          ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    final allowed = ref.watch(doctorFeatureProvider(DoctorFeature.speechToText));
    return switch (widget.style) {
      DoctorSpeechInputStyle.standard => _buildStandard(allowed),
      DoctorSpeechInputStyle.borderlessExpanding => _buildBorderlessExpanding(allowed),
    };
  }
}
