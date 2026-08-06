import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:shifa_doc_app_v1/core/layout/responsive.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/theme/app_design_system.dart';
import 'package:shifa_doc_app_v1/core/widgets/safari_safe_dialog.dart';
import 'package:shifa_doc_app_v1/state/patients/patients_provider.dart';
import 'package:shifa_doc_app_v1/state/shell/shell_controller.dart';

/// Universal search overlay — triggered via Cmd+K / Ctrl+K.
class HomeSearchOverlay extends ConsumerStatefulWidget {
  const HomeSearchOverlay({super.key});

  static Future<void> show(BuildContext context) {
    return showDialog<void>(
      context: context,
      barrierColor: Colors.black54,
      builder: (_) => const HomeSearchOverlay(),
    );
  }

  @override
  ConsumerState<HomeSearchOverlay> createState() => _HomeSearchOverlayState();
}

class _HomeSearchOverlayState extends ConsumerState<HomeSearchOverlay> {
  final _query = TextEditingController();
  final _focus = FocusNode();

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _focus.requestFocus();
    });
  }

  @override
  void dispose() {
    _query.dispose();
    _focus.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final patients = ref.watch(patientsProvider);
    final q = _query.text.trim().toLowerCase();
    final results = q.isEmpty
        ? patients.take(8).toList()
        : patients
            .where((p) => p.name.toLowerCase().contains(q))
            .take(12)
            .toList();

    return Shortcuts(
      shortcuts: {
        LogicalKeySet(LogicalKeyboardKey.escape): const _DismissIntent(),
      },
      child: Actions(
        actions: {
          _DismissIntent: CallbackAction<_DismissIntent>(
            onInvoke: (_) {
              Navigator.pop(context);
              return null;
            },
          ),
        },
        child: SafariSafeDialog(
          child: Container(
            width: Responsive.overlayWidth(context, 520),
            decoration: AppDesignSystem.cardDecoration(),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                  Padding(
                    padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
                    child: TextField(
                      controller: _query,
                      focusNode: _focus,
                      autofocus: true,
                      decoration: InputDecoration(
                        hintText: l10n.translate('searchPatientsAppointmentsHint'),
                        prefixIcon: const Icon(Icons.search),
                        border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(12),
                        ),
                      ),
                      onChanged: (_) => setState(() {}),
                    ),
                  ),
                  ConstrainedBox(
                    constraints: const BoxConstraints(maxHeight: 360),
                    child: ListView.builder(
                      shrinkWrap: true,
                      itemCount: results.length,
                      itemBuilder: (context, i) {
                        final p = results[i];
                        return ListTile(
                          leading: CircleAvatar(
                            child: Text(
                              p.name.isNotEmpty ? p.name[0].toUpperCase() : '?',
                            ),
                          ),
                          title: Text(p.name),
                          subtitle: Text(p.general.phone ?? ''),
                          onTap: () {
                            Navigator.pop(context);
                            ref.read(shellProvider.notifier).setTab(3);
                          },
                        );
                      },
                    ),
                  ),
                  const SizedBox(height: 8),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _DismissIntent extends Intent {
  const _DismissIntent();
}

/// Keyboard shortcut wrapper for the home screen.
class HomeSearchShortcut extends StatelessWidget {
  const HomeSearchShortcut({super.key, required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Shortcuts(
      shortcuts: {
        LogicalKeySet(LogicalKeyboardKey.meta, LogicalKeyboardKey.keyK):
            const _OpenSearchIntent(),
        LogicalKeySet(LogicalKeyboardKey.control, LogicalKeyboardKey.keyK):
            const _OpenSearchIntent(),
      },
      child: Actions(
        actions: {
          _OpenSearchIntent: CallbackAction<_OpenSearchIntent>(
            onInvoke: (_) {
              HomeSearchOverlay.show(context);
              return null;
            },
          ),
        },
        child: Focus(autofocus: true, child: child),
      ),
    );
  }
}

class _OpenSearchIntent extends Intent {
  const _OpenSearchIntent();
}
