// lib/features/tasks/presentation/task_details_screen.dart
import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/core/api/api_providers.dart';
import 'package:shifa_doc_app_v1/features/tasks/domain/task_models.dart';
import 'package:shifa_doc_app_v1/state/tasks/task_actions.dart';
import 'package:shifa_doc_app_v1/state/tasks/tasks_provider.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';
import 'package:timezone/timezone.dart' as tz;
// Using manual date formatting instead of intl package

/// Polling interval for refetching task and check-ins (real-time sync).
const _kTaskDetailsPollInterval = Duration(seconds: 15);

class TaskDetailsScreen extends ConsumerStatefulWidget {
  final int taskId;

  const TaskDetailsScreen({Key? key, required this.taskId}) : super(key: key);

  @override
  ConsumerState<TaskDetailsScreen> createState() => _TaskDetailsScreenState();
}

class _TaskDetailsScreenState extends ConsumerState<TaskDetailsScreen> {
  Timer? _pollTimer;

  @override
  void initState() {
    super.initState();
    _pollTimer = Timer.periodic(_kTaskDetailsPollInterval, (_) {
      ref.invalidate(taskByIdProvider(widget.taskId));
      ref.invalidate(taskCheckInsProvider(widget.taskId));
    });
  }

  @override
  void dispose() {
    _pollTimer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final taskId = widget.taskId;
    final l10n = AppLocalizations.of(context)!;
    final brand = Theme.of(context).colorScheme.primary;
    final taskAsync = ref.watch(taskByIdProvider(taskId));
    final checkInsAsync = ref.watch(taskCheckInsProvider(taskId));

    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.taskDetails),
        backgroundColor: brand,
        foregroundColor: Colors.white,
      ),
      body: taskAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(child: Text('${l10n.error}: $e')),
        data: (task) {
          if (task == null) {
            return Center(child: Text(l10n.translate('taskNotFound') ?? 'Task not found'));
          }

          String statusName;
          switch (task.status) {
            case TaskStatus.active:
              statusName = l10n.active;
              break;
            case TaskStatus.completed:
              statusName = l10n.taskCompleted;
              break;
            case TaskStatus.expired:
              statusName = l10n.expired;
              break;
            case TaskStatus.cancelled:
              statusName = l10n.taskStatusCancelled;
              break;
            case TaskStatus.draft:
              statusName = l10n.draft;
              break;
          }

          String categoryName;
          switch (task.category) {
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

          String inputTypeName;
          switch (task.inputType) {
            case TaskInputType.numeric:
              inputTypeName = l10n.numeric;
              break;
            case TaskInputType.text:
              inputTypeName = l10n.text;
              break;
            case TaskInputType.boolean:
              inputTypeName = l10n.boolean;
              break;
          }

          return SingleChildScrollView(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Task Info Card
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Expanded(
                              child: Text(
                                task.taskName,
                                style: const TextStyle(
                                  fontSize: 24,
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                            ),
                            Container(
                              padding: const EdgeInsets.symmetric(
                                horizontal: 12,
                                vertical: 6,
                              ),
                              decoration: BoxDecoration(
                                color: _getStatusColor(task.status)
                                    .withOpacity(0.2),
                                borderRadius: BorderRadius.circular(8),
                              ),
                              child: Text(
                                statusName,
                                style: TextStyle(
                                  color: _getStatusColor(task.status),
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 12),
                        _InfoRow(label: l10n.patient, value: task.patientName),
                        _InfoRow(
                          label: l10n.category,
                          value: categoryName,
                        ),
                        _InfoRow(
                          label: l10n.inputType,
                          value: inputTypeName,
                        ),
                        if (task.inputLabel != null)
                          _InfoRow(
                            label: l10n.inputLabel,
                            value: task.inputLabel!,
                          ),
                        if (task.description != null)
                          _InfoRow(
                            label: l10n.description,
                            value: task.description!,
                          ),
                        _InfoRow(
                          label: l10n.startDate,
                          value: '${task.startDate.year}-${task.startDate.month.toString().padLeft(2, '0')}-${task.startDate.day.toString().padLeft(2, '0')}',
                        ),
                        if (task.endDate != null)
                          _InfoRow(
                            label: l10n.endDate,
                            value: '${task.endDate!.year}-${task.endDate!.month.toString().padLeft(2, '0')}-${task.endDate!.day.toString().padLeft(2, '0')}',
                          ),
                        if (task.durationDays != null)
                          _InfoRow(
                            label: l10n.duration,
                            value: '${task.durationDays} ${l10n.translate('days') ?? 'days'}',
                          ),
                        if (task.progress != null) ...[
                          const SizedBox(height: 16),
                          Text(
                            l10n.progress,
                            style: TextStyle(
                              fontSize: 16,
                              fontWeight: FontWeight.bold,
                              color: Colors.grey.shade700,
                            ),
                          ),
                          const SizedBox(height: 8),
                          Row(
                            children: [
                              Expanded(
                                child: LinearProgressIndicator(
                                  value: task.progress!.completionPercentage,
                                  backgroundColor: Colors.grey.shade200,
                                  valueColor:
                                      AlwaysStoppedAnimation<Color>(brand),
                                ),
                              ),
                              const SizedBox(width: 12),
                              Text(
                                '${task.progress!.completedCheckIns}/${task.progress!.totalCheckIns}',
                                style: const TextStyle(
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 8),
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceAround,
                            children: [
                              _ProgressStat(
                                label: l10n.checkInCompleted,
                                value: task.progress!.completedCheckIns,
                                color: Colors.green,
                              ),
                              _ProgressStat(
                                label: l10n.pending,
                                value: task.progress!.pendingCheckIns,
                                color: Colors.orange,
                              ),
                              _ProgressStat(
                                label: l10n.missed,
                                value: task.progress!.missedCheckIns,
                                color: Colors.red,
                              ),
                            ],
                          ),
                        ],
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 16),

                // Check-ins Section
                Text(
                  l10n.checkIns,
                  style: TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.bold,
                    color: Colors.grey.shade800,
                  ),
                ),
                const SizedBox(height: 12),
                checkInsAsync.when(
                  loading: () => const Center(
                    child: Padding(
                      padding: EdgeInsets.all(32),
                      child: CircularProgressIndicator(),
                    ),
                  ),
                  error: (e, _) => Center(child: Text('${l10n.error}: $e')),
                  data: (checkIns) {
                    if (checkIns.isEmpty) {
                      return Center(
                        child: Padding(
                          padding: const EdgeInsets.all(32),
                          child: Text(l10n.translate('noCheckInsFound') ?? 'No check-ins found'),
                        ),
                      );
                    }

                    return ListView.builder(
                      shrinkWrap: true,
                      physics: const NeverScrollableScrollPhysics(),
                      itemCount: checkIns.length,
                      itemBuilder: (context, index) {
                        final checkIn = checkIns[index];
                        return _CheckInCard(
                          checkIn: checkIn,
                          inputLabel: task.inputLabel,
                          onTap: () => _showCheckInDetailModal(
                            context,
                            l10n: l10n,
                            checkIn: checkIn,
                            inputLabel: task.inputLabel,
                            patientTimeZone: task.patientTimeZone,
                          ),
                        );
                      },
                    );
                  },
                ),
                const SizedBox(height: 16),

                // Cancel Button (if active)
                if (task.status == TaskStatus.active)
                  ShifaSecondaryButton(
                    label: l10n.translate('cancelTask') ?? 'Cancel Task',
                    width: ButtonWidth.fill,
                    variant: ButtonVariant.destructive,
                    onPressed: () async {
                      final confirm = await showDialog<bool>(
                        context: context,
                        builder: (ctx) => AlertDialog(
                          title: Text(l10n.translate('cancelTask') ?? 'Cancel Task'),
                          content: Text(
                            l10n.translate('cancelTaskConfirm') ?? 'Are you sure you want to cancel this task?',
                          ),
                          actions: [
                            TextButton(
                              onPressed: () => Navigator.pop(ctx, false),
                              child: Text(l10n.no),
                            ),
                            ShifaPrimaryButton(
                              label: l10n.translate('yesCancel') ?? 'Yes, Cancel',
                              variant: ButtonVariant.destructive,
                              onPressed: () => Navigator.pop(ctx, true),
                            ),
                          ],
                        ),
                      );

                      if (confirm == true) {
                        try {
                          final client = ref.read(apiClientProvider);
                          await cancelTaskWithClient(
                            client: client,
                            taskId: taskId,
                          );
                          ref.invalidate(taskByIdProvider(widget.taskId));
                          ref.read(tasksProvider.notifier).loadTasks();
                          if (context.mounted) {
                            Navigator.pop(context);
                            ScaffoldMessenger.of(context).showSnackBar(
                              SnackBar(
                                content: Text(l10n.taskCancelled),
                              ),
                            );
                          }
                        } catch (e) {
                          if (context.mounted) {
                            ScaffoldMessenger.of(context).showSnackBar(
                              SnackBar(
                                content: Text('${l10n.translate('failedToCancel') ?? 'Failed to cancel'}: $e'),
                                backgroundColor: Colors.red,
                              ),
                            );
                          }
                        }
                      }
                    },
                  ),
              ],
            ),
          );
        },
      ),
    );
  }

  Color _getStatusColor(TaskStatus status) {
    switch (status) {
      case TaskStatus.active:
        return Colors.green;
      case TaskStatus.completed:
        return Colors.blue;
      case TaskStatus.expired:
        return Colors.orange;
      case TaskStatus.cancelled:
        return Colors.red;
      case TaskStatus.draft:
        return Colors.grey;
    }
  }
}

