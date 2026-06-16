import 'package:flutter/material.dart';

/// Wraps dialog content in a [Dialog] shell instead of [Center]+[Material].
///
/// Safari on macOS can mis-center and block backdrop taps when overlays use a
/// transparent [Material] centered manually inside [showDialog].
class SafariSafeDialog extends StatelessWidget {
  const SafariSafeDialog({
    super.key,
    required this.child,
    this.insetPadding = const EdgeInsets.all(24),
    this.alignment = Alignment.center,
  });

  final Widget child;
  final EdgeInsets insetPadding;
  final AlignmentGeometry alignment;

  @override
  Widget build(BuildContext context) {
    return Dialog(
      backgroundColor: Colors.transparent,
      elevation: 0,
      insetPadding: insetPadding,
      alignment: alignment,
      clipBehavior: Clip.none,
      child: child,
    );
  }
}
