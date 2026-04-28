import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import 'package:shifa_doc_app_v1/features/admin/domain/admin_models.dart';
import 'package:shifa_doc_app_v1/state/admin/admin_providers.dart';
import 'package:shifa_doc_app_v1/state/admin/admin_actions.dart';

class AdminPaymentsScreen extends ConsumerStatefulWidget {
  const AdminPaymentsScreen({super.key});

  @override
  ConsumerState<AdminPaymentsScreen> createState() => _AdminPaymentsScreenState();
}

class _AdminPaymentsScreenState extends ConsumerState<AdminPaymentsScreen> {
  final Set<int> _retryingIds = <int>{};
  final Set<int> _selectedIds = <int>{};

  @override
  Widget build(BuildContext context) {
    final webhooksAsync = ref.watch(failedStripeWebhooksProvider);
    return Scaffold(
      backgroundColor: Colors.grey.shade50,
      appBar: AppBar(
        title: const Text('Payments Ops'),
        backgroundColor: Colors.white,
        elevation: 0,
        foregroundColor: Colors.black87,
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: () => ref.invalidate(failedStripeWebhooksProvider),
          ),
        ],
      ),
      body: webhooksAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(child: Text('Failed to load failed webhooks: $e')),
        data: (items) {
          final itemById = {for (final i in items) i.id: i};
          _selectedIds.removeWhere((id) => !itemById.containsKey(id));
          final allSelected = items.isNotEmpty && _selectedIds.length == items.length;
          if (items.isEmpty) {
            return const Center(
              child: Text('No failed or unprocessed Stripe webhook events.'),
            );
          }
          return Column(
            children: [
              Container(
                color: Colors.white,
                padding: const EdgeInsets.fromLTRB(16, 10, 16, 10),
                child: Row(
                  children: [
                    Checkbox(
                      value: allSelected,
                      onChanged: (checked) {
                        setState(() {
                          if (checked == true) {
                            _selectedIds
                              ..clear()
                              ..addAll(items.map((e) => e.id));
                          } else {
                            _selectedIds.clear();
                          }
                        });
                      },
                    ),
                    Text('${_selectedIds.length} selected'),
                    const Spacer(),
                    FilledButton.icon(
                      onPressed: _selectedIds.isEmpty || _retryingIds.isNotEmpty
                          ? null
                          : () async {
                              final selectedEvents = _selectedIds
                                  .map((id) => itemById[id])
                                  .whereType<FailedWebhookEvent>()
                                  .toList();
                              final confirmed = await _confirmBulkRetry(context, selectedEvents);
                              if (confirmed != true) return;
                              await _retrySelected(ref.read(adminActionsProvider), selectedEvents);
                            },
                      icon: const Icon(Icons.refresh),
                      label: const Text('Retry selected'),
                    ),
                  ],
                ),
              ),
              Expanded(
                child: ListView.separated(
                  padding: const EdgeInsets.all(16),
                  itemCount: items.length,
                  separatorBuilder: (_, __) => const SizedBox(height: 10),
                  itemBuilder: (context, index) {
                    final it = items[index];
                    DateTime? created;
                    try {
                      created = DateTime.parse(it.createdAt).toLocal();
                    } catch (_) {}
                    return Card(
                      child: Padding(
                        padding: const EdgeInsets.all(12),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(
                              children: [
                                Checkbox(
                                  value: _selectedIds.contains(it.id),
                                  onChanged: (checked) {
                                    setState(() {
                                      if (checked == true) {
                                        _selectedIds.add(it.id);
                                      } else {
                                        _selectedIds.remove(it.id);
                                      }
                                    });
                                  },
                                ),
                                Expanded(
                                  child: Text(
                                    it.eventType,
                                    style: const TextStyle(fontWeight: FontWeight.w700),
                                  ),
                                ),
                                Container(
                                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                                  decoration: BoxDecoration(
                                    color: it.processed ? Colors.orange.shade100 : Colors.red.shade100,
                                    borderRadius: BorderRadius.circular(10),
                                  ),
                                  child: Text(
                                    it.processed ? 'FAILED' : 'UNPROCESSED',
                                    style: TextStyle(
                                      fontWeight: FontWeight.w700,
                                      color: it.processed ? Colors.orange.shade900 : Colors.red.shade900,
                                    ),
                                  ),
                                ),
                              ],
                            ),
                            const SizedBox(height: 6),
                            SelectableText('eventId: ${it.eventId}'),
                            const SizedBox(height: 4),
                            Text(
                              'created: ${created != null ? DateFormat('yyyy-MM-dd HH:mm:ss').format(created) : it.createdAt}',
                              style: TextStyle(color: Colors.grey.shade700),
                            ),
                            const SizedBox(height: 2),
                            Text(
                              'retryCount: ${it.retryCount}'
                              '${it.lastRetryAt != null ? ' · lastRetryAt: ${it.lastRetryAt}' : ''}'
                              '${it.retriedByAdminUserId != null ? ' · retriedByAdminUserId: ${it.retriedByAdminUserId}' : ''}',
                              style: TextStyle(color: Colors.grey.shade700, fontSize: 12),
                            ),
                            if ((it.failureReason ?? '').isNotEmpty) ...[
                              const SizedBox(height: 8),
                              Text(
                                it.failureReason!,
                                style: TextStyle(color: Colors.red.shade700),
                              ),
                            ],
                            const SizedBox(height: 10),
                            Align(
                              alignment: Alignment.centerRight,
                              child: FilledButton.icon(
                                onPressed: _retryingIds.contains(it.id)
                                    ? null
                                    : () async {
                                        final confirmed = await _confirmRetry(context, it.eventType, it.eventId);
                                        if (confirmed != true) return;
                                        await _retryEvent(ref.read(adminActionsProvider), it.id);
                                      },
                                icon: _retryingIds.contains(it.id)
                                    ? const SizedBox(
                                        width: 14,
                                        height: 14,
                                        child: CircularProgressIndicator(strokeWidth: 2),
                                      )
                                    : const Icon(Icons.refresh),
                                label: Text(_retryingIds.contains(it.id) ? 'Retrying...' : 'Retry'),
                              ),
                            ),
                          ],
                        ),
                      ),
                    );
                  },
                ),
              ),
            ],
          );
        },
      ),
    );
  }

  Future<bool?> _confirmRetry(BuildContext context, String eventType, String eventId) {
    return showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Retry webhook event?'),
        content: Text(
          'This will reprocess the stored Stripe webhook payload.\n\n'
          'eventType: $eventType\n'
          'eventId: $eventId',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(ctx).pop(true),
            child: const Text('Retry'),
          ),
        ],
      ),
    );
  }

  Future<bool?> _confirmBulkRetry(BuildContext context, List<FailedWebhookEvent> items) {
    return showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Retry selected webhook events?'),
        content: Text(
          'You are about to retry ${items.length} webhook event(s). '
          'Each selected event will be replayed from stored payload.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(ctx).pop(true),
            child: const Text('Retry selected'),
          ),
        ],
      ),
    );
  }

  Future<void> _retryEvent(AdminActions actions, int paymentEventId) async {
    setState(() => _retryingIds.add(paymentEventId));
    try {
      final ok = await actions.retryFailedStripeWebhook(paymentEventId);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(ok ? 'Webhook retried successfully.' : 'Retry attempted but still failing.'),
          backgroundColor: ok ? Colors.green : Colors.orange,
        ),
      );
      ref.invalidate(failedStripeWebhooksProvider);
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(e.toString().replaceFirst('Exception: ', '')),
          backgroundColor: Colors.red,
        ),
      );
    } finally {
      if (mounted) setState(() => _retryingIds.remove(paymentEventId));
    }
  }

  Future<void> _retrySelected(AdminActions actions, List<FailedWebhookEvent> selectedEvents) async {
    int successCount = 0;
    int failCount = 0;
    for (final event in selectedEvents) {
      setState(() => _retryingIds.add(event.id));
      try {
        final ok = await actions.retryFailedStripeWebhook(event.id);
        if (ok) {
          successCount += 1;
        } else {
          failCount += 1;
        }
      } catch (_) {
        failCount += 1;
      } finally {
        if (mounted) setState(() => _retryingIds.remove(event.id));
      }
    }
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text('Bulk retry complete: $successCount succeeded, $failCount failed.'),
        backgroundColor: failCount == 0 ? Colors.green : Colors.orange,
      ),
    );
    ref.invalidate(failedStripeWebhooksProvider);
  }
}