class _InfoRow extends StatelessWidget {
  final String label;
  final String value;

  const _InfoRow({required this.label, required this.value});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 100,
            child: Text(
              '$label:',
              style: TextStyle(
                color: Colors.grey.shade600,
                fontWeight: FontWeight.w500,
              ),
            ),
          ),
          Expanded(
            child: Text(value),
          ),
        ],
      ),
    );
  }
}

class _ProgressStat extends StatelessWidget {
  final String label;
  final int value;
  final Color color;

  const _ProgressStat({
    required this.label,
    required this.value,
    required this.color,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Text(
          value.toString(),
          style: TextStyle(
            fontSize: 20,
            fontWeight: FontWeight.bold,
            color: color,
          ),
        ),
        Text(
          label,
          style: TextStyle(
            fontSize: 12,
            color: Colors.grey.shade600,
          ),
        ),
      ],
    );
  }
}

/// Effective display status: PENDING with scheduled time in the past shows as MISSED.
CheckInStatus effectiveCheckInStatus(TaskCheckIn checkIn) {
  if (checkIn.status == CheckInStatus.completed) return CheckInStatus.completed;
  if (checkIn.status == CheckInStatus.missed) return CheckInStatus.missed;
  final now = DateTime.now();
  final scheduled = DateTime(
    checkIn.scheduledDate.year,
    checkIn.scheduledDate.month,
    checkIn.scheduledDate.day,
    checkIn.scheduledTime?.hour ?? 23,
    checkIn.scheduledTime?.minute ?? 59,
  );
  return now.isAfter(scheduled) ? CheckInStatus.missed : CheckInStatus.pending;
}

