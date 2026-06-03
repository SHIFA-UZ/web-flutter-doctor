import 'package:flutter/material.dart';
import 'package:shifa_doc_app_v1/core/layout/responsive.dart';

/// Shows a height-constrained, keyboard-safe bottom sheet for multi-field forms.
Future<T?> showScrollableFormBottomSheet<T>({
  required BuildContext context,
  required Widget Function(BuildContext context) builder,
  double maxHeightFactor = 0.92,
  bool includeBottomNavClearance = true,
  bool isDismissible = true,
  bool enableDrag = true,
  bool showDragHandle = false,
}) {
  return showModalBottomSheet<T>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    isDismissible: isDismissible,
    enableDrag: enableDrag,
    showDragHandle: showDragHandle,
    builder: (ctx) {
      final bottomInset = MediaQuery.viewInsetsOf(ctx).bottom;
      final maxHeight = MediaQuery.sizeOf(ctx).height * maxHeightFactor;
      return Padding(
        padding: EdgeInsets.only(bottom: bottomInset),
        child: ConstrainedBox(
          constraints: BoxConstraints(maxHeight: maxHeight),
          child: builder(ctx),
        ),
      );
    },
  );
}

/// Standard scroll wrapper for bottom-sheet form bodies.
Widget scrollableBottomSheetContent(
  BuildContext context, {
  required Widget child,
  bool includeBottomNavClearance = true,
  EdgeInsetsGeometry? padding,
}) {
  final bottomPad =
      includeBottomNavClearance ? Responsive.bottomNavClearance(context) : 0.0;
  return SingleChildScrollView(
    padding: padding ??
        EdgeInsets.fromLTRB(16, 16, 16, 16 + bottomPad),
    child: child,
  );
}

/// Bottom sheet with a pinned footer (e.g. Save) and scrollable body.
Future<T?> showScrollableFormBottomSheetWithFooter<T>({
  required BuildContext context,
  required Widget Function(BuildContext context) bodyBuilder,
  required Widget footer,
  double maxHeightFactor = 0.92,
  bool includeBottomNavClearance = true,
}) {
  return showScrollableFormBottomSheet<T>(
    context: context,
    maxHeightFactor: maxHeightFactor,
    includeBottomNavClearance: includeBottomNavClearance,
    builder: (ctx) {
      final bottomPad = includeBottomNavClearance
          ? Responsive.bottomNavClearance(ctx)
          : 0.0;
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Expanded(
            child: SingleChildScrollView(
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
              child: bodyBuilder(ctx),
            ),
          ),
          Padding(
            padding: EdgeInsets.fromLTRB(16, 8, 16, 16 + bottomPad),
            child: footer,
          ),
        ],
      );
    },
  );
}

/// Dialog that becomes fullscreen on mobile with a scrollable body.
Future<T?> showScrollableFormDialog<T>({
  required BuildContext context,
  required Widget title,
  required Widget content,
  List<Widget>? actions,
  bool barrierDismissible = true,
}) {
  if (Responsive.isMobile(context)) {
    return showDialog<T>(
      context: context,
      barrierDismissible: barrierDismissible,
      builder: (ctx) {
        final l10nClose = IconButton(
          icon: const Icon(Icons.close),
          onPressed: () => Navigator.pop(ctx),
        );
        return Dialog.fullscreen(
          child: Scaffold(
            appBar: AppBar(
              title: title,
              leading: l10nClose,
              automaticallyImplyLeading: false,
            ),
            body: SingleChildScrollView(
              padding: const EdgeInsets.all(16),
              child: content,
            ),
            bottomNavigationBar: actions == null || actions.isEmpty
                ? null
                : SafeArea(
                    child: Padding(
                      padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.end,
                        children: actions,
                      ),
                    ),
                  ),
          ),
        );
      },
    );
  }

  return showDialog<T>(
    context: context,
    barrierDismissible: barrierDismissible,
    builder: (ctx) => AlertDialog(
      title: title,
      content: SingleChildScrollView(
        child: SizedBox(
          width: Responsive.dialogMaxWidth(ctx),
          child: content,
        ),
      ),
      actions: actions,
    ),
  );
}
