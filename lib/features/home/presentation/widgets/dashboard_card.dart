import 'package:flutter/material.dart';
import 'package:shifa_doc_app_v1/core/theme/app_colors.dart';
import 'package:shifa_doc_app_v1/core/theme/app_design_system.dart';

/// Premium dashboard card shell with optional header and loading skeleton.
class DashboardCard extends StatelessWidget {
  const DashboardCard({
    super.key,
    required this.child,
    this.title,
    this.subtitle,
    this.trailing,
    this.padding,
    this.decoration,
    this.onTap,
  });

  final Widget child;
  final String? title;
  final String? subtitle;
  final Widget? trailing;
  final EdgeInsetsGeometry? padding;
  final BoxDecoration? decoration;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final content = Padding(
      padding: padding ?? const EdgeInsets.all(AppDesignSystem.cardPadding),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          if (title != null) ...[
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(title!, style: AppDesignSystem.h2(context)),
                      if (subtitle != null) ...[
                        const SizedBox(height: 4),
                        Text(subtitle!, style: AppDesignSystem.body2(context)),
                      ],
                    ],
                  ),
                ),
                if (trailing != null) trailing!,
              ],
            ),
            const SizedBox(height: 16),
          ],
          child,
        ],
      ),
    );

    final decorated = AnimatedContainer(
      duration: const Duration(milliseconds: 200),
      curve: Curves.easeOut,
      decoration: decoration ?? AppDesignSystem.cardDecoration(),
      child: content,
    );

    if (onTap == null) return decorated;

    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(AppDesignSystem.cardRadius),
        hoverColor: AppColors.primaryTeal.withValues(alpha: 0.04),
        child: decorated,
      ),
    );
  }
}

class DashboardSkeleton extends StatelessWidget {
  const DashboardSkeleton({
    super.key,
    this.height = 120,
    this.lines = 3,
  });

  final double height;
  final int lines;

  @override
  Widget build(BuildContext context) {
    return Container(
      height: height,
      decoration: AppDesignSystem.cardDecoration(
        color: AppDesignSystem.backgroundSecondary,
      ),
      padding: const EdgeInsets.all(AppDesignSystem.cardPadding),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: List.generate(lines, (i) {
          return Padding(
            padding: EdgeInsets.only(bottom: i < lines - 1 ? 10 : 0),
            child: _ShimmerBar(widthFactor: i == 0 ? 0.4 : (0.7 - i * 0.1)),
          );
        }),
      ),
    );
  }
}

class _ShimmerBar extends StatefulWidget {
  const _ShimmerBar({required this.widthFactor});

  final double widthFactor;

  @override
  State<_ShimmerBar> createState() => _ShimmerBarState();
}

class _ShimmerBarState extends State<_ShimmerBar>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1200),
    )..repeat();
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _controller,
      builder: (context, _) {
        return LayoutBuilder(
          builder: (context, constraints) {
            final w = constraints.maxWidth * widget.widthFactor;
            return Container(
              width: w,
              height: 12,
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(6),
                gradient: LinearGradient(
                  begin: Alignment(-1 + _controller.value * 2, 0),
                  end: Alignment(1 + _controller.value * 2, 0),
                  colors: const [
                    Color(0xFFE5E7EB),
                    Color(0xFFF3F4F6),
                    Color(0xFFE5E7EB),
                  ],
                ),
              ),
            );
          },
        );
      },
    );
  }
}