void _showCheckInDetailModal(
  BuildContext context, {
  required AppLocalizations l10n,
  required TaskCheckIn checkIn,
  String? inputLabel,
  String? patientTimeZone,
}) {
  final effective = effectiveCheckInStatus(checkIn);
  String statusName;
  switch (effective) {
    case CheckInStatus.completed:
      statusName = l10n.checkInCompleted;
      break;
    case CheckInStatus.pending:
      statusName = l10n.pending;
      break;
    case CheckInStatus.missed:
      statusName = l10n.missed;
      break;
  }
  final scheduledStr = _formatScheduled(checkIn.scheduledDate, checkIn.scheduledTime);
  showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    builder: (ctx) => Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.of(ctx).viewPadding.bottom),
      child: SingleChildScrollView(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                l10n.translate('checkInDetails') ?? 'Check-in Details',
                style: Theme.of(ctx).textTheme.titleLarge?.copyWith(
                  fontWeight: FontWeight.bold,
                ),
              ),
              const SizedBox(height: 16),
              _ModalRow(
                label: l10n.translate('scheduled') ?? 'Scheduled',
                value: scheduledStr,
              ),
              _ModalRow(
                label: l10n.translate('status') ?? 'Status',
                value: statusName,
              ),
              if (effective == CheckInStatus.completed) ...[
                if (checkIn.completedAt != null)
                  _ModalRow(
                    label: l10n.translate('submittedAt') ?? 'Submitted at',
                    value: _formatSubmittedAtInPatientTz(checkIn.completedAt!, patientTimeZone),
                  ),
                _ModalValueRow(
                  l10n: l10n,
                  checkIn: checkIn,
                  inputLabel: inputLabel,
                ),
                if (checkIn.notes != null && checkIn.notes!.isNotEmpty)
                  _ModalRow(
                    label: l10n.notes,
                    value: checkIn.notes!,
                  ),
              ] else if (effective == CheckInStatus.missed) ...[
                const SizedBox(height: 8),
                Text(
                  l10n.translate('noSubmissionReceived') ?? 'No submission received',
                  style: TextStyle(color: Colors.grey.shade600),
                ),
              ] else ...[
                const SizedBox(height: 8),
                Text(
                  l10n.translate('awaitingSubmission') ?? 'Awaiting submission',
                  style: TextStyle(color: Colors.grey.shade600),
                ),
              ],
              const SizedBox(height: 24),
              ShifaPrimaryButton(
                label: l10n.translate('close') ?? 'Close',
                width: ButtonWidth.fill,
                onPressed: () => Navigator.pop(ctx),
              ),
            ],
          ),
        ),
      ),
    ),
  );
}

