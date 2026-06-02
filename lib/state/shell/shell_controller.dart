import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Controls the selected tab index for the main shell navigation.
class ShellController extends StateNotifier<int> {
  ShellController() : super(1); // Default to Home tab (index 1)

  void setTab(int index) => state = index;
}

final shellProvider = StateNotifierProvider<ShellController, int>(
  (ref) => ShellController(),
);

/// When set (e.g. from notification tap for TASK_COMPLETED), shell will push TaskDetailsScreen(taskId).
/// Cleared after pushing to avoid re-triggering.
final notificationPendingTaskIdProvider = StateProvider<int?>((ref) => null);

/// When set from a chat push notification, ChatScreen opens this conversation.
final notificationPendingConversationIdProvider = StateProvider<int?>((ref) => null);
