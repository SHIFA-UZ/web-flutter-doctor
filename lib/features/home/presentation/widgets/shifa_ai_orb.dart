import 'dart:math' as math;

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'package:shifa_doc_app_v1/core/constants/assets.dart';
import 'package:shifa_doc_app_v1/core/theme/app_colors.dart';
import 'package:shifa_doc_app_v1/core/theme/app_design_system.dart';

/// Voice-session phases already used by the intelligence conversation.
/// There is no separate connecting phase in the live session.
enum VoiceSessionPhase { idle, listening, thinking, speaking }

/// Maps a normalized mic level (about 0–1, as produced by the existing
/// recorder) into a calm animation target.
///
/// Levels at or below the recorder noise floor stay near rest so room noise
/// does not drive the orb. Non-listening phases ignore [level]: speaking and
/// thinking are state-driven because playback amplitude is not available.
double shifaOrbTargetIntensity({
  required VoiceSessionPhase phase,
  required double level,
  required bool reduceMotion,
}) {
  if (reduceMotion) {
    return switch (phase) {
      VoiceSessionPhase.listening => 0.2,
      VoiceSessionPhase.speaking => 0.28,
      VoiceSessionPhase.thinking => 0.12,
      VoiceSessionPhase.idle => 0.05,
    };
  }
  final safe = level.isFinite ? level.clamp(0.0, 1.0) : 0.0;
  return switch (phase) {
    VoiceSessionPhase.idle => 0.06,
    VoiceSessionPhase.thinking => 0.16,
    VoiceSessionPhase.speaking => 0.46,
    VoiceSessionPhase.listening => _listeningEnergy(safe),
  };
}

const double _noiseFloor = 0.16;

double _listeningEnergy(double level) {
  if (level <= _noiseFloor) return 0.08;
  return ((level - _noiseFloor) / (1 - _noiseFloor)).clamp(0.0, 1.0);
}

/// Fluid turquoise orb with the official Shifa mark held still in the centre.
class ShifaAiOrb extends StatefulWidget {
  const ShifaAiOrb({
    super.key,
    required this.phase,
    this.intensity,
    this.size = 240,
    this.reduceMotion = false,
  });

  final VoiceSessionPhase phase;

  /// Latest normalized level from the existing voice session. Null when the
  /// session has no level source.
  final ValueListenable<double>? intensity;
  final double size;
  final bool reduceMotion;

  static const logoKey = Key('shifa-ai-orb-logo');

  @override
  State<ShifaAiOrb> createState() => _ShifaAiOrbState();
}

