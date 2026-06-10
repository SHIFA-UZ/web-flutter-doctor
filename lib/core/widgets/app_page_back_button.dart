import 'package:flutter/material.dart';
import 'package:shifa_doc_app_v1/features/shell/presentation/shell_scope.dart';

/// Pops one route from the shell nested navigator when available, otherwise
/// from the nearest [Navigator].
void appPop(BuildContext context, [Object? result]) {
  ShellScope.pop(context, result);
}

/// True when this screen was pushed on top of another route.
bool appCanPop(BuildContext context) => ShellScope.canPop(context);

/// AppBar [leading] that pops one step back. Returns null on root routes.
Widget? appBarBackLeading(BuildContext context) {
  if (!appCanPop(context)) return null;
  return IconButton(
    icon: const Icon(Icons.arrow_back),
    tooltip: MaterialLocalizations.of(context).backButtonTooltip,
    onPressed: () => appPop(context),
  );
}

/// Inline back control for screens without an AppBar (e.g. Patients overlay).
class AppPageBackButton extends StatelessWidget {
  const AppPageBackButton({
    super.key,
    this.label,
    this.onPressed,
  });

  final String? label;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    if (!appCanPop(context) && onPressed == null) {
      return const SizedBox.shrink();
    }
    final action = onPressed ?? () => appPop(context);
    if (label != null) {
      return TextButton.icon(
        onPressed: action,
        icon: const Icon(Icons.arrow_back),
        label: Text(label!),
      );
    }
    return IconButton(
      icon: const Icon(Icons.arrow_back),
      tooltip: MaterialLocalizations.of(context).backButtonTooltip,
      onPressed: action,
    );
  }
}