String _formatScheduled(DateTime date, TimeOfDay? time) {
  final d = '${date.year}-${date.month.toString().padLeft(2, '0')}-${date.day.toString().padLeft(2, '0')}';
  if (time == null) return d;
  return '$d ${time.hour.toString().padLeft(2, '0')}:${time.minute.toString().padLeft(2, '0')}';
}

/// Formats [utcDt] (UTC) in the assigned patient's timezone for display.
String _formatSubmittedAtInPatientTz(DateTime utcDt, String? patientTimeZone) {
  tz.Location loc;
  try {
    loc = tz.getLocation(patientTimeZone?.isNotEmpty == true ? patientTimeZone! : 'UTC');
  } catch (_) {
    loc = tz.UTC;
  }
  final utc = utcDt.isUtc
      ? utcDt
      : DateTime.utc(
          utcDt.year,
          utcDt.month,
          utcDt.day,
          utcDt.hour,
          utcDt.minute,
          utcDt.second,
          utcDt.millisecond,
        );
  final local = tz.TZDateTime.from(utc, loc);
  return '${local.year}-${local.month.toString().padLeft(2, '0')}-${local.day.toString().padLeft(2, '0')} '
      '${local.hour.toString().padLeft(2, '0')}:${local.minute.toString().padLeft(2, '0')}';
}

class _ModalRow extends StatelessWidget {
  final String label;
  final String value;

  const _ModalRow({required this.label, required this.value});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            label,
            style: TextStyle(
              fontSize: 12,
              color: Colors.grey.shade600,
              fontWeight: FontWeight.w500,
            ),
          ),
          const SizedBox(height: 2),
          Text(value, style: const TextStyle(fontSize: 16)),
        ],
      ),
    );
  }
}

