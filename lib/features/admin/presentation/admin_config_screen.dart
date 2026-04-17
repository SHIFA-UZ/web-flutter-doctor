// lib/features/admin/presentation/admin_config_screen.dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/state/admin/admin_providers.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';

class AdminConfigScreen extends ConsumerWidget {
  const AdminConfigScreen({super.key});

  // Format camelCase or snake_case config keys to human-readable Title Case
  String _formatConfigKey(String key) {
    if (key.isEmpty) return key;

    // Handle snake_case (e.g., "max_upload_size" -> "Max Upload Size")
    if (key.contains('_')) {
      return key
          .split('_')
          .map((word) => word.isEmpty
              ? ''
              : word[0].toUpperCase() + word.substring(1).toLowerCase())
          .join(' ');
    }

    // Handle camelCase (e.g., "maxUploadSize" -> "Max Upload Size")
    final words = <String>[];
    final buffer = StringBuffer();

    for (int i = 0; i < key.length; i++) {
      final char = key[i];
      if (i > 0 && char == char.toUpperCase() && char != char.toLowerCase()) {
        words.add(buffer.toString());
        buffer.clear();
      }
      buffer.write(char);
    }
    if (buffer.isNotEmpty) words.add(buffer.toString());

    return words
        .map((word) => word.isEmpty
            ? ''
            : word[0].toUpperCase() + word.substring(1).toLowerCase())
        .join(' ');
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context)!;
    final configAsync = ref.watch(systemConfigProvider);

    return Scaffold(
      backgroundColor: Colors.grey.shade50,
      appBar: AppBar(
        title: Text(l10n.translate('systemConfiguration')),
        backgroundColor: Colors.white,
        elevation: 0,
        foregroundColor: Colors.black87,
      ),
      body: configAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(child: Text('${l10n.error}: $e')),
        data: (config) => ListView(
          padding: const EdgeInsets.all(16),
          children: config.entries.map((entry) {
            return Card(
              margin: const EdgeInsets.only(bottom: 12),
              child: ListTile(
                title: Text(_formatConfigKey(entry.key)),
                subtitle: Text(entry.value),
                trailing: IconButton(
                  icon: const Icon(Icons.edit),
                  onPressed: () => _showEditDialog(context, ref, entry.key, entry.value),
                ),
              ),
            );
          }).toList(),
        ),
      ),
    );
  }

  void _showEditDialog(
    BuildContext context,
    WidgetRef ref,
    String key,
    String currentValue,
  ) {
    final l10n = AppLocalizations.of(context)!;
    final controller = TextEditingController(text: currentValue);
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('${l10n.translate('editKey')} ${_formatConfigKey(key)}'),
        content: TextField(
          controller: controller,
          decoration: InputDecoration(
            labelText: l10n.translate('value'),
            border: const OutlineInputBorder(),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: Text(l10n.cancel),
          ),
          ShifaPrimaryButton(
            label: l10n.save,
            onPressed: () async {
              try {
                final actions = ref.read(adminActionsProvider);
                await actions.updateConfig(key, controller.text);
                ref.invalidate(systemConfigProvider);
                if (context.mounted) {
                  Navigator.pop(context);
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(content: Text(l10n.configUpdated)),
                  );
                }
              } catch (e) {
                if (context.mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(content: Text('${l10n.error}: $e')),
                  );
                }
              }
            },
          ),
        ],
      ),
    );
  }
}
