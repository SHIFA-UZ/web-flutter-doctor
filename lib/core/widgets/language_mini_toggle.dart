import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/core/providers/language_provider.dart';
import 'package:shifa_doc_app_v1/app/app.dart';

/// Small, unobtrusive language dropdown used across the app.
/// Shows "EN", "UZ", and "RU" and updates the app locale immediately.
class LanguageMiniToggle extends ConsumerStatefulWidget {
  const LanguageMiniToggle({super.key});

  @override
  ConsumerState<LanguageMiniToggle> createState() => _LanguageMiniToggleState();
}

class _LanguageMiniToggleState extends ConsumerState<LanguageMiniToggle> {
  final GlobalKey _buttonKey = GlobalKey();

  @override
  Widget build(BuildContext context) {
    final lang = ref.watch(languageProvider).locale.languageCode;
    final brand = Theme.of(context).colorScheme.primary;

    // Get the Navigator's context for showing the popup menu
    final navigatorContext = navigatorKey.currentContext;
    
    // If Navigator context is not available yet, show a simple button that does nothing
    // This can happen during initial app load
    if (navigatorContext == null) {
      return Container(
        key: _buttonKey,
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(999),
          border: Border.all(color: Colors.black12),
          boxShadow: const [
            BoxShadow(
              color: Color(0x14000000),
              blurRadius: 8,
              offset: Offset(0, 2),
            ),
          ],
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              (lang == 'uz') ? 'UZ' : (lang == 'ru') ? 'RU' : 'EN',
              style: const TextStyle(
                fontSize: 12,
                fontWeight: FontWeight.w700,
                color: Colors.black87,
                letterSpacing: 0.3,
              ),
            ),
            const SizedBox(width: 6),
            Icon(Icons.keyboard_arrow_down, size: 18, color: brand),
          ],
        ),
      );
    }

    return Material(
      type: MaterialType.transparency,
      child: InkWell(
        borderRadius: BorderRadius.circular(999),
        onTap: () async {
          final buttonBox = _buttonKey.currentContext?.findRenderObject() as RenderBox?;
          if (buttonBox == null) return;
          
          // Get the Navigator's overlay state directly
          final navigatorState = navigatorKey.currentState;
          final overlay = navigatorState?.overlay;
          if (overlay == null) return;
          
          // Get overlay context - this context has access to the Overlay
          final overlayContext = overlay.context;
          final overlayBox = overlayContext.findRenderObject() as RenderBox?;
          if (overlayBox == null) return;

          final buttonPosition = buttonBox.localToGlobal(Offset.zero);
          final overlaySize = overlayBox.size;

          final selected = await showMenu<String>(
            context: overlayContext,
            position: RelativeRect.fromRect(
              Rect.fromLTWH(buttonPosition.dx, buttonPosition.dy, buttonBox.size.width, buttonBox.size.height),
              Offset.zero & overlaySize,
            ),
            items: const [
              PopupMenuItem(
                value: 'en',
                child: Text('EN'),
              ),
              PopupMenuItem(
                value: 'uz',
                child: Text('UZ'),
              ),
              PopupMenuItem(
                value: 'ru',
                child: Text('RU'),
              ),
            ],
          );

          if (selected != null) {
            ref.read(languageProvider.notifier).setLanguage(Locale(selected));
          }
        },
        child: Container(
          key: _buttonKey,
          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(999),
            border: Border.all(color: Colors.black12),
            boxShadow: const [
              BoxShadow(
                color: Color(0x14000000),
                blurRadius: 8,
                offset: Offset(0, 2),
              ),
            ],
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                (lang == 'uz') ? 'UZ' : (lang == 'ru') ? 'RU' : 'EN',
                style: const TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.w700,
                  color: Colors.black87,
                  letterSpacing: 0.3,
                ),
              ),
              const SizedBox(width: 6),
              Icon(Icons.keyboard_arrow_down, size: 18, color: brand),
            ],
          ),
        ),
      ),
    );
  }
}