class _ShifaAiOrbState extends State<ShifaAiOrb>
    with SingleTickerProviderStateMixin {
  late final AnimationController _motion;
  late final ValueNotifier<double> _smoothed;
  double _target = 0;
  DateTime _lastIntensityAt = DateTime.fromMillisecondsSinceEpoch(0);

  @override
  void initState() {
    super.initState();
    _smoothed = ValueNotifier<double>(0);
    _motion = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 10),
    );
    _motion.addListener(_smoothTowardTarget);
    widget.intensity?.addListener(_onIntensity);
    _applyTarget(immediate: true);
    _syncMotion();
  }

  void _syncMotion() {
    if (widget.reduceMotion) {
      _motion.stop();
      _motion.value = 0.18;
      return;
    }
    if (!_motion.isAnimating) {
      _motion.repeat();
    }
  }

  void _onIntensity() => _applyTarget();

  void _applyTarget({bool immediate = false}) {
    _lastIntensityAt = DateTime.now();
    _target = shifaOrbTargetIntensity(
      phase: widget.phase,
      level: widget.intensity?.value ?? 0,
      reduceMotion: widget.reduceMotion,
    );
    if (immediate || widget.reduceMotion) {
      _smoothed.value = _target;
    }
  }

  void _smoothTowardTarget() {
    if (widget.phase == VoiceSessionPhase.listening &&
        DateTime.now().difference(_lastIntensityAt) >
            const Duration(milliseconds: 280)) {
      _target = _target + (0.08 - _target) * 0.08;
    }
    final next = (_smoothed.value + (_target - _smoothed.value) * 0.18).clamp(
      0.0,
      1.0,
    );
    if ((next - _smoothed.value).abs() < 0.002) return;
    _smoothed.value = next;
  }

  @override
  void didUpdateWidget(ShifaAiOrb oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.intensity != widget.intensity) {
      oldWidget.intensity?.removeListener(_onIntensity);
      widget.intensity?.addListener(_onIntensity);
    }
    _applyTarget(immediate: widget.reduceMotion);
    _syncMotion();
  }

  @override
  void dispose() {
    widget.intensity?.removeListener(_onIntensity);
    _motion.removeListener(_smoothTowardTarget);
    _motion.dispose();
    _smoothed.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final logo = widget.size * 0.28;
    return SizedBox(
      width: widget.size,
      height: widget.size,
      child: Stack(
        alignment: Alignment.center,
        children: [
          RepaintBoundary(
            child: CustomPaint(
              size: Size.square(widget.size),
              isComplex: true,
              willChange: !widget.reduceMotion,
              painter: ShifaOrbPainter(
                time: _motion,
                energy: _smoothed,
                phase: widget.phase,
              ),
            ),
          ),
          IgnorePointer(
            child: Container(
              width: logo,
              height: logo,
              alignment: Alignment.center,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: Colors.white.withValues(alpha: 0.38),
              ),
              child: SvgPicture.asset(
                Assets.shifaMark,
                key: ShifaAiOrb.logoKey,
                width: logo * 0.72,
                height: logo * 0.72,
                fit: BoxFit.contain,
                semanticsLabel: 'Shifa',
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class ShifaOrbPainter extends CustomPainter {
  ShifaOrbPainter({
    required Animation<double> time,
    required ValueListenable<double> energy,
    required this.phase,
  }) : _time = time,
       _energy = energy,
       super(repaint: Listenable.merge([time, energy]));

  final Animation<double> _time;
  final ValueListenable<double> _energy;
  final VoiceSessionPhase phase;

  @override
  void paint(Canvas canvas, Size size) {
    final turns = _time.value;
    final angle = turns * math.pi * 2;
    final energy = _energy.value.clamp(0.0, 1.0);
    final center = Offset(size.width / 2, size.height / 2);
    final orbRadius = size.shortestSide * 0.33;

    _paintRipples(canvas, center, orbRadius, turns, energy);
    _paintGlow(canvas, center, orbRadius, energy);

    canvas.save();
    canvas.clipPath(
      Path()..addOval(Rect.fromCircle(center: center, radius: orbRadius)),
    );
    _paintBody(canvas, center, orbRadius);
    _paintFluid(canvas, center, orbRadius, angle, energy);
    _paintHighlight(canvas, center, orbRadius);
    canvas.restore();

    final rim = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.5
      ..color = Colors.white.withValues(alpha: 0.9);
    canvas.drawCircle(center, orbRadius - 0.8, rim);
  }

  void _paintRipples(
    Canvas canvas,
    Offset center,
    double orbRadius,
    double turns,
    double energy,
  ) {
    final rippleTurns = (turns * 2.4) % 1.0;
    final travel = switch (phase) {
      VoiceSessionPhase.idle => 0.12,
      VoiceSessionPhase.thinking => 0.18,
      VoiceSessionPhase.listening => 0.16 + energy * 0.16,
      VoiceSessionPhase.speaking =>
        0.2 + 0.1 * (0.5 + 0.5 * math.sin(turns * math.pi * 8)),
    };
    final alpha = switch (phase) {
      VoiceSessionPhase.idle => 0.10,
      VoiceSessionPhase.thinking => 0.14,
      VoiceSessionPhase.listening => 0.16 + energy * 0.22,
      VoiceSessionPhase.speaking => 0.2 + energy * 0.12,
    };
    for (var i = 0; i < 4; i++) {
      final wave = (rippleTurns + i / 4) % 1.0;
      final grow = wave * travel;
      final rx = orbRadius * (1.08 + grow);
      final ry = orbRadius * (1.02 + grow * 0.55);
      final paint = Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 1.35
        ..color = AppColors.primaryTeal.withValues(
          alpha: ((1 - wave) * alpha).clamp(0.0, 0.42),
        );
      canvas.drawOval(
        Rect.fromCenter(center: center, width: rx * 2, height: ry * 2),
        paint,
      );
    }
  }

  void _paintGlow(
    Canvas canvas,
    Offset center,
    double orbRadius,
    double energy,
  ) {
    final radius = orbRadius * (1.28 + energy * 0.08);
    final paint = Paint()
      ..shader = RadialGradient(
        colors: [
          AppColors.primaryTeal.withValues(alpha: 0.16 + energy * 0.1),
          AppColors.primaryTeal.withValues(alpha: 0),
        ],
      ).createShader(Rect.fromCircle(center: center, radius: radius));
    canvas.drawCircle(center, radius, paint);
  }

  void _paintBody(Canvas canvas, Offset center, double orbRadius) {
    final paint = Paint()
      ..shader = const RadialGradient(
        center: Alignment(0, 0.2),
        radius: 0.95,
        colors: [
          Color(0xFFF4FFFE),
          Color(0xFFC9F8F4),
          Color(0xFF3ADFD0),
          Color(0xFF00A99A),
        ],
        stops: [0.0, 0.34, 0.7, 1],
      ).createShader(Rect.fromCircle(center: center, radius: orbRadius));
    canvas.drawCircle(center, orbRadius, paint);
  }

  void _paintFluid(
    Canvas canvas,
    Offset center,
    double orbRadius,
    double angle,
    double energy,
  ) {
    final motion = switch (phase) {
      VoiceSessionPhase.idle => 0.45,
      VoiceSessionPhase.thinking => 0.7,
      VoiceSessionPhase.listening => 0.9 + energy * 0.7,
      VoiceSessionPhase.speaking => 1.25,
    };
    final drift = orbRadius * (0.16 + energy * 0.1);
    _blob(
      canvas,
      center +
          Offset(
            math.cos(angle * motion) * drift,
            math.sin(angle * motion * 0.85) * drift * 0.72,
          ),
      orbRadius * 0.7,
      const Color(0xFFFFFFFF),
      0.5,
    );
    _blob(
      canvas,
      center +
          Offset(
            math.cos(angle * motion * 1.25 + 2.1) * drift * 1.05,
            math.sin(angle * motion * 1.05 + 0.8) * drift,
          ),
      orbRadius * 0.58,
      const Color(0xFF8FF7F0),
      0.62,
    );
    _blob(
      canvas,
      center +
          Offset(
            math.cos(angle * motion * 0.75 + 4.0) * drift * 0.85,
            math.sin(angle * motion * 1.35 + 2.4) * drift * 0.9,
          ),
      orbRadius * 0.5,
      AppColors.primaryTeal,
      0.38,
    );
  }

  void _blob(
    Canvas canvas,
    Offset at,
    double radius,
    Color color,
    double alpha,
  ) {
    final paint = Paint()
      ..shader = RadialGradient(
        colors: [
          color.withValues(alpha: alpha),
          color.withValues(alpha: 0),
        ],
      ).createShader(Rect.fromCircle(center: at, radius: radius));
    canvas.drawCircle(at, radius, paint);
  }

  void _paintHighlight(Canvas canvas, Offset center, double orbRadius) {
    final at = center + Offset(-orbRadius * 0.28, -orbRadius * 0.34);
    final radius = orbRadius * 0.5;
    final paint = Paint()
      ..shader = RadialGradient(
        colors: [
          Colors.white.withValues(alpha: 0.72),
          Colors.white.withValues(alpha: 0),
        ],
      ).createShader(Rect.fromCircle(center: at, radius: radius));
    canvas.drawCircle(at, radius, paint);
  }

  @override
  bool shouldRepaint(ShifaOrbPainter oldDelegate) => oldDelegate.phase != phase;
}

/// Listening / speaking / thinking surface that replaces the audio-bar strip.
class ShifaVoiceOrbPanel extends StatelessWidget {
  const ShifaVoiceOrbPanel({
    super.key,
    required this.phase,
    required this.status,
    this.subtitle,
    this.caption,
    this.intensity,
    this.onStop,
    this.stopLabel = 'Stop',
    this.stopSemanticsLabel,
  });

  final VoiceSessionPhase phase;
  final String status;
  final String? subtitle;
  final String? caption;
  final ValueListenable<double>? intensity;
  final VoidCallback? onStop;
  final String stopLabel;
  final String? stopSemanticsLabel;

  static const stopKey = Key('shifa-voice-orb-stop');

  @override
  Widget build(BuildContext context) {
    final reduceMotion = MediaQuery.disableAnimationsOf(context);
    return LayoutBuilder(
      builder: (context, constraints) {
        final maxW = constraints.maxWidth.isFinite
            ? constraints.maxWidth
            : 360.0;
        final canvas = (maxW * 0.32).clamp(112.0, 148.0);
        return Container(
          width: double.infinity,
          padding: const EdgeInsets.fromLTRB(12, 8, 12, 10),
          decoration: BoxDecoration(
            color: Color.alphaBlend(
              AppColors.secondaryLight.withValues(alpha: 0.55),
              Colors.white,
            ),
            borderRadius: BorderRadius.circular(AppDesignSystem.cardRadius),
            border: Border.all(
              color: AppColors.primaryTeal.withValues(alpha: 0.38),
              width: 1.2,
            ),
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              ShifaAiOrb(
                phase: phase,
                intensity: intensity,
                size: canvas,
                reduceMotion: reduceMotion,
              ),
              const SizedBox(height: 2),
              ExcludeSemantics(child: _PhaseIcon(phase: phase)),
              const SizedBox(height: 2),
              Semantics(
                liveRegion: true,
                child: Text(
                  status,
                  textAlign: TextAlign.center,
                  style: AppDesignSystem.body2(context).copyWith(
                    color: AppColors.primaryTeal,
                    fontWeight: FontWeight.w700,
                    fontSize: 14,
                  ),
                ),
              ),
              if (subtitle != null && subtitle!.trim().isNotEmpty) ...[
                const SizedBox(height: 2),
                Text(
                  subtitle!,
                  textAlign: TextAlign.center,
                  style: AppDesignSystem.caption(
                    context,
                  ).copyWith(color: AppDesignSystem.textSecondary),
                ),
              ],
              if (caption != null && caption!.trim().isNotEmpty) ...[
                const SizedBox(height: 6),
                Text(
                  caption!,
                  textAlign: TextAlign.center,
                  style: AppDesignSystem.body2(
                    context,
                  ).copyWith(color: AppDesignSystem.textPrimary),
                ),
              ],
              if (onStop != null) ...[
                const SizedBox(height: 8),
                Semantics(
                  button: true,
                  label: stopSemanticsLabel ?? stopLabel,
                  onTap: onStop,
                  child: ExcludeSemantics(
                    child: TextButton.icon(
                      key: stopKey,
                      onPressed: onStop,
                      style: TextButton.styleFrom(
                        foregroundColor: AppColors.primaryTeal,
                        backgroundColor: AppColors.secondaryLight.withValues(
                          alpha: 0.85,
                        ),
                        minimumSize: const Size(72, 36),
                        padding: const EdgeInsets.symmetric(
                          horizontal: 14,
                          vertical: 6,
                        ),
                        shape: const StadiumBorder(
                          side: BorderSide(color: Color(0x6600BBB0)),
                        ),
                        tapTargetSize: MaterialTapTargetSize.padded,
                      ),
                      icon: const Icon(Icons.stop_rounded, size: 16),
                      label: Text(
                        stopLabel,
                        style: const TextStyle(fontWeight: FontWeight.w600),
                      ),
                    ),
                  ),
                ),
              ],
            ],
          ),
        );
      },
    );
  }
}

class _PhaseIcon extends StatelessWidget {
  const _PhaseIcon({required this.phase});

  final VoiceSessionPhase phase;

  @override
  Widget build(BuildContext context) {
    return switch (phase) {
      VoiceSessionPhase.thinking => const SizedBox(
        width: 18,
        height: 18,
        child: CircularProgressIndicator(
          strokeWidth: 2,
          color: AppColors.primaryTeal,
        ),
      ),
      VoiceSessionPhase.speaking => const Icon(
        Icons.graphic_eq,
        color: AppColors.primaryTeal,
        size: 22,
      ),
      VoiceSessionPhase.idle => const Icon(
        Icons.mic_none,
        color: AppColors.primaryTeal,
        size: 22,
      ),
      VoiceSessionPhase.listening => const Icon(
        Icons.mic,
        color: AppColors.primaryTeal,
        size: 22,
      ),
    };
  }
}
