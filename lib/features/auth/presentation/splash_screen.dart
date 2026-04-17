import 'dart:math' as math;
import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:shifa_doc_app_v1/core/theme/app_colors.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'package:shifa_doc_app_v1/app/router.dart';
import 'package:shifa_doc_app_v1/core/constants/assets.dart';
import 'package:shifa_doc_app_v1/state/auth/auth_controller.dart';

class SplashScreen extends ConsumerStatefulWidget {
  const SplashScreen({super.key});

  @override
  ConsumerState<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends ConsumerState<SplashScreen>
    with SingleTickerProviderStateMixin {
  late final AnimationController _c;
  late final Animation<double> _t;

  @override
  void initState() {
    super.initState();
    _c = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1600),
    );
    _t = CurvedAnimation(parent: _c, curve: Curves.easeInOutCubic)
      ..addStatusListener((status) async {
        if (status == AnimationStatus.completed && mounted) {
          await Future.delayed(const Duration(milliseconds: 200));
          if (!mounted) return;
          // Restore session from storage (e.g. localStorage on web); stay logged in across refresh
          final restored = await ref.read(authProvider.notifier).restoreSession();
          if (!mounted) return;
          Navigator.pushReplacementNamed(
            context,
            restored ? AppRoutes.shell : AppRoutes.login,
          );
        }
      });
    _c.forward();
  }

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    const brand = AppColors.primaryTeal;
    return AnimatedBuilder(
      animation: _t,
      builder: (context, _) {
        final size = MediaQuery.of(context).size;
        final w = size.width;
        final h = size.height;

        final bg = Color.lerp(brand, Colors.white, _t.value)!;

        final blTranslate = Offset(
          _lerp(-0.15 * w, -0.55 * w, _t.value),
          _lerp(0.25 * h, 0.55 * h, _t.value),
        );
        final blScale = _lerp(1.35, 1.05, _t.value);
        final blRotation = _lerp(-10, -25, _t.value) * math.pi / 180.0;

        final trTranslate = Offset(
          _lerp(0.10 * w, 0.55 * w, _t.value),
          _lerp(-0.20 * h, -0.45 * h, _t.value),
        );
        final trScale = _lerp(1.10, 0.95, _t.value);
        final trRotation = _lerp(10, 22, _t.value) * math.pi / 180.0;

        final logoScale = _lerp(0.96, 1.00, _t.value);
        final logoOpacity = Curves.easeIn.transform(_t.value.clamp(0.0, 1.0));

        return Scaffold(
          backgroundColor: bg,
          body: Stack(
            fit: StackFit.expand,
            children: [
              _Blob(
                baseSize: math.max(w, h) * 1.0,
                translate: blTranslate,
                scale: blScale,
                rotation: blRotation,
                color: brand,
                blurSigma: 24,
              ),
              _Blob(
                baseSize: math.max(w, h) * 0.95,
                translate: trTranslate,
                scale: trScale,
                rotation: trRotation,
                color: brand.withOpacity(0.90),
                blurSigma: 28,
              ),
              Center(
                child: Opacity(
                  opacity: logoOpacity,
                  child: Transform.scale(
                    scale: logoScale,
                    child: SvgPicture.asset(
                      Assets.shifaLogo,
                      width: 96,
                      height: 96,
                      fit: BoxFit.contain,
                    ),
                  ),
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  double _lerp(double a, double b, double t) => a + (b - a) * t;
}

class _Blob extends StatelessWidget {
  final double baseSize;
  final Offset translate;
  final double scale;
  final double rotation;
  final Color color;
  final double blurSigma;

  const _Blob({
    required this.baseSize,
    required this.translate,
    required this.scale,
    required this.rotation,
    required this.color,
    this.blurSigma = 20,
  });

  @override
  Widget build(BuildContext context) {
    final size = baseSize * scale;
    return Transform.translate(
      offset: translate,
      child: Transform.rotate(
        angle: rotation,
        child: Center(
          child: ClipRRect(
            borderRadius: BorderRadius.circular(size * 0.45),
            child: ImageFiltered(
              imageFilter: ImageFilter.blur(
                sigmaX: blurSigma,
                sigmaY: blurSigma,
              ),
              child: Container(
                width: size * 1.2,
                height: size * 0.85,
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                    colors: [color.withOpacity(0.95), color.withOpacity(0.70)],
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
