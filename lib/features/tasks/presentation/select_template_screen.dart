// lib/features/tasks/presentation/select_template_screen.dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/app/router.dart';
import 'package:shifa_doc_app_v1/core/api/api_providers.dart';
import 'package:shifa_doc_app_v1/features/tasks/domain/task_models.dart';
import 'package:shifa_doc_app_v1/features/shell/presentation/shell_scope.dart';
import 'package:shifa_doc_app_v1/state/tasks/task_actions.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/widgets/app_page_back_button.dart';

class SelectTemplateScreen extends ConsumerStatefulWidget {
  const SelectTemplateScreen({Key? key}) : super(key: key);

  @override
  ConsumerState<SelectTemplateScreen> createState() => _SelectTemplateScreenState();
}

class _SelectTemplateScreenState extends ConsumerState<SelectTemplateScreen> {
  List<TaskTemplate>? _templates;
  String? _error;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _loadTemplates();
  }

  Future<void> _loadTemplates() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final client = ref.read(apiClientProvider);
      final list = await fetchTemplatesWithClient(client: client);
      if (mounted) {
        setState(() {
          _templates = list;
          _loading = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = e.toString();
          _loading = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final brand = Theme.of(context).colorScheme.primary;

    return Scaffold(
      appBar: AppBar(
        leading: appBarBackLeading(context),
        automaticallyImplyLeading: false,
        title: Text(l10n.translate('useTemplate') ?? 'Use a template'),
        backgroundColor: brand,
        foregroundColor: Colors.white,
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
              ? Center(
                  child: Padding(
                    padding: const EdgeInsets.all(24),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text(_error!, textAlign: TextAlign.center),
                        const SizedBox(height: 16),
                        TextButton(
                          onPressed: _loadTemplates,
                          child: Text(l10n.translate('retry') ?? 'Retry'),
                        ),
                      ],
                    ),
                  ),
                )
              : _templates == null || _templates!.isEmpty
                  ? Center(
                      child: Text(
                        l10n.translate('noTemplatesYet') ?? 'No templates yet. Create a task first, then it will appear here.',
                        textAlign: TextAlign.center,
                        style: TextStyle(color: Colors.grey.shade600),
                      ),
                    )
                  : RefreshIndicator(
                      onRefresh: _loadTemplates,
                      child: ListView.builder(
                        padding: const EdgeInsets.all(16),
                        itemCount: _templates!.length,
                        itemBuilder: (context, index) {
                          final t = _templates![index];
                          String categoryName;
                          switch (t.category) {
                            case TaskCategory.vital:
                              categoryName = l10n.vital;
                              break;
                            case TaskCategory.exercise:
                              categoryName = l10n.exercise;
                              break;
                            case TaskCategory.medication:
                              categoryName = l10n.medication;
                              break;
                            case TaskCategory.other:
                              categoryName = l10n.taskOther;
                              break;
                          }
                          return Card(
                            margin: const EdgeInsets.only(bottom: 12),
                            child: ListTile(
                              title: Text(
                                t.taskName,
                                style: const TextStyle(fontWeight: FontWeight.w600),
                              ),
                              subtitle: Text(
                                '${l10n.category}: $categoryName · ${l10n.timesPerDay}: ${t.timesPerDay}',
                                style: TextStyle(
                                  color: Colors.grey.shade600,
                                  fontSize: 12,
                                ),
                              ),
                              trailing: const Icon(Icons.arrow_forward_ios, size: 16),
                              onTap: () {
                                ShellScope.pushNamed(
                                  context,
                                  AppRoutes.createTask,
                                  arguments: {'template': t},
                                ).then((_) {
                                  if (mounted) Navigator.of(context).maybePop();
                                });
                              },
                            ),
                          );
                        },
                      ),
                    ),
    );
  }
}
