// lib/features/tasks/presentation/tasks_screen.dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/app/router.dart';
import 'package:shifa_doc_app_v1/features/shell/presentation/shell_scope.dart';
import 'package:shifa_doc_app_v1/features/tasks/domain/task_models.dart';
import 'package:shifa_doc_app_v1/state/tasks/tasks_provider.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';

class TasksScreen extends ConsumerStatefulWidget {
  const TasksScreen({Key? key}) : super(key: key);

  @override
  ConsumerState<TasksScreen> createState() => _TasksScreenState();
}

class _TasksScreenState extends ConsumerState<TasksScreen> {
  TaskStatus? _selectedStatus;
  int? _selectedPatientId;
  TaskCategory? _selectedCategory;
  String _searchQuery = '';

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      ref.read(tasksProvider.notifier).loadTasks();
    });
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final brand = Theme.of(context).colorScheme.primary;
    final tasks = ref.watch(tasksProvider);

    // Filter + search
    final filteredTasks = tasks.where((task) {
      if (_selectedStatus != null && task.status != _selectedStatus) {
        return false;
      }
      if (_selectedPatientId != null && task.patientId != _selectedPatientId) {
        return false;
      }
      if (_selectedCategory != null && task.category != _selectedCategory) {
        return false;
      }
      if (_searchQuery.trim().isNotEmpty) {
        final q = _searchQuery.trim().toLowerCase();
        final inName = task.taskName.toLowerCase().contains(q);
        final inPatient = task.patientName.toLowerCase().contains(q);
        if (!inName && !inPatient) return false;
      }
      return true;
    }).toList();

    final activeCount =
        tasks.where((t) => t.status == TaskStatus.active).length;
    final completedCount =
        tasks.where((t) => t.status == TaskStatus.completed).length;
    final overdueCount =
        tasks.where((t) => t.status == TaskStatus.expired).length;

    return Scaffold(
      backgroundColor: const Color(0xFFF5F5F5),
      appBar: AppBar(
        title: Text(l10n.remoteCareTasks),
        backgroundColor: brand,
        foregroundColor: Colors.white,
      ),
      body: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 1200),
          child: RefreshIndicator(
            onRefresh: () async {
              await ref.read(tasksProvider.notifier).loadTasks();
            },
            child: ListView(
              padding: const EdgeInsets.fromLTRB(24, 24, 24, 32),
              children: [
                // Header
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            l10n.remoteCareTasks,
                            style: const TextStyle(
                              fontSize: 24,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                          const SizedBox(height: 4),
                          Text(
                            l10n.translate('remoteCareTasksSubtitle'),
                            style: TextStyle(
                              fontSize: 14,
                              color: Colors.grey.shade700,
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(width: 16),
                    ShifaPrimaryButton(
                      label: l10n.translate('createTask'),
                      icon: Icons.add,
                      onPressed: () async {
                        await ShellScope.pushNamed(
                          context,
                          AppRoutes.createTask,
                        );
                        ref.read(tasksProvider.notifier).loadTasks();
                      },
                    ),
                    const SizedBox(width: 12),
                    ShifaSecondaryButton(
                      label: l10n.translate('useTemplate'),
                      onPressed: () async {
                        await ShellScope.pushNamed(
                          context,
                          AppRoutes.selectTemplate,
                        );
                        ref.read(tasksProvider.notifier).loadTasks();
                      },
                    ),
                  ],
                ),

                const SizedBox(height: 24),

                // Summary cards
                Row(
                  children: [
                    _SummaryCard(
                      title: l10n.translate('activeTasks'),
                      value: activeCount.toString(),
                      color: Colors.blue,
                    ),
                    const SizedBox(width: 12),
                    _SummaryCard(
                      title: l10n.translate('completedTasks'),
                      value: completedCount.toString(),
                      color: Colors.green,
                    ),
                    const SizedBox(width: 12),
                    _SummaryCard(
                      title: l10n.translate('overdueTasks'),
                      value: overdueCount.toString(),
                      color: Colors.red,
                    ),
                  ],
                ),

                const SizedBox(height: 24),

                // Search + filters panel
                Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: Colors.grey.shade200),
                  ),
                  child: Row(
                    children: [
                      Expanded(
                        flex: 3,
                        child: TextField(
                          decoration: InputDecoration(
                            prefixIcon: const Icon(Icons.search),
                            hintText: l10n.translate('searchTasksOrPatients'),
                            border: const OutlineInputBorder(),
                            isDense: true,
                          ),
                          onChanged: (value) {
                            setState(() => _searchQuery = value);
                          },
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        flex: 2,
                        child: DropdownButtonFormField<TaskStatus?>(
                          value: _selectedStatus,
                          decoration: InputDecoration(
                            labelText: l10n.status,
                            border: const OutlineInputBorder(),
                            isDense: true,
                          ),
                          items: [
                            DropdownMenuItem(
                              value: null,
                              child: Text(l10n.all),
                            ),
                            ...TaskStatus.values.map((status) {
                              String statusName;
                              switch (status) {
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
                              return DropdownMenuItem(
                                value: status,
                                child: Text(statusName),
                              );
                            }),
                          ],
                          onChanged: (value) {
                            setState(() => _selectedStatus = value);
                          },
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        flex: 2,
                        child: DropdownButtonFormField<TaskCategory?>(
                          value: _selectedCategory,
                          decoration: InputDecoration(
                            labelText: l10n.category,
                            border: const OutlineInputBorder(),
                            isDense: true,
                          ),
                          items: [
                            DropdownMenuItem(
                              value: null,
                              child: Text(l10n.all),
                            ),
                            DropdownMenuItem(
                              value: TaskCategory.vital,
                              child: Text(l10n.vital),
                            ),
                            DropdownMenuItem(
                              value: TaskCategory.medication,
                              child: Text(l10n.medication),
                            ),
                            DropdownMenuItem(
                              value: TaskCategory.exercise,
                              child: Text(l10n.exercise),
                            ),
                            DropdownMenuItem(
                              value: TaskCategory.other,
                              child: Text(l10n.taskOther),
                            ),
                          ],
                          onChanged: (value) {
                            setState(() => _selectedCategory = value);
                          },
                        ),
                      ),
                      const SizedBox(width: 12),
                      IconButton(
                        icon: const Icon(Icons.refresh),
                        tooltip: l10n.refresh,
                        onPressed: () {
                          ref.read(tasksProvider.notifier).loadTasks();
                        },
                      ),
                    ],
                  ),
                ),

                const SizedBox(height: 24),

                if (filteredTasks.isEmpty)
                  Container(
                    padding: const EdgeInsets.all(32),
                    decoration: BoxDecoration(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(color: Colors.grey.shade200),
                    ),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      crossAxisAlignment: CrossAxisAlignment.center,
                      children: [
                        Icon(
                          Icons.assignment_turned_in_outlined,
                          size: 48,
                          color: Colors.grey.shade400,
                        ),
                        const SizedBox(height: 16),
                        Text(
                          l10n.noTasksFound,
                          style: TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.w600,
                            color: Colors.grey.shade700,
                          ),
                        ),
                        const SizedBox(height: 8),
                        Text(
                          l10n.translate('createFirstRemoteTask'),
                          textAlign: TextAlign.center,
                          style: TextStyle(
                            fontSize: 13,
                            color: Colors.grey.shade600,
                          ),
                        ),
                        const SizedBox(height: 16),
                        ShifaPrimaryButton(
                          label: l10n.translate('createTask'),
                          icon: Icons.add,
                          onPressed: () async {
                            await ShellScope.pushNamed(
                              context,
                              AppRoutes.createTask,
                            );
                            ref.read(tasksProvider.notifier).loadTasks();
                          },
                        ),
                      ],
                    ),
                  )
                else
                  Column(
                    children: filteredTasks
                        .map(
                          (task) => _TaskCard(
                            task: task,
                            onTap: () {
                              ShellScope.pushNamed(
                                context,
                                AppRoutes.taskDetails,
                                arguments: task.id,
                              );
                            },
                          ),
                        )
                        .toList(),
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _SummaryCard extends StatelessWidget {
  final String title;
  final String value;
  final Color color;

  const _SummaryCard({
    required this.title,
    required this.value,
    required this.color,
  });

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: Colors.grey.shade200),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              title,
              style: TextStyle(
                fontSize: 13,
                color: Colors.grey.shade600,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              value,
              style: TextStyle(
                fontSize: 22,
                fontWeight: FontWeight.bold,
                color: color,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _TaskCard extends StatelessWidget {
  final RemoteCareTask task;
  final VoidCallback onTap;

  const _TaskCard({
    required this.task,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final brand = Theme.of(context).colorScheme.primary;
    final statusColor = _getStatusColor(task.status);

    IconData leadingIcon;
    switch (task.category) {
      case TaskCategory.vital:
        leadingIcon = Icons.monitor_heart;
        break;
      case TaskCategory.exercise:
        leadingIcon = Icons.directions_run;
        break;
      case TaskCategory.medication:
        leadingIcon = Icons.medication;
        break;
      case TaskCategory.other:
        leadingIcon = Icons.assignment_outlined;
        break;
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

    final frequencyText =
        '${task.timesPerDay}× ${l10n.translate('perDay')}';

    String dateRange;
    if (task.endDate != null) {
      dateRange =
          '${_formatDate(task.startDate)} – ${_formatDate(task.endDate!)}';
    } else if (task.durationDays != null) {
      dateRange = '${_formatDate(task.startDate)} · '
          '${task.durationDays} ${l10n.translate('days')}';
    } else {
      dateRange = _formatDate(task.startDate);
    }

    final completionLabel = task.progress != null
        ? '${task.progress!.completedCheckIns}/${task.progress!.totalCheckIns}'
        : '0/0';

    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.grey.shade200),
      ),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(12),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Container(
                    width: 40,
                    height: 40,
                    decoration: BoxDecoration(
                      color: brand.withOpacity(0.08),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Icon(
                      leadingIcon,
                      color: brand,
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          task.taskName,
                          style: const TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                        const SizedBox(height: 4),
                        Row(
                          children: [
                            CircleAvatar(
                              radius: 10,
                              backgroundColor: Colors.teal.shade100,
                              child: Text(
                                _initials(task.patientName),
                                style: const TextStyle(
                                  fontSize: 10,
                                  fontWeight: FontWeight.bold,
                                  color: Colors.teal,
                                ),
                              ),
                            ),
                            const SizedBox(width: 6),
                            Flexible(
                              child: Text(
                                task.patientName,
                                style: TextStyle(
                                  fontSize: 13,
                                  color: Colors.grey.shade800,
                                ),
                                overflow: TextOverflow.ellipsis,
                              ),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(width: 8),
                  Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 10,
                      vertical: 4,
                    ),
                    decoration: BoxDecoration(
                      color: statusColor.withOpacity(0.12),
                      borderRadius: BorderRadius.circular(999),
                    ),
                    child: Text(
                      statusName,
                      style: TextStyle(
                        color: statusColor,
                        fontSize: 11,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              Row(
                children: [
                  Icon(
                    Icons.category_outlined,
                    size: 14,
                    color: Colors.grey.shade600,
                  ),
                  const SizedBox(width: 4),
                  Text(
                    categoryName,
                    style: TextStyle(
                      fontSize: 12,
                      color: Colors.grey.shade700,
                    ),
                  ),
                  const SizedBox(width: 12),
                  Icon(
                    Icons.schedule,
                    size: 14,
                    color: Colors.grey.shade600,
                  ),
                  const SizedBox(width: 4),
                  Text(
                    frequencyText,
                    style: TextStyle(
                      fontSize: 12,
                      color: Colors.grey.shade700,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 4),
              Row(
                children: [
                  Icon(
                    Icons.calendar_today_outlined,
                    size: 14,
                    color: Colors.grey.shade600,
                  ),
                  const SizedBox(width: 4),
                  Text(
                    dateRange,
                    style: TextStyle(
                      fontSize: 12,
                      color: Colors.grey.shade700,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          l10n.translate('taskProgress'),
                          style: TextStyle(
                            fontSize: 11,
                            color: Colors.grey.shade600,
                          ),
                        ),
                        const SizedBox(height: 4),
                        LinearProgressIndicator(
                          value: task.progress?.completionPercentage ?? 0.0,
                          backgroundColor: Colors.grey.shade200,
                          valueColor:
                              AlwaysStoppedAnimation<Color>(brand),
                          minHeight: 6,
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(width: 12),
                  Text(
                    completionLabel,
                    style: TextStyle(
                      color: task.progress != null &&
                              task.progress!.completedCheckIns > 0
                          ? Colors.green.shade700
                          : Colors.grey.shade700,
                      fontSize: 13,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
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

  String _initials(String name) {
    final parts = name.trim().split(RegExp(r'\s+'));
    if (parts.isEmpty) return '';
    if (parts.length == 1) {
      final chars = parts.first.characters.toList();
      if (chars.isEmpty) return '';
      if (chars.length == 1) return chars.first.toUpperCase();
      return (chars[0] + chars[1]).toUpperCase();
    }
    final first = parts.first.isNotEmpty ? parts.first[0] : '';
    final last = parts.last.isNotEmpty ? parts.last[0] : '';
    return (first + last).toUpperCase();
  }

  String _formatDate(DateTime d) {
    return '${d.year.toString().padLeft(4, '0')}-'
        '${d.month.toString().padLeft(2, '0')}-'
        '${d.day.toString().padLeft(2, '0')}';
  }
}