class _ModalValueRow extends StatelessWidget {
  final AppLocalizations l10n;
  final TaskCheckIn checkIn;
  final String? inputLabel;

  const _ModalValueRow({
    required this.l10n,
    required this.checkIn,
    this.inputLabel,
  });

  @override
  Widget build(BuildContext context) {
    final label = inputLabel ?? (l10n.translate('value') ?? 'Value');
    String value;
    if (checkIn.numericValue != null) {
      value = checkIn.numericValue.toString();
    } else if (checkIn.textValue != null) {
      value = checkIn.textValue!;
    } else if (checkIn.booleanValue != null) {
      value = checkIn.booleanValue! ? l10n.yes : l10n.no;
    } else {
      value = '—';
    }
    return _ModalRow(label: label, value: value);
  }
}

class _CheckInCard extends StatelessWidget {
  final TaskCheckIn checkIn;
  final String? inputLabel;
  final VoidCallback? onTap;

  const _CheckInCard({
    required this.checkIn,
    this.inputLabel,
    this.onTap,
  });

  static String _formatDate(DateTime date) {
    return '${date.year}-${date.month.toString().padLeft(2, '0')}-${date.day.toString().padLeft(2, '0')}';
  }

  static String _formatTime(TimeOfDay time) {
    return '${time.hour.toString().padLeft(2, '0')}:${time.minute.toString().padLeft(2, '0')}';
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final effective = effectiveCheckInStatus(checkIn);
    final statusColor = _getStatusColor(effective);

    String statusName;
    switch (effective) {
      case CheckInStatus.completed:
        statusName = l10n.checkInCompleted;
        break;
      case CheckInStatus.pending:
        statusName = l10n.pending;
        break;
      case CheckInStatus.missed:
        statusName = l10n.missed;
        break;
    }

    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(12),
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text(
                      _formatDate(checkIn.scheduledDate),
                      style: const TextStyle(
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ),
                  if (checkIn.scheduledTime != null)
                    Text(
                      _formatTime(checkIn.scheduledTime!),
                      style: TextStyle(color: Colors.grey.shade600),
                    ),
                  const SizedBox(width: 8),
                  Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 8,
                      vertical: 4,
                    ),
                    decoration: BoxDecoration(
                      color: statusColor.withOpacity(0.2),
                      borderRadius: BorderRadius.circular(6),
                    ),
                    child: Text(
                      statusName,
                      style: TextStyle(
                        color: statusColor,
                        fontSize: 11,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ),
                ],
              ),
              if (checkIn.status == CheckInStatus.completed) ...[
                const SizedBox(height: 8),
                if (checkIn.numericValue != null)
                  Text('${inputLabel ?? (l10n.translate('value') ?? 'Value')}: ${checkIn.numericValue}'),
                if (checkIn.textValue != null)
                  Text('${inputLabel ?? (l10n.translate('value') ?? 'Value')}: ${checkIn.textValue}'),
                if (checkIn.booleanValue != null)
                  Text('${inputLabel ?? (l10n.translate('value') ?? 'Value')}: ${checkIn.booleanValue! ? l10n.yes : l10n.no}'),
                if (checkIn.notes != null && checkIn.notes!.isNotEmpty) ...[
                  const SizedBox(height: 4),
                  Text(
                    '${l10n.notes}: ${checkIn.notes}',
                    style: TextStyle(
                      color: Colors.grey.shade700,
                      fontSize: 12,
                    ),
                  ),
                ],
              ],
            ],
          ),
        ),
      ),
    );
  }

  Color _getStatusColor(CheckInStatus status) {
    switch (status) {
      case CheckInStatus.completed:
        return Colors.green;
      case CheckInStatus.pending:
        return Colors.orange;
      case CheckInStatus.missed:
        return Colors.red;
    }
  }
}
