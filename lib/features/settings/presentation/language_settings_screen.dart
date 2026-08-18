import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/providers/language_provider.dart';
import 'package:shifa_doc_app_v1/core/services/push_notification_service.dart';
import 'package:shifa_doc_app_v1/core/theme/app_colors.dart';
import 'package:shifa_doc_app_v1/core/widgets/app_page_back_button.dart';

/// Full-screen language picker (opened from mobile More → Language).
class LanguageSettingsScreen extends ConsumerWidget {
  const LanguageSettingsScreen({super.key});

  static const _options = <({String tag, String Function(AppLocalizations) label})>[
    (tag: 'en', label: _en),
    (tag: 'uz', label: _uz),
    (tag: kUzbekCyrillicLocaleTag, label: _uzCyrl),
    (tag: 'ru', label: _ru),
  ];

  static String _en(AppLocalizations l10n) => l10n.english;
  static String _uz(AppLocalizations l10n) => '${l10n.uzbek} (UZ)';
  static String _uzCyrl(AppLocalizations l10n) =>
      l10n.translate('uzbekCyrillicMenu');
  static String _ru(AppLocalizations l10n) => l10n.russian;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context)!;
    final current = ref.watch(languageProvider).locale.persistenceTag;
    final brand = AppColors.primaryTeal;

    return Scaffold(
      backgroundColor: AppColors.cardboard,
      appBar: AppBar(
        leading: appBarBackLeading(context),
        automaticallyImplyLeading: false,
        title: Text(l10n.selectLanguage),
        backgroundColor: AppColors.cardboard,
        foregroundColor: Colors.black,
        elevation: 0,
      ),
      body: ListView.separated(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
        itemCount: _options.length,
        separatorBuilder: (_, __) => const SizedBox(height: 8),
        itemBuilder: (context, index) {
          final option = _options[index];
          final selected = current == option.tag;
          return Material(
            color: selected
                ? brand.withValues(alpha: 0.08)
                : AppColors.card,
            borderRadius: BorderRadius.circular(12),
            child: InkWell(
              borderRadius: BorderRadius.circular(12),
              onTap: () async {
                if (selected) return;
                await ref
                    .read(languageProvider.notifier)
                    .setLanguage(localeFromPersistenceTag(option.tag));
                unawaited(PushNotificationService().refreshLocalizationCache());
                if (!context.mounted) return;
                ScaffoldMessenger.of(context).showSnackBar(
                  SnackBar(content: Text(l10n.languageChanged)),
                );
              },
              child: Padding(
                padding: const EdgeInsets.symmetric(
                  horizontal: 16,
                  vertical: 14,
                ),
                child: Row(
                  children: [
                    Expanded(
                      child: Text(
                        option.label(l10n),
                        style: TextStyle(
                          fontSize: 16,
                          fontWeight:
                              selected ? FontWeight.w600 : FontWeight.w500,
                        ),
                      ),
                    ),
                    Icon(
                      selected
                          ? Icons.radio_button_checked
                          : Icons.radio_button_off,
                      color: selected ? brand : Colors.grey.shade500,
                    ),
                  ],
                ),
              ),
            ),
          );
        },
      ),
    );
  }
}
