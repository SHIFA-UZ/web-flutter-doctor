// lib/state/tasks/tasks_provider.dart
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/core/api/api_providers.dart';
import 'package:shifa_doc_app_v1/features/tasks/domain/task_models.dart';
import 'package:shifa_doc_app_v1/state/tasks/task_actions.dart';

class TasksController extends StateNotifier<List<RemoteCareTask>> {
  TasksController(this.ref) : super([]);

  final Ref ref;

  Future<void> loadTasks({int? patientId, TaskStatus? status}) async {
    try {
      final client = ref.read(apiClientProvider);
      final tasks = await fetchTasksWithClient(
        client: client,
        patientId: patientId,
        status: status,
      );
      state = tasks;
    } catch (e) {
      // Handle error
      print('Error loading tasks: $e');
    }
  }
}

final tasksProvider =
    StateNotifierProvider<TasksController, List<RemoteCareTask>>((ref) {
  return TasksController(ref);
});

final taskByIdProvider = FutureProvider.family<RemoteCareTask?, int>((ref, taskId) async {
  try {
    final client = ref.read(apiClientProvider);
    return await fetchTaskWithClient(client: client, taskId: taskId);
  } catch (e) {
    return null;
  }
});

final taskCheckInsProvider = FutureProvider.family<List<TaskCheckIn>, int>((ref, taskId) async {
  try {
    final client = ref.read(apiClientProvider);
    return await fetchCheckInsWithClient(client: client, taskId: taskId);
  } catch (e) {
    return [];
  }
});

final taskProgressProvider = FutureProvider.family<TaskProgress?, int>((ref, taskId) async {
  try {
    final client = ref.read(apiClientProvider);
    return await fetchTaskProgressWithClient(client: client, taskId: taskId);
  } catch (e) {
    return null;
  }
});
